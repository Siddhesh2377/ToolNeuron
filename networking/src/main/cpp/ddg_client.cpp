#include "ddg_client.h"

#include "http_backend.h"
#include "html_extract.h"
#include "url_util.h"

#include <algorithm>

namespace net::ddg {

namespace {

constexpr const char* kHtmlHost = "https://html.duckduckgo.com/html/";
constexpr const char* kLiteHost = "https://lite.duckduckgo.com/lite/";

HttpRequest build_post(const std::string& host, const std::string& query,
                       const std::string& ua, const std::string& locale) {
    HttpRequest req;
    req.url = host;
    req.method = "POST";
    std::string kl = locale.empty() ? "wt-wt" : locale;
    req.body = "q=" + url::encode(query) + "&kl=" + url::encode(kl) + "&kd=-1&b=";
    // Header set must agree with the curl-impersonate "chrome116" TLS
    // fingerprint we set globally. Missing Sec-CH-UA + Sec-CH-UA-Mobile +
    // Sec-CH-UA-Platform on a Chrome 116 TLS handshake is itself a tell —
    // DDG returns the anomaly page when these disagree. Sec-Fetch-Site is
    // "same-origin" because we're POSTing back to the same site we
    // referred from (the DDG search form).
    req.headers = {
        {"User-Agent", ua},
        {"Content-Type", "application/x-www-form-urlencoded"},
        {"Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,*/*;q=0.8"},
        {"Accept-Language", "en-US,en;q=0.9"},
        {"Accept-Encoding", "gzip, deflate, br"},
        {"Referer", "https://duckduckgo.com/"},
        {"Origin", "https://duckduckgo.com"},
        {"Sec-CH-UA", "\"Not_A Brand\";v=\"8\", \"Chromium\";v=\"116\", \"Google Chrome\";v=\"116\""},
        {"Sec-CH-UA-Mobile", "?0"},
        {"Sec-CH-UA-Platform", "\"Windows\""},
        {"Sec-Fetch-Dest", "document"},
        {"Sec-Fetch-Mode", "navigate"},
        {"Sec-Fetch-Site", "same-origin"},
        {"Sec-Fetch-User", "?1"},
        {"Upgrade-Insecure-Requests", "1"},
    };
    req.timeout_ms = 15000;
    req.follow_redirects = true;
    return req;
}

std::vector<Result> to_results(std::vector<html::Entry>&& entries) {
    std::vector<Result> out;
    out.reserve(entries.size());
    for (auto& e : entries) {
        Result r;
        r.title = std::move(e.title);
        r.url = url::unwrap_ddg_redirect(e.href);
        r.snippet = std::move(e.snippet);
        if (r.url.rfind("//", 0) == 0) r.url = "https:" + r.url;
        out.push_back(std::move(r));
    }
    return out;
}

SearchOutcome try_host(const std::string& host, const std::string& query, const std::string& ua,
                       int max_results, const std::string& locale) {
    SearchOutcome out;
    auto resp = http_execute(build_post(host, query, ua, locale));
    if (!resp.has_value()) {
        out.error = {"http backend unavailable"};
        return out;
    }
    if (!resp->error.empty()) {
        out.error = {resp->error};
        return out;
    }
    if (resp->status == 429 || resp->status == 202) {
        out.error = {"rate-limited (status " + std::to_string(resp->status) + ")"};
        return out;
    }
    if (resp->status < 200 || resp->status >= 300) {
        out.error = {"http status " + std::to_string(resp->status)};
        return out;
    }
    auto entries = html::extract_ddg_results(resp->body, max_results);
    out.results = to_results(std::move(entries));
    out.ok = true;
    return out;
}

}

SearchOutcome search(const std::string& query, const std::string& user_agent,
                     int max_results, const std::string& locale) {
    if (query.empty()) {
        SearchOutcome out;
        out.error = {"empty query"};
        return out;
    }

    int capped = std::clamp(max_results, 1, 30);

    auto primary = try_host(kHtmlHost, query, user_agent, capped, locale);
    if (primary.ok) return primary;

    // If DDG answered with the 202 anti-bot challenge, it set a tracking
    // cookie on the response. The CURLSH-shared cookie jar in http_curl.cpp
    // captured it, so an immediate retry against the same host carries the
    // cookie and almost always clears the challenge.
    if (primary.error.message.find("status 202") != std::string::npos) {
        auto retry = try_host(kHtmlHost, query, user_agent, capped, locale);
        if (retry.ok) return retry;
    }

    auto fallback = try_host(kLiteHost, query, user_agent, capped, locale);
    if (fallback.ok) return fallback;

    return primary.error.message.empty() ? fallback : primary;
}

}
