#ifndef CONFIG_SHARED_H
#define CONFIG_SHARED_H

#include <cstdint>
#include <string>

static const std::uint32_t DEFAULT_METRICS_DEST_PORT = 11514;

static const std::string metrics_tsdb_tag = "prefix_override=fkpr";

#endif
