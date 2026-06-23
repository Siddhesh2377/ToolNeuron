#include "server_core.h"

#include "gen_session.h"
#include "jvm_bridge.h"
#include "openai_schema.h"
#include "reply_session.h"
#include "server_audit.h"
#include "server_auth.h"
#include "server_docs.h"
#include "server_models.h"
#include "server_rate_limit.h"
#include "server_staging.h"
#include "server_webui.h"
#include "server_crypto.h"

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

    long long now_unix_sec() {
        using clk = std::chrono::system_clock;
        return std::chrono::duration_cast<std::chrono::seconds>(
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
    namespace m = models;

    ServerCore::ServerCore() : server_(std::make_unique<httplib::Server>()) {}

    ServerCore::~ServerCore() {
        stop();
    }

    namespace {

        void respond_error(httplib::Response& res, int status,
                           const std::string& code, const std::string& message,
                           const std::string& type) {
            res.status = status;
            res.set_content(oai::error_response(status, code, message, type), "application/json");
        }

        bool require_bridge(httplib::Response& res) {
            if (jvm::has_bridge()) return true;
            respond_error(res, 503, "bridge_unavailable", "inference bridge not attached", "server_error");
            return false;
        }

        std::string resolve_model_id(const std::string& requested, m::Kind k) {
            if (!requested.empty() && m::has_id_of_kind(requested, k)) return requested;
            if (!requested.empty()) return {};
            auto fallback = m::default_of_kind(k);
            return fallback.id;
        }

        bool ensure_model_for(httplib::Response& res, std::string& model_id, m::Kind k,
                              const char* type_label) {
            if (model_id.empty()) model_id = resolve_model_id(model_id, k);
            if (model_id.empty()) {
                std::string msg = m::has_any_of_kind(k)
                    ? std::string("no default ") + type_label + " model configured"
                    : std::string("no ") + type_label + " model installed";
                respond_error(res, 404, "model_not_found", msg, "invalid_request_error");
                return false;
            }
            if (!m::has_id_of_kind(model_id, k)) {
                std::string msg = std::string("model not available for ") + type_label + ": " + model_id;
                respond_error(res, 404, "model_not_found", msg, "invalid_request_error");
                return false;
            }
            return true;
        }

        bool ensure_text_chat_model(httplib::Response& res, std::string& model_id) {
            if (model_id.empty()) model_id = resolve_model_id(model_id, m::Kind::ChatGguf);
            if (model_id.empty()) model_id = resolve_model_id(model_id, m::Kind::Vlm);
            if (model_id.empty()) {
                std::string msg = (m::has_any_of_kind(m::Kind::ChatGguf) || m::has_any_of_kind(m::Kind::Vlm))
                    ? "no default chat model configured"
                    : "no chat model installed";
                respond_error(res, 404, "model_not_found", msg, "invalid_request_error");
                return false;
            }
            if (!m::has_id_of_kind(model_id, m::Kind::ChatGguf) &&
                !m::has_id_of_kind(model_id, m::Kind::Vlm)) {
                std::string msg = std::string("model not available for chat: ") + model_id;
                respond_error(res, 404, "model_not_found", msg, "invalid_request_error");
                return false;
            }
            return true;
        }

        struct PreparedImages {
            std::vector<std::string> tmp_paths;
            ~PreparedImages() {
                for (const auto& p : tmp_paths) staging::unlink_safe(p);
            }
        };

        bool stage_image_payloads(const std::vector<oai::InlineImage>& images,
                                  PreparedImages& out, std::string& err) {
            for (const auto& img : images) {
                std::string ext = "img";
                if (img.mime == "image/png") ext = "png";
                else if (img.mime == "image/jpeg" || img.mime == "image/jpg") ext = "jpg";
                else if (img.mime == "image/webp") ext = "webp";
                std::string path = staging::make_path(std::string("vlm.") + ext);
                if (path.empty() || !staging::write_bytes(path, img.bytes.data(), img.bytes.size())) {
                    err = "failed to stage image bytes";
                    return false;
                }
                out.tmp_paths.push_back(std::move(path));
            }
            return true;
        }

        void handle_chat_text_only(httplib::Response& res,
                                   const oai::ChatRequest& req,
                                   const std::string& model_id,
                                   long long created) {
            auto session    = gen::registry().create();
            int64_t genId   = session->id();
            std::string msgJson   = req.messages.dump();
            std::string paramJson = oai::serialize_params(req);

            if (!jvm::start_generation(genId, model_id, msgJson, paramJson, {})) {
                gen::registry().erase(genId);
                respond_error(res, 503, "bridge_start_failed",
                              "inference bridge refused to start generation",
                              "server_error");
                return;
            }

            if (req.stream) {
                res.status = 200;
                res.set_header("Cache-Control", "no-cache");
                res.set_header("X-Accel-Buffering", "no");
                res.set_chunked_content_provider("text/event-stream",
                    [session, model_id, created](size_t, httplib::DataSink& sink) {
                        std::string head = oai::build_chat_stream_chunk(
                            model_id, std::string(), std::string(), true, created);
                        if (!sink.write(head.data(), head.size())) {
                            session->cancel();
                            jvm::cancel_generation(session->id());
                            return false;
                        }
                        while (true) {
                            auto t = session->take(200);
                            if (t.state == gen::TakeState::Token && !t.token.empty()) {
                                std::string chunk = oai::build_chat_stream_chunk(
                                    model_id, t.token, std::string(), false, created);
                                if (!sink.write(chunk.data(), chunk.size())) {
                                    session->cancel();
                                    jvm::cancel_generation(session->id());
                                    return false;
                                }
                            } else if (t.state == gen::TakeState::Done) {
                                std::string tail = oai::build_chat_stream_chunk(
                                    model_id, std::string(), t.finish_reason, false, created);
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
                    respond_error(res, 500, "inference_error", t.error_message, "server_error");
                    return;
                } else if (t.state == gen::TakeState::Cancelled) {
                    gen::registry().erase(genId);
                    respond_error(res, 499, "client_cancelled", "generation cancelled", "server_error");
                    return;
                }
            }

            gen::registry().erase(genId);
            res.status = 200;
            res.set_content(
                oai::build_chat_response_nonstream(model_id, collected, finish, created),
                "application/json");
        }

        void handle_chat_vlm(httplib::Response& res,
                             oai::ChatRequest& req,
                             const std::string& model_id,
                             const std::vector<oai::InlineImage>& images,
                             long long created) {
            PreparedImages staged;
            std::string err;
            if (!stage_image_payloads(images, staged, err)) {
                respond_error(res, 500, "staging_failed", err, "server_error");
                return;
            }

            auto session    = gen::registry().create();
            int64_t genId   = session->id();
            std::string msgJson   = req.messages.dump();
            std::string paramJson = oai::serialize_params(req);

            if (!jvm::start_generation(genId, model_id, msgJson, paramJson, staged.tmp_paths)) {
                gen::registry().erase(genId);
                respond_error(res, 503, "bridge_start_failed",
                              "inference bridge refused to start VLM generation",
                              "server_error");
                return;
            }

            if (req.stream) {
                res.status = 200;
                res.set_header("Cache-Control", "no-cache");
                res.set_header("X-Accel-Buffering", "no");
                std::vector<std::string> hold_paths = staged.tmp_paths;
                staged.tmp_paths.clear();
                res.set_chunked_content_provider("text/event-stream",
                    [session, model_id, created, hold_paths](size_t, httplib::DataSink& sink) {
                        std::string head = oai::build_chat_stream_chunk(
                            model_id, std::string(), std::string(), true, created);
                        if (!sink.write(head.data(), head.size())) {
                            session->cancel();
                            jvm::cancel_generation(session->id());
                            for (const auto& p : hold_paths) staging::unlink_safe(p);
                            return false;
                        }
                        while (true) {
                            auto t = session->take(400);
                            if (t.state == gen::TakeState::Token && !t.token.empty()) {
                                std::string chunk = oai::build_chat_stream_chunk(
                                    model_id, t.token, std::string(), false, created);
                                if (!sink.write(chunk.data(), chunk.size())) {
                                    session->cancel();
                                    jvm::cancel_generation(session->id());
                                    for (const auto& p : hold_paths) staging::unlink_safe(p);
                                    return false;
                                }
                            } else if (t.state == gen::TakeState::Done) {
                                std::string tail = oai::build_chat_stream_chunk(
                                    model_id, std::string(), t.finish_reason, false, created);
                                sink.write(tail.data(), tail.size());
                                std::string done = oai::build_chat_stream_done();
                                sink.write(done.data(), done.size());
                                sink.done();
                                gen::registry().erase(session->id());
                                for (const auto& p : hold_paths) staging::unlink_safe(p);
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
                                for (const auto& p : hold_paths) staging::unlink_safe(p);
                                return true;
                            } else if (t.state == gen::TakeState::Cancelled) {
                                sink.done();
                                gen::registry().erase(session->id());
                                for (const auto& p : hold_paths) staging::unlink_safe(p);
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
                    respond_error(res, 500, "inference_error", t.error_message, "server_error");
                    return;
                } else if (t.state == gen::TakeState::Cancelled) {
                    gen::registry().erase(genId);
                    respond_error(res, 499, "client_cancelled", "generation cancelled", "server_error");
                    return;
                }
            }
            gen::registry().erase(genId);
            res.status = 200;
            res.set_content(
                oai::build_chat_response_nonstream(model_id, collected, finish, created),
                "application/json");
        }

        struct MultipartFile {
            std::string filename;
            std::string content;
            std::string content_type;
        };

        bool extract_file_part(const httplib::Request& req,
                               const std::string& name,
                               MultipartFile& out) {
            auto it = req.files.find(name);
            if (it == req.files.end()) return false;
            out.filename     = it->second.filename;
            out.content      = it->second.content;
            out.content_type = it->second.content_type;
            return !out.content.empty();
        }

        std::string field_value(const httplib::Request& req, const std::string& name,
                                const std::string& fallback) {
            auto it = req.files.find(name);
            if (it != req.files.end()) return it->second.content;
            if (req.has_param(name.c_str())) return req.get_param_value(name.c_str());
            return fallback;
        }

        int field_int(const httplib::Request& req, const std::string& name, int fallback) {
            std::string s = field_value(req, name, "");
            if (s.empty()) return fallback;
            try { return std::stoi(s); } catch (...) { return fallback; }
        }

        double field_double(const httplib::Request& req, const std::string& name, double fallback) {
            std::string s = field_value(req, name, "");
            if (s.empty()) return fallback;
            try { return std::stod(s); } catch (...) { return fallback; }
        }

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
        server_->Get("/webui.css", [](const httplib::Request&, httplib::Response& res) {
            if (!webui::has_css()) {
                res.status = 503;
                res.set_content("Web UI CSS not configured", "text/plain");
                return;
            }
            res.set_content(webui::get_css(), "text/css; charset=utf-8");
        });

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
            res.set_content(m::build_list_response(), "application/json");
        });

        server_->Post("/v1/chat/completions", [](const httplib::Request& req, httplib::Response& res) {
            auto parsed = oai::parse_chat_request(req.body);
            if (!parsed.ok) {
                respond_error(res, parsed.status, parsed.error_code, parsed.error_message, "invalid_request_error");
                return;
            }

            if (!require_bridge(res)) return;

            const bool route_vlm = parsed.request.has_images;

            std::string model_id = parsed.request.model;
            if (route_vlm) {
                if (!ensure_model_for(res, model_id, m::Kind::Vlm, "VLM")) return;
            } else {
                if (!ensure_text_chat_model(res, model_id)) return;
            }

            parsed.request.model = model_id;
            long long created    = now_unix_sec();

            if (route_vlm) {
                std::vector<oai::InlineImage> images;
                json sanitized;
                std::string err;
                if (!oai::extract_images_from_messages(parsed.request.messages, images, sanitized, err)) {
                    respond_error(res, 400, "invalid_image", err, "invalid_request_error");
                    return;
                }
                parsed.request.messages = sanitized;
                handle_chat_vlm(res, parsed.request, model_id, images, created);
            } else {
                handle_chat_text_only(res, parsed.request, model_id, created);
            }
        });

        server_->Post("/v1/embeddings", [](const httplib::Request& req, httplib::Response& res) {
            if (!require_bridge(res)) return;

            json body;
            try { body = json::parse(req.body); }
            catch (const std::exception& e) {
                respond_error(res, 400, "invalid_json", std::string("invalid JSON: ") + e.what(),
                              "invalid_request_error");
                return;
            }
            if (!body.is_object()) {
                respond_error(res, 400, "invalid_request", "body must be a JSON object",
                              "invalid_request_error");
                return;
            }

            std::string model_id;
            if (body.contains("model") && body["model"].is_string()) {
                model_id = body["model"].get<std::string>();
            }
            if (!ensure_model_for(res, model_id, m::Kind::Embedding, "embedding")) return;

            auto inIt = body.find("input");
            if (inIt == body.end()) {
                respond_error(res, 400, "missing_field", "required field missing: input",
                              "invalid_request_error");
                return;
            }
            json inputs = json::array();
            int total_chars = 0;
            if (inIt->is_string()) {
                std::string s = inIt->get<std::string>();
                total_chars += static_cast<int>(s.size());
                inputs.push_back(s);
            } else if (inIt->is_array()) {
                for (const auto& v : *inIt) {
                    if (!v.is_string()) continue;
                    std::string s = v.get<std::string>();
                    total_chars += static_cast<int>(s.size());
                    inputs.push_back(s);
                }
            } else {
                respond_error(res, 400, "invalid_input", "input must be a string or array of strings",
                              "invalid_request_error");
                return;
            }
            if (inputs.empty()) {
                respond_error(res, 400, "invalid_input", "input must contain at least one entry",
                              "invalid_request_error");
                return;
            }

            auto session  = reply::registry().create();
            int64_t rid   = session->id();
            if (!jvm::start_embedding(rid, model_id, inputs.dump())) {
                reply::registry().erase(rid);
                respond_error(res, 503, "bridge_start_failed",
                              "embedding bridge refused to start", "server_error");
                return;
            }
            auto r = session->wait(120000);
            reply::registry().erase(rid);

            if (r.state == reply::State::Cancelled) {
                respond_error(res, 504, "timeout", "embedding timed out", "server_error");
                return;
            }
            if (r.state == reply::State::Error) {
                respond_error(res, 500, "embedding_failed", r.error_message, "server_error");
                return;
            }
            if (r.state != reply::State::Text) {
                respond_error(res, 500, "embedding_failed", "embedding produced no result", "server_error");
                return;
            }

            json parsed;
            try { parsed = json::parse(r.text); }
            catch (...) {
                respond_error(res, 500, "embedding_failed", "bridge returned invalid JSON", "server_error");
                return;
            }
            std::vector<std::vector<float>> vectors;
            if (parsed.is_object() && parsed.contains("vectors") && parsed["vectors"].is_array()) {
                for (const auto& v : parsed["vectors"]) {
                    if (!v.is_array()) continue;
                    std::vector<float> row;
                    row.reserve(v.size());
                    for (const auto& x : v) {
                        if (x.is_number()) row.push_back(static_cast<float>(x.get<double>()));
                    }
                    vectors.push_back(std::move(row));
                }
            }
            res.status = 200;
            res.set_content(oai::build_embedding_response(model_id, vectors, total_chars / 4),
                            "application/json");
        });

        server_->Post("/v1/audio/speech", [](const httplib::Request& req, httplib::Response& res) {
            if (!require_bridge(res)) return;
            json body;
            try { body = json::parse(req.body); }
            catch (const std::exception& e) {
                respond_error(res, 400, "invalid_json", std::string("invalid JSON: ") + e.what(),
                              "invalid_request_error");
                return;
            }
            std::string model_id;
            if (body.contains("model") && body["model"].is_string())
                model_id = body["model"].get<std::string>();
            if (!ensure_model_for(res, model_id, m::Kind::Tts, "TTS")) return;

            std::string text;
            if (body.contains("input") && body["input"].is_string()) text = body["input"].get<std::string>();
            if (text.empty()) {
                respond_error(res, 400, "missing_field", "required field missing: input",
                              "invalid_request_error");
                return;
            }
            int speaker = 0;
            if (body.contains("voice")) {
                if (body["voice"].is_number_integer()) speaker = body["voice"].get<int>();
                else if (body["voice"].is_string()) {
                    try { speaker = std::stoi(body["voice"].get<std::string>()); } catch (...) {}
                }
            }
            float speed = 1.0f;
            if (body.contains("speed") && body["speed"].is_number())
                speed = static_cast<float>(body["speed"].get<double>());

            std::string out_path = staging::make_path("speech.wav");
            if (out_path.empty()) {
                respond_error(res, 500, "staging_failed", "could not allocate staging path", "server_error");
                return;
            }

            auto session = reply::registry().create();
            int64_t rid  = session->id();
            if (!jvm::start_tts(rid, model_id, text, speaker, speed, out_path)) {
                reply::registry().erase(rid);
                staging::unlink_safe(out_path);
                respond_error(res, 503, "bridge_start_failed",
                              "TTS bridge refused to start", "server_error");
                return;
            }
            auto r = session->wait(180000);
            reply::registry().erase(rid);

            if (r.state == reply::State::Error) {
                staging::unlink_safe(out_path);
                respond_error(res, 500, "tts_failed", r.error_message, "server_error");
                return;
            }
            if (r.state != reply::State::Binary) {
                staging::unlink_safe(out_path);
                respond_error(res, 500, "tts_failed", "TTS produced no audio", "server_error");
                return;
            }
            std::vector<uint8_t> bytes;
            if (!staging::read_bytes(r.binary_path, bytes, 64 * 1024 * 1024)) {
                staging::unlink_safe(r.binary_path);
                respond_error(res, 500, "tts_failed", "could not read TTS output", "server_error");
                return;
            }
            res.status = 200;
            std::string mime = r.binary_mime.empty() ? std::string("audio/wav") : r.binary_mime;
            res.set_content(std::string(reinterpret_cast<const char*>(bytes.data()), bytes.size()), mime);
            staging::unlink_safe(r.binary_path);
        });

        server_->Post("/v1/audio/transcriptions", [](const httplib::Request& req, httplib::Response& res) {
            if (!require_bridge(res)) return;

            MultipartFile file;
            if (!extract_file_part(req, "file", file)) {
                respond_error(res, 400, "missing_field", "required form field missing: file",
                              "invalid_request_error");
                return;
            }
            std::string model_id = field_value(req, "model", "");
            if (!ensure_model_for(res, model_id, m::Kind::Stt, "STT")) return;

            std::string wav_path = staging::make_path("input.wav");
            if (wav_path.empty() ||
                !staging::write_bytes(wav_path,
                    reinterpret_cast<const uint8_t*>(file.content.data()),
                    file.content.size())) {
                respond_error(res, 500, "staging_failed", "could not stage audio bytes", "server_error");
                return;
            }

            auto session = reply::registry().create();
            int64_t rid  = session->id();
            if (!jvm::start_stt(rid, model_id, wav_path)) {
                reply::registry().erase(rid);
                staging::unlink_safe(wav_path);
                respond_error(res, 503, "bridge_start_failed",
                              "STT bridge refused to start", "server_error");
                return;
            }
            auto r = session->wait(180000);
            reply::registry().erase(rid);
            staging::unlink_safe(wav_path);

            if (r.state == reply::State::Error) {
                respond_error(res, 500, "stt_failed", r.error_message, "server_error");
                return;
            }
            if (r.state != reply::State::Text) {
                respond_error(res, 500, "stt_failed", "STT produced no transcript", "server_error");
                return;
            }
            res.status = 200;
            res.set_content(oai::build_audio_transcription_response(r.text), "application/json");
        });

        auto image_response_from_session = [](httplib::Response& res, const reply::Result& r,
                                               const std::string& staged_in,
                                               const std::string& staged_mask) {
            if (!staged_in.empty()) staging::unlink_safe(staged_in);
            if (!staged_mask.empty()) staging::unlink_safe(staged_mask);

            if (r.state == reply::State::Error) {
                respond_error(res, 500, "image_failed", r.error_message, "server_error");
                return;
            }
            if (r.state != reply::State::Binary) {
                respond_error(res, 500, "image_failed", "image generation produced no output",
                              "server_error");
                return;
            }
            std::vector<uint8_t> bytes;
            if (!staging::read_bytes(r.binary_path, bytes, 64 * 1024 * 1024)) {
                staging::unlink_safe(r.binary_path);
                respond_error(res, 500, "image_failed", "could not read image output", "server_error");
                return;
            }
            staging::unlink_safe(r.binary_path);

            std::string b64 = crypto::to_base64_std(bytes);
            std::vector<std::string> arr;
            arr.push_back(std::move(b64));
            res.status = 200;
            res.set_content(oai::build_image_response(arr, now_unix_sec()), "application/json");
        };

        server_->Post("/v1/images/generations", [image_response_from_session]
                       (const httplib::Request& req, httplib::Response& res) {
            if (!require_bridge(res)) return;
            json body;
            try { body = json::parse(req.body); }
            catch (const std::exception& e) {
                respond_error(res, 400, "invalid_json", std::string("invalid JSON: ") + e.what(),
                              "invalid_request_error");
                return;
            }
            std::string model_id;
            if (body.contains("model") && body["model"].is_string()) model_id = body["model"].get<std::string>();
            if (!ensure_model_for(res, model_id, m::Kind::ImageGen, "image_gen")) return;

            std::string prompt;
            if (body.contains("prompt") && body["prompt"].is_string()) prompt = body["prompt"].get<std::string>();
            if (prompt.empty()) {
                respond_error(res, 400, "missing_field", "required field missing: prompt",
                              "invalid_request_error");
                return;
            }

            std::string out_path = staging::make_path("image.png");
            if (out_path.empty()) {
                respond_error(res, 500, "staging_failed", "could not allocate staging path", "server_error");
                return;
            }
            json params = body;
            params["mode"] = "generate";
            params["out_path"] = out_path;

            auto session = reply::registry().create();
            int64_t rid  = session->id();
            if (!jvm::start_image_gen(rid, model_id, params.dump(), std::string(), std::string(), out_path)) {
                reply::registry().erase(rid);
                staging::unlink_safe(out_path);
                respond_error(res, 503, "bridge_start_failed",
                              "image bridge refused to start", "server_error");
                return;
            }
            auto r = session->wait(600000);
            reply::registry().erase(rid);
            image_response_from_session(res, r, std::string(), std::string());
        });

        server_->Post("/v1/images/edits", [image_response_from_session]
                       (const httplib::Request& req, httplib::Response& res) {
            if (!require_bridge(res)) return;

            MultipartFile image;
            if (!extract_file_part(req, "image", image)) {
                respond_error(res, 400, "missing_field", "required form field missing: image",
                              "invalid_request_error");
                return;
            }
            std::string model_id = field_value(req, "model", "");
            if (!ensure_model_for(res, model_id, m::Kind::ImageGen, "image_gen")) return;

            std::string prompt = field_value(req, "prompt", "");
            if (prompt.empty()) {
                respond_error(res, 400, "missing_field", "required form field missing: prompt",
                              "invalid_request_error");
                return;
            }

            std::string in_path = staging::make_path("image.png");
            if (in_path.empty() || !staging::write_bytes(in_path,
                    reinterpret_cast<const uint8_t*>(image.content.data()),
                    image.content.size())) {
                respond_error(res, 500, "staging_failed", "could not stage input image", "server_error");
                return;
            }

            std::string mask_path;
            MultipartFile mask;
            bool has_mask = extract_file_part(req, "mask", mask);
            if (has_mask) {
                mask_path = staging::make_path("mask.png");
                if (mask_path.empty() || !staging::write_bytes(mask_path,
                        reinterpret_cast<const uint8_t*>(mask.content.data()),
                        mask.content.size())) {
                    staging::unlink_safe(in_path);
                    respond_error(res, 500, "staging_failed", "could not stage mask", "server_error");
                    return;
                }
            }

            std::string out_path = staging::make_path("image.png");
            if (out_path.empty()) {
                staging::unlink_safe(in_path);
                if (!mask_path.empty()) staging::unlink_safe(mask_path);
                respond_error(res, 500, "staging_failed", "could not allocate staging path", "server_error");
                return;
            }

            json params = json::object();
            params["prompt"]          = prompt;
            params["negative_prompt"] = field_value(req, "negative_prompt", "");
            params["steps"]           = field_int(req, "steps", 20);
            params["cfg"]             = field_double(req, "cfg", 7.0);
            params["denoise"]         = field_double(req, "denoise", 0.7);
            params["scheduler"]       = field_value(req, "scheduler", "Euler a");
            params["width"]           = field_int(req, "width", 512);
            params["height"]          = field_int(req, "height", 512);
            params["mode"]            = has_mask ? "inpaint" : "edit";
            params["out_path"]        = out_path;

            auto session = reply::registry().create();
            int64_t rid  = session->id();
            if (!jvm::start_image_gen(rid, model_id, params.dump(), in_path, mask_path, out_path)) {
                reply::registry().erase(rid);
                staging::unlink_safe(in_path);
                if (!mask_path.empty()) staging::unlink_safe(mask_path);
                staging::unlink_safe(out_path);
                respond_error(res, 503, "bridge_start_failed",
                              "image bridge refused to start", "server_error");
                return;
            }
            auto r = session->wait(600000);
            reply::registry().erase(rid);
            image_response_from_session(res, r, in_path, mask_path);
        });

        server_->Post("/v1/images/upscale", [image_response_from_session]
                       (const httplib::Request& req, httplib::Response& res) {
            if (!require_bridge(res)) return;

            MultipartFile image;
            if (!extract_file_part(req, "image", image)) {
                respond_error(res, 400, "missing_field", "required form field missing: image",
                              "invalid_request_error");
                return;
            }
            std::string model_id = field_value(req, "model", "");
            if (!ensure_model_for(res, model_id, m::Kind::ImageUpscaler, "image_upscaler")) return;

            std::string in_path = staging::make_path("upscale_in.png");
            if (in_path.empty() || !staging::write_bytes(in_path,
                    reinterpret_cast<const uint8_t*>(image.content.data()),
                    image.content.size())) {
                respond_error(res, 500, "staging_failed", "could not stage input image", "server_error");
                return;
            }
            std::string out_path = staging::make_path("upscale_out.png");
            if (out_path.empty()) {
                staging::unlink_safe(in_path);
                respond_error(res, 500, "staging_failed", "could not allocate staging path", "server_error");
                return;
            }

            auto session = reply::registry().create();
            int64_t rid  = session->id();
            if (!jvm::start_image_upscale(rid, model_id, in_path, out_path)) {
                reply::registry().erase(rid);
                staging::unlink_safe(in_path);
                staging::unlink_safe(out_path);
                respond_error(res, 503, "bridge_start_failed",
                              "upscaler bridge refused to start", "server_error");
                return;
            }
            auto r = session->wait(300000);
            reply::registry().erase(rid);
            image_response_from_session(res, r, in_path, std::string());
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
        server_->set_read_timeout(60, 0);
        server_->set_write_timeout(120, 0);
        server_->set_payload_max_length(64 * 1024 * 1024);

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
