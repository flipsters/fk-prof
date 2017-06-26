#ifndef FTRACE_TRACER_H
#define FTRACE_TRACER_H

#include <cstdint>
#include <string>
#include <atomic>
#include <list>
#include <unordered_map>
#include <functional>
#include "ftrace/proto.hh"

namespace ftrace {
    class Tracer {
    public:
        class Listener {

        public:
            virtual ~Listener() {}
            
            virtual void unicast(pid_t dest_pid, void* ctx, ftrace::v_curr::PktType pkt_type, const std::uint8_t* payload, ftrace::v_curr::PayloadLen payload_len) = 0;

            virtual void multicast(ftrace::v_curr::PktType pkt_type, const std::uint8_t* payload, ftrace::v_curr::PayloadLen payload_len) = 0;
            
        };
        
        struct DataLink {
            int pipe_fd;
            int stats_fd;
            std::uint16_t cpu;
        };
        
        explicit Tracer(const std::string& tracing_dir, Listener& _listener, std::function<void(const DataLink&)> data_link_listener);

        ~Tracer();

        void trace_on(pid_t pid, void* ctx);
        
        void trace_off(pid_t pid);

        void process(const DataLink& link);

    private:
        void start();

        void stop();

        Listener& listener;

        std::string instance_path;

        struct {
            int tracing_on;
            int trace_options;
            int sched_switch_enable;
            int sched_wakeup_enable;
            int syscall_enter_enable;
            int syscall_exit_enable;
            int set_event_pid; // newer kernels support this, enhance this to write pids to be traced here -jj
        } ctrl_fds;

        std::list<DataLink> dls;

        std::unordered_map<pid_t, void*> tracees;
    };
}

#endif
