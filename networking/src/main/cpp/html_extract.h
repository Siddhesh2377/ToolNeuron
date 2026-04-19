#pragma once

#include <string>
#include <vector>

namespace net::html {

struct Entry {
    std::string title;
    std::string href;
    std::string snippet;
};

std::vector<Entry> extract_ddg_results(const std::string& html, int max_results);

std::string strip_tags(const std::string& html);

std::string decode_entities(const std::string& in);

}
