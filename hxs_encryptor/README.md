# HXS Encryptor

Cryptographic engine for the HXS (Hex-Storage) system. Native C++17 with Kotlin JNI bindings.

Built on **BoringSSL** (Google's OpenSSL fork) and **liboqs** (Open Quantum Safe). No Java-level crypto — everything runs in native code with hardware acceleration where available.

---

## Architecture

```
┌─────────────────────────────────────────────┐
│              HxsEncryptor.kt                │
│           (Kotlin public API)               │
├─────────────────────────────────────────────┤
│            hxs_encryptor.cpp                │
│             (JNI bridge)                    │
├──────┬──────┬───────┬──────────┬────────────┤
│Crypto│Argon2│  PQ   │Integrity │ Memory     │
│Engine│  id  │KEM/Sig│  Guard   │ Guard      │
├──────┴──────┴───────┴──────────┴────────────┤
│         BoringSSL        │      liboqs      │
│    (AES, ChaCha, Ed25519,│  (ML-KEM-768,   │
│     X25519, HKDF, SHA)   │   ML-DSA-65)    │
└──────────────────────────┴──────────────────┘
```

---

## Capabilities

| Category | Algorithm | Purpose |
|---|---|---|
| Symmetric encryption | AES-256-GCM | Authenticated encryption (hardware-accelerated on ARMv8) |
| Symmetric encryption | ChaCha20-Poly1305 | Authenticated encryption (fast on devices without AES instructions) |
| Key derivation | Argon2id | Memory-hard password hashing (GPU/ASIC resistant) |
| Key derivation | PBKDF2-SHA256 | Fallback password hashing (600k iterations default) |
| Key derivation | HKDF-SHA256 | Deterministic key expansion from master keys |
| Key exchange | X25519 | Classical elliptic curve Diffie-Hellman |
| Key exchange | ML-KEM-768 (Kyber) | Post-quantum key encapsulation (NIST standard) |
| Signatures | Ed25519 | Classical Edwards curve signatures |
| Signatures | ML-DSA-65 (Dilithium) | Post-quantum digital signatures (NIST standard) |
| Hashing | SHA-256 | General-purpose cryptographic hash |
| CSPRNG | BoringSSL RAND_bytes | Cryptographically secure random number generation |
| Anti-tamper | ptrace / proc inspection | Debugger, Frida, Xposed detection |
| Anti-tamper | APK signature verification | Repackaging detection via cert hash comparison |
| Anti-tamper | File integrity hashing | SHA-256 of .so / .dex files at runtime |

---

## Auto Cipher Selection

The engine detects hardware AES support at runtime and selects the optimal cipher automatically:

```
ARMv8 with AES instructions  →  AES-256-GCM         (hardware-accelerated)
ARMv8 without AES / ARMv7    →  ChaCha20-Poly1305   (faster in software)
x86_64 (emulator)            →  AES-256-GCM         (AES-NI assumed)
```

Sealed data format includes a 1-byte cipher identifier, so data encrypted with either cipher is automatically decrypted with the correct algorithm:

```
Sealed format: [cipher_id : 1 byte][nonce : 12 bytes][ciphertext + auth_tag : N + 16 bytes]

cipher_id 0x00 = AES-256-GCM
cipher_id 0x01 = ChaCha20-Poly1305
```

This means a device with AES hardware can still read data written by a device without it, and vice versa.

---

## Post-Quantum Hybrid Cryptography

Both key exchange and signatures use a **hybrid** scheme: classical + post-quantum algorithms combined so that if *either* algorithm holds, the data stays secure.

### Hybrid KEM (Key Encapsulation)

Used for: backup encryption, device-to-device key exchange, future sync protocols.

```
Classical:      X25519 (elliptic curve Diffie-Hellman)
Post-quantum:   ML-KEM-768 / Kyber (NIST FIPS 203)
Combination:    shared_secret = HKDF-SHA256(x25519_shared || mlkem_shared, info="hxs-hybrid-kem-v1")
```

Key sizes:
- Public key: 1216 bytes (32 X25519 + 1184 ML-KEM)
- Secret key: 2432 bytes (32 X25519 + 2400 ML-KEM)
- Ciphertext: 1120 bytes (32 X25519 ephemeral + 1088 ML-KEM)
- Shared secret: 32 bytes

### Hybrid Signatures

Used for: backup integrity verification, manifest signing.

```
Classical:      Ed25519 (Edwards curve)
Post-quantum:   ML-DSA-65 / Dilithium (NIST FIPS 204)
Verification:   BOTH signatures must be valid
```

Key sizes:
- Public key: 1984 bytes (32 Ed25519 + 1952 ML-DSA)
- Secret key: 4096 bytes (64 Ed25519 + 4032 ML-DSA)
- Signature: up to 3373 bytes (64 Ed25519 + up to 3309 ML-DSA)

---

## Key Derivation

### Argon2id (Primary)

Memory-hard KDF that resists GPU and ASIC brute-force attacks. Used for deriving encryption keys from user passphrases.

Default parameters (tunable per device):
- Time cost: 3 iterations
- Memory cost: 64 MB (65536 KiB)
- Parallelism: 4 lanes
- Output: 32 bytes
- Salt: 16 bytes (random)

Implementation is the RFC 9106 reference algorithm, vendored as a single C file (~280 lines). Uses BoringSSL's BLAKE2b for the internal hash. No threads — lanes are processed sequentially for maximum device compatibility.

### PBKDF2-SHA256 (Fallback)

Available as a lighter fallback where Argon2id's memory cost is too high. Default 600,000 iterations.

### HKDF-SHA256 (Key Expansion)

Used to derive purpose-specific keys from a master key. Not password-based — takes high-entropy input only.

```kotlin
// Example: derive per-collection encryption key
val collectionKey = encryptor.deriveKey(
    ikm = masterKey,
    salt = collectionId.toByteArray(),
    info = "hxs-collection-dek-v1"
)
```

---

## Integrity & Anti-Tamper

All checks run in native C++ code. They are *detection* mechanisms, not hard blocks — the Kotlin layer decides how to respond.

### Debugger Detection

- **ptrace self-trace**: calls `ptrace(PTRACE_TRACEME)` — fails if a debugger is already attached
- **TracerPid check**: reads `/proc/self/status` for non-zero `TracerPid`

### Frida Detection

- Scans `/proc/self/maps` for `frida`, `gadget`, `linjector` strings
- Checks `/proc/net/tcp` for default Frida port (27042 / 0x69A2)
- Scans `/proc/self/fd` symlinks for Frida named pipes

### Xposed / LSPosed Detection

- Scans `/proc/self/maps` for `xposed`, `XposedBridge`, `lspd`, `LSPosed`, `edxp`, `riru`

### APK Signature Binding

The signing certificate's SHA-256 hash is baked into the key derivation chain. A repackaged APK with a different signing key produces different encryption keys and cannot decrypt existing data.

```
AppMasterKey = HKDF-SHA256(
    ikm  = keystore_secret,
    salt = SHA256(apk_signing_certificate),
    info = "com.dark.tool_neuron.hxs"
)
```

Verification flow:
1. Kotlin side reads the APK signing cert via `PackageManager.GET_SIGNING_CERTIFICATES`
2. Passes SHA-256 of cert to `verifyApkSignature(expected, actual)`
3. Native side does constant-time comparison (`CRYPTO_memcmp`)

### File Integrity

`hashFile(path)` computes SHA-256 of any file. Use at startup to verify `.so` libraries and `classes.dex` against expected hashes injected at build time.

---

## Memory Security

All sensitive data (keys, plaintext, passphrases) is handled through `SecureBuffer`:

- Allocated via `mmap(MAP_PRIVATE | MAP_ANONYMOUS)` — page-aligned
- Locked in RAM via `mlock()` — prevents swapping to disk
- Wiped via `OPENSSL_cleanse()` on destruction — guaranteed not optimized away
- Move-only semantics — no accidental copies
- JNI temporaries wiped after use

---

## Kotlin API

```kotlin
val encryptor = HxsEncryptor()

// ── Symmetric encryption ──
val key = encryptor.randomBytes(32)
val sealed = encryptor.encrypt(plaintext, key)
val decrypted = encryptor.decrypt(sealed, key)

// With additional authenticated data (AAD)
val sealed = encryptor.encrypt(plaintext, key, aad = metadata)
val decrypted = encryptor.decrypt(sealed, key, aad = metadata)

// ── Cipher detection ──
val cipher = encryptor.detectOptimalCipher()
// 0 = AES-256-GCM, 1 = ChaCha20-Poly1305

// ── Key derivation ──
val salt = encryptor.randomBytes(16)
val derived = encryptor.argon2id(password.toByteArray(), salt)
val derived = encryptor.pbkdf2(password.toByteArray(), salt)
val subKey = encryptor.deriveKey(masterKey, salt, info = "purpose-string")

// ── Hashing ──
val hash = encryptor.sha256(data)

// ── Secure wipe ──
encryptor.secureWipe(sensitiveData)

// ── Hybrid KEM (X25519 + ML-KEM-768) ──
val keyPair = encryptor.hybridKemKeygen()
val (ciphertext, sharedSecret) = encryptor.hybridKemEncaps(keyPair.publicKey)
val recovered = encryptor.hybridKemDecaps(ciphertext, keyPair.secretKey)
// sharedSecret == recovered

// ── Hybrid signatures (Ed25519 + ML-DSA-65) ──
val sigKeyPair = encryptor.hybridSignKeygen()
val signature = encryptor.hybridSign(message, sigKeyPair.secretKey)
val valid = encryptor.hybridVerify(message, signature, sigKeyPair.publicKey)

// ── Integrity checks ──
val debugged = encryptor.isDebuggerAttached()
val blocked = encryptor.blockDebugger()
val frida = encryptor.isFridaPresent()
val xposed = encryptor.isXposedPresent()
val sigValid = encryptor.verifyApkSignature(expectedHash, actualHash)
val fileHash = encryptor.hashFile("/data/app/.../lib/arm64/libhxs.so")
```

---

## Build

### Dependencies (fetched automatically via CMake FetchContent)

| Dependency | Version | Purpose |
|---|---|---|
| BoringSSL | `617634bc` (Feb 2026) | AES-GCM, ChaCha20, Ed25519, X25519, HKDF, SHA, BLAKE2b, CSPRNG |
| liboqs | 0.12.0 | ML-KEM-768 (Kyber), ML-DSA-65 (Dilithium) |
| Argon2 | Reference impl (vendored) | Memory-hard password hashing |

### Build Configuration

- **C++ standard**: C++17
- **NDK ABIs**: arm64-v8a, x86_64
- **Min SDK**: 29 (Android 10)
- **Symbol visibility**: hidden (only JNI exports visible)
- **Optimizations**: LTO, section GC, ICF (release builds)
- **Output**: `libhxs_encryptor.so`

### Gradle

```kotlin
// In app/build.gradle.kts
dependencies {
    implementation(project(":hxs_encryptor"))
}
```

---

## File Structure

```
hxs_encryptor/
├── build.gradle.kts
├── consumer-rules.pro
├── src/main/
│   ├── AndroidManifest.xml
│   ├── cpp/
│   │   ├── CMakeLists.txt          # Build config, BoringSSL + liboqs fetch
│   │   ├── memory_guard.h/cpp      # SecureBuffer (mmap, mlock, cleanse)
│   │   ├── crypto_engine.h/cpp     # AES-GCM, ChaCha20, Ed25519, X25519, HKDF, PBKDF2, SHA-256
│   │   ├── argon2.h/cpp            # Argon2id reference implementation
│   │   ├── integrity.h/cpp         # Debugger, Frida, Xposed, APK sig, file hash
│   │   ├── pq_kem.h/cpp            # Hybrid KEM: X25519 + ML-KEM-768
│   │   ├── pq_sign.h/cpp           # Hybrid signatures: Ed25519 + ML-DSA-65
│   │   └── hxs_encryptor.cpp       # JNI bridge
│   └── java/com/dark/hxs_encryptor/
│       └── HxsEncryptor.kt         # Kotlin API
```

---

## Security Properties

| Property | Guarantee |
|---|---|
| Confidentiality | AES-256-GCM or ChaCha20-Poly1305 (256-bit keys) |
| Integrity | GCM/Poly1305 authentication tags on all ciphertext |
| Quantum resistance (symmetric) | AES-256 provides 128-bit post-quantum security (Grover's algorithm) |
| Quantum resistance (key exchange) | Hybrid X25519 + ML-KEM-768 — secure if either algorithm holds |
| Quantum resistance (signatures) | Hybrid Ed25519 + ML-DSA-65 — secure if either algorithm holds |
| Password brute-force resistance | Argon2id with 64 MB memory cost (GPU/ASIC hardened) |
| Key material protection | mmap + mlock + OPENSSL_cleanse (never swapped, guaranteed wiped) |
| Anti-cloning | APK signing cert bound into key derivation |
| Anti-debugging | ptrace self-trace + TracerPid monitoring |
| Anti-instrumentation | Frida agent + Xposed framework detection |

---

## Wire Format Reference

### Sealed Data (symmetric encryption)

```
Offset  Size    Field
0       1       cipher_id (0x00 = AES-GCM, 0x01 = ChaCha20)
1       12      nonce (random per encryption)
13      N       ciphertext
13+N    16      authentication tag (GCM or Poly1305)
```

### Hybrid KEM Ciphertext

```
Offset  Size    Field
0       32      X25519 ephemeral public key
32      1088    ML-KEM-768 ciphertext
```

### Hybrid Signature

```
Offset  Size       Field
0       64         Ed25519 signature
64      <=3309     ML-DSA-65 signature
```
