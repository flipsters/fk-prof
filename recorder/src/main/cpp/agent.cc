#include <stdio.h>
#include <string.h>

#include <string>
#include <jvmti.h>

#include "globals.hh"
#include "config.hh"
#include "thread_map.hh"
#include "profiler.hh"
#include "controller.hh"
#include "perf_ctx.hh"
#include "prob_pct.hh"

#if defined(__APPLE__) || defined(__FreeBSD__)
#define GETENV_NEW_THREAD_ASYNC_UNSAFE
#endif

LoggerP logger(nullptr);
static ConfigurationOptions* CONFIGURATION;
static Controller* controller;
static ThreadMap threadMap;
PerfCtx::Registry* GlobalCtx::ctx_reg;
ProbPct* GlobalCtx::prob_pct;

// This has to be here, or the VM turns off class loading events.
// And AsyncGetCallTrace needs class loading events to be turned on!
void JNICALL OnClassLoad(jvmtiEnv *jvmti_env, JNIEnv *jni_env, jthread thread,
        jclass klass) {
    IMPLICITLY_USE(jvmti_env);
    IMPLICITLY_USE(jni_env);
    IMPLICITLY_USE(thread);
    IMPLICITLY_USE(klass);
}

static void JNICALL CompiledMethodLoad(jvmtiEnv* jvmti, jmethodID method,
                                       jint code_size, const void* code_addr,
                                       jint map_length, const jvmtiAddrLocationMap* map,
                                       const void* compile_info) {
    // Needed to enable DebugNonSafepoints info by default
}

// Calls GetClassMethods on a given class to force the creation of
// jmethodIDs of it.
void CreateJMethodIDsForClass(jvmtiEnv *jvmti, jclass klass) {
    jint method_count;
    JvmtiScopedPtr<jmethodID> methods(jvmti);
    jvmtiError e = jvmti->GetClassMethods(klass, &method_count, methods.GetRef());
    if (e != JVMTI_ERROR_NONE && e != JVMTI_ERROR_CLASS_NOT_PREPARED) {
        // JVMTI_ERROR_CLASS_NOT_PREPARED is okay because some classes may
        // be loaded but not prepared at this point.
        JvmtiScopedPtr<char> ksig(jvmti);
        JVMTI_ERROR_CLEANUP(
            jvmti->GetClassSignature(klass, ksig.GetRef(), NULL),
            ksig.AbandonBecauseOfError());
        logError("Failed to create method IDs for methods in class %s with error %d ",
                 ksig.Get(), e);
    }
}

void JNICALL OnVMInit(jvmtiEnv *jvmti, JNIEnv *jniEnv, jthread thread) {
    IMPLICITLY_USE(thread);
    // Forces the creation of jmethodIDs of the classes that had already
    // been loaded (eg java.lang.Object, java.lang.ClassLoader) and
    // OnClassPrepare() misses.
    jint class_count;
    JvmtiScopedPtr<jclass> classes(jvmti);
    JVMTI_ERROR((jvmti->GetLoadedClasses(&class_count, classes.GetRef())));
    jclass *classList = classes.Get();
    for (int i = 0; i < class_count; ++i) {
        jclass klass = classList[i];
        CreateJMethodIDsForClass(jvmti, klass);
    }

    controller->start();
}

void JNICALL OnClassPrepare(jvmtiEnv *jvmti_env, JNIEnv *jni_env,
        jthread thread, jclass klass) {
    IMPLICITLY_USE(jni_env);
    IMPLICITLY_USE(thread);
    // We need to do this to "prime the pump", as it were -- make sure
    // that all of the methodIDs have been initialized internally, for
    // AsyncGetCallTrace.  I imagine it slows down class loading a mite,
    // but honestly, how fast does class loading have to be?
    CreateJMethodIDsForClass(jvmti_env, klass);
}

void JNICALL OnVMDeath(jvmtiEnv *jvmti_env, JNIEnv *jni_env) {
    IMPLICITLY_USE(jvmti_env);
    IMPLICITLY_USE(jni_env);
}

static bool PrepareJvmti(jvmtiEnv *jvmti) {
    // Set the list of permissions to do the various internal VM things
    // we want to do.
    jvmtiCapabilities caps;

    memset(&caps, 0, sizeof(caps));
    caps.can_generate_all_class_hook_events = 1;

    caps.can_get_source_file_name = 1;
    caps.can_get_line_numbers = 1;
    caps.can_get_bytecodes = 1;
    caps.can_get_constant_pool = 1;
    caps.can_generate_compiled_method_load_events = 1;
    caps.can_generate_native_method_bind_events = 1;

    jvmtiCapabilities all_caps;
    int error;

    if (JVMTI_ERROR_NONE ==
            (error = jvmti->GetPotentialCapabilities(&all_caps))) {
        // This makes sure that if we need a capability, it is one of the
        // potential capabilities.  The technique isn't wonderful, but it
        // is compact and as likely to be compatible between versions as
        // anything else.
        char *has = reinterpret_cast<char *>(&all_caps);
        const char *should_have = reinterpret_cast<const char *>(&caps);
        for (int i = 0; i < sizeof(all_caps); i++) {
            if ((should_have[i] != 0) && (has[i] == 0)) {
                return false;
            }
        }

        // This adds the capabilities.
        JVMTI_ERROR_CLEANUP_RET(
            jvmti->AddCapabilities(&caps),
            false,
            logError("Failed to add capabilities with error %d\n", error))
    }
    return true;
}

sigset_t prof_signal_mask;

void (*actual_JVM_StartThread)(JNIEnv *, jthread) = NULL;

void JVM_StartThread_Interposer(JNIEnv *jni_env, jobject jthread) {
    pthread_sigmask(SIG_BLOCK, &prof_signal_mask, NULL);
    actual_JVM_StartThread(jni_env, jthread);
    pthread_sigmask(SIG_UNBLOCK, &prof_signal_mask, NULL);
}

//Set up interposition of calls to Thread::start0
void JNICALL OnNativeMethodBind(jvmtiEnv *jvmti_env, JNIEnv *jni_env, jthread thread,
        jmethodID method, void *address, void **new_address_ptr) {
    if (actual_JVM_StartThread != NULL) {
        return;
    }

    char *name_ptr, *signature_ptr;

    int err = jvmti_env->GetMethodName(method, &name_ptr, &signature_ptr, NULL);
    if (err != JNI_OK) {
        logError("Error %i retrieving method name", err);
        return;
    }
    if (strcmp(name_ptr, "start0") == 0 && strcmp(signature_ptr, "()V") == 0) {
        jclass declaringClass;
        int err = jvmti_env->GetMethodDeclaringClass(method, &declaringClass);
        if (err != JNI_OK) {
            logError("Error %i retrieving class", err);
            jvmti_env->Deallocate((unsigned char *) name_ptr);
            jvmti_env->Deallocate((unsigned char *) signature_ptr);
            return;
        }
        jclass clazz = jni_env->GetObjectClass(declaringClass);
        jmethodID getSimpleNameMethod = jni_env->GetMethodID(clazz,
            "getSimpleName", "()Ljava/lang/String;");
        jstring jClassName = (jstring) jni_env->CallObjectMethod(declaringClass,
            getSimpleNameMethod);

        const char *className = jni_env->GetStringUTFChars(jClassName, 0);
        if (strcmp(className, "Thread") == 0) {
            *new_address_ptr = (void*) &JVM_StartThread_Interposer;
            actual_JVM_StartThread = (void (*)(JNIEnv *, jthread)) address;
        }
        jni_env->ReleaseStringUTFChars(jClassName, className);
    }
    jvmti_env->Deallocate((unsigned char *) name_ptr);
    jvmti_env->Deallocate((unsigned char *) signature_ptr);
}

volatile bool main_started = false;

void JNICALL OnThreadStart(jvmtiEnv *jvmti_env, JNIEnv *jni_env, jthread thread) {
    SPDLOG_TRACE(logger, "Some thraed started");
    jvmtiThreadInfo thread_info;
    int error = jvmti_env->GetThreadInfo(thread, &thread_info);
    if (error == JNI_OK) {
        if (!main_started) {
            if (strcmp(thread_info.name, "main") == 0) {
                main_started = true;
            }
        }
        threadMap.put(jni_env, thread_info.name, thread_info.priority, thread_info.is_daemon);
        jvmti_env->Deallocate((unsigned char *) thread_info.name);
    }
    pthread_sigmask(SIG_UNBLOCK, &prof_signal_mask, NULL);
}

void JNICALL OnThreadEnd(jvmtiEnv *jvmti_env, JNIEnv *jni_env, jthread thread) {
    pthread_sigmask(SIG_BLOCK, &prof_signal_mask, NULL);
    threadMap.remove(jni_env);
}

static bool RegisterJvmti(jvmtiEnv *jvmti) {
    sigemptyset(&prof_signal_mask);
    sigaddset(&prof_signal_mask, SIGPROF);
    // Create the list of callbacks to be called on given events.
    jvmtiEventCallbacks *callbacks = new jvmtiEventCallbacks();
    memset(callbacks, 0, sizeof(jvmtiEventCallbacks));

    callbacks->VMInit = &OnVMInit;
    callbacks->VMDeath = &OnVMDeath;

    callbacks->ClassLoad = &OnClassLoad;
    callbacks->ClassPrepare = &OnClassPrepare;

    callbacks->CompiledMethodLoad = &CompiledMethodLoad;

    callbacks->NativeMethodBind = &OnNativeMethodBind;
    callbacks->ThreadStart = &OnThreadStart;
    callbacks->ThreadEnd = &OnThreadEnd;

    JVMTI_ERROR_RET((jvmti->SetEventCallbacks(callbacks, sizeof(jvmtiEventCallbacks))), false);

    jvmtiEvent events[] = { JVMTI_EVENT_VM_DEATH, JVMTI_EVENT_VM_INIT,
                            JVMTI_EVENT_CLASS_FILE_LOAD_HOOK, JVMTI_EVENT_CLASS_LOAD, JVMTI_EVENT_CLASS_PREPARE, JVMTI_EVENT_COMPILED_METHOD_LOAD,
                            JVMTI_EVENT_THREAD_START, JVMTI_EVENT_THREAD_END, JVMTI_EVENT_NATIVE_METHOD_BIND };

    size_t num_events = sizeof(events) / sizeof(jvmtiEvent);

    // Enable the callbacks to be triggered when the events occur.
    // Events are enumerated in jvmstatagent.h
    for (int i = 0; i < num_events; i++) {
        JVMTI_ERROR_RET((jvmti->SetEventNotificationMode(JVMTI_ENABLE, events[i], NULL)), false);
        SPDLOG_DEBUG(logger, "Initialized notification for ti-event = {} (which is {} / {})", events[i], i + 1, num_events);
    }

    return true;
}

#define LST "LOGGING-SELF-TEST: "

void log_level_self_test() {
    logger->trace(LST "*trace*");
    SPDLOG_TRACE(logger, LST "*compile-time checked trace*");
    logger->debug(LST "*debug*");
    SPDLOG_DEBUG(logger, LST "*compile-time checked debug*");
    logger->info(LST "*info*");
    logger->warn(LST "*warn*");
    logger->error(LST "*err*");
    logger->critical(LST "*critical*");
}

AGENTEXPORT jint JNICALL Agent_OnLoad(JavaVM *jvm, char *options, void *reserved) {
    IMPLICITLY_USE(reserved);
    int err;
    jvmtiEnv *jvmti;
    logger = spdlog::syslog_logger("syslog", "fk-prof-rec", LOG_PID);
    
    CONFIGURATION = new ConfigurationOptions(options);

    logger->set_level(CONFIGURATION->log_level);
    logger->set_pattern("{%t} %+");//TODO: make this configurable
    log_level_self_test();
    logger->info("======================= Starting fk-prof JVMTI agent =======================");
    
    if (! CONFIGURATION->valid()) return 1;

    if ((err = (jvm->GetEnv(reinterpret_cast<void **>(&jvmti), JVMTI_VERSION))) !=
            JNI_OK) {
        logError("ERROR: JVMTI initialisation error %d\n", err);
        return 1;
    }

    /*
      JNIEnv *jniEnv;
      if ((err = (vm->GetEnv(reinterpret_cast<void **>(&jniEnv),
      JNI_VERSION_1_6))) != JNI_OK) {
        logError("JNI Error %d\n", err);
        return 1;
      }
    */

    logger->trace("Preparing TI");

    if (!PrepareJvmti(jvmti)) {
        logger->critical("Failed to initialize JVMTI. Continuing...");
        return 0;
    }

    if (!RegisterJvmti(jvmti)) {
        logger->critical("Failed to enable JVMTI events");
        // We fail hard here because we may have failed in the middle of
        // registering callbacks, which will leave the system in an
        // inconsistent state.
        return 1;
    }

    Asgct::SetAsgct(Accessors::GetJvmFunction<ASGCTType>("AsyncGetCallTrace"));
    Asgct::SetIsGCActive(Accessors::GetJvmFunction<IsGCActiveType>("IsGCActive"));

    GlobalCtx::ctx_reg = new PerfCtx::Registry();
    GlobalCtx::prob_pct = new ProbPct();    
    
    controller = new Controller(jvm, jvmti, threadMap, *CONFIGURATION);

    return 0;
}

AGENTEXPORT void JNICALL Agent_OnUnload(JavaVM *vm) {
    IMPLICITLY_USE(vm);

    controller->stop();

    delete controller;
    delete GlobalCtx::ctx_reg;
    delete GlobalCtx::prob_pct;
    delete CONFIGURATION;
}

void logError(const char *__restrict format, ...) {
    va_list arg;

    va_start(arg, format);
    vfprintf(stderr, format, arg);
    va_end(arg);
}

ThreadMap& get_thread_map() {
    return threadMap;
}
