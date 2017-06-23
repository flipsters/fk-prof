#ifndef FTRACE_SERVER_H
#define FTRACE_SERVER_H
#include <cstdint>
#include <string>
#include <atomic>

namespace ftrace {
    class Server {
    public:
        explicit Server(const std::string& tracing_dir, const std::string& socket_path);

        ~Server();

        void run();

        void stop();

    private:
        std::atomic<bool> keep_running;
        
    };
}

#endif
