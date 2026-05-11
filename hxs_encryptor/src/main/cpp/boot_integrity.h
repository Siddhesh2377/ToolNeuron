#pragma once

#include <cstdint>
#include <cstddef>

namespace hxs {
namespace boot {

enum FailureReason : uint32_t {
    FAIL_NONE          = 0,
    FAIL_DEBUGGER      = 1u << 0,
    FAIL_FRIDA         = 1u << 1,
    FAIL_XPOSED        = 1u << 2,
    FAIL_PTRACE_DENIED = 1u << 3,
    FAIL_LIB_HASH      = 1u << 4,
    FAIL_BAD_INPUT     = 1u << 5,
    FAIL_INLINE_HOOK   = 1u << 6,
};

struct LibCheck {
    const char* path;
    const uint8_t* expected_hash_32;
};

void install_ptrace_self_trace();

uint32_t scan_process_environment();

uint32_t verify_lib_hashes(const LibCheck* libs, size_t count);

uint32_t boot_verify(const LibCheck* libs, size_t count);

void capture_hook_baselines();

bool verify_hook_baselines();

void hard_fail(uint32_t reason);

bool set_relaxed_for_testing(bool relaxed);

bool is_relaxed_for_testing();

}
}
