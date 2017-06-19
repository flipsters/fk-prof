#include "ctx_switch_tracer.hh"

CtxSwitchTracer::CtxSwitchTracer(ThreadMap& _thread_map, ProfileSerializingWriter& _serializer, std::uint32_t _max_stack_depth, ProbPct& _prob_pct, std::uint8_t _noctx_cov_pct, std::uint64_t _latency_threshold_ns, bool _track_wakeup_lag, bool _use_global_clock, bool _track_syscall) {}

CtxSwitchTracer::~CtxSwitchTracer() {}

void CtxSwitchTracer::start(JNIEnv* env) {}

void CtxSwitchTracer::run() {}

void CtxSwitchTracer::stop() {}
