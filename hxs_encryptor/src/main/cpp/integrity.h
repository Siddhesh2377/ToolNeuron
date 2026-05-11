#pragma once

#include <cstdint>
#include <cstddef>
#include <string>
#include <vector>

namespace hxs {

struct IntegrityResult {
    bool debugger_detected;
    bool frida_detected;
    bool xposed_detected;
    bool signature_valid;    // true if APK sig matches expected
    bool libs_intact;        // true if .so hashes match expected
};

class IntegrityGuard {
public:
    // Check if a debugger is attached (ptrace + TracerPid)
    static bool is_debugger_attached();

    // Block debugger attachment via ptrace self-trace
    static bool block_debugger();

    // Scan /proc/self/maps for Frida agent libraries
    static bool is_frida_present();

    // Check for Xposed/LSPosed framework
    static bool is_xposed_present();

    // Verify APK signing certificate SHA-256
    // expected_hash: 32-byte SHA-256 of the signing cert
    // actual_hash: obtained from PackageManager on Kotlin side, passed in
    static bool verify_apk_signature(
        const uint8_t* expected_hash, size_t expected_len,
        const uint8_t* actual_hash, size_t actual_len
    );

    // Compute SHA-256 of a file (for .so / DEX integrity)
    static bool hash_file(const char* path, uint8_t* out_32);

    // Run all checks and return combined result
    static IntegrityResult run_all_checks(
        const uint8_t* expected_sig_hash,
        const uint8_t* actual_sig_hash
    );
};

} // namespace hxs
