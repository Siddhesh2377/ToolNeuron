# ToolNeuron

On-device AI for Android. No Google Play services, no telemetry, no cloud. Models, chats, RAG documents, and key material stay on the phone.

The point isn't to clone ChatGPT into your pocket. It's to give you a chat surface, a RAG pipeline, a voice loop, and a plugin runtime that all run with the radio off if you want them to.

## What it does

- **Chat** against any compatible GGUF model. Streaming output, multi-turn, optional thinking mode, per-turn tok/s + TTFT + peak-memory metrics.
- **Vision** via colocated `mmproj` projector files. Image bytes cross AIDL as PFDs, never `byte[]`.
- **RAG** over PDF, DOCX, XLSX, PPTX, ODT, EPUB, RTF, MD, HTML, JSON, XML, CSV, TXT. Source bytes are content-addressed; re-attach a doc to any chat from the picker.
- **Voice** through sherpa-onnx. Streaming TTS that chunks by sentence, tap-to-toggle STT. VITS / Piper / Whisper all work.
- **HTTP server** in its own `:server` process. OpenAI-shaped endpoints, bearer-token auth, rate limit, audit log. Material 3 web UI bundled at `/`.
- **HuggingFace browse** — full-screen explorer with the filters that matter (pipeline tag, library, params, quant, license, gated, author, dataset).
- **Plugin store** — install plugins from `Void2377/tool-neuron-plugins` on HF. Each one is a sandboxed Android module with its own Compose UI, can ship ONNX models, and runs inside the host process behind a capability gate.

## What it doesn't do

Tool calling, Termux integration, anything cloud. The April 2026 scope pivot took those out and they're not coming back. Image generation (`:ai_sd`) and the plugin marketplace came in instead, May 2026.

## Architecture

Three processes.

- `:app` is the UI and where trust decisions live.
- `:inference` runs `InferenceService` over the GGUF engine and sherpa-onnx. Dies with the app.
- `:server` is foreground (`dataSync`, `stopWithTask=false`) so it survives swipe-from-recents and keeps the HTTP listener alive.

Modules:

| Module | Purpose |
|---|---|
| `:app` | UI, viewmodels, Hilt graph |
| `:hxs` | Encrypted KV store with C++ core |
| `:hxs_encryptor` | Argon2id / AEAD / BoringSSL / ML-KEM-768 / ML-DSA-65 / Ed25519, plus the native policy + boot-integrity stack |
| `:native-server` | Embedded HTTP server (cpp-httplib + nlohmann/json) |
| `:download_manager` | Native downloader with JNI bridge |
| `:networking` | Network primitives in jniLibs |
| `:plugin-api` | Pure-Kotlin plugin contract — the only thing plugin authors compile against |
| `:plugin-exc` | Plugin runtime — DexClassLoader, capability gate, HF catalog client, dock |
| `:plugins:*` | First-party plugins (notes, counter, expense) — each is its own Android application module |

## Security

Trust decisions live in C++ so an obfuscation bypass at the Kotlin layer doesn't grant access.

- Auth is Argon2id (t=4, m=128 MiB, p=1, outLen=32). PIN must be 6 digits and pass weak-PIN rejection.
- After verify, a 32-byte opaque session token registers with the native `PolicyEngine`. Every gated feature crosses JNI as `PolicyEngine.isAllowed(Feature, sessionToken)`.
- DEK is wrapped by an Android Keystore AES-256-GCM key (StrongBox-preferred, TEE fallback) and stored XOR-masked at `<filesDir>/app_bootstrap/k.bin`. The crypto is the wrapped ciphertext; the XOR is anti-grep.
- Encrypted prefs at `<filesDir>/app_prefs/` are sealed under `HKDF(DEK, "tn.app_prefs.user_key.v1")`. `AuthState` gets a second AEAD layer keyed on `HKDF(DEK, "tn.app_prefs.auth_key.v1")`.
- Lockout: three free, then `1m → 5m → 15m → 1h → 4h → 12h → 24h`. Tenth wipes device-side state. Clock rollback past five minutes is double-penalized.
- Panic PIN, if set, triggers `hardWipe()` and returns `VerifyResult.Wiped` — indistinguishable from "attempts exceeded".
- TOFU manifest of every `.so` in `nativeLibraryDir`, rebound to install identity (`{signerHash, longVersionCode, lastUpdateTime}`). Hook baseline verify catches inline hooks.
- Debugger / Frida / Xposed scans run before any auth path. Detection strings are XOR-obfuscated at compile time so `strings libhxs_encryptor.so | grep -i frida` returns nothing.
- `FLAG_SECURE` is on for PIN entry only. Chats stay screenshottable.

`CLAUDE.md` is authoritative for the rest, including the planned pro-license hook (`PolicyEngine.is_pro_feature(fid >= 1000)`).

## Plugins

Plugins live at [`Void2377/tool-neuron-plugins`](https://huggingface.co/Void2377/tool-neuron-plugins) on HuggingFace. Each one is a zip — `manifest.json` + `classes*.dex` + optional `lib/<abi>/*.so` — under `plugins/<id>/<version>/`. The app reads `plugins.json` on every screen open. No local cache. The repo is the source of truth, every time.

Install flow: tap a plugin in the in-app store, runtime streams the zip into `cacheDir`, verifies SHA-256 against the manifest, extracts via `PluginBundle`, locks dex/so as read-only (Android 14+ rejects writable dex), deletes the temp file. The plugin's classes load through `DexClassLoader` with `plugin-api` as the parent, and the host calls into `Plugin.Content()` — a `@Composable` that owns its own theme and scaffold.

Capabilities are declared in the manifest and gated by the host: `hxs.read` / `hxs.write` for storage, `ai.onnx` for ORT sessions, `internet` for network, plus camera, mic, filesystem, notifications, clipboard. If a plugin tries something it didn't declare, the gate throws `SecurityException`.

To add to the public store:

```sh
./gradlew :plugins:<name>:packagePlugin
# drop the zip under plugins/<id>/<version>/, add an entry to plugins.json
hf upload Void2377/tool-neuron-plugins . .
```

## Build

```sh
# Dev loop
./gradlew :app:compileDebugKotlin
./gradlew :app:installDebug

# Release (R8 + resource shrink)
./gradlew :app:assembleRelease
```

Release signing reads from `local.properties`:

```properties
TN_KEYSTORE_PATH=/abs/path/to/keystore.jks
TN_KEYSTORE_PASSWORD=...
TN_KEY_ALIAS=...
TN_KEY_PASSWORD=...
```

Missing keys fall back to an unsigned release so the dev flow stays open. `compileSdk 37`, `minSdk 31`, ABI filters `arm64-v8a` + `x86_64`, JVM 17.

`:hxs_encryptor` fetches BoringSSL and liboqs via CMake `FetchContent`. `:native-server` fetches cpp-httplib v0.18.5 and nlohmann/json v3.11.3 the same way. The LSP sometimes flags missing `openssl/mem.h`; that's a false positive — build-green is the source of truth.

## Repo conventions

- HXS-only persisted storage. No `SharedPreferences` / Room / DataStore / raw files, with two intentional exceptions: the bootstrap DEK blob and content-addressed RAG source bytes.
- Single `Scaffold`. `AppScaffold` is the only one. Per-route top bars dispatch from `AppTopBar.kt`; bottom bars from `AppBottomBar.kt`. Screens take `innerPadding: PaddingValues` and render plain `Column` / `LazyColumn` / `Box`.
- No comments in source except a one-liner for non-obvious *why*. No decorative banners. Names and structure self-document.
- Only `:app` minifies. Library modules collide on `Type a.a is defined multiple times` against pre-minified prebuilt jars (e.g. `gguf_lib-release-runtime.jar`) if they pre-minify too. Library rules go in each module's `consumer-rules.pro`.
- Conventional commits. No `Co-Authored-By` trailer.
- `CLAUDE.md` at the repo root is project memory. Spec / plan / research / TODO docs don't go in the tree.

## License

MIT.

## Credits

- llama.cpp / GGUF (Liquid AI fork) via prebuilt AAR
- sherpa-onnx via prebuilt AAR
- BoringSSL, liboqs, cpp-httplib, nlohmann/json — fetched at build time
- Apache Commons Compress for `.tar.bz2` voice archives
- Tabler-derived icon set
