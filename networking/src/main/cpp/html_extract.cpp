#include "html_extract.h"

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

}
