#include "circular_queue.hh"
#include <iostream>
#include <unistd.h>

template <typename TraceType, typename InMsg> CircularQueue<TraceType, InMsg>::CircularQueue(QueueListener<TraceType>& listener, std::uint32_t maxFrameSize) : listener_(listener), input(0), output(0) {
    memset(buffer, 0, sizeof(buffer));
    for (int i = 0; i < Capacity; ++i)
        frame_buffer_[i] = new StackFrame[maxFrameSize]();
}

template <typename TraceType, typename InMsg> CircularQueue<TraceType, InMsg>::~CircularQueue() {
    for (int i = 0; i < Capacity; ++i)
        delete[] frame_buffer_[i];
}

template <typename TraceType, typename InMsg> bool CircularQueue<TraceType, InMsg>::acquire_write_slot(size_t& slot) {
    size_t currentInput;
    size_t nextInput;
    do {
        currentInput = input.load(std::memory_order_seq_cst);
        nextInput = advance(currentInput);
        if (output.load(std::memory_order_seq_cst) == nextInput) {
            return false;
        }
        // TODO: have someone review the memory ordering constraints
    } while (!input.compare_exchange_strong(currentInput, nextInput, std::memory_order_relaxed));

    slot = currentInput;
    return true;
}

template <typename TraceType, typename InMsg> void CircularQueue<TraceType, InMsg>::mark_committed(const size_t slot) {
    buffer[slot].is_committed.store(COMMITTED, std::memory_order_release);
}

template <typename TraceType, typename InMsg> bool CircularQueue<TraceType, InMsg>::push(const InMsg& in_msg) {
    size_t slot;
    if (! acquire_write_slot(slot)) return false;

    StackFrame* fb = frame_buffer_[slot];
    TraceType& entry = buffer[slot];
    write(entry, fb, in_msg);

    mark_committed(slot);
    return true;
}

template <typename TraceType, typename InMsg> bool CircularQueue<TraceType, InMsg>::pop() {
    const auto current_output = output.load(std::memory_order_seq_cst);

    // queue is empty
    if (current_output == input.load(std::memory_order_seq_cst)) {
        return false;
    }

    // wait until we've finished writing to the buffer
    while (buffer[current_output].is_committed.load(std::memory_order_acquire) != COMMITTED) {
        usleep(1);
    }

    listener_.record(buffer[current_output]);
    
    // ensure that the record is ready to be written to
    buffer[current_output].is_committed.store(UNCOMMITTED, std::memory_order_release);
    // Signal that you've finished reading the record
    output.store(advance(current_output), std::memory_order_seq_cst);

    return true;
}

template <typename TraceType, typename InMsg> size_t CircularQueue<TraceType, InMsg>::advance(size_t index) const {
    return (index + 1) % Capacity;
}

CpuSamplesQueue::CpuSamplesQueue(QueueListener<TraceHolder> &listener, std::uint32_t maxFrameSize) : CircularQueue<TraceHolder, CpuSample>(listener, maxFrameSize) {}

CpuSamplesQueue::~CpuSamplesQueue() {}

void CpuSamplesQueue::write(TraceHolder& entry, StackFrame* fb, const CpuSample& in_msg) {
    // Unable to use memcpy inside the push method because its not async-safe
    
    switch(in_msg.type) {
    case BacktraceType::Java: {
        const auto& jct = in_msg.ct.java.ct;
        for (auto frame_num = 0; frame_num < jct->num_frames; ++frame_num) {
            // Padding already set to 0 by the consumer.
            fb[frame_num].jvmpi_frame.lineno = jct->frames[frame_num].lineno;
            fb[frame_num].jvmpi_frame.method_id = jct->frames[frame_num].method_id;
        }
        entry.trace.num_frames = jct->num_frames; }
    break;
    case BacktraceType::Native: {
        const auto& nct = in_msg.ct.native;
        for (auto frame_num = 0; frame_num < nct.num_frames; ++frame_num) {
            fb[frame_num].native_frame = nct.ct[frame_num];
        }
        entry.trace.num_frames = nct.num_frames; }
        break;
    default:
        assert(false);
    }
    
    entry.trace.frames = fb;
    entry.trace.type = in_msg.type;
    entry.trace.error = in_msg.error;
    entry.info = in_msg.info;
    entry.ctx_len = (entry.info == nullptr) ? 0 : entry.info->ctx_tracker.current(entry.ctx);
    entry.default_ctx = in_msg.default_ctx;
}

bool CpuSamplesQueue::push(const JVMPI_CallTrace &item, const BacktraceError error, bool default_ctx, ThreadBucket *info) {
    CpuSample s(item, info, error, default_ctx);
    return CircularQueue<TraceHolder, CpuSample>::push(s);
}

bool CpuSamplesQueue::push(const NativeFrame* item, const std::uint32_t num_frames, const BacktraceError error, bool default_ctx, ThreadBucket *info) {
    CpuSample s(item, num_frames, info, error, default_ctx);
    return CircularQueue<TraceHolder, CpuSample>::push(s);
}

CpuSample::CpuSample(const JVMPI_CallTrace& item, ThreadBucket* info, const BacktraceError error, const bool default_ctx) : CpuSample(BacktraceType::Java, info, error, default_ctx) {
    ct.java.ct = &item;
}

CpuSample::CpuSample(const NativeFrame* trace, const std::uint32_t num_frames, ThreadBucket* info, const BacktraceError error, bool default_ctx) : CpuSample(BacktraceType::Native, info, error, default_ctx) {
    ct.native.ct = trace;
    ct.native.num_frames = num_frames;
}

CpuSample::CpuSample(BacktraceType _type, ThreadBucket* _info, const BacktraceError _error, bool _default_ctx) : type(_type), info(_info), error(_error), default_ctx(_default_ctx) { }

template class CircularQueue<TraceHolder, CpuSample>;
