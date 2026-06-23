#include "ddg_client.h"

#include "http_backend.h"
#include "html_extract.h"
#include "url_util.h"

#include <algorithm>
#include <unordered_set>

namespace net::ddg {

namespace {

using Parser = std::vector<html::Entry> (*)(const std::string&, int);

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

HttpRequest build_get(const std::string& url, const std::string& ua) {
    HttpRequest req;
    req.url = url;
    req.method = "GET";
    req.headers = {
        {"User-Agent", ua},
        {"Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,*/*;q=0.8"},
        {"Accept-Language", "en-US,en;q=0.9"},
        {"Accept-Encoding", "gzip, deflate, br"},
        {"Sec-CH-UA", "\"Not_A Brand\";v=\"8\", \"Chromium\";v=\"116\", \"Google Chrome\";v=\"116\""},
        {"Sec-CH-UA-Mobile", "?0"},
        {"Sec-CH-UA-Platform", "\"Windows\""},
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

SearchOutcome run_engine(const HttpRequest& req, Parser parse, int max_results) {
    SearchOutcome out;
    auto resp = http_execute(req);
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
    out.results = to_results(parse(resp->body, max_results));
    out.ok = true;
    return out;
}

std::string bing_url(const std::string& query, int count) {
    return "https://www.bing.com/search?q=" + url::encode(query) +
           "&count=" + std::to_string(count) + "&setlang=en-US";
}

std::string mojeek_url(const std::string& query) {
    return "https://www.mojeek.com/search?q=" + url::encode(query);
}

}

SearchOutcome search(const std::string& query, const std::string& user_agent,
                     int max_results, const std::string& locale) {
    SearchOutcome merged;
    if (query.empty()) {
        merged.error = {"empty query"};
        return merged;
    }

    const int capped = std::clamp(max_results, 1, 30);
    std::unordered_set<std::string> seen;
    std::string last_error;

    auto enough = [&]() { return static_cast<int>(merged.results.size()) >= capped; };
    auto absorb = [&](SearchOutcome eo) {
        if (!eo.ok) {
            if (!eo.error.message.empty()) last_error = eo.error.message;
            return;
        }
        for (auto& r : eo.results) {
            if (r.url.empty()) continue;
            if (!seen.insert(r.url).second) continue;
            merged.results.push_back(std::move(r));
        }
    };

    absorb(run_engine(build_post(kHtmlHost, query, user_agent, locale),
                      html::extract_ddg_results, capped));
    if (merged.results.empty() && last_error.find("status 202") != std::string::npos) {
        absorb(run_engine(build_post(kHtmlHost, query, user_agent, locale),
                          html::extract_ddg_results, capped));
    }

    if (!enough()) {
        absorb(run_engine(build_get(bing_url(query, capped), user_agent),
                          html::extract_bing_results, capped));
    }
    if (!enough()) {
        absorb(run_engine(build_get(mojeek_url(query), user_agent),
                          html::extract_mojeek_results, capped));
    }
    if (merged.results.empty()) {
        absorb(run_engine(build_post(kLiteHost, query, user_agent, locale),
                          html::extract_ddg_results, capped));
    }

    if (static_cast<int>(merged.results.size()) > capped) merged.results.resize(capped);
    merged.ok = !merged.results.empty();
    if (!merged.ok) {
        merged.error = {last_error.empty() ? "no results" : last_error};
    }
    return merged;
}

}
