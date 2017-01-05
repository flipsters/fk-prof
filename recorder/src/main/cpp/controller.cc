#include "controller.hh"
#include <chrono>
#include <curl/curl.h>
#include "buff.hh"

void controllerRunnable(jvmtiEnv *jvmti_env, JNIEnv *jni_env, void *arg) {
    IMPLICITLY_USE(jvmti_env);
    IMPLICITLY_USE(jni_env);
    Controller *control = (Controller *) arg;
    sigset_t mask;

    sigemptyset(&mask);
    sigaddset(&mask, SIGPROF);

    if (pthread_sigmask(SIG_BLOCK, &mask, NULL) < 0) {
        logError("ERROR: unable to set controller thread signal mask\n");
    }

    control->run();
}

void Controller::start() {
    JNIEnv *env = getJNIEnv(jvm);
    jvmtiError result;

    if (env == NULL) {
        logError("ERROR: Failed to obtain JNIEnv\n");
        return;
    }

    running.store(true, std::memory_order_relaxed);

    jthread thread = newThread(env, "Honest Profiler Controller Thread");
    jvmtiStartFunction callback = controllerRunnable;
    result = jvmti->RunAgentThread(thread, callback, this, JVMTI_THREAD_NORM_PRIORITY);

    if (result != JVMTI_ERROR_NONE) {
        logError("ERROR: Running controller thread failed with: %d\n", result);
    }
}

void Controller::stop() {
    running.store(false, std::memory_order_relaxed);
}

bool Controller::is_running() const {
    return running.load();
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

static void populate_recorder_info(recording::RecorderInfo& ri, const ConfigurationOptions& cfg, const std::chrono::time_point<std::chrono::steady_clock>& start_time) {
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
    auto now = std::chrono::steady_clock::now();
    std::chrono::duration<double> uptime = now - start_time;
    ri.set_recorder_uptime(uptime.count());
}

static void populate_issued_work_status(recording::WorkResponse& w_res, std::uint64_t work_id, recording::WorkResponse::WorkState state, recording::WorkResponse::WorkResult result) {
    w_res.set_work_id(work_id);
    w_res.set_work_state(state);
    w_res.set_work_result(result);
    w_res.set_elapsed_time(0);//TODO: fix me
}

typedef std::unique_ptr<CURL, void(*)(CURL*)> Curl;
typedef std::unique_ptr<struct curl_slist, void(*)(curl_slist*)> CurlHeader;

static void backoff(std::uint32_t& seconds, std::uint32_t multiplier, std::uint32_t max_backoff_val) {
    std::this_thread::sleep_for(std::chrono::duration<int>(seconds));
    logger->error("COMM failed, backed-off by {} seconds", seconds);
    seconds = min(seconds * multiplier, max_backoff_val);
}

static int write_to_curl_request(char *out_buff, size_t size, size_t nitems, void *send_buff) {
    auto buff = static_cast<Buff*>(send_buff);
    std::uint32_t out_capacity = size * nitems;
    auto should_copy = min(out_capacity, buff->write_end - buff->read_end);
    std::memcpy(out_buff, buff->buff + buff->read_end, should_copy);
    buff->read_end += should_copy;
    return should_copy;
}

static int read_from_curl_response(char *in_buff, size_t size, size_t nmemb, void *recv_buff) {
    auto buff = static_cast<Buff*>(recv_buff);
    auto available = size * nmemb;
    buff->ensure_capacity(available);
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

void Controller::run_with_associate(const Buff& associate_response_buff, const std::chrono::time_point<std::chrono::steady_clock>& start_time) {
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
    
    std::this_thread::sleep_for(std::chrono::duration<int>(1));
    while (running.load(std::memory_order_relaxed)) {
        recording::PollReq p_req;
        populate_recorder_info(*p_req.mutable_recorder_info(), cfg, start_time);
        Controller::WSt state;
        Controller::WRes result;
        std::uint64_t work_id;

        with_current_work([&state, &result, &work_id](Controller::W& w, Controller::WSt& wst, Controller::WRes& wres, std::string& desc) {
                work_id = w.work_id();
                state = wst;
                result = wres;
            });
        
        populate_issued_work_status(*p_req.mutable_work_last_issued(), work_id, state, result);
        
        auto serialized_size = p_req.ByteSize();
        send.ensure_capacity(serialized_size);
        p_req.SerializeToArray(send.buff, send.capacity);
        send.read_end = 0;
        send.write_end = serialized_size;
        curl_easy_setopt(curl.get(), CURLOPT_INFILESIZE, serialized_size);
        recv.read_end = recv.write_end = 0;

        logger->trace("Polling now");

        if (do_call(curl, url.c_str(), "associate-poll", retries_used)) {
            accept_work(recv);
            std::this_thread::sleep_for(std::chrono::duration<int>(cfg.poll_itvl));
            backoff_seconds = cfg.backoff_start;
            retries_used = 0;
        } else {
            if (retries_used++ >= cfg.max_retries) {
                logger->error("COMM failed too many times, giving up on the associate: {}", url);
                break;
            }
            backoff(backoff_seconds, cfg.backoff_multiplier, cfg.backoff_max);
        }
    }
}

void Controller::run() {
    auto start_time = std::chrono::steady_clock::now();
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
    
    while (running.load(std::memory_order_relaxed)) {
        logger->info("Calling service-endpoint {} for associate resolution", service_endpoint_url);
        recording::RecorderInfo ri;
        populate_recorder_info(ri, cfg, start_time);
        auto serialized_size = ri.ByteSize();
        send.ensure_capacity(serialized_size);
        ri.SerializeToArray(send.buff, send.capacity);
        send.write_end = serialized_size;
        send.read_end = 0;
        curl_easy_setopt(curl.get(), CURLOPT_INFILESIZE, serialized_size);
        curl_easy_setopt(curl.get(), CURLOPT_READDATA, &send);
        curl_easy_setopt(curl.get(), CURLOPT_READFUNCTION, write_to_curl_request);
        recv.write_end = recv.read_end = 0;
        curl_easy_setopt(curl.get(), CURLOPT_WRITEDATA, &recv);
        curl_easy_setopt(curl.get(), CURLOPT_WRITEFUNCTION, read_from_curl_response);
        if (do_call(curl, service_endpoint_url.c_str(), "associate-discovery", 0)) {
            backoff_seconds = cfg.backoff_start;
            run_with_associate(recv, start_time);
        } else {
            backoff(backoff_seconds, cfg.backoff_multiplier, cfg.backoff_max);
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

void Controller::with_current_work(std::function<void(Controller::W&, Controller::WSt&, Controller::WRes&, std::string&)> proc) {
    std::lock_guard<std::mutex> current_work_guard(current_work_mtx);
    proc(current_work, current_work_state, current_work_result, current_work_description);
}

void Controller::accept_work(Buff& poll_response_buff) {
    recording::PollRes res;
    auto parse_successful = res.ParseFromArray(poll_response_buff.buff + poll_response_buff.read_end, poll_response_buff.write_end - poll_response_buff.read_end);
    if (! parse_successful) {
        logger->error("Parse of poll-response failed, ignoring it");
        return;
    }
    
    with_current_work([&res](Controller::W& w, Controller::WSt& wst, Controller::WRes& wres, std::string& desc) {
            auto new_work = res.mutable_assignment();
            if (wst != recording::WorkResponse::complete) {
                logger->critical("New work (id: {}, desc: {}, controller: {}/{}) issued while current-work (id: {}, desc: {}) is incomplete (state {})", 
                                 new_work->work_id(), res.work_description(), res.controller_id(), res.controller_version(),
                                 w.work_id(), wst, desc);
                return;
            }

            logger->trace("New work (id: {}, desc: {}, controller: {}/{}) is being assigned",
                          new_work->work_id(), res.work_description(), res.controller_id(), res.controller_version());

            w.Swap(new_work);
            desc = res.work_description();

            if (w.work_size() > 0) {
                wst = recording::WorkResponse::pre_start;
                wres = recording::WorkResponse::unknown;
            } else {
                wst = recording::WorkResponse::complete;
                wres = recording::WorkResponse::success;
            }
        });
}

void Controller::startSampling() {
    JNIEnv *env = getJNIEnv(jvm);

    if (env == NULL) {
        logError("ERROR: Failed to obtain JNI environment, cannot start sampling\n");
        return;
    }

    profiler->start(env);
}

void Controller::stopSampling() {
    profiler->stop();
}
