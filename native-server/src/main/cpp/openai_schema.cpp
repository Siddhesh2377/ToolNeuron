#include "openai_schema.h"

#include "server_crypto.h"

namespace tn::server::oai {

    using json = nlohmann::json;

    namespace {
        template <typename T>
        bool get_opt(const json& o, const std::string& key, T& out) {
            auto it = o.find(key);
            if (it == o.end() || it->is_null()) return false;
            try {
                out = it->get<T>();
                return true;
            } catch (...) {
                return false;
            }
        }

        std::string make_id(const std::string& prefix) {
            auto raw = tn::server::crypto::random_bytes(18);
            if (raw.empty()) return prefix + "000000";
            return prefix + tn::server::crypto::to_base64url(raw);
        }

        std::string flatten_parts_to_text(const json& parts) {
            if (!parts.is_array()) return {};
            std::string flat_text;
            for (const auto& part : parts) {
                if (!part.is_object()) continue;
                auto tIt = part.find("type");
                if (tIt == part.end() || !tIt->is_string()) continue;
                const std::string type = tIt->get<std::string>();
                if (type == "text") {
                    auto vIt = part.find("text");
                    if (vIt != part.end() && vIt->is_string()) {
                        if (!flat_text.empty()) flat_text.push_back('\n');
                        flat_text += vIt->get<std::string>();
                    }
                } else if (type == "image_url") {
                    if (!flat_text.empty()) flat_text.push_back('\n');
                    flat_text += "[Image attached]";
                }
            }
            return flat_text;
        }
    }

    ParseResult parse_chat_request(const std::string& body) {
        ParseResult out;
        json j;
        try {
            j = json::parse(body);
        } catch (const std::exception& e) {
            out.status        = 400;
            out.error_code    = "invalid_json";
            out.error_message = std::string("invalid JSON body: ") + e.what();
            return out;
        }
        if (!j.is_object()) {
            out.status        = 400;
            out.error_code    = "invalid_request";
            out.error_message = "request body must be a JSON object";
            return out;
        }

        if (!get_opt<std::string>(j, "model", out.request.model) || out.request.model.empty()) {
            out.status        = 400;
            out.error_code    = "missing_field";
            out.error_message = "required field missing: model";
            return out;
        }

        auto mIt = j.find("messages");
        if (mIt == j.end() || !mIt->is_array() || mIt->empty()) {
            out.status        = 400;
            out.error_code    = "missing_field";
            out.error_message = "required field missing or empty: messages";
            return out;
        }
        out.request.messages = *mIt;

        for (const auto& msg : *mIt) {
            if (!msg.is_object()) continue;
            auto cIt = msg.find("content");
            if (cIt == msg.end() || !cIt->is_array()) continue;
            for (const auto& part : *cIt) {
                if (!part.is_object()) continue;
                auto tIt = part.find("type");
                if (tIt != part.end() && tIt->is_string() && tIt->get<std::string>() == "image_url") {
                    out.request.has_images = true;
                    break;
                }
            }
            if (out.request.has_images) break;
        }

        get_opt<bool>(j, "stream", out.request.stream);

        {
            int v = 0;
            if (get_opt<int>(j, "max_tokens", v) && v > 0) {
                out.request.max_tokens     = v;
                out.request.has_max_tokens = true;
            }
        }
        {
            double v = 0;
            if (get_opt<double>(j, "temperature", v)) {
                out.request.temperature     = static_cast<float>(v);
                out.request.has_temperature = true;
            }
        }
        {
            double v = 0;
            if (get_opt<double>(j, "top_p", v)) {
                out.request.top_p     = static_cast<float>(v);
                out.request.has_top_p = true;
            }
        }
        {
            double v = 0;
            if (get_opt<double>(j, "presence_penalty", v)) {
                out.request.presence_penalty = static_cast<float>(v);
            }
        }
        {
            double v = 0;
            if (get_opt<double>(j, "frequency_penalty", v)) {
                out.request.frequency_penalty = static_cast<float>(v);
            }
        }
        {
            auto sIt = j.find("stop");
            if (sIt != j.end() && !sIt->is_null()) out.request.stop = *sIt;
        }

        out.ok = true;
        return out;
    }

    bool extract_images_from_messages(const json& messages,
                                      std::vector<InlineImage>& out,
                                      json& sanitized_messages,
                                      std::string& parse_error) {
        sanitized_messages = json::array();
        out.clear();
        for (const auto& msg : messages) {
            if (!msg.is_object()) {
                sanitized_messages.push_back(msg);
                continue;
            }
            auto cIt = msg.find("content");
            if (cIt == msg.end() || !cIt->is_array()) {
                sanitized_messages.push_back(msg);
                continue;
            }

            json new_msg = msg;
            std::string flat_text;
            for (const auto& part : *cIt) {
                if (!part.is_object()) continue;
                auto tIt = part.find("type");
                if (tIt == part.end() || !tIt->is_string()) continue;
                const std::string type = tIt->get<std::string>();
                if (type == "text") {
                    auto vIt = part.find("text");
                    if (vIt != part.end() && vIt->is_string()) {
                        if (!flat_text.empty()) flat_text.push_back('\n');
                        flat_text += vIt->get<std::string>();
                    }
                } else if (type == "image_url") {
                    auto urlObj = part.find("image_url");
                    if (urlObj == part.end()) continue;
                    std::string url;
                    if (urlObj->is_string()) {
                        url = urlObj->get<std::string>();
                    } else if (urlObj->is_object()) {
                        auto uu = urlObj->find("url");
                        if (uu != urlObj->end() && uu->is_string()) url = uu->get<std::string>();
                    }
                    if (url.empty()) continue;

                    const std::string prefix = "data:";
                    if (url.compare(0, prefix.size(), prefix) != 0) {
                        parse_error = "image_url must be data:image/...;base64,... (network URLs are not supported on this offline server)";
                        return false;
                    }
                    auto semi = url.find(';', prefix.size());
                    auto comma = url.find(',', prefix.size());
                    if (semi == std::string::npos || comma == std::string::npos || comma < semi) {
                        parse_error = "malformed data URL";
                        return false;
                    }
                    std::string mime = url.substr(prefix.size(), semi - prefix.size());
                    std::string enc_marker = url.substr(semi + 1, comma - semi - 1);
                    if (enc_marker != "base64") {
                        parse_error = "only base64-encoded data URLs are supported";
                        return false;
                    }
                    std::string b64 = url.substr(comma + 1);
                    InlineImage img;
                    img.mime = mime;
                    if (!tn::server::crypto::from_base64_any(b64, img.bytes) || img.bytes.empty()) {
                        parse_error = "invalid base64 in image_url";
                        return false;
                    }
                    out.push_back(std::move(img));
                }
            }
            new_msg["content"] = flat_text;
            sanitized_messages.push_back(new_msg);
        }
        return true;
    }

    json flatten_text_parts(const json& messages) {
        if (!messages.is_array()) return messages;
        json sanitized = json::array();
        for (const auto& msg : messages) {
            if (!msg.is_object()) {
                sanitized.push_back(msg);
                continue;
            }
            auto cIt = msg.find("content");
            if (cIt == msg.end() || !cIt->is_array()) {
                sanitized.push_back(msg);
                continue;
            }
            json new_msg = msg;
            new_msg["content"] = flatten_parts_to_text(*cIt);
            sanitized.push_back(std::move(new_msg));
        }
        return sanitized;
    }

    std::string build_chat_response_nonstream(
        const std::string& model_id,
        const std::string& content,
        const std::string& finish_reason,
        long long created_unix) {
        json root;
        root["id"]      = make_id("chatcmpl_");
        root["object"]  = "chat.completion";
        root["created"] = created_unix;
        root["model"]   = model_id;
        json choice;
        choice["index"]         = 0;
        json msg;
        msg["role"]             = "assistant";
        msg["content"]          = content;
        choice["message"]       = std::move(msg);
        choice["finish_reason"] = finish_reason.empty() ? "stop" : finish_reason;
        root["choices"]         = json::array({ choice });
        return root.dump();
    }

    std::string build_chat_stream_chunk(
        const std::string& model_id,
        const std::string& delta,
        const std::string& finish_reason,
        bool is_role,
        long long created_unix) {
        json root;
        root["id"]      = make_id("chatcmpl_");
        root["object"]  = "chat.completion.chunk";
        root["created"] = created_unix;
        root["model"]   = model_id;
        json choice;
        choice["index"] = 0;
        json d = json::object();
        if (is_role) d["role"] = "assistant";
        if (!delta.empty()) d["content"] = delta;
        choice["delta"] = std::move(d);
        choice["finish_reason"] = finish_reason.empty() ? json(nullptr) : json(finish_reason);
        root["choices"] = json::array({ choice });
        return "data: " + root.dump() + "\n\n";
    }

    std::string build_chat_stream_done() {
        return std::string("data: [DONE]\n\n");
    }

    std::string make_chat_completion_id() {
        return make_id("chatcmpl_");
    }

    std::string error_response(int /*status*/,
                               const std::string& code,
                               const std::string& message,
                               const std::string& type) {
        json root;
        json err;
        err["code"]    = code;
        err["message"] = message;
        err["type"]    = type;
        root["error"]  = std::move(err);
        return root.dump();
    }

    std::string serialize_params(const ChatRequest& req) {
        json j;
        if (req.has_temperature) j["temperature"]  = req.temperature;
        if (req.has_top_p)       j["top_p"]        = req.top_p;
        if (req.has_max_tokens)  j["max_tokens"]   = req.max_tokens;
        j["presence_penalty"]   = req.presence_penalty;
        j["frequency_penalty"]  = req.frequency_penalty;
        if (!req.stop.is_null()) j["stop"]         = req.stop;
        return j.dump();
    }

    std::string build_embedding_response(const std::string& model_id,
                                         const std::vector<std::vector<float>>& vectors,
                                         int prompt_tokens) {
        json root;
        root["object"] = "list";
        json data = json::array();
        for (size_t i = 0; i < vectors.size(); ++i) {
            json entry;
            entry["object"] = "embedding";
            entry["index"]  = static_cast<int>(i);
            entry["embedding"] = vectors[i];
            data.push_back(std::move(entry));
        }
        root["data"]  = std::move(data);
        root["model"] = model_id;
        json usage;
        usage["prompt_tokens"] = prompt_tokens;
        usage["total_tokens"]  = prompt_tokens;
        root["usage"] = std::move(usage);
        return root.dump();
    }

    std::string build_audio_transcription_response(const std::string& text) {
        json root;
        root["text"] = text;
        return root.dump();
    }

    std::string build_image_response(const std::vector<std::string>& b64_pngs,
                                     long long created_unix) {
        json root;
        root["created"] = created_unix;
        json data = json::array();
        for (const auto& b64 : b64_pngs) {
            json entry;
            entry["b64_json"] = b64;
            data.push_back(std::move(entry));
        }
        root["data"] = std::move(data);
        return root.dump();
    }

}
