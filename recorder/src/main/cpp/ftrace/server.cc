#include <sys/types.h>
#include <sys/stat.h>
#include "logging.hh"
#include "ftrace/server.hh"
#include "ftrace/tracer.hh"


ftrace::Server::Server(const std::string& tracing_dir, const std::string& socket_path) : tracer(tracing_dir) {
}


