#include "config.hh"
#include "config_util.hh"

std::ostream& operator<<(std::ostream& os, const ConfigurationOptions* config) {
    auto i = 0; //for last entry tracking;
    os << "{ ";
    PRINT_FIELD(service_endpoint);
    PRINT_FIELD(ip);
    PRINT_FIELD(host);
    PRINT_FIELD(app_id);
    PRINT_FIELD(inst_grp);
    PRINT_FIELD(cluster);
    PRINT_FIELD(inst_id);
    PRINT_FIELD(proc);
    PRINT_FIELD(vm_id);
    PRINT_FIELD(zone);
    PRINT_FIELD(inst_typ);
    PRINT_FIELD(backoff_start);
    PRINT_FIELD(backoff_multiplier);
    PRINT_FIELD(max_retries);
    PRINT_FIELD(backoff_max);
    PRINT_FIELD(log_level);
    PRINT_FIELD(poll_itvl);
    PRINT_FIELD(metrics_dst_port);
    PRINT_FIELD_VALUE(noctx_cov_pct, static_cast<int>(config->noctx_cov_pct));
    PRINT_FIELD(allow_sigprof);
    PRINT_FIELD(pctx_jar_path);
    PRINT_FIELD(rpc_timeout);
    PRINT_FIELD(slow_tx_tolerance);
    PRINT_FIELD(tx_ring_sz);
    PRINT_FIELD(stats_syslog_tag);
    PRINT_FIELD(allow_bci);
    PRINT_FIELD(allow_ftrace);
    os << " }";
    return os;
}

void ConfigurationOptions::load(const char* options) {
    const char* next = options;
    for (const char *key = options; next != NULL; key = next + 1) {
        const char *value = strchr(key, '=');
        next = strchr(key, ',');
        if (value == NULL) {
            logger->warn("WARN: No value for key {}", key);
            continue;
        } else {
            value++;
            if (strstr(key, "service_endpoint") == key) {
                service_endpoint = safe_copy_string(value, next);
            } else if (strstr(key, "ip") == key) {
                ip = safe_copy_string(value, next);
            } else if (strstr(key, "host") == key) {
                host = safe_copy_string(value, next);
            } else if (strstr(key, "app_id") == key) {
                app_id = safe_copy_string(value, next);
            } else if (strstr(key, "inst_grp") == key) {
                inst_grp = safe_copy_string(value, next);
            } else if (strstr(key, "cluster") == key) {
                cluster = safe_copy_string(value, next);
            } else if (strstr(key, "inst_id") == key) {
                inst_id = safe_copy_string(value, next);
            } else if (strstr(key, "proc") == key) {
                proc = safe_copy_string(value, next);
            } else if (strstr(key, "vm_id") == key) {
                vm_id = safe_copy_string(value, next);
            } else if (strstr(key, "zone") == key) {
                zone = safe_copy_string(value, next);
            } else if (strstr(key, "inst_typ") == key) {
                inst_typ = safe_copy_string(value, next);
            } else if (strstr(key, "backoff_start") == key) {
                backoff_start = static_cast<std::uint32_t>(atoi(value));
                if (backoff_start == 0) backoff_start = MIN_BACKOFF_START;
            } else if (strstr(key, "backoff_multiplier") == key) {
                backoff_multiplier = static_cast<std::uint32_t>(atoi(value));
                if (backoff_multiplier == 0) backoff_multiplier = DEFAULT_BACKOFF_MULTIPLIER;
            } else if (strstr(key, "max_retries") == key) {
                max_retries = static_cast<std::uint32_t>(atoi(value));
            } else if (strstr(key, "backoff_max") == key) {
                backoff_max = static_cast<std::uint32_t>(atoi(value));
                if (backoff_max == 0) backoff_max = DEFAULT_BACKOFF_MAX;
            } else if (strstr(key, "log_lvl") == key) {
                ConfArg val(safe_copy_string(value, next), safe_free_string);
                log_level = resolv_log_level(val);
                logger->warn("Log-level set to: {}", log_level);
            } else if (strstr(key, "poll_itvl") == key) {
                poll_itvl = static_cast<std::uint32_t>(atoi(value));
                if (poll_itvl == 0) poll_itvl = DEFAULT_POLLING_INTERVAL;
            } else if (strstr(key, "metrics_dst_port") == key) {
                metrics_dst_port = static_cast<std::uint16_t>(atoi(value));
                if (metrics_dst_port == 0) metrics_dst_port = DEFAULT_METRICS_DEST_PORT;
            } else if (strstr(key, "noctx_cov_pct") == key) {
                noctx_cov_pct = static_cast<std::uint8_t>(atoi(value));
                if (noctx_cov_pct > 100) {
                    logger->warn("NoCtx coverage pct is too high at {}, re-setting it to 100", noctx_cov_pct);
                    noctx_cov_pct = 100;
                }
            } else if (strstr(key, "allow_sigprof") == key) {
                allow_sigprof = is_yes(value);
            } else if (strstr(key, "pctx_jar_path") == key) {
                pctx_jar_path = safe_copy_string(value, next);
            } else if (strstr(key, "rpc_timeout") == key) {
                rpc_timeout = static_cast<std::uint32_t>(atoi(value));
            } else if (strstr(key, "slow_tx_tolerance") == key) {
                slow_tx_tolerance = atof(value);
            } else if (strstr(key, "tx_ring_sz") == key) {
                tx_ring_sz = static_cast<std::uint32_t>(atoi(value));
            } else if (strstr(key, "stats_syslog_tag") == key) {
                stats_syslog_tag = safe_copy_string(value, next);
            } else if (strstr(key, "allow_bci") == key) {
                allow_bci = is_yes(value);
            } else if (strstr(key, "allow_ftrace") == key) {
                allow_ftrace = is_yes(value);
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

bool ConfigurationOptions::valid() {
    bool is_valid = true;
    ENSURE_NOT_NULL(service_endpoint);
    ENSURE_NOT_NULL(ip);
    ENSURE_NOT_NULL(host);
    ENSURE_NOT_NULL(app_id);
    ENSURE_NOT_NULL(inst_grp);
    ENSURE_NOT_NULL(cluster);
    ENSURE_NOT_NULL(inst_id);
    ENSURE_NOT_NULL(proc);
    ENSURE_NOT_NULL(vm_id);
    ENSURE_NOT_NULL(zone);
    ENSURE_NOT_NULL(inst_typ);
    ENSURE_NOT_NULL(pctx_jar_path);
    ENSURE_GT(rpc_timeout, 0);
    ENSURE_GT(slow_tx_tolerance, 1.0);
    ENSURE_GT(tx_ring_sz, 0);
    ENSURE_NOT_NULL(stats_syslog_tag);
    return is_valid;
}

ConfigurationOptions::~ConfigurationOptions()  {
    safe_free_string(service_endpoint);
    safe_free_string(ip);
    safe_free_string(host);
    safe_free_string(app_id);
    safe_free_string(inst_grp);
    safe_free_string(cluster);
    safe_free_string(inst_id);
    safe_free_string(proc);
    safe_free_string(vm_id);
    safe_free_string(zone);
    safe_free_string(inst_typ);
    safe_free_string(pctx_jar_path);
    safe_free_string(stats_syslog_tag);
}
