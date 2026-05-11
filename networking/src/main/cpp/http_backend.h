#pragma once

#include <string>
#include <vector>
#include <optional>
#include <utility>

namespace net {

using Header = std::pair<std::string, std::string>;

struct HttpRequest {
    std::string url;
    std::string method = "GET";
    std::string body;
    std::vector<Header> headers;
    int timeout_ms = 15000;
    bool follow_redirects = true;
};

struct HttpResponse {
    int status = 0;
    std::string body;
    std::vector<Header> headers;
    std::string error;
};

std::optional<HttpResponse> http_execute(const HttpRequest& req);

bool http_available();

const char* http_backend_name();

void http_set_ca_bundle(const std::string& path);

void http_set_impersonate_profile(const std::string& profile);

}
