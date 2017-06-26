#include "ftrace/proto.hh"
#include <stdexcept>
#include <ostream>

const std::uint8_t ftrace::v0::VERSION = 0;


std::string std::to_string(const ftrace::v0::PktType type) {
    switch(type) {
    case ftrace::v0::PktType::add_tid:
        return "v0::AddTid";
    case ftrace::v0::PktType::del_tid:
        return "v0::DelTid";
    case ftrace::v0::PktType::lost_events:
        return "v0::LostEvents";
    case ftrace::v0::PktType::sched_switch:
        return "v0::SchedSwitch";
    case ftrace::v0::PktType::sched_wakeup:
        return "v0::SchedWakeup";
    default:
        std::runtime_error("Unknown v0 pkt-type: " + std::to_string(static_cast<std::uint32_t>(type)));
    }
}

bool operator==(const ftrace::v0::payload::SchedSwitch& sw1, const ftrace::v0::payload::SchedSwitch& sw2) {
    return (sw1.timestamp == sw2.timestamp) &&
        (sw1.in_tid == sw2.in_tid) &&
        (sw1.out_tid == sw2.out_tid) &&
        (sw1.syscall_nr == sw2.syscall_nr) &&
        (sw1.cpu == sw2.cpu) &&
        (sw1.voluntary == sw2.voluntary);
}

std::ostream& operator<<(std::ostream& os, const ftrace::v0::payload::SchedSwitch& sw) {
    os << '{';
    os << "ts: " << sw.timestamp << ", ";
    os << "in_tid: " << sw.in_tid << ", ";
    os << "out_tid: " << sw.out_tid << ", ";
    os << "syscall_nr: " << sw.syscall_nr << ", ";
    os << "cpu: " << sw.cpu << ", ";
    os << "voluntary: " << sw.voluntary;
    os << '}';
    return os;
}

bool operator==(const ftrace::v0::payload::SchedWakeup& w1, const ftrace::v0::payload::SchedWakeup& w2) {
    return (w1.timestamp == w2.timestamp) &&
        (w1.target_cpu == w2.target_cpu) &&
        (w1.tid == w2.tid) &&
        (w1.cpu == w2.cpu);
}

std::ostream& operator<<(std::ostream& os, const ftrace::v0::payload::SchedWakeup& w) {
    os << '{';
    os << "ts: " << w.timestamp << ", ";
    os << "target_cpu: " << w.target_cpu << ", ";
    os << "in_tid: " << w.tid << ", ";
    os << "cpu: " << w.cpu;
    os << '}';
    return os;
}
