#ifndef LOGGING_H
#define LOGGING_H

#define SPDLOG_ENABLE_SYSLOG
#include <spdlog/spdlog.h>
#include <cerrno>

typedef std::shared_ptr<spdlog::logger> LoggerP;

extern LoggerP logger;

std::string error_message(const std::string& prefix, int err);

std::runtime_error log_and_get_error(const std::string& prefix, int err);

void init_logging(spdlog::level::level_enum level, const std::string& service);

#endif
