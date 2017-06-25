#include "logging.hh"
#include <stdexcept>

std::string error_message(const std::string& prefix, int err) {
     char err_msg[128];
     auto msg = strerror_r(err, err_msg, sizeof(err_msg));
     return prefix + ", " + msg;
}

std::runtime_error log_and_get_error(const std::string& prefix, int err) {
    auto msg = error_message(prefix, err);
    logger->error(msg);
    return std::runtime_error(msg);
}

#define LST "LOGGING-SELF-TEST: "

void log_level_self_test() {
    logger->trace(LST "*trace*");
    SPDLOG_TRACE(logger, LST "*compile-time checked trace*");
    logger->debug(LST "*debug*");
    SPDLOG_DEBUG(logger, LST "*compile-time checked debug*");
    logger->info(LST "*info*");
    logger->warn(LST "*warn*");
    logger->error(LST "*err*");
    logger->critical(LST "*critical*");
}

void log_startup_message(const std::string& service) {
    logger->info("======================= Starting fk-prof {} =======================", service);
}

void init_logging(spdlog::level::level_enum level, const std::string& service) {
    logger->set_level(level);
    logger->set_pattern("{%t} %+");//TODO: make this configurable
    log_level_self_test();
    log_startup_message(service);
}
