#include <thread>
#include <vector>
#include <iostream>
#include <cstdint>
#include "fixtures.hh"
#include "test.hh"
#include "../../main/cpp/profiler.hh"
#include <unordered_map>
#include "../../main/cpp/blocking_ring_buffer.hh"
#include "../../main/cpp/circular_queue.hh"
#include <memory>
#include <tuple>
#include "../../main/cpp/checksum.hh"
#include <google/protobuf/io/coded_stream.h>

namespace std {
    template <> class hash<std::tuple<std::int64_t, jint>> {
        std::hash<std::int64_t> i64_h;
        std::hash<jint> jint_h;

    public:
        std::size_t operator()(const std::tuple<std::int64_t, jint>& v) const {
            return 13 * i64_h(std::get<0>(v)) + std::get<1>(v);
        }
    };
}

typedef std::unordered_map<std::int64_t, std::tuple<std::int64_t, const char*, const char*, const char*, const char*>> FnStub;
typedef std::unordered_map<std::tuple<std::int64_t, jint>, jint> LineNoStub;

FnStub method_lookup_stub;
LineNoStub line_no_lookup_stub;

bool test_mthd_info_resolver(const jmethodID method_id, jvmtiEnv* jvmti, SiteResolver::MethodListener& listener) {
    auto t = method_lookup_stub.find(reinterpret_cast<std::int64_t>(method_id));
    if (t != method_lookup_stub.end()) {
        const auto& identity = t->second;
        listener.recordNewMethod(method_id, std::get<1>(identity), std::get<2>(identity), std::get<3>(identity), std::get<4>(identity));
        return true;
    }
    return false;
}

jint test_line_no_resolver(jint bci, jmethodID method_id, jvmtiEnv* jvmti) {
    auto t = line_no_lookup_stub.find(std::make_tuple(reinterpret_cast<int64_t>(method_id), bci));
    if (t != line_no_lookup_stub.end()) {
        return t->second;
    }
    return 0;
}

void stub(FnStub& method_lookup_stub, LineNoStub& line_no_lookup_stub, std::int64_t method_id, const char* file, const char* fqdn, const char* fn_name, const char* fn_sig) {
    method_lookup_stub.insert({method_id, std::make_tuple(method_id, file, fqdn, fn_name, fn_sig)});
    line_no_lookup_stub.insert({std::make_tuple(method_id, 10), 1});
    line_no_lookup_stub.insert({std::make_tuple(method_id, 20), 2});
    line_no_lookup_stub.insert({std::make_tuple(method_id, 30), 3});
}

struct AccumulatingRawWriter : public RawWriter {
    BlockingRingBuffer& buff;
    
    AccumulatingRawWriter(BlockingRingBuffer& _buff) : RawWriter(), buff(_buff) {}
    virtual ~AccumulatingRawWriter() {}
    
    void write_unbuffered(const std::uint8_t* data, std::uint32_t sz, std::uint32_t offset) {
        auto wrote = buff.write(data, offset, sz, false);
        if (wrote < sz) {
            throw std::runtime_error(Util::to_s("Wrote too little to byte-buff, wanted to write ", sz, " but wrote only ", wrote));
        }
    }
};

jmethodID mid(std::uint64_t id) {
    return reinterpret_cast<jmethodID>(id);
}


#define ASSERT_THREAD_INFO_IS(thd_info, thd_id, thd_name, thd_prio, thd_is_daemon, thd_tid) \
    {                                                                   \
        CHECK_EQUAL(thd_id, thd_info.thread_id());                      \
        CHECK_EQUAL(thd_name, thd_info.thread_name());                  \
        CHECK_EQUAL(thd_prio, thd_info.priority());                     \
        CHECK_EQUAL(thd_is_daemon, thd_info.is_daemon());               \
        CHECK_EQUAL(thd_tid, thd_info.tid());                           \
    }

#define ASSERT_METHOD_INFO_IS(mthd_info, mthd_id, kls_file, kls_fqdn, fn_name, fn_sig) \
    {                                                                   \
        CHECK_EQUAL(mthd_id, mthd_info.method_id());                    \
        CHECK_EQUAL(kls_file, mthd_info.file_name());                   \
        CHECK_EQUAL(kls_fqdn, mthd_info.class_fqdn());                  \
        CHECK_EQUAL(fn_name, mthd_info.method_name());                  \
        CHECK_EQUAL(fn_sig, mthd_info.signature());                     \
    }

#define ASSERT_TRACE_CTX_INFO_IS(trace_ctx, ctx_id, ctx_name, cov_pct, merge_semantics, is_gen) \
    {                                                                   \
        CHECK_EQUAL(ctx_id, trace_ctx.trace_id());                      \
        CHECK_EQUAL(ctx_name, trace_ctx.trace_name());                  \
        if (is_gen) {                                                   \
            CHECK_EQUAL(true, trace_ctx.is_generated());                \
            CHECK_EQUAL(false, trace_ctx.has_coverage_pct());           \
            CHECK_EQUAL(false, trace_ctx.has_merge());                  \
        } else {                                                        \
            CHECK_EQUAL(true, trace_ctx.has_coverage_pct());            \
            CHECK_EQUAL(true, trace_ctx.has_merge());                   \
            CHECK_EQUAL(cov_pct, trace_ctx.coverage_pct());             \
            CHECK_EQUAL(merge_semantics, trace_ctx.merge());            \
        }                                                               \
    }

typedef std::int64_t F_mid;
typedef std::int32_t F_bci;
typedef std::int32_t F_line;
std::tuple<F_mid, F_bci, F_line> fr(F_mid mid, F_bci bci, F_line line) {
    return std::make_tuple(mid, bci, line);
}

#define ASSERT_STACK_SAMPLE_IS(ss, time_offset, thd_id, frames, ctx_ids, is_snipped) \
    {                                                                   \
        CHECK_EQUAL(time_offset, ss.start_offset_micros());             \
        CHECK_EQUAL(thd_id, ss.thread_id());                            \
        CHECK_EQUAL(frames.size(), ss.frame_size());                    \
        auto i = 0;                                                     \
        for (auto it = frames.begin(); it != frames.end(); it++, i++) { \
            auto f = ss.frame(i);                                       \
            CHECK_EQUAL(std::get<0>(*it), f.method_id());               \
            CHECK_EQUAL(std::get<1>(*it), f.bci());                     \
            CHECK_EQUAL(std::get<2>(*it), f.line_no());                 \
        }                                                               \
        CHECK_EQUAL(frames.size(), i);                                  \
        CHECK_EQUAL(ctx_ids.size(), ss.trace_id_size());                \
        i = 0;                                                          \
        for (auto it = ctx_ids.begin(); it != ctx_ids.end(); it++, i++) { \
            CHECK_EQUAL(*it, ss.trace_id(i));                           \
        }                                                               \
        CHECK_EQUAL(is_snipped, ss.snipped());                          \
    }


TEST(ProfileSerializer__should_write_cpu_samples) {
    init_logger();
    BlockingRingBuffer buff(1024 * 1024);
    std::shared_ptr<RawWriter> raw_w_ptr(new AccumulatingRawWriter(buff));
    Buff pw_buff;
    ProfileWriter pw(raw_w_ptr, pw_buff);

    method_lookup_stub.clear();
    line_no_lookup_stub.clear();
    
    std::int64_t y = 1, c = 2, d = 3, e = 4, f = 5;
    stub(method_lookup_stub, line_no_lookup_stub, y, "x/Y.class", "x.Y", "fn_y", "(I)J");
    stub(method_lookup_stub, line_no_lookup_stub, c, "x/C.class", "x.C", "fn_c", "(F)I");
    stub(method_lookup_stub, line_no_lookup_stub, d, "x/D.class", "x.D", "fn_d", "(J)I");
    stub(method_lookup_stub, line_no_lookup_stub, e, "x/E.class", "x.E", "fn_e", "(J)I");
    stub(method_lookup_stub, line_no_lookup_stub, f, "x/F.class", "x.F", "fn_f", "(J)I");

    PerfCtx::Registry reg;
    auto to_parent_semantic = static_cast<std::uint8_t>(PerfCtx::MergeSemantic::to_parent);
    auto dup_semantic = static_cast<std::uint8_t>(PerfCtx::MergeSemantic::duplicate);
    auto ctx_foo = reg.find_or_bind("foo", 20, to_parent_semantic);
    auto ctx_bar = reg.find_or_bind("bar", 30, to_parent_semantic);
    auto ctx_baz = reg.find_or_bind("baz", 40, dup_semantic);
    
    ProbPct prob_pct;
    GlobalCtx::prob_pct = &prob_pct;
    GlobalCtx::ctx_reg = &reg;

    jvmtiEnv* ti = nullptr;

    SerializationFlushThresholds sft;
    TruncationThresholds tts(7);
    ProfileSerializingWriter ps(ti, pw, test_mthd_info_resolver, test_line_no_resolver, reg, sft, tts);

    CircularQueue q(ps, 10);
    
    STATIC_ARRAY(frames, JVMPI_CallFrame, 7, 7);
    JVMPI_CallTrace ct;
    ct.frames = frames;

    frames[0].method_id = mid(d);
    frames[0].lineno = 10;
    frames[1].method_id = mid(c);
    frames[1].lineno = 10;
    frames[2].method_id = mid(d);
    frames[2].lineno = 20;
    frames[3].method_id = mid(c);
    frames[3].lineno = 20;
    frames[4].method_id = mid(y);
    frames[4].lineno = 30;
    ct.num_frames = 5;

    ThreadBucket t25(25, "Thread No. 25", 5, true);
    t25.ctx_tracker.enter(ctx_foo);
    t25.ctx_tracker.enter(ctx_bar);
    q.push(ct, &t25);
    t25.ctx_tracker.exit(ctx_bar);
    t25.ctx_tracker.exit(ctx_foo);

    frames[0].method_id = mid(d);
    frames[0].lineno = 10;
    frames[1].method_id = mid(c);
    frames[1].lineno = 10;
    frames[2].method_id = mid(e);
    frames[2].lineno = 20;
    frames[3].method_id = mid(d);
    frames[3].lineno = 20;
    frames[4].method_id = mid(c);
    frames[4].lineno = 30;
    frames[5].method_id = mid(y);
    frames[5].lineno = 30;
    ct.num_frames = 6;

    ThreadBucket tmain(42, "main thread", 10, false);
    tmain.ctx_tracker.enter(ctx_bar);
    tmain.ctx_tracker.enter(ctx_baz);
    q.push(ct, &tmain);
    tmain.ctx_tracker.exit(ctx_baz);

    frames[0].method_id = mid(c);
    frames[0].lineno = 10;
    frames[1].method_id = mid(f);
    frames[1].lineno = 10;
    frames[2].method_id = mid(e);
    frames[2].lineno = 20;
    frames[3].method_id = mid(d);
    frames[3].lineno = 20;
    frames[4].method_id = mid(c);
    frames[4].lineno = 30;
    frames[5].method_id = mid(y);
    frames[5].lineno = 30;
    ct.num_frames = 6;
    q.push(ct, &tmain);
    
    tmain.ctx_tracker.exit(ctx_bar);

    CHECK(q.pop());
    CHECK(q.pop());
    CHECK(q.pop());
    CHECK(! q.pop());//because only 3 samples were pushed

    ps.flush();

    buff.readonly();

    std::shared_ptr<std::uint8_t> tmp_buff(new std::uint8_t[1024 * 1024], std::default_delete<std::uint8_t[]>());
    auto bytes_sz = buff.read(tmp_buff.get(), 0, 1024 * 1024);
    CHECK(bytes_sz > 0);

    google::protobuf::io::CodedInputStream cis(tmp_buff.get(), bytes_sz);

    std::uint32_t len;
    CHECK(cis.ReadVarint32(&len));

    auto lim = cis.PushLimit(len);

    recording::Wse wse;
    CHECK(wse.ParseFromCodedStream(&cis));

    cis.PopLimit(lim);

    auto pos = cis.CurrentPosition();

    std::uint32_t csum;
    CHECK(cis.ReadVarint32(&csum));

    Checksum c_calc;
    auto computed_csum = c_calc.chksum(tmp_buff.get(), pos);

    CHECK_EQUAL(computed_csum, csum);

    CHECK_EQUAL(recording::WorkType::cpu_sample_work, wse.w_type());
    auto idx_data = wse.indexed_data();
    CHECK_EQUAL(0, idx_data.monitor_info_size());
    
    CHECK_EQUAL(2, idx_data.thread_info_size());
    ASSERT_THREAD_INFO_IS(idx_data.thread_info(0), 3, "Thread No. 25", 5, true, 25);
    ASSERT_THREAD_INFO_IS(idx_data.thread_info(1), 4, "main thread", 10, false, 42);

    CHECK_EQUAL(5, idx_data.method_info_size());
    ASSERT_METHOD_INFO_IS(idx_data.method_info(0), d, "x/D.class", "x.D", "fn_d", "(J)I");
    ASSERT_METHOD_INFO_IS(idx_data.method_info(1), c, "x/C.class", "x.C", "fn_c", "(F)I");
    ASSERT_METHOD_INFO_IS(idx_data.method_info(2), y, "x/Y.class", "x.Y", "fn_y", "(I)J");
    ASSERT_METHOD_INFO_IS(idx_data.method_info(3), e, "x/E.class", "x.E", "fn_e", "(J)I");
    ASSERT_METHOD_INFO_IS(idx_data.method_info(4), f, "x/F.class", "x.F", "fn_f", "(J)I");

    CHECK_EQUAL(3, idx_data.trace_ctx_size());
    ASSERT_TRACE_CTX_INFO_IS(idx_data.trace_ctx(0), 5, "foo", 20, 0, false);
    ASSERT_TRACE_CTX_INFO_IS(idx_data.trace_ctx(1), 6, "bar", 30, 0, false);
    ASSERT_TRACE_CTX_INFO_IS(idx_data.trace_ctx(2), 7, "baz", 40, 4, false);

    auto cse = wse.cpu_sample_entry();

    CHECK_EQUAL(3, cse.stack_sample_size());
    auto s1 = {fr(d, 10, 1), fr(c, 10, 1), fr(d, 20, 2), fr(c, 20, 2), fr(y, 30, 3)};
    auto s1_ctxs = {5};
    ASSERT_STACK_SAMPLE_IS(cse.stack_sample(0), 0, 3, s1, s1_ctxs, false); //TODO: fix this to actually record time-offset, right now we are using zero
    auto s2 = {fr(d, 10, 1), fr(c, 10, 1), fr(e, 20, 2), fr(d, 20, 2), fr(c, 30, 3), fr(y, 30, 3)};
    auto s2_ctxs = {6, 7};
    ASSERT_STACK_SAMPLE_IS(cse.stack_sample(1), 0, 4, s2, s2_ctxs, false);
    auto s3 = {fr(c, 10, 1), fr(f, 10, 1), fr(e, 20, 2), fr(d, 20, 2), fr(c, 30, 3), fr(y, 30, 3)};
    auto s3_ctxs = {6};
    ASSERT_STACK_SAMPLE_IS(cse.stack_sample(2), 0, 4, s3, s3_ctxs, false);
}

TEST(ProfileSerializer__should_write_cpu_samples__with_scoped_ctx) {
    init_logger();
    BlockingRingBuffer buff(1024 * 1024);
    std::shared_ptr<RawWriter> raw_w_ptr(new AccumulatingRawWriter(buff));
    Buff pw_buff;
    ProfileWriter pw(raw_w_ptr, pw_buff);

    method_lookup_stub.clear();
    line_no_lookup_stub.clear();

    std::int64_t y = 1, c = 2;
    stub(method_lookup_stub, line_no_lookup_stub, y, "x/Y.class", "x.Y", "fn_y", "(I)J");
    stub(method_lookup_stub, line_no_lookup_stub, c, "x/C.class", "x.C", "fn_c", "(F)I");

    PerfCtx::Registry reg;
    auto to_parent_semantic = static_cast<std::uint8_t>(PerfCtx::MergeSemantic::to_parent);
    auto scoped_semantic = static_cast<std::uint8_t>(PerfCtx::MergeSemantic::scoped);
    auto ctx_foo = reg.find_or_bind("foo", 20, to_parent_semantic);
    auto ctx_bar = reg.find_or_bind("bar", 30, scoped_semantic);
    
    ProbPct prob_pct;
    GlobalCtx::prob_pct = &prob_pct;
    GlobalCtx::ctx_reg = &reg;

    jvmtiEnv* ti = nullptr;

    SerializationFlushThresholds sft;
    TruncationThresholds tts(7);
    ProfileSerializingWriter ps(ti, pw, test_mthd_info_resolver, test_line_no_resolver, reg, sft, tts);

    CircularQueue q(ps, 10);
    
    //const JVMPI_CallTrace item, ThreadBucket *info = nullptr, std::uint8_t ctx_len = 0, PerfCtx::ThreadTracker::EffectiveCtx* ctx = nullptr

    STATIC_ARRAY(frames, JVMPI_CallFrame, 7, 7);
    JVMPI_CallTrace ct;
    ct.frames = frames;

    frames[0].method_id = mid(c);
    frames[0].lineno = 10;
    frames[1].method_id = mid(y);
    frames[1].lineno = 20;
    ct.num_frames = 2;

    ThreadBucket t25(25, "some thread", 8, false);
    t25.ctx_tracker.enter(ctx_foo);
    t25.ctx_tracker.enter(ctx_bar);
    q.push(ct, &t25);
    t25.ctx_tracker.exit(ctx_bar);
    t25.ctx_tracker.exit(ctx_foo);

    frames[0].method_id = mid(y);
    frames[0].lineno = 10;
    frames[1].method_id = mid(c);
    frames[1].lineno = 20;
    ct.num_frames = 2;

    t25.ctx_tracker.enter(ctx_bar);
    t25.ctx_tracker.enter(ctx_foo);
    q.push(ct, &t25);
    t25.ctx_tracker.exit(ctx_foo);
    t25.ctx_tracker.exit(ctx_bar);

    CHECK(q.pop());
    CHECK(q.pop());
    CHECK(! q.pop());//because only 2 samples were pushed

    ps.flush();

    buff.readonly();

    std::shared_ptr<std::uint8_t> tmp_buff(new std::uint8_t[1024 * 1024]);
    auto bytes_sz = buff.read(tmp_buff.get(), 0, 1024 * 1024);
    CHECK(bytes_sz > 0);

    google::protobuf::io::CodedInputStream cis(tmp_buff.get(), bytes_sz);

    std::uint32_t len;
    CHECK(cis.ReadVarint32(&len));

    auto lim = cis.PushLimit(len);

    recording::Wse wse;
    CHECK(wse.ParseFromCodedStream(&cis));

    cis.PopLimit(lim);

    CHECK_EQUAL(recording::WorkType::cpu_sample_work, wse.w_type());
    auto idx_data = wse.indexed_data();
    CHECK_EQUAL(0, idx_data.monitor_info_size());
    
    CHECK_EQUAL(1, idx_data.thread_info_size());
    ASSERT_THREAD_INFO_IS(idx_data.thread_info(0), 3, "some thread", 8, false, 25);

    CHECK_EQUAL(2, idx_data.method_info_size());
    ASSERT_METHOD_INFO_IS(idx_data.method_info(0), c, "x/C.class", "x.C", "fn_c", "(F)I");
    ASSERT_METHOD_INFO_IS(idx_data.method_info(1), y, "x/Y.class", "x.Y", "fn_y", "(I)J");

    CHECK_EQUAL(2, idx_data.trace_ctx_size());
    ASSERT_TRACE_CTX_INFO_IS(idx_data.trace_ctx(0), 5, "foo > bar", 0, 0, true);
    ASSERT_TRACE_CTX_INFO_IS(idx_data.trace_ctx(1), 6, "bar", 30, 1, false);

    auto cse = wse.cpu_sample_entry();

    CHECK_EQUAL(2, cse.stack_sample_size());
    auto s1 = {fr(c, 10, 1), fr(y, 20, 2)};
    auto s1_ctxs = {5};
    ASSERT_STACK_SAMPLE_IS(cse.stack_sample(0), 0, 3, s1, s1_ctxs, false); //TODO: fix this to actually record time-offset, right now we are using zero
    auto s2 = {fr(y, 10, 1), fr(c, 20, 2)};
    auto s2_ctxs = {6};
    ASSERT_STACK_SAMPLE_IS(cse.stack_sample(1), 0, 3, s2, s2_ctxs, false);
}


TEST(ProfileSerializer__should_auto_flush__at_buffering_threshold) {
    init_logger();
    BlockingRingBuffer buff(1024 * 1024);
    std::shared_ptr<RawWriter> raw_w_ptr(new AccumulatingRawWriter(buff));
    Buff pw_buff;
    ProfileWriter pw(raw_w_ptr, pw_buff);

    method_lookup_stub.clear();
    line_no_lookup_stub.clear();

    std::int64_t y = 1, c = 2;
    stub(method_lookup_stub, line_no_lookup_stub, y, "x/Y.class", "x.Y", "fn_y", "(I)J");
    stub(method_lookup_stub, line_no_lookup_stub, c, "x/C.class", "x.C", "fn_c", "(F)I");

    PerfCtx::Registry reg;
    auto to_parent_semantic = static_cast<std::uint8_t>(PerfCtx::MergeSemantic::to_parent);
    auto ctx_foo = reg.find_or_bind("foo", 20, to_parent_semantic);
    
    ProbPct prob_pct;
    GlobalCtx::prob_pct = &prob_pct;
    GlobalCtx::ctx_reg = &reg;

    jvmtiEnv* ti = nullptr;

    SerializationFlushThresholds sft;
    sft.cpu_samples = 10;
    TruncationThresholds tts(7);
    ProfileSerializingWriter ps(ti, pw, test_mthd_info_resolver, test_line_no_resolver, reg, sft, tts);

    CircularQueue q(ps, 10);
    
    STATIC_ARRAY(frames, JVMPI_CallFrame, 7, 7);
    JVMPI_CallTrace ct;
    ct.frames = frames;

    frames[0].method_id = mid(c);
    frames[0].lineno = 10;
    frames[1].method_id = mid(y);
    frames[1].lineno = 20;
    ct.num_frames = 2;

    ThreadBucket t25(25, "some thread", 8, false);
    t25.ctx_tracker.enter(ctx_foo);
    for (auto i = 0; i < 10; i++) {
        q.push(ct, &t25);
        CHECK(q.pop());

        std::uint8_t tmp;
        CHECK_EQUAL(0, buff.read(&tmp, 0, 1, false));
    }
    q.push(ct, &t25);
    t25.ctx_tracker.exit(ctx_foo);
    CHECK(q.pop());

    buff.readonly();

    const std::size_t one_meg = 1024 * 1024;
    std::shared_ptr<std::uint8_t> tmp_buff(new std::uint8_t[one_meg]);
    auto bytes_sz = buff.read(tmp_buff.get(), 0, one_meg);
    CHECK(bytes_sz > 0);
    CHECK(bytes_sz < one_meg);

    google::protobuf::io::CodedInputStream cis(tmp_buff.get(), bytes_sz);

    std::uint32_t len;
    CHECK(cis.ReadVarint32(&len));

    auto lim = cis.PushLimit(len);

    recording::Wse wse;
    CHECK(wse.ParseFromCodedStream(&cis));

    cis.PopLimit(lim);

    CHECK_EQUAL(recording::WorkType::cpu_sample_work, wse.w_type());
    auto idx_data = wse.indexed_data();
    CHECK_EQUAL(0, idx_data.monitor_info_size());
    
    CHECK_EQUAL(1, idx_data.thread_info_size());
    ASSERT_THREAD_INFO_IS(idx_data.thread_info(0), 3, "some thread", 8, false, 25);

    CHECK_EQUAL(2, idx_data.method_info_size());
    ASSERT_METHOD_INFO_IS(idx_data.method_info(0), c, "x/C.class", "x.C", "fn_c", "(F)I");
    ASSERT_METHOD_INFO_IS(idx_data.method_info(1), y, "x/Y.class", "x.Y", "fn_y", "(I)J");

    CHECK_EQUAL(1, idx_data.trace_ctx_size());
    ASSERT_TRACE_CTX_INFO_IS(idx_data.trace_ctx(0), 5, "foo", 20, 0, false);

    auto cse = wse.cpu_sample_entry();
    CHECK_EQUAL(10, cse.stack_sample_size());

    auto s1 = {fr(c, 10, 1), fr(y, 20, 2)};
    auto s1_ctxs = {5};
    for (auto i = 0; i < 10; i++) {
        ASSERT_STACK_SAMPLE_IS(cse.stack_sample(i), 0, 3, s1, s1_ctxs, false);
    }
}

TEST(ProfileSerializer__should_auto_flush_correctly__after_first_flush___and_should_incrementally_push___index_data_mapping) {
    init_logger();
    BlockingRingBuffer buff(1024 * 1024);
    std::shared_ptr<RawWriter> raw_w_ptr(new AccumulatingRawWriter(buff));
    Buff pw_buff;
    ProfileWriter pw(raw_w_ptr, pw_buff);

    method_lookup_stub.clear();
    line_no_lookup_stub.clear();

    std::int64_t y = 1, c = 2, d = 3;
    stub(method_lookup_stub, line_no_lookup_stub, y, "x/Y.class", "x.Y", "fn_y", "(I)J");
    stub(method_lookup_stub, line_no_lookup_stub, c, "x/C.class", "x.C", "fn_c", "(F)I");
    stub(method_lookup_stub, line_no_lookup_stub, d, "x/D.class", "x.D", "fn_d", "(J)I");

    PerfCtx::Registry reg;
    auto to_parent_semantic = static_cast<std::uint8_t>(PerfCtx::MergeSemantic::to_parent);
    auto ctx_foo = reg.find_or_bind("foo", 20, to_parent_semantic);
    auto ctx_bar = reg.find_or_bind("bar", 30, to_parent_semantic);
    
    ProbPct prob_pct;
    GlobalCtx::prob_pct = &prob_pct;
    GlobalCtx::ctx_reg = &reg;

    jvmtiEnv* ti = nullptr;

    SerializationFlushThresholds sft;
    sft.cpu_samples = 10;
    TruncationThresholds tts(7);
    ProfileSerializingWriter ps(ti, pw, test_mthd_info_resolver, test_line_no_resolver, reg, sft, tts);

    CircularQueue q(ps, 10);
    
    STATIC_ARRAY(frames0, JVMPI_CallFrame, 7, 7);
    JVMPI_CallTrace ct0;

    frames0[0].method_id = mid(c);
    frames0[0].lineno = 10;
    frames0[1].method_id = mid(y);
    frames0[1].lineno = 20;
    ct0.frames = frames0;
    ct0.num_frames = 2;

    STATIC_ARRAY(frames1, JVMPI_CallFrame, 7, 7);
    JVMPI_CallTrace ct1;
    frames1[0].method_id = mid(d);
    frames1[0].lineno = 10;
    frames1[1].method_id = mid(y);
    frames1[1].lineno = 20;
    ct1.frames = frames1;
    ct1.num_frames = 2;

    ThreadBucket t25(25, "some thread", 8, false);
    ThreadBucket t10(10, "some other thread", 6, true);
    t25.ctx_tracker.enter(ctx_foo);
    t10.ctx_tracker.enter(ctx_bar);
    for (auto i = 0; i < 26; i++) {
        if (i == 15) {
            ps.flush();//check manual flush interleving
        }
        if (i < 15) {
            q.push(ct0, &t25);
        } else {
            q.push(ct1, &t10);
        }
        CHECK(q.pop());
    }
    t25.ctx_tracker.exit(ctx_foo);
    t10.ctx_tracker.exit(ctx_bar);

    buff.readonly();

    const std::size_t one_meg = 1024 * 1024;
    std::shared_ptr<std::uint8_t> tmp_buff(new std::uint8_t[one_meg]);
    auto bytes_sz = buff.read(tmp_buff.get(), 0, one_meg);
    CHECK(bytes_sz > 0);
    CHECK(bytes_sz < one_meg);

    google::protobuf::io::CodedInputStream cis(tmp_buff.get(), bytes_sz);

    std::uint32_t len;
    std::uint32_t csum;
    Checksum c_calc;
    recording::Wse wse0, wse1, wse2;

    CHECK(cis.ReadVarint32(&len));
    auto lim = cis.PushLimit(len);
    CHECK(wse0.ParseFromCodedStream(&cis));
    cis.PopLimit(lim);
    auto pos = cis.CurrentPosition();
    CHECK(cis.ReadVarint32(&csum));
    auto computed_csum = c_calc.chksum(tmp_buff.get(), pos);
    CHECK_EQUAL(computed_csum, csum);
    auto next_record_start = cis.CurrentPosition();

    CHECK(cis.ReadVarint32(&len));
    lim = cis.PushLimit(len);
    CHECK(wse1.ParseFromCodedStream(&cis));
    cis.PopLimit(lim);
    pos = cis.CurrentPosition();
    CHECK(cis.ReadVarint32(&csum));
    c_calc.reset();
    computed_csum = c_calc.chksum(tmp_buff.get() + next_record_start, pos - next_record_start);
    CHECK_EQUAL(computed_csum, csum);
    next_record_start = cis.CurrentPosition();

    CHECK(cis.ReadVarint32(&len));
    lim = cis.PushLimit(len);
    CHECK(wse2.ParseFromCodedStream(&cis));
    cis.PopLimit(lim);
    pos = cis.CurrentPosition();
    CHECK(cis.ReadVarint32(&csum));
    c_calc.reset();
    computed_csum = c_calc.chksum(tmp_buff.get() + next_record_start, pos - next_record_start);
    CHECK_EQUAL(computed_csum, csum);

    CHECK_EQUAL(recording::WorkType::cpu_sample_work, wse0.w_type());
    CHECK_EQUAL(recording::WorkType::cpu_sample_work, wse1.w_type());
    CHECK_EQUAL(recording::WorkType::cpu_sample_work, wse2.w_type());

    auto idx_data0 = wse0.indexed_data();
    CHECK_EQUAL(0, idx_data0.monitor_info_size());
    
    CHECK_EQUAL(1, idx_data0.thread_info_size());
    ASSERT_THREAD_INFO_IS(idx_data0.thread_info(0), 3, "some thread", 8, false, 25);

    CHECK_EQUAL(2, idx_data0.method_info_size());
    ASSERT_METHOD_INFO_IS(idx_data0.method_info(0), c, "x/C.class", "x.C", "fn_c", "(F)I");
    ASSERT_METHOD_INFO_IS(idx_data0.method_info(1), y, "x/Y.class", "x.Y", "fn_y", "(I)J");

    CHECK_EQUAL(1, idx_data0.trace_ctx_size());
    ASSERT_TRACE_CTX_INFO_IS(idx_data0.trace_ctx(0), 5, "foo", 20, 0, false);

    auto cse0 = wse0.cpu_sample_entry();
    CHECK_EQUAL(10, cse0.stack_sample_size());

    auto s0 = {fr(c, 10, 1), fr(y, 20, 2)};
    auto s0_ctxs = {5};
    for (auto i = 0; i < 10; i++) {
        ASSERT_STACK_SAMPLE_IS(cse0.stack_sample(i), 0, 3, s0, s0_ctxs, false);
    }

    auto idx_data1 = wse1.indexed_data();
    CHECK_EQUAL(0, idx_data1.monitor_info_size());
    CHECK_EQUAL(0, idx_data1.thread_info_size());
    CHECK_EQUAL(0, idx_data1.method_info_size());
    CHECK_EQUAL(0, idx_data1.trace_ctx_size());
    auto cse1 = wse1.cpu_sample_entry();

    CHECK_EQUAL(5, cse1.stack_sample_size());
    for (auto i = 0; i < 5; i++) {
        ASSERT_STACK_SAMPLE_IS(cse1.stack_sample(i), 0, 3, s0, s0_ctxs, false);
    }

    auto idx_data2 = wse2.indexed_data();
    CHECK_EQUAL(0, idx_data2.monitor_info_size());
    
    CHECK_EQUAL(1, idx_data2.thread_info_size());
    ASSERT_THREAD_INFO_IS(idx_data2.thread_info(0), 4, "some other thread", 6, true, 10);

    CHECK_EQUAL(1, idx_data2.method_info_size());
    ASSERT_METHOD_INFO_IS(idx_data2.method_info(0), d, "x/D.class", "x.D", "fn_d", "(J)I");

    CHECK_EQUAL(1, idx_data2.trace_ctx_size());
    ASSERT_TRACE_CTX_INFO_IS(idx_data2.trace_ctx(0), 6, "bar", 30, 0, false);

    auto cse2 = wse2.cpu_sample_entry();
    CHECK_EQUAL(10, cse2.stack_sample_size());

    auto s1 = {fr(d, 10, 1), fr(y, 20, 2)};
    auto s1_ctxs = {6};
    for (auto i = 0; i < 10; i++) {
        ASSERT_STACK_SAMPLE_IS(cse2.stack_sample(i), 0, 4, s1, s1_ctxs, false);
    }
}

TEST(ProfileSerializer__should_write_cpu_samples__with_forte_error) {
    init_logger();
    BlockingRingBuffer buff(1024 * 1024);
    std::shared_ptr<RawWriter> raw_w_ptr(new AccumulatingRawWriter(buff));
    Buff pw_buff;
    ProfileWriter pw(raw_w_ptr, pw_buff);

    method_lookup_stub.clear();
    line_no_lookup_stub.clear();

    PerfCtx::Registry reg;
    
    ProbPct prob_pct;
    GlobalCtx::prob_pct = &prob_pct;
    GlobalCtx::ctx_reg = &reg;

    jvmtiEnv* ti = nullptr;

    SerializationFlushThresholds sft;
    TruncationThresholds tts(7);
    ProfileSerializingWriter ps(ti, pw, test_mthd_info_resolver, test_line_no_resolver, reg, sft, tts);

    CircularQueue q(ps, 10);
    
    //const JVMPI_CallTrace item, ThreadBucket *info = nullptr, std::uint8_t ctx_len = 0, PerfCtx::ThreadTracker::EffectiveCtx* ctx = nullptr

    STATIC_ARRAY(frames, JVMPI_CallFrame, 7, 7);
    JVMPI_CallTrace ct;
    ct.frames = frames;

    for (auto i = 0; i < 11; i++) {
        ct.num_frames = -1 * i;
        q.push(ct, nullptr);
        CHECK(q.pop());
    }

    ps.flush();

    buff.readonly();

    std::shared_ptr<std::uint8_t> tmp_buff(new std::uint8_t[1024 * 1024]);
    auto bytes_sz = buff.read(tmp_buff.get(), 0, 1024 * 1024);
    CHECK(bytes_sz > 0);

    google::protobuf::io::CodedInputStream cis(tmp_buff.get(), bytes_sz);

    std::uint32_t len;
    CHECK(cis.ReadVarint32(&len));

    auto lim = cis.PushLimit(len);

    recording::Wse wse;
    CHECK(wse.ParseFromCodedStream(&cis));

    cis.PopLimit(lim);

    CHECK_EQUAL(recording::WorkType::cpu_sample_work, wse.w_type());
    auto idx_data = wse.indexed_data();
    CHECK_EQUAL(0, idx_data.monitor_info_size());
    CHECK_EQUAL(0, idx_data.thread_info_size());
    CHECK_EQUAL(0, idx_data.method_info_size());
    CHECK_EQUAL(0, idx_data.trace_ctx_size());

    auto cse = wse.cpu_sample_entry();

    CHECK_EQUAL(11, cse.stack_sample_size());
    for (auto i = 0; i < 11; i++) {
        auto ss = cse.stack_sample(i);
        CHECK_EQUAL(false, ss.has_thread_id());
        CHECK_EQUAL(0, ss.frame_size());
        CHECK_EQUAL(0, ss.trace_id_size());
        CHECK_EQUAL(static_cast<recording::StackSample::Error>(i), ss.error());
    }
}

TEST(ProfileSerializer__should_snip_short__very_long_cpu_sample_backtraces) {
    init_logger();
    BlockingRingBuffer buff(1024 * 1024);
    std::shared_ptr<RawWriter> raw_w_ptr(new AccumulatingRawWriter(buff));
    Buff pw_buff;
    ProfileWriter pw(raw_w_ptr, pw_buff);

    method_lookup_stub.clear();
    line_no_lookup_stub.clear();
    
    std::int64_t y = 1, c = 2, d = 3, e = 4, f = 5;
    stub(method_lookup_stub, line_no_lookup_stub, y, "x/Y.class", "x.Y", "fn_y", "(I)J");
    stub(method_lookup_stub, line_no_lookup_stub, c, "x/C.class", "x.C", "fn_c", "(F)I");
    stub(method_lookup_stub, line_no_lookup_stub, d, "x/D.class", "x.D", "fn_d", "(J)I");
    stub(method_lookup_stub, line_no_lookup_stub, e, "x/E.class", "x.E", "fn_e", "(J)I");
    stub(method_lookup_stub, line_no_lookup_stub, f, "x/F.class", "x.F", "fn_f", "(J)I");

    PerfCtx::Registry reg;
    auto to_parent_semantic = static_cast<std::uint8_t>(PerfCtx::MergeSemantic::to_parent);
    auto ctx_foo = reg.find_or_bind("foo", 20, to_parent_semantic);
    
    ProbPct prob_pct;
    GlobalCtx::prob_pct = &prob_pct;
    GlobalCtx::ctx_reg = &reg;

    jvmtiEnv* ti = nullptr;

    SerializationFlushThresholds sft;
    TruncationThresholds tts(4);
    ProfileSerializingWriter ps(ti, pw, test_mthd_info_resolver, test_line_no_resolver, reg, sft, tts);

    CircularQueue q(ps, 10);
    
    STATIC_ARRAY(frames, JVMPI_CallFrame, 7, 7);
    JVMPI_CallTrace ct;
    ct.frames = frames;

    frames[0].method_id = mid(d);
    frames[0].lineno = 10;
    frames[1].method_id = mid(c);
    frames[1].lineno = 10;
    frames[2].method_id = mid(d);
    frames[2].lineno = 20;
    frames[3].method_id = mid(c);
    frames[3].lineno = 20;
    frames[4].method_id = mid(y);
    frames[4].lineno = 30;
    ct.num_frames = 5;

    ThreadBucket t25(25, "Thread No. 25", 5, true);
    t25.ctx_tracker.enter(ctx_foo);
    q.push(ct, &t25);
    t25.ctx_tracker.exit(ctx_foo);

    CHECK(q.pop());
    CHECK(! q.pop());//because only 1 sample was pushed

    ps.flush();

    buff.readonly();

    std::shared_ptr<std::uint8_t> tmp_buff(new std::uint8_t[1024 * 1024], std::default_delete<std::uint8_t[]>());
    auto bytes_sz = buff.read(tmp_buff.get(), 0, 1024 * 1024);
    CHECK(bytes_sz > 0);

    google::protobuf::io::CodedInputStream cis(tmp_buff.get(), bytes_sz);

    std::uint32_t len;
    CHECK(cis.ReadVarint32(&len));

    auto lim = cis.PushLimit(len);

    recording::Wse wse;
    CHECK(wse.ParseFromCodedStream(&cis));

    cis.PopLimit(lim);

    auto pos = cis.CurrentPosition();

    std::uint32_t csum;
    CHECK(cis.ReadVarint32(&csum));

    Checksum c_calc;
    auto computed_csum = c_calc.chksum(tmp_buff.get(), pos);

    CHECK_EQUAL(computed_csum, csum);

    CHECK_EQUAL(recording::WorkType::cpu_sample_work, wse.w_type());
    auto idx_data = wse.indexed_data();
    CHECK_EQUAL(0, idx_data.monitor_info_size());
    
    CHECK_EQUAL(1, idx_data.thread_info_size());
    ASSERT_THREAD_INFO_IS(idx_data.thread_info(0), 3, "Thread No. 25", 5, true, 25);

    CHECK_EQUAL(2, idx_data.method_info_size());
    ASSERT_METHOD_INFO_IS(idx_data.method_info(0), d, "x/D.class", "x.D", "fn_d", "(J)I");
    ASSERT_METHOD_INFO_IS(idx_data.method_info(1), c, "x/C.class", "x.C", "fn_c", "(F)I");

    CHECK_EQUAL(1, idx_data.trace_ctx_size());
    ASSERT_TRACE_CTX_INFO_IS(idx_data.trace_ctx(0), 5, "foo", 20, 0, false);

    auto cse = wse.cpu_sample_entry();

    CHECK_EQUAL(1, cse.stack_sample_size());
    auto s1 = {fr(d, 10, 1), fr(c, 10, 1), fr(d, 20, 2), fr(c, 20, 2)};
    auto s1_ctxs = {5};
    ASSERT_STACK_SAMPLE_IS(cse.stack_sample(0), 0, 3, s1, s1_ctxs, true); //TODO: fix this to actually record time-offset, right now we are using zero
}
