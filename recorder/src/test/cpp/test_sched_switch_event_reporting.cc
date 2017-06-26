#include <thread>
#include <vector>
#include <iostream>
#include <cstdint>
#include <chrono>
#include <ftrace/proto.hh>
#include "test.hh"
#include <ftrace/tracer.hh>

struct Event {
    ftrace::v_curr::PktType type;
    union {
        ftrace::v_curr::payload::SchedSwitch sched_switch;
        ftrace::v_curr::payload::SchedWakeup sched_wakeup;
    } evt;
    void* ctx;
};

class TestListener : public ftrace::Tracer::Listener {
public:
    TestListener(std::vector<Event>& _events) : events(_events) {}

    virtual ~TestListener() {}

    virtual void unicast(pid_t dest_pid, void* ctx, ftrace::v_curr::PktType pkt_type, const std::uint8_t* payload, ftrace::v_curr::PayloadLen payload_len) {
        assert((pkt_type == ftrace::v_curr::PktType::sched_switch) ||
               (pkt_type == ftrace::v_curr::PktType::sched_wakeup));
        Event e;
        e.type = pkt_type;
        if (pkt_type == ftrace::v_curr::PktType::sched_switch) {
            assert(payload_len == sizeof(ftrace::v_curr::payload::SchedSwitch));
            e.evt.sched_switch = *reinterpret_cast<const ftrace::v_curr::payload::SchedSwitch*>(payload);
        } else {
            assert(pkt_type == ftrace::v_curr::PktType::sched_wakeup);
            e.evt.sched_wakeup = *reinterpret_cast<const ftrace::v_curr::payload::SchedWakeup*>(payload);
        }
        e.ctx = ctx;

        events.push_back(e);
    }

    virtual void multicast(ftrace::v_curr::PktType pkt_type, const std::uint8_t* payload, ftrace::v_curr::PayloadLen payload_len) {

    }

private:
    std::vector<Event>& events;
};

TEST(SchedSwitch_does_not_deliver_unrelated_events) {
    TestEnv _;

    std::vector<Event> reported;
    TestListener l(reported);
    ftrace::Tracer::Tracees tracees;
    ftrace::Tracer::SwitchTrackingEventHandler steh(l, tracees);
    
    ftrace::event::CommonFields cf1 {20, 0, 1, 1020};
    ftrace::event::SchedSwitch sw1 {"1_0_2_0", 1020, 120, 0, "some_other", 2030, 119};
    steh.handle(0, 10, &cf1, &sw1);

    CHECK_EQUAL(0, reported.size());
}

TEST(SchedWakeup_does_not_deliver_unrelated_events) {
    TestEnv _;

    std::vector<Event> reported;
    TestListener l(reported);
    ftrace::Tracer::Tracees tracees;
    ftrace::Tracer::SwitchTrackingEventHandler steh(l, tracees);
    
    ftrace::event::CommonFields cf1 {20, 0, 1, 1020};
    ftrace::event::SchedWakeup sw1 {"1_0_2_0", 1020, 120, 1, 3};
    steh.handle(0, 10, &cf1, &sw1);

    CHECK_EQUAL(0, reported.size());
}

TEST(SchedSwitch_Event_without_syscall) {
    TestEnv _;

    std::vector<Event> reported;
    TestListener l(reported);
    ftrace::Tracer::Tracees tracees;
    ftrace::Tracer::SwitchTrackingEventHandler steh(l, tracees);
    tracees[1020] = reinterpret_cast<void*>(&tracees);
    
    ftrace::event::CommonFields cf1 {20, 0, 1, 1020};
    ftrace::event::SchedSwitch sw1 {"1_0_2_0", 1020, 120, 0, "some_other", 2030, 119};
    steh.handle(0, 10, &cf1, &sw1);
    
    CHECK_EQUAL(1, reported.size());
    CHECK(ftrace::v_curr::PktType::sched_switch == reported[0].type);
    auto& sw = reported[0].evt.sched_switch;
    ftrace::v_curr::payload::SchedSwitch expected {10, 1020, 2030, -1, 0, false};
    CHECK_EQUAL(expected, sw);
    CHECK_EQUAL(reinterpret_cast<void*>(&tracees), reported[0].ctx);
}

TEST(SchedSwitch_Event_after_unparied_syscall_exit) { //timing issue, we started profiling after syscall-dispatch, but before return -jj
    TestEnv _;

    std::vector<Event> reported;
    TestListener l(reported);
    ftrace::Tracer::Tracees tracees;
    ftrace::Tracer::SwitchTrackingEventHandler steh(l, tracees);
    tracees[1020] = reinterpret_cast<void*>(&tracees);
    
    ftrace::event::CommonFields cf1 {20, 0, 1, 1020};
    ftrace::event::SyscallExit read_out{0};
    steh.handle(1, 7, &cf1, &read_out);
    ftrace::event::SchedSwitch sw1 {"1_0_2_0", 1020, 120, 0, "some_other", 2030, 119};
    steh.handle(0, 10, &cf1, &sw1);
    
    CHECK_EQUAL(1, reported.size());
    CHECK(ftrace::v_curr::PktType::sched_switch == reported[0].type);
    auto& sw = reported[0].evt.sched_switch;
    ftrace::v_curr::payload::SchedSwitch expected {10, 1020, 2030, -1, 0, false};
    CHECK_EQUAL(expected, sw);
    CHECK_EQUAL(reinterpret_cast<void*>(&tracees), reported[0].ctx);
}


TEST(SchedSwitch_Event_during_syscall) {
    TestEnv _;

    std::vector<Event> reported;
    TestListener l(reported);
    ftrace::Tracer::Tracees tracees;
    ftrace::Tracer::SwitchTrackingEventHandler steh(l, tracees);
    tracees[1020] = reinterpret_cast<void*>(&tracees);

    ftrace::event::CommonFields cf1 {20, 0, 1, 1020};
    ftrace::event::SyscallEntry read_in{0};
    steh.handle(1, 7, &cf1, &read_in);
    ftrace::event::SchedSwitch sw1 {"1_0_2_0", 1020, 120, 0, "some_other", 2030, 119};
    steh.handle(1, 10, &cf1, &sw1);
    ftrace::event::SyscallExit read_out{0};
    steh.handle(1, 12, &cf1, &read_out);
    
    CHECK_EQUAL(1, reported.size());
    CHECK(ftrace::v_curr::PktType::sched_switch == reported[0].type);
    auto& sw = reported[0].evt.sched_switch;
    ftrace::v_curr::payload::SchedSwitch expected {10, 1020, 2030, 0, 1, false};
    CHECK_EQUAL(expected, sw);
    CHECK_EQUAL(reinterpret_cast<void*>(&tracees), reported[0].ctx);
}

TEST(SchedSwitch_Event_after_syscall) {
    TestEnv _;

    std::vector<Event> reported;
    TestListener l(reported);
    ftrace::Tracer::Tracees tracees;
    ftrace::Tracer::SwitchTrackingEventHandler steh(l, tracees);
    tracees[1020] = reinterpret_cast<void*>(&tracees);

    ftrace::event::CommonFields cf1 {20, 0, 1, 1020};
    ftrace::event::SyscallEntry read_in{0};
    steh.handle(2, 7, &cf1, &read_in);
    ftrace::event::SyscallExit read_out{0};
    steh.handle(2, 10, &cf1, &read_out);
    ftrace::event::SchedSwitch sw1 {"1_0_2_0", 1020, 120, 0, "some_other", 2030, 119};
    steh.handle(2, 12, &cf1, &sw1);
    
    CHECK_EQUAL(1, reported.size());
    CHECK(ftrace::v_curr::PktType::sched_switch == reported[0].type);
    auto& sw = reported[0].evt.sched_switch;
    ftrace::v_curr::payload::SchedSwitch expected {12, 1020, 2030, -1, 2, false};
    CHECK_EQUAL(expected, sw);
    CHECK_EQUAL(reinterpret_cast<void*>(&tracees), reported[0].ctx);
}

TEST(Voluntary_SchedSwitch_Event_without_syscall) { // Don't make faces. This can happen due to missed events. -jj
    TestEnv _;

    std::vector<Event> reported;
    TestListener l(reported);
    ftrace::Tracer::Tracees tracees;
    ftrace::Tracer::SwitchTrackingEventHandler steh(l, tracees);
    tracees[500] = reinterpret_cast<void*>(&tracees);

    ftrace::event::CommonFields cf1 {20, 0, 1, 500};
    ftrace::event::SchedSwitch sw1 {"5_0_0", 500, 119, 1, "2_0_0", 200, 120};
    steh.handle(2, 12, &cf1, &sw1);
    
    CHECK_EQUAL(1, reported.size());
    CHECK(ftrace::v_curr::PktType::sched_switch == reported[0].type);
    auto& sw = reported[0].evt.sched_switch;
    ftrace::v_curr::payload::SchedSwitch expected {12, 500, 200, -1, 2, true};
    CHECK_EQUAL(expected, sw);
    CHECK_EQUAL(reinterpret_cast<void*>(&tracees), reported[0].ctx);
}

TEST(Voluntary_SchedSwitch_Event_during_syscall) {
    TestEnv _;

    std::vector<Event> reported;
    TestListener l(reported);
    ftrace::Tracer::Tracees tracees;
    ftrace::Tracer::SwitchTrackingEventHandler steh(l, tracees);
    tracees[500] = reinterpret_cast<void*>(&tracees);

    ftrace::event::CommonFields cf1 {20, 0, 1, 500};
    ftrace::event::SyscallEntry write_in{1};
    steh.handle(3, 7, &cf1, &write_in);
    ftrace::event::SchedSwitch sw1 {"5_0_0", 500, 119, 1, "2_0_0", 200, 120};
    steh.handle(7, 10, &cf1, &sw1);
    ftrace::event::SyscallExit write_out{1};
    steh.handle(2, 12, &cf1, &write_out);
    
    CHECK_EQUAL(1, reported.size());
    CHECK(ftrace::v_curr::PktType::sched_switch == reported[0].type);
    auto& sw = reported[0].evt.sched_switch;
    ftrace::v_curr::payload::SchedSwitch expected {10, 500, 200, 1, 7, true};
    CHECK_EQUAL(expected, sw);
    CHECK_EQUAL(reinterpret_cast<void*>(&tracees), reported[0].ctx);
}

TEST(Voluntary_SchedSwitch_Event_after_syscall) { //again, due to lost events
    TestEnv _;

    std::vector<Event> reported;
    TestListener l(reported);
    ftrace::Tracer::Tracees tracees;
    ftrace::Tracer::SwitchTrackingEventHandler steh(l, tracees);
    std::uint64_t x = 123456;
    tracees[500] = reinterpret_cast<void*>(&x);

    ftrace::event::CommonFields cf1 {20, 0, 1, 500};
    ftrace::event::SyscallEntry read_in{0};
    steh.handle(2, 7, &cf1, &read_in);
    ftrace::event::SyscallExit read_out{0};
    steh.handle(2, 10, &cf1, &read_out);
    ftrace::event::SchedSwitch sw1 {"5_0_0", 500, 119, 2, "2_0_0", 200, 120};
    steh.handle(3, 12, &cf1, &sw1);
    
    CHECK_EQUAL(1, reported.size());
    CHECK(ftrace::v_curr::PktType::sched_switch == reported[0].type);
    auto& sw = reported[0].evt.sched_switch;
    ftrace::v_curr::payload::SchedSwitch expected {12, 500, 200, -1, 3, true};
    CHECK_EQUAL(expected, sw);
    CHECK_EQUAL(reinterpret_cast<void*>(&x), reported[0].ctx);
}

TEST(SchedWakeup_Event) {
    TestEnv _;

    std::vector<Event> reported;
    TestListener l(reported);
    ftrace::Tracer::Tracees tracees;
    ftrace::Tracer::SwitchTrackingEventHandler steh(l, tracees);
    std::uint64_t x = 123456;
    tracees[500] = reinterpret_cast<void*>(&x);

    ftrace::event::CommonFields cf1 {20, 0, 1, 500};
    ftrace::event::SchedWakeup wake1 {"my_proc", 500, 120, 1, 3};
    steh.handle(2, 12, &cf1, &wake1);
    
    CHECK_EQUAL(1, reported.size());
    CHECK(ftrace::v_curr::PktType::sched_wakeup == reported[0].type);
    auto& wake = reported[0].evt.sched_wakeup;
    ftrace::v_curr::payload::SchedWakeup expected {12, 3, 500};
    CHECK_EQUAL(expected, wake);
    CHECK_EQUAL(reinterpret_cast<void*>(&x), reported[0].ctx);
}

TEST(SchedSwitch_Event_after_untrack) {
    TestEnv _;

    std::vector<Event> reported;
    TestListener l(reported);
    ftrace::Tracer::Tracees tracees;
    ftrace::Tracer::SwitchTrackingEventHandler steh(l, tracees);
    tracees[1020] = reinterpret_cast<void*>(&tracees);

    ftrace::event::CommonFields cf1 {20, 0, 1, 1020};
    ftrace::event::SyscallEntry read_in{0};
    steh.handle(1, 7, &cf1, &read_in);
    steh.untrack_tid(1020);
    ftrace::event::SchedSwitch sw1 {"1_0_2_0", 1020, 120, 0, "some_other", 2030, 119};
    steh.handle(1, 10, &cf1, &sw1);
    
    CHECK_EQUAL(1, reported.size());
    CHECK(ftrace::v_curr::PktType::sched_switch == reported[0].type);
    auto& sw = reported[0].evt.sched_switch;
    ftrace::v_curr::payload::SchedSwitch expected {10, 1020, 2030, -1, 1, false};
    CHECK_EQUAL(expected, sw);
    CHECK_EQUAL(reinterpret_cast<void*>(&tracees), reported[0].ctx);
}

TEST(SchedSwitch_Event_after_untracking_untracked) {
    TestEnv _;

    std::vector<Event> reported;
    TestListener l(reported);
    ftrace::Tracer::Tracees tracees;
    ftrace::Tracer::SwitchTrackingEventHandler steh(l, tracees);
    tracees[1020] = reinterpret_cast<void*>(&tracees);

    ftrace::event::CommonFields cf1 {20, 0, 1, 1020};
    steh.untrack_tid(1020);
    ftrace::event::SchedSwitch sw1 {"1_0_2_0", 1020, 120, 0, "some_other", 2030, 119};
    steh.handle(1, 10, &cf1, &sw1);
    
    CHECK_EQUAL(1, reported.size());
    CHECK(ftrace::v_curr::PktType::sched_switch == reported[0].type);
    auto& sw = reported[0].evt.sched_switch;
    ftrace::v_curr::payload::SchedSwitch expected {10, 1020, 2030, -1, 1, false};
    CHECK_EQUAL(expected, sw);
    CHECK_EQUAL(reinterpret_cast<void*>(&tracees), reported[0].ctx);
}

TEST(SchedSwitch_Event__is_delivered_only_once_and_for_outpid__when_both_threads_belong_to_the_same_ctx) {//ctx == client (this API identifies client opaquely thru ctx) -jj
    TestEnv _;

    std::vector<Event> reported;
    TestListener l(reported);
    ftrace::Tracer::Tracees tracees;
    ftrace::Tracer::SwitchTrackingEventHandler steh(l, tracees);
    tracees[1020] = reinterpret_cast<void*>(&tracees);
    tracees[2030] = reinterpret_cast<void*>(&tracees);
    
    ftrace::event::CommonFields cf1 {20, 0, 1, 1020};
    ftrace::event::SyscallExit read_out{0};
    steh.handle(1, 7, &cf1, &read_out);
    ftrace::event::SchedSwitch sw1 {"1_0_2_0", 1020, 120, 0, "some_other", 2030, 119};
    steh.handle(0, 10, &cf1, &sw1);

    CHECK_EQUAL(1, reported.size());
    CHECK(ftrace::v_curr::PktType::sched_switch == reported[0].type);
    auto& sw = reported[0].evt.sched_switch;
    ftrace::v_curr::payload::SchedSwitch expected {10, 1020, 2030, -1, 0, false};
    CHECK_EQUAL(expected, sw);
    CHECK_EQUAL(reinterpret_cast<void*>(&tracees), reported[0].ctx);
}

TEST(SchedSwitch_Event__is_delivered_to_both_outpid_and_inpid__when_both_threads_belong_to_the_different_ctx) {
    TestEnv _;

    std::vector<Event> reported;
    TestListener l(reported);
    ftrace::Tracer::Tracees tracees;
    ftrace::Tracer::SwitchTrackingEventHandler steh(l, tracees);
    tracees[1020] = reinterpret_cast<void*>(&tracees);
    tracees[2030] = reinterpret_cast<void*>(&reported);
    
    ftrace::event::CommonFields cf1 {20, 0, 1, 1020};
    ftrace::event::SyscallExit read_out{0};
    steh.handle(1, 7, &cf1, &read_out);
    ftrace::event::SchedSwitch sw1 {"1_0_2_0", 1020, 120, 0, "some_other", 2030, 119};
    steh.handle(0, 10, &cf1, &sw1);

    CHECK_EQUAL(2, reported.size());
    CHECK(ftrace::v_curr::PktType::sched_switch == reported[0].type);
    CHECK(ftrace::v_curr::PktType::sched_switch == reported[1].type);

    auto& sw = reported[0].evt.sched_switch;
    ftrace::v_curr::payload::SchedSwitch expected {10, 1020, 2030, -1, 0, false};
    CHECK_EQUAL(expected, sw);
    CHECK_EQUAL(reinterpret_cast<void*>(&tracees), reported[0].ctx);

    sw = reported[1].evt.sched_switch;
    CHECK_EQUAL(expected, sw);
    CHECK_EQUAL(reinterpret_cast<void*>(&reported), reported[1].ctx);

}

TEST(SchedSwitch_Event__as_next__without_syscall) {
    TestEnv _;

    std::vector<Event> reported;
    TestListener l(reported);
    ftrace::Tracer::Tracees tracees;
    ftrace::Tracer::SwitchTrackingEventHandler steh(l, tracees);
    tracees[2030] = reinterpret_cast<void*>(&tracees);
    
    ftrace::event::CommonFields cf1 {20, 0, 1, 1020};
    ftrace::event::SchedSwitch sw1 {"1_0_2_0", 1020, 120, 0, "some_other", 2030, 119};
    steh.handle(0, 10, &cf1, &sw1);
    
    CHECK_EQUAL(1, reported.size());
    CHECK(ftrace::v_curr::PktType::sched_switch == reported[0].type);
    auto& sw = reported[0].evt.sched_switch;
    ftrace::v_curr::payload::SchedSwitch expected {10, 1020, 2030, -1, 0, false};
    CHECK_EQUAL(expected, sw);
    CHECK_EQUAL(reinterpret_cast<void*>(&tracees), reported[0].ctx);
}

TEST(SchedSwitch_Event__as_next__during_syscall) {
    TestEnv _;

    std::vector<Event> reported;
    TestListener l(reported);
    ftrace::Tracer::Tracees tracees;
    ftrace::Tracer::SwitchTrackingEventHandler steh(l, tracees);
    tracees[2030] = reinterpret_cast<void*>(&tracees);

    ftrace::event::CommonFields cf1 {20, 0, 1, 1020};
    ftrace::event::SyscallEntry read_in{0};
    steh.handle(1, 7, &cf1, &read_in);
    ftrace::event::SchedSwitch sw1 {"1_0_2_0", 1020, 120, 0, "some_other", 2030, 119};
    steh.handle(1, 10, &cf1, &sw1);
    ftrace::event::SyscallExit read_out{0};
    steh.handle(1, 12, &cf1, &read_out);
    
    CHECK_EQUAL(1, reported.size());
    CHECK(ftrace::v_curr::PktType::sched_switch == reported[0].type);
    auto& sw = reported[0].evt.sched_switch;
    ftrace::v_curr::payload::SchedSwitch expected {10, 1020, 2030, 0, 1, false};
    CHECK_EQUAL(expected, sw);
    CHECK_EQUAL(reinterpret_cast<void*>(&tracees), reported[0].ctx);
}

TEST(SchedSwitch_Event__as_next__after_syscall) {
    TestEnv _;

    std::vector<Event> reported;
    TestListener l(reported);
    ftrace::Tracer::Tracees tracees;
    ftrace::Tracer::SwitchTrackingEventHandler steh(l, tracees);
    tracees[2030] = reinterpret_cast<void*>(&tracees);

    ftrace::event::CommonFields cf1 {20, 0, 1, 1020};
    ftrace::event::SyscallEntry read_in{0};
    steh.handle(2, 7, &cf1, &read_in);
    ftrace::event::SyscallExit read_out{0};
    steh.handle(2, 10, &cf1, &read_out);
    ftrace::event::SchedSwitch sw1 {"1_0_2_0", 1020, 120, 0, "some_other", 2030, 119};
    steh.handle(2, 12, &cf1, &sw1);
    
    CHECK_EQUAL(1, reported.size());
    CHECK(ftrace::v_curr::PktType::sched_switch == reported[0].type);
    auto& sw = reported[0].evt.sched_switch;
    ftrace::v_curr::payload::SchedSwitch expected {12, 1020, 2030, -1, 2, false};
    CHECK_EQUAL(expected, sw);
    CHECK_EQUAL(reinterpret_cast<void*>(&tracees), reported[0].ctx);
}

TEST(Voluntary_SchedSwitch_Event__as_next__without_syscall) { // Don't make faces. This can happen due to missed events. -jj
    TestEnv _;

    std::vector<Event> reported;
    TestListener l(reported);
    ftrace::Tracer::Tracees tracees;
    ftrace::Tracer::SwitchTrackingEventHandler steh(l, tracees);
    tracees[200] = reinterpret_cast<void*>(&tracees);

    ftrace::event::CommonFields cf1 {20, 0, 1, 500};
    ftrace::event::SchedSwitch sw1 {"5_0_0", 500, 119, 1, "2_0_0", 200, 120};
    steh.handle(2, 12, &cf1, &sw1);
    
    CHECK_EQUAL(1, reported.size());
    CHECK(ftrace::v_curr::PktType::sched_switch == reported[0].type);
    auto& sw = reported[0].evt.sched_switch;
    ftrace::v_curr::payload::SchedSwitch expected {12, 500, 200, -1, 2, true};
    CHECK_EQUAL(expected, sw);
    CHECK_EQUAL(reinterpret_cast<void*>(&tracees), reported[0].ctx);
}

TEST(Voluntary_SchedSwitch_Event__as_next__during_syscall) {
    TestEnv _;

    std::vector<Event> reported;
    TestListener l(reported);
    ftrace::Tracer::Tracees tracees;
    ftrace::Tracer::SwitchTrackingEventHandler steh(l, tracees);
    tracees[200] = reinterpret_cast<void*>(&tracees);

    ftrace::event::CommonFields cf1 {20, 0, 1, 500};
    ftrace::event::SyscallEntry write_in{1};
    steh.handle(3, 7, &cf1, &write_in);
    ftrace::event::SchedSwitch sw1 {"5_0_0", 500, 119, 1, "2_0_0", 200, 120};
    steh.handle(7, 10, &cf1, &sw1);
    ftrace::event::SyscallExit write_out{1};
    steh.handle(2, 12, &cf1, &write_out);
    
    CHECK_EQUAL(1, reported.size());
    CHECK(ftrace::v_curr::PktType::sched_switch == reported[0].type);
    auto& sw = reported[0].evt.sched_switch;
    ftrace::v_curr::payload::SchedSwitch expected {10, 500, 200, 1, 7, true};
    CHECK_EQUAL(expected, sw);
    CHECK_EQUAL(reinterpret_cast<void*>(&tracees), reported[0].ctx);
}

TEST(Voluntary_SchedSwitch_Event__as_next__after_syscall) { //again, due to lost events
    TestEnv _;

    std::vector<Event> reported;
    TestListener l(reported);
    ftrace::Tracer::Tracees tracees;
    ftrace::Tracer::SwitchTrackingEventHandler steh(l, tracees);
    std::uint64_t x = 123456;
    tracees[200] = reinterpret_cast<void*>(&x);

    ftrace::event::CommonFields cf1 {20, 0, 1, 500};
    ftrace::event::SyscallEntry read_in{0};
    steh.handle(2, 7, &cf1, &read_in);
    ftrace::event::SyscallExit read_out{0};
    steh.handle(2, 10, &cf1, &read_out);
    ftrace::event::SchedSwitch sw1 {"5_0_0", 500, 119, 2, "2_0_0", 200, 120};
    steh.handle(3, 12, &cf1, &sw1);
    
    CHECK_EQUAL(1, reported.size());
    CHECK(ftrace::v_curr::PktType::sched_switch == reported[0].type);
    auto& sw = reported[0].evt.sched_switch;
    ftrace::v_curr::payload::SchedSwitch expected {12, 500, 200, -1, 3, true};
    CHECK_EQUAL(expected, sw);
    CHECK_EQUAL(reinterpret_cast<void*>(&x), reported[0].ctx);
}
