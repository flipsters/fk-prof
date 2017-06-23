#ifndef FTRACE_PROTO_H
#define FTRACE_PROTO_H

#include <cstdint>
#include <sys/types.h>

/*
  Pkt:
  0    4    8   12   16   20   24   28   32
  |----|----|----|----|----|----|----|----|
  v    v    v    v    v    v    v    v    v
  
  +----+----------------------------------+
  |v:4 |            ....                  |
  +----+            ....                  |
  |                 ....                  |
  +---------------------------------------+

  (v0, supported, current)
  +----+--------------+-------------------+
  | 0x0|type:12       |len:16             |
  +----+--------------+-------------------|
  |                 ....                  |
  |             data (0..64kB)            |
  |                 ....                  |
  +---------------------------------------+
*/

namespace ftrace {
    namespace v0 {
        extern const std::uint8_t VERSION;

        struct __attribute__((packed)) Header {
            std::uint8_t     v : 4;
            std::uint16_t type : 12;
            std::uint16_t len  : 16;
        };

        enum class PktType : std::uint16_t {
            add_tid           = 0,
            del_tid           = 1,
            lost_events       = 2,
            sched_switch      = 3,
            sched_wakeup      = 4
        };

        namespace data {
            typedef pid_t AddTid;
            
            typedef pid_t DelTid;
            
            typedef std::uint64_t LostEvents;
            
            struct __attribute__((packed)) SchedSwitch {
                std::uint64_t timestamp;
                std::uint32_t in_tid;
                std::uint32_t out_tid;
                std::int64_t syscall_nr;
                std::uint32_t cpu;
                bool syscall; //false => no syscall
                bool voluntary; // true => out_tid was not running
            };

            struct __attribute__((packed)) SchedWakeup {
                std::uint64_t timestamp;
                std::uint32_t cpu;
                std::uint32_t tid;
            };
        }
    }

    namespace v_curr = v0;
}

#endif
