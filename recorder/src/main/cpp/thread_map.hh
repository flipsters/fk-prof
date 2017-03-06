#ifndef THREAD_MAP_H
#define THREAD_MAP_H

#include <jvmti.h>
#include <jni.h>
#include <string.h>
#include "concurrent_map.hh"
#include "perf_ctx.hh"
#include "globals.hh"

int gettid();

template <typename PType>
struct PointerHasher {
	/* Numerical Recipes, 3rd Edition */
	static int64_t hash(void *p) {
		int64_t v = (int64_t)p / sizeof(PType);
		v = v * 3935559000370003845 + 2691343689449507681;

  		v ^= v >> 21;
  		v ^= v << 37;
  		v ^= v >>  4;

  		v *= 4768777513237032717;

  		v ^= v << 20;
  		v ^= v >> 41;
  		v ^= v <<  5;

  		return v;
	}
};

const int kInitialMapSize = 256;

class GCHelper {
public:
	static map::GC::EpochType attach() {
		return map::DefaultGC.attachThread();
	}

	static void detach(map::GC::EpochType &localEpoch) {
		if (localEpoch != map::GC::kEpochInitial)
			map::DefaultGC.detachThread(localEpoch);
	}

	static void safepoint(map::GC::EpochType &localEpoch) {
		map::DefaultGC.safepoint(localEpoch);
	}

	static void signalSafepoint(map::GC::EpochType &localEpoch) {
		map::DefaultGC.ss_safepoint(localEpoch);
	}
};

struct ThreadBucket {
	const int tid;
	char *name;
    std::uint32_t priority;
    bool is_daemon;
	std::atomic_int refs;
	map::GC::EpochType localEpoch;
    PerfCtx::ThreadTracker ctx_tracker;

	ThreadBucket(int id, const char *n, std::uint32_t _priority, bool _is_daemon) : tid(id), priority(_priority), is_daemon(_is_daemon), refs(1), ctx_tracker(*GlobalCtx::ctx_reg, *GlobalCtx::prob_pct, id) {
		int len = strlen(n) + 1;
		name = new char[len];
		std::copy(n, n + len, name);
	}

	void release() {
		int prev = refs.fetch_sub(1, std::memory_order_acquire);
		if (prev == 1)
			delete this;
	}

	~ThreadBucket() {
		delete[] name;
	}
};


template <typename MapProvider>
class ThreadMapBase {
private:
	MapProvider map;

	static ThreadBucket *acq_bucket(ThreadBucket *tb) {
		if (tb != nullptr) {
			int prev = tb->refs.fetch_add(1, std::memory_order_relaxed);
			if (prev > 0) {
				return tb;
			}
			tb->refs.fetch_sub(1, std::memory_order_relaxed);
		}
		return nullptr;
	}

public:

	ThreadMapBase(int capacity = kInitialMapSize) : map(capacity) {}

	void put(JNIEnv *jni_env, const char *name, jint priority, jboolean is_daemon) {
		put(jni_env, name, gettid(), priority, is_daemon);
	}

	void put(JNIEnv *jni_env, const char *name, int tid, jint priority, jboolean is_daemon) {
		// constructor == call to acquire
        logger->info("Thread started - '{}' (jniEnv: {}, tid: {}, priority: {}, is_daemon: {})", name, reinterpret_cast<std::uint64_t>(jni_env), tid, priority, is_daemon);
		ThreadBucket *info = new ThreadBucket(tid, name, static_cast<std::uint32_t>(priority), static_cast<bool>(is_daemon));
        info->localEpoch = GCHelper::attach();
		ThreadBucket *old = (ThreadBucket*)map.put((map::KeyType)jni_env, (map::ValueType)info);
		if (old != nullptr)
			old->release();
		GCHelper::safepoint(info->localEpoch); // each thread inserts once
	}

	ThreadBucket *get(JNIEnv *jni_env) {
		ThreadBucket *info = acq_bucket((ThreadBucket*)map.get((map::KeyType)jni_env));
		if (info != nullptr)
			GCHelper::signalSafepoint(info->localEpoch);
		return info;
	}

	void remove(JNIEnv *jni_env) {
		ThreadBucket *info = (ThreadBucket*)map.remove((map::KeyType)jni_env);
		if (info != nullptr) {
            logger->info("Thread stopped - '{}' (jniEnv: {}, tid: {}, priority: {}, is_daemon: {})", info->name, reinterpret_cast<std::uint64_t>(jni_env), info->tid, info->priority, info->is_daemon);
			GCHelper::detach(info->localEpoch);
			info->release();
		}
	}
};

typedef ThreadMapBase<map::ConcurrentMapProvider<PointerHasher<JNIEnv>, true> > ThreadMap;
ThreadMap& get_thread_map();

#endif
