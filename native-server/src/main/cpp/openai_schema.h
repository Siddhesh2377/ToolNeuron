#pragma once

#include <nlohmann/json.hpp>

#include <string>
#include <vector>

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
        bool               has_images       = false;
    };

    struct ParseResult {
        bool        ok    = false;
        int         status = 400;
        std::string error_code;
        std::string error_message;
        ChatRequest request;
    };

    struct InlineImage {
        std::string mime;
        std::vector<uint8_t> bytes;
    };

    ParseResult parse_chat_request(const std::string& body);

    bool extract_images_from_messages(const nlohmann::json& messages,
                                      std::vector<InlineImage>& out,
                                      nlohmann::json& sanitized_messages,
                                      std::string& parse_error);

    nlohmann::json flatten_text_parts(const nlohmann::json& messages);

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

    std::string build_embedding_response(const std::string& model_id,
                                         const std::vector<std::vector<float>>& vectors,
                                         int prompt_tokens);

    std::string build_audio_transcription_response(const std::string& text);

    std::string build_image_response(const std::vector<std::string>& b64_pngs,
                                     long long created_unix);

}
