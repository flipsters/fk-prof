#ifndef LOGGING_H
#define LOGGING_H

#define SPDLOG_ENABLE_SYSLOG
#include <spdlog/spdlog.h>

typedef std::shared_ptr<spdlog::logger> LoggerP;

extern LoggerP logger;

#endif
