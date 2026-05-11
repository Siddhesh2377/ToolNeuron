#include "policy_engine.h"
#include "memory_guard.h"

#include <array>
#include <cstring>
#include <mutex>
#include <openssl/mem.h>

namespace hxs {
namespace policy {

namespace {

struct State {
    std::mutex mu;
    bool tampered = false;
    bool passthrough = false;
    bool session_active = false;
    std::array<uint8_t, SESSION_TOKEN_LEN> token{};
};

State& state() {
    static State s;
    return s;
}

bool is_unauth_feature(uint32_t fid) {
    switch (fid) {
        case FEATURE_APP_LAUNCH:
        case FEATURE_OPEN_VAULT:
        case FEATURE_AUTH_SETUP:
        case FEATURE_AUTH_VERIFY:
        case FEATURE_UI_PASSWORD_SCREEN:
        case FEATURE_UI_SETUP_SCREEN:
        case FEATURE_UI_INTRO:
            return true;
        default:
            return false;
    }
}

bool is_pro_feature(uint32_t fid) {
    return fid >= 1000;
}

}

void set_passthrough(bool passthrough) {
    auto& s = state();
    std::lock_guard<std::mutex> lk(s.mu);
    if (s.tampered) return;
    s.passthrough = passthrough;
}

void register_session(const uint8_t token[SESSION_TOKEN_LEN]) {
    auto& s = state();
    std::lock_guard<std::mutex> lk(s.mu);
    if (s.tampered) return;
    std::memcpy(s.token.data(), token, SESSION_TOKEN_LEN);
    s.session_active = true;
}

void invalidate_session() {
    auto& s = state();
    std::lock_guard<std::mutex> lk(s.mu);
    s.session_active = false;
    hxs::secure_zero(s.token.data(), SESSION_TOKEN_LEN);
}

void mark_tampered() {
    auto& s = state();
    std::lock_guard<std::mutex> lk(s.mu);
    s.tampered = true;
    s.session_active = false;
    s.passthrough = false;
    hxs::secure_zero(s.token.data(), SESSION_TOKEN_LEN);
}

bool is_tampered() {
    auto& s = state();
    std::lock_guard<std::mutex> lk(s.mu);
    return s.tampered;
}

bool has_session() {
    auto& s = state();
    std::lock_guard<std::mutex> lk(s.mu);
    return s.session_active && !s.tampered;
}

void reset_for_testing() {
    auto& s = state();
    std::lock_guard<std::mutex> lk(s.mu);
    s.tampered = false;
    s.passthrough = false;
    s.session_active = false;
    hxs::secure_zero(s.token.data(), SESSION_TOKEN_LEN);
}

bool is_allowed(uint32_t feature_id, const uint8_t* token, size_t token_len) {
    auto& s = state();
    std::lock_guard<std::mutex> lk(s.mu);

    if (s.tampered) return false;

    if (is_pro_feature(feature_id)) {
        return false;
    }

    if (is_unauth_feature(feature_id)) {
        return true;
    }

    if (s.passthrough) {
        return true;
    }

    if (!s.session_active) return false;
    if (token == nullptr || token_len != SESSION_TOKEN_LEN) return false;

    return CRYPTO_memcmp(s.token.data(), token, SESSION_TOKEN_LEN) == 0;
}

}
}
