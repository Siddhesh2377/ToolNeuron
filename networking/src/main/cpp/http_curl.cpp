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

// In-memory cookie jar shared across every curl_easy handle in this process.
// Without this, each request gets a fresh empty cookie store, so DDG (and
// any other site that uses Set-Cookie to challenge bots) sees every request
// as a brand-new visit and answers with an anti-bot HTTP 202. Privacy-safe
// because (a) cookies live in RAM only, (b) the jar dies with the process,
// (c) no on-disk persistence across launches.
CURLSH* g_share = nullptr;
std::mutex g_share_cookie_mtx;
std::mutex g_share_dns_mtx;
std::mutex g_share_ssl_mtx;

void share_lock(CURL*, curl_lock_data data, curl_lock_access, void*) {
    switch (data) {
        case CURL_LOCK_DATA_COOKIE:    g_share_cookie_mtx.lock(); return;
        case CURL_LOCK_DATA_DNS:       g_share_dns_mtx.lock(); return;
        case CURL_LOCK_DATA_SSL_SESSION: g_share_ssl_mtx.lock(); return;
        default: return;
    }
}

void share_unlock(CURL*, curl_lock_data data, void*) {
    switch (data) {
        case CURL_LOCK_DATA_COOKIE:    g_share_cookie_mtx.unlock(); return;
        case CURL_LOCK_DATA_DNS:       g_share_dns_mtx.unlock(); return;
        case CURL_LOCK_DATA_SSL_SESSION: g_share_ssl_mtx.unlock(); return;
        default: return;
    }
}

void ensure_global_init() {
    std::lock_guard<std::mutex> lk(g_mtx);
    if (!g_global_inited) {
        curl_global_init(CURL_GLOBAL_DEFAULT);
        g_share = curl_share_init();
        if (g_share) {
            curl_share_setopt(g_share, CURLSHOPT_SHARE, CURL_LOCK_DATA_COOKIE);
            curl_share_setopt(g_share, CURLSHOPT_SHARE, CURL_LOCK_DATA_DNS);
            curl_share_setopt(g_share, CURLSHOPT_SHARE, CURL_LOCK_DATA_SSL_SESSION);
            curl_share_setopt(g_share, CURLSHOPT_LOCKFUNC, share_lock);
            curl_share_setopt(g_share, CURLSHOPT_UNLOCKFUNC, share_unlock);
        }
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

    HttpResponse resp;

    // Fail-closed TLS policy. Privacy-first app contract: refuse to issue any
    // request unless we can pin verification to our bundled CA. If the bundle
    // isn't installed yet, the network call is rejected client-side rather
    // than emitted with VERIFYPEER=0 (which would silently accept MitM certs).
    if (ca_copy.empty()) {
        __android_log_print(ANDROID_LOG_ERROR, kTag,
            "request refused: CA bundle not installed (TLS would be unverified)");
        resp.status = 0;
        resp.error = "tls_not_initialized";
        return resp;
    }

    // Privacy-first app contract: never speak plain HTTP. The bundled CA only
    // helps if we won't downgrade. http(s)://… is the only acceptable scheme;
    // anything else gets rejected before curl sees it.
    if (req.url.rfind("https://", 0) != 0) {
        __android_log_print(ANDROID_LOG_ERROR, kTag,
            "request refused: non-HTTPS scheme");
        resp.status = 0;
        resp.error = "scheme_not_https";
        return resp;
    }

    CURL* curl = curl_easy_init();
    if (!curl) return std::nullopt;

    curl_easy_impersonate(curl, profile_copy.c_str(), 1);

    // Attach the process-wide CURLSH so cookies, DNS, and TLS sessions persist
    // across calls. Critical for DDG: without a carried session cookie, every
    // request looks like a brand-new visit and DDG answers with HTTP 202.
    if (g_share) curl_easy_setopt(curl, CURLOPT_SHARE, g_share);

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

    // ca_copy is guaranteed non-empty here — http_execute fails fast above
    // when no CA bundle has been installed. VERIFYPEER=1 forces full chain
    // validation against our bundled cacert.pem; VERIFYHOST=2 forces SAN /
    // CN match. Anything stricter (cert pinning) is out of scope for v1.
    curl_easy_setopt(curl, CURLOPT_CAINFO, ca_copy.c_str());
    curl_easy_setopt(curl, CURLOPT_SSL_VERIFYPEER, 1L);
    curl_easy_setopt(curl, CURLOPT_SSL_VERIFYHOST, 2L);
    curl_easy_setopt(curl, CURLOPT_SSLVERSION, CURL_SSLVERSION_TLSv1_2);
    curl_easy_setopt(curl, CURLOPT_PROTOCOLS_STR, "https");
    curl_easy_setopt(curl, CURLOPT_REDIR_PROTOCOLS_STR, "https");

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
