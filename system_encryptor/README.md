# system_encryptor

Native C++ encryption module for ToolNeuron. Provides AES-256-GCM encryption, Ed25519 signing/verification, X25519 key exchange, HKDF key derivation, and license verification — all backed by BoringSSL and exposed to Kotlin via JNI.

## Prerequisites

- Android NDK (installed via Android Studio SDK Manager)
- CMake 3.22.1+ (installed via Android Studio SDK Manager)
- Git (for BoringSSL FetchContent download)
- No Go installation required (`-DOPENSSL_NO_ASM=1` is set)

## Setup

### 1. Generate your signing keypair

Each build needs a unique Ed25519 keypair to differentiate it from cloned copies. Generate one using Python:

```python
from cryptography.hazmat.primitives.asymmetric.ed25519 import Ed25519PrivateKey
import secrets

private_key = Ed25519PrivateKey.generate()
seed = private_key.private_bytes_raw().hex()
public_key = private_key.public_key().public_bytes_raw().hex()
content_key = secrets.token_bytes(32).hex()

print(f"tn.signing.private_seed={seed}")
print(f"tn.signing.public_key={public_key}")
print(f"tn.content.encryption_key={content_key}")
```

Install the dependency: `pip install cryptography`

### 2. Add keys to `local.properties`

Add these lines to the project-root `local.properties` (this file is gitignored):

```properties
tn.signing.private_seed=<64-char hex>
tn.signing.public_key=<64-char hex>
tn.content.encryption_key=<64-char hex>
```

| Key | Required | Purpose |
|-----|----------|---------|
| `tn.signing.public_key` | **Yes** | Embedded into native binary at compile time. Used for Ed25519 signature verification. Build fails without it. |
| `tn.signing.private_seed` | For signing | Used at signing time (build server / content pipeline) to sign licenses, themes, and plugins. |
| `tn.content.encryption_key` | For encryption | AES-256 key for encrypting/decrypting content files (themes, configs, etc.). |

### 3. Build

The module builds automatically as part of the Gradle build:

```bash
./gradlew :system_encryptor:assembleDebug
```

On first build, CMake will download BoringSSL via `FetchContent` (requires internet). Subsequent builds use the cached copy in `.cxx/`.

## Architecture

```
system_encryptor/
├── build.gradle.kts                  # Reads public key from local.properties, passes to CMake
├── src/main/
│   ├── java/.../SystemEncryptor.kt   # Kotlin API (JNI facade)
│   └── cpp/
│       ├── CMakeLists.txt            # Build config, BoringSSL FetchContent
│       ├── system_encryptor.cpp      # JNI bridge (extern "C" functions)
│       ├── crypto_engine.h/cpp       # Core crypto: AES-GCM, Ed25519, X25519, HKDF
│       ├── memory_guard.h/cpp        # SecureBuffer (mmap/mlock/cleanse), secure_zero
│       ├── key_store.h/cpp           # Embedded public key (compile-time define)
│       └── license_verifier.h/cpp    # License blob verification
```

### Key flow: `local.properties` → binary

```
local.properties
  tn.signing.public_key=<hex>
    → build.gradle.kts reads it via readLines()
      → passes -DTN_SIGNING_PUBLIC_KEY=<hex> to CMake
        → CMakeLists.txt sets target_compile_definitions
          → key_store.cpp uses #define, parses hex at runtime
            → get_embedded_public_key() returns 32-byte key
```

### Components

| File | Purpose |
|------|---------|
| `crypto_engine` | Stateless crypto operations. AES-256-GCM encrypt/decrypt with random nonce, Ed25519 sign/verify, X25519 shared secret, HKDF-SHA256, CSPRNG. |
| `memory_guard` | `SecureBuffer` — allocated via `mmap` with `mlock` to prevent swapping. Wiped with `OPENSSL_cleanse` on destruction. Also provides `secure_zero()` and `secure_compare()`. |
| `key_store` | Embeds the Ed25519 public key at compile time. Uses `std::call_once` for thread-safe initialization. Key is parsed from hex on first access. |
| `license_verifier` | Verifies license blobs: extracts Ed25519 signature (first 64 bytes), verifies against embedded public key. Returns `LicenseStatus` enum. |
| `system_encryptor` | JNI bridge. Converts between `jbyteArray`/JNI types and C++ using `JniBytes` RAII helper. |

## Kotlin API

```kotlin
val encryptor = SystemEncryptor()

// Encrypt/decrypt (AES-256-GCM, 32-byte key)
val sealed: ByteArray = encryptor.encryptData(plaintext, key32)
val plain: ByteArray = encryptor.decryptData(sealed, key32)

// Sign content (Ed25519, 64-byte private key)
val signature: ByteArray = encryptor.signContent(data, privateKey64)

// Verify signature (uses embedded public key)
val valid: Boolean = encryptor.verifySignature(data, signature)

// Verify license blob
val result: LicenseResult = encryptor.verifyLicense(licenseBlob)
// VALID | INVALID_SIGNATURE | EXPIRED | MALFORMED | WRONG_DEVICE

// Derive a context-specific key (HKDF-SHA256)
val themeKey: ByteArray = encryptor.deriveKey(masterKey, "theme_encryption")

// Securely wipe sensitive data from memory
encryptor.secureWipe(sensitiveBytes)
```

The native library loads automatically via `System.loadLibrary("system_encryptor")` in the companion object.

## Adding New Features

### Adding a new crypto operation

**1. Declare in `crypto_engine.h`:**

```cpp
bool my_new_operation(const uint8_t* input, size_t len, uint8_t* output);
```

**2. Implement in `crypto_engine.cpp`** using BoringSSL APIs:

```cpp
bool CryptoEngine::my_new_operation(const uint8_t* input, size_t len, uint8_t* output) {
    // BoringSSL calls here
    return true;
}
```

**3. Add JNI bridge in `system_encryptor.cpp`:**

```cpp
extern "C" {
JNIEXPORT jbyteArray JNICALL
Java_com_dark_system_1encryptor_SystemEncryptor_nativeMyNewOp(
    JNIEnv* env, jobject, jbyteArray input
) {
    JniBytes in(env, input);
    if (!in.ptr) return nullptr;

    // Call g_engine.my_new_operation(...)
    // Return result via to_jbyteArray(env, data, len)
}
}
```

**4. Add Kotlin binding in `SystemEncryptor.kt`:**

```kotlin
external fun nativeMyNewOp(input: ByteArray): ByteArray?

fun myNewOp(input: ByteArray): ByteArray {
    return nativeMyNewOp(input)
        ?: throw SecurityException("Operation failed")
}
```

### Adding encrypted storage for a new data type

To encrypt a new type of content (plugin files, user settings, etc.):

**1. Define a unique context string** for HKDF key derivation (domain separation):

```kotlin
val pluginKey = encryptor.deriveKey(masterKey, "plugin_encryption_v1")
```

**2. Encrypt before writing to disk:**

```kotlin
val json = Json.encodeToString(myData)
val encrypted = encryptor.encryptData(json.toByteArray(), pluginKey)
File("data.enc").writeBytes(encrypted)
```

**3. Decrypt after reading:**

```kotlin
val encrypted = File("data.enc").readBytes()
val json = encryptor.decryptData(encrypted, pluginKey).decodeToString()
val myData = Json.decodeFromString<MyDataType>(json)
```

**4. Wipe keys when done:**

```kotlin
encryptor.secureWipe(pluginKey)
```

### Adding signed content verification

For content that must be verified as authentic (themes, licenses, configs):

**1. Sign during your build/content pipeline** (server-side, using the private key):

```kotlin
val signature = encryptor.signContent(contentBytes, privateKey)
val signedBlob = signature + contentBytes  // 64-byte sig prefix
```

**2. Verify on device** (uses embedded public key, no private key needed):

```kotlin
val signature = blob.sliceArray(0 until 64)
val content = blob.sliceArray(64 until blob.size)
if (encryptor.verifySignature(content, signature)) {
    // Content is authentic
}
```

## Crypto Specifications

| Algorithm | Purpose | Key Size | Notes |
|-----------|---------|----------|-------|
| AES-256-GCM | Symmetric encryption | 32 bytes | Random 12-byte nonce prepended, 16-byte auth tag appended |
| Ed25519 | Digital signatures | 32 pub / 64 priv | Content signing and license verification |
| X25519 | Key exchange | 32 bytes | Compute shared secret from two keypairs |
| HKDF-SHA256 | Key derivation | Variable | Derive domain-specific keys from a master key |
| CSPRNG | Random bytes | — | BoringSSL `RAND_bytes` |

## Sealed data format (AES-256-GCM)

```
[12-byte nonce][ciphertext][16-byte GCM tag]
```

The nonce is randomly generated per encryption call and prepended to the output. The tag is appended by BoringSSL's AEAD seal operation.

## Security Notes

- The public key is compiled into the native `.so` binary via C preprocessor define — not stored as a separate asset.
- `SecureBuffer` uses `mmap`/`mlock` to prevent sensitive data from being swapped to disk, and `OPENSSL_cleanse` to wipe on free.
- All JNI byte array access uses the RAII `JniBytes` helper to prevent leaks.
- Native symbols are hidden (`-fvisibility=hidden`) — only `JNIEXPORT` functions are exported.
- BoringSSL is statically linked, no system OpenSSL dependency.
- Private keys and encryption keys in `local.properties` are gitignored — never commit them.

## Troubleshooting

| Problem | Solution |
|---------|----------|
| `FATAL_ERROR: TN_SIGNING_PUBLIC_KEY is not set` | Add `tn.signing.public_key=<hex>` to `local.properties` |
| BoringSSL download fails | Check internet. FetchContent clones from `boringssl.googlesource.com` |
| `Go not found` from BoringSSL | Already handled — `-DOPENSSL_NO_ASM=1` skips Go-dependent assembly |
| Stale CMake cache | Delete `system_encryptor/.cxx/` and rebuild |
| IDE red squiggles in C++ | Normal — clangd doesn't resolve BoringSSL includes. NDK build works fine. |
