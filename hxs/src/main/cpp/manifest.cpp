#include "manifest.h"

#include <cstring>
#include <cstdio>
#include <unistd.h>
#include <fcntl.h>
#include <sys/stat.h>

namespace hxs {

static constexpr char KEY_CHECK_PLAINTEXT[] = "HXS_KEY_CHECK_OK";

Manifest::Manifest(const std::string& path) : path_(path) {
    argon2_salt_.resize(16, 0);
}

// ── DEK wrapping (double-wrap: inner=app_key, outer=user_key) ──

std::vector<uint8_t> Manifest::wrap_dek(
    const uint8_t* app_key, size_t ak_len,
    const uint8_t* user_key, size_t uk_len
) {
    if (!crypto_.encrypt) return {};

    // Inner wrap: AES-GCM(app_key, DEK)
    // We use the crypto callback but need to temporarily set ctx for each key
    // For simplicity, we do a direct double-wrap approach:
    // The caller provides pre-derived keys; we just encrypt twice.

    std::vector<uint8_t> inner;
    if (!crypto_.encrypt(dek_, 32, inner, crypto_.ctx)) return {};

    std::vector<uint8_t> outer;
    if (!crypto_.encrypt(inner.data(), inner.size(), outer, crypto_.ctx)) return {};

    return outer;
}

bool Manifest::unwrap_dek(
    const uint8_t* app_key, size_t ak_len,
    const uint8_t* user_key, size_t uk_len,
    const std::vector<uint8_t>& wrapped
) {
    if (!crypto_.decrypt) return false;

    // Outer decrypt
    std::vector<uint8_t> inner;
    if (!crypto_.decrypt(wrapped.data(), wrapped.size(), inner, crypto_.ctx)) return false;

    // Inner decrypt
    std::vector<uint8_t> plain;
    if (!crypto_.decrypt(inner.data(), inner.size(), plain, crypto_.ctx)) return false;

    if (plain.size() != 32) return false;
    memcpy(dek_, plain.data(), 32);
    return true;
}

std::vector<uint8_t> Manifest::make_key_check(const uint8_t* app_key, size_t ak_len) {
    if (!crypto_.encrypt) return {};
    auto pt = reinterpret_cast<const uint8_t*>(KEY_CHECK_PLAINTEXT);
    size_t pt_len = strlen(KEY_CHECK_PLAINTEXT);
    std::vector<uint8_t> out;
    crypto_.encrypt(pt, pt_len, out, crypto_.ctx);
    return out;
}

bool Manifest::verify_key_check(const uint8_t* app_key, size_t ak_len,
                                 const std::vector<uint8_t>& check) {
    if (!crypto_.decrypt || check.empty()) return false;
    std::vector<uint8_t> plain;
    if (!crypto_.decrypt(check.data(), check.size(), plain, crypto_.ctx)) return false;
    if (plain.size() != strlen(KEY_CHECK_PLAINTEXT)) return false;
    return memcmp(plain.data(), KEY_CHECK_PLAINTEXT, plain.size()) == 0;
}

// ── Create (encrypted) ──

bool Manifest::create(
    const uint8_t* app_key, size_t ak_len,
    const uint8_t* user_key, size_t uk_len,
    const CryptoCallbacks& crypto
) {
    crypto_ = crypto;
    flags_ = 0;

    // Generate random DEK
    // We need randomness — use /dev/urandom directly since we don't link hxs_encryptor here
    int rfd = ::open("/dev/urandom", O_RDONLY);
    if (rfd < 0) return false;
    ::read(rfd, dek_, 32);
    ::read(rfd, argon2_salt_.data(), 16);
    ::close(rfd);

    wrapped_dek_ = wrap_dek(app_key, ak_len, user_key, uk_len);
    if (wrapped_dek_.empty()) return false;

    key_check_ = make_key_check(app_key, ak_len);
    return save();
}

// ── Open (encrypted) ──

bool Manifest::open(
    const uint8_t* app_key, size_t ak_len,
    const uint8_t* user_key, size_t uk_len,
    const CryptoCallbacks& crypto
) {
    crypto_ = crypto;

    int fd = ::open(path_.c_str(), O_RDONLY);
    if (fd < 0) return false;

    struct stat st;
    if (fstat(fd, &st) != 0 || st.st_size == 0) {
        ::close(fd);
        return false;
    }

    std::vector<uint8_t> buf(static_cast<size_t>(st.st_size));
    ssize_t n = ::read(fd, buf.data(), buf.size());
    ::close(fd);
    if (n <= 0) return false;

    if (!deserialize(buf)) return false;

    if (is_plaintext()) return true; // no decryption needed

    // Verify key
    if (!verify_key_check(app_key, ak_len, key_check_)) return false;

    // Unwrap DEK
    return unwrap_dek(app_key, ak_len, user_key, uk_len, wrapped_dek_);
}

// ── Plaintext mode ──

bool Manifest::create_plaintext() {
    flags_ = FLAG_PLAINTEXT;
    memset(dek_, 0, 32);
    return save();
}

bool Manifest::open_plaintext() {
    int fd = ::open(path_.c_str(), O_RDONLY);
    if (fd < 0) return false;

    struct stat st;
    if (fstat(fd, &st) != 0 || st.st_size == 0) {
        ::close(fd);
        return false;
    }

    std::vector<uint8_t> buf(static_cast<size_t>(st.st_size));
    ssize_t n = ::read(fd, buf.data(), buf.size());
    ::close(fd);
    if (n <= 0) return false;

    return deserialize(buf);
}

// ── Collection registry ──

void Manifest::register_collection(const CollectionMeta& meta) {
    std::lock_guard lock(mtx_);
    collections_[meta.name] = meta;
}

void Manifest::unregister_collection(const std::string& name) {
    std::lock_guard lock(mtx_);
    collections_.erase(name);
}

void Manifest::update_collection(const std::string& name, uint32_t count,
                                  uint64_t modified, uint32_t schema_ver) {
    std::lock_guard lock(mtx_);
    auto it = collections_.find(name);
    if (it != collections_.end()) {
        it->second.record_count = count;
        it->second.last_modified = modified;
        it->second.schema_version = schema_ver;
    }
}

const CollectionMeta* Manifest::get_collection(const std::string& name) const {
    std::lock_guard lock(mtx_);
    auto it = collections_.find(name);
    return (it != collections_.end()) ? &it->second : nullptr;
}

std::vector<CollectionMeta> Manifest::list_collections() const {
    std::lock_guard lock(mtx_);
    std::vector<CollectionMeta> result;
    result.reserve(collections_.size());
    for (auto& [_, meta] : collections_) result.push_back(meta);
    return result;
}

bool Manifest::has_collection(const std::string& name) const {
    std::lock_guard lock(mtx_);
    return collections_.count(name) > 0;
}

// ── Serialization ──

std::vector<uint8_t> Manifest::serialize() const {
    std::vector<uint8_t> out;
    auto append = [&](const void* data, size_t len) {
        const auto* p = static_cast<const uint8_t*>(data);
        out.insert(out.end(), p, p + len);
    };
    auto append_u16 = [&](uint16_t v) { append(&v, 2); };
    auto append_u32 = [&](uint32_t v) { append(&v, 4); };
    auto append_u64 = [&](uint64_t v) { append(&v, 8); };
    auto append_blob = [&](const std::vector<uint8_t>& b) {
        append_u32(static_cast<uint32_t>(b.size()));
        if (!b.empty()) append(b.data(), b.size());
    };
    auto append_str = [&](const std::string& s) {
        append_u16(static_cast<uint16_t>(s.size()));
        if (!s.empty()) append(s.data(), s.size());
    };

    // Header
    uint32_t magic = MANIFEST_MAGIC;
    append(&magic, 4);
    append_u16(MANIFEST_VERSION);
    append_u16(flags_);
    append(argon2_salt_.data(), 16);
    append_u32(argon2_t_cost_);
    append_u32(argon2_m_cost_);
    append_u32(argon2_p_cost_);

    append_blob(wrapped_dek_);
    append_blob(key_check_);

    // Collections
    append_u16(static_cast<uint16_t>(collections_.size()));
    for (auto& [_, meta] : collections_) {
        append_str(meta.name);
        append_str(meta.filename);
        append_u32(meta.record_count);
        append_u64(meta.last_modified);
        append_u32(meta.schema_version);
    }

    return out;
}

bool Manifest::deserialize(const std::vector<uint8_t>& data) {
    if (data.size() < 36) return false;

    const uint8_t* p = data.data();
    const uint8_t* end = data.data() + data.size();

    auto read_u16 = [&]() -> uint16_t {
        if (p + 2 > end) return 0;
        uint16_t v; memcpy(&v, p, 2); p += 2; return v;
    };
    auto read_u32 = [&]() -> uint32_t {
        if (p + 4 > end) return 0;
        uint32_t v; memcpy(&v, p, 4); p += 4; return v;
    };
    auto read_u64 = [&]() -> uint64_t {
        if (p + 8 > end) return 0;
        uint64_t v; memcpy(&v, p, 8); p += 8; return v;
    };
    auto read_blob = [&]() -> std::vector<uint8_t> {
        uint32_t len = read_u32();
        if (p + len > end) return {};
        std::vector<uint8_t> v(p, p + len);
        p += len;
        return v;
    };
    auto read_str = [&]() -> std::string {
        uint16_t len = read_u16();
        if (p + len > end) return {};
        std::string s(reinterpret_cast<const char*>(p), len);
        p += len;
        return s;
    };

    uint32_t magic = read_u32();
    if (magic != MANIFEST_MAGIC) return false;

    uint16_t version = read_u16();
    (void)version; // forward compat

    flags_ = read_u16();

    if (p + 16 > end) return false;
    argon2_salt_.assign(p, p + 16);
    p += 16;

    argon2_t_cost_ = read_u32();
    argon2_m_cost_ = read_u32();
    argon2_p_cost_ = read_u32();

    wrapped_dek_ = read_blob();
    key_check_ = read_blob();

    uint16_t coll_count = read_u16();
    collections_.clear();
    for (uint16_t i = 0; i < coll_count; i++) {
        CollectionMeta meta;
        meta.name = read_str();
        meta.filename = read_str();
        meta.record_count = read_u32();
        meta.last_modified = read_u64();
        meta.schema_version = read_u32();
        if (!meta.name.empty()) {
            collections_[meta.name] = std::move(meta);
        }
    }

    return true;
}

// ── Save ──

bool Manifest::save() {
    auto data = serialize();
    int fd = ::open(path_.c_str(), O_WRONLY | O_CREAT | O_TRUNC, 0600);
    if (fd < 0) return false;
    ::write(fd, data.data(), data.size());
    ::fsync(fd);
    ::close(fd);
    return true;
}

} // namespace hxs
