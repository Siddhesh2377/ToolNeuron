#pragma once

#include <string>
#include <vector>

namespace net::ddg {

struct Result {
    std::string title;
    std::string url;
    std::string snippet;
};

struct SearchError {
    std::string message;
};

struct SearchOutcome {
    std::vector<Result> results;
    SearchError error;
    bool ok = false;
};

SearchOutcome search(const std::string& query,
                     const std::string& user_agent,
                     int max_results,
                     const std::string& locale = "");

}
