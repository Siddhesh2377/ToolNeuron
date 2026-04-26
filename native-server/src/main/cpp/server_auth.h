#pragma once

#include <cstdint>
#include <string>
#include <vector>

namespace httplib {
    struct Request;
    struct Response;
}

namespace tn::server::auth {

    void set_token(const uint8_t* data, size_t len);
    void clear_token();
    bool has_token();

    bool is_public_path(const std::string& path);

    bool check_request(const httplib::Request& req);

    void write_unauthorized(httplib::Response& res);
    void write_forbidden(httplib::Response& res, const std::string& msg);

}
