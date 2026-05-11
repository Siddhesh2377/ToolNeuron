#include "boot_integrity.h"
#include "auth.h"
#include "integrity.h"
#include "memory_guard.h"
#include "policy_engine.h"

#include <array>
#include <atomic>
#include <cstdlib>
#include <cstring>
#include <openssl/mem.h>
#include <sys/ptrace.h>
#include <unistd.h>
#include <android/log.h>

namespace hxs {
namespace boot {

namespace {

constexpr const char* LOG_TAG = "HXS_Boot";

std::atomic<bool> g_ptrace_installed{false};
std::atomic<bool> g_relaxed_for_testing{false};

constexpr size_t BASELINE_BYTES = 32;

struct FnBaseline {
    const uint8_t* addr;
    uint8_t bytes[BASELINE_BYTES];
};

std::array<FnBaseline, 3> g_hook_baselines{};
std::atomic<bool> g_baselines_captured{false};

}

void install_ptrace_self_trace() {
    if (g_ptrace_installed.exchange(true)) return;
    long r = ptrace(PTRACE_TRACEME, 0, nullptr, nullptr);
    if (r == -1) {
    }
}

uint32_t scan_process_environment() {
    uint32_t reasons = FAIL_NONE;
    if (hxs::IntegrityGuard::is_debugger_attached()) reasons |= FAIL_DEBUGGER;
    if (hxs::IntegrityGuard::is_frida_present())    reasons |= FAIL_FRIDA;
    if (hxs::IntegrityGuard::is_xposed_present())   reasons |= FAIL_XPOSED;
    return reasons;
}

uint32_t verify_lib_hashes(const LibCheck* libs, size_t count) {
    if (count == 0) return FAIL_NONE;
    if (libs == nullptr) return FAIL_BAD_INPUT;

    uint8_t actual[32];
    for (size_t i = 0; i < count; i++) {
        const auto& check = libs[i];
        if (check.path == nullptr || check.expected_hash_32 == nullptr) {
            return FAIL_BAD_INPUT;
        }
        if (!hxs::IntegrityGuard::hash_file(check.path, actual)) {
            return FAIL_LIB_HASH;
        }
        if (CRYPTO_memcmp(actual, check.expected_hash_32, 32) != 0) {
            hxs::secure_zero(actual, sizeof(actual));
            return FAIL_LIB_HASH;
        }
    }
    hxs::secure_zero(actual, sizeof(actual));
    return FAIL_NONE;
}

uint32_t boot_verify(const LibCheck* libs, size_t count) {
    uint32_t reasons = scan_process_environment();
    reasons |= verify_lib_hashes(libs, count);
    if (!verify_hook_baselines()) reasons |= FAIL_INLINE_HOOK;
    return reasons;
}

void capture_hook_baselines() {
    if (g_baselines_captured.exchange(true)) return;

    auto* verify_fn     = reinterpret_cast<const uint8_t*>(&hxs::auth::verify);
    auto* allow_fn      = reinterpret_cast<const uint8_t*>(&hxs::policy::is_allowed);
    auto* hard_fail_fn  = reinterpret_cast<const uint8_t*>(&hxs::boot::hard_fail);

    g_hook_baselines[0].addr = verify_fn;
    std::memcpy(g_hook_baselines[0].bytes, verify_fn, BASELINE_BYTES);

    g_hook_baselines[1].addr = allow_fn;
    std::memcpy(g_hook_baselines[1].bytes, allow_fn, BASELINE_BYTES);

    g_hook_baselines[2].addr = hard_fail_fn;
    std::memcpy(g_hook_baselines[2].bytes, hard_fail_fn, BASELINE_BYTES);
}

bool verify_hook_baselines() {
    if (!g_baselines_captured.load()) return true;
    for (const auto& b : g_hook_baselines) {
        if (b.addr == nullptr) continue;
        if (CRYPTO_memcmp(b.bytes, b.addr, BASELINE_BYTES) != 0) {
            return false;
        }
    }
    return true;
}

void hard_fail(uint32_t reason) {
    hxs::policy::mark_tampered();
    __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, "integrity fail %u", reason);
    if (g_relaxed_for_testing.load()) {
        return;
    }
    _exit(1);
}

bool set_relaxed_for_testing(bool relaxed) {
    return g_relaxed_for_testing.exchange(relaxed);
}

bool is_relaxed_for_testing() {
    return g_relaxed_for_testing.load();
}

}
}
