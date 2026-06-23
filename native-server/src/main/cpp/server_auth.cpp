#include "server_auth.h"

#include "server_crypto.h"

#include <httplib.h>
#include <nlohmann/json.hpp>

#include <mutex>

namespace tn::server::auth {

    using json = nlohmann::json;

    namespace {
        std::mutex              g_mu;
        std::vector<uint8_t>    g_token;

        std::string extract_bearer(const httplib::Request& req) {
            auto it = req.headers.find("Authorization");
            if (it == req.headers.end()) return {};
            const std::string& v = it->second;
            const std::string prefix = "Bearer ";
            if (v.size() < prefix.size()) return {};
            if (v.compare(0, prefix.size(), prefix) != 0) return {};
            return v.substr(prefix.size());
        }
    }

    void set_token(const uint8_t* data, size_t len) {
        std::lock_guard<std::mutex> lock(g_mu);
        if (!g_token.empty()) {
            crypto::secure_zero(g_token.data(), g_token.size());
        }
        g_token.assign(data, data + len);
    }

    void clear_token() {
        std::lock_guard<std::mutex> lock(g_mu);
        if (!g_token.empty()) {
            crypto::secure_zero(g_token.data(), g_token.size());
        }
        g_token.clear();
    }

    bool has_token() {
        std::lock_guard<std::mutex> lock(g_mu);
        return !g_token.empty();
    }

    bool is_public_path(const std::string& path) {
        return path == "/health" ||
               path == "/" ||
               path == "/index.html" ||
               path == "/webui" ||
               path == "/webui.css" ||
               path == "/docs" ||
               path == "/docs/" ||
               path == "/docs/index.html";
    }

    bool check_request(const httplib::Request& req) {
        std::string bearer = extract_bearer(req);
        if (bearer.empty()) return false;

        std::lock_guard<std::mutex> lock(g_mu);
        if (g_token.empty()) return false;
        if (bearer.size() != g_token.size()) return false;

        return crypto::const_time_eq(
            reinterpret_cast<const uint8_t*>(bearer.data()),
            g_token.data(),
            g_token.size());
    }

    void write_unauthorized(httplib::Response& res) {
        json body;
        body["error"] = json::object({
            {"code",    401},
            {"message", "missing or invalid bearer token"},
            {"type",    "authentication_error"},
        });
        res.status = 401;
        res.set_header("WWW-Authenticate", "Bearer");
        res.set_content(body.dump(), "application/json");
    }

    void write_forbidden(httplib::Response& res, const std::string& msg) {
        json body;
        body["error"] = json::object({
            {"code",    403},
            {"message", msg},
            {"type",    "permission_error"},
        });
        res.status = 403;
        res.set_content(body.dump(), "application/json");
    }

}
