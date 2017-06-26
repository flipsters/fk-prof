#include "ftrace/proto.hh"
#include <stdexcept>

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
