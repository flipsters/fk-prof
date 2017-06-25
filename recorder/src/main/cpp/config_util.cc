#include "config_util.hh"

char *safe_copy_string(const char *value, const char *next) {
    size_t size = (next == 0) ? strlen(value) : (size_t) (next - value);
    char *dest = (char *) malloc((size + 1) * sizeof(char));

    strncpy(dest, value, size);
    dest[size] = '\0';

    return dest;
}

void safe_free_string(char *&value) {
    if (value != NULL) {
        free(value);
        value = NULL;
    }
}

typedef std::unique_ptr<char, void(*)(char*&)> ConfArg;

static bool matches(const char* expected, const ConfArg& val) {
    return std::strcmp(val.get(), expected) == 0;
}

spdlog::level::level_enum resolv_log_level(ConfArg& level) {
    if (matches("off", level)) {
        return spdlog::level::off;
    } else if (matches("critical", level)) {
        return spdlog::level::critical;
    } else if (matches("err", level)) {
        return spdlog::level::err;
    } else if (matches("warn", level)) {
        return spdlog::level::warn;
    } else if (matches("debug", level)) {
        return spdlog::level::debug;
    } else if (matches("trace", level)) {
        return spdlog::level::trace;
    } else {
        return spdlog::level::info;
    }
}

bool is_one_char(const char* value) {
    return (strlen(value) == 1) || (value[1] == ',');
}

bool is_yes(const char* value) {
    return is_one_char(value) &&
        ((value[0] == 'y') || (value[0] == 'Y'));
}

