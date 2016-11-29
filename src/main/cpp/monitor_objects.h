#ifndef MONITOR_OBJECTS_H
#define MONITOR_OBJECTS_H

#include <cuckoohash_map.hh>
#include <city_hasher.hh>
#include <jni.h>
#include <cstdint>
#include <utility>
#include <string>
#include <memory>
#include <stdio.h>
#include <jvmti.h>
#include "loaded_classes.h"
#include "thread_map.h"

class MonitorObjects {
public:
    typedef std::uint32_t MonitorId;
    struct MonitorInfo {
        MonitorId monitor_id;
        LoadedClasses::ClassId class_id;
    };
    typedef std::shared_ptr<MonitorInfo> MonitorInfoPtr;
    typedef std::function<void(MonitorInfoPtr)> NewMonitorHandler;

    FILE *contentions_out;
    FILE *waits_out;
    std::atomic<bool> do_report;
    
private:
    typedef cuckoohash_map<MonitorId, MonitorInfoPtr, CityHasher<MonitorId>> MonitorsMap;
    
    MonitorsMap monitors;
    std::atomic<MonitorId> new_monitor_id{1};
    
public:
    MonitorObjects() {
        monitors.reserve(100000);
        contentions_out = fopen("/tmp/MONITOR_CONTENTIONS.tsv", "w+");
        waits_out = fopen("/tmp/MONITOR_WAITS.tsv", "w+");
        fprintf(contentions_out, "event\tmonitor_id\tsecondary_id\tepoch\n");
        fprintf(waits_out, "event\tmonitor_id\tsecondary_id\tepoch\tattribute\n");
        do_report.store(true, std::memory_order_release);
    }
    ~MonitorObjects() {}

    void stop_reporting();
    void report_mc(std::string mc_state, MonitorObjects::MonitorId monitor_id, std::string thread_name, std::chrono::microseconds epoch);
    void report_m_wait(MonitorObjects::MonitorId monitor_id, std::string thread_name, std::chrono::microseconds epoch, jlong timeout);
    void report_m_waited(MonitorObjects::MonitorId monitor_id, std::string thread_name, std::chrono::microseconds epoch, jboolean timed_out);

    const MonitorId xlate(jvmtiEnv *jvmti, JNIEnv *jni_env, jobject object, ThreadBucket *threadInfo, LoadedClasses &loaded_classes, NewMonitorHandler new_monitor_handler);
    bool find(MonitorId &class_id, MonitorInfoPtr &info_ptr);
};

#endif
