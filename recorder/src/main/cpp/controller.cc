#include "controller.hh"
#include <chrono>
#include <curl/curl.h>
#include "buff.hh"
#include "blocking_ring_buffer.hh"

void controllerRunnable(jvmtiEnv *jvmti_env, JNIEnv *jni_env, void *arg) {
    auto control = static_cast<Controller*>(arg);
    control->run();
}

void Controller::start() {
    keep_running.store(true, std::memory_order_relaxed);
    thd_proc = start_new_thd(jvm, jvmti, "Fk-Prof Controller Thread", controllerRunnable, this);
}

void Controller::stop() {
    keep_running.store(false, std::memory_order_relaxed);
    await_thd_death(thd_proc);
    thd_proc.reset();
}

bool Controller::is_running() const {
    return keep_running.load();
}

struct CurlInit {
    CurlInit() {
        curl_global_init(CURL_GLOBAL_ALL);
    }
    ~CurlInit() {
        curl_global_cleanup();
    }
};

static void time_now_str(std::function<void(const char*)> fn) {
    std::time_t t = std::time(nullptr);
    std::tm tm = *std::localtime(&t);
    char buffer[120];
    strftime(buffer, sizeof(buffer),"%Y-%m-%dT%H:%M:%S%z", &tm);
    fn(buffer);
}

static void populate_recorder_info(recording::RecorderInfo& ri, const ConfigurationOptions& cfg, const Time::Pt& start_time) {
    ri.set_ip(cfg.ip);
    ri.set_hostname(cfg.host);
    ri.set_app_id(cfg.app_id);
    ri.set_instance_grp(cfg.inst_grp);
    ri.set_cluster(cfg.cluster);
    ri.set_instance_id(cfg.inst_id);
    ri.set_proc_name(cfg.proc);
    ri.set_vm_id(cfg.vm_id);
    ri.set_zone(cfg.zone);
    ri.set_instance_type(cfg.inst_typ);
    time_now_str([&ri](std::string now) {
            ri.set_local_time(now);
        });
    ri.set_recorder_version(RECORDER_VERION);
    auto now = Time::now();
    std::chrono::duration<double> uptime = now - start_time;
    ri.set_recorder_uptime(uptime.count());
}

static void populate_issued_work_status(recording::WorkResponse& w_res, std::uint64_t work_id, recording::WorkResponse::WorkState state, recording::WorkResponse::WorkResult result, Time::Pt& start_tm, Time::Pt& end_tm) {
    w_res.set_work_id(work_id);
    w_res.set_work_state(state);
    w_res.set_work_result(result);
    std::uint32_t elapsed_time = 0;
    switch(state) {
    case recording::WorkResponse::running:
        elapsed_time = Time::elapsed_seconds(Time::now(), start_tm);
        break;
    case recording::WorkResponse::complete:
        elapsed_time = Time::elapsed_seconds(end_tm, start_tm);
        break;
    case recording::WorkResponse::pre_start:
        break; //already initialized
    default:
        logger->error("Unexpected work-state {} found", state);
    }
    w_res.set_elapsed_time(elapsed_time);
}

typedef std::unique_ptr<CURL, void(*)(CURL*)> Curl;
typedef std::unique_ptr<struct curl_slist, void(*)(curl_slist*)> CurlHeader;

static std::uint32_t backoff(std::uint32_t& seconds, std::uint32_t multiplier, std::uint32_t max_backoff_val) {
    auto current_backoff = seconds;
    logger->error("COMM failed, backing-off by {} seconds", seconds);
    seconds = Util::min(seconds * multiplier, max_backoff_val);
    return current_backoff;
}

static int write_to_curl_request(char *out_buff, size_t size, size_t nitems, void *send_buff) {
    auto buff = static_cast<Buff*>(send_buff);
    std::uint32_t out_capacity = size * nitems;
    auto should_copy = Util::min(out_capacity, buff->write_end - buff->read_end);
    std::memcpy(out_buff, buff->buff + buff->read_end, should_copy);
    buff->read_end += should_copy;
    return should_copy;
}

static int read_from_curl_response(char *in_buff, size_t size, size_t nmemb, void *recv_buff) {
    auto buff = static_cast<Buff*>(recv_buff);
    auto available = size * nmemb;
    buff->ensure_free(available);
    memcpy(buff->buff + buff->write_end, in_buff, available);
    buff->write_end += available;
    return available;
}

CurlHeader make_header_list(const std::vector<const char*>& headers) {
    CurlHeader header_list(nullptr, curl_slist_free_all);
    for(auto hdr : headers) {
        auto new_head = curl_slist_append(header_list.get(), hdr);
        if (new_head != nullptr) {
            header_list.release();
            header_list.reset(new_head);
        }
    }
    return header_list;
}

static inline bool do_call(Curl& curl, const char* url, const char* functional_area, std::uint32_t retries_used) {
    auto res = curl_easy_perform(curl.get());
    long http_code = -1;
    if (res == CURLE_OK) {
        curl_easy_getinfo (curl.get(), CURLINFO_RESPONSE_CODE, &http_code);
        if (http_code >= 200 && http_code < 300) {
            return true;
        }
    }
    auto curl_err_str = curl_easy_strerror(res);
    logger->error("COMM Couldn't talk to {} (for {}) (error({}): {}, http-status: {}, retries-used: {})", url, functional_area, res, curl_err_str, http_code, retries_used);
    return false;
}

void Controller::run_with_associate(const Buff& associate_response_buff, const Time::Pt& start_time) {
    recording::AssignedBackend assigned;
    assigned.ParseFromArray(associate_response_buff.buff + associate_response_buff.read_end, associate_response_buff.write_end - associate_response_buff.read_end);
    const std::string& host = assigned.host();
    std::uint32_t port = assigned.port();
    std::stringstream ss;
    ss << "http://" << host << ":" << port << "/poll";
    const std::string url = ss.str();
    logger->info("Connecting to associate: {}", url);
    std::uint32_t backoff_seconds = cfg.backoff_start;
    auto retries_used = 0;

    Curl curl(curl_easy_init(), curl_easy_cleanup);
    CurlHeader header_list(make_header_list({"Content-type: application/octet-stream", "Transfer-Encoding:", "Expect:"}));
    if (curl.get() == nullptr || header_list == nullptr) {
        logger->error("Controller couldn't talk to assigned backend failed because cURL init failed");
        return;
    }
    curl_easy_setopt(curl.get(), CURLOPT_HTTPHEADER, header_list.get());
    curl_easy_setopt(curl.get(), CURLOPT_URL, url.c_str());
    curl_easy_setopt(curl.get(), CURLOPT_POST, 1L);
    curl_easy_setopt(curl.get(), CURLOPT_UPLOAD, 1L);

    Buff send(1024);
    Buff recv(1024);

    curl_easy_setopt(curl.get(), CURLOPT_READDATA, &send);
    curl_easy_setopt(curl.get(), CURLOPT_READFUNCTION, write_to_curl_request);
    curl_easy_setopt(curl.get(), CURLOPT_WRITEDATA, &recv);
    curl_easy_setopt(curl.get(), CURLOPT_WRITEFUNCTION, read_from_curl_response);
    
    std::function<void()> poll_cb;

    poll_cb = [&]() {
        recording::PollReq p_req;
        populate_recorder_info(*p_req.mutable_recorder_info(), cfg, start_time);

        with_current_work([&p_req](Controller::W& w, Controller::WSt& wst, Controller::WRes& wres, Time::Pt& start_tm, Time::Pt& end_tm) {
                populate_issued_work_status(*p_req.mutable_work_last_issued(), w.work_id(), wst, wres, start_tm, end_tm);
            });

        auto serialized_size = p_req.ByteSize();
        send.ensure_free(serialized_size);
        p_req.SerializeToArray(send.buff, send.capacity);
        send.read_end = 0;
        send.write_end = serialized_size;
        curl_easy_setopt(curl.get(), CURLOPT_INFILESIZE, serialized_size);
        recv.read_end = recv.write_end = 0;

        logger->trace("Polling now");

        auto next_tick = Time::now();
        if (do_call(curl, url.c_str(), "associate-poll", retries_used)) {
            accept_work(recv, host, port);
            backoff_seconds = cfg.backoff_start;
            retries_used = 0;
            next_tick += Time::sec(cfg.poll_itvl);
        } else {
            if (retries_used++ >= cfg.max_retries) {
                logger->error("COMM failed too many times, giving up on the associate: {}", url);
                return;
            }
            next_tick += Time::sec(backoff(backoff_seconds, cfg.backoff_multiplier, cfg.backoff_max));
        }
        scheduler.schedule(next_tick, poll_cb);
    };

    scheduler.schedule(Time::now(), poll_cb);
    
    while (keep_running.load(std::memory_order_relaxed) && scheduler.poll());
}

void Controller::run() {
    auto start_time = Time::now();
    CurlInit _;
    Curl curl(curl_easy_init(), curl_easy_cleanup);
    CurlHeader header_list(make_header_list({ "Content-type: application/octet-stream", "Transfer-Encoding:", "Expect:"}));
    Buff send(1024);
    Buff recv(1024);
    auto backoff_seconds = cfg.backoff_start;
    
    if (curl.get() == nullptr || header_list == nullptr) {
        logger->error("Controller initialization failed because cURL init failed");
        return;
    }
    curl_easy_setopt(curl.get(), CURLOPT_HTTPHEADER, header_list.get());
    std::string service_endpoint_url = cfg.service_endpoint + std::string("/association");
    curl_easy_setopt(curl.get(), CURLOPT_URL, service_endpoint_url.c_str());
    curl_easy_setopt(curl.get(), CURLOPT_UPLOAD, 1L);

    curl_easy_setopt(curl.get(), CURLOPT_READDATA, &send);
    curl_easy_setopt(curl.get(), CURLOPT_READFUNCTION, write_to_curl_request);
    curl_easy_setopt(curl.get(), CURLOPT_WRITEDATA, &recv);
    curl_easy_setopt(curl.get(), CURLOPT_WRITEFUNCTION, read_from_curl_response);
    
    while (keep_running.load(std::memory_order_relaxed)) {
        logger->info("Calling service-endpoint {} for associate resolution", service_endpoint_url);
        recording::RecorderInfo ri;
        populate_recorder_info(ri, cfg, start_time);
        auto serialized_size = ri.ByteSize();
        send.ensure_free(serialized_size);
        ri.SerializeToArray(send.buff, send.capacity);
        send.write_end = serialized_size;
        send.read_end = 0;
        curl_easy_setopt(curl.get(), CURLOPT_INFILESIZE, serialized_size);
        recv.write_end = recv.read_end = 0;
        if (do_call(curl, service_endpoint_url.c_str(), "associate-discovery", 0)) {
            backoff_seconds = cfg.backoff_start;
            run_with_associate(recv, start_time);
        } else {
            std::this_thread::sleep_for(Time::sec(backoff(backoff_seconds, cfg.backoff_multiplier, cfg.backoff_max)));
        }
    }
 

    // if ((clientConnection = accept(listener, (struct sockaddr *) &clientAddress, &addressSize)) == -1) {
    //     logError("ERROR: Failed to accept incoming connection: %s\n", strerror(errno));
    //     continue;
    // }

    // if ((bytesRead = recv(clientConnection, buf, MAX_DATA_SIZE - 1, 0)) == -1) {
    //     if (bytesRead == 0) {
    //         // client closed the connection
    //     } else {
    //         logError("ERROR: Failed to read data from client: %s\n", strerror(errno));
    //     }
    // } else {
    //     buf[bytesRead] = '\0';

    //     if (strstr(buf, "start") == buf) {
    //         startSampling();
    //     } else if (strstr(buf, "stop") == buf) {
    //         stopSampling();
    //     } else if (strstr(buf, "status") == buf) {
    //         reportStatus(clientConnection);
    //     } else if (strstr(buf, "get ") == buf) {
    //         getProfilerParam(clientConnection, buf + 4);
    //     } else if (strstr(buf, "set ") == buf) {
    //         setProfilerParam(buf + 4);
    //     } else {
    //         logError("WARN: Unknown command received, ignoring: %s\n", buf);
    //     }
    // }

    // close(clientConnection);
}

void Controller::with_current_work(std::function<void(Controller::W&, Controller::WSt&, Controller::WRes&, Time::Pt&, Time::Pt&)> proc) {
    std::lock_guard<std::recursive_mutex> current_work_guard(current_work_mtx);
    proc(current_work, current_work_state, current_work_result, work_start, work_end);
}

void Controller::accept_work(Buff& poll_response_buff, const std::string& host, const std::uint32_t port) {
    recording::PollRes res;
    logger->trace("Accept-work host: {} and port: {}", host, port);
    auto parse_successful = res.ParseFromArray(poll_response_buff.buff + poll_response_buff.read_end, poll_response_buff.write_end - poll_response_buff.read_end);
    if (! parse_successful) {
        logger->error("Parse of poll-response failed, ignoring it");
        return;
    }
    if (res.has_assignment()) {
        with_current_work([&](Controller::W& w, Controller::WSt& wst, Controller::WRes& wres, Time::Pt& start_tm, Time::Pt& end_tm) {
                auto new_work = res.mutable_assignment();

                if (wst != recording::WorkResponse::complete) {
                    logger->critical("New work (id: {}, desc: {}, controller: {}/{}) issued while current-work (id: {}, desc: {}) is incomplete (state {})", 
                                     new_work->work_id(), new_work->description(), res.controller_id(), res.controller_version(),
                                     w.work_id(), wst, w.description());
                    return;
                }

                logger->trace("New work (id: {}, desc: {}, controller: {}/{}) is being assigned",
                              new_work->work_id(), new_work->description(), res.controller_id(), res.controller_version());

                w.Swap(new_work);

                const auto delay = w.delay();

                start_tm = end_tm = Time::now();

                if ((delay + w.duration()) > 0) {
                    wst = recording::WorkResponse::pre_start;
                    wres = recording::WorkResponse::unknown;
                    issueWork(host, port, res.controller_id(), res.controller_version());
                } else {
                    wst = recording::WorkResponse::complete;
                    wres = recording::WorkResponse::success;
                }
            });
    } else {
        logger->debug("Controller {}/{} issued no-work", res.controller_id(), res.controller_version());
    }
}

void http_raw_writer_runnable(jvmtiEnv *jvmti_env, JNIEnv *jni_env, void *arg);

static int ring_to_curl(char *out_buff, size_t size, size_t nitems, void *_ring) {
    auto ring = static_cast<BlockingRingBuffer*>(_ring);
    auto copied = ring->read(reinterpret_cast<std::uint8_t*>(out_buff), 0, size * nitems);
    logger->debug("Writing {} bytes of recorded data", copied);
    return copied;
}

class HttpRawProfileWriter : public RawWriter {
private:
    std::string host;
    std::uint32_t port;
    //This ring is a 1 copy approach. But this is needed (and acceptable), because alternatives aren't very lucrative.
    //
    //The problem is, we have atomicity boundry across 3 writes (length, object itself and its checksum, check ProfileWriter)
    //  which means, critical section for write to ring-buffer will have to extend around the 3 writes (one being heavy serialization op).
    //  Ideally, serialization, being heavy operation it is, should happen outside critical section to keep contention low, but then variable-length encoding
    //  of integers (length, checksum) prevents us from accurately predicting the space necessary. We can over-provision and later jump over empty space
    //  in the ring-buffer, but if that is to become a pointer chase, it kinda looses the point.
    //
    //So the solution is to have a 1 copy approach, which has additional-copy overhead, but also has some advantages. Eg. now the extra copy step
    //  can be used for other desirable things like compression.
    BlockingRingBuffer& ring;

    std::function<void()> cancellation_fn;

    ThdProcP thd_proc;

public:
    HttpRawProfileWriter(JavaVM *jvm, jvmtiEnv *jvmti, const std::string& _host, const std::uint32_t _port, BlockingRingBuffer& _ring, std::function<void()>&& _cancellation_fn) : RawWriter(), host(_host), port(_port), ring(_ring), cancellation_fn(_cancellation_fn) {
        ring.reset();
        thd_proc = start_new_thd(jvm, jvmti, "Fk-Prof Profiler Writer Thread", http_raw_writer_runnable, this);
    }
    virtual ~HttpRawProfileWriter() {
        logger->trace("HTTP RawProfileWriter destructor called");
        ring.readonly();
        await_thd_death(thd_proc);
    }

    void write_unbuffered(const std::uint8_t* data, std::uint32_t sz, std::uint32_t offset) {
        ring.write(data, offset, sz);
    }

    void run() {
        std::stringstream ss;
        ss << "http://" << host << ":" << port << "/profile";
        const std::string url = ss.str();
        logger->info("Will now post profile to associate: {}", url);

        Curl curl(curl_easy_init(), curl_easy_cleanup);
        CurlHeader header_list(make_header_list({"Content-type: application/octet-stream", "Transfer-Encoding: chunked"}));
        if (curl.get() == nullptr || header_list == nullptr) {
            logger->error("Controller couldn't post profile because cURL init failed");
            return;
        }
        curl_easy_setopt(curl.get(), CURLOPT_HTTPHEADER, header_list.get());
        curl_easy_setopt(curl.get(), CURLOPT_URL, url.c_str());
        curl_easy_setopt(curl.get(), CURLOPT_POST, 1L);
        curl_easy_setopt(curl.get(), CURLOPT_UPLOAD, 1L);

        curl_easy_setopt(curl.get(), CURLOPT_READDATA, &ring);
        curl_easy_setopt(curl.get(), CURLOPT_READFUNCTION, ring_to_curl);

        if (do_call(curl, url.c_str(), "send-profile", 0)) {
            logger->info("Profile posted successfully!");
        } else {
            logger->error("Couldn't post profile, http request failed, cancelling work.");
            cancellation_fn();
        }
    }
};

void http_raw_writer_runnable(jvmtiEnv *jvmti_env, JNIEnv *jni_env, void *arg) {
    logger->trace("HTTP raw-writer thread target entered");
    auto rpw = static_cast<HttpRawProfileWriter*>(arg);
    rpw->run();
}

void populate_recording_header(recording::RecordingHeader& rh, const recording::WorkAssignment& w, std::uint32_t controller_id, std::uint32_t controller_version) {
    rh.set_recorder_version(RECORDER_VERION);
    rh.set_controller_version(controller_version);
    rh.set_controller_id(controller_id);
    recording::WorkAssignment* wa = rh.mutable_work_assignment();
    *wa = w;
}

void Controller::issueWork(const std::string& host, const std::uint32_t port, std::uint32_t controller_id, std::uint32_t controller_version) {
    auto at = Time::now() + Time::sec(current_work.delay());
    scheduler.schedule(at, [&, port, controller_id, controller_version]() {
            with_current_work([&](Controller::W& w, Controller::WSt& wst, Controller::WRes& wres, Time::Pt& start_tm, Time::Pt& end_tm) {
                    auto work_id = w.work_id();
                    std::function<void()> cancellation_cb = [&, work_id]() {
                        scheduler.schedule(Time::now(), [&] {
                                wres = recording::WorkResponse::failure;
                                retireWork(work_id);
                            });
                    };
                    if (w.work_size() > 0) {
                        std::shared_ptr<HttpRawProfileWriter> raw_writer(new HttpRawProfileWriter(jvm, jvmti, host, port, raw_writer_ring, std::move(cancellation_cb)));
                        writer.reset(new ProfileWriter(raw_writer, buff));
                        recording::RecordingHeader rh;
                        populate_recording_header(rh, w, controller_id, controller_version);
                        writer->write_header(rh);
                    }
                    
                    for (auto i = 0; i < w.work_size(); i++) {
                        auto work = w.work(i);
                        issue(work);
                    }
                    start_tm = Time::now();
                    auto stop_at = start_tm + Time::sec(w.duration());
                    scheduler.schedule(stop_at, [&, work_id]() {
                            retireWork(work_id);
                        });
                    logger->info("Issuing work-id {}, it is slated for retire in {} seconds", w.work_id(), w.duration());
                    wst = recording::WorkResponse::running;
                });
        });
}

void Controller::retireWork(const std::uint64_t work_id) {
    with_current_work([&](Controller::W& w, Controller::WSt& wst, Controller::WRes& wres, Time::Pt& start_tm, Time::Pt& end_tm) {
            if (w.work_id() != work_id) {
                logger->warn("Stale work-retire call (target work_id was {}, current work_id is {}), ignoring", work_id, w.work_id());
                return;//TODO: test me!
            }
            logger->info("Will now retire work {}", work_id);
            for (auto i = 0; i < w.work_size(); i++) {
                auto work = w.work(i);
                retire(work);
            }
            writer.reset();
            
            logger->info("Retiring work-id {}, status before retire {}", w.work_id(), wres);
            wst = recording::WorkResponse::complete;
            if ( wres == recording::WorkResponse::unknown) {
                wres = recording::WorkResponse::success;
            }
            end_tm = Time::now();
        });
}

bool has_cpu_sample_work_p(const recording::Work& work) {
    if (work.has_cpu_sample()) return true;
    logger->error("Work of CPU-sampling-type {} doesn't have a definition", work.w_type());
    return false;
}

void Controller::issue(const recording::Work& work) {
    auto w_type = work.w_type();
    switch(w_type) {
    case recording::WorkType::cpu_sample_work:
        if (has_cpu_sample_work_p(work))
            issue(work.cpu_sample());
        return;
    default:
        logger->error("Encountered unknown work type {}", w_type);
    }
}

void Controller::retire(const recording::Work& work) {
    auto w_type = work.w_type();
    switch(w_type) {
    case recording::WorkType::cpu_sample_work:
        if (has_cpu_sample_work_p(work))
            retire(work.cpu_sample());
        return;
    default:
        logger->error("Encountered unknown work type {}", w_type);
    }
}

void Controller::issue(const recording::CpuSampleWork& csw) {
    auto freq = csw.frequency();
    auto max_stack_depth = csw.max_frames();
    logger->info("Starting cpu-sampling at {} Hz and for upto {} frames", freq, max_stack_depth);
    
    GlobalCtx::recording.cpu_profiler.reset(new Profiler(jvm, jvmti, thread_map, writer, max_stack_depth, freq));
    JNIEnv *env = getJNIEnv(jvm);
    GlobalCtx::recording.cpu_profiler->start(env);
}

void Controller::retire(const recording::CpuSampleWork& csw) {
    auto freq = csw.frequency();
    auto max_stack_depth = csw.max_frames();
    logger->info("Stopping cpu-sampling", freq, max_stack_depth);

    GlobalCtx::recording.cpu_profiler->stop();
    GlobalCtx::recording.cpu_profiler.reset();
}

void Controller::startSampling() {
    // JNIEnv *env = getJNIEnv(jvm);

    // if (env == NULL) {
    //     logError("ERROR: Failed to obtain JNI environment, cannot start sampling\n");
    //     return;
    // }

    // profiler->start(env);
}

void Controller::stopSampling() {
    // profiler->stop();
}

namespace GlobalCtx {
    GlobalCtx::Rec recording;
}
