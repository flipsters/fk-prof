#include "scheduler.hh"
#include <thread>
#include <iostream>

void Scheduler::schedule(Time::Pt time, Scheduler::Cb task) {
    auto sec_in_future = std::chrono::duration_cast<Time::sec>(time - Time::now());
    logger->debug("Scheduling {} {}s in future", typeid(task).name(), sec_in_future.count());//TODO: handle me better
    std::lock_guard<std::mutex> g(m);
    q.push({time, task});
    auto top_expiry = q.top().first;
    if (time < top_expiry) {
        nearest_entry_changed.notify_one();
    }
}

Time::usec usec_to_expiry(const Time::Pt& tm) {
    return std::chrono::duration_cast<Time::usec>(tm - Time::now());
}

bool is_expired(const Time::Pt& tm) {
    return usec_to_expiry(tm).count() <= 0;
}

typedef std::unique_lock<std::mutex> Lock;

void block_for_expiry(const Scheduler::Q& q, Lock& l, std::condition_variable& expiry_plan) {
    while (true) {
        auto top_expiry = q.top().first;
        if (is_expired(top_expiry)) break;
        expiry_plan.wait_until(l, top_expiry);
    }
}

struct Unlocker {
    Lock& l;

    Unlocker(Lock& _l) : l(_l) {
        l.unlock();
    }

    ~Unlocker() {
        l.lock();
    }
};

void execute_top(Scheduler::Q& q, Lock& l) {
    Scheduler::Ent sched_for = std::move(q.top());
    q.pop();
    logger->debug("Scheduler is now triggering {}", typeid(sched_for).name());
    {
        Unlocker ul(l);
        sched_for.second();
    }
}

bool Scheduler::poll() {
    Lock l(m);
    if (q.empty()) {
        return false;
    }

    block_for_expiry(q, l, nearest_entry_changed);

    execute_top(q, l);

    while ((! q.empty()) && is_expired(q.top().first)) {
        execute_top(q, l);
    }

    return true;
}
