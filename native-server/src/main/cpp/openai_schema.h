#pragma once

#include <nlohmann/json.hpp>

#include <string>

namespace tn::server::oai {

    struct ChatRequest {
        std::string        model;
        nlohmann::json     messages;
        bool               stream           = false;
        int                max_tokens       = 0;
        float              temperature      = 0.0f;
        float              top_p            = 0.0f;
        float              presence_penalty = 0.0f;
        float              frequency_penalty = 0.0f;
        nlohmann::json     stop             = nlohmann::json();
        bool               has_temperature  = false;
        bool               has_top_p        = false;
        bool               has_max_tokens   = false;
    };

    struct ParseResult {
        bool        ok    = false;
        int         status = 400;
        std::string error_code;
        std::string error_message;
        ChatRequest request;
    };

    ParseResult parse_chat_request(const std::string& body);

    std::string build_chat_response_nonstream(
        const std::string& model_id,
        const std::string& content,
        const std::string& finish_reason,
        long long created_unix);

    std::string build_chat_stream_chunk(
        const std::string& model_id,
        const std::string& delta,
        const std::string& finish_reason,
        bool is_role,
        long long created_unix);

    std::string build_chat_stream_done();

    std::string make_chat_completion_id();

    std::string error_response(int status,
                               const std::string& code,
                               const std::string& message,
                               const std::string& type);

    std::string serialize_params(const ChatRequest& req);

}
