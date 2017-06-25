#ifndef FTRACE_SERVER_H
#define FTRACE_SERVER_H

#include <cstdint>
#include <string>
#include <atomic>
#include <memory>
#include "tracer.hh"
#include "metrics.hh"

namespace ftrace {
    class Server {
    public:
        explicit Server(const std::string& tracing_dir, const std::string& socket_path);

        ~Server();

        void run();

        void stop();

    private:
        void setup_listener(const std::string& socket_path);

        std::atomic<bool> keep_running;

        std::unique_ptr<Tracer> tracer;

        metrics::Timer& s_t_processing_time;

        metrics::Timer& s_t_wait_time;

        metrics::Mtr& s_m_poll_events;

        int poll_fd;

        int listener_fd;
    };
}

#endif
