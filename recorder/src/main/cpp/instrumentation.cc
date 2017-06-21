#include "instrumentation.hh"
#include "globals.hh"
#include <dlfcn.h>
#include <set>
#include "ctx_switch_tracer.hh"

extern Instrumentation* instr;

static void* load_lib(const char* lib_path) {
    void* lib = dlopen(lib_path, RTLD_LAZY);
    if (lib == nullptr) {
        logger->error("Couldn't load library: {} (error: '{}')", lib_path, dlerror());
    }
    return lib;
}

static void unload_lib(void* lib) {
    dlclose(lib);
}

static void* load_sun_boot_lib(jvmtiEnv* jvmti, const std::string& lib_name) {
    char* lib_parent_path;

    auto e = jvmti->GetSystemProperty("sun.boot.library.path", &lib_parent_path);
    if (e != JVMTI_ERROR_NONE) {
        logger->error("Couldn't get boot-lib-path, hence couldn't load {}", lib_name);
        return nullptr;
    }

    std::stringstream lib_path_ss;
    lib_path_ss << lib_parent_path << "/" << lib_name;
    jvmti->Deallocate(reinterpret_cast<unsigned char*>(lib_parent_path));

    auto path = lib_path_ss.str();
    return load_lib(path.c_str());
}

static void* lib_symbol(void* lib, const std::string& sym_name) {
    assert(lib != nullptr);
    void* sym = dlsym(lib, sym_name.c_str());
    if (sym == nullptr) {
        logger->error("Couldn't find symbol: {} (error: '{}')", sym_name, dlerror());
    }
    return sym;
}


Instrumentation::Instrumentation(JavaVM *_jvm, jvmtiEnv *_jvmti, ConfigurationOptions& _cfg) : bci_allowed(_cfg.allow_bci), jvmti(_jvmti) {
    if (bci_allowed) {
        crw_lib = load_sun_boot_lib(jvmti, "libjava_crw_demo.so");
        do_instr = reinterpret_cast<Crw::instrument>(lib_symbol(crw_lib, "java_crw_demo"));
        assert(do_instr != nullptr);
        get_cn = reinterpret_cast<Crw::get_class_name>(lib_symbol(crw_lib, "java_crw_demo_classname"));
        assert(get_cn != nullptr);
    }
}

Instrumentation::~Instrumentation() {
    if (bci_allowed) {
        unload_lib(crw_lib);
    }
}
static void crw_error_handler(const char * msg, const char *file, int line) {
    logger->error("CRW-op failed: {} (file: {}, line: {})", msg, file, line);
}

const std::set<std::string> SYSTEM_CLASSES = {
    "java/lang/Object",
    "java/io/Serializable",
    "java/lang/String",
    "java/lang/Class",
    "java/lang/ClassLoader",
    "java/lang/System",
    "java/lang/Thread",
    "java/lang/ThreadGroup"
};

#define TRACKER_CLASS_NAME "fk/prof/InstrumentationStub"
#define TRACKER_CLASS_SIGNATURE "L" TRACKER_CLASS_NAME ";"
#define METHOD_RETURN_TRACEPT_NAME "returnTracepoint"
#define METHOD_RETURN_TRACEPT_SIG "(II)V"
#define TRACKER_ENGAGED_NAME  "engaged"
#define TRACKER_ENGAGED_SIG "I"

void Instrumentation::class_file_loaded(VmInitState init_state, JNIEnv* env, jclass class_being_redefined, jobject loader, const char* name,
                                        jobject protection_domain, jint class_data_len, const unsigned char* class_data,
                                        jint* new_class_data_len, unsigned char** new_class_data) {
    auto total_instances_bci_ed = clsfile_load_count.fetch_add(1, std::memory_order_relaxed);

    char* classname = (name == nullptr) ? get_cn(class_data, class_data_len, &crw_error_handler) : strdup(name);

    if (strcmp(classname, TRACKER_CLASS_NAME) != 0) {
        int system_class =
            (init_state == VmInitState::PRE_INIT) &&
            ((SYSTEM_CLASSES.find(classname) != std::end(SYSTEM_CLASSES)) ||
             (total_instances_bci_ed < 8));

        unsigned char* new_image = nullptr;
        long new_length = 0;

        do_instr(0, classname, class_data, class_data_len, system_class,
                 const_cast<char*>(TRACKER_CLASS_NAME), const_cast<char*>(TRACKER_CLASS_SIGNATURE),

                 nullptr, nullptr,
                 const_cast<char*>(METHOD_RETURN_TRACEPT_NAME), const_cast<char*>(METHOD_RETURN_TRACEPT_SIG),
                 nullptr, nullptr,
                 nullptr, nullptr,

                 &new_image, &new_length, &crw_error_handler, nullptr);

        auto new_class_data_prep_failed = false;
        if (new_length > 0) {
            unsigned char* jvmti_klass_data;
            auto e = jvmti->Allocate(static_cast<jlong>(new_length), &jvmti_klass_data);
            if (e == JVMTI_ERROR_NONE) {
                memcpy(jvmti_klass_data, new_image, new_length);
                *new_class_data_len = static_cast<jint>(new_length);
                *new_class_data     = jvmti_klass_data;
            } else {
                logger->error("Couldn't allocate memory for class-rewrite");
                new_class_data_prep_failed = true;
            }
        }
        if (new_class_data_prep_failed) {
            *new_class_data_len = 0;
            *new_class_data     = nullptr;
        }
        if (new_image != nullptr) {
            free(new_image);
        }
    }
    free(classname);
}

template <typename V> struct Releasable {
    typedef std::function<V()> Acq;
    typedef std::function<void(V)> Rel;

    V v;
    Rel release;

    Releasable(Acq acquire, Rel _release) : release(_release) {
        v = acquire();
    }

    virtual ~Releasable() {
        release(v);
    }
};

struct LocalFrameTracker : Releasable<int> {
    LocalFrameTracker(JNIEnv *jni, int num) : Releasable([jni, num]() {
            if (jni->PushLocalFrame(num) != 0) {
                logger->error("Couldn't push local-frame {}", num);
            }
            return num;
        },
        [jni](int _) { jni->PopLocalFrame(nullptr); }) {}

    ~LocalFrameTracker() {}
};

bool has_exception(JNIEnv* jni, const char* msg) {
    auto throwable = jni->ExceptionOccurred();
    if (throwable == nullptr) {
        return false;
    }
    //TODO: add a counter here
    logger->error("Exception found: {}", msg);
    jni->ExceptionDescribe();
    return true;
}

void handle_exception_if_any(JNIEnv* jni, const char* msg) {
    if (has_exception(jni, msg)) jni->ExceptionClear();
}

void engage_tracepoints(JNIEnv *jni, bool on) {
    LocalFrameTracker lft(jni, 10);
    if (has_exception(jni, "Won't try to enable tracking")) return;

    auto tracker_klass = jni->FindClass(TRACKER_CLASS_SIGNATURE);
    handle_exception_if_any(jni, "Something failed while loading class " TRACKER_CLASS_SIGNATURE);
    if (tracker_klass == NULL) {
        return;
    }

    auto field_id = jni->GetStaticFieldID(tracker_klass, TRACKER_ENGAGED_NAME, TRACKER_ENGAGED_SIG);
    handle_exception_if_any(jni, "Couldn't find tracker field " TRACKER_ENGAGED_NAME);
    if (field_id == NULL) {
        return;
    }

    jni->SetStaticIntField(tracker_klass, field_id, on ? 1 : 0);
    handle_exception_if_any(jni, "Couldn't set tracker field " TRACKER_ENGAGED_NAME);
}

void Instrumentation::engage_return_tracer(UniqueReadsafePtr<CtxSwitchTracer>& ctxsw_tracer, JNIEnv* env) {
    auto old_rsptr = tracer_ursp.exchange(&ctxsw_tracer, std::memory_order_relaxed);
    assert(old_rsptr == nullptr);
    engage_tracepoints(env, true);
}

void Instrumentation::disengage_return_tracer(JNIEnv* env) {
    engage_tracepoints(env, false); //this line can be moved down for testing, will still work (a little more inefficiently though) -jj
    auto old_rsptr = tracer_ursp.exchange(nullptr, std::memory_order_relaxed);
    assert(old_rsptr != nullptr);
}

void Instrumentation::evt_mthd_return(JNIEnv* env) {
    auto tracer = tracer_ursp.load(std::memory_order_relaxed);
    if (tracer == nullptr) return;
    ReadsafePtr<CtxSwitchTracer> cst(*tracer);
    if (cst.available()) cst->evt_mthd_return(env);
}

extern "C" JNIEXPORT void JNICALL Java_fk_prof_InstrumentationStub_evtReturn(JNIEnv* env, jclass _) {
    instr->evt_mthd_return(env);
}
