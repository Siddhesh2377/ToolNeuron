# ToolNeuron — Repo Guide

Project memory for this repo. **When you change anything that affects future work — architecture, security behavior, new features, deprecated paths, public APIs, native JNI contracts, new scope — update this file as part of the same change.** A future session reads this to reconstruct intent; if it drifts, work breaks.

---

## Project scope

Privacy-first, offline-only on-device AI assistant. No Google Play services, no network telemetry, no analytics. In-scope pillars: on-device LLM chat, RAG over user documents, vision-language models (VLM), voice (TTS+STT), Remote Server with bundled web UI, HF Explorer, **on-device image generation / img2img / inpaint / 4× upscale via the `:ai_sd` AAR (re-pivoted in 2026-05-08)**, **first-party plugin system with ONNX inference + capability-gated APIs (re-pivoted in 2026-05-11)**. Out of scope: tool calling, Termux integration. (Image generation was originally cut on 2026-04-20 and re-added on 2026-05-08. Plugin marketplace was also originally cut on 2026-04-20 and re-added on 2026-05-11 as a first-party plugin runtime — DexClassLoader, Plugin contract with @Composable Content(), capability-gated OnnxApi/HxsApi/NetworkApi, floating plugin dock with smooth switch transitions.)

Package: `com.dark.tool_neuron` · minSdk 29 · targetSdk 36 · abiFilters `arm64-v8a`, `x86_64`.

Modules:
- `:app` — UI (Compose), viewmodels, DI graph, activities, services.
- `:hxs` — encrypted key-value store (Kotlin wrapper + C++ core).
- `:hxs_encryptor` — crypto + integrity primitives: Argon2id, AES-GCM/ChaCha20-Poly1305, BoringSSL, ML-KEM-768, ML-DSA-65, Ed25519, HKDF, mmap+mlock `SecureBuffer`, plus the native security policy / auth / boot-integrity stack.
- `:native-server` — embedded OpenAI-compatible HTTP server (cpp-httplib + nlohmann/json header-only via FetchContent, no BoringSSL / OpenSSL / zlib dep). Powers Remote Server mode.
- `:plugin-api` — pure-Kotlin contract plugin authors compile against. `Plugin` interface with lifecycle + `@Composable Content()`, `PluginContext`, `PluginCapability` enum, `PluginManifest`, `OnnxApi`, `HxsApi`, `NetworkApi`. Compose deps are `compileOnly`; host provides them at runtime via classloader-parent.
- `:plugin-exc` — plugin host runtime. `PluginExecutor`, `PluginLoader` (DexClassLoader from `<filesDir>/plugins/<id>/classes.dex` + .so detection across SUPPORTED_ABIS), `PluginRegistry`, `PluginInstance`, `CapabilityGate` (PolicyEngine.hasSession + manifest cap check), `PluginContainerActivity` (hosts plugin's `Content()` with AnimatedContent fade+scale plugin-switch transitions), `PluginDock` (floating M3-expressive surface with circle chips per open plugin, tertiary-corner native-code badge), `OnnxApiImpl` (wraps onnxruntime-android 1.20.0, gated by AI_ONNX), `HxsApiImpl` (per-plugin collection `plugin_<id>`, indexed string keys, gated by HXS_READ/WRITE), `NetworkApiImpl` (WebNative.fetchBytes GET, gated by INTERNET).
- `:download_manager`, `:networking` — ancillary modules.

Prebuilt AARs in `libs/`:
- `gguf_lib-release.aar` — chat + VLM + embedding inference engine.
- `ai_sherpa-release.aar` — TTS / STT.
- `ai_sd-release.aar` — Stable Diffusion (text-to-image / img2img / inpaint / 4× upscale) via QNN on Snapdragon NPU and MNN on CPU. Currently the **debug** AAR is shipped because the release AAR's R8 minified the `StableDiffusionManager.Companion.getInstance` accessor; consumer-rules in `:ai_sd` need a `-keep class com.dark.ai_sd.StableDiffusionManager$Companion { *; }` before we can switch to release.

---

## Build & Release

- **Dev (debug):** `./gradlew :app:compileDebugKotlin` to verify compilation. `./gradlew :app:installDebug` to install. Never `assemble + adb install`.
- **Release:** built **from Android Studio**, signed via `local.properties` keys `TN_KEYSTORE_PATH / TN_KEYSTORE_PASSWORD / TN_KEY_ALIAS / TN_KEY_PASSWORD`. If any are missing, release falls back to unsigned so dev flow isn't blocked.
- **Native:** `./gradlew :hxs_encryptor:externalNativeBuildDebug` — BoringSSL + liboqs fetched via CMake FetchContent. The LSP flags `'openssl/mem.h' not found` etc. as false positives — build-green is the source of truth, not clangd.
- **Instrumented tests:** `./gradlew :app:connectedDebugAndroidTest`.

---

## Hard rules

1. **HXS-only persisted storage.** No `SharedPreferences`, Room, DataStore, or raw files. The only exception is `app_bootstrap/k.bin` (XOR-masked raw blob holding the Keystore-wrapped DEK) — it has to live outside the encrypted vault by construction.
2. **Security logic lives in C++/JNI.** Kotlin wraps native; every auth / trust / policy decision is made native and crosses JNI as opaque token or bool. No boolean-trust-through-JNI, no Kotlin `if (verify)` gating.
3. **No comments in source** except one-liner `//` for non-obvious WHY. No decorative banners, no block comments, no docstrings on internal/private. Names and structure must be self-documenting.
4. **Never write fully-qualified class names inline.** `import` at the top; short name in the body.
5. **ViewModels live under `com.dark.tool_neuron.viewmodel`.** Never co-locate a VM with its screen.
6. **Commit hygiene:** conventional commits, no `Co-Authored-By` trailer, never push without explicit ask, never skip hooks. Don't commit unless the user explicitly asks.
7. **Research / exploration subagents run on Sonnet at low effort** — not Opus — unless the user overrides.
8. **No TODOs, stubs, or half-implementations.** Every task is coded end-to-end.
9. **When you change security-affecting state, update CLAUDE.md in the same change.**
10. **One Scaffold only** — the root `AppScaffold`. Screens take `innerPadding: PaddingValues` and render plain `Column`/`LazyColumn`/`Box`. Per-route top bars go in `AppTopBar.kt`'s `when`, bottom bars in `AppBottomBar.kt`'s `when`.
11. **Library modules must NOT minify.** Only `:app` minifies. R8 collides on `Type a.a is defined multiple times` against pre-minified prebuilt jars (e.g. `gguf_lib-release-runtime.jar`) if libraries also pre-minify. Library rules go in each module's `consumer-rules.pro`.
12. **No spec/plan/research docs in the repo.** Project memory belongs here. Implementation roadmaps belong in conversation context, not in `*.md` files at the repo root.

---

## Security architecture

### Layered model

- **UI:** PasswordScreen / SetupPasswordScreen wrapped in `SecureScreen` (FLAG_SECURE). `AppScaffold` watches `shouldLock` and re-routes to PasswordScreen.
- **App Kotlin:** `SecurityManager` (only auth API the app consumes), `SessionHolder` (opaque 32-byte token), `AppLockObserver` (ProcessLifecycleOwner; clears on ON_STOP), `NativeIntegrity` (TOFU .so hashes + APK signer capture), `AccessibilityGuard`, `RootGuard`, `PinStrength`, `AppPreferences` (encrypted HXS + sealed AuthState), `AppKeyStore` (Android Keystore wrap/unwrap of DEK).
- **Native (libhxs_encryptor.so):** `PolicyEngine` — single `is_allowed(feature_id, token)` gate; `AuthNative` (Argon2id setup/verify, emits session token); `BootIntegrity` (JNI_OnLoad hooks, hook-baseline capture); `IntegrityGuard` (debugger / frida / xposed / sig / hash); `CryptoEngine` (AEAD, HKDF, Argon2id, Ed25519, X25519); `HybridKem` / `HybridSign` (X25519+ML-KEM-768, Ed25519+ML-DSA-65).

### Keystore DEK flow

1. `AppKeyStore` reads / writes `filesDir/app_bootstrap/k.bin`. Layout: `[magic "TNDK"(4)][version(1)][iv_len(2)][iv][ct_len(2)][ct]` masked byte-wise with a 32-byte hardcoded XOR key. XOR is obfuscation; the cryptographic protection is the Keystore-wrapped ciphertext inside.
2. Keystore alias `toolneuron_vault_dek_v1`: AES-256-GCM, `setIsStrongBoxBacked(true)` with `StrongBoxUnavailableException` fallback to TEE. NOT `setUserAuthenticationRequired(true)` (chicken-and-egg with setup flow).
3. First launch: generate 32-byte DEK via `SecureRandom`, wrap, write XOR-masked blob.
4. Every launch: read, unmask, parse, unwrap.
5. `AppKeyStore.backing()` classifies via `KeyInfo.securityLevel` (API 31+) or `KeyInfo.isInsideSecureHardware` → STRONGBOX / TEE / SOFTWARE_FALLBACK / UNKNOWN.
6. `AppKeyStore.wipe()` deletes `k.bin` + Keystore alias.
7. **Legacy migration:** if `k.bin` is missing but `app_bootstrap/` has any other file, wipe `app_bootstrap/*` + `app_prefs/*` + Keystore alias, then re-bootstrap.

### Signer-bound user-keys (v2)

Every per-vault user-key is derived as `HKDF(ikm = DEK, salt = installSignerHash, info = "tn.<scope>.user_key.v2")`. The signer hash is `SHA-256(packageInfo.signingInfo.firstSigner.toByteArray())`, computed once per process via `AppKeyStore.installSignerHash()` and cached in `cachedSignerHash` (cleared on `wipe()`). On API < 28 it falls back to `GET_SIGNATURES`.

Why salt-bind to the signer: Keystore-wrapped DEK is already device-bound (a different device cannot unwrap `k.bin`). Signer-binding closes the **same-device, replaced-APK** attack — root + repack ToolNeuron with an attacker cert + boot it on the legit device. The Keystore alias is uid-scoped, so the patched APK *can* unwrap the DEK; but its signing certificate hashes to a different value, so every user-key derived under the attacker build is wrong. AEAD records fail to decrypt. The repo's `openOrRebuild` helper detects the open failure and wipes the vault, so the attacker gets a fresh empty vault — the original encrypted bytes on disk stay sealed under the legitimate signer's user-key forever.

If `getPackageInfo(... GET_SIGNING_CERTIFICATES)` returns null/empty (some weird OEM, broken install), `installSignerHash()` throws `SecurityException` and the app refuses to bootstrap. Don't add a fallback that returns zeros — that would defeat the binding.

### Vault inventory

| Vault dir | Sealed under | Notes |
|---|---|---|
| `app_bootstrap/k.bin` | Android Keystore alias `toolneuron_vault_dek_v1` (StrongBox/TEE), wrapped in XOR-masked envelope | Format: `[magic "TNDK"(4)][version(1)][iv_len(2)][iv][ct_len(2)][ct]`, byte-XOR with hardcoded 32-byte key. The XOR is obfuscation; the cryptographic protection is the Keystore-wrapped `ct`. |
| `app_prefs/` | `tn.app_prefs.user_key.v2` | All preferences. AuthState rides a second AEAD layer keyed `tn.app_prefs.auth_key.v2`, AAD `"tn.auth_state.v1"`. |
| `chat_store_v2/` | `tn.chats.user_key.v2` | Chats + messages. Replaces the legacy plaintext `chat_store/` (deleted on first v2 boot). |
| `chat_documents_meta_v1/` | `tn.chat_documents.user_key.v2` | RAG document metadata (id, name, mime, chunk count, sourceId). Dir name is historical — the user-key is v2. |
| `chat_documents/sources_v2/` | per-file AEAD via `SourceFileVault` | Each `<sourceId>.bin` is `[iv(12)][ct][tag(16)]` AEAD blob. Per-file key is `HKDF(DEK, salt=signerHash, info="tn.chat_doc_source.user_key.v2@<sourceId>")`. AAD = sourceId UTF-8 bytes (rename → decrypt fails). Replaces the legacy plaintext `chat_documents/sources/` (deleted on first v2 boot). |
| `rag_keyword_v1/` | `tn.rag_keyword.user_key.v2` | BM25 inverted-index records. |

**v1 → v2 migration is destructive.** Each repo's `openOrRebuild` tries `openEncrypted` with the v2 key. If that fails (existing v1 data sealed under the old non-signer-bound key), it wipes the vault dir and re-creates fresh. On first launch with a v2 build, an existing user loses their PIN, chat history, and RAG attachments — one time. The Keystore alias is preserved (so the DEK is still the same), only the per-vault user-keys change.

### AppPreferences — encrypted HXS + sealed AuthState

`HexStorage.openEncrypted(path, appKey=DEK, userKey=HKDF(DEK, salt=signerHash, info="tn.app_prefs.user_key.v2"), encryptor)`. Auth-critical state rides a second AEAD layer: `writeAuthState`/`readAuthState` use key `HKDF(DEK, salt=signerHash, info="tn.app_prefs.auth_key.v2")` and AAD `"tn.auth_state.v1"`. Ordinary flags (`onboarding_complete`, `tc_accepted`, `setup_done`, server settings, etc.) are plain encrypted records.

### AuthState v4

```
version(1) = 4
security_mode(1)             — 0=NONE, 1=APP_PASSWORD
salt_len(2) + salt
hash_len(2) + hash
failed_attempts(2)
next_attempt_at_ms(8)
has_panic(1)
  if has_panic:
    panic_salt_len(2) + panic_salt
    panic_hash_len(2) + panic_hash
last_seen_now_ms(8)          — monotonic wall-clock anchor
```

Decoder accepts v1/v2/v3/v4 and zero-fills missing fields. Bump `AuthState.VERSION` and the decoder when extending.

### Native auth path

- `hxs::auth::setup(pin) → {salt[16], hash[32]}` — Argon2id `t=4 / m=128 MiB (131072 KiB) / p=1 / outLen=32`.
- `hxs::auth::verify(pin, salt, stored_hash) → 32-byte session_token | null`. Constant-time `CRYPTO_memcmp`, then `policy::register_session(token)`.
- `hxs::auth::invalidate()` → `policy::invalidate_session()`.

### PolicyEngine

Every gated call: `PolicyEngine.isAllowed(Feature, sessionToken)`. Native logic order:
1. `tampered` → false (latched one-way).
2. `is_pro_feature(fid)` (fid ≥ 1000) → **false**. *This is the flip-point for the future license system.*
3. `is_unauth_feature(fid)` (APP_LAUNCH, OPEN_VAULT, AUTH_SETUP, AUTH_VERIFY, UI_PASSWORD_SCREEN, UI_SETUP_SCREEN, UI_INTRO) → true.
4. `passthrough` is on (set when `security_mode == NONE`) → true.
5. Else require `session_active && CRYPTO_memcmp(stored_token, given_token, 32) == 0`.

State mutations: `register_session`, `invalidate_session`, `set_passthrough`, `mark_tampered`, `reset_for_testing` (test-only). Feature IDs in `policy_engine.h` mirrored as `PolicyEngine.Feature` in Kotlin — keep in sync.

### Lockout / backoff / wipe / clock rollback

`LockoutPolicy.backoffMillis(failed)` — first 3 free, then 1m → 5m → 15m → 1h → 4h → 12h → 24h. `WIPE_THRESHOLD = 10` triggers `SecurityManager.hardWipe()`.

Clock-rollback defense: `AuthState.lastSeenNowMs` updated on every verify; if `nowMs + CLOCK_SKEW_GRACE_MS (5 min) < lastSeenNowMs`, the attempt is double-penalized and backoff extends from `max(nowMs, lastSeenNowMs)`.

`hardWipe()`: `session.clear()` → `PolicyEngine.invalidateSession()` → `PolicyEngine.markTampered()` → `prefs.clearAuthState()` → `keyStore.wipe()`. `keyStore.wipe()` is scorched-earth: clears the cached DEK reference, recursively deletes everything under `filesDir` (models, voice, chat_store, chat_documents, model_store, plugin_store, rag_prefs, app_prefs, app_bootstrap, config, cache subtree) and `cacheDir`, then removes the Keystore alias. After hardWipe the app is in an unrecoverable in-process state (`markTampered` latches, files are gone), so the user must hit Restart on `WipedScreen` to bootstrap fresh.

Panic PIN: `SecurityManager.setPanicPin(pin)` writes a second Argon2id hash. The gate is `securityMode == APP_PASSWORD` only — the live-session check was removed because `ProcessLifecycleOwner.ON_STOP` (notification panel pull, brief focus loss) clears the session via `AppLockObserver` while a Compose dialog stays visible above the locked screen, producing a non-deterministic "Couldn't set panic PIN" failure when the user submits. The panic-PIN UI is reachable only from Settings, which is itself gated by `shouldLock → PasswordScreen` re-routing, so the persistent "lock is set" fact is sufficient. `verifyPassword` tries real first; on mismatch, if `hasPanic`, tries panic. Panic match → `hardWipe()` + `VerifyResult.Wiped` (UX-indistinguishable from "attempts exceeded"). `clearPanicPin` and `disableLock` use the same `securityMode == APP_PASSWORD` gate for the same reason.

PIN rules: 6 digits exactly. Weak PINs (all-same, monotonic ±1, top-20 commons) rejected at setup via `PinStrength.evaluate`.

### Boot integrity

`JNI_OnLoad` in `hxs_encryptor.cpp`:
1. `boot::install_ptrace_self_trace()` — PTRACE_TRACEME.
2. `boot::capture_hook_baselines()` — first 32 bytes of `auth::verify`, `policy::is_allowed`, `boot::hard_fail`.

`TNApplication.onCreate()` in main process:
1. `integrity.scanProcessEnvironment()` — debugger / frida / xposed. Only `FAIL_DEBUGGER | FAIL_FRIDA` → `BootIntegrity.hardFail(reasons)`. `FAIL_XPOSED` is *not* a hard fail — it's recorded into `TNApplication.softEnvReasons` and surfaced as `tamperEvidence` in the one-time `RootWarningDialog` later.
2. `integrity.bootVerify()` — TOFU .so hash walk rebound to install identity. Manifest layout v2: `version(1) + signerHashLen(2)+signerHash + versionCode(8) + lastUpdateTime(8) + count(4) + (nameLen(2)+filename + hashLen(2)+hash)*`. If `{signerHash, longVersionCode, lastUpdateTime}` differs (or no manifest), re-TOFU and store. Within the same install identity, filename-set + hash mismatch → `FAIL_LIB_HASH` → hard fail. Filenames are stored (not absolute paths) since Android reshuffles `/data/app/~~…/`. `apk_signer_hash_v1` is also written for the future license-binding path.
3. `BootIntegrity.verifyHookBaselines()` — re-reads the prologue and compares; catches inline hooks.
4. `accessibilityGuard.scan()` — does NOT hard-fail any longer. Suspicious packages flow through `ScaffoldViewModel` into `RootWarning.a11yPackages` for the same one-time warning dialog.
5. `appLockObserver.register()`.

Root detection was removed from the boot path. `RootGuard.scan()` runs from `ScaffoldViewModel` on first launch; if rooted and `rootWarningShown == false`, `AppScaffold` shows a one-time `RootWarningDialog`. Acknowledging flips the flag.

The same one-time-warning treatment now also covers two adjacent "rooted-user" tamper signals that previously hard-failed the app:

- **Xposed / LSPosed / Riru** — `scan_process_environment` still detects the `/proc/self/maps` substrings, but `TNApplication.onCreate` only hard-fails on `FAIL_DEBUGGER | FAIL_FRIDA`. A standalone `FAIL_XPOSED` bit is recorded into `TNApplication.softEnvReasons` (process-singleton, `internal set`) and surfaced as `tamperEvidence` in `RootWarningDialog`. `FAIL_DEBUGGER` and `FAIL_FRIDA` remain hard fails — those are active attack tools, not just a user-installed framework.
- **Suspicious accessibility services** — `accessibilityGuard.scan()` no longer hard-fails in release on `SuspiciousAttached`. The packages flow into `RootWarning.a11yPackages` and render as a third paragraph in the same dialog.

`ScaffoldViewModel.RootWarning` is `(rootEvidence, tamperEvidence, a11yPackages)` — three independent `Set<String>`. The dialog renders a paragraph per non-empty section with a single "I understand" button. `acknowledgeRootWarning()` flips `rootWarningShown` once and silences all three sources for that install.

`BootIntegrity.hardFail(reason)` → `PolicyEngine.markTampered()` + `_exit(1)`, unless `setRelaxedForTesting(true)` (tests).

### Tamper / hook obfuscation

All detection strings in `integrity.cpp` use `HXS_OBF(var, "literal")` (compile-time XOR). Verified clean: `strings libhxs_encryptor.so | grep -iE '^(frida|gadget|linjector|xposed|TracerPid)$'` returns nothing.

### Session lifecycle + UI lock

- `SessionHolder.active: StateFlow<Boolean>` flips on `AuthNative.verify` success, off on `clear()`.
- `AppLockObserver` (a `DefaultLifecycleObserver` on `ProcessLifecycleOwner`) calls `session.clear()` on `ON_STOP` when `security.isLockEnabled`. Clear also calls `AuthNative.invalidate()`.
- `ScaffoldViewModel.shouldLock = security.isLockEnabled && !session.active`. `AppScaffold` re-routes to PasswordScreen with `popUpTo(0) { inclusive = true }`, except when on a non-interruptible route (`PasswordScreen`, `SetupScreen`, `IntroScreen`).

### FLAG_SECURE + SecureClipboard

`ui/components/SecureScreen.kt` adds FLAG_SECURE on enter, clears on dispose. Applied to PIN entry. Not global (so users can screenshot their own chats). `util/SecureClipboard.kt` sets `EXTRA_IS_SENSITIVE` (TIRAMISU+) and auto-clears after 30 s if the clip still matches.

### Release hardening

`app/build.gradle.kts`: `isMinifyEnabled = true`, `isShrinkResources = true`, `isDebuggable = false`, `isJniDebuggable = false`. ProGuard (`app/proguard-rules.pro`) strips `Log.d/v/i` via `assumenosideeffects` (keeps `w`/`e`), `-repackageclasses ''`, `-allowaccessmodification`. Manifest: `allowBackup=false`; `TempActivity` + `ColorShowcaseActivity` un-exported; `InferenceService` runs in `:inference` process.

---

## Future pro license system — the hook

The license plumbing is live: every gated feature routes through `PolicyEngine.isAllowed(Feature, sessionToken)`. Feature IDs `≥ 1000` are `PRO_*` and currently return false. To enable monetization:

1. Flip the `is_pro_feature` branch in `policy_engine.cpp` from `return false` to "verify signed license blob".
2. License blob layout (planned): `{device_id_hash, features_bitmap, expiry_unix, nonce, signature}` — Ed25519 or ML-DSA-65 signed; public key XOR-baked into native at build time.
3. APK signer SHA-256 is **already captured** on first launch and stored as `apk_signer_hash_v1`. That's the anchor.
4. Device id must be an attested Keystore key fingerprint (hardware-rooted), **NOT** `Settings.Secure.ANDROID_ID`.
5. License blob lives in a separate HXS collection sealed under the same DEK.
6. On tamper detection, invalidate any loaded license too — single fail-closed path.

---

## File map

### Native (`hxs_encryptor/src/main/cpp/`)
- `policy_engine.{h,cpp}` — `is_allowed`, session registry, tamper latch, `reset_for_testing`.
- `auth.{h,cpp}` — `setup(pin)`, `verify(pin, salt, hash)`, hardened Argon2id.
- `boot_integrity.{h,cpp}` — env scan, lib-hash verify, hook-baseline capture/verify, `hard_fail`, `setRelaxedForTesting`.
- `integrity.{h,cpp}` — debugger / frida / xposed checks, file hashing, APK sig compare.
- `xor_str.h` — `HXS_OBF(var, "literal")` compile-time XOR.
- `crypto_engine.{h,cpp}` — AEAD, HKDF, PBKDF2, Argon2id, Ed25519, X25519, SHA-256.
- `memory_guard.{h,cpp}` — `SecureBuffer` (mmap+mlock+mprotect), `secure_zero`, `secure_compare`.
- `pq_kem.{h,cpp}` — X25519 + ML-KEM-768 hybrid KEM.
- `pq_sign.{h,cpp}` — Ed25519 + ML-DSA-65 hybrid signatures.
- `hxs_encryptor.cpp` — JNI bindings + `JNI_OnLoad`.
- `CMakeLists.txt` — fetches BoringSSL + liboqs; `-march=armv8-a+crypto+sha2` on arm64; LTO/gc-sections/icf on release; `-Wl,-z,max-page-size=16384` on every owned native CMake target.

### Encryptor module Kotlin (`hxs_encryptor/src/main/java/com/dark/hxs_encryptor/`)
`HxsEncryptor.kt`, `PolicyEngine.kt`, `AuthNative.kt`, `BootIntegrity.kt`.

### App-side security (`app/src/main/java/com/dark/tool_neuron/data/`)
`AppKeyStore`, `SessionHolder`, `SecurityManager`, `SecurityModule`, `AppPreferences`, `AuthState`, `VerifyResult`, `LockoutPolicy`, `NativeIntegrity`, `AppLockObserver`, `AccessibilityGuard`, `RootGuard`, `PinStrength`, `KeyFingerprint`.

### UI
- `ui/components/SecureScreen.kt` — FLAG_SECURE wrapper.
- `ui/screens/password_screen/PasswordScreen.kt` + `setup_screen/SetupPasswordScreen.kt` — wrapped in SecureScreen.
- `ui/screens/setup_screen/SetupThemeScreen.kt` — first-run theme + palette.
- `ui/screens/system_ui/AppScaffold.kt` — single Scaffold; auto-lock + server-lockdown re-routing.
- `util/SecureClipboard.kt`, `util/VlmPaths.kt`.
- `ui/screens/guide/` — hub + 7 detail screens via `GuideDetailLayout` + `GuideTopBar`.
- `ui/screens/home_screen/PlusMenu.kt` — Documents / Thinking / Attach image (image disabled until `isVlmLoaded`).
- `ui/screens/home_screen/HomeScreenBottomBar.kt` — image-attach button + mic button + transcribe equalizer.
- `ui/screens/server/ServerScreen.kt` + `ServerTopBar.kt` — Remote Server config + token + status + request log.
- `ui/screens/hf_explorer/{HfExplorerScreen,HfRepoDetailScreen}.kt` — search / filter / repo browser.

### Build
`app/build.gradle.kts`, `app/proguard-rules.pro`, `hxs_encryptor/build.gradle.kts`, `gradle/libs.versions.toml`, `app/src/main/AndroidManifest.xml`.

---

## VLM (vision-language models)

Vision rides on top of an active GGUF chat model via a separate mmproj projector file. Image data crosses AIDL via `ParcelFileDescriptor[]` (1 MB binder limit forbids `byte[]`).

### Folder layout

VLM models live as `<modelsDir>/vlm/<repoLeaf>/{base.gguf, mmproj.gguf}`. A HuggingFace repo is detected as VLM if any `.gguf` file in its tree has `mmproj` (case-insensitive) in its name. Downloads pull both files into the per-repo folder; loading the base auto-loads the colocated mmproj. There is no manual "load projector" UI.

### AIDL surface (used)

```
boolean loadVlmProjector(String path, int threads, int imageMinTokens, int imageMaxTokens);
boolean loadVlmProjectorFromFd(in ParcelFileDescriptor pfd, int threads, int imageMinTokens, int imageMaxTokens);
void releaseVlmProjector();
boolean isVlmLoaded();
String getVlmInfo();
String getVlmDefaultMarker();
void generateVlm(String messagesJson, in ParcelFileDescriptor[] imageFds, int maxTokens, IGenerationCallback callback);
```

### Service flow

`InferenceService.generateVlm(messagesJson, imageFds, maxTokens, cb)` reads each PFD via `AutoCloseInputStream.readBytes()` on Dispatchers.IO, hands `List<ByteArray>` to `engine.generateVlmFlow`, and bridges `GenerationEvent` → `IGenerationCallback`. Read failures → `callback.onError`.

### Client / coordinator

`InferenceClient.isVlmLoaded: StateFlow<Boolean>` mirrors service-side state. `loadVlmProjector(path, threads=2)` is the path-based load used by auto-load. `generateVlm(context, messagesJson, imageUris, maxTokens): Flow<InferenceEvent>` opens PFDs, hands the array to the service, closes after the call. `InferenceCoordinator.run()` per-iteration: if iteration==0 AND last user has non-empty `imageUris` AND `isVlmLoaded.value` → VLM route. `buildMessagesJson(messages, vlmLastUserId=lastUser.id)` prepends `getVlmDefaultMarker()` to the last user's content.

### Auto-load

`ModelSessionManager.load(model)`:
1. `releaseVlmProjector()` if currently loaded.
2. Load base.
3. On success, if `pathType == FILE` and the path is inside `<modelsDir>/vlm/`, call `VlmPaths.colocatedMmproj(baseFile)`. Present → load. Missing → surface `vlmAutoLoadError` via `StateFlow<String?>`; UI shows `VlmErrorBanner`.

`ModelSessionManager.unload()` releases projector first.

### Persistence

`ChatMessage.imageUris: List<String>` persisted via `ChatRepository.TAG_MSG_IMAGES = 8` (JSON array of URI strings). Image bytes never land on disk.

`Chat.forkedFromChatId: String?` persisted via `ChatRepository.TAG_FORKED_FROM = 9` (chats collection). Set by `ChatRepository.forkChat(sourceChatId, atMessageId)` — clones every message up to and including the cut point into a new chat with title `"<src> (fork)"`. Drawer renders `TnIcons.Fork` + "Forked" label next to the title when the field is non-null. Forking is gated on `!isGenerating` in `HomeViewModel.forkFromMessage`.

### Catalog + downloads

`ModelCatalog.fetchRepo` flags any repo whose tree contains a `*mmproj*.gguf` file; non-mmproj `.gguf` rows get `isVlm=true`, `repoPath`, `mmprojFileName`, `mmprojFileUri`, `mmprojSizeBytes`. Tag list adds "VLM". `ModelStoreViewModel.downloadModel` routes VLM base into `vlmModelFile(repoPath, fileName)`; on completion, enqueues mmproj into the same folder under the same `modelId`. Finalize inserts a single `ModelInfo` whose path is the base `.gguf`.

### UI

PlusMenu Attach-image is disabled when `!isVlmLoaded`, with a "Attach image · VLM required" badge. PendingImageRow renders thumbnails via `BitmapFactory.decodeStream`. `MessageBubble` renders `UserImageThumbnails` when `message.imageUris.isNotEmpty()`. `InstalledModelCard` shows a "VLM" tag for paths under `models/vlm/`.

### DI

`HomeViewModel` and `InferenceCoordinator` take `Application` (for `contentResolver` + `generateVlm(app, ...)`).

---

## Voice (TTS + STT)

Streaming TTS playback of assistant messages and tap-to-toggle STT input via the sherpa-onnx AAR. The AAR exposes VITS + Kokoro TTS and Whisper STT only; SupertonicTTS is not supported.

**Install path: Store only.** BYOM / SAF directory import was removed (2026-04-24). The Store downloads `.tar.bz2` from sherpa-onnx GitHub releases, extracts into `<filesDir>/voice/<tts|stt>/<folder>/`, builds the sherpa-onnx config JSON, inserts `ModelInfo` + `ModelConfig`. Archive deleted after extraction. First TTS/STT download of each kind is auto-selected as active.

### AIDL surface

```
boolean loadTtsModel(String configJson);   void unloadTtsModel();   boolean isTtsLoaded();
float[] synthesize(String text, int speakerId, float speed);   int getTtsSampleRate();
boolean loadSttModel(String configJson);   void unloadSttModel();   boolean isSttLoaded();
String recognize(in float[] samples, int sampleRate);
String recognizeFromFd(in ParcelFileDescriptor pfd, int sampleCount, int sampleRate);
```

`synthesize` and `recognize` are batch — there is no streaming callback. Streaming TTS is faked by sentence-chunking at the **text** layer.

### Layout

`app/src/main/java/com/dark/tool_neuron/voice/`:
- `TtsPlayer` — sentence-chunk streaming via `AudioTrack.MODE_STREAM + WRITE_BLOCKING`. Cancellable per-chunk via `_speakingId.value == messageId` check.
- `SttRecorder` — `AudioRecord` 16 kHz mono `ENCODING_PCM_FLOAT`, source `MediaRecorder.AudioSource.VOICE_RECOGNITION`. Exposes `isRecording`, `amplitude` flows for UI.
- `VoiceModelManager` — `@Singleton`. Auto-loads active TTS/STT on first use by reading `AppPreferences.activeTtsModelId` / `activeSttModelId`. Uses `Mutex` to serialize loads. Injects `Lazy<AppPreferences>` to avoid eager construction in non-main processes.
- `VoiceArchive` — extraction. Streams `.tar.bz2` through `BZip2CompressorInputStream` → `TarArchiveInputStream`, writes each entry into a per-archive folder, builds the sherpa-onnx config JSON. Per-entry `safeResolve` rejects path-traversal. Calls back `onEntry(name)` for per-file UI progress.

### Model distinction

No `modelType` field on `ModelInfo`. `ProviderType` is canonical (`GGUF` / `TTS` / `STT` / `EMBEDDING`). `HuggingFaceModel.modelType: String` is the pre-install hint; `ModelStoreViewModel.finalizeNonVlmDownload` maps it to `ProviderType` at insert time. `HomeViewModel.chatModels` filters to `ProviderType.GGUF`.

### Persistence

`AppPreferences` keys `active_tts_model` and `active_stt_model` (encrypted HXS records). Empty → fallback to first installed model of that type. Voice models live under `<filesDir>/voice/<tts|stt>/<folder>/`. Voice deletes need `deleteRecursively()` since the folder is non-empty (current limitation: store delete uses `File.delete`).

### Streaming TTS approximation

`TtsPlayer.sanitize(text)` strips code fences / inline code / markdown emphasis / links / headers. `splitIntoSentences(text)` breaks at `.`/`!`/`?`/`…`/`;`/`\n` after ≥20 chars or at comma/space if ≥180 chars. Each chunk synth'd on Dispatchers.IO and written into the `AudioTrack` with `WRITE_BLOCKING`. `AudioTrack` is lazy at `getTtsSampleRate()`; recreated on rate change.

### STT

`SttRecorder.start()` reads 1024-sample chunks in a tight loop, snapshots max abs into `_amplitude`, appends to a synchronized buffer. `stop()` snapshots into `FloatArray`, releases. The array is passed to `InferenceClient.recognize(samples, 16000)` from `HomeViewModel.stopRecordingAndTranscribe`, which pushes recognized text into `_transcribedText: StateFlow<String?>`. `HomeScreenBottomBar` observes and appends to its local text state, then calls `consumeTranscribedText()`. STT is unloaded after each transcription to free memory.

### Permissions

`RECORD_AUDIO` requested at first mic tap via `ActivityResultContracts.RequestPermission`; on grant, immediately `startRecording()`. No `FOREGROUND_SERVICE_MICROPHONE` (UI is held while recording).

### UI

- Speak / Stop button on assistant bubbles (`MessageActions`) when `voiceTtsAvailable`. Icon flips between `TnIcons.Volume` and `TnIcons.PlayerStop`, and shows a CircularProgressIndicator with stop-icon overlay while the TTS model is loading (`isSpeakLoading`).
- Mic `ActionButton` always rendered. No STT installed → navigate to ModelStore. Permission missing → request. Else `startRecording()`.
- Recording crossfades the input bar to `RecordingEqualizer` (`[X cancel] [waveform] [✓ stop]`). Stop calls `stopRecordingAndTranscribe`.
- Image-attach button moved out of PlusMenu into the input bar.
- Voice errors surface through the same `VlmErrorBanner` component.
- No dedicated Voice Settings screen — Store manages downloads + first-install becomes active. Default TTS / STT swap surfaces in Settings → Voice section (`SettingsViewModel.voiceSection`); selecting a different model writes `active_tts_model` / `active_stt_model` and calls `VoiceModelManager.unloadTts/Stt()` so the next request reloads the new pick.

### DI

`VoiceModelManager`, `TtsPlayer`, `SttRecorder` are all `@Singleton`. `HomeViewModel` injects `VoiceModelManager`. `ModelStoreViewModel` injects `AppPreferences` to flag first install of each kind as active.

### Catalog (2026-04-24)

`ModelCatalog.BUILT_IN_MODELS` carries four sherpa-onnx releases: `vits-piper-en_US-amy-low` (TTS, ~30 MB), `vits-piper-en_US-libritts-high` (TTS, ~124 MB), `sherpa-onnx-whisper-tiny-en` (STT, ~75 MB), `sherpa-onnx-whisper-tiny` (STT, ~82 MB). URLs hit `sherpa-onnx/releases/download/{tts,asr}-models/…`. If sherpa-onnx restructures their releases, the Store surfaces the failure.

---

## Remote Server

Embedded HTTP server exposing every installed engine over an OpenAI-compatible API on the local network. Standalone replacement for the rejected Ktor PR. As of 2026-05-11 the server is multi-engine: chat GGUF, VLM (chat + images), embeddings, TTS, STT, and image generation (txt2img / img2img / inpaint / 4x upscale). No TLS, no mDNS, no outbound calls.

### Process model

Three processes:

- `:app` — UI, chat ViewModels, `ServerController` (AIDL client), `InferenceClient` (AIDL client of `:inference`). HXS / Keystore live here; nothing crosses out.
- `:inference` — `InferenceService` (chat-side llama.cpp + sherpa-onnx). Untouched by the server.
- `:server` — `RemoteServerService`, its own per-type engine instances (`ServerEngine` chat, `ServerVlmEngine`, `ServerEmbeddingEngine`, `ServerTtsEngine`, `ServerSttEngine`, `ServerImageEngine`), the embedded native HTTP server, the bearer token in native memory. Foreground (`dataSync`, `stopWithTask="false"`). Independent: app crash doesn't kill it, server crash doesn't kill the app.

`:server` doesn't open HXS. The bearer token, full engines catalog (per-model id / display name / file path / mmproj path / per-model config JSON / kind), web-UI HTML, and docs HTML are all handed across via AIDL `start(configJson)`. When the user rotates the token in the UI, `:app` regenerates + persists, then pushes the new token to `:server` via `IRemoteServerService.rotateToken(newToken)`.

When the user swipes the app away, `:app` and `:inference` both die. `:server` keeps running because it's foreground **and** because `handleStart` self-calls `startService(Intent(this, RemoteServerService::class.java))` immediately before `startForeground`. That transitions it from bind-only to started lifecycle — without it, every binder client dying (which happens on swipe-to-kill) makes the service eligible for destruction even with a foreground notification. Reopening the app re-binds; `ServerController` calls `currentSnapshotJson()` and `recentRequestEventsJson(100)` to rehydrate the Server Screen with whatever's running right now.

Tapping the notification body opens `MainActivity` with `EXTRA_OPEN_SERVER_SCREEN=true`, which routes straight to the Server Screen. The Stop button on the notification fires `startService(action=ACTION_STOP)` against `:server`, which tears down in-process.

### Engine model — lazy per-type, primary preload

`ServerEngineRegistry` (in `:server`) holds one instance per kind. Each kind is loaded lazily on first request and cached:

| Kind             | Wrapper                  | Library backing                                      |
|------------------|--------------------------|------------------------------------------------------|
| `gguf`           | `ServerEngine`           | `com.dark.gguf_lib.GGMLEngine`                       |
| `vlm`            | `ServerVlmEngine`        | `GGMLEngine` + mmproj projector                      |
| `embedding`      | `ServerEmbeddingEngine`  | `com.dark.gguf_lib.EmbeddingEngine`                  |
| `tts`            | `ServerTtsEngine`        | `com.dark.ai_sherpa.OfflineTts` (VITS)               |
| `stt`            | `ServerSttEngine`        | `com.dark.ai_sherpa.OfflineRecognizer` (Whisper)     |
| `image_gen`      | `ServerImageEngine`      | `com.dark.ai_sd.StableDiffusionManager` (QNN/MNN)    |
| `image_upscaler` | `ServerImageEngine`      | same SDK, separate `loadUpscaler` path               |

Why lazy-load: a 4 GB device cannot hold every engine simultaneously. On `start(configJson)` the primary chat GGUF (or first VLM if no chat) is preloaded so first /v1/chat/completions returns fast; everything else materialises on first request to that endpoint. Per-kind `Mutex`/`synchronized` lock prevents two requests racing the load. Engine instances are NOT reaped on idle in this iteration — only `shutdownAll()` on server stop. If RAM becomes a problem on smaller devices, the natural extension is a per-kind TTL cache or a `POST /v1/admin/unload?kind=image_gen` route.

Cross-process gotchas:
- `:server` loads `StableDiffusionManager.getInstance(context)` independently from `:app`. They each have their own native pipeline. The `qnnlibs.tar.xz` runtime extraction is uid-shared at `<filesDir>/ai_sd_runtime/`; whichever process extracts first wins, the other sees the existing files and skips. Image gen via the server therefore requires the user to have downloaded the SD runtime through the Image Task screen at least once.
- `:server` does NOT open HXS to read `prefs.activeTtsModelId` / `activeSttModelId`. Voice "default" is whatever the catalog ranks first per kind — `ServerController.buildEnginesCatalog` walks the `ModelRepository` in install order, so the active voice model from `:app` doesn't influence which voice the server uses as fallback. Clients pick explicitly via the `model` field. If the request `model` is unknown, the server falls back to `first_of_kind`.
- `:inference` (the chat-side llama.cpp service) is untouched by the server — no AIDL hops between `:server` and `:inference`. Server-side load and chat-side load are independent. This is intentional: it keeps the request path on one process and prevents the server lockdown from also blocking chat-side voice/image flows on the same engine.

### Routes

| Method | Path                          | Auth | Stream | Purpose                                                 |
|--------|-------------------------------|------|--------|---------------------------------------------------------|
| GET    | `/`, `/index.html`, `/webui`  | public | -    | Bundled Material-3 web UI                                |
| GET    | `/docs`, `/docs/`             | public | -    | API documentation                                        |
| GET    | `/health`                      | public | -    | Liveness ping `{status:ok}`                              |
| GET    | `/v1/models`                   | auth | -      | Full enabled-engine catalog (id, type, owned_by)         |
| POST   | `/v1/chat/completions`         | auth | yes    | Chat GGUF; auto-routes to VLM when message contains `image_url` parts |
| POST   | `/v1/embeddings`               | auth | -      | Dense embeddings — `{input: string \| string[]}`         |
| POST   | `/v1/audio/speech`             | auth | -      | TTS — body `{model, input, voice, speed}`, returns `audio/wav` |
| POST   | `/v1/audio/transcriptions`     | auth | -      | STT — multipart `file=<wav>` + `model=<id>`              |
| POST   | `/v1/images/generations`       | auth | -      | txt2img — body `{model, prompt, negative_prompt?, steps?, cfg?, width?, height?, seed?}`, returns `{data:[{b64_json}]}` |
| POST   | `/v1/images/edits`             | auth | -      | img2img / inpaint — multipart `image`, optional `mask`, `model`, `prompt`, sampling params |
| POST   | `/v1/images/upscale`           | auth | -      | 4x upscale — multipart `image` + `model`                  |

Every non-public route is gated by the same `pre_routing_handler`: rate-limit token bucket → ban list → bearer auth (constant-time compare, 20 consecutive fails → 1 h ban). The same `post_routing_handler` records every request into the 128-entry audit ring buffer + pushes it across to `:app` via `InferenceBridge.onRequestEvent`. No exceptions, no per-route auth bypass.

### Native (`:native-server`)

```
native-server/src/main/cpp/
  server_core.{h,cpp}        — httplib::Server lifecycle, pre/post-routing, every route registration
  server_auth.{h,cpp}        — bearer token store + constant-time compare + 401/403
  server_crypto.{h,cpp}      — getrandom(2) RNG, const_time_eq, base64url, base64 std, base64 decoder, secure_zero
  server_models.{h,cpp}      — typed catalog: id + display_name + path + mmproj_path + config_json + Kind + created
                                + has_id_of_kind / first_of_kind / has_any_of_kind / build_list_response
  server_audit.{h,cpp}       — 128-entry ring buffer of request events
  server_rate_limit.{h,cpp}  — per-client token bucket (cap=30, refill=1/s) + auth-fail ban (20 fails → 1 h)
  server_webui.{h,cpp}       — set/clear/get/has HTML (mutex-protected std::string)
  server_docs.{h,cpp}        — same for /docs
  server_staging.{h,cpp}     — tmpfile dir for large binary payloads; staged paths handed across JNI (no byte[] copies)
  wav_codec.{h,cpp}          — minimal RIFF/PCM16 + IEEE-float decode (STT input); encode is on the Kotlin side
  gen_session.{h,cpp}        — chat / VLM streaming session: token queue + cancellation
  reply_session.{h,cpp}      — single-shot reply session for embeddings / TTS / STT / image; carries text or staged binary path
  openai_schema.{h,cpp}      — ChatRequest parser (detects has_images), VLM image part extractor (base64 data URLs ONLY),
                                error envelope, embedding response, transcription response, image response builders
  jvm_bridge.{h,cpp}         — JavaVM pin, JNI upcalls (startGeneration + startEmbedding + startTts + startStt
                                + startImageGen + startImageUpscale + cancelGeneration + onRequestEvent)
  native_server.cpp          — JNI entry points (start/stop/token/catalog/bridge/feeders/staging/audit/rl/webui/docs) + JNI_OnLoad
```

CMake fetches `cpp-httplib v0.18.5` and `nlohmann/json v3.11.3`, both header-only (`HTTPLIB_COMPILE=OFF`, `HTTPLIB_REQUIRE_OPENSSL=OFF`, `HTTPLIB_REQUIRE_ZLIB=OFF`, `JSON_BuildTests=OFF`). Same flags as `:hxs_encryptor`: c++17, `-fvisibility=hidden`, `-fstack-protector-strong`, LTO/gc-sections/icf release, `-march=armv8-a+crypto+sha2` on arm64, `-Wl,-z,max-page-size=16384`. Read timeout was lifted from 15s → 60s and payload max from 1 MB → 64 MB to accommodate base64-encoded VLM images and multipart audio/image uploads.

Payload mechanics:
- **Streaming** (chat / VLM): `gen_session` queue with `nativeFeedToken / nativeFeedDone / nativeFeedError`; the httplib chunked content provider drains it onto an SSE stream.
- **Single-response** (embeddings / TTS / STT / image): `reply_session` — bridge calls `nativeFeedReplyText(replyId, body, mime)` for JSON/text or `nativeFeedReplyBinary(replyId, path, mime)` for staged binary; the route handler blocks on `session->wait(timeout)`.
- **Big binary upload** (multipart image, mask, wav): cpp-httplib decodes multipart natively; the route writes each part to `<cacheDir>/server-staging/tn_<rand>_<name>` via `server_staging::write_bytes`, hands the path to Java via JNI string (avoids byte[] JNI copies), and unlinks on response.
- **Big binary download** (TTS wav, generated PNG): Kotlin writes the bytes to the staged path, hands the path back via `nativeFeedReplyBinary(path, mime)`, the C++ side reads + sends + unlinks. PNG responses are base64-encoded into JSON `b64_json` per OpenAI; WAV is sent as raw `audio/wav`.
- **VLM image_url parts**: only `data:image/...;base64,...` URLs are accepted. Network URLs return 400 (offline-only scope). Decoded bytes are staged to tmpfiles and the paths passed to `InferenceBridge.startGeneration(..., imagePaths=[...])`. Sanitised messages (image parts collapsed into text-only `content`) are forwarded to the engine alongside the path list — the Kotlin bridge reads each tmpfile and feeds the bytes to `GGMLEngine.generateVlmFlow(imageData = [...])`.

### Web UI

Bundled at `app/src/main/assets/server_webui.html`. Single Material-3 SPA with a sidebar tab strip that swaps the main panel between four workspaces:

- **Chat** — preserved from the prior build: localStorage history, markdown rendering, streaming with blinking cursor, settings dialog, connection indicator. Adds an attach-image button (📎) that converts the uploaded image to a `data:image/...;base64,...` URL and appends it as an OpenAI multi-part `image_url` content entry on the next send. Server auto-detects and routes to the VLM engine.
- **Embeddings** — model select, multi-line input (one row per line), runs `/v1/embeddings`, shows vector count + first 8 dims of each row.
- **Voice** — two cards. TTS: model + text + voice id + speed, plays the returned WAV inline. STT: model + WAV upload, shows transcribed text.
- **Image** — segmented switch (Generate / Edit / Inpaint / Upscale). Prompt + negative + steps/CFG/width/height for diffusion modes. Input image file for Edit/Inpaint/Upscale. Mask file for Inpaint. Result is rendered inline from `b64_json`.

`refreshModelCache()` hits `/v1/models` once per tab activation and filters per-kind for the model dropdowns. JNI: `nativeSetWebUiHtml(html)` pushes the bundled file at server start; `nativeClearWebUi()` clears on stop. Same applies to `/docs` via `nativeSetDocsHtml` + `app/src/main/assets/server_docs.html`. The docs file documents every endpoint with copy-pasteable curl examples.

### Start config (`configJson`) schema

JSON object passed to `IRemoteServerService.start(configJson)`. Built by `ServerController.start()` from `:app`:

```json
{
  "token":     "tn_sk_<base64url>",
  "port":      11434,
  "bindMode":  "ALL_INTERFACES | LOOPBACK_ONLY | WIFI_ONLY",
  "webUiHtml": "<bundled assets/server_webui.html>",
  "docsHtml":  "<bundled assets/server_docs.html>",
  "engines":   [
    { "id":"...", "name":"...", "path":"<abs file path>", "type":"gguf|vlm|embedding|tts|stt|image_gen|image_upscaler",
      "mmproj_path":"<vlm only, optional>", "config_json":"{...}", "created":1715000000, "primary":true|false }
  ]
}
```

`engines` is built by walking the entire installed `ModelRepository` and mapping `ProviderType` → engine kind. GGUF chat models living under `<modelsDir>/vlm/` with a colocated `*mmproj*.gguf` are auto-classified as `vlm`. `config_json` merges the per-model `loadingParamsJson` + `inferenceParamsJson` from `ModelConfig`. URI-pathType models are skipped — the server only supports `FILE` paths because there's no clean way to trampoline content URIs across the `:server` process boundary.

### `:server`-side Kotlin (in `app/src/main/java/com/dark/tool_neuron/service/server/`, runs in `:server` process)

- `RemoteServerService.kt` — plain `Service`, NOT `@AndroidEntryPoint`. Holds the `ServerEngineRegistry`, the `ServerInferenceBridge`, and a `RemoteCallbackList<IRemoteServerCallback>`. Implements `IRemoteServerService.Stub` inline. Foreground promotion happens *inside* the AIDL `start(configJson)` call — parses the catalog, sets the staging dir, calls `registry.setCatalog`, preloads the primary chat (or first VLM if no chat), configures + starts the native HTTP server, publishes a `ServerSnapshot` to all callbacks. `onStartCommand` only handles `ACTION_STOP` (the notification's Stop button). `onCreate` calls `nativeSetStagingDir(<cacheDir>/server-staging/)` so the cleanup path is wired even before a `start`.
- `ServerEngine.kt` — wraps `GGMLEngine` for chat GGUF. `load(modelId, path, configJson)` (carries the id so the registry can decide reload vs. reuse), `unload()`, `generateMultiTurnFlow(...)`, `setSampling`, `setSystemPrompt`, `stopGeneration`. Same JSON shape `InferenceService` parses for chat (contextSize, threadMode, flashAttn, cacheTypeK/V, sampling, kvSink/Window/Evict).
- `ServerVlmEngine.kt` — separate `GGMLEngine` instance. `ensureLoaded(modelId, basePath, mmprojPath, configJson)` releases any prior projector, loads the base GGUF, then auto-loads the mmproj (preferring the explicit path; falling back to colocated `*mmproj*.gguf`). `generateFlow(messagesJson, imageBytes, maxTokens)` dispatches to `GGMLEngine.generateVlmFlow(imageData=..., imageQuality=HIGH)`.
- `ServerEmbeddingEngine.kt` — wraps `EmbeddingEngine`. `ensureLoaded(modelId, path, configJson)` calls `engine.load(path, threads, contextSize)`. Exposes `embedBatch(texts, normalize=true)` — the bridge JSON-encodes the result for `nativeFeedReplyText`.
- `ServerTtsEngine.kt` — wraps sherpa-onnx `OfflineTts` (VITS only). `ensureLoaded` builds `OfflineTtsConfig` from the model config JSON; `synthesize(text, speakerId, speed)` returns mono float samples; `sampleRate()` exposes the codec rate so the WAV encoder writes the right header.
- `ServerSttEngine.kt` — wraps sherpa-onnx `OfflineRecognizer` (Whisper only). `recognize(samples, sampleRate)` returns the transcribed text or null on failure.
- `ServerImageEngine.kt` — wraps `StableDiffusionManager`. `ensureRuntime()` is gated on `<filesDir>/ai_sd_runtime/qnnlibs.tar.xz` existing (downloaded by `:app`-side `ImageGenManager`). `loadDiffusion(id, name, path, width, height)` walks the model dir, builds `DiffusionModelConfig`, and calls `sdk.loadModel`. `loadUpscaler(id, path)` toggles MNN vs. OpenCL based on filename. `generate(params)` is `sdk.generateImageSync(...)` (blocks until result). `upscale(bitmap)` posts the bitmap and `.first {}`s on `upscaleState` for Complete/Error. PNG encoding/decoding lives here too.
- `ServerEngineRegistry.kt` — single source of truth for catalog + lazy loading. `chatFor`, `vlmFor`, `embedFor`, `ttsFor`, `sttFor`, `imageGenFor(width, height)`, `upscalerFor`. Each method picks the entry by id or falls back to `firstOf(kind)`. Per-kind locks (`Mutex` for suspend, plain `Object` for sherpa's synchronous load) serialise concurrent loads.
- `ServerInferenceBridge.kt` — extends `InferenceBridge`. Each upcall (`startGeneration` / `startEmbedding` / `startTts` / `startStt` / `startImageGen` / `startImageUpscale`) launches a coroutine on a `SupervisorJob` IO scope, calls the right registry method, dispatches to the engine, and feeds the result back via `NativeServer.nativeFeed*`. Chat + VLM streaming uses the existing `nativeFeedToken / nativeFeedDone / nativeFeedError` triplet; everything else uses the single-shot `nativeFeedReplyText / nativeFeedReplyBinary / nativeFeedReplyError`. VLM marker is prefixed onto the last user message via `engine.defaultMarker()` (= `engine.getVlmDefaultMarker()` in the Kotlin call site).
- `ServerCatalog.kt` — typed catalog model + `ServerEngineKind` enum + JSON serializer matching the C++ `set_catalog_json` parser.
- `ServerWavCodec.kt` — Kotlin-side RIFF reader/writer used by TTS (encode floats → wav) and STT (decode wav → floats). Mirrors the native helper.
- `ServerSnapshot` (in `RemoteServerService.kt`, internal) — phase / modelId / modelName / host / displayHost / lanHost / port / bindModeName / wifiActive / reason. Serialised to JSON for cross-process shipping. `modelId / modelName` reflect the **primary** engine (chat GGUF or first VLM) — the snapshot doesn't enumerate every loaded engine.
- `BindResolver.kt`, `ServerTypes.kt` — unchanged.

### `:app`-side Kotlin

- `ServerController.kt` — `@Singleton`, AIDL client. `start()` walks `ModelRepository.models.value`, builds the full multi-engine catalog (one JSON entry per installed model), packages with token / port / bind mode / web-UI HTML / docs HTML, calls `IRemoteServerService.start(configJson)`. URI-pathType models are silently skipped. The "selected chat model" pref still exists but is now only used to mark a `"primary": true` flag inside the engines array — if it's unset or invalid the first installed chat GGUF wins. Start enables as long as **any** engine is installed (was: required a chat model). `stop()` and `rotateToken()` forward via AIDL.
- `viewmodel/ServerViewModel.kt` — adds `anyEngineInstalled: StateFlow<Boolean>`. Chat-model selector card stays; selecting only seeds the `primary` flag.
- `ui/screens/server/ServerScreen.kt` — Start button enabled if any engine is installed OR a chat is selected.

### State sync / process-survival semantics

Unchanged from the single-engine build. `:app` calls `bindService(intent, conn, BIND_AUTO_CREATE)`. Android starts `:server`. AIDL stub returned. App registers callback, reads `currentSnapshotJson()` to rehydrate. App-killed-but-server-running: re-launching the app re-binds; `currentSnapshotJson()` returns `phase=running` with all live fields, plus `recentRequestEventsJson(100)` for the log card. Server foregrounds *only* during AIDL `start`, not on `bindService` alone — so a brief "exists, idle, no notification" state is impossible (we never enter it).

### Lockdown

`ScaffoldViewModel.serverRunning: StateFlow<Boolean>` derived from `ServerController.state`. `AppScaffold` `LaunchedEffect(serverRunning, currentRoute)` re-routes to `ServerScreen` with `popUpTo(0) { inclusive = true }` when running and not already there. `BackHandler(running) {}` inside ServerScreen absorbs back. Drawer gesture hidden via `showDrawer = currentRoute == HomeScreen.route && !serverRunning`. Chat-side load/unload + sendMessage in `HomeViewModel` are gated on `!serverController.isBusy`; same for `ModelStoreViewModel.downloadPack / downloadModel / setActive`. Because the lockdown is UI-routing rather than per-VM gating, the Image Task / Voice screens are inherently unreachable while server is running — no per-VM gate needed there. The server owns whatever model state it has loaded for its current request; chat-side reload would have nothing to clobber anyway because they're separate engine instances in separate processes.

### Auth + token lifecycle

- `ensureToken()` calls `nativeGenerateToken()` → `tn_sk_` + 32 random bytes base64url-encoded (getrandom(2)).
- Stored plaintext in encrypted HXS vault (`AppPreferences.serverToken`).
- Handed to native at start (`nativeSetToken`); zeroed on stop (`nativeClearToken`).
- 20 consecutive auth-fails from same client_addr → 1 h ban.
- Reveal in UI gates on `session.isAllowed(AUTH_VERIFY)`. Rotate generates a new token + invalidates the old.

### HXS server keys

`server_token` (String), `server_port` (String, validated [1024..65535], default `11434`), `server_bind_mode` (String, default `ALL_INTERFACES`), `server_auto_start` (Boolean, reserved), `server_configured` (Boolean), `server_selected_model` (String — primary chat hint only as of multi-engine). All ride the same encrypted `app_prefs` vault.

### Deliberate omissions

HTTPS / TLS, mDNS / Bonjour, QR-pairing, dynamic-model-load over the wire, streaming usage metrics, request-log persistence to HXS, network-URL image fetching (offline-only). Audio transcoding — `/v1/audio/transcriptions` accepts WAV PCM (16-bit or 32-bit float) only; MP3/AAC etc. return a generic decode failure. Per-engine RAM accounting — engines stay loaded until server stop; no idle TTL. Image-gen progress streaming — `/v1/images/generations` is single-response (uses `generateImageSync`); the live diffusion intermediate previews available in the in-app Image Task screen are not exposed.

---

## HF Explorer

Rewritten 2026-04-29. All HF traffic flows through `:networking` (curl-impersonate Chrome116 + bundled CA bundle); the previous `HttpURLConnection` path is gone. Filter chips are populated dynamically from `/api/models-tags-by-type`; the README on the detail screen renders client-side from `/{author}/{repo}/raw/main/README.md`.

### Layers

- `repo/HuggingFaceApi.kt` (Hilt `@Singleton class`) — URL builders + thin HTTP layer. Methods: `fetchJson(url): Result<JSONObject>`, `fetchJsonArray(url): Result<JSONArray>`, `fetchRaw(url): Result<String>`, `probe(url): Result<Int>`. All go through `WebNative.fetch` with `Accept: application/json` and `Accept-Encoding: gzip`. URL builders: `modelInfoUrl`, `modelTreeUrl`, `resolveFileUrl`, `rawFileUrl`, `searchUrl`, `quickSearchUrl`, `trendingUrl`, `tagsByTypeUrl`. **Failures are typed via `HfApiError`** (`RateLimited(retryAfterSeconds)`, `NotFound`, `Forbidden`, `Network`, `Parse`, `Http`).
- `repo/hf/HfClient.kt` (Hilt `@Singleton`) — typed explorer endpoints over `HuggingFaceApi`. `searchModels`, `quickSearch`, `trending`, `modelDetail`, `readme`, `tagsCatalog` (cached 24h in encrypted `app_prefs` under keys `hf_tags_catalog_v1` + `hf_tags_catalog_v1_at`).
- `repo/hf/HfModels.kt` — `HfModelSummary`, `HfModelDetail` (with `HfSibling`/`HfGgufMeta`/`HfCardData`), `HfTrendingItem`, `HfQuickResult`, `HfTagsCatalog`/`HfTagEntry`, `HfGated` enum (OPEN/GATED/AUTO).
- `repo/hf/HfJsonParse.kt` — internal `org.json` parsers for each shape.
- `repo/HuggingFaceExplorer.kt` — kept as a thin compat wrapper exposing `searchModels` / `searchGgufRepos` / `fetchRepoDetail` mapped to legacy `ExplorerRepo` / `HfRepoDetail` types for `ModelStoreViewModel`.

`ModelCatalog` and `RepositoryValidator` inject `HuggingFaceApi` directly. They no longer touch `HttpURLConnection`.

### VM (`viewmodel/HfExplorerViewModel.kt`)

State flows: `query`, `filters: HfFilters`, `results: List<HfModelSummary>`, `isSearching`, `searchError: HfApiError?`, `tagsCatalog`, `trending`, `history`, `hideAdded`, `detailState`, `fileFilter`, `fileSizeBucket`, `existingRepoPaths`. On VM init: kicks off `tagsCatalog()` and `trending(12)` once each (cached).

**Search trigger policy** (intentional, rate-limit conservative):
- IME action / Search button → fires `client.searchModels`.
- Any chip toggle / sort change / param-range slider release → fires fresh search via `updateAndSearch`.
- Per-keystroke quicksearch is **deliberately not wired**.

### Filter set (intentionally minimal, 2026-04-29)

`HfFilters` carries only fields that map to documented HF list params: `libraries: Set<String>` (default `{"gguf"}`, multiple `filter=…`), `author: String` (`author=…`), `gated: GatedFilter` (`gated=true|false`), `paramsMinMillions/Max` (`num_parameters=min:7B,max:13B`), `sort: HfSort`. The previous "kitchen sink" filters (apps, inference_provider, languages, licenses, regions, other-tags, quant chips, trained-dataset, pipeline-tag, inference-warm) and post-filter sliders (min-downloads, min-likes, recent-days) were dropped because (a) HF rejected the speculative URL params with HTTP 400, and (b) the heavy filter UI added clutter without unlocking working searches. Tags catalog still fetched + cached for future use; just not wired to chip rows yet.

### Screens

- `ui/screens/hf_explorer/HfExplorerScreen.kt` — search hero (TnTextField + ActionButton submit), history strip when empty, sort row, gated/hide-added quick toggles, collapsible Filters card with: param-range slider, library chips (GGUF/Transformers/Safetensors/ONNX/MLX/Diffusers), author text. Trending strip when results empty + query empty. Result cards with author-initials avatar, downloads/likes/pipeline pills, tag chips, Gated badge variants (Gated / Gated · auto), Add/Added trailing icon. Errors render via `ErrorBanner` with rate-limit aware copy.
- `ui/screens/hf_explorer/HfRepoDetailScreen.kt` — `HeaderCard` with stats + gated badge; `GatedNotice` block when gated (license prompt preview + sign-in CTA); `GgufCard` (architecture, context, total bytes, BOS/EOS) when GGUF; `CardDataView` (license, base model, languages, task, tag chips); file filter pills + file rows; **README rendered via `lazyMarkdownItems`** from raw markdown; failure view distinguishes rate-limit / not-found / forbidden / network / parse / http.

### Tags catalog cache

Sealed in encrypted `app_prefs` (under existing `tn.app_prefs.user_key.v2` HKDF key). Plaintext JSON never lands on disk. 24h TTL; `forceRefresh = true` bypasses. On every cold start the first explorer open hydrates from the cache; if expired or empty, hits `/api/models-tags-by-type` once and re-persists.

---

## RAG attachments

The Action Window's third tab is **Attach** (formerly Tools). It shows the current chat's attachments and a single full-width "Add attachment" button. Tapping it opens `AttachmentPickerDialog` with two paths:

- **Pick from previous chats** — opens `PrevChatsPickerDialog`, a full-screen `Dialog` with a list grouped by source chat title. Tapping a row re-attaches the document to the active chat.
- **Pick from storage** — launches `ActivityResultContracts.OpenDocument` with the existing MIME filter (text/*, pdf, json, xml, rtf, epub, odt, docx, pptx, xlsx).

### Persistence model

Every attached document is stored content-addressed by SHA-256 of its bytes:

- `<filesDir>/chat_documents/sources/<sourceId>.bin` — raw bytes, written once per unique content; multiple chats sharing the same content share the file.
- `<filesDir>/chat_documents_meta_v1/` — **encrypted** HXS collection holding `(id, chatId, sourceId, name, mimeType, chunkCount, sizeBytes, addedAt)`. Sealed under `HKDF(DEK, "tn.chat_documents.user_key.v1")`. `DocumentRepository.init` migrates legacy plaintext at `chat_documents/` (top-level files) into the encrypted vault on first launch. `sources/` subdirectory is preserved during migration.
- `<filesDir>/rag_keyword_v1/` — **native HXS-encrypted** keyword index for hybrid retrieval. Sealed under `HKDF(DEK, "tn.rag_keyword.user_key.v1")`. Tokenization, inverted index construction, BM25 scoring all live in C++ (`hxs/src/main/cpp/rag_keyword.{h,cpp}`); only the wrapper class is in Kotlin. Inverted index is rebuilt in-RAM on every process start by scanning the HXS records (bounded by # of chunks).
- `chat_documents` HXS collection — same TAG layout as before: `(1=id, 2=chatId, 3=name, 4=mimeType, 5=chunkCount, 6=sizeBytes, 7=addedAt, 8=sourceId)`. Persisted across restarts. **Do not** call `documentRepo.clearAll()` from `RagManager.init` — that's the previous (wrong) behavior that wiped doc history every boot.
- `id` is the compound `<chatId>:<sourceId>`. Same content attached to two chats produces two records sharing one `sourceId.bin` blob.

`RagManager.hydrateChat(chatId)` re-ingests persisted records into the live RAG engine on chat-open (the engine itself is rebuilt fresh per process). It tracks `ingestedDocIds: MutableSet<String>` to avoid duplicate ingests; the set clears on `engine.close()`. Hydration also re-populates the FTS5 BM25 index for text-format documents (idempotent — `keywordIndex.docCount(docId) > 0` check skips already-indexed).

`RagManager.attachExisting(currentChatId, source)` is the prev-chat re-attach: builds the new compound docId, re-reads `<sourceId>.bin`, calls `engine.ingestBytes(...)`, persists the new record. Idempotent — if the chat already has the same `sourceId`, returns the existing record.

`RagManager.removeDocument(docId)` removes the chunks from the engine + the FTS5 keyword rows + record from HXS, and deletes `<sourceId>.bin` only when no other record references that sourceId (`documentRepo.countWithSource(sourceId) == 0`).

### Hybrid retrieval (dense + BM25 + RRF)

`RagManager.augment(chatId, query, originalPrompt, maxContextTokens)` returns `RagAugmentation(augmentedPrompt, chunks)`:

1. **Optional multi-query** — if `appPrefs.ragMultiQuery`, `RagQueryRewriter` asks the loaded chat model to generate 3 alternative phrasings of the user's query. Falls back to single-query if the model isn't loaded or the rewriter times out (8s).
2. **Per-query retrieval** — for each query (original + variants), runs the dense engine `engine.query(q)` (capped at `topN = DENSE_CANDIDATES = 20`) AND `RagKeywordIndex.query(q, chatId, KEYWORD_CANDIDATES = 20)` BM25 lookup against the FTS5 index.
3. **RRF fusion** — `rrfFuseMany` over all 2-N rankings (`k = 60`, identity = `(docId, chunkIndex)` pair). Items appearing in multiple rankings get summed RRF scores; items in only one ranking still score. Returns `FUSED_POOL_SIZE = 12` candidates.
4. **Optional LLM rerank** — if `appPrefs.ragSmartRerank`, `RagReranker` asks the loaded chat model to score each pooled chunk 1–5 against the query (single LLM call, 15s timeout, 256 max tokens). Returns reordered list. Falls back to RRF order if the model isn't loaded or scoring fails.
5. **Token budget** — `InferenceCoordinator.computeRagBudget(messages)` derives `contextSize - maxTokens - approxHistoryTokens - 256` (clamped to 256–4096). `RagManager.buildAugmentedPrompt` walks ranked chunks in order, summing approx tokens (chars/4), keeping until budget exhausted. Truncates the first chunk if it alone exceeds budget. Caps at `FINAL_TOP_N = 8` chunks.
6. **Citation contract** — the prompt instructs the LLM to cite chunks inline as `[1]`, `[2]`, etc. After generation, `RagCitationMatcher.match(response, chunks)` parses explicit `[N]` markers AND runs a 4-gram overlap check (≥3 hits = cited). Resulting `List<Citation>` is stored on the assistant `ChatMessage` via `ChatRepository.TAG_MSG_CITATIONS = 13` (JSON array). UI: `CitationStrip` renders chip per citation below the message bubble; tap opens an `AlertDialog` with the snippet, doc name, score, and cited/possibly-used label.

`RagKeywordIndex` is now native — backed by `hxs::RagKeywordIndex` in `hxs/src/main/cpp/rag_keyword.{h,cpp}`. Per-chunk records are stored in HXS-encrypted collection `rag_chunks` with TAG layout `(1=docId, 2=chatId, 3=sourceId, 4=chunkIndex, 5=text)`. The C++ side maintains an in-memory `unordered_map<term, vector<Posting{record_id, term_freq}>>` rebuilt at construction by scanning the encrypted records. Tokenizer is ASCII alphanumeric + underscore + UTF-8 bytes ≥0x80 passthrough, lowercased, length 2-64. BM25 params k1=1.2, b=0.75. JNI surface: `nativeRagIngest`, `nativeRagQuery`, `nativeRagRemoveDocument`, `nativeRagClear`, `nativeRagDocCount`. Replaces the prior SQLite FTS5 implementation, which broke on devices with stripped SQLite (no `fts5` module) and lived plaintext at rest.

**FTS5 limitation:** only text-format documents are indexed. The native engine doesn't expose extracted text back to Kotlin (#329 is blocked-native), so binary formats (PDF/DOCX/EPUB/etc.) bypass BM25 — they only get dense retrieval. `RagManager.isTextLike(mime, name)` decides via mime-prefix `text/`, `application/{json,xml,rtf,javascript,yaml}`, or extensions `txt|md|markdown|json|xml|csv|tsv|html|htm|rtf|yaml|yml|log|ini|toml|properties|kt|java|py|js|ts`.

`RagChunker` does Kotlin-side recursive splitting for the FTS5 path (target 1024 chars, min 200, separators in priority `\n\n / \n / . / ! / ? / ; / , / space`). The native engine's chunking is independent — chunk indices from FTS5 do not align with native engine indices. RRF treats them as separate items by `(docId, chunkIndex)` identity, which is fine.

### Deep Index (contextual retrieval, simplified)

Per attached document, a "Deep Index" sparkles-icon affordance in the Attach tab triggers `RagManager.deepIndex(docId)`. The flow:
1. Read source bytes from `chat_documents/sources/<sha256>.bin` (text-format docs only — `RagManager.isTextLike` mime/extension gate).
2. `RagDocSummarizer` asks the loaded chat model to write a one-sentence document summary (≤320 chars, 30 s timeout, 200 max tokens). One LLM call per document.
3. `RagChunker` splits the source text into ~1024-char Kotlin-side chunks.
4. For each chunk, prepend `[Document context: <name> — <summary>]` and re-ingest into the dense engine + BM25 index using compound docId `${origDocId}::ctx<idx>`. The native engine internally re-chunks the (context + chunk) blob into multiple sub-chunks, each carrying the doc context. The original doc remains untouched.
5. `ChatDocument.isDeepIndexed = true` is persisted (TAG 9 on the chat_documents collection); the UI shows a "Deep" badge next to the filename.
6. `RagManager.deepIndexing: StateFlow<Set<String>>` exposes the in-flight set so the UI can show a spinner per row.

Inflation factor: ~Nx storage per deep-indexed doc, where N = (Kotlin chunk size + summary length) / native chunk size. For 1024-char Kotlin chunks + native chunk_size=256, ~5x more native chunks per doc.

Augment-side change: `RagManager.augment` strips `::ctx<n>` suffixes when looking up the parent ChatDocument so citations group under the original doc, not its context-pseudo-children.

Cleanup: `RagManager.removeDocument` recurses through `ingestedDocIds` removing every `${origDocId}::ctx*` from the engine + BM25 index before deleting the parent record.

Limitations: text-format docs only (PDFs/DOCX blocked by no native extract API). One doc-level summary per doc, NOT per-chunk Anthropic-style — simpler v1, marginally lower quality than per-chunk contextual retrieval but ~1 LLM call vs. N. Idempotent: skipped if already deep-indexed.

### Retrieval debug screen

`Settings → Chat & RAG → Retrieval debug` opens `RagDebugScreen` (route `NavScreens.RagDebug`). VM is `RagDebugViewModel` (injects `RagManager` + `ChatRepository`). Renders:

- Status pill (ready + active embedding name).
- Chat dropdown to scope the test query.
- Query text field + Run button.
- Tabs: Fused (RRF result), Dense (raw native), BM25 (raw FTS5), Context (final assembled `<context>` block + token count), Engine (raw `engine.info()` JSON).
- Each hit card shows chunkIndex, score, docId, first 600 chars of text.

Backed by `RagManager.debugQuery(chatId, query, budget)` which returns `RagDebugResult`. Multi-query is NOT applied in the debug path (single-query for clarity).

### Extension badge

`model/DocExtension.kt` enum maps mime + filename to a `(label, tint)` pair (PDF/DOCX/XLSX/PPTX/ODT/EPUB/RTF/MD/HTML/JSON/XML/CSV/TXT/OTHER). `ExtensionBadge` in `ui/components/action_window/Attachments.kt` renders a rounded card with the label centered, tinted from the entry's color. Used in the Attach tab and the prev-chats picker.

### PlusMenu cleanup

The PlusMenu's old "Documents" button is gone — attachments live entirely in the Attach tab now. PlusMenu shows only Thinking when `supportsThinking`; if not supported, `PlusMenuCard` returns null.

---

## Web search

Replaces the prior Research pipeline (2026-05-15). Single-shot LLM-driven web search. User flips the Web Search toggle on the bottom action bar (or types `/search <query>`); next chat send becomes a web-search run.

Flow (`viewmodel/WebSearchCoordinator.kt`):

1. **Plan** — coordinator emits `WebSearchEvent.Plan(userQuery)` so the card renders immediately.
2. **GenerateQueries** — one LLM call (`WebSearchPrompts.generateQueries`) asking for exactly 3 numbered queries. Regex-parsed via `QUERY_LINE_REGEX = ^\s*(?:\d+[.)\-:]|[-*•])\s+(.+)$`. Failures fall through to `WebSearchEvent.Failed`.
3. **Search** — for each of the 3 queries, `WebSearcher.search(query, maxResults=5, idx)` via `WebNative.search` (DDG HTML). Per-query results are deduped against a session-wide `seenUrls` set so cross-query overlap doesn't double-feed the synthesizer. Total cap: 3 queries × 5 results = 15 unique snippets.
4. **Synthesize** — one LLM call (`WebSearchPrompts.synthesize`) with the user query + numbered `[i]` snippet list. Output is markdown with inline `[1]/[2]/[3]` citations and a trailing Sources section.
5. **Done** — emits `WebSearchEvent.Done(answer, sources)`; card renders the markdown answer + collapsible tappable source list (chip → `LocalUriHandler.openUri`).

No URL fetching. No document extraction. No iteration loop. The user-visible difference vs. old Research: seconds instead of minutes, single inline result instead of a "research document" archive screen.

### Persistence

State rides entirely on the chat message via `webSearchRunId: String?` (`TAG_MSG_WEBSEARCH_RUN = 14`) and `webSearchState: String` (`TAG_MSG_WEBSEARCH_STATE = 15`, JSON-serialized `WebSearchUiState`). `ChatMessageList` renders `WebSearchCard` instead of `MessageBubble` when `webSearchRunId != null`. Done runs survive process restart because the terminal state is on the message. No separate vault, no Documents archive, no DocumentViewer screen.

`HomeViewModel.handleWebSearchEvent` looks up `(chatId, messageId)` via `webSearchMessages[runId]`, applies the event to the persisted state, and writes the updated message back. The map evicts on Done/Cancelled/Failed.

### Lockdown

Same pattern as the old research lockdown — `webSearchActive: StateFlow<Boolean>` is derived from `webSearchCoordinator.activeRuns.isNotEmpty()`. `sendMessage`, `loadModel`, and `unloadModel` all early-return while a run is active because the chat LLM is borrowed for both the GenerateQueries and Synthesize calls.

### Card UI

`ui/screens/web_search/WebSearchCard.kt` is a single Surface with:
- Header (Globe icon, "Web search", user query)
- Queries strip (3 rows with per-query progress indicators — Circle / spinner / Check + hit count)
- AnimatedContent for current phase (Plan / Queries / Search / Synthesize / Done / Cancelled / Failed)
- Stop button while in flight
- For Done: markdown answer + collapsible `N sources` accordion with `[i]` chips opening URLs externally

### File map

- `model/WebSearchEvent.kt` — sealed event class + `WebSearchHit` data class.
- `model/WebSearchUiState.kt` — phase machine + JSON serde.
- `repo/web_search/WebSearcher.kt` — thin wrapper over `WebNative.search`.
- `repo/web_search/WebSearchPrompts.kt` — prompt templates + `QUERY_LINE_REGEX`.
- `viewmodel/WebSearchCoordinator.kt` — single coordinator (no repository, stateless across runs).
- `ui/screens/web_search/WebSearchCard.kt` — the only UI.
- Modified: `ChatMessage` (+ webSearchRunId, webSearchState), `ChatRepository` (TAG 14/15 renamed), `HomeViewModel` (toggle, slash parse, coordinator wiring, event mirror), `HomeScreen{Body,BottomBar}` + `ToolsPickerWindow` (Web search toggle), `ChatMessageList` (WebSearchCard render).

---

## Image generation (`:ai_sd` AAR)

Re-pivoted into scope on 2026-05-08. Drop-in port of LocalDream's catalog (xororz/sd-qnn + xororz/sd-mnn + xororz/sdxl-qnn + xororz/upscaler) onto the existing TN model store. Four user-facing tasks: **Generate (txt2img)**, **Edit (img2img)**, **Inpaint**, **Upscale 4×**. Tasks #5–#8 from the SDK's surface (LaMa fast removal, MobileSAM segmentation, depth, AdaIN style transfer) are implemented in the AAR's C++ but not yet bound through JNI — out of scope until the bindings ship.

### SoC bucket policy (mirrors LocalDream)

`data/SocBucket.kt` reads `Build.SOC_MODEL` (API ≥ 31; pre-S falls back to `"CPU"` and the user only sees CPU-bucket models). `chipsetModelSuffixes` maps known Snapdragons to one of three buckets:

```
SM8475, SM8450                                                         → "8gen1"
SM8550, SM8550P, QCS8550, QCM8550, SM8650, SM8650P, SM8750, SM8750P,
SM8850, SM8850P, SM8735, SM8845                                        → "8gen2"   (also covers 8 Gen 3 / Elite / Elite Gen 5)
any other SM*                                                          → "min"
non-Qualcomm                                                           → null      (CPU-only)
```

`isSdxlCapable(soc)` is a stricter predicate: only `{SM8650, SM8845, SM8750, SM8750P, SM8850, SM8850P}` get the SDXL rows. SDXL contexts are baked at a single `_8gen3.zip` variant (no per-bucket file).

### Catalog wiring

`ModelCatalog.imageModels()` is computed per-call (not in `BUILT_IN_MODELS` const) so the `Build.SOC_MODEL` read picks up cleanly. When a Snapdragon bucket is available it emits 5 SD 1.5 NPU rows (AnythingV5, QteaMix, AbsoluteReality, CuteYukiMix, ChilloutMix), the 2 SDXL rows (gated on `isSdxlCapable`), and 2 upscaler rows (Real-ESRGAN x4 anime + UltraSharp v2 Lite). On non-Snapdragon devices it instead emits 5 SD 1.5 CPU/MNN rows from `xororz/sd-mnn`. `qnn2.28` is baked into the URL as the SDK version token; if the AAR ever upgrades to `qnn2.30` both the URL constant and the `v3` upgrade marker need to bump together.

`HuggingFaceModel` carries new image-gen fields (`isSdxl`, `requiresNpu`, `isUpscaler`, `featureLabel`, `defaultPrompt`, `defaultNegativePrompt`, `generationSize`); `modelType ∈ {"image_gen", "image_upscaler"}` switches the download finalize path. `ProviderType.IMAGE_GEN` and `ProviderType.IMAGE_UPSCALER` are the canonical categories on `ModelInfo` after install.

### Download + extraction

`ModelStoreViewModel.finalizeImageGenDownload` extracts the QNN/MNN ZIP into `<filesDir>/sd_models/<id>/` via `java.util.zip.ZipFile` with a hardened path-traversal check (entry's canonical path must start with the target's canonical path + `File.separator`). Archive deleted after extraction, `ModelInfo` inserted with `path` = the dir. `finalizeImageUpscalerDownload` is simpler: the upscaler is a single `.bin` file at `<filesDir>/sd_upscalers/<id>/upscaler_<bucket>.bin`, no extraction.

### Runtime singleton

`repo/ImageGenManager.kt` is the Hilt `@Singleton` wrapper around `StableDiffusionManager.getInstance(context)`. `ensureRuntime()` is mutex-guarded and fires `StableDiffusionManager.initialize()` on first use, which extracts `qnnlibs.tar.xz` from the AAR's bundled assets into `<filesDir>/ai_sd_runtime/`. Subsequent `loadDiffusionModel(model, w, h)` calls run model-specific load on the engine. The active model id is cached so re-entering Image Task screen with the same model is a no-op.

### Image Task screen

`ui/screens/image_task/ImageTaskScreen.kt` + `ImageTaskTopBar.kt` + `viewmodel/ImageTaskViewModel.kt`. Route: `NavScreens.ImageTask` (`"image_task"`). Reachable from the chat drawer's "Images" quick-link. The screen is one LazyColumn of cards:

- **Task** — `ActionToggleGroup` segmented switch (Generate / Edit / Inpaint / Upscale).
- **Image model / Upscaler** — list of installed models for the picked task; tapping a row triggers `loadDiffusionModel` or `loadUpscaler`.
- **Prompt** — TnTextField for prompt + negative prompt (hidden in Upscale mode).
- **Settings** — `ActionToggleGroup` rows for Steps, CFG, Scheduler, Resolution, Denoise (img2img / inpaint only).
- **Input image** — SAF `OpenDocument` picker, shown for Edit / Inpaint / Upscale.
- **Run** — Generate / Edit / Inpaint / Upscale 4× button, with Stop appearing during generation. LinearProgressIndicator binds to `DiffusionGenerationState.Progress.progress`.
- **Output** — renders the final `Bitmap` (or live intermediate if `showDiffusionProcess` is on).

### Things to know

- The `:ai_sd` library declares `commons-compress` and `xz` as `api` deps in its module build. When consuming as a path AAR (`implementation(files(...))`) Gradle does NOT pull transitive deps from a POM-less file dependency, so `app/build.gradle.kts` must declare both directly. `xz` was added to `gradle/libs.versions.toml` as `org.tukaani:xz:1.12`.
- `app/proguard-rules.pro` adds `-keep class com.dark.ai_sd.** { *; }` and `-dontwarn com.dark.ai_sd.**` alongside the existing gguf_lib / ai_sherpa rules.
- The AAR ships `qnnlibs.tar.xz` (~200 MB compressed) inside `assets/qnnlibs/`. First-run setup extracts it into `filesDir/ai_sd_runtime/` and is observable through `RuntimeSetupState`. Don't move the runtime dir without bumping the SDK version token in catalog URLs.
- Currently the **debug** AAR is shipped (release AAR's R8 mangled `StableDiffusionManager.Companion.getInstance`). Once `:ai_sd`'s `consumer-rules.pro` adds `-keep class com.dark.ai_sd.StableDiffusionManager$Companion { *; }` and the AAR is rebuilt, swap to release.

### Future expansion

To add Object Removal (LaMa), Segmentation (MobileSAM), Depth (MiDaS / Depth Anything V2), or Style Transfer (AdaIN) — the C++ already implements all four; needs a small JNI surface on `SDNativeLib` + matching state flows on `StableDiffusionManager` + new `ImageTaskMode` values + per-feature catalog rows pointing at the matching HF repos.

To add SDXL on devices that aren't in `isSdxlCapable` — pipeline-level, not gating-level. SDK has `textEmbeddingSize=768` hardcoded across the C++ pipeline + SDK keeps 4-channel latents + 77-token CLIP + LDM-style weight names. SDXL needs 2048-dim, dual CLIP, additional UNet conditioning inputs (`text_embeds`, `time_ids`), and matching `sd_structure.h` / `lora_mapping.h` entries. Out of scope for this pivot.

---

## Setup flow

Sequence: Intro → **TermsConditions** (if !tcAccepted) → **DevNotes** (if !onboardingComplete) → SetupScreen (lock mode) → (SetupPassword if password chosen) → SetupTheme → ModelSetup → SetupRag → Home.

`ScaffoldViewModel.resolveStartDestination()` ordering: tcAccepted, then onboardingComplete, then securitySetupDone, then modelSetupDone, then `isLockEnabled` → PasswordScreen, else HomeScreen.

### TermsConditions

- Route: `NavScreens.TermsConditions("terms_conditions")`.
- Screen: `ui/screens/terms_conditions/TermsConditionsScreen.kt`. Top bar: `TermsConditionsTopBar.kt`. Bottom bar: `TermsConditionsBottomBar.kt`. VM: `viewmodel/TermsConditionsViewModel.kt` writes `prefs.tcAccepted = true` on accept. `BackHandler(true) {}` absorbs back so users can't escape to Intro.
- AppScaffold handoff: `onTermsAccepted` calls `markTermsAccepted()` then navigates DevNotes (popping T&C). The same callback is reusable from Settings later — accept becomes a no-op + popBackStack when `tcAccepted` is already true.
- The screen body is plain-English use-at-your-own-risk language, not legalese. No "decline" button — close the app or accept.

### DevNotes

The first interactive welcome screen for new users (NOT release notes for engineers). Compose-native section cards with icons covering: data stays here, chat models, voice, document attachments, vision, web search, local-network server, and a short "rough edges" honesty section. Lives at `ui/screens/dev_notes/DevNotesScreenBody.kt`. Replaces the previous markdown-blob version. `fun DevNotesScreen(innerPadding: PaddingValues)` signature is stable so `TNavigation` keeps working. All copy is plain-language; no jargon, no em dashes, no rule-of-three patterns.

### SetupTheme

- Route: `NavScreens.SetupTheme("setup_theme")`.
- Screen: `ui/screens/setup_screen/SetupThemeScreen.kt`. VM: `viewmodel/SetupThemeViewModel.kt` (injects `ThemeController`). Selection commits immediately.
- Continue button: `SetupThemeBottomBar.kt`, dispatched from `AppBottomBar.kt`. AppScaffold handoff: `onSetupComplete → SetupTheme` (TNavigation); `SetupTheme → ModelSetup` (AppBottomBar callback wires the navigation).
- Top bar: `SetupScreenTopBar()` dispatched from `AppTopBar` on `SetupTheme.route`.
- No "themeSetupDone" pref — defaults are valid on first launch.

### ModelSetup — Packs

`ModelSetupScreen.kt` shows three feature packs plus a "Custom" toggle for power users. Packs are bundles of catalog ids downloaded sequentially:

| Pack id | Includes | Approx size |
|---|---|---|
| `chat_only` | LFM2 350M | 200 MB |
| `chat_voice` | LFM2 350M + sherpa-onnx-whisper-tiny-en + vits-piper-en_US-amy-low | 310 MB |
| `chat_voice_large` | Qwen3 0.6B + same STT + same TTS | 530 MB |

Pack content is defined as `PACK_CONTENTS: Map<String, List<String>>` in `ModelStoreViewModel`. `downloadPack(packId: String)` resolves each catalog id and enqueues via `downloadModel`. Reuses the existing `downloadByQuickStartId` for chat-model resolution (preferred quant priority: `Q4_K_M → Q4_K_S → Q4_0 → Q5_K_M → Q5_K_S → Q8_0` then smallest-by-size). The Custom side keeps "Browse all models" (opens ModelStore) and "Pick a local file" (SAF picker → `ModelImportTypePicker`).

---

## App Guide

Hub + 7 detail screens, all **single-Scaffold** (accept `innerPadding: PaddingValues`):

- Hub `AppGuideScreen.kt` — three categories ("Getting started" / "Advanced AI" / "Your phone, your data"). Cards dispatch via `onOpenEntry(key)`.
- `GuideDetailLayout(innerPadding, lede, icon, steps: List<GuideStep>, tips)`. Steps numbered, optional `visual` composable.
- `GuideTopBar(title, onBack)` dispatched from `AppTopBar.kt` for each guide route.
- Detail screens: `GuideChatScreen`, `GuideModelsScreen`, `GuideRagScreen`, `GuideVlmScreen`, `GuideVoiceScreen`, `GuideSecurityScreen`, `GuideThemesScreen` (and optionally `GuideServerScreen` for Remote Server).
- Adding a feature: add `GuideEntry` in `AppGuideScreen.guideCategories()`, key in `GuideEntryKeys`, route in `NavScreens`, detail screen, `composable(...)` registration in `TNavigation`, and a `when` case in `AppTopBar.kt`.

---

## Test layout

49+ instrumented tests across 7 classes (PhaseOne/Two/Three/Four, ExtraHardening, Resilience, ExampleInstrumentedTest). All green on Pixel_Tablet AVD API 35. Any test mutating native global state must call `PolicyEngine.resetForTesting()` in `@Before` AND `BootIntegrity.setRelaxedForTesting(true)` BEFORE any `hardFail` path (otherwise the process `_exit(1)`s mid-test).

---

## Things still deferred

- **Encrypt WAL** (`hxs/src/main/cpp/wal.cpp`) — plaintext even in encrypted mode. Real audit finding; needs HXS WAL format work.
- **Native cert pinning** — low priority; offline-only scope.
- **Play Integrity opt-in** — conflicts with privacy-first.

---

## Things NOT to regress

- Don't re-introduce `Settings.Secure.ANDROID_ID` — Keystore-attested identities only.
- Don't take Argon2 below `t=4 / m=131072 / p=1`. Constants in `auth.h`.
- Don't re-add `OPENSSL_NO_ASM=1` — ARM crypto matters for performance.
- Don't expand the unauth feature set in `policy_engine.cpp::is_unauth_feature` without explicit threat-model review.
- Don't remove `setRelaxedForTesting` wiring; tests rely on it.
- Don't emit plaintext detection strings in native code — wrap them in `HXS_OBF`.
- Don't switch back to `verifyPassword(): Boolean`. The contract is `VerifyResult`.
- Don't route any gated feature around `PolicyEngine.isAllowed`.
- Don't collapse the Quick-Start quant preference list in `TNavigation.kt`. The priority `Q4_K_M → Q4_K_S → Q4_0 → Q5_K_M → Q5_K_S → Q8_0` then smallest-by-size keeps the "Tiny & Fast" download tiny.
- Don't send VLM image bytes as `byte[]` over AIDL — `ParcelFileDescriptor[]` only (1 MB binder limit).
- Don't read images on the main thread in `InferenceService.generateVlm` — PFD reads happen in the `scope.launch` Dispatchers.IO collector.
- Don't drop the VLM marker prefix when `isVlmLoaded`. `buildMessagesJson(messages, vlmLastUserId)` must prepend `getVlmDefaultMarker()`.
- Don't key VLM repo detection off anything other than the `mmproj` substring (case-insensitive). Repos use `mmproj-<name>-F16.gguf`, `*-mmproj-*.gguf`, etc.
- Don't re-add a manual "Load projector" UI. Auto-load is the contract.
- Don't flatten the VLM folder layout. Base + mmproj as siblings under `models/vlm/<repoLeaf>/`.
- Don't register the mmproj as its own `ModelInfo`. Mmproj is a sibling on disk.
- Don't skip `releaseVlmProjector()` at the top of `ModelSessionManager.load` and `.unload`.
- Don't break the setup-flow handoff. Order is Intro → TermsConditions (if !tcAccepted) → DevNotes (if !onboardingComplete) → SetupScreen → SetupTheme → ModelSetup → SetupRag → Home. `ScaffoldViewModel.resolveStartDestination()` checks `tcAccepted` FIRST, before `onboardingComplete`. AppScaffold callback chain: `onTermsAccepted → DevNotes`, `onSetupComplete → SetupTheme`, `onThemeSetupComplete → ModelSetup`, `onModelSetupComplete → SetupRag`, `onRagSetupComplete → Home`. T&C must come before DevNotes — DevNotes is informational; T&C is the user's legal acknowledgment. Re-ordering them is a regression.
- Don't fold the ModelSetup "Packs" toggle back to a single-model picker. The packs flow is the default for non-technical users; `ModelStoreViewModel.downloadPack(packId)` enqueues every catalog id in `PACK_CONTENTS` for that pack id and the Custom toggle covers Browse-all + Pick-local for power users. Pack ids: `chat_only`, `chat_voice`, `chat_voice_large`. Voice catalog ids are pulled from `ModelCatalog.BUILT_IN_MODELS` (`sherpa-onnx-whisper-tiny-en`, `vits-piper-en_US-amy-low`); chat repos resolve via `downloadByQuickStartId` so the existing quant-priority is preserved.
- Don't drop the auto-active + auto-load contract on chat send. Three coupled rules:
  (a) `ModelStoreViewModel.finalizeNonVlmDownload` MUST mark a freshly-installed GGUF model as `isActive=true` when no other GGUF is currently active. Without this, pack-based setup leaves the user with no active chat model and the next chat-send opens the manual model picker instead of generating. Voice models use the separate `prefs.activeTtsModelId` / `prefs.activeSttModelId` first-install pattern in `finalizeVoiceDownload` and that path stays intact.
  (b) `HomeViewModel.sendMessage` MUST (1) fall back to `chatModels.value.firstOrNull()?.also { modelRepo.setActive(it.id) }` when `activeModel.value` is null but a chat model is installed, and (2) call `modelSession.load(active)` then check `loadState.value is ModelLoadState.Active` before invoking `runGeneration` when the engine is not yet loaded. The user's typed message must already be persisted to chat history BEFORE the load coroutine kicks off so the input bar clears and nothing is lost on slow loads. Only fall back to opening `_loadModelWindow` when there are zero chat models installed at all.
  (c) `HomeScreenBottomBar.canSend` MUST be `text.isNotBlank() && !isGenerating && (isModelLoaded || installedModels.isNotEmpty())`. Tying `canSend` to `isModelLoaded` alone re-introduces the original bug where the load-model composable pops up on send instead of generating, because pack-based setup completes with the engine still unloaded.
  Together these three put the manual load-model composable on the rare-empty path only. The common path is: type, hit Send, the inline pill flips to Loading then Generating, the response streams. The native `Model loaded (ctx=...)` log appearing seconds after Send is normal and expected on the first send post-launch.
- Don't put back `documentRepo.clearAll()` in `RagManager.init`. Doc records persist across restarts; the engine re-ingests lazily through `hydrateChat(chatId)`. Wiping breaks the prev-chats picker.
- Don't generate a UUID-based docId for chat documents. `id = "$chatId:$sourceId"` so re-attach is idempotent and `removeDocument` can reference-count the source blob.
- Don't ingest a chat document without first writing its bytes to `<filesDir>/chat_documents/sources/<sha256>.bin`. The picker re-ingests from that file on demand.
- Don't downgrade `DocumentRepository` back to `openPlaintext`. Metadata is sealed under HKDF(DEK, "tn.chat_documents.user_key.v1") at `chat_documents_meta_v1/`. The init's legacy migration is one-shot — re-running on already-migrated installs only deletes top-level files in `chat_documents/` (preserving `sources/`), so it's safe to leave in place.
- Don't drop the BM25 `RagKeywordIndex` from the augment path. Pure dense retrieval is the regression we already fixed. The `RagManager.augment` flow is: optional multi-query (LLM rewriter) → per-query (dense + BM25) → `rrfFuseMany` → optional LLM rerank → token-budget trim → top-N. Keep the order; flipping rerank before fusion gives the rerank LLM nothing useful to look at.
- Don't move the BM25 index back to Kotlin / SQLite. The tokenizer + inverted index + scoring all live in `hxs::RagKeywordIndex` (C++) and the records are encrypted via the existing HXS AEAD path. Reasons: (1) tamper resistance — index ranking logic is harder to manipulate when it's behind libhxs.so; (2) on-device portability — some Android OEMs strip the FTS5 module from system SQLite, breaking SQLite-based BM25 entirely; (3) privacy — chunk text was plaintext on disk in `databases/rag_keyword_v1.db` previously. The new vault at `<filesDir>/rag_keyword_v1/` is sealed under `HKDF(DEK, "tn.rag_keyword.user_key.v1")` like the rest of the app's data.
- Don't bypass `appPrefs.ragSmartRerank` / `appPrefs.ragMultiQuery` toggles. Both Phase 2 features are user-opt-in (off by default) because they each cost an LLM call per query. The rerank prompt is in `RagReranker.buildPrompt`; the variants prompt is in `RagQueryRewriter.buildPrompt`. Don't strip the `withTimeoutOrNull(15s/8s)` either — the chat model can hang on bad input.
- Don't change the Citation TAG byte. `ChatRepository.TAG_MSG_CITATIONS = 13` for assistant messages; older messages without the TAG decode with `citations = emptyList()`. JSON shape: `{sourceId, docId, chunkIndex, score, name, mimeType, snippet, cited}`. `RagCitationMatcher.match` writes them on every assistant turn that ran through RAG augmentation; `MessageBubble` renders them via `CitationStrip` (chip per citation, tap → AlertDialog).
- Don't pass binary-format documents through the FTS5 indexer. Native engine is the only thing that can extract their text. `RagManager.isTextLike(mime, name)` is the gate — text/* mimes, structured-text mimes (json/xml/rtf/yaml/javascript), and known text extensions (txt/md/html/csv/log/code). Binary docs (pdf/docx/epub/etc.) get dense-only retrieval until a Kotlin extractor or a native API addition lands (see #329).
- Don't change the RRF identity key. Items in the fused pool are keyed by `(docId, chunkIndex)`. Native chunks and FTS5 chunks use independent indices, so identical text from both rankers is treated as two separate hits — that's intentional, since they come from different chunk boundaries. RRF still works correctly.
- Don't enable algorithmic darkening / unguarded toggles in the Settings → Chat & RAG section without a `prefs.<key>` write paired with a `_<flow>.value = value` update. The `combine(...)` flow has 13 inputs now; if the toggle's StateFlow doesn't update, the UI stays stale until process restart.
- Don't drop `-Wl,-z,max-page-size=16384` in any owned native CMake target. Android 15+ / Play Store requires 0x4000 LOAD alignment on arm64 + x86_64. Verify: `unzip -p libs/ai_sherpa-release.aar jni/arm64-v8a/libai_sherpa.so > /tmp/s.so && readelf -l /tmp/s.so | awk '/LOAD/{getline;print $NF}'` → `0x4000`.
- Don't `secureWipe` the userKey passed to `HexStorage.openEncrypted`/`createEncrypted`. `hxs.cpp` keeps a `NewGlobalRef`; zeroing turns every later AEAD op into a zero-key op (silent decrypt failure on next launch).
- Don't zero `AppKeyStore.cachedDek` in-place inside `wipe()`. The same ByteArray is held by HXS via `NewGlobalRef` (it's the `appKey` passed into `openEncrypted`). Filling it with zeros breaks every in-flight HXS op in the running process and crashes the app immediately after panic-PIN wipe. Just null the reference and let process-kill on the WipedScreen Restart button handle physical memory clearing.
- Don't tighten `migrateLegacyIfNeeded()` back to bootstrap-only. After `hardWipe()`, `app_bootstrap/` is empty (k.bin deleted) but `app_prefs/*` still has records sealed under the now-gone DEK. The migration must fire when EITHER the bootstrap dir has stale files OR `app_prefs/` has files but `k.bin` is missing — otherwise next-launch tries to decrypt records under a fresh DEK and `SecurityException`s.
- Don't downgrade `keyStore.wipe()` back to deleting only `k.bin` + the Keystore alias. The panic-PIN/wipe contract is "delete everything the user owns" — models, voice files, chat history, RAG documents, plugin state, repo config, the lot. Implementation: `context.filesDir.listFiles().forEach { deleteRecursively() }` + `context.cacheDir.listFiles().forEach { deleteRecursively() }` + alias delete. Anything held mmap'd by `:inference` or `:server` survives via POSIX inode-after-unlink until those processes die, but the data is gone after the user taps Restart.
- Don't run Argon2id on the main thread. `SecurityManager.setPassword` / `verifyPassword` / `setPanicPin` are all ~1.5 s on a Pixel 8; calling them from a Compose `onSubmit` lambda freezes the UI. Every call site in viewmodels must wrap with `viewModelScope.launch { withContext(Dispatchers.Default) { … } }`. `PasswordViewModel.submit` already does this; `SettingsViewModel.openPinDialog / openDisableLockDialog / openPanicPinDialog` and `SetupViewModel.submitPassword(onSuccess)` were main-thread until 2026-04-27.
- Try the panic PIN BEFORE the lockout gate in `SecurityManager.verifyPassword`. A duress-PIN must work even when the user is locked out — that's the whole point. Order: (1) try panic against `panicSalt/panicHash`, hardWipe + return Wiped on match; (2) check `nextAttemptAtMs > nowMs` and return LockedOut; (3) try real PIN; (4) bump counter on miss.
- Prime `PasswordViewModel._lockedUntilMs` from `security.snapshotLockoutState().nextAttemptAtMs` at construction. If you initialize it to `0L`, an app restart while locked shows the password input again (the persisted lockout only kicks in after a SUBMIT, letting the user freely retry inputs). The countdown screen must come up immediately on launch when the lock is still active.
- Don't eagerly construct `AppKeyStore` or `AppPreferences` from any process other than main. `InferenceService` runs in `:inference`. `TNApplication.isMainProcess()` early-returns; integrity / pref Hilt fields must be `dagger.Lazy<T>`.
- Don't wrap individual screens in their own `Scaffold`. One Scaffold = `AppScaffold`. Per-route bars go in `AppTopBar.kt` / `AppBottomBar.kt` `when` blocks.
- Don't set `isMinifyEnabled = true` on any library module. Only `:app` minifies. Library minification collides on `Type a.a is defined multiple times` against pre-minified prebuilts.
- Don't remove the per-step `visual` composables in guide detail screens. Update them if the real UI changes.
- Don't key the TOFU `.so` manifest by absolute path. Filenames + `nativeLibraryDir` resolve.
- Don't verify the `.so` manifest across app updates without rebinding to install identity. Mismatched `{signerHash, longVersionCode, lastUpdateTime}` triggers re-TOFU, not hard-fail.
- Don't re-add a root hard-fail. One-time `RootWarningDialog`, gated on `rootWarningShown`.
- Don't re-add a hard-fail for `FAIL_XPOSED` or `AccessibilityGuard.SuspiciousAttached`. `scan_process_environment` returns a bitmask; `TNApplication.onCreate` only hard-fails on `FAIL_DEBUGGER | FAIL_FRIDA`. The `FAIL_XPOSED` bit lands in `TNApplication.softEnvReasons` and `accessibilityGuard.scan()` results land in `ScaffoldViewModel.resolveInitialRootWarning`; both surface as additional paragraphs in the existing `RootWarningDialog`. Reason: rooted users almost universally also have LSPosed and/or one third-party a11y service installed (banking-overlay-blocker, password manager, Tasker, Shizuku-helper). Hard-failing the whole app on first launch is a worse user experience than warning once and letting them opt in. The active-attack tools (`debugger`, `frida-server`) still hard-fail because those are not "device customisation", they're "someone is poking at the running process right now".
- Don't bind `:inference` from `:app` with `BIND_IMPORTANT`. That flag elevates `:inference` into the same OOM-priority bucket as `:app` (or higher), so on low-RAM devices like Snapdragon 662 / 4 GB phones the kernel's lowmemorykiller picks `:app` to evict instead of the process actually holding the multi-GB model mmap. Plain `BIND_AUTO_CREATE` keeps `:inference` on the foreground-service path (it calls `startForeground` in `onCreate`), so it's still well-protected — but `:app` is no longer demoted relative to it. `InferenceClient.performBind` uses `BIND_AUTO_CREATE` only.
- Don't leave `_service.first { it != null }` un-timed in the `generate` / `generateMultiTurn` / `generateVlm` callbackFlows. Wrap with `withTimeoutOrNull(BIND_TIMEOUT_MS)` and emit `InferenceEvent.Error("Inference service unavailable")` on null — otherwise a permanently failed rebind hangs the UI in `_isGenerating = true` forever with no surfacing.
- Don't drop the `failPendingLoads` call from `onNullBinding`. All four `ServiceConnection` death paths (`onServiceDisconnected`, `onBindingDied`, `onNullBinding`, `unbind`) must drain `pendingLoads` and resume them with `Result.failure`, otherwise a load coroutine that started right before service-process death suspends forever.
- Don't drop the `try { modelSession.load(active) } catch (Exception)` wrapper in `HomeViewModel.sendMessage`'s auto-load branch. `viewModelScope` is `SupervisorJob`-backed, but an unhandled throw from a child coroutine still reaches the thread's default uncaught handler and crashes `:app`. The existing `Result<String>` contract from `InferenceClient.loadModel` keeps the happy path safe; the wrapper is defense-in-depth for any future code that throws here.
- Don't drop the pre-load RAM check in `InferenceService.loadModel`. `File(path).length()` vs `MemAvailable:` from `/proc/meminfo`: if `memAvail in 1 until (modelSize * 6 / 5)`, surface a clear "Not enough free memory" error to the client immediately. Without this guard, low-RAM devices try to mmap a model larger than physical RAM, the kernel page-faults loop, and `:inference` gets killed mid-load — visible to the user as a generic "service died" with no actionable message. Skip the check when `memAvail <= 0` (read failed) so the gate doesn't accidentally block valid loads.
- Don't strip the `logDeviceProfile()` call at the top of `InferenceService.loadModel`. Logs `Build.MODEL`, `Build.SOC_MODEL`, `Build.SUPPORTED_ABIS`, `MemTotal`, and the `Features:` line from `/proc/cpuinfo` exactly once per service-process lifetime. This is the only telemetry that lets remote debugging triage a "model load crash on $unknown_device" report — specifically whether `dotprod`/`i8mm`/`fp16` are present (Snapdragon 662 / Cortex-A53 lacks all three; many llama.cpp dispatch paths assume at least asimd-dotprod).
- Don't re-add a plaintext HXS container for the bootstrap DEK. Raw XOR-masked `k.bin` only.
- Don't skip `migrateLegacyIfNeeded()` in `AppKeyStore.init`.
- Don't drop the signer-binding salt from any user-key derivation. Every `encryptor.deriveKey(ikm = dek, salt = ?, info = ?)` call in app code MUST use `salt = keyStore.installSignerHash()` (not `salt = dek`, not `salt = null`). Sites: `AppPreferences.init`, `AppPreferences.deriveAuthKey`, `DocumentRepository.init`, `RagKeywordIndex.init`, `ChatRepository.init`, `SourceFileVault.keyFor`. Without this salt, a same-device replaced-APK attack (root + repack with attacker's cert) can unwrap the DEK via the inherited uid-scoped Keystore alias and decrypt every vault. Salting with the signer hash means the patched APK derives a different user-key and AEAD fails — the data stays sealed.
- Don't add a "fallback to all zeros" or "return empty bytes on failure" path in `AppKeyStore.computeSignerHash()` / `installSignerHash()`. If the platform can't read the install signer, throw `SecurityException` and let the app refuse to bootstrap. A zero-fallback collapses the binding for any device with a broken signature lookup, which is exactly the path an attacker would try to engineer.
- Don't bump user-key info strings without bumping the version suffix (`v2` → `v3`). Bumping invalidates existing v2 vaults — the open-or-rebuild helper detects the AEAD failure on `openEncrypted` and wipes the dir. Documented loss is intentional; a silent loss because the info string was edited inline is not.
- Don't downgrade `ChatRepository` back to `openPlaintext`. Chats and messages are sealed under `tn.chats.user_key.v2`. Plaintext on disk was the cross-device readability hole — closed in this build.
- Don't bypass `SourceFileVault` for `chat_documents/sources_v2/` reads or writes. Every byte of every attached RAG document is AEAD-sealed per-file under a key bound to (DEK, signerHash, sourceId), with AAD = sourceId. This means: (a) cross-device read fails (no DEK unwrap), (b) cross-build read fails (different signer), (c) renaming a file breaks decryption (AAD mismatch — defends against record-substitution). Direct `File(...).readBytes()` / `writeBytes()` is forbidden for source files.
- Don't preserve the legacy `chat_store/` (plaintext) or `chat_documents/sources/` (plaintext) directories on a v2 build. `ChatRepository.init` and `SourceFileVault.init` both `deleteRecursively()` them on first launch — this is the migration path that closes the historical leakage. Re-introducing those dirs (e.g. for a "compatibility shim") puts plaintext back on disk.
- Don't unwrap the DEK in `:server` or `:inference`. `:server` runs in its own process and never opens HXS — token, model path, and config are pushed via AIDL `start(configJson)`. `:inference` is similar. Only `:app` (main process) holds the DEK, and only the main process derives signer-bound user-keys. Cross-process key handoff would defeat the binding.
- Don't link `:native-server` against BoringSSL / OpenSSL / zlib. Header-only httplib + getrandom(2).
- Don't add a new HTTP route without auth pre-routing. Only `/`, `/index.html`, `/webui`, `/health` are in `auth::is_public_path`. Never make `/v1/models` or `/v1/chat/completions` public.
- `RemoteServerService` lives in `:server` (its own process). Don't fold it back into `:app`. `:server` MUST NOT open HXS — token / model path / config / asset HTML are passed in via AIDL `start(configJson)`. Token rotation pushes from `:app` via `rotateToken(newToken)`.
- Don't remove the `startService(Intent(this, RemoteServerService::class.java))` call inside `handleStart` (just before `startForeground`). The service is otherwise bind-only and gets destroyed when `:app` dies / swipes from recents. The self-start transitions it to the "started" lifecycle so a foreground notification + `stopWithTask="false"` keeps it alive across client death.
- Don't let any UI escape the server lockdown. `LaunchedEffect(serverRunning, currentRoute)` with `popUpTo(0) { inclusive = true }`; `BackHandler(enabled = running) {}` in ServerScreen; drawer gated by `showDrawer = currentRoute == HomeScreen.route && !serverRunning`.
- Don't persist the server token outside the encrypted HXS `app_prefs` vault.
- `/v1/models` returns the full enabled-engine catalog (every installed model across `gguf` / `vlm` / `embedding` / `tts` / `stt` / `image_gen` / `image_upscaler`) with per-entry `type` + `owned_by`. Each model's JSON entry must carry `id`, `path`, `type`, `config_json`; for `vlm` rows also `mmproj_path`. Don't shrink this back to "currently loaded only" — clients pick the model per request via the OpenAI `model` field, and the registry lazy-loads on first use. Don't silently expose models with `pathType == CONTENT_URI`; they're filtered in `ServerController.buildEnginesCatalog` because the server has no SAF trampoline.
- Don't bypass `ServerEngineRegistry` from any new route. Per-kind locks (`Mutex` for the suspend-based loaders, plain `synchronized` for the sherpa-onnx ones) serialise concurrent loads — a second request for the same engine waits for the first load to complete instead of racing it. New endpoints add a new typed `Kind` to `server_models::Kind`, a new wrapper class, a new registry method, a new `InferenceBridge.start<X>` upcall, and a new `nativeFeedReply*` consumption path. Don't shortcut by reaching into `GGMLEngine` / `StableDiffusionManager` directly from the route handler — every engine touch must go through the registry so loading discipline is preserved.
- Don't route `/v1/chat/completions` to the VLM engine unless the request has at least one `image_url` content part. `oai::extract_images_from_messages` is the single decision point — it sets `request.has_images = true` only when a part of type `image_url` is detected in any message. Don't move that detection upstream into the rate-limit / auth layer; pre-routing must stay pure.
- Don't accept network URLs in `image_url` parts on the server. Privacy / offline-only scope: only `data:image/...;base64,...` is parsed; `http(s)://...` returns 400 `invalid_image`. Adding outbound fetch from `:server` would mean adding curl-impersonate to the native server, which doubles its native deps and breaks the "no BoringSSL/OpenSSL/zlib in `:native-server`" rule.
- Don't pass big binary payloads (TTS audio, generated images, multipart image/mask inputs, multipart WAV) as `byte[]` over JNI. The contract is: write the bytes to `<cacheDir>/server-staging/tn_<rand>_<name>` via `server_staging`, hand the **path** across JNI as a Java string. The C++ route reads/writes the file directly. Cleanup happens after the response is sent (`staging::unlink_safe`) and at every server stop (`staging::purge_all`). JNI byte[] copies for 4 MB images are measurable overhead and have caused OOMs in adjacent products.
- Don't change `reply_session.wait(timeout_ms)` to take 0 or -1 unconditionally. The defaults exist for a reason: embeddings 120s, TTS 180s, STT 180s, image gen 600s, image upscale 300s. On a Snapdragon 8 Gen 1 a 20-step SDXL run can flirt with 5 min; the 600s ceiling is the cliff before we say "something's wedged".
- Don't drop the in-process `ServerImageEngine.ensureRuntime()` check that gates on `<filesDir>/ai_sd_runtime/qnnlibs.tar.xz` existing. The user must have triggered the SD runtime download via the in-app Image Task screen at least once before the server's image endpoints work. Image gen routes return a clean 500 with "image engine unavailable or runtime not installed" if not.
- Don't share `StableDiffusionManager` state between `:app` and `:server`. Each process has its own `getInstance(context)` (class loaders are per-process). They cooperate only via the shared on-disk `<filesDir>/ai_sd_runtime/` directory; whichever process initialises first extracts qnnlibs, the other sees the existing files and skips re-extraction. Don't add IPC between the two image stacks.
- Don't fold the `:server`-side `ServerTtsEngine` / `ServerSttEngine` back into using `InferenceClient` AIDL. `:server` MUST own its sherpa-onnx instances directly — the AIDL hop would mean (a) crossing into `:inference` which has its own active TTS/STT for in-app voice, (b) yanking that state under the user's feet when the chat-side mic is in flight. The two stacks are intentionally independent.
- Don't read voice / embedding "active" preferences from inside `:server`. The server doesn't open HXS. `ServerTtsEngine.ttsFor(modelId)` falls back to `catalog.firstOf(TTS)` when the requested id isn't found — which is "whatever's listed first in `ModelRepository.models`" (install order). If the request omits `model`, the server picks the first installed engine of that kind. Clients that want a specific voice MUST send `model` explicitly.
- Don't broaden the OpenAI streaming contract to non-chat routes. Embeddings, TTS, STT, image gen are all single-response. Adding `stream:true` support to them would require either a new SSE schema (no OpenAI precedent for images), or partial-bytes-over-chunked-transfer (sherpa-onnx and SD AAR are batch-only; no per-step callback exposes individual samples). Stay aligned with OpenAI's published shapes.
- Don't trip the `server_->set_payload_max_length` setting back to 1 MB. The 64 MB cap is sized to fit base64-encoded VLM image_urls (4 MB raw ≈ 5.4 MB b64) plus multipart audio uploads (whisper-friendly 30s WAV at 16kHz 16-bit ≈ 960 KB) plus 4× upscale inputs. Same applies to the read/write timeouts (60s / 120s) — TTS synthesis of a single sentence on a Tensor G3 takes ~3s, but a 200-character paragraph runs longer.
- Don't add audio transcoding to `/v1/audio/transcriptions`. WAV-only is the documented contract. Bringing in ffmpeg or symphonia would balloon the native footprint. If clients want MP3/AAC support, they decode client-side before upload (the bundled web UI's STT panel already only accepts `.wav`).
- Don't bind the server only to the Wi-Fi IP by default. `ALL_INTERFACES` (0.0.0.0) is the default so the loopback URL is reachable from the device's own browser regardless of Wi-Fi state. Display two URLs: loopback (always works) + LAN (when Wi-Fi is up).
- Don't display `serverPort` from raw HXS without validation. Getter validates [1024..65535]; setter clamps. Effective port (post-bind) is written back from `nativeBoundPort()`.
- Don't drop the `serverController.isBusy` gating on chat-side load/unload/send. The server owns the loaded model; uncontrolled chat-side reload would yank state mid-request.
- Don't add a `modelType: String` field to `ModelInfo`. `ProviderType` is canonical. `HuggingFaceModel.modelType: String` is the pre-install hint mapped at insert time.
- Don't add a streaming-synthesize AIDL method. The AAR's `OfflineTts.generate` is synchronous. Streaming TTS = client-side text chunking.
- Don't record STT at anything other than 16 kHz mono `ENCODING_PCM_FLOAT` from `MediaRecorder.AudioSource.VOICE_RECOGNITION`.
- Don't skip the mid-chunk cancellation check in `TtsPlayer`'s write loop.
- Don't pass `dataDir` / `espeak-ng-data` as `content://`. sherpa-onnx wants filesystem paths.
- Don't resurrect a BYOM / SAF directory import path for voice. Store-only.
- Don't make the Mic button conditional on `voiceSttAvailable` — always rendered; the click handler routes to Store if no STT model is installed.
- Don't skip `voiceManager.unloadStt()` in `HomeViewModel.stopRecordingAndTranscribe`'s `finally`.
- Don't auto-request `FOREGROUND_SERVICE_MICROPHONE`.
- Don't cram more than three quick-links into a single drawer `SpaceEvenly` row. The drawer layout is two separate rows: a "chat tools" row under the New Chat button (Store / Docs / Server) and an "info" row at the bottom (Guide / Dev Notes / Credits). Settings sits as a gear `ActionButton` in the drawer header next to the title — it is not in either row because it is global, not chat-related. Keep three-and-three; pushing four into either row reintroduces the touch-target squashing the original 6-in-a-row had.
- Don't move the Credits screen out of fullscreen. Route is `NavScreens.Credits("credits")`. `AppScaffold.isFullscreen` includes it alongside Intro and Password so the AppTopBar / AppBottomBar are hidden — the screen owns the full viewport and draws its own theme-coloured background. Audio is `R.raw.credits` (mp3 in `app/src/main/res/raw/`); `MediaPlayer.create` is acquired via `remember`, started in `DisposableEffect`, released on dispose. `setOnCompletionListener { onExit() }` exits when the audio ends. Scroll is a `verticalScroll(scrollState, enabled = false)` Column animated by `scrollState.animateScrollTo(maxValue, tween(durationMillis = mediaPlayer.duration, easing = LinearEasing))` keyed on `scrollState.maxValue` and `durationMs` so the first emission with non-zero `maxValue` triggers the crawl. User scroll is disabled to keep the timing deterministic; tap or back exits. Colours pull from `MaterialTheme.colorScheme` (`surface` background, `primary` title, `onSurface` lines, `onSurfaceVariant` section labels) — adapts to dark/light theme.
- Don't let `VoiceModelManager` construct `AppPreferences` eagerly. `dagger.Lazy<AppPreferences>`.
- Don't add `*.md` spec / plan / research / TODO docs at the repo root. Project memory lives here. Implementation roadmaps belong in conversation context.
- Don't auto-scroll on every streaming token via `LazyListState.scrollToItem(index)`. That fights the user's drag and locks manual scroll mid-generation. The pattern is: track `stickToBottom` from `snapshotFlow { isScrollInProgress }` (re-evaluate when scroll settles via `!canScrollForward`), gate the auto-scroll `LaunchedEffect` on `stickToBottom && !isScrollInProgress`, and use `scrollToItem(last, scrollOffset = Int.MAX_VALUE)` so the bottom of the growing streaming bubble stays in view.
- Don't drop the `:native-server` consumer-rules.pro keeps for `NativeServer`, `NativeServer$*`, `InferenceBridge`, `BindMode`. The native HTTP server's JVM bridge invokes `InferenceBridge.startGeneration / cancelGeneration / onRequestEvent` via JNI on a `NewGlobalRef`'d jobject; renaming or stripping breaks dlsym at runtime.
- Don't drop the `com.dark.networking.**` keep + `dontwarn` block from `app/proguard-rules.pro` or the `WebNative` / `WebResponse` / `WebBytesResponse` / `WebSearchResult` keeps from `networking/consumer-rules.pro`. `WebNative` is a Kotlin `object` with `@JvmStatic external fun nativeFetch / nativeFetchBytes / nativeSearch / nativeHasBackend / nativeBackendName / nativeSetCaBundle / nativeSetProfile`; the JNI binding is `Java_com_dark_networking_WebNative_nativeFetch` etc., so the class FQCN must survive R8. Without these keeps, every HF Explorer / web-search / model-catalog HTTP call dies on release with `UnsatisfiedLinkError` (build is green; runtime crash). Verify post-R8: `grep com.dark.networking.WebNative app/build/outputs/mapping/release/mapping.txt` should show `WebNative -> com.dark.networking.WebNative` (identity mapping).
- Don't keep `com.dark.ai_sd.**` in `app/proguard-rules.pro`. The AAR was removed in commit `9d79a3b` — the rule is dead weight.
- Don't drop the `com.dark.gguf_lib.**` / `com.dark.ai_sherpa.**` keep + `dontwarn` block. The prebuilt AARs are already minified and rely on specific class+method names for JNI lookup.
- Don't strip `lockedUntilMs` and `wiped` from `PasswordScreen`. Both flow through `PasswordViewModel` from `VerifyResult.LockedOut(retryAtMs)` and `VerifyResult.Wiped`. The screen branches `wiped → WipedScreen → "Restart" → finishAffinity + Process.killProcess(myPid)` (post-`hardWipe`, `PolicyEngine.markTampered()` is latched and the process is unrecoverable in-place). `lockedUntilMs > now → LockedOutScreen` with a 500 ms-tick countdown that clears itself once the timestamp passes.
- Don't add a "set panic PIN" path that doesn't go through `SecurityManager.setPanicPin`. It gates on `securityMode == APP_PASSWORD` (NOT `session.isAllowed(AUTH_DISABLE)` — that was a non-deterministic timing trap; see Panic PIN section) and writes the second Argon2id hash into the same encrypted `AuthState` blob. UI lives in `Settings → Privacy` and only renders when `isLockEnabled`. `SettingsViewModel._panicPinSet` mirrors `security.hasPanicPin` and must reset to false on `setPassword`, `disableLock`, and `Wiped`. Same gate change applies to `clearPanicPin` and `disableLock`.
- Don't fan out `RemoteCallbackList` broadcasts from `InferenceService` without holding `sdBroadcastLock`. `RemoteCallbackList.beginBroadcast()` is not nestable — calling it from one thread while another is between `beginBroadcast()` and `finishBroadcast()` throws `IllegalStateException: beginBroadcast() called while already in a broadcast` and kills `:inference`. `startSdForwarding` launches five parallel collectors (backend / generation / isGenerating / upscale / runtimeSetup) on `Dispatchers.IO` — without serialisation they race on `sdClients.beginBroadcast()` and the service crash-loops immediately. The fix is `synchronized(sdBroadcastLock)` around the entire `fanoutSd` body; the same lock can serve `tnClients` if a similar pattern is ever added there.
- Don't bump `TAG_MSG_WEBSEARCH_RUN` away from `14` or `TAG_MSG_WEBSEARCH_STATE` away from `15`. Older messages without these tags decode with `webSearchRunId = null` and `webSearchState = ""`. New chat-message fields must use TAG ≥ 16.
- Don't reintroduce a runtime-only `webSearchEvents: Map<String, WebSearchEvent>` in HomeViewModel. The card's state must come from `WebSearchUiState.fromJson(message.webSearchState)` because (a) opening a different chat while a run is in flight should NOT bleed the running run's state into a completed chat's card; (b) after process restart, completed web-search cards must keep showing their Done state. `HomeViewModel.handleWebSearchEvent` is the single write point — looks up `(chatId, messageId)` via `webSearchMessages[runId]`, reads the message, applies the event, writes back.
- Don't lift the web-search lockdown. While `webSearchActive.value`, `HomeViewModel.sendMessage / loadModel / unloadModel` all early-return — the chat LLM is borrowed for both the GenerateQueries and Synthesize calls.
- Don't replace `WebNative.search` with `HttpURLConnection` or any other client. The `:networking` curl-impersonate Chrome116 fingerprint is the single allowed pipe for DDG. The 3 queries × 5 results contract assumes that backend.
- Don't expand the web-search flow back into a multi-iteration pipeline. The user-facing contract is "3 queries, snippets, answer, done". Adding iterations / fetches / per-page extraction is what the old Research pipeline was; it was deleted on 2026-05-15 because it was minutes-slow and barely better than snippet-only synthesis.
- Don't switch `WebSearchCoordinator` from `tryEmit` to suspending `emit(...)` in the `catch (CancellationException)` / `catch (Throwable)` blocks. The catch fires on a cancelled Job, and `withContext(Dispatchers.Default)` throws CE immediately on a cancelled context — so the Cancelled event never reaches the SharedFlow and the card freezes mid-flight. `_events` has 64-slot buffer; tryEmit always succeeds.
- Don't change `WebSearchPrompts.QUERY_LINE_REGEX`. The synthesis prompt asks for the format `1. <query>` / `2. <query>` / `3. <query>` and the regex `^\s*(?:\d+[.)\-:]|[-*•])\s+(.+)$` parses that AND tolerates common LLM deviations (`-`/`*`/`•` bullets, `:` after the number). Tightening the regex breaks smaller models that don't follow numbered-list instructions perfectly; loosening it picks up the LLM's preamble lines.
- Don't fall back to `java.net.HttpURLConnection` for any HuggingFace API call — search, model info, tree, raw README, tags-by-type, trending, quicksearch, resolve. Every HF request goes through `:networking` (`WebNative.fetch`) so it inherits the curl-impersonate Chrome116 fingerprint + bundled `cacert.pem` + strict cert verify. The hub is `repo/HuggingFaceApi.kt` (Hilt singleton class, not an object); `repo/hf/HfClient.kt` builds typed endpoints on top. `ModelCatalog` and `RepositoryValidator` inject `HuggingFaceApi`. Same rule applies for any future HF or non-HF HTTP target — `:networking` is the only allowed pipe.
- Don't change `WebNative.fetch` back to `Result<String>`. The contract is `Result<WebResponse>` where `WebResponse(status: Int, body: String, error: String?)`. Result.failure is reserved for transport-layer issues (DNS, TLS handshake, native call collapse). HTTP non-2xx comes back as `Result.success(WebResponse(status=4xx, ...))` — callers can react to 429 (rate limited) vs 404 (not found) vs 401/403 (auth). Old behavior of returning `null` on non-2xx silently masked HF API bugs (e.g. invalid `expand=` params returning 400) for years.
- Don't log full URLs (with query string) to `ANDROID_LOG_WARN` from `net_jni.cpp`. Use the `host_of(url)` helper. Search queries are user PII (typed model names, sometimes sensitive). Status code + host is the maximum log surface.
- Don't add per-keystroke quicksearch / autocomplete to the HF Explorer search bar. Search fires on the Search button, the IME `Search` action, or a filter chip touch — never on every typed character. The HF API has a 500-call/5min unauthenticated rate limit per IP; per-keystroke autocomplete burns it on a single typing session. Slider drags fire only on `onValueChangeFinished` (one call per drag). Post-filter sliders (`minDownloads`, `minLikes`, `recentDays` in `HfPostFilters`) update `visibleResults()` locally without an API call. `HfClient.quickSearch` exists for future use but the UI must not wire it to typing.
- Don't write the HF tags catalog (`/api/models-tags-by-type` payload) anywhere outside the encrypted `app_prefs` vault. Keys are `hf_tags_catalog_v1` (JSON string) and `hf_tags_catalog_v1_at` (Long unix-millis), 24h TTL. The catalog feeds the dynamic filter chips; falling back to `HfFilterTaxonomy` constants is OK but only for the brief window before the catalog hydrates. Plaintext-on-disk is forbidden — use the encrypted prefs API only.
- Don't pass any device-identifying value to `WebNative.fetch`'s `headers` map. The map is intended for protocol headers (`Accept`, `Accept-Encoding`, future `Authorization`). Adding `X-Install-Id`, `User-Agent` overrides with TN-identifying suffixes, or anything that would let HF (or any future server) fingerprint a specific install is a privacy regression.
- Don't add the `expand=tags`, `expand=downloads`, etc. parameters back to `searchUrl`. Those query params are for `/api/models/{id}/tree/...`, NOT `/api/models?search=...`. HF returns 400 when they're present on the list endpoint. The minimal list response already includes `id`, `author`, `gated`, `tags`, `pipeline_tag`, `downloads`, `likes`, `lastModified`, `createdAt` — sufficient for `HfModelSummary` without `full=true`.
- Don't snake_case the `sort` URL param. HF API wants camelCase: `trendingScore`, `lastModified`, `createdAt`, `downloads`, `likes`. The legacy code emitted `trending_score` / `last_modified` / `created_at` and HF returned 400. Source of truth is `HfSort.apiKey` in `repo/HfFilters.kt`.
- Don't add speculative URL params to `HuggingFaceApi.searchUrl` without verifying they're documented for `/api/models`. `apps=`, `inference_provider=`, `inference=warm`, `filter=region:us` (with `region:` prefix), `filter=dataset:foo` (with `dataset:` prefix), `pipeline_tag=` — all of these were added historically and have been removed because they trigger HF 400. Only documented params are kept: `search`, `author`, `filter` (plain tag values, stackable), `gated`, `num_parameters`, `sort`, `limit`, `skip`. If you need a new filter, verify it works against a curl-built URL first; don't add it to the URL builder on the assumption that HF tolerates it.
- Don't reintroduce post-filter sliders (`minDownloads`, `minLikes`, `recentDays`) into `HfFilters` / `HfPostFilters` / the VM. They were UI clutter without unlocking new searches — `sort=downloads` already gets the user "popular" results in the right order, and "recent" is `sort=lastModified`. The user explicitly asked to drop them; bringing them back without consent is a regression.
- Don't change the SoC-bucket mapping in `data/SocBucket.kt` without first verifying `xororz/sd-qnn` still uses the same `_8gen1.zip` / `_8gen2.zip` / `_min.zip` filename suffixes. We pull our QNN model archives directly from LocalDream's HF repo, so a bucket rename or new chip class needs a re-validation against the actual `tree/main` listing. 8gen3 / 8 Elite intentionally route to the `8gen2` bucket because Qualcomm's HTP V73 contexts are forward-compatible — don't add a new "8gen3" bucket without uploading new archives first.
- Don't show NPU image-gen rows on non-Snapdragon devices. `imageModels()` is gated on `SocBucket.bucket(soc) != null`. Falling back through to NPU rows on Tensor / Dimensity / Exynos would download QNN contexts that can't load — surface only the `xororz/sd-mnn` CPU/MNN variants on those devices.
- Don't show SDXL rows on a SOC that's not in `SocBucket.SDXL_ELIGIBLE_SOCS`. The SDXL contexts ship only as `_8gen3.zip` and Qualcomm AI Hub hasn't compiled them for older NPUs. The rest of the pipeline still uses 768-dim CLIP under the hood, so even if you forced the download, generation would crash on the dimension mismatch — keep both gates (SDXL row visibility + 2048-dim future pipeline work) in lock-step.
- Don't bypass the path-traversal check in `unzipInto`. Each entry's canonical path must start with `target.canonicalPath + File.separator` (or equal `target.canonicalPath` itself for the top-level dir). Skip `..` entries pre-canonicalization too. The QNN ZIPs from `xororz/sd-qnn` have flat layouts today, but a malicious mirror could craft `../../files/key.bin` entries; the check is the only line of defense.
- Don't unwrap the SDK runtime onto an external dir. `<filesDir>/ai_sd_runtime/` is the correct location — internal storage, app-private, survives backups (`allowBackup=false` is set elsewhere). The QNN `.so`s extracted there are device-specific and shouldn't roam.
- Don't open a fresh `StableDiffusionManager` per request. It's a process singleton (`getInstance(context)`), wrapped by Hilt's `ImageGenManager`. The init-mutex inside `ensureRuntime()` covers the qnnlibs.tar.xz extraction. Calling `initialize()` twice is a no-op but rebuilding the manager would tear down the persistent native sessions used across generations.
- Don't ship the release AAR yet. `ai_sd-release.aar` ran R8 on the SDK side and renamed `StableDiffusionManager.Companion.getInstance` past Kotlin's compile-time visibility. Keep `ai_sd-debug.aar` copied as `libs/ai_sd-release.aar` until `:ai_sd`'s `consumer-rules.pro` adds `-keep class com.dark.ai_sd.StableDiffusionManager$Companion { *; }` and the AAR is rebuilt.
- Don't remove the standalone QNN upscaler implementation. The original AAR's `nativeLoadUpscaler` for QNN was a stub that only stashed the model path — `nativeUpscaleImage` would then fail with "Upscaler model not provided" because the QnnModel was never built. Filled in 2026-05-08 by porting LocalDream's per-request load pattern: `sd_pipeline::loadStandaloneQnnUpscaler(modelPath)` in `model_loader.cpp` calls `createQnnModel(path, "upscaler")` + `initializeQnnApp("Upscaler", upscalerApp)` and assigns to the global `upscalerApp`, mirroring `main.cpp:3203` in LocalDream. Prerequisite: `sd_pipeline::ensureQnnSystemReady(systemLibPath, backendPath)` must be called first to populate `g_qnnSystemFuncs` + `g_backendPathCmd` — `ai_sd_jni.cpp::nativeInitRuntime` does this best-effort using `<libDir>/libQnn{System,Htp}.so`. The Kotlin caller (`ImageGenManager.loadUpscaler`) just calls `sdk.loadUpscaler(path, useMnn=path.endsWith(".mnn"), useOpenCL=...)` and the AAR's JNI dispatches to the right load path. Don't restore the .mnn-only IllegalStateException guard — the QNN path works now.
- Don't lift the upscaler input cap above 1024 max-edge in `ImageTaskViewModel.runUpscale`. 4× output of 2048² is 8192²×4 ≈ 256 MB which OOMs on bitmap allocation in `DiffusionManager.createBitmapFromRgb` even with `largeHeap=true`. The 1024 cap produces 4096²×4 ≈ 64 MB which fits comfortably. Combined with `android:largeHeap="true"` in the manifest, larger inputs MIGHT work on flagship devices, but the failure mode (OOM during bitmap return) is silent + crashy, so keep the cap and let users downscale beforehand if they need higher fidelity.
- Don't declare `commons-compress` and `xz` as anything weaker than `implementation` in `app/build.gradle.kts`. They are required by the AAR's runtime extraction path; `implementation(files(...))` AAR consumption does NOT pull transitive POM deps, so without explicit declarations the app crashes with `NoClassDefFoundError: org.tukaani.xz.XZInputStream` on first init.
- Don't switch image-gen tasks to a separate ViewModel per task. `ImageTaskViewModel` is the single VM for all four modes (Generate, Img2Img, Inpaint, Upscale) — sharing prompt / model selection / preview state across modes is intentional so the user can tweak a prompt then quickly switch from Generate to Edit without re-typing.
- Don't reuse the chat model picker for image-gen models. They're separate `ProviderType` rows (`IMAGE_GEN`, `IMAGE_UPSCALER`) on `ModelInfo` and live in `<filesDir>/sd_models/` / `<filesDir>/sd_upscalers/`, never in the GGUF chat model dir. The store routes them through `finalizeImageGenDownload` / `finalizeImageUpscalerDownload` and they should not appear in `chatModels` filters anywhere.

---

## Housekeeping

Whenever you change anything on the list below, update **this file** as part of the same change:

- Security architecture or threat model
- Any auth flow or API surface (SecurityManager, SessionHolder, PolicyEngine, AuthNative, BootIntegrity)
- Any sealed state layout (AuthState, NativeIntegrity manifest, license blob)
- New feature IDs or reshuffling of the pro-feature range
- New persistent keys in HXS (`app_prefs` or `app_bootstrap`)
- New integrity checks, obfuscation scheme, crypto primitives
- New DI bindings touching the security graph
- Changes to release-build hardening (ProGuard, signing, manifest flags)
- Anything in "Things still deferred" moving in or out of scope
- Any new "Things NOT to regress" item discovered along the way

If the CLAUDE.md update isn't part of your diff, the change isn't finished.
