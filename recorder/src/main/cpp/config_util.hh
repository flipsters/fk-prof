#ifndef CONFIG_UTIL_H
#define CONFIG_UTIL_H

#include <memory>
#include "logging.hh"

#ifdef IN_TEST
char *safe_copy_string(const char *value, const char *next);
void safe_free_string(char *&value);
#endif

char *safe_copy_string(const char *value, const char *next);

void safe_free_string(char *&value);

typedef std::unique_ptr<char, void(*)(char*&)> ConfArg;

spdlog::level::level_enum resolv_log_level(ConfArg& level);

#define PRINT_FIELD_VALUE(field, value) {                       \
        if (i > 0) os << ",";                                   \
        os << " " << #field << " : " << "'" << value << "'";    \
        i++;                                                    \
    }

#define PRINT_FIELD(field) {                            \
        PRINT_FIELD_VALUE(field, config->field)         \
    }

bool is_one_char(const char* value);

bool is_yes(const char* value);

#define ENSURE_NOT_NULL(param)                                          \
    {                                                                   \
        if (param == nullptr) {                                         \
            logger->warn("Configuration is NOT valid, '"#param"' has not been provided"); \
            is_valid = false;                                           \
        }                                                               \
    }

#define ENSURE_NOT_EMPTY(param)                                         \
    {                                                                   \
        if (param.length() == 0) {                                      \
            logger->warn("Configuration is NOT valid, '"#param"' is an empty-string"); \
            is_valid = false;                                           \
        }                                                               \
    }

#define ENSURE_GT(param, lower_bound)                                   \
    {                                                                   \
        if (param <= lower_bound) {                                     \
            logger->warn("Configuration is NOT valid, '"#param"' value {} is too small (it is expected to be > {})", param, lower_bound); \
            is_valid = false;                                           \
        }                                                               \
    }

#endif
