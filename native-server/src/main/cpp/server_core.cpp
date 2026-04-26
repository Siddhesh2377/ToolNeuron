#include "server_core.h"

#include "gen_session.h"
#include "jvm_bridge.h"
#include "openai_schema.h"
#include "server_audit.h"
#include "server_auth.h"
#include "server_docs.h"
#include "server_models.h"
#include "server_rate_limit.h"
#include "server_webui.h"

#include <httplib.h>
#include <nlohmann/json.hpp>

#include <android/log.h>
#include <chrono>

namespace {
    thread_local long long tls_request_start_ms = 0;

    long long now_unix_ms() {
        using clk = std::chrono::system_clock;
        return std::chrono::duration_cast<std::chrono::milliseconds>(
            clk::now().time_since_epoch()).count();
    }

    std::string client_addr_mask(const std::string& addr) {
        if (addr.empty()) return addr;
        auto dot3 = addr.rfind('.');
        if (dot3 != std::string::npos) return addr.substr(0, dot3) + ".x";
        auto col = addr.rfind(':');
        if (col != std::string::npos) return addr.substr(0, col) + ":x";
        return addr;
    }
}

#define LOG_TAG "tn_server"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO,  LOG_TAG, __VA_ARGS__)
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN,  LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

namespace tn::server {

    using json = nlohmann::json;

    ServerCore::ServerCore() : server_(std::make_unique<httplib::Server>()) {}

    ServerCore::~ServerCore() {
        stop();
    }

    void ServerCore::registerRoutes() {
        server_->set_pre_routing_handler([](const httplib::Request& req, httplib::Response& res) {
            tls_request_start_ms = now_unix_ms();
            long long now_ms = tls_request_start_ms;
            const std::string& addr = req.remote_addr;

            if (rl::is_banned(addr, now_ms)) {
                auth::write_forbidden(res, "client banned after repeated auth failures");
                return httplib::Server::HandlerResponse::Handled;
            }

            if (!rl::allow(addr, now_ms)) {
                json body;
                body["error"] = json::object({
                    {"code",    429},
                    {"message", "rate limit exceeded"},
                    {"type",    "rate_limit_error"},
                });
                res.status = 429;
                res.set_header("Retry-After", "1");
                res.set_content(body.dump(), "application/json");
                return httplib::Server::HandlerResponse::Handled;
            }

            if (auth::is_public_path(req.path)) return httplib::Server::HandlerResponse::Unhandled;

            if (!auth::check_request(req)) {
                rl::note_auth_fail(addr, now_ms);
                auth::write_unauthorized(res);
                return httplib::Server::HandlerResponse::Handled;
            }
            rl::note_auth_success(addr);
            return httplib::Server::HandlerResponse::Unhandled;
        });

        server_->set_post_routing_handler([](const httplib::Request& req, const httplib::Response& res) {
            long long end_ms  = now_unix_ms();
            long long elapsed = tls_request_start_ms > 0 ? (end_ms - tls_request_start_ms) : 0;
            tls_request_start_ms = 0;

            audit::Event e;
            e.timestamp_unix_ms = end_ms;
            e.method            = req.method;
            e.path              = req.path;
            e.status            = res.status;
            e.duration_ms       = elapsed;
            e.client_addr_hash  = client_addr_mask(req.remote_addr);
            audit::record(e);

            json ev;
            ev["ts_ms"]       = e.timestamp_unix_ms;
            ev["method"]      = e.method;
            ev["path"]        = e.path;
            ev["status"]      = e.status;
            ev["duration_ms"] = e.duration_ms;
            ev["client"]      = e.client_addr_hash;
            jvm::emit_request_event(ev.dump());
        });

        server_->Get("/health", [](const httplib::Request&, httplib::Response& res) {
            json body;
            body["status"]  = "ok";
            body["service"] = "tool-neuron";
            body["version"] = CPPHTTPLIB_VERSION;
            res.set_content(body.dump(), "application/json");
        });

        auto serve_webui = [](const httplib::Request&, httplib::Response& res) {
            if (!webui::has_html()) {
                res.status = 503;
                res.set_content("Web UI not configured", "text/plain");
                return;
            }
            res.set_content(webui::get_html(), "text/html; charset=utf-8");
        };
        server_->Get("/", serve_webui);
        server_->Get("/index.html", serve_webui);
        server_->Get("/webui", serve_webui);

        auto serve_docs = [](const httplib::Request&, httplib::Response& res) {
            if (!docs::has_html()) {
                res.status = 503;
                res.set_content("Docs not configured", "text/plain");
                return;
            }
            res.set_content(docs::get_html(), "text/html; charset=utf-8");
        };
        server_->Get("/docs", serve_docs);
        server_->Get("/docs/", serve_docs);
        server_->Get("/docs/index.html", serve_docs);

        server_->Get("/v1/models", [](const httplib::Request&, httplib::Response& res) {
            res.set_content(models::build_list_response(), "application/json");
        });

        server_->Post("/v1/chat/completions", [](const httplib::Request& req, httplib::Response& res) {
            using clk = std::chrono::system_clock;
            auto now_unix = [] {
                return std::chrono::duration_cast<std::chrono::seconds>(
                    clk::now().time_since_epoch()).count();
            };

            auto parsed = oai::parse_chat_request(req.body);
            if (!parsed.ok) {
                res.status = parsed.status;
                res.set_content(
                    oai::error_response(parsed.status, parsed.error_code, parsed.error_message, "invalid_request_error"),
                    "application/json");
                return;
            }

            if (!models::has_id(parsed.request.model)) {
                res.status = 404;
                res.set_content(
                    oai::error_response(404, "model_not_found",
                        "model not available: " + parsed.request.model,
                        "invalid_request_error"),
                    "application/json");
                return;
            }

            if (!jvm::has_bridge()) {
                res.status = 503;
                res.set_content(
                    oai::error_response(503, "bridge_unavailable",
                        "inference bridge not attached",
                        "server_error"),
                    "application/json");
                return;
            }

            auto session    = gen::registry().create();
            int64_t genId   = session->id();
            std::string msgJson   = parsed.request.messages.dump();
            std::string paramJson = oai::serialize_params(parsed.request);

            if (!jvm::start_generation(genId, msgJson, paramJson)) {
                gen::registry().erase(genId);
                res.status = 503;
                res.set_content(
                    oai::error_response(503, "bridge_start_failed",
                        "inference bridge refused to start generation",
                        "server_error"),
                    "application/json");
                return;
            }

            const std::string modelId = parsed.request.model;
            const long long   created = now_unix();

            if (parsed.request.stream) {
                res.status = 200;
                res.set_header("Cache-Control", "no-cache");
                res.set_header("X-Accel-Buffering", "no");
                res.set_chunked_content_provider("text/event-stream",
                    [session, modelId, created](size_t /*offset*/, httplib::DataSink& sink) {
                        std::string head = oai::build_chat_stream_chunk(
                            modelId, std::string(), std::string(), true, created);
                        if (!sink.write(head.data(), head.size())) {
                            session->cancel();
                            jvm::cancel_generation(session->id());
                            return false;
                        }

                        while (true) {
                            auto t = session->take(200);
                            if (t.state == gen::TakeState::Token && !t.token.empty()) {
                                std::string chunk = oai::build_chat_stream_chunk(
                                    modelId, t.token, std::string(), false, created);
                                if (!sink.write(chunk.data(), chunk.size())) {
                                    session->cancel();
                                    jvm::cancel_generation(session->id());
                                    return false;
                                }
                            } else if (t.state == gen::TakeState::Done) {
                                std::string tail = oai::build_chat_stream_chunk(
                                    modelId, std::string(), t.finish_reason, false, created);
                                sink.write(tail.data(), tail.size());
                                std::string done = oai::build_chat_stream_done();
                                sink.write(done.data(), done.size());
                                sink.done();
                                gen::registry().erase(session->id());
                                return true;
                            } else if (t.state == gen::TakeState::Error) {
                                std::string payload = oai::error_response(500, "inference_error",
                                    t.error_message, "server_error");
                                std::string frame = "data: " + payload + "\n\n";
                                sink.write(frame.data(), frame.size());
                                std::string done = oai::build_chat_stream_done();
                                sink.write(done.data(), done.size());
                                sink.done();
                                gen::registry().erase(session->id());
                                return true;
                            } else if (t.state == gen::TakeState::Cancelled) {
                                sink.done();
                                gen::registry().erase(session->id());
                                return true;
                            }
                        }
                    });
                return;
            }

            std::string collected;
            std::string finish = "stop";
            while (true) {
                auto t = session->take(500);
                if (t.state == gen::TakeState::Token) {
                    collected += t.token;
                } else if (t.state == gen::TakeState::Done) {
                    finish = t.finish_reason.empty() ? "stop" : t.finish_reason;
                    break;
                } else if (t.state == gen::TakeState::Error) {
                    gen::registry().erase(genId);
                    res.status = 500;
                    res.set_content(
                        oai::error_response(500, "inference_error",
                            t.error_message, "server_error"),
                        "application/json");
                    return;
                } else if (t.state == gen::TakeState::Cancelled) {
                    gen::registry().erase(genId);
                    res.status = 499;
                    res.set_content(
                        oai::error_response(499, "client_cancelled",
                            "generation cancelled",
                            "server_error"),
                        "application/json");
                    return;
                }
            }

            gen::registry().erase(genId);
            res.status = 200;
            res.set_content(
                oai::build_chat_response_nonstream(modelId, collected, finish, created),
                "application/json");
        });

        server_->set_error_handler([](const httplib::Request&, httplib::Response& res) {
            if (!res.body.empty()) return;
            json body;
            std::string msg;
            switch (res.status) {
                case 401: msg = "missing or invalid bearer token"; break;
                case 403: msg = "forbidden"; break;
                case 404: msg = "not found"; break;
                case 405: msg = "method not allowed"; break;
                case 429: msg = "rate limit exceeded"; break;
                default:  msg = res.status >= 500 ? "internal server error" : "request failed"; break;
            }
            body["error"] = json::object({
                {"code",    res.status},
                {"message", msg},
            });
            res.set_content(body.dump(), "application/json");
        });

        server_->set_exception_handler([](const httplib::Request&, httplib::Response& res, std::exception_ptr ep) {
            std::string what = "internal error";
            try {
                if (ep) std::rethrow_exception(ep);
            } catch (const std::exception& e) {
                what = e.what();
            } catch (...) {}
            json body;
            body["error"] = json::object({
                {"code",    500},
                {"message", what},
            });
            res.status = 500;
            res.set_content(body.dump(), "application/json");
        });
    }

    bool ServerCore::start(const std::string& host, int port) {
        std::lock_guard<std::mutex> lock(lifecycle_mu_);
        if (running_.load()) return false;

        server_ = std::make_unique<httplib::Server>();
        server_->set_keep_alive_max_count(4);
        server_->set_read_timeout(15, 0);
        server_->set_write_timeout(30, 0);
        server_->set_payload_max_length(1 * 1024 * 1024);

        registerRoutes();

        int bound = server_->bind_to_port(host.c_str(), port);
        if (bound < 0) {
            LOGE("bind_to_port failed host=%s port=%d", host.c_str(), port);
            return false;
        }
        bound_port_.store(bound);
        running_.store(true);

        LOGI("server bound host=%s port=%d", host.c_str(), bound);

        listen_thread_ = std::thread([this, host] {
            bool ok = server_->listen_after_bind();
            if (!ok) LOGW("listen_after_bind returned false host=%s", host.c_str());
            running_.store(false);
            bound_port_.store(-1);
        });

        return true;
    }

    void ServerCore::stop() {
        std::lock_guard<std::mutex> lock(lifecycle_mu_);
        if (!running_.load() && !listen_thread_.joinable()) return;

        if (server_) server_->stop();
        if (listen_thread_.joinable()) listen_thread_.join();

        running_.store(false);
        bound_port_.store(-1);
        LOGI("server stopped");
    }

    ServerCore& core() {
        static ServerCore instance;
        return instance;
    }

}
