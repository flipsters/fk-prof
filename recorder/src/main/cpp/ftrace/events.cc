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
    //TODO: assert event header prefix content matchs
}

ftrace::EventReader::~EventReader() {}

std::size_t r_u32(const std::uint8_t* buff, std::size_t remaining, std::uint32_t& val) {
    auto sz = sizeof(std::uint32_t);
    assert(remaining > sz);
    val = *reinterpret_cast<const std::uint32_t*>(buff);
    return sz;
}

std::size_t r_i64(const std::uint8_t* buff, std::size_t remaining, std::int64_t& val) {
    auto sz = sizeof(std::int64_t);
    assert(remaining > sz);
    val = *reinterpret_cast<const std::int64_t*>(buff);
    return sz;
}

struct __attribute__((packed)) EvtHdrPrefix {
    std::uint32_t type_len:5, time_delta:27;
};

#define RINGBUF_TYPE_DATA_TYPE_LEN_MAX 28
#define RINGBUF_TYPE_PADDING 29
#define RINGBUF_TYPE_TIME_EXTEND 30

void mark_consumed(std::size_t& consumed, std::size_t& remaining, std::size_t len) {
    consumed += len;
    remaining -= len;
}

#define MARK_CONSUMED(len)                      \
    {                                           \
        buff += len;                            \
        remaining -= len;                       \
    }

#define CONSUMED (buff - buff_start)

std::size_t ftrace::EventReader::read(std::int32_t cpu, std::uint64_t timestamp_ns, const std::uint8_t* buff, std::size_t remaining) const {
    auto buff_start = buff;
    SPDLOG_TRACE(logger, "Data in page: {}, start: {}, end: {}", pg_hdr.data_len, static_cast<void*>(buff_start), static_cast<void*>(buff_start + remaining));
    while (remaining > 0) {
        SPDLOG_TRACE(logger, "Curr Pos: '{}'", static_cast<void*>(buff));
        auto fhp = reinterpret_cast<const EvtHdrPrefix*>(buff);
        MARK_CONSUMED(sizeof(EvtHdrPrefix));
        SPDLOG_TRACE(logger, "Type-len: {}", fhp->type_len);
        if (fhp->type_len == 0) {
            std::uint32_t arr_0;
            MARK_CONSUMED(r_u32(buff, remaining, arr_0));
            timestamp_ns += fhp->time_delta;
            assert(remaining >= arr_0);
            MARK_CONSUMED(read_payload(buff, arr_0, timestamp_ns, cpu));
        } else if (fhp->type_len <= RINGBUF_TYPE_DATA_TYPE_LEN_MAX) {
            std::uint32_t len = fhp->type_len;
            len <<= 2;
            timestamp_ns += fhp->time_delta;
            assert(remaining >= len);
            MARK_CONSUMED(read_payload(buff, len, timestamp_ns, cpu));
        } else if (fhp->type_len == RINGBUF_TYPE_PADDING) {
            if (fhp->time_delta == 0) {
                SPDLOG_TRACE(logger, "Padding tail-record");
                return CONSUMED;
            }
            std::uint32_t arr_0;
            MARK_CONSUMED(r_u32(buff, remaining, arr_0));
            assert(remaining >= arr_0);
            MARK_CONSUMED(arr_0);
            timestamp_ns += fhp->time_delta;
        } else if (fhp->type_len == RINGBUF_TYPE_TIME_EXTEND) {
            std::uint32_t arr_0;
            MARK_CONSUMED(r_u32(buff, remaining, arr_0));
            std::uint64_t time_delta = (arr_0 << 27) + fhp->time_delta;
            SPDLOG_TRACE(logger, "Time δ: +{}", time_delta);
            timestamp_ns += time_delta;
        } else {
            logger->error("Encounterd unkonwn type_len value {}", fhp->type_len);
            throw std::runtime_error("Got unknown type_len value");
        }
    }
    return CONSUMED;
}

std::size_t ftrace::EventReader::read_payload(const std::uint8_t* buff, std::size_t remaining, std::uint64_t timestamp_ns, std::int32_t cpu) const {
    auto buff_start = buff;

    event::CommonFields common_fields;
    assert(remaining > common_header_rdr->repr_length());
    MARK_CONSUMED(common_header_rdr->read(buff, remaining, common_fields));
    
    const auto type_id = common_fields.common_type;
    
    if (type_id == sys_entry_id) {
        event::SyscallEntry e;
        assert(remaining >= sys_entry_rdr->repr_length());
        MARK_CONSUMED(sys_entry_rdr->read(buff, remaining, e));
        handler.handle(cpu, timestamp_ns, common_fields, e);
    } else if (type_id == sys_exit_id) {

    } else if (type_id == sched_switch_id) {

    } else if (type_id == sched_wakeup_id) {

    } else {
        logger->error("Encountered event with unknown type-id: {}", type_id);
        throw std::runtime_error("Encountered event with unknown type-id");
    }
    return CONSUMED;
}

ftrace::PageReader::PageReader(const EventReader& _e_rdr, std::size_t _pg_sz) : e_rdr(_e_rdr), pg_sz(_pg_sz) {
    //assert page_header content matches
}

ftrace::PageReader::~PageReader() {}

struct PgHeader {
    std::uint64_t ts;
    std::uint64_t data_len;
};

std::size_t ftrace::PageReader::read(std::int32_t cpu, const std::uint8_t* buff) {
    auto buff_start = buff;
    std::size_t remaining = pg_sz;
    auto pg_hdr = reinterpret_cast<const PgHeader*>(buff);
    MARK_CONSUMED(sizeof(PgHeader));
    assert(pg_hdr->data_len <= remaining);
    MARK_CONSUMED(e_rdr.read(cpu, pg_hdr->ts, buff, pg_hdr->data_len));
    auto consumed = CONSUMED;
    assert(consumed == (pg_hdr->data_len + sizeof(PgHeader)));
    return consumed;
}
