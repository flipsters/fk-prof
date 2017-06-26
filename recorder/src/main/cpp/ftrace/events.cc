#include "events.hh"

ftrace::EventReader::EventReader(const std::string& _events_dir, EventHandler& _handler) : handler(_handler) {

}

ftrace::EventReader::~EventReader() {}

ftrace::PageReader::PageReader(const EventReader& _e_rdr, std::size_t _pg_sz) : e_rdr(_e_rdr), pg_sz(_pg_sz) {}

ftrace::PageReader::~PageReader() {}

std::size_t ftrace::PageReader::read(const std::uint8_t* page) {
    return 0;
}
