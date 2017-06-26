#ifndef FTRACE_EVENTS_H
#define FTRACE_EVENTS_H

#include <cstdint>
#include <string>

namespace ftrace {
    namespace event {
        struct __attribute__((packed)) SyscallEntry {
            std::int64_t nr;
            std::uint64_t args[6];
        };

        struct __attribute__((packed)) SyscallExit {
            std::int64_t nr;
            std::int64_t ret;
        };

        struct __attribute__((packed)) CommonFields {
            std::uint16_t common_type;
            std::uint8_t common_flags;
            std::uint8_t common_preempt_count;
            std::int32_t common_pid;
        };

        struct __attribute__((packed)) SchedSwitch {
            char prev_comm[16];
            std::int32_t prev_pid;
            std::int32_t prev_prio;
            std::int64_t prev_state;
            char next_comm[16];
            std::int32_t next_pid;
            std::int32_t next_prio;
        };

        struct __attribute__((packed)) SchedWakeup {
            char comm[16];
            std::int32_t pid;
            std::int32_t prio;
            std::int32_t success;
            std::int32_t target_cpu;
        };
    }

    class EventHandler {
    public:
        virtual ~EventHandler() {};

        virtual void handle(std::uint32_t cpu, std::uint64_t timestamp_ns, const event::CommonFields* cf, const event::SyscallEntry* sys_entry) = 0;
        virtual void handle(std::uint32_t cpu, std::uint64_t timestamp_ns, const event::CommonFields* cf, const event::SyscallExit* sys_exit) = 0;
        virtual void handle(std::uint32_t cpu, std::uint64_t timestamp_ns, const event::CommonFields* cf, const event::SchedSwitch* sched_switch) = 0;
        virtual void handle(std::uint32_t cpu, std::uint64_t timestamp_ns, const event::CommonFields* cf, const event::SchedWakeup* sched_wakeup) = 0;
    };

    class CommonHeaderReader {
    public:
        virtual ~CommonHeaderReader() {};

        virtual std::size_t read(const std::uint8_t* buff, event::CommonFields& common_fields) = 0;
    };

    class SyscallEntryReader {
    public:
        virtual ~SyscallEntryReader() {};

        virtual std::size_t read(const std::uint8_t* buff, event::SyscallEntry& sys_entry) = 0;
    };

    class SyscallExitReader {
    public:
        virtual ~SyscallExitReader() {};

        virtual std::size_t read(const std::uint8_t* buff, event::SyscallExit& sys_exit) = 0;
    };

    class SchedSwitchReader {
    public:
        virtual ~SchedSwitchReader() {};

        virtual std::size_t read(const std::uint8_t* buff, event::SchedSwitch& sched_switch) = 0;
    };

    class SchedWakeupReader {
    public:
        virtual ~SchedWakeupReader() {};

        virtual std::size_t read(const std::uint8_t* buff, event::SchedWakeup& sched_wakeup) = 0;
    };

    class EventReader {
    public:
        EventReader(const std::string& _events_dir, EventHandler& _handler);

        virtual ~EventReader();

        std::size_t read(const std::uint8_t* buff, std::size_t sz) const;

    private:
        EventHandler& handler;

        CommonHeaderReader* common_header_rdr;
        SyscallEntryReader* sys_entry_rdr;
        SyscallExitReader* sys_exit_rdr;
        SchedSwitchReader* sched_switch_rdr;
        SchedWakeupReader* sched_wakeup_rdr;
    };

    class PageReader {
    public:
        PageReader(const EventReader& _e_rdr, std::size_t pg_sz);

        PageReader();

        virtual ~PageReader();

        std::size_t read(const std::uint8_t* page);

    private:
        const EventReader& e_rdr;

        std::size_t pg_sz;

    };
};

#endif
