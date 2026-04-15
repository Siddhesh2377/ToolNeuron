#include "manifest.h"

#include <android/log.h>
#include <cstring>

#define LOG_TAG "ums_manifest"
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

namespace ums {

// ---------------------------------------------------------------------------
// Key check plaintext — known constant encrypted with app_key to verify it
// ---------------------------------------------------------------------------

static const uint8_t KEY_CHECK_PLAINTEXT[16] = {
    'U','M','S','_','K','E','Y','_','C','H','E','C','K','_','O','K'
};

// ---------------------------------------------------------------------------
// Little-endian helpers
// ---------------------------------------------------------------------------

static void write_le16(uint8_t* out, uint16_t v) {
    out[0] = static_cast<uint8_t>(v);
    out[1] = static_cast<uint8_t>(v >> 8);
}

static void write_le32(uint8_t* out, uint32_t v) {
    out[0] = static_cast<uint8_t>(v);
    out[1] = static_cast<uint8_t>(v >> 8);
    out[2] = static_cast<uint8_t>(v >> 16);
    out[3] = static_cast<uint8_t>(v >> 24);
}

static void write_le64(uint8_t* out, uint64_t v) {
    for (int i = 0; i < 8; ++i) {
        out[i] = static_cast<uint8_t>(v >> (i * 8));
    }
}

static uint16_t read_le16(const uint8_t* p) {
    return static_cast<uint16_t>(p[0]) |
           (static_cast<uint16_t>(p[1]) << 8);
}

static uint32_t read_le32(const uint8_t* p) {
    return static_cast<uint32_t>(p[0]) |
           (static_cast<uint32_t>(p[1]) << 8) |
           (static_cast<uint32_t>(p[2]) << 16) |
           (static_cast<uint32_t>(p[3]) << 24);
}

static uint64_t read_le64(const uint8_t* p) {
    uint64_t v = 0;
    for (int i = 0; i < 8; ++i) {
        v |= static_cast<uint64_t>(p[i]) << (i * 8);
    }
    return v;
}

// ---------------------------------------------------------------------------
// Construction
// ---------------------------------------------------------------------------

Manifest::Manifest(fo::IOEngine& io, tn::CryptoEngine& crypto, std::string path)
    : io_(io), crypto_(crypto), path_(std::move(path)) {}

bool Manifest::exists() const {
    return io_.exists(path_);
}

const uint8_t* Manifest::dek() const {
    return dek_.data();
}

const std::unordered_map<std::string, CollectionMeta>& Manifest::collections() const {
    return collections_;
}

// ---------------------------------------------------------------------------
// Collection metadata management
// ---------------------------------------------------------------------------

void Manifest::register_collection(const CollectionMeta& meta) {
    collections_[meta.name] = meta;
}

void Manifest::update_collection(const std::string& name, uint32_t count, uint64_t modified) {
    auto it = collections_.find(name);
    if (it != collections_.end()) {
        it->second.record_count = count;
        it->second.last_modified = modified;
    }
}

// ---------------------------------------------------------------------------
// Key check — encrypt a known plaintext with app_key; verify on open
// ---------------------------------------------------------------------------

std::vector<uint8_t> Manifest::make_key_check(const uint8_t* app_key) {
    auto result = crypto_.encrypt_aes_gcm(
        KEY_CHECK_PLAINTEXT, sizeof(KEY_CHECK_PLAINTEXT),
        app_key, tn::AES_KEY_SIZE,
        nullptr, 0
    );
    if (!result.success) return {};
    return std::move(result.sealed_data);
}

bool Manifest::verify_key_check(const uint8_t* app_key, const std::vector<uint8_t>& check) {
    if (check.empty()) return false;

    auto result = crypto_.decrypt_aes_gcm(
        check.data(), check.size(),
        app_key, tn::AES_KEY_SIZE,
        nullptr, 0
    );
    if (!result.success) return false;
    if (result.plaintext.size() != sizeof(KEY_CHECK_PLAINTEXT)) return false;

    return tn::secure_compare(result.plaintext.data(),
                              KEY_CHECK_PLAINTEXT,
                              sizeof(KEY_CHECK_PLAINTEXT));
}

// ---------------------------------------------------------------------------
// DEK wrapping — double-wrap: AES-GCM(AppKey, AES-GCM(UserKey, DEK))
// ---------------------------------------------------------------------------

std::vector<uint8_t> Manifest::wrap_dek(const uint8_t* app_key, const uint8_t* user_key) {
    // Inner wrap: AES-GCM(UserKey, DEK)
    auto inner = crypto_.encrypt_aes_gcm(
        dek_.data(), DEK_SIZE,
        user_key, tn::AES_KEY_SIZE,
        nullptr, 0
    );
    if (!inner.success) {
        LOGE("wrap_dek: inner wrap failed");
        return {};
    }

    // Outer wrap: AES-GCM(AppKey, inner_sealed)
    auto outer = crypto_.encrypt_aes_gcm(
        inner.sealed_data.data(), inner.sealed_data.size(),
        app_key, tn::AES_KEY_SIZE,
        nullptr, 0
    );
    if (!outer.success) {
        LOGE("wrap_dek: outer wrap failed");
        return {};
    }

    return std::move(outer.sealed_data);
}

bool Manifest::unwrap_dek(const uint8_t* app_key, const uint8_t* user_key,
                          const std::vector<uint8_t>& wrapped) {
    // Outer unwrap: decrypt with AppKey
    auto outer = crypto_.decrypt_aes_gcm(
        wrapped.data(), wrapped.size(),
        app_key, tn::AES_KEY_SIZE,
        nullptr, 0
    );
    if (!outer.success) {
        LOGE("unwrap_dek: outer unwrap failed (bad app_key?)");
        return false;
    }

    // Inner unwrap: decrypt with UserKey
    auto inner = crypto_.decrypt_aes_gcm(
        outer.plaintext.data(), outer.plaintext.size(),
        user_key, tn::AES_KEY_SIZE,
        nullptr, 0
    );
    if (!inner.success) {
        LOGE("unwrap_dek: inner unwrap failed (bad user_key?)");
        return false;
    }

    if (inner.plaintext.size() != DEK_SIZE) {
        LOGE("unwrap_dek: unexpected DEK size %zu", inner.plaintext.size());
        return false;
    }

    std::memcpy(dek_.data(), inner.plaintext.data(), DEK_SIZE);
    return true;
}

// ---------------------------------------------------------------------------
// create_plaintext() — create manifest with FLAG_PLAINTEXT_MODE, no DEK
// ---------------------------------------------------------------------------

bool Manifest::create_plaintext() {
    flags_ |= FLAG_PLAINTEXT_MODE;

    // No DEK, no wrapped_dek, no key_check, no PBKDF2 salt needed
    wrapped_dek_.clear();
    key_check_.clear();
    pbkdf2_salt_.resize(PBKDF2_SALT_SIZE, 0); // zeroed salt (serialization expects fixed size)
    pbkdf2_iterations_ = 0;

    // DEK buffer stays zeroed (initialized by SecureBuffer constructor)
    collections_.clear();

    return save();
}

// ---------------------------------------------------------------------------
// open_plaintext() — read manifest, verify FLAG_PLAINTEXT_MODE, skip key ops
// ---------------------------------------------------------------------------

bool Manifest::open_plaintext() {
    auto result = io_.read(path_);
    if (!result.success) {
        LOGE("open_plaintext: failed to read manifest file");
        return false;
    }

    if (!deserialize(result.data)) {
        LOGE("open_plaintext: failed to deserialize manifest");
        return false;
    }

    if (!is_plaintext()) {
        LOGE("open_plaintext: manifest does not have FLAG_PLAINTEXT_MODE set");
        return false;
    }

    // Skip verify_key_check and unwrap_dek entirely.
    // DEK stays zeroed — it won't be used.
    return true;
}

// ---------------------------------------------------------------------------
// is_plaintext() — check if FLAG_PLAINTEXT_MODE bit is set
// ---------------------------------------------------------------------------

bool Manifest::is_plaintext() const {
    return (flags_ & FLAG_PLAINTEXT_MODE) != 0;
}

// ---------------------------------------------------------------------------
// create() — generate new manifest with random DEK
// ---------------------------------------------------------------------------

bool Manifest::create(const uint8_t* app_key, const uint8_t* user_key) {
    // Generate random DEK
    if (!crypto_.random_bytes(dek_.data(), DEK_SIZE)) {
        LOGE("create: failed to generate random DEK");
        return false;
    }

    // Generate random PBKDF2 salt
    pbkdf2_salt_.resize(PBKDF2_SALT_SIZE);
    if (!crypto_.random_bytes(pbkdf2_salt_.data(), PBKDF2_SALT_SIZE)) {
        LOGE("create: failed to generate PBKDF2 salt");
        return false;
    }

    pbkdf2_iterations_ = DEFAULT_PBKDF2_ITERATIONS;

    // Wrap DEK
    wrapped_dek_ = wrap_dek(app_key, user_key);
    if (wrapped_dek_.empty()) {
        LOGE("create: failed to wrap DEK");
        return false;
    }

    // Make key check
    key_check_ = make_key_check(app_key);
    if (key_check_.empty()) {
        LOGE("create: failed to make key check");
        return false;
    }

    collections_.clear();
    flags_ = 0;

    return save();
}

// ---------------------------------------------------------------------------
// open() — read and verify manifest, unwrap DEK
// ---------------------------------------------------------------------------

bool Manifest::open(const uint8_t* app_key, const uint8_t* user_key) {
    auto result = io_.read(path_);
    if (!result.success) {
        LOGE("open: failed to read manifest file");
        return false;
    }

    if (!deserialize(result.data)) {
        LOGE("open: failed to deserialize manifest");
        return false;
    }

    // Verify key check
    if (!verify_key_check(app_key, key_check_)) {
        LOGE("open: key check failed (wrong app_key)");
        return false;
    }

    // Unwrap DEK
    if (!unwrap_dek(app_key, user_key, wrapped_dek_)) {
        LOGE("open: failed to unwrap DEK");
        return false;
    }

    return true;
}

// ---------------------------------------------------------------------------
// create_with_passphrase() — PBKDF2 done internally, salt stored in manifest
// ---------------------------------------------------------------------------

bool Manifest::create_with_passphrase(const uint8_t* app_key,
                                       const uint8_t* passphrase, size_t pass_len) {
    // Generate random DEK
    if (!crypto_.random_bytes(dek_.data(), DEK_SIZE)) {
        LOGE("create_with_passphrase: failed to generate random DEK");
        return false;
    }

    // Generate random PBKDF2 salt
    pbkdf2_salt_.resize(PBKDF2_SALT_SIZE);
    if (!crypto_.random_bytes(pbkdf2_salt_.data(), PBKDF2_SALT_SIZE)) {
        LOGE("create_with_passphrase: failed to generate PBKDF2 salt");
        return false;
    }

    pbkdf2_iterations_ = DEFAULT_PBKDF2_ITERATIONS;

    // Derive userKey from passphrase — uses the SAME salt stored in manifest
    tn::SecureBuffer user_key(tn::AES_KEY_SIZE);
    if (!crypto_.pbkdf2_sha256(passphrase, pass_len,
                                pbkdf2_salt_.data(), pbkdf2_salt_.size(),
                                pbkdf2_iterations_,
                                user_key.data(), tn::AES_KEY_SIZE)) {
        LOGE("create_with_passphrase: PBKDF2 derivation failed");
        return false;
    }

    // Wrap DEK
    wrapped_dek_ = wrap_dek(app_key, user_key.data());
    if (wrapped_dek_.empty()) {
        LOGE("create_with_passphrase: failed to wrap DEK");
        return false;
    }

    // Make key check
    key_check_ = make_key_check(app_key);
    if (key_check_.empty()) {
        LOGE("create_with_passphrase: failed to make key check");
        return false;
    }

    collections_.clear();
    flags_ = 0;

    // user_key wiped by SecureBuffer destructor
    return save();
}

// ---------------------------------------------------------------------------
// open_with_passphrase() — read salt from manifest, derive key, unwrap DEK
// ---------------------------------------------------------------------------

bool Manifest::open_with_passphrase(const uint8_t* app_key,
                                     const uint8_t* passphrase, size_t pass_len) {
    auto result = io_.read(path_);
    if (!result.success) {
        LOGE("open_with_passphrase: failed to read manifest file");
        return false;
    }

    if (!deserialize(result.data)) {
        LOGE("open_with_passphrase: failed to deserialize manifest");
        return false;
    }

    // Verify key check
    if (!verify_key_check(app_key, key_check_)) {
        LOGE("open_with_passphrase: key check failed (wrong app_key)");
        return false;
    }

    // Derive userKey from passphrase using salt/iterations from manifest
    tn::SecureBuffer user_key(tn::AES_KEY_SIZE);
    if (!crypto_.pbkdf2_sha256(passphrase, pass_len,
                                pbkdf2_salt_.data(), pbkdf2_salt_.size(),
                                pbkdf2_iterations_,
                                user_key.data(), tn::AES_KEY_SIZE)) {
        LOGE("open_with_passphrase: PBKDF2 derivation failed");
        return false;
    }

    // Unwrap DEK
    if (!unwrap_dek(app_key, user_key.data(), wrapped_dek_)) {
        LOGE("open_with_passphrase: failed to unwrap DEK (wrong passphrase?)");
        return false;
    }

    // user_key wiped by SecureBuffer destructor
    return true;
}

// ---------------------------------------------------------------------------
// change_passphrase() — re-wrap DEK with new user key
// ---------------------------------------------------------------------------

bool Manifest::change_passphrase(const uint8_t* app_key, const uint8_t* old_user_key,
                                 const uint8_t* new_user_key) {
    // First unwrap with old keys to ensure they're valid
    if (!unwrap_dek(app_key, old_user_key, wrapped_dek_)) {
        LOGE("change_passphrase: unwrap with old keys failed");
        return false;
    }

    // Re-wrap with new user key
    wrapped_dek_ = wrap_dek(app_key, new_user_key);
    if (wrapped_dek_.empty()) {
        LOGE("change_passphrase: re-wrap failed");
        return false;
    }

    return save();
}

uint16_t Manifest::flags() const { return flags_; }
void Manifest::set_flags(uint16_t f) { flags_ = f; }

// ---------------------------------------------------------------------------
// save() — serialize and write manifest to disk
// ---------------------------------------------------------------------------

bool Manifest::save() {
    auto data = serialize();
    if (data.empty()) {
        LOGE("save: serialize returned empty");
        return false;
    }

    auto wr = io_.write(path_, data.data(), data.size());
    if (!wr.success) {
        LOGE("save: io_.write failed for %s", path_.c_str());
        return false;
    }

    return true;
}

// ---------------------------------------------------------------------------
// serialize() — build binary manifest
//
// Format:
//   magic(4) + format_version(2) + flags(2) + pbkdf2_salt(16) + pbkdf2_iterations(4)
//   wrapped_dek_blob: len(4) + data
//   key_check: len(4) + data
//   collection_count(2), then per collection:
//     name_len(2) + name + filename_len(2) + filename + record_count(4) + last_modified(8)
// ---------------------------------------------------------------------------

std::vector<uint8_t> Manifest::serialize() {
    // Calculate total size
    size_t total = 4 + 2 + 2 + PBKDF2_SALT_SIZE + 4; // header
    total += 4 + wrapped_dek_.size();                   // wrapped DEK blob
    total += 4 + key_check_.size();                     // key check
    total += 2;                                         // collection_count

    for (const auto& [name, meta] : collections_) {
        total += 2 + meta.name.size();     // name_len + name
        total += 2 + meta.filename.size(); // filename_len + filename
        total += 4 + 8;                    // record_count + last_modified
    }

    std::vector<uint8_t> buf(total);
    uint8_t* p = buf.data();

    // Header
    write_le32(p, MANIFEST_MAGIC); p += 4;
    write_le16(p, FORMAT_VERSION); p += 2;
    write_le16(p, flags_); p += 2;

    // PBKDF2 salt
    if (pbkdf2_salt_.size() == PBKDF2_SALT_SIZE) {
        std::memcpy(p, pbkdf2_salt_.data(), PBKDF2_SALT_SIZE);
    }
    p += PBKDF2_SALT_SIZE;

    // PBKDF2 iterations
    write_le32(p, pbkdf2_iterations_); p += 4;

    // Wrapped DEK blob
    write_le32(p, static_cast<uint32_t>(wrapped_dek_.size())); p += 4;
    if (!wrapped_dek_.empty()) {
        std::memcpy(p, wrapped_dek_.data(), wrapped_dek_.size());
        p += wrapped_dek_.size();
    }

    // Key check
    write_le32(p, static_cast<uint32_t>(key_check_.size())); p += 4;
    if (!key_check_.empty()) {
        std::memcpy(p, key_check_.data(), key_check_.size());
        p += key_check_.size();
    }

    // Collections
    write_le16(p, static_cast<uint16_t>(collections_.size())); p += 2;

    for (const auto& [name, meta] : collections_) {
        write_le16(p, static_cast<uint16_t>(meta.name.size())); p += 2;
        std::memcpy(p, meta.name.data(), meta.name.size()); p += meta.name.size();

        write_le16(p, static_cast<uint16_t>(meta.filename.size())); p += 2;
        std::memcpy(p, meta.filename.data(), meta.filename.size()); p += meta.filename.size();

        write_le32(p, meta.record_count); p += 4;
        write_le64(p, meta.last_modified); p += 8;
    }

    return buf;
}

// ---------------------------------------------------------------------------
// deserialize() — parse binary manifest
// ---------------------------------------------------------------------------

bool Manifest::deserialize(const std::vector<uint8_t>& data) {
    // Minimum size: header(4+2+2) + salt(16) + iterations(4) + wrapped_len(4) + check_len(4) + coll_count(2) = 38
    if (data.size() < 38) {
        LOGE("deserialize: data too small (%zu bytes)", data.size());
        return false;
    }

    const uint8_t* p = data.data();
    const uint8_t* end = data.data() + data.size();

    // Magic
    uint32_t magic = read_le32(p); p += 4;
    if (magic != MANIFEST_MAGIC) {
        LOGE("deserialize: bad magic 0x%08X (expected 0x%08X)", magic, MANIFEST_MAGIC);
        return false;
    }

    // Version
    uint16_t version = read_le16(p); p += 2;
    if (version != FORMAT_VERSION) {
        LOGE("deserialize: unsupported version %u", version);
        return false;
    }

    // Flags
    flags_ = read_le16(p); p += 2;

    // PBKDF2 salt
    if (p + PBKDF2_SALT_SIZE > end) return false;
    pbkdf2_salt_.assign(p, p + PBKDF2_SALT_SIZE);
    p += PBKDF2_SALT_SIZE;

    // PBKDF2 iterations
    if (p + 4 > end) return false;
    pbkdf2_iterations_ = read_le32(p); p += 4;

    // Wrapped DEK blob
    if (p + 4 > end) return false;
    uint32_t wrapped_len = read_le32(p); p += 4;
    if (p + wrapped_len > end) return false;
    wrapped_dek_.assign(p, p + wrapped_len);
    p += wrapped_len;

    // Key check
    if (p + 4 > end) return false;
    uint32_t check_len = read_le32(p); p += 4;
    if (p + check_len > end) return false;
    key_check_.assign(p, p + check_len);
    p += check_len;

    // Collections
    if (p + 2 > end) return false;
    uint16_t coll_count = read_le16(p); p += 2;

    collections_.clear();
    for (uint16_t i = 0; i < coll_count; ++i) {
        CollectionMeta meta{};

        // name
        if (p + 2 > end) return false;
        uint16_t name_len = read_le16(p); p += 2;
        if (p + name_len > end) return false;
        meta.name.assign(reinterpret_cast<const char*>(p), name_len);
        p += name_len;

        // filename
        if (p + 2 > end) return false;
        uint16_t fname_len = read_le16(p); p += 2;
        if (p + fname_len > end) return false;
        meta.filename.assign(reinterpret_cast<const char*>(p), fname_len);
        p += fname_len;

        // record_count + last_modified
        if (p + 12 > end) return false;
        meta.record_count = read_le32(p); p += 4;
        meta.last_modified = read_le64(p); p += 8;

        collections_[meta.name] = std::move(meta);
    }

    return true;
}

} // namespace ums
