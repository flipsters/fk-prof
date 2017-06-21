// Somewhat originally dervied from:
// http://www.codeproject.com/Articles/43510/Lock-Free-Single-Producer-Single-Consumer-Circular

// Multiple Producer, Single Consumer Queue

#ifndef CIRCULAR_QUEUE_H
#define CIRCULAR_QUEUE_H

#include "thread_map.hh"
#include "stacktraces.hh"
#include <string.h>
#include <cstddef>

const size_t Size = 1024;

// Capacity is 1 larger than size to make sure
// we can use input = output as our "can't read" invariant
// and advance(output) = input as our "can't write" invariant
// effective the gap acts as a sentinel
const size_t Capacity = Size + 1;

template <typename TraceType> class QueueListener {
public:
    virtual void record(const TraceType& entry) = 0;
    //const Backtrace& item, ThreadBucket* info = nullptr, std::uint8_t ctx_len = 0, PerfCtx::ThreadTracker::EffectiveCtx* ctx = nullptr, bool default_ctx = false

    virtual ~QueueListener() { }
};

const int COMMITTED = 1;
const int UNCOMMITTED = 0;

struct TraceHolder {
    std::atomic<int> is_committed;
    Backtrace trace;
    ThreadBucket *info;
    PerfCtx::ThreadTracker::EffectiveCtx ctx;
    std::uint8_t ctx_len;
    bool default_ctx;
};

template <typename TraceType, typename InMsg> class CircularQueue {
public:
    typedef QueueListener<TraceType> Listener;

    explicit CircularQueue(QueueListener<TraceType> &listener, std::uint32_t maxFrameSize);

    virtual ~CircularQueue();

    bool push(const InMsg& in_msg);

    bool pop();

private:
    QueueListener<TraceType> &listener_;

    std::atomic<size_t> input;
    std::atomic<size_t> output;

    TraceType buffer[Capacity];
    StackFrame *frame_buffer_[Capacity];

    size_t advance(size_t index) const;

    bool acquire_write_slot(size_t& slot);

    void mark_committed(const size_t slot);

protected:
    virtual void write(TraceType& entry, StackFrame* fb, const InMsg& in_msg) = 0;
};

struct CpuSample {
    BacktraceType type;
    ThreadBucket* info;
    BacktraceError error;
    bool default_ctx;
    union {
        struct {
            const JVMPI_CallTrace* ct;
        } java;
        struct {
            const NativeFrame* ct;
            std::uint32_t num_frames;
        } native;
    } ct;

    CpuSample(const JVMPI_CallTrace& item, ThreadBucket* info, const BacktraceError error, const bool default_ctx);
    
    CpuSample(const NativeFrame* trace, const std::uint32_t num_frames, ThreadBucket* info, const BacktraceError error, bool default_ctx);
    
private:
    CpuSample(BacktraceType type, ThreadBucket* info, const BacktraceError error, bool default_ctx);
};

extern template class CircularQueue<TraceHolder, CpuSample>;

class CpuSamplesQueue : public CircularQueue<TraceHolder, CpuSample> {
public:
    explicit CpuSamplesQueue(QueueListener<TraceHolder>& listener, std::uint32_t maxFrameSize);

    ~CpuSamplesQueue();

    // We tolerate following obnoxious push overloads (and write overloads) for performance reasons
    //      (this is already a 1-copy impl, the last thing we want is make it 2-copy, just to make it pretty).
    // Yuck! I know...
    bool push(const JVMPI_CallTrace &item, const BacktraceError error, bool default_ctx, ThreadBucket *info = nullptr);

    bool push(const NativeFrame* item, const std::uint32_t num_frames, const BacktraceError error, bool default_ctx, ThreadBucket *info = nullptr);

protected:
    void update_trace_info(TraceHolder& entry, StackFrame* fb, const BacktraceType type, const size_t slot, const std::uint32_t num_frames, ThreadBucket* info, const BacktraceError error, bool default_ctx);

    void write(TraceHolder& entry, StackFrame* fb, const CpuSample& in_msg);
};

#endif /* CIRCULAR_QUEUE_H */
