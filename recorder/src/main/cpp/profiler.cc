#include "profiler.hh"

ASGCTType Asgct::asgct_;
IsGCActiveType Asgct::is_gc_active_;

TRACE_DEFINE_BEGIN(Profiler, kTraceProfilerTotal)
    TRACE_DEFINE("start failed")
    TRACE_DEFINE("start succeeded")
    TRACE_DEFINE("set sampling interval failed")
    TRACE_DEFINE("set sampling interval succeeded")
    TRACE_DEFINE("set stack frames to capture failed")
    TRACE_DEFINE("set stack frames to capture succeeded")
    TRACE_DEFINE("set new file failed")
    TRACE_DEFINE("set new file succeeded")
    TRACE_DEFINE("stop failed")
    TRACE_DEFINE("stop succeeded")
TRACE_DEFINE_END(Profiler, kTraceProfilerTotal);


bool Profiler::lookup_frame_information(const JVMPI_CallFrame &frame, jvmtiEnv *jvmti, MethodListener &logWriter) {
    jint error;
    JvmtiScopedPtr<char> methodName(jvmti);

    error = jvmti->GetMethodName(frame.method_id, methodName.GetRef(), NULL, NULL);
    if (error != JVMTI_ERROR_NONE) {
        methodName.AbandonBecauseOfError();
        if (error == JVMTI_ERROR_INVALID_METHODID) {
            static int once = 0;
            if (!once) {
                once = 1;
                logError("One of your monitoring interfaces "
                         "is having trouble resolving its stack traces.  "
                         "GetMethodName on a jmethodID involved in a stacktrace "
                         "resulted in an INVALID_METHODID error which usually "
                         "indicates its declaring class has been unloaded.\n");
                logError("Unexpected JVMTI error %d in GetMethodName\n", error);
            }
        }
        return false;
    }

    // Get class name, put it in signature_ptr
    jclass declaring_class;
    JVMTI_ERROR_RET(
        jvmti->GetMethodDeclaringClass(frame.method_id, &declaring_class), false);

    JvmtiScopedPtr<char> signature_ptr2(jvmti);
    JVMTI_ERROR_CLEANUP_RET(
        jvmti->GetClassSignature(declaring_class, signature_ptr2.GetRef(), NULL),
        false, signature_ptr2.AbandonBecauseOfError());

    // Get source file, put it in source_name_ptr
    char *fileName;
    JvmtiScopedPtr<char> source_name_ptr(jvmti);
    static char file_unknown[] = "UnknownFile";
    if (JVMTI_ERROR_NONE !=
            jvmti->GetSourceFileName(declaring_class, source_name_ptr.GetRef())) {
        source_name_ptr.AbandonBecauseOfError();
        fileName = file_unknown;
    } else {
        fileName = source_name_ptr.Get();
    }

    logWriter.recordNewMethod((method_id) frame.method_id, fileName,
                              signature_ptr2.Get(), methodName.Get());

    return true;
}

static void handle_profiling_signal(int signum, siginfo_t *info, void *context) {
    if (GlobalCtx::recording.on.load(std::memory_order_acquire)) {
        GlobalCtx::recording.profiler->handle(signum, info, context);
    } else {
        logger->warn("Received profiling signal while recording is off! Something wrong?");
    }
}

void Profiler::handle(int signum, siginfo_t *info, void *context) {
    IMPLICITLY_USE(signum);//TODO: make this reenterant or implement try_lock+backoff
    IMPLICITLY_USE(info);
    SimpleSpinLockGuard<false> guard(ongoing_conf); // sync buffer
    ThreadBucket *threadInfo;

    // sample data structure
    STATIC_ARRAY(frames, JVMPI_CallFrame, capture_stack_depth(), MAX_FRAMES_TO_CAPTURE);

    JVMPI_CallTrace trace;
    trace.frames = frames;
    JNIEnv *jniEnv = getJNIEnv(jvm);
    if (jniEnv == NULL) {
        IsGCActiveType is_gc_active = Asgct::GetIsGCActive();
        trace.num_frames = ((is_gc_active != NULL) &&
                            ((*is_gc_active)() == 1)) ? -2 : -3; // ticks_unknown_not_Java or GC
        threadInfo = nullptr;
    } else {
        trace.env_id = jniEnv;
        ASGCTType asgct = Asgct::GetAsgct();
        (*asgct)(&trace, capture_stack_depth(), context);
        threadInfo = thread_map.get(jniEnv);
    }
    // log all samples, failures included, let the post processing sift through the data
    buffer->push(trace, threadInfo);
}

bool Profiler::start(JNIEnv *jniEnv) {
    SimpleSpinLockGuard<true> guard(ongoing_conf);
    /* within critical section */

    if (__is_running()) {
        TRACE(Profiler, kTraceProfilerStartFailed);
        logError("WARN: Start called but sampling is already running\n");
        return true;
    }

    TRACE(Profiler, kTraceProfilerStartOk);

    // reference back to Profiler::handle on the singleton
    // instance of Profiler
    handler->SetAction(&handle_profiling_signal);
    processor->start(jniEnv);
    bool res = handler->updateSigprofInterval();
    return res;
}

void Profiler::stop() {
    /* Make sure it doesn't overlap with configure */
    SimpleSpinLockGuard<true> guard(ongoing_conf);

    if (!__is_running()) {
        TRACE(Profiler, kTraceProfilerStopFailed);
        return;
    }

    handler->stopSigprof();
    processor->stop();
}

// non-blocking version (cen be called once spin-lock with acquire semantics is grabed)
bool Profiler::__is_running() {
    return processor && processor->isRunning();
}

void Profiler::set_sampling_freq(std::uint32_t sampling_freq) {
    auto mean_sampling_itvl = 1000000 / sampling_freq;
    std::uint32_t itvl_10_pct = 0.1 * mean_sampling_itvl;
    auto itvl_max = mean_sampling_itvl + itvl_10_pct;
    auto itvl_min = mean_sampling_itvl - itvl_10_pct;
    itvl_min = itvl_min > 0 ? itvl_min : DEFAULT_SAMPLING_INTERVAL;
    itvl_max = itvl_max > 0 ? itvl_max : DEFAULT_SAMPLING_INTERVAL;
    logger->warn("Chose CPU sampling interval range [{0:06d}, {1:06d}) for requested sampling freq {2:d} Hz", itvl_min, itvl_max, sampling_freq);
}

void Profiler::set_max_stack_depth(int max_stack_depth) {
    max_stack_depth = (max_stack_depth > 0 && max_stack_depth < (MAX_FRAMES_TO_CAPTURE - 1)) ?
        max_stack_depth : DEFAULT_MAX_FRAMES_TO_CAPTURE;
}

void Profiler::configure() {
    serializer = new ProfileSerializer(writer);
    
    buffer = new CircularQueue(*serializer, capture_stack_depth());

    handler = new SignalHandler(itvl_min, itvl_max);
    int processor_interval = Size * itvl_min / 1000 / 2;
    processor = new Processor(jvmti, *buffer, *handler, processor_interval > 0 ? processor_interval : 1);
}

Profiler::~Profiler() {
    SimpleSpinLockGuard<false> guard(ongoing_conf); // nonblocking
    if (__is_running()) stop();
    delete processor;
    delete handler;
    delete buffer;
    delete serializer;
}