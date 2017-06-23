#include "ftrace/tracer.hh"
#include <cassert>

bool dir_exists(const char *path) {
    struct stat info;
    if(stat(path, &info) != 0)
        return false;
    return S_ISDIR(info.st_mode);
}

bool file_exists(const char *path) {
    struct stat info;
    if(stat(path, &info) != 0)
        return false;
    return S_ISREG(info.st_mode);
}

#define TRACE_ON "/tracing_on"
#define TRACE_OPTIONS "/trace_options"
#define INSTANCES_DIR "/instances"
#define INSTANCE "/fk-prof-rec"
#define EVENTS_DIR "/events"
#define SCHED_SWITCH_DIR "/events/sched/sched_switch"
#define SCHED_WAKEUP_DIR "/events/sched/sched_wakeup"
#define SYSCALL_ENTER_DIR "/events/raw_syscalls/sys_enter"
#define SYSCALL_EXIT_DIR "/events/raw_syscalls/sys_exit"
#define ENABLE_FILE "/enable"
#define CPU_DIR_PREFIX "/per_cpu/cpu"
#define PER_CPU_RAW_TRACE_PIPE "/trace_pipe_raw"
#define PER_CPU_STATS "/stats"


static int open_file(const std::string& instance_path, const std::string& subpath) {
    auto path = instance_path + subpath;
    if (! file_exists(path.c_str())) {
        logger->error("Couldn't find file: {}, perhaps this version of Linux is not supported?", path);
        throw std::runtime_error("Couldn't find file: " + path);
    }
}

std::uint32_t stou(const std::string& str) {
    std::size_t end;
    std::uint64_t result = std::stoul(str, &end, 10);
    assert(end == str.length());
    if (result > std::numeric_limits<std::uint32_t>::max()) {
        throw std::out_of_range("stou");
    }
    return result;
}

std::uint32_t cpus_present() {
    std::fstream in{"/sys/devices/system/cpu/present", std::ios_base::in};
    std::string content;
    in >> content;
    auto idx = content.find('-');
    return stou(content.substr(idx + 1));
}

static void populate_data_links(const std::string& instance_path, std::list<ftrace::Tracer::DataLink>& dls, function<void(DataLink&)>& data_link_listener) {
    std::uint32_t nr_cpu = cpus_present();
    for (std::uint32_t c = 0; c < nr_cpu; c++) {
        auto cpu_dir = instance_path, CPU_DIR_PREFIX + std::to_string(c);
        dls.emplace(open_file(instance_path, cpu_dir + PER_CPU_RAW_TRACE_PIPE, O_RDONLY),
                    open_file(instance_path, cpu_dir + PER_CPU_STATS, O_RDONLY),
                    c);
        data_link_listener(dls.back());
    }
}

void append(int fd, const std::string buff) {
    auto wr_sz = write(fd, buff.c_str(), buff.length());
    assert(wr_sz == buff.length());
}

ftrace::Tracer::Tracer(const std::string& tracing_dir, function<void(DataLink&)> data_link_listener) {
    VALIDATE_HAS(tracing_dir, INSTANCES_DIR, dir_exists);
    instance_path = tracing_dir + INSTANCES_DIR + INSTANCE;
    if (! dir_exists(instance_path.c_str())) {
        auto ret = mkdir(instance_path.c_str(), S_IRWXU | S_IXGRP | S_IRGRP | S_IXGRP | S_IROTH | S_IXOTH);
        assert(ret == 0);
    }
    ctrl_fds.tracing_on = open_file(instance_path, TRACING_ON, O_WRONLY | O_TRUNC);
    ctrl_fds.trace_options = open_file(instance_path, TRACE_OPTIONS,  O_WRONLY | O_APPEND);
    ctrl_fds.sched_switch_enable = open_file(instance_path, SCHED_SWITCH_DIR + ENABLE_FILE,  O_WRONLY | O_APPEND);
    ctrl_fds.sched_wakeup_enable = open_file(instance_path, SCHED_WAKEUP_DIR + ENABLE_FILE,  O_WRONLY | O_APPEND);
    ctrl_fds.syscall_enter_enable = open_file(instance_path, SYSCALL_ENTER_DIR + ENABLE_FILE,  O_WRONLY | O_APPEND);
    ctrl_fds.syscall_exit_enable = open_file(instance_path, SYSCALL_EXIT_DIR + ENABLE_FILE,  O_WRONLY | O_APPEND);
    
    populate_data_links(instance_path, dls, data_link_listener);

    append(ctrl_fds.trace_options, "bin");
    append(ctrl_fds.trace_options, "nooverwrite");
}

void ftrace::Tracer::trace_on(pid_t pid, Listener& l) {
    bool start = tracees.empty();
    tracees[pid] = l;
    if (start) {
        start_tracing();
    }
}

#define ON "1"
#define OFF "0"

void ftrace::Tracer::start() {
    append(ctrl_fds.syscall_exit_enable,  ON);
    append(ctrl_fds.syscall_enter_enable, ON);
    append(ctrl_fds.sched_wakeup_enable,  ON);
    append(ctrl_fds.sched_switch_enable,  ON);
    append(ctrl_fds.tracing_on,           ON);
}

void ftrace::Tracer::stop() {
    append(ctrl_fds.syscall_exit_enable,  OFF);
    append(ctrl_fds.syscall_enter_enable, OFF);
    append(ctrl_fds.sched_wakeup_enable,  OFF);
    append(ctrl_fds.sched_switch_enable,  OFF);
    append(ctrl_fds.tracing_on,           OFF);
}

void ftrace::Tracer::process(DataLink& dl) {
    
}
