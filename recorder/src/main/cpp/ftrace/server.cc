#include <sys/types.h>
#include <sys/stat.h>
#include "logging.hh"
#include "ftrace/server.hh"
#include "ftrace/tracer.hh"
#include <sys/types.h>
#include <sys/socket.h>
#include <sys/un.h>
#include <sys/epoll.h>

enum class FdType : std::uint8_t {
    listener = 0, connection, tracer
};

namespace PollCtx {
    std::uint16_t cpu(std::uint64_t val) {
        return (val >> 40) & 0xFFFF;
    }
    
    int fd(std::uint64_t val) {
        return (val >> 8) & 0xFFFFFFFF;
    }

    FdType type(std::uint64_t val) {
        return static_cast<FdType>(val & 0xFF);
    }

    std::uint64_t pack(FdType type, int fd) {
        assert(type != FdType::tracer);
        assert(fd > 0);
        std::uint64_t val = fd;
        return (val << 8) | (static_cast<std::uint8_t>(type) & 0xFF);
    }

    std::uint64_t pack(FdType type, int fd, std::uint16_t cpu) {
        assert(type == FdType::tracer);
        assert(fd > 0);
        std::uint64_t val = cpu;
        val <<= 32;
        val |= fd;
        return (val << 8) | (static_cast<std::uint8_t>(type) & 0xFF);
    }
}

static void make_non_blocking(int fd) {
    int flags = fcntl(fd, F_GETFL);
    if (flags == -1) throw log_and_get_error("Couldn't create listener-socket", errno);

    flags |= O_NONBLOCK;

    auto ret = fcntl(fd, F_SETFL, flags);
    if(ret == -1) throw log_and_get_error("Couldn't create listener-socket", errno);
}

#define METRIC_TYPE "server"

ftrace::Server::Server(const std::string& tracing_dir, const std::string& socket_path) :
    keep_running(true), tracer(nullptr),
    
    s_t_processing_time(get_metrics_registry().new_timer({METRICS_DOMAIN_TRACE, METRIC_TYPE, "processing_time"})),
    s_t_wait_time(get_metrics_registry().new_timer({METRICS_DOMAIN_TRACE, METRIC_TYPE, "wait_time"})),
    s_m_poll_events(get_metrics_registry().new_meter({METRICS_DOMAIN_TRACE, METRIC_TYPE, "poll", "events"}, "rate")) {
    
    poll_fd = epoll_create1(EPOLL_CLOEXEC);
    if (poll_fd < 0) {
        throw log_and_get_error("Couldn't create epoll-fd", errno);
    }
    setup_listener(socket_path);
    tracer.reset(new Tracer(tracing_dir, [&](const ftrace::Tracer::DataLink& link) {
                auto fd = link.pipe_fd;
                make_non_blocking(fd);
                epoll_event evt { .events = EPOLLIN, .data = {.u64 = PollCtx::pack(FdType::tracer, fd, link.cpu)}};
                auto ret = epoll_ctl(poll_fd, EPOLL_CTL_ADD, fd, &evt);
                if (ret != 0) throw log_and_get_error("Couldn't add tracer-fd to epoll-ctx", errno);
            }));
}

ftrace::Server::~Server() {}

// We may need to tune this value upwards as servers/VMs with higher number of cores arrive (because ftrace-buffer is a per-cpu asset)
#define MAX_POLLED_EVENTS 256
#define SERVER_POLL_TIMEOUT_SECONDS 10

void ftrace::Server::run() {
    int num_evts;
    epoll_event evts[MAX_POLLED_EVENTS];

    while (keep_running) {{
            auto timer_ctx = s_t_wait_time.time_scope();
            num_evts = epoll_wait(poll_fd, evts, MAX_POLLED_EVENTS, SERVER_POLL_TIMEOUT_SECONDS * 1000);
        }
        auto timer_ctx = s_t_processing_time.time_scope();
        if (num_evts < 0) {
            logger->warn(error_message("IO-poll failed", errno));
        } else {
            s_m_poll_events.mark(num_evts);
            for (int i = 0; i < num_evts; i++) {
                //handle_io_evt(evts[i].events, evts[i].data.u64);
            }
        }
    }
}

void ftrace::Server::stop() {
    
}

void ftrace::Server::setup_listener(const std::string& socket_path) {
    listener_fd = socket(AF_UNIX, SOCK_STREAM | SOCK_NONBLOCK | SOCK_CLOEXEC, 0);
    if (listener_fd < 0) throw log_and_get_error("Couldn't create listener-socket", errno);
    
    sockaddr_un addr;
    std::memset(&addr, 0, sizeof(addr));
    addr.sun_family = AF_UNIX;
    std::strncpy(addr.sun_path, socket_path.c_str(), sizeof(addr.sun_path));
    // We can unlink before bind, but that will be useful only when the server is misbehaving (crashing etc),
    //    in that kind of a situation, let us just fix the problem (we don't need that unlink hack) -jj
    auto ret = bind(listener_fd, reinterpret_cast<sockaddr*>(&addr), sizeof(addr));
    if (ret != 0) throw log_and_get_error("Couldn't bind server socket", errno);

    ret = listen(listener_fd, 8);
    if (ret != 0) throw log_and_get_error("Couldn't start listening", errno);
 
    epoll_event evt { .events = EPOLLIN, .data = {.u64 = PollCtx::pack(FdType::listener, listener_fd)}};
    ret = epoll_ctl(poll_fd, EPOLL_CTL_ADD, listener_fd, &evt);
    if (ret != 0) throw log_and_get_error("Couldn't add listener to epoll-ctx", errno);
}
