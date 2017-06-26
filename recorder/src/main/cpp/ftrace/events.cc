#include "events.hh"
#include "logging.hh"

/**
   There is no documentation or help for the magic that happens here, but who needs documentation when we have source-tree?

   Majority of this is fairly old and has been largely untouched since 2012, I was reffering to 4.5.0 while writing this.

   For page-parsing, check header format in <debugfs>/tracing or <tracefs> files events/header_event and events/header_page

   But header_event has an un-intuitive format that has no documentation, so please refer to <linux>/kernel/trace/ring_buffer.c and <linux>/include/linux/ring_buffer.h

   Check out enum ring_buffer_type and rb_event_length, rb_event_ts_length, rb_buffer_event_length etc to understand how it is used in various cases in ring_buffer.c.

   Happy hacking!

   -jj
 */

ftrace::EventReader::EventReader(const std::string& _events_dir, EventHandler& _handler) : handler(_handler) {

}

ftrace::EventReader::~EventReader() {}

std::uint32_t r_u32(const std::uint8_t* buff, std::size_t& consumed) {
    auto val = reinterpret_cast<const std::uint32_t*>(buff + consumed);
    consumed += sizeof(std::uint32_t);
    return *val;
}

std::int64_t r_i64(const std::uint8_t* buff, std::size_t& consumed) {
    auto val = reinterpret_cast<const std::int64_t*>(buff + consumed);
    consumed += sizeof(std::int64_t);
    return *val;
}

struct __attribute__((packed)) EvtHdrPrefix {
    std::uint32_t type_len:5, time_delta:27;
};

#define RINGBUF_TYPE_DATA_TYPE_LEN_MAX 28
#define RINGBUF_TYPE_PADDING 29
#define RINGBUF_TYPE_TIME_EXTEND 30

std::size_t ftrace::EventReader::read(std::int32_t cpu, std::uint64_t timestamp_ns, const std::uint8_t* buff, std::size_t len) const {
    std::size_t consumed = 0;
    while (consumed < len) {
        SPDLOG_TRACE(logger, "Buff start pos: {}", static_cast<const void*>(buff + consumed));
        assert((len - consumed) > sizeof(EvtHdrPrefix));
        auto fhp = reinterpret_cast<const EvtHdrPrefix*>(buff + consumed);
        consumed += sizeof(EvtHdrPrefix);
        SPDLOG_TRACE(logger, "Type-len: {}", fhp->type_len);
        if (fhp->type_len == 0) {
            auto arr_0 = r_u32(buff, consumed);
            timestamp_ns += fhp->time_delta;
            consumed += read_payload(buff + consumed, arr_0, timestamp_ns, cpu);
        } else if (fhp->type_len <= RINGBUF_TYPE_DATA_TYPE_LEN_MAX) {
            std::size_t len = fhp->type_len;
            len <<= 2;
            timestamp_ns += fhp->time_delta;
            consumed += read_payload(buff + consumed, len, timestamp_ns, cpu);
        } else if (fhp->type_len == RINGBUF_TYPE_PADDING) {
            if (fhp->time_delta == 0) {
                SPDLOG_TRACE(logger, "Padding tail-record");
                consumed = len;
                break;
            }
            auto arr_0 = r_u32(buff, consumed);
            consumed += arr_0;
            timestamp_ns += fhp->time_delta;
        } else if (fhp->type_len == RINGBUF_TYPE_TIME_EXTEND) {
            auto arr_0 = r_u32(buff, consumed);
            std::uint64_t time_delta = (arr_0 << 27) + fhp->time_delta;
            SPDLOG_TRACE("Time δ: {}", time_δ);
            timestamp_ns += time_delta;
        } else {
            logger->error("Encountered invalid type_len value {}, which was unexpected", fhp->type_len);
            throw std::runtime_error("Encountered invalid type_len value");
        }
    }
    assert(consumed == len);
    return consumed;
}

std::size_t ftrace::EventReader::read_payload(const std::uint8_t* buff, std::size_t len, std::uint64_t timestamp_ns, std::int32_t cpu) const {

    return 0;
}

ftrace::PageReader::PageReader(const EventReader& _e_rdr, std::size_t _pg_sz) : e_rdr(_e_rdr), pg_sz(_pg_sz) {}

ftrace::PageReader::~PageReader() {}

struct PgHeader {
    std::uint64_t ts;
    std::uint64_t data_len;
};

std::size_t ftrace::PageReader::read(std::int32_t cpu, const std::uint8_t* page) {
    std::size_t consumed = 0;
    auto pg_hdr = reinterpret_cast<const PgHeader*>(page);
    consumed += sizeof(PgHeader);
    consumed += e_rdr.read(cpu, pg_hdr->ts, page + consumed, pg_hdr->data_len);
    assert(consumed == pg_hdr->data_len + sizeof(PgHeader));
    return consumed;
}
