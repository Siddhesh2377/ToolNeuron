#include "http_backend.h"

#include <android/log.h>
#include <curl/curl.h>

#include <mutex>
#include <string>

namespace net {

namespace {

constexpr const char* kTag = "networking";

std::mutex g_mtx;
std::string g_ca_bundle;
std::string g_profile = "chrome116";
bool g_global_inited = false;

void ensure_global_init() {
    std::lock_guard<std::mutex> lk(g_mtx);
    if (!g_global_inited) {
        curl_global_init(CURL_GLOBAL_DEFAULT);
        g_global_inited = true;
    }
}

size_t body_cb(char* ptr, size_t size, size_t nmemb, void* ud) {
    size_t n = size * nmemb;
    static_cast<std::string*>(ud)->append(ptr, n);
    return n;
}

size_t header_cb(char* buf, size_t size, size_t nmemb, void* ud) {
    size_t n = size * nmemb;
    auto* vec = static_cast<std::vector<Header>*>(ud);
    std::string line(buf, n);
    auto colon = line.find(':');
    if (colon != std::string::npos) {
        std::string k = line.substr(0, colon);
        std::string v = line.substr(colon + 1);
        while (!v.empty() && (v.front() == ' ' || v.front() == '\t')) v.erase(0, 1);
        while (!v.empty() && (v.back() == '\r' || v.back() == '\n' || v.back() == ' ')) v.pop_back();
        vec->emplace_back(std::move(k), std::move(v));
    }
    return n;
}

}

void http_set_ca_bundle(const std::string& path) {
    std::lock_guard<std::mutex> lk(g_mtx);
    g_ca_bundle = path;
}

void http_set_impersonate_profile(const std::string& profile) {
    std::lock_guard<std::mutex> lk(g_mtx);
    if (!profile.empty()) g_profile = profile;
}

std::optional<HttpResponse> http_execute(const HttpRequest& req) {
    ensure_global_init();

    std::string ca_copy;
    std::string profile_copy;
    {
        std::lock_guard<std::mutex> lk(g_mtx);
        ca_copy = g_ca_bundle;
        profile_copy = g_profile;
    }

    CURL* curl = curl_easy_init();
    if (!curl) return std::nullopt;

    HttpResponse resp;

    curl_easy_impersonate(curl, profile_copy.c_str(), 1);

    curl_easy_setopt(curl, CURLOPT_URL, req.url.c_str());

    if (req.method == "POST" || !req.body.empty()) {
        curl_easy_setopt(curl, CURLOPT_POST, 1L);
        curl_easy_setopt(curl, CURLOPT_POSTFIELDS, req.body.c_str());
        curl_easy_setopt(curl, CURLOPT_POSTFIELDSIZE, static_cast<long>(req.body.size()));
    } else if (req.method != "GET") {
        curl_easy_setopt(curl, CURLOPT_CUSTOMREQUEST, req.method.c_str());
    }

    struct curl_slist* hdrs = nullptr;
    for (const auto& h : req.headers) {
        std::string line = h.first + ": " + h.second;
        hdrs = curl_slist_append(hdrs, line.c_str());
    }
    if (hdrs) curl_easy_setopt(curl, CURLOPT_HTTPHEADER, hdrs);

    curl_easy_setopt(curl, CURLOPT_FOLLOWLOCATION, req.follow_redirects ? 1L : 0L);
    curl_easy_setopt(curl, CURLOPT_MAXREDIRS, 10L);
    curl_easy_setopt(curl, CURLOPT_TIMEOUT_MS, static_cast<long>(req.timeout_ms));
    curl_easy_setopt(curl, CURLOPT_CONNECTTIMEOUT_MS, 10000L);

    curl_easy_setopt(curl, CURLOPT_NOSIGNAL, 1L);
    curl_easy_setopt(curl, CURLOPT_ACCEPT_ENCODING, "");

    if (!ca_copy.empty()) {
        curl_easy_setopt(curl, CURLOPT_CAINFO, ca_copy.c_str());
        curl_easy_setopt(curl, CURLOPT_SSL_VERIFYPEER, 1L);
        curl_easy_setopt(curl, CURLOPT_SSL_VERIFYHOST, 2L);
    } else {
        curl_easy_setopt(curl, CURLOPT_SSL_VERIFYPEER, 0L);
        curl_easy_setopt(curl, CURLOPT_SSL_VERIFYHOST, 0L);
        __android_log_print(ANDROID_LOG_WARN, kTag,
            "CA bundle not set — TLS verification DISABLED (call setCaBundle before use)");
    }

    curl_easy_setopt(curl, CURLOPT_COOKIEFILE, "");

    curl_easy_setopt(curl, CURLOPT_WRITEFUNCTION, body_cb);
    curl_easy_setopt(curl, CURLOPT_WRITEDATA, &resp.body);
    curl_easy_setopt(curl, CURLOPT_HEADERFUNCTION, header_cb);
    curl_easy_setopt(curl, CURLOPT_HEADERDATA, &resp.headers);

    CURLcode rc = curl_easy_perform(curl);
    if (rc == CURLE_OK) {
        long code = 0;
        curl_easy_getinfo(curl, CURLINFO_RESPONSE_CODE, &code);
        resp.status = static_cast<int>(code);
    } else {
        resp.error = curl_easy_strerror(rc);
        __android_log_print(ANDROID_LOG_WARN, kTag, "curl: %s (%s)", resp.error.c_str(), req.url.c_str());
    }

    if (hdrs) curl_slist_free_all(hdrs);
    curl_easy_cleanup(curl);
    return resp;
}

bool http_available() { return true; }

const char* http_backend_name() { return "curl-impersonate-chrome"; }

}
