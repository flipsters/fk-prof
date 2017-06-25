#include "ftrace/tracer.hh"
#include <cassert>
#include <sys/stat.h>
#include <sys/types.h>
#include <unistd.h>
#include <fcntl.h>
#include <fstream>
#include "logging.hh"

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

#define TRACING_ON "/tracing_on"
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


void throw_file_not_found(const std::string& path, const std::string& type = "file") {
    logger->error("Couldn't find {}: {}, perhaps this version of Linux is not supported?", type, path);
    throw std::runtime_error("Couldn't find " + type + ": " + path);
}

static int open_file(const std::string& instance_path, const std::string& subpath, int flags) {
    auto path = instance_path + subpath;
    if (! file_exists(path.c_str())) {
        throw_file_not_found(path);
    }
    return open(path.c_str(), flags);
}

template <typename T> T stoun(const std::string& str) {
    std::size_t end;
    std::uint64_t result = std::stoul(str, &end, 10);
    assert(end == str.length());
    if (result > std::numeric_limits<T>::max()) {
        throw std::out_of_range("stoun");
    }
    return result;
}

std::uint16_t cpus_present() {
    std::fstream in{"/sys/devices/system/cpu/present", std::ios_base::in};
    std::string content;
    in >> content;
    auto idx = content.find('-');
    return stoun<std::uint16_t>(content.substr(idx + 1));
}

static void populate_data_links(const std::string& instance_path, std::list<ftrace::Tracer::DataLink>& dls, std::function<void(const ftrace::Tracer::DataLink&)>& data_link_listener) {
    std::uint16_t nr_cpu = cpus_present();
    for (std::uint16_t c = 0; c < nr_cpu; c++) {
        auto cpu_dir = instance_path + CPU_DIR_PREFIX + std::to_string(c);
        auto ring_fd = open_file(instance_path, cpu_dir + PER_CPU_RAW_TRACE_PIPE, O_RDONLY);
        auto stats_fd = open_file(instance_path, cpu_dir + PER_CPU_STATS, O_RDONLY);
        dls.push_back({ring_fd, stats_fd, c});
        data_link_listener(dls.back());
    }
}

void append(int fd, const std::string buff) {
    auto wr_sz = write(fd, buff.c_str(), buff.length());
    assert(wr_sz == buff.length());
}

ftrace::Tracer::Tracer(const std::string& tracing_dir, std::function<void(const ftrace::Tracer::DataLink&)> data_link_listener) {
    auto instances_dir = tracing_dir + INSTANCES_DIR;
    if (! dir_exists(instances_dir.c_str())) {
        throw_file_not_found(instances_dir, "dir");
    }
    instance_path = tracing_dir + INSTANCES_DIR + INSTANCE;
    if (! dir_exists(instance_path.c_str())) {
        auto ret = mkdir(instance_path.c_str(), S_IRWXU | S_IXGRP | S_IRGRP | S_IXGRP | S_IROTH | S_IXOTH);
        assert(ret == 0);
    }
    ctrl_fds.tracing_on = open_file(instance_path, TRACING_ON, O_WRONLY | O_TRUNC);
    ctrl_fds.trace_options = open_file(instance_path, TRACE_OPTIONS,  O_WRONLY | O_APPEND);
    ctrl_fds.sched_switch_enable = open_file(instance_path, SCHED_SWITCH_DIR ENABLE_FILE,  O_WRONLY | O_APPEND);
    ctrl_fds.sched_wakeup_enable = open_file(instance_path, SCHED_WAKEUP_DIR ENABLE_FILE,  O_WRONLY | O_APPEND);
    ctrl_fds.syscall_enter_enable = open_file(instance_path, SYSCALL_ENTER_DIR ENABLE_FILE,  O_WRONLY | O_APPEND);
    ctrl_fds.syscall_exit_enable = open_file(instance_path, SYSCALL_EXIT_DIR ENABLE_FILE,  O_WRONLY | O_APPEND);
    
    populate_data_links(instance_path, dls, data_link_listener);

    append(ctrl_fds.trace_options, "bin");
    append(ctrl_fds.trace_options, "nooverwrite");
}

ftrace::Tracer::~Tracer() {
    //TODO: impl me!
}

void ftrace::Tracer::trace_on(pid_t pid, Listener* l) {
    bool is_first_pid = tracees.empty();
    tracees[pid] = l;
    if (is_first_pid) {
        start();
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
