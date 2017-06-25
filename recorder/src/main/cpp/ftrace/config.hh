#ifndef FTRACE_CONFIG_H
#define FTRACE_CONFIG_H

#include <cstdint>
#include "config_shared.hh"
#include "../logging.hh"

namespace ftrace {
    struct Config {
        std::string trace_dir;
        std::string listener_socket;
        spdlog::level::level_enum log_level;
        std::uint16_t metrics_dst_port;
        std::string stats_syslog_tag;

        Config(const char* options) :
            trace_dir("/sys/kernel/debug/tracing"),
            listener_socket("/var/tmp/fkp-tracer.sock"),
            log_level(spdlog::level::info),
            metrics_dst_port(DEFAULT_METRICS_DEST_PORT),
            stats_syslog_tag("") {

            load(options);
        }

        virtual ~Config();

        bool valid();

    private:
        void load(const char* options);
        
    };

}

#endif
