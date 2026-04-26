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

}
