#include <signal.h>
#include "ftrace/server.hh"
#include <stdexcept>
#include "logging.hh"
#include <atomic>

LoggerP logger = spdlog::syslog_logger("syslog", "fk-prof-rec-ftracer", LOG_PID);

#define EXPECTED_ARGS 2

std::atomic<bool> cancel_startup {false};
std::atomic<ftrace::Server*> server {nullptr};

void sigterm_handler(int signal_no, siginfo_t* info, void* context) {
    cancel_startup.store(true, std::memory_order_seq_cst);
    auto s = server.load(std::memory_order_seq_cst);
    if (s != nullptr) {
        s.stop();
    }
}

void setup_term_action() {
    struct sigaction sa;
    sa.sa_handler = NULL;
    sa.sa_sigaction = sigterm_handler;
    sa.sa_flags = SA_RESTART | SA_SIGINFO;

    sigemptyset(&sa.sa_mask);

    if (sigaction(SIGTERM, &sa, nullptr) != 0) {
        char err_msg[128];
        int err = errno;
        auto msg = strerror_r(err, err_msg, sizeof(err_msg));
        logger->error("Couldn't setup sigterm handler: {} ({})", msg, err);
        throw new std::runtime_error("Failed to setup signal-handler, aborting");
    }
}

int main(int argc, char** argv) {
    if (argc != (EXPECTED_ARGS + 1)) {
        logger->error("Expected {} arguments, got {}", EXPECTED_ARGS, argc - 1);
        throw std::runtime_error("Bad usage: fk-prof-ftracer <tracing-dir-path> <socket-path>");
    }
    setup_term_action();

    std::string trace_dir {argv[1]};
    std::string socket_path {argv[2]};
    
    server.store(new ftrace::Server(trace_dir, socket_path), std::memory_order_seq_cst);
    
    if (cancel_startup.load(std::memory_order_seq_cst)) return;
    
    server.load(std::memory_order_seq_cst)->run();
    
    return 0;
}
