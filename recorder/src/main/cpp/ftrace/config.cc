#include "ftrace/config.hh"
#include "config.hh"
#include "config_util.hh"
#include <ostream>
#include <sstream>

std::ostream& operator<<(std::ostream& os, const ftrace::Config* config) {
    auto i = 0;
    os << "{ ";
    PRINT_FIELD(trace_dir);
    PRINT_FIELD(listener_socket);
    PRINT_FIELD(log_level);
    PRINT_FIELD(metrics_dst_port);
    PRINT_FIELD(stats_syslog_tag);
    os << " }";
    return os;
}

void ftrace::Config::load(const char* options) {
    const char* next = options;
    for (const char *key = options; next != NULL; key = next + 1) {
        const char *value = strchr(key, '=');
        next = strchr(key, ',');
        if (value == NULL) {
            logger->warn("WARN: No value for key {}", key);
            continue;
        } else {
            value++;
            if (strstr(key, "trace_dir") == key) {
                ConfArg val(safe_copy_string(value, next), safe_free_string);
                trace_dir = val.get();
            } else if (strstr(key, "listener_socket") == key) {
                ConfArg val(safe_copy_string(value, next), safe_free_string);
                listener_socket = val.get();
            } else if (strstr(key, "log_lvl") == key) {
                ConfArg val(safe_copy_string(value, next), safe_free_string);
                log_level = resolv_log_level(val);
                logger->warn("Log-level set to: {}", log_level);
            } else if (strstr(key, "metrics_dst_port") == key) {
                metrics_dst_port = static_cast<std::uint16_t>(atoi(value));
                if (metrics_dst_port == 0) metrics_dst_port = DEFAULT_METRICS_DEST_PORT;
            } else if (strstr(key, "stats_syslog_tag") == key) {
                ConfArg val(safe_copy_string(value, next), safe_free_string);
                stats_syslog_tag = val.get();
            } else {
                logger->warn("Unknown configuration option: {}", key);
            }
        }
    }
    std::stringstream ss;
    ss << this;
    auto str = ss.str();
    logger->info("Config load complete, config: {}", str);
}

bool ftrace::Config::valid() {
    bool is_valid = true;
    ENSURE_NOT_EMPTY(trace_dir);
    ENSURE_NOT_EMPTY(listener_socket);
    ENSURE_NOT_EMPTY(stats_syslog_tag);
    return is_valid;
}

ftrace::Config::~Config()  { }

