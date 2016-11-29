#include "monitor_objects.h"
#include <memory>
#include "globals.h"
#include <iostream>
#include <chrono>

const MonitorObjects::MonitorId MonitorObjects::xlate(jvmtiEnv *jvmti, JNIEnv *jni_env, jobject object, ThreadBucket *threadInfo, LoadedClasses &loaded_classes, NewMonitorHandler new_monitor_handler) {
    jlong tag;
    auto e = jvmti->GetTag(object, &tag);
    if (e != JVMTI_ERROR_NONE) {
        std::cerr << "\nCouldn't get tag of a monitor object (error: " << e << ")\n";
        tag = 0;
    } else if (tag != 0) {
        return tag;
    }
    
    jclass clazz = jni_env->GetObjectClass(object);
    LoadedClasses::ClassId class_id = loaded_classes.xlate(jvmti, clazz, [](LoadedClasses::ClassSigPtr ptr) {
        fprintf(stdout, "\nNew Monitor Class => id=%d,ksig=%s,gsig=%s\n", ptr->class_id, ptr->ksig.c_str(), ptr->gsig.c_str());
    });

    LoadedClasses::ClassSigPtr sig;
    if(!loaded_classes.find(class_id, sig)) {
        //TODO: Can this happen ever?
        fprintf(stderr, "\nCannot find monitor class from loaded classes");
    } else {
        fprintf(stdout, "type=monitorclass,class_id=%d,class_sig=%s\n", sig->class_id, sig->ksig.c_str());
    }

    MonitorId monitor_id = new_monitor_id.fetch_add(1, std::memory_order_relaxed);
    MonitorInfoPtr info_ptr(new MonitorInfo);
    info_ptr->monitor_id = monitor_id;
    info_ptr->class_id = class_id;
    
    if (monitors.insert(monitor_id, info_ptr)) {
        if (do_report.load(std::memory_order_acquire)) {
            fprintf(contentions_out, "%s\t%d\t%d\n", "newmonitor", info_ptr->monitor_id, info_ptr->class_id);
            fprintf(waits_out, "%s\t%d\t%d\n", "newmonitor", info_ptr->monitor_id, info_ptr->class_id);
        }

        if (new_monitor_handler != nullptr) {
            new_monitor_handler(info_ptr);
        }
        tag = monitor_id;
        e = jvmti->SetTag(object, tag);
        if (e != JVMTI_ERROR_NONE) {
            std::cerr << "\nCouldn't tag monitor object (error: " << e << ")\n";
        } else {
            return tag;
        }
    }
    return 0;
}

bool MonitorObjects::find(MonitorId &monitor_id, MonitorInfoPtr &info_ptr) {
    return monitors.find(monitor_id, info_ptr);
}

void MonitorObjects::stop_reporting() {
    do_report.store(false, std::memory_order_release);
    fclose(contentions_out);
    fclose(waits_out);
}

void MonitorObjects::report_mc(std::string mc_state, MonitorObjects::MonitorId monitor_id, std::string thread_name, std::chrono::microseconds epoch) {
    if (do_report.load(std::memory_order_acquire)) {
        fprintf(contentions_out, "%s\t%d\t%s\t%lld\n", mc_state.c_str(), monitor_id, thread_name.c_str(), epoch.count());
    }
}

void MonitorObjects::report_m_wait(MonitorObjects::MonitorId monitor_id, std::string thread_name, std::chrono::microseconds epoch, jlong timeout) {
    if (do_report.load(std::memory_order_acquire)) {
        fprintf(waits_out, "%s\t%d\t%s\t%lld\t%ld\n", "mwbegin", monitor_id, thread_name.c_str(), epoch.count(), timeout);
    }
}

void MonitorObjects::report_m_waited(MonitorObjects::MonitorId monitor_id, std::string thread_name, std::chrono::microseconds epoch, jboolean timed_out) {
    if (do_report.load(std::memory_order_acquire)) {
        fprintf(waits_out, "%s\t%d\t%s\t%lld\t%d\n", "mwend", monitor_id, thread_name.c_str(), epoch.count(), timed_out);
    }
}
