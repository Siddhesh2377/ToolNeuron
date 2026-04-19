#include "http_backend.h"

namespace net {

std::optional<HttpResponse> http_execute(const HttpRequest&) {
    return std::nullopt;
}

bool http_available() {
    return false;
}

const char* http_backend_name() {
    return "stub";
}

void http_set_ca_bundle(const std::string&) {}

void http_set_impersonate_profile(const std::string&) {}

}
