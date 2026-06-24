#include "html_extract.h"

#include "url_util.h"

#include <regex>
#include <unordered_map>

namespace net::html {

namespace {

const std::unordered_map<std::string, std::string> kEntities = {
    {"amp", "&"}, {"lt", "<"}, {"gt", ">"},
    {"quot", "\""}, {"apos", "'"}, {"#39", "'"},
    {"nbsp", " "}, {"#160", " "},
};

}

std::string decode_entities(const std::string& in) {
    std::string out;
    out.reserve(in.size());
    for (size_t i = 0; i < in.size(); ++i) {
        if (in[i] == '&') {
            auto end = in.find(';', i + 1);
            if (end != std::string::npos && end - i <= 8) {
                std::string name = in.substr(i + 1, end - i - 1);
                auto it = kEntities.find(name);
                if (it != kEntities.end()) {
                    out += it->second;
                    i = end;
                    continue;
                }
                if (!name.empty() && name[0] == '#') {
                    try {
                        int code = std::stoi(name.substr(1));
                        if (code >= 32 && code <= 126) {
                            out.push_back(static_cast<char>(code));
                            i = end;
                            continue;
                        }
                    } catch (...) {}
                }
            }
        }
        out.push_back(in[i]);
    }
    return out;
}

std::string strip_tags(const std::string& html) {
    std::string out;
    out.reserve(html.size());
    bool in_tag = false;
    for (char c : html) {
        if (c == '<') in_tag = true;
        else if (c == '>') in_tag = false;
        else if (!in_tag) out.push_back(c);
    }
    return decode_entities(out);
}

std::vector<Entry> extract_ddg_results(const std::string& html, int max_results) {
    std::vector<Entry> out;
    if (max_results <= 0) return out;

    static const std::regex kRedirectAnchor(
        R"rx(<a\b[^>]*href="((?:https?:)?//(?:duckduckgo\.com)?/l/\?[^"]*uddg=[^"]+)"[^>]*>([\s\S]*?)</a>)rx",
        std::regex::icase | std::regex::optimize
    );

    auto rbegin = std::sregex_iterator(html.begin(), html.end(), kRedirectAnchor);
    auto rend = std::sregex_iterator();

    for (auto it = rbegin; it != rend && static_cast<int>(out.size()) < max_results; ++it) {
        Entry e;
        e.href = (*it)[1].str();
        e.title = strip_tags((*it)[2].str());
        while (!e.title.empty() && (e.title.front() == ' ' || e.title.front() == '\n' ||
                                    e.title.front() == '\t' || e.title.front() == '\r')) {
            e.title.erase(0, 1);
        }
        while (!e.title.empty() && (e.title.back() == ' ' || e.title.back() == '\n' ||
                                    e.title.back() == '\t' || e.title.back() == '\r')) {
            e.title.pop_back();
        }
        if (e.href.empty() || e.title.empty()) continue;

        std::size_t after = it->position() + it->length();
        if (after < html.size()) {
            std::size_t cap_end = std::min(after + 1024, html.size());
            std::string window = html.substr(after, cap_end - after);
            std::string snippet = strip_tags(window);
            std::string compact;
            compact.reserve(snippet.size());
            bool prev_space = false;
            for (char c : snippet) {
                if (c == '\n' || c == '\t' || c == '\r') c = ' ';
                if (c == ' ') {
                    if (!prev_space) compact.push_back(c);
                    prev_space = true;
                } else {
                    compact.push_back(c);
                    prev_space = false;
                }
            }
            while (!compact.empty() && compact.front() == ' ') compact.erase(0, 1);
            const char* kCutMarkers[] = {"More results", "Next page", "Prev page", nullptr};
            for (int i = 0; kCutMarkers[i]; ++i) {
                auto pos = compact.find(kCutMarkers[i]);
                if (pos != std::string::npos) compact.resize(pos);
            }
            if (compact.size() > 320) compact.resize(320);
            while (!compact.empty() && compact.back() == ' ') compact.pop_back();
            e.snippet = compact;
        }

        out.push_back(std::move(e));
    }

    if (!out.empty()) return out;

    static const std::regex kResultBlock(
        R"rx(<div[^>]*class="[^"]*\bresult\b[^"]*"[^>]*>([\s\S]*?)(?=<div[^>]*class="[^"]*\bresult\b|</div>\s*</div>\s*<div[^>]*class="nav-link))rx",
        std::regex::icase | std::regex::optimize
    );
    static const std::regex kTitleAnchor(
        R"rx(<a[^>]*class="[^"]*result__a[^"]*"[^>]*href="([^"]+)"[^>]*>([\s\S]*?)</a>)rx",
        std::regex::icase | std::regex::optimize
    );
    static const std::regex kSnippet(
        R"rx(<a[^>]*class="[^"]*result__snippet[^"]*"[^>]*>([\s\S]*?)</a>)rx",
        std::regex::icase | std::regex::optimize
    );

    auto begin = std::sregex_iterator(html.begin(), html.end(), kResultBlock);
    auto end = std::sregex_iterator();

    for (auto it = begin; it != end && static_cast<int>(out.size()) < max_results; ++it) {
        const std::string block = (*it)[1].str();

        std::smatch m_title;
        if (!std::regex_search(block, m_title, kTitleAnchor)) continue;

        Entry e;
        e.href = m_title[1].str();
        e.title = strip_tags(m_title[2].str());

        std::smatch m_snip;
        if (std::regex_search(block, m_snip, kSnippet)) {
            e.snippet = strip_tags(m_snip[1].str());
        }

        if (!e.href.empty() && !e.title.empty()) {
            out.push_back(std::move(e));
        }
    }

    return out;
}

namespace {

std::string trim_ws(std::string s) {
    while (!s.empty() && (s.front() == ' ' || s.front() == '\n' ||
                          s.front() == '\t' || s.front() == '\r')) {
        s.erase(0, 1);
    }
    while (!s.empty() && (s.back() == ' ' || s.back() == '\n' ||
                          s.back() == '\t' || s.back() == '\r')) {
        s.pop_back();
    }
    return s;
}

std::string collapse_ws(const std::string& in, std::size_t cap) {
    std::string out;
    out.reserve(in.size());
    bool prev_space = false;
    for (char c : in) {
        if (c == '\n' || c == '\t' || c == '\r') c = ' ';
        if (c == ' ') {
            if (!prev_space) out.push_back(c);
            prev_space = true;
        } else {
            out.push_back(c);
            prev_space = false;
        }
    }
    out = trim_ws(std::move(out));
    if (out.size() > cap) out.resize(cap);
    return out;
}

std::vector<Entry> extract_h2_results(const std::string& html, int max_results,
                                      const std::regex& snippet_re, bool bing_unwrap) {
    std::vector<Entry> out;
    if (max_results <= 0) return out;

    static const std::regex kTitle(
        R"rx(<h2\b[^>]*>\s*<a\b[^>]*href="([^"]+)"[^>]*>([\s\S]*?)</a>)rx",
        std::regex::icase | std::regex::optimize
    );

    auto begin = std::sregex_iterator(html.begin(), html.end(), kTitle);
    auto end = std::sregex_iterator();

    for (auto it = begin; it != end && static_cast<int>(out.size()) < max_results; ++it) {
        Entry e;
        e.href = (*it)[1].str();
        if (bing_unwrap) e.href = url::unwrap_bing_redirect(e.href);
        e.title = trim_ws(strip_tags((*it)[2].str()));
        if (e.href.empty() || e.title.empty()) continue;
        if (e.href.rfind("http", 0) != 0 && e.href.rfind("//", 0) != 0) continue;
        if (e.href.rfind("//", 0) == 0) e.href = "https:" + e.href;

        std::size_t after = it->position() + it->length();
        if (after < html.size()) {
            std::size_t cap_end = std::min(after + 2000, html.size());
            std::string window = html.substr(after, cap_end - after);
            std::smatch m;
            if (std::regex_search(window, m, snippet_re)) {
                e.snippet = collapse_ws(strip_tags(m[1].str()), 500);
            }
        }
        out.push_back(std::move(e));
    }
    return out;
}

}

std::vector<Entry> extract_bing_results(const std::string& html, int max_results) {
    static const std::regex kSnippet(
        R"rx(<p\b[^>]*>([\s\S]*?)</p>)rx",
        std::regex::icase | std::regex::optimize
    );
    return extract_h2_results(html, max_results, kSnippet, true);
}

std::vector<Entry> extract_mojeek_results(const std::string& html, int max_results) {
    static const std::regex kSnippet(
        R"rx(<p\b[^>]*class="[^"]*\bs\b[^"]*"[^>]*>([\s\S]*?)</p>)rx",
        std::regex::icase | std::regex::optimize
    );
    auto out = extract_h2_results(html, max_results, kSnippet, false);
    if (!out.empty()) return out;
    static const std::regex kAnyP(
        R"rx(<p\b[^>]*>([\s\S]*?)</p>)rx",
        std::regex::icase | std::regex::optimize
    );
    return extract_h2_results(html, max_results, kAnyP, false);
}

}
