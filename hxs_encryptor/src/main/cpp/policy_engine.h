#pragma once

#include <cstdint>
#include <cstddef>

namespace hxs {
namespace policy {

enum FeatureId : uint32_t {
    FEATURE_APP_LAUNCH         = 0,
    FEATURE_OPEN_VAULT         = 1,
    FEATURE_AUTH_SETUP         = 2,
    FEATURE_AUTH_VERIFY        = 3,
    FEATURE_AUTH_DISABLE       = 4,
    FEATURE_READ_MODEL         = 5,
    FEATURE_WRITE_MODEL        = 6,
    FEATURE_READ_CHAT          = 7,
    FEATURE_WRITE_CHAT         = 8,
    FEATURE_READ_RAG           = 9,
    FEATURE_WRITE_RAG          = 10,
    FEATURE_INFERENCE          = 11,
    FEATURE_UI_SETTINGS        = 12,
    FEATURE_UI_PASSWORD_SCREEN = 13,
    FEATURE_UI_SETUP_SCREEN    = 14,
    FEATURE_UI_INTRO           = 15,
    FEATURE_UI_HOME            = 16,
    FEATURE_UI_MODEL_STORE     = 17,
    FEATURE_UI_MODEL_MANAGER   = 18,
    FEATURE_UI_DEV_NOTES       = 19,
    FEATURE_UI_GUIDE           = 20,

    FEATURE_PRO_UNLIMITED_CONTEXT = 1000,
    FEATURE_PRO_ADVANCED_RAG      = 1001,
    FEATURE_PRO_EXPORT_CHATS      = 1002,
};

constexpr size_t SESSION_TOKEN_LEN = 32;

void set_passthrough(bool passthrough);

void register_session(const uint8_t token[SESSION_TOKEN_LEN]);

void invalidate_session();

void mark_tampered();

bool is_tampered();

bool is_allowed(uint32_t feature_id, const uint8_t* token, size_t token_len);

bool has_session();

void reset_for_testing();

}
}
