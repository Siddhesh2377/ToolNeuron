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
    req.headers = {
        {"User-Agent", ua},
        {"Content-Type", "application/x-www-form-urlencoded"},
        {"Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8"},
        {"Accept-Language", "en-US,en;q=0.9"},
        {"Sec-Fetch-Dest", "document"},
        {"Sec-Fetch-Mode", "navigate"},
        {"Sec-Fetch-Site", "none"},
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

    auto fallback = try_host(kLiteHost, query, user_agent, capped, locale);
    if (fallback.ok) return fallback;

    return primary.error.message.empty() ? fallback : primary;
}

}
