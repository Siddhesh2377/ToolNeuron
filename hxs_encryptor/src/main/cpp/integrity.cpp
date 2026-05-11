#include "integrity.h"
#include "xor_str.h"

#include <openssl/sha.h>
#include <openssl/crypto.h>

#include <cstdio>
#include <cstring>
#include <cstdlib>
#include <sys/ptrace.h>
#include <unistd.h>
#include <fcntl.h>
#include <dirent.h>

namespace hxs {

bool IntegrityGuard::is_debugger_attached() {
    HXS_OBF(status_path, "/proc/self/status");
    HXS_OBF(tracer_pid, "TracerPid:");

    FILE* f = fopen(status_path, "r");
    if (!f) return false;

    char line[256];
    bool detected = false;
    while (fgets(line, sizeof(line), f)) {
        if (strstr(line, tracer_pid) == line) {
            int pid = atoi(line + strlen(tracer_pid));
            detected = pid != 0;
            break;
        }
    }
    fclose(f);
    return detected;
}

bool IntegrityGuard::block_debugger() {
    long ret = ptrace(PTRACE_TRACEME, 0, nullptr, nullptr);
    return ret != -1;
}

bool IntegrityGuard::is_frida_present() {
    HXS_OBF(maps_path, "/proc/self/maps");
    HXS_OBF(tcp_path, "/proc/net/tcp");
    HXS_OBF(fd_dir, "/proc/self/fd");
    HXS_OBF(pat_frida, "frida");
    HXS_OBF(pat_gadget, "gadget");
    HXS_OBF(pat_linjector, "linjector");
    HXS_OBF(port_upper, "69A2");
    HXS_OBF(port_lower, "69a2");

    FILE* f = fopen(maps_path, "r");
    if (f) {
        char line[512];
        bool found = false;
        while (fgets(line, sizeof(line), f)) {
            if (strstr(line, pat_frida) ||
                strstr(line, pat_gadget) ||
                strstr(line, pat_linjector)) {
                found = true;
                break;
            }
        }
        fclose(f);
        if (found) return true;
    }

    f = fopen(tcp_path, "r");
    if (f) {
        char line[512];
        bool found = false;
        while (fgets(line, sizeof(line), f)) {
            if (strstr(line, port_upper) || strstr(line, port_lower)) {
                found = true;
                break;
            }
        }
        fclose(f);
        if (found) return true;
    }

    DIR* dir = opendir(fd_dir);
    if (!dir) return false;

    bool found = false;
    struct dirent* entry;
    while ((entry = readdir(dir)) != nullptr) {
        char fd_path[128];
        char link_target[256];
        snprintf(fd_path, sizeof(fd_path), "%s/%s", fd_dir, entry->d_name);
        ssize_t len = readlink(fd_path, link_target, sizeof(link_target) - 1);
        if (len > 0) {
            link_target[len] = '\0';
            if (strstr(link_target, pat_frida) || strstr(link_target, pat_linjector)) {
                found = true;
                break;
            }
        }
    }
    closedir(dir);

    return found;
}

bool IntegrityGuard::is_xposed_present() {
    HXS_OBF(maps_path, "/proc/self/maps");
    HXS_OBF(p_xposed_lc, "xposed");
    HXS_OBF(p_xposed_uc, "XposedBridge");
    HXS_OBF(p_lspd, "lspd");
    HXS_OBF(p_lsposed, "LSPosed");
    HXS_OBF(p_edxp, "edxp");
    HXS_OBF(p_riru, "riru");

    FILE* f = fopen(maps_path, "r");
    if (!f) return false;

    char line[512];
    bool found = false;
    while (fgets(line, sizeof(line), f)) {
        if (strstr(line, p_xposed_lc) ||
            strstr(line, p_xposed_uc) ||
            strstr(line, p_lspd) ||
            strstr(line, p_lsposed) ||
            strstr(line, p_edxp) ||
            strstr(line, p_riru)) {
            found = true;
            break;
        }
    }
    fclose(f);
    return found;
}

bool IntegrityGuard::verify_apk_signature(
    const uint8_t* expected_hash, size_t expected_len,
    const uint8_t* actual_hash, size_t actual_len
) {
    if (expected_len != 32 || actual_len != 32) return false;
    return CRYPTO_memcmp(expected_hash, actual_hash, 32) == 0;
}

bool IntegrityGuard::hash_file(const char* path, uint8_t* out_32) {
    FILE* f = fopen(path, "rb");
    if (!f) return false;

    SHA256_CTX ctx;
    SHA256_Init(&ctx);

    uint8_t buf[8192];
    size_t n;
    while ((n = fread(buf, 1, sizeof(buf), f)) > 0) {
        SHA256_Update(&ctx, buf, n);
    }

    fclose(f);
    SHA256_Final(out_32, &ctx);
    return true;
}

IntegrityResult IntegrityGuard::run_all_checks(
    const uint8_t* expected_sig_hash,
    const uint8_t* actual_sig_hash
) {
    IntegrityResult result{};
    result.debugger_detected = is_debugger_attached();
    result.frida_detected = is_frida_present();
    result.xposed_detected = is_xposed_present();
    result.signature_valid = (expected_sig_hash && actual_sig_hash)
        ? verify_apk_signature(expected_sig_hash, 32, actual_sig_hash, 32)
        : false;
    result.libs_intact = true;
    return result;
}

}
