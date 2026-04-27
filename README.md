# ToolNeuron

Privacy-first, offline-only on-device AI assistant for Android.

No Google Play services. No network telemetry. No analytics. Models, chats, RAG documents, and crypto state stay on the device.

---

## Status

Active development. 3.0. Built from Android Studio; signed releases require keystore properties in `local.properties` (see Build).

---

## What it does

- **On-device LLM chat** against any compatible GGUF model. Streaming, multi-turn, thinking-mode, per-turn metrics (tok/s, TTFT, peak memory, context %).
- **Vision-language models (VLM)** via colocated `mmproj` projector files. Image bytes cross the AIDL boundary as PFDs, never `byte[]`.
- **RAG over user documents** (PDF / DOCX / XLSX / PPTX / ODT / EPUB / RTF / MD / HTML / JSON / XML / CSV / TXT). Content-addressed source storage; documents persist across app restarts and can be re-attached to other chats from the prev-chats picker.
- **Voice** — streaming-feel TTS (sentence chunking) and tap-to-toggle STT, both via sherpa-onnx (VITS / Piper / Whisper).
- **Remote Server** — embedded OpenAI-compatible HTTP server (cpp-httplib + nlohmann/json, header-only) running in its own `:server` process. Bundled Material-3 web UI at `/`. Bearer-token auth, rate-limited, audit-logged.
- **HuggingFace Explorer** — full-screen browse with filters across pipeline tag, library, app, language, license, region, quant, gated status, params, and free-text author / dataset.

---

## Out of scope

Removed at the 2026-04-20 scope pivot and not coming back:

- Tool calling / agent platform
- Image generation
- Plugin marketplace
- Termux integration
- Cloud features of any kind

---

## Architecture

### Processes

| Process | Responsibility |
|---|---|
| `:app` (main) | UI (Compose), ViewModels, Hilt graph, security state, HXS vault |
| `:inference` | `InferenceService` over `GGMLEngine` + sherpa-onnx for chat / VLM / TTS / STT |
| `:server` | `RemoteServerService` over its own `GGMLEngine` and the embedded native HTTP server |

`:app` and `:inference` die when the user swipes the app away. `:server` is foreground (`dataSync`, `stopWithTask="false"`) and self-starts before `startForeground`, so it survives swipe-from-recents.

### Modules

| Module | Purpose |
|---|---|
| `:app` | UI + viewmodels + DI graph |
| `:hxs` | Encrypted key-value store (Kotlin + C++ core) |
| `:hxs_encryptor` | Crypto / integrity primitives — Argon2id, AEAD, BoringSSL, ML-KEM-768, ML-DSA-65, Ed25519, HKDF, plus the native security-policy / auth / boot-integrity stack |
| `:native-server` | Embedded HTTP server (cpp-httplib + nlohmann/json, header-only, no BoringSSL/zlib) |
| `:download_manager` | Native download manager with a JNI bridge |
| `:networking` | Network primitives (jniLibs) |
| `:rag-doc-lib` | Document parsing for RAG |

---

## Security model

Layered, with the trust decisions in C++ (so an obfuscation bypass at the Kotlin layer doesn't grant access).

- **Auth.** Argon2id (`t=4 / m=128 MiB / p=1 / outLen=32`). PIN must be 6 digits and pass weak-PIN rejection.
- **Sessions.** A 32-byte opaque session token is registered with the native `PolicyEngine` after a successful verify. Every gated feature crosses JNI as `PolicyEngine.isAllowed(Feature, sessionToken)`.
- **DEK.** A 32-byte data-encryption key is wrapped by an Android Keystore AES-256-GCM key (StrongBox-preferred, TEE fallback) and stored XOR-masked at `<filesDir>/app_bootstrap/k.bin`. Cryptographic protection is the wrapped ciphertext; the XOR is anti-grep obfuscation.
- **Encrypted prefs.** Everything at `<filesDir>/app_prefs/` is sealed under `HKDF(DEK, "tn.app_prefs.user_key.v1")`; `AuthState` carries a second AEAD layer keyed on `HKDF(DEK, "tn.app_prefs.auth_key.v1")`.
- **Lockout.** First three wrong PINs free, then `1m → 5m → 15m → 1h → 4h → 12h → 24h`. Tenth wipes the device-side state. Clock-rollback past five minutes is double-penalized.
- **Panic PIN.** Optional second PIN; on entry it triggers `hardWipe()` and returns `VerifyResult.Wiped`, indistinguishable from "attempts exceeded".
- **Boot integrity.** TOFU manifest of every `.so` in `nativeLibraryDir`, rebound to install identity (`{signerHash, longVersionCode, lastUpdateTime}`). Hook-baseline verify catches inline hooks.
- **Tamper signals.** Debugger / Frida / Xposed scans run before any auth path. Detection strings are XOR-obfuscated at compile time; `strings libhxs_encryptor.so | grep -i frida` is empty.
- **FLAG_SECURE.** Applied to PIN entry only — chats stay screenshottable for the user.

`CLAUDE.md` is the authoritative source for the rest, including the planned pro-license hook (`PolicyEngine.is_pro_feature(fid >= 1000)`).

---

## Build

```sh
# Verify Kotlin compiles (preferred dev loop)
./gradlew :app:compileDebugKotlin

# Install a debug APK
./gradlew :app:installDebug

# Native build verification
./gradlew :hxs_encryptor:externalNativeBuildDebug
```

Release APKs are built **from Android Studio**. Signing is read from `local.properties`:

```properties
TN_KEYSTORE_PATH=/absolute/path/to/keystore.jks
TN_KEYSTORE_PASSWORD=...
TN_KEY_ALIAS=...
TN_KEY_PASSWORD=...
```

Missing keys fall back to an unsigned release so the dev flow isn't blocked.

### Targets

- `minSdk 29` / `targetSdk 36`
- ABI filters: `arm64-v8a`, `x86_64`
- JVM 17

### Native dependencies

`:hxs_encryptor` fetches BoringSSL and liboqs via CMake `FetchContent`. `:native-server` fetches cpp-httplib v0.18.5 and nlohmann/json v3.11.3 the same way. The LSP may flag missing `openssl/mem.h` etc. as errors — those are false positives; build-green is the source of truth.

---

## Repository conventions

- **HXS-only persisted storage.** No `SharedPreferences` / Room / DataStore / raw files (with two intentional exceptions: the bootstrap DEK blob, and content-addressed RAG source bytes).
- **Single Scaffold.** `AppScaffold` is the only `Scaffold` in the app. Per-route top bars dispatch from `AppTopBar.kt`'s `when`; bottom bars from `AppBottomBar.kt`'s `when`. Screens accept `innerPadding: PaddingValues` and render plain `Column` / `LazyColumn` / `Box`.
- **No comments in source** except a one-liner for non-obvious *why*. No decorative banners. No docstrings on internal/private. Names and structure must self-document.
- **Library modules must not minify.** Only `:app` minifies. R8 collides on `Type a.a is defined multiple times` against pre-minified prebuilt jars (e.g. `gguf_lib-release-runtime.jar`) if libraries also pre-minify. Library rules go in each module's `consumer-rules.pro`.
- **Conventional commits.** No `Co-Authored-By` trailer.
- **Project memory lives in `CLAUDE.md`** at the repo root. Spec / plan / research / TODO docs do not go in the tree.

See `CLAUDE.md` for the complete list, including the "things not to regress" section.

---

## License

MIT.

---

## Attribution

- llama.cpp / GGUF (Liquid AI fork) via prebuilt AAR
- sherpa-onnx via prebuilt AAR
- BoringSSL, liboqs, cpp-httplib, nlohmann/json — fetched at build time
- Apache Commons Compress for `.tar.bz2` voice archives
- Tabler-derived icon set
