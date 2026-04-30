# ToolNeuron — Repo Guide

Project memory for this repo. **When you change anything that affects future work — architecture, security behavior, new features, deprecated paths, public APIs, native JNI contracts, new scope — update this file as part of the same change.** A future session reads this to reconstruct intent; if it drifts, work breaks.

---

## Project scope

Privacy-first, offline-only on-device AI assistant. No Google Play services, no network telemetry, no analytics. In-scope pillars: on-device LLM chat, RAG over user documents, vision-language models (VLM), voice (TTS+STT), Remote Server with bundled web UI, HF Explorer. Out of scope (pivoted 2026-04-20): tool calling, image generation, plugin hub, Termux integration.

Package: `com.dark.tool_neuron` · minSdk 29 · targetSdk 36 · abiFilters `arm64-v8a`, `x86_64`.

Modules:
- `:app` — UI (Compose), viewmodels, DI graph, activities, services.
- `:hxs` — encrypted key-value store (Kotlin wrapper + C++ core).
- `:hxs_encryptor` — crypto + integrity primitives: Argon2id, AES-GCM/ChaCha20-Poly1305, BoringSSL, ML-KEM-768, ML-DSA-65, Ed25519, HKDF, mmap+mlock `SecureBuffer`, plus the native security policy / auth / boot-integrity stack.
- `:native-server` — embedded OpenAI-compatible HTTP server (cpp-httplib + nlohmann/json header-only via FetchContent, no BoringSSL / OpenSSL / zlib dep). Powers Remote Server mode.
- `:download_manager`, `:networking` — ancillary modules.

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
| `research_v1/` | `tn.research.user_key.v2` | Research documents + run snapshots. Two collections, one vault. |

**v1 → v2 migration is destructive.** Each repo's `openOrRebuild` tries `openEncrypted` with the v2 key. If that fails (existing v1 data sealed under the old non-signer-bound key), it wipes the vault dir and re-creates fresh. On first launch with a v2 build, an existing user loses their PIN, chat history, RAG attachments, and research docs — one time. The Keystore alias is preserved (so the DEK is still the same), only the per-vault user-keys change.

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
1. `integrity.scanProcessEnvironment()` — debugger / frida / xposed → `BootIntegrity.hardFail(reasons)` on positive.
2. `integrity.bootVerify()` — TOFU .so hash walk rebound to install identity. Manifest layout v2: `version(1) + signerHashLen(2)+signerHash + versionCode(8) + lastUpdateTime(8) + count(4) + (nameLen(2)+filename + hashLen(2)+hash)*`. If `{signerHash, longVersionCode, lastUpdateTime}` differs (or no manifest), re-TOFU and store. Within the same install identity, filename-set + hash mismatch → `FAIL_LIB_HASH` → hard fail. Filenames are stored (not absolute paths) since Android reshuffles `/data/app/~~…/`. `apk_signer_hash_v1` is also written for the future license-binding path.
3. `BootIntegrity.verifyHookBaselines()` — re-reads the prologue and compares; catches inline hooks.
4. `accessibilityGuard.scan()` — release hard-fails on a11y outside stock + OEM allowlist; debug warns.
5. `appLockObserver.register()`.

Root detection was removed from the boot path. `RootGuard.scan()` runs from `ScaffoldViewModel` on first launch; if rooted and `rootWarningShown == false`, `AppScaffold` shows a one-time `RootWarningDialog`. Acknowledging flips the flag.

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

Embedded HTTP server exposing the loaded LLM over an OpenAI-compatible API on the local network. Standalone replacement for the rejected Ktor PR. Phase 1 = LLM chat completions only; no embeddings, image gen, TLS, mDNS, tool calling.

### Process model

Three processes:

- `:app` — UI, chat ViewModels, `ServerController` (AIDL client), `InferenceClient` (AIDL client of `:inference`). HXS / Keystore live here; nothing crosses out.
- `:inference` — `InferenceService` (chat-side llama.cpp + sherpa-onnx). Untouched by the server.
- `:server` — `RemoteServerService`, its own `GGMLEngine`, the embedded native HTTP server, the bearer token in native memory. Foreground (`dataSync`, `stopWithTask="false"`). Independent: app crash doesn't kill it, server crash doesn't kill the app.

`:server` doesn't open HXS. The bearer token, model path, model config, web-UI HTML, docs HTML are all handed across via AIDL `start(configJson)`. When the user rotates the token in the UI, `:app` regenerates + persists, then pushes the new token to `:server` via `IRemoteServerService.rotateToken(newToken)`.

When the user swipes the app away, `:app` and `:inference` both die. `:server` keeps running because it's foreground **and** because `handleStart` self-calls `startService(Intent(this, RemoteServerService::class.java))` immediately before `startForeground`. That transitions it from bind-only to started lifecycle — without it, every binder client dying (which happens on swipe-to-kill) makes the service eligible for destruction even with a foreground notification. Reopening the app re-binds; `ServerController` calls `currentSnapshotJson()` and `recentRequestEventsJson(100)` to rehydrate the Server Screen with whatever's running right now.

Tapping the notification body opens `MainActivity` with `EXTRA_OPEN_SERVER_SCREEN=true`, which routes straight to the Server Screen. The Stop button on the notification fires `startService(action=ACTION_STOP)` against `:server`, which tears down in-process.

### Native (`:native-server`)

```
native-server/src/main/cpp/
  server_core.{h,cpp}        — httplib::Server lifecycle, pre/post-routing, route registry
  server_auth.{h,cpp}        — bearer token store + constant-time compare + 401/403
  server_crypto.{h,cpp}      — getrandom(2) RNG, const_time_eq, base64url, secure_zero (no BoringSSL)
  server_models.{h,cpp}      — catalog JSON cache + /v1/models envelope
  server_audit.{h,cpp}       — 128-entry ring buffer of request events
  server_rate_limit.{h,cpp}  — per-client token bucket (cap=30, refill=1/s) + auth-fail ban (20 fails → 1 h)
  server_webui.{h,cpp}       — set/clear/get/has HTML (mutex-protected std::string)
  gen_session.{h,cpp}        — gen-id Registry + per-session token queue (push_token / push_done / push_error / cancel) + blocking take(timeout)
  openai_schema.{h,cpp}      — ChatRequest parser, non-stream + SSE chunk responses, error envelope
  jvm_bridge.{h,cpp}         — JavaVM pin, JNI upcalls (startGeneration / cancelGeneration / onRequestEvent) via global jobject ref
  native_server.cpp          — JNI entry points + JNI_OnLoad
```

CMake fetches `cpp-httplib v0.18.5` and `nlohmann/json v3.11.3`, both header-only (`HTTPLIB_COMPILE=OFF`, `HTTPLIB_REQUIRE_OPENSSL=OFF`, `HTTPLIB_REQUIRE_ZLIB=OFF`, `JSON_BuildTests=OFF`). Same flags as `:hxs_encryptor`: c++17, `-fvisibility=hidden`, `-fstack-protector-strong`, LTO/gc-sections/icf release, `-march=armv8-a+crypto+sha2` on arm64, `-Wl,-z,max-page-size=16384`.

`POST /v1/chat/completions` flow:
1. pre_routing: rate-limit → 429; ban list → 403; bearer auth on every path except public allowlist → 401 with `note_auth_fail` (triggers ban after 20).
2. `openai_schema` parses body → validates `model` ∈ catalog → creates GenSession.
3. `jvm_bridge` upcalls `InferenceBridge.startGeneration(genId, messagesJson, paramsJson)`.
4. Java side runs `InferenceClient.generateMultiTurn(...)`; pushes each token via `nativeFeedToken(genId, tok)`; ends `nativeFeedDone(genId, "stop")`.
5. `stream=true` → httplib `set_chunked_content_provider` spools OpenAI SSE (`data: {…}\n\n`, terminator `data: [DONE]\n\n`). `stream=false` → single `chat.completion`.
6. post_routing: `audit::record` + `jvm::emit_request_event` → Kotlin `ServerInferenceBridge.onRequestEvent` → `ServerController.appendRequestEvent`.

### Web UI

Bundled at `app/src/main/assets/server_webui.html`. Loaded by `RemoteServerService.loadWebUiHtml()` on start; if asset read fails, `FALLBACK_WEBUI_HTML` (inline minimal HTML) is used. JNI: `nativeSetWebUiHtml(html)` pushes; `nativeClearWebUi()` clears on stop. Routes `/`, `/index.html`, `/webui` serve `webui::get_html()` and are added to the `auth::is_public_path` allowlist. The bundled UI is a single-file Material-3 chat client: sidebar history (`localStorage` key `tn.chats.v2`), markdown rendering with code-copy + math + tables, streaming with blinking cursor, settings dialog (bearer token, host, model display, "Clear all chats"), connection indicator polling `/health` every 30 s, auto dark/light via `prefers-color-scheme`.

### AIDL (`app/src/main/aidl/com/dark/tool_neuron/service/server/`)

- `IRemoteServerService.aidl` — start / stop / isRunning / currentSnapshotJson / rotateToken / recentRequestEventsJson / clearAuditLog / register-callback / unregister-callback. Everything passes as JSON strings; no parcelable plugin needed.
- `IRemoteServerCallback.aidl` — `onStateChanged(snapshotJson)` and `onRequestEvent(eventJson)`. Wire pushes from `:server` to `:app`.

### `:server`-side Kotlin (in `app/src/main/java/com/dark/tool_neuron/service/server/`, runs in `:server` process)

- `RemoteServerService.kt` — plain `Service`, NOT `@AndroidEntryPoint`. Holds its own `ServerEngine`, `ServerInferenceBridge`, and a `RemoteCallbackList<IRemoteServerCallback>`. Implements `IRemoteServerService.Stub` inline. Foreground promotion happens *inside* the AIDL `start(configJson)` call — pulls model id / name / path / config / token / port / bind mode / web-UI HTML / docs HTML from the JSON, calls `engine.load`, configures + starts the native HTTP server, publishes a `ServerSnapshot` to all callbacks. `onStartCommand` only handles `ACTION_STOP` (the notification's Stop button).
- `ServerEngine.kt` — wraps `com.dark.gguf_lib.GGMLEngine`. `load(path, configJson)`, `unload()`, `generateMultiTurnFlow(...)`, `setSampling`, `setSystemPrompt`, `stopGeneration`. Same JSON shape `InferenceService` parses for chat (contextSize, threadMode, flashAttn, cacheTypeK/V, sampling, kvSink/Window/Evict).
- `ServerInferenceBridge.kt` — extends `com.dark.native_server.InferenceBridge`. Constructed with a `ServerEngine` reference and an `onRequestEvent` callback. No `@Singleton`, no Hilt. Calls `engine.generateMultiTurnFlow` directly — never crosses AIDL.
- `ServerSnapshot` (in `RemoteServerService.kt`, internal) — phase / modelId / modelName / host / displayHost / lanHost / port / bindModeName / wifiActive / reason. Serialised to JSON for cross-process shipping.
- `BindResolver.kt`, `ServerTypes.kt` — same as before. `ServerInfo` and `ServerState` continue to model UI-side state in `:app`.

### `:app`-side Kotlin

- `ServerController.kt` — `@Singleton`, AIDL client. Binds to `:server` on first construction (Hilt eager-ish). Mirrors `IRemoteServerCallback.onStateChanged` into `state: StateFlow<ServerState>` and `onRequestEvent` into `requestEvents`. `start()` reads selected model + config + token + port + bind mode + asset HTML, packages as a JSON `ServerStartConfig`, calls `IRemoteServerService.start(configJson)`. `stop()` and `rotateToken()` forward via AIDL. Token generation is pure Kotlin (`SecureRandom` + base64url, no JNI in `:app`).
- `viewmodel/ServerViewModel.kt`, `ui/screens/server/ServerScreen.kt`, `ServerTopBar.kt` — unchanged. Their `ServerController` API surface matches what existed before.

### State sync / process-survival semantics

- `:app` calls `bindService(intent, conn, BIND_AUTO_CREATE)`. Android starts `:server`. AIDL stub returned. App registers callback, reads `currentSnapshotJson()` to rehydrate.
- App-killed-but-server-running case: re-launching the app re-binds; `currentSnapshotJson()` returns `phase=running` with all live fields, plus `recentRequestEventsJson(100)` for the log card.
- Server foregrounds *only* during AIDL `start`, not on `bindService` alone — so a brief "exists, idle, no notification" state is impossible (we never enter it).

### Lockdown

`ScaffoldViewModel.serverRunning: StateFlow<Boolean>` derived from `ServerController.state`. `AppScaffold` `LaunchedEffect(serverRunning, currentRoute)` re-routes to `ServerScreen` with `popUpTo(0) { inclusive = true }` when running and not already there. `BackHandler(running) {}` inside ServerScreen absorbs back. Drawer gesture hidden via `showDrawer = currentRoute == HomeScreen.route && !serverRunning`. Chat-side load/unload + sendMessage are gated on `!serverController.isBusy` so server-owned model state isn't perturbed.

### Auth + token lifecycle

- `ensureToken()` calls `nativeGenerateToken()` → `tn_sk_` + 32 random bytes base64url-encoded (getrandom(2)).
- Stored plaintext in encrypted HXS vault (`AppPreferences.serverToken`).
- Handed to native at start (`nativeSetToken`); zeroed on stop (`nativeClearToken`).
- 20 consecutive auth-fails from same client_addr → 1 h ban.
- Reveal in UI gates on `session.isAllowed(AUTH_VERIFY)`. Rotate generates a new token + invalidates the old.

### HXS server keys

`server_token` (String), `server_port` (String, validated [1024..65535], default `11434`), `server_bind_mode` (String, default `ALL_INTERFACES`), `server_auto_start` (Boolean, reserved), `server_configured` (Boolean), `server_selected_model` (String). All ride the same encrypted `app_prefs` vault.

### Phase-1 deliberate omissions

HTTPS / TLS, mDNS / Bonjour, QR-pairing, dynamic-model-load over the wire, streaming usage metrics, embeddings / image-gen / audio endpoints, request-log persistence to HXS.

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

## Research

Multi-iteration on-device research pipeline. The user types `/research <question>` (or flips the Research toggle on the bottom action bar). The app spawns a `ResearchCard` chat-message and a `ResearchCoordinator` run on `viewModelScope`. The coordinator drives this loop, capped by `prefs.researchMaxIterations`:

1. **Search** — `DdgSearch.search(query, prefs.researchResultsPerSearch, prefs.researchDdgLocale)` against DuckDuckGo HTML via `:networking` (`WebNative.search`). The `kl=` URL param defaults to `wt-wt` (worldwide) when locale is empty; otherwise it uses the configured locale (e.g. `us-en`, `de-de`).
2. **Fetch** — top-N URLs in parallel (concurrency 3). Each URL goes through `ResearchUrlUtil.canonicalize` first — `arxiv.org/abs/X` is rewritten to `arxiv.org/pdf/X.pdf`. URLs that look binary (`.pdf`/`.epub`/`.docx`/`.odt`/`.rtf` extensions, or matching content-type prefixes) take the **binary fetch path** (`WebNative.fetchBytes` JNI returns `[status, byte[] body, error, content-type]`); HTML/text URLs take the existing string-fetch path. Both paths reuse the curl-impersonate Chrome116 fingerprint + CA bundle.
3. **Extract** — for HTML, `HtmlReadability.extract(html)` (Kotlin Readability-lite: strip script/style/noscript/template/svg, prefer `<article>`/`<main>`/`<section>`, fall back to `<body>`, decode entities, collapse whitespace). For binary docs, `DocumentExtractor.extract(bytes, mimeHint=contentType, nameHint=urlBasename)` calls `GGUFNativeLib.nativeRagExtractText` — native PDF/DOCX/EPUB/ODT/PPTX/XLSX/RTF/HTML/text extraction via the embedded RAG ingest stack (no embedding model required for the extract path). Headless WebView is intentionally not used.
4. **Compress** — `ResearchModelClient.compress(blobs, question)` runs each fetched doc through `TextDigest.compress(text, query, targetTokens=200)` — pure-C++ extractive summarization (TF-IDF + TextRank centrality + cosine query relevance + lead bias + NE density + MMR redundancy elimination, sealed inside `gguf_lib`). No LLM call. Replaces the previous LLM-based compress that burned one inference call per fetched doc per iteration.
5. **Generate questions** — `ResearchModelClient.generateQuestions(ctx, prefs.researchMaxQuestions)` asks the LLM for ≤ N follow-ups; empty response stops the loop.
6. **Final** — `ResearchModelClient.finalDocument(...)` returns a `StructuredDoc` (title + summary + sections + sources + iteration log + footer). Persisted to the encrypted `research_documents` HXS vault.

The ResearchCard renders states: Plan → Search → Fetch → Compress → QuestionGen → Final → Done | Cancelled | Failed. Tapping "Open document" navigates to `NavScreens.DocumentViewer.routeFor(docId)` (read-only viewer in v1). The drawer's `Docs` quick-link opens `NavScreens.Documents` (archive of every persisted research doc).

### Model layer (the v1↔v2 swap point)

```
interface ResearchModelClient {
    suspend fun generateQuestions(ctx: ResearchContext, max: Int): List<String>
    suspend fun compress(blobs: List<FetchedDoc>, question: String): String
    suspend fun finalDocument(allCompressed, question, sources, iterationsUsed, modelName, totalFetchedBytes, durationMs): StructuredDoc
}
```

v1 impl is `ChatLlmResearchClient`. **Compress is no longer a model call** — it routes through `com.dark.gguf_lib.TextDigest.compress(text, query)` (pure-CPU C++ extractive summarizer in the `gguf_lib` AAR). `generateQuestions` and `finalDocument` still wrap `InferenceClient.generate(prompt, maxTokens)`, parsing questions line-by-line and final-doc JSON with fence-strip + `{`-find. The bound impl, exclusive — those two phases borrow the active chat GGUF in `:inference`. The single-research lockdown is what keeps that safe (no chat-side `sendMessage` while a run is in flight). v2 (`NativeResearchClient` + a custom `:research` process + `.rmg` engine) was prototyped on 2026-04-29 and pulled — SmolLM2-135M was below the capability threshold for instruction-following summarization. Future v2 candidates would need a 360M+ model; the architecture seam (`ResearchModelClient` interface) is intentionally preserved so the swap remains a one-line `@Binds` change.

### TextDigest — extractive compression engine

Lives entirely in `gguf_lib/src/main/cpp/text_digest/text_digest.{h,cpp}` (~430 LOC, pure C++17 STL, no llama.cpp/ggml/ML dependency) with a Kotlin `object` facade `com.dark.gguf_lib.TextDigest`. Public API: `suspend fun compress(text: String, query: String?, options: TextDigest.Options = …): String`. JNI export is `GGUFNativeLib.nativeTextDigest` — kept by `consumer-rules.pro` (under `native <methods>` of `GGUFNativeLib`) and the explicit `-keep class com.dark.gguf_lib.TextDigest` rule.

Algorithm (per call, all O(N²) over sentences, N ≤ 80):

1. Sentence segmentation — UTF-8-safe walk; sentence-end `.!?` followed by whitespace + uppercase/quote/parenthesis is a split. Abbreviation guard (`Mr|Dr|U.S|e.g|etc|…`) and decimal-period guard (`1.5 million`) prevent false splits. Hard cap at `max_sentences=80`, drop sentences shorter than 20 chars or longer than 600 chars (long ones are subdivided).
2. Tokenize each sentence — drop stopwords (~150-word English list inline), drop length<2 / length>40, ASCII-lowercase, accept UTF-8 bytes ≥0x80 as token chars (keeps non-English content tokenizing without ICU).
3. Build TF-IDF — corpus is the document's own sentences. `tf = 1 + log(count)`, `idf = log((N+1)/(df+1)) + 1`. L2-normalize per-sentence vectors. Sparse representation `vector<pair<term_id, weight>>` sorted by term_id.
4. Pairwise cosine similarity — NxN matrix, sparse-vector dot product (merge-walk).
5. TextRank centrality — row-normalized similarity matrix, `r ← (1-d)/N + d * Σⱼ (sim[j,i]/rowSum[j]) * rⱼ`, damping `d=0.85`, up to 30 iterations or until `Σ |Δr| < 1e-4`.
6. Query relevance — query → TF-IDF (same vocab/IDF), cosine to each sentence vector. Skipped if query empty.
7. Lead bias — `1/(1+i)` decay, favors paragraph-leading sentences (good for HTML/research-paper structure).
8. NE density — fraction of capitalized non-leading words in the sentence (proxy for "this sentence names entities"). The first word's capitalization is excluded so "The cat sat." doesn't score 1/3.
9. Combined score — each component normalized to its max=1, then weighted sum: `α·query + β·centrality + γ·lead + δ·NE`. Defaults `α=0.40, β=0.30, γ=0.15, δ=0.15`. When the query is blank, α reweights into β so centrality dominates.
10. MMR selection — greedy: `mmr_score = λ·score - (1-λ)·max(sim_to_already_picked)`, λ=0.7. Stop when token budget (`chars/4` approx) reached or no candidates remain. Reorder picks by original sentence position before joining.

Verified on a 941-char input: extracted three on-topic sentences (179 chars) for query "How does self-attention work?" — 5x compression with sensible content.

Replacement of LLM compress saves N inference calls per iteration (where N = fetched docs). For a 5-iteration × 5-results-per-search run, that's 25 LLM calls dropped → research finishes in seconds instead of minutes on a Pixel 8 with a 4B model.

### Persistence

**Single encrypted vault** at `<filesDir>/research_v1/`, sealed under `HKDF(DEK, "tn.research.user_key.v1")`. Two collections inside:

- `research_documents` — tags `1=docId, 2=title, 3=originChatId, 4=originMessageId, 5=question, 6=structuredJson, 7=createdAt, 8=durationMs, 9=modelId, 10=iterationsUsed`. `structuredJson` is the `StructuredDoc.toJson()` blob.
- `research_runs` — tags `1=runId, 2=docId, 3=question, 4=phase, 5=iterationLogJson, 6=startedAt, 7=finishedAt, 8=cancelReason`. Snapshot updated on every phase transition.

The single-storage / multiple-collections layout is mandatory: hxs.cpp keeps a process-singleton `g_encryptor_ref` + `g_crypto.ctx` (lines 62, 245-259). Every `nativeOpenEncrypted` call `DeleteGlobalRef`s the previous AEAD callback's user-key jobject. If a single Kotlin repository opens two vaults back-to-back via two `HexStorage` instances, the first vault's `Collection` snapshots a now-stale jobject and the next flush on it triggers `JNI ERROR: jobject is an invalid global reference` → SIGABRT (observed on 2026-04-28). Match the existing pattern (`ChatRepository` = `chats` + `messages` in one vault).

`ChatMessage.researchRunId: String?` rides on `TAG_MSG_RESEARCH_RUN = 14`; `ChatMessage.researchState: String` (JSON-serialized `ResearchUiState`) rides on `TAG_MSG_RESEARCH_STATE = 15`. Both live on the existing chat messages collection and decode with zero-fill defaults for backward compat. When `researchRunId != null`, `ChatMessageList` renders `ResearchCard` instead of `MessageBubble`, and the card derives entirely from `ResearchUiState.fromJson(message.researchState)` — there is **no** runtime-only `researchEvents` map. `HomeViewModel.handleResearchEvent` reads the owning message via `chatRepo.getMessageById(messageId)`, applies the incoming `ResearchEvent` to the persisted state, and writes the updated message back. A `runId → (chatId, messageId)` map (`HomeViewModel.researchMessages`) is populated on `startResearch` and evicted on Done/Cancelled/Failed so cross-chat updates land on the right message even when the user is viewing a different chat. Done research cards survive process restart unchanged because the terminal state is on the message.

**Raw fetched HTML never touches disk.** Compressed-text intermediate lives only in the run's coroutine scope. Source URLs are persisted via `structuredJson`; bodies are not.

### Cancellation & lifecycle

- The ResearchCard's "Stop" button calls `HomeViewModel.cancelResearch(runId)`. ViewModel **optimistically writes `phase = PHASE_STOPPING`** to the message before delegating to `ResearchCoordinator.cancel(runId)`, so the card instantly flips to "Stopping research…" with no Stop footer. `ResearchUiState.applyEvent` is a one-way gate while in STOPPING — only Done/Cancelled/Failed can move the state out, so a late-arriving Compress event (in flight when Stop was pressed) won't bounce the card back to Compress.
- `ChatLlmResearchClient.runInference` catches `CancellationException`, calls `InferenceClient.stopGeneration()`, then rethrows. Without this, the inference service keeps generating tokens after the coroutine is cancelled and Stop becomes effectively a no-op for the multi-second LLM phases (compress / questions / final-doc).
- The coordinator's `catch (CancellationException)` and `catch (Throwable)` blocks **must use `_events.tryEmit(...)`** — the suspending `emit(...)` wrapper goes through `withContext(Dispatchers.Default)` which throws CancellationException immediately on a cancelled Job, so the Cancelled event never lands and the card hangs in its last in-flight phase. `_events` has `extraBufferCapacity = 128`, so tryEmit is non-blocking and always succeeds in practice. Saved snapshot writes are wrapped in `runCatching` for the same reason — sync HXS calls don't suspend, but they can hit other failure modes during teardown that we don't want to mask the original CE.
- HTTP fetches are bounded by `FETCH_TIMEOUT_MS = 15s` per URL — JNI calls into `WebNative.fetch` aren't cooperatively cancellable, so a Stop pressed mid-fetch storm waits up to that timeout per in-flight fetch (3 in parallel). Acceptable v1 behavior.
- `ResearchBackgroundObserver` (a `DefaultLifecycleObserver` on `ProcessLifecycleOwner`, registered from `TNApplication.onCreate` in main process) calls `coordinator.cancelAll()` on `ON_STOP` when `prefs.researchCancelOnBackground == true` (default true).
- Cancelled runs are discarded — no resume in v1.

### Single-research lockdown

Only one research run at a time. `HomeViewModel.researchActive: StateFlow<Boolean>` is derived from `researchCoordinator.activeRuns.isNotEmpty()`. `startResearch` early-returns if it's already true. While a run is active, `sendMessage`, `loadModel`, and `unloadModel` all early-return so chat-side inference and model swaps cannot interfere with the run's borrowed chat LLM. `serverController.isBusy` and `_isGenerating.value` keep their existing gates; `researchActive` stacks on top.

### Settings keys (encrypted `app_prefs`)

- `research_max_iterations` Int default 5 (clamped 1..10)
- `research_max_questions` Int default 4 (clamped 1..6)
- `research_results_per_search` Int default 5 (clamped 3..10)
- `research_ddg_locale` String default "" (empty → DDG `kl=wt-wt` worldwide; non-empty (`us-en`, `de-de`, `fr-fr`, etc.) is passed straight to the `kl=` URL parameter)
- `research_cancel_on_bg` Bool default true
- `active_research_model` String default "" (empty = mirror active chat model)

The Settings → Research section exposes each as a `SettingsItem.Choice` (max-iter, max-q, per-search, model) or `SettingsItem.Toggle` (cancel-on-bg).

### File map

- `model/{StructuredDoc,ResearchEvent,ResearchPhase,FetchedDoc,ResearchContext}.kt` — pipeline data classes + `ResearchDocument` aggregate.
- `repo/ResearchRepository.kt` — encrypted CRUD over both vaults.
- `repo/research/{ResearchModelClient,ChatLlmResearchClient,HtmlReadability,DdgSearch,ResearchPrompts,ResearchModule,DocumentExtractor,ResearchUrlUtil}.kt` — pipeline support + Hilt binding.
- `viewmodel/{ResearchCoordinator,DocumentViewerViewModel,DocumentsViewModel}.kt`
- `ui/screens/research/{ResearchCard,ResearchCardStates}.kt`
- `ui/screens/document/{DocumentViewerScreen,DocumentViewerTopBar}.kt`
- `ui/screens/documents/{DocumentsScreen,DocumentsTopBar}.kt`
- `data/ResearchBackgroundObserver.kt`
- Modified: `ChatMessage` (+ researchRunId), `ChatRepository` (TAG 14), `HomeViewModel` (toggle, slash parse, coordinator wiring, event mirror), `HomeScreen{Body,BottomBar}`, `NavScreens` (Documents + DocumentViewer routes), `TNavigation`, `AppTopBar`, `AppScaffold` + `ChatDrawer` (Docs quick-link), `AppPreferences` (research_* keys), `SettingsViewModel` (researchSection), `TNApplication` (background observer), `:networking/WebNative` + `net_jni.cpp` (`nativeFetch`).

### v2 plan

`:research_engine` is a not-yet-built native module that will house a from-scratch CPU inference engine (mmap loader, custom int4/int8 quant, KV cache, threadpool) and a custom small MoE model (~100M-500M params, two experts: question-gen + compression, ~32k BPE tokenizer, 4k ctx, English-only). Implementation studies llama.cpp's design patterns (compute-graph DAG, GGUF bundle layout, KV cache layout, quant block design) but does **not** vendor llama.cpp code. v2 swaps `bindResearchModelClient(ChatLlmResearchClient)` to `NativeResearchClient`; UX, persistence, and event flow stay unchanged.

A first attempt (rm-graph engine + SmolLM2-135M-Int4) was wired and reverted on 2026-04-29 — at 135M parameters the model parroted prompt fragments instead of summarizing, returning empty question generations after iteration 1. Next attempt should target ≥360M parameters.

---

## Setup flow — theme step

Sequence: Intro → SetupScreen (lock mode) → (SetupPassword if password chosen) → **SetupTheme** → ModelSetup → Home.

- Route: `NavScreens.SetupTheme("setup_theme")`.
- Screen: `ui/screens/setup_screen/SetupThemeScreen.kt`. VM: `viewmodel/SetupThemeViewModel.kt` (injects `ThemeController`). Selection commits immediately.
- Continue button: `SetupThemeBottomBar.kt`, dispatched from `AppBottomBar.kt`. AppScaffold handoff: `onSetupComplete → SetupTheme` (TNavigation); `SetupTheme → ModelSetup` (AppBottomBar callback wires the navigation).
- Top bar: `SetupScreenTopBar()` dispatched from `AppTopBar` on `SetupTheme.route`.
- No "themeSetupDone" pref — defaults are valid on first launch.

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
- Don't break the setup-flow handoff (`onSetupComplete → SetupTheme`, `onThemeSetupComplete → ModelSetup`, `onModelSetupComplete → Home`).
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
- Don't re-add a plaintext HXS container for the bootstrap DEK. Raw XOR-masked `k.bin` only.
- Don't skip `migrateLegacyIfNeeded()` in `AppKeyStore.init`.
- Don't drop the signer-binding salt from any user-key derivation. Every `encryptor.deriveKey(ikm = dek, salt = ?, info = ?)` call in app code MUST use `salt = keyStore.installSignerHash()` (not `salt = dek`, not `salt = null`). Sites: `AppPreferences.init`, `AppPreferences.deriveAuthKey`, `DocumentRepository.init`, `RagKeywordIndex.init`, `ResearchRepository.init`, `ChatRepository.init`, `SourceFileVault.keyFor`. Without this salt, a same-device replaced-APK attack (root + repack with attacker's cert) can unwrap the DEK via the inherited uid-scoped Keystore alias and decrypt every vault. Salting with the signer hash means the patched APK derives a different user-key and AEAD fails — the data stays sealed.
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
- Don't make `/v1/models` return the full installed catalog. Phase 1 exposes only the currently-loaded model. Dynamic load over the wire requires explicit opt-in.
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
- Don't sort the drawer quick-links past six. `SpaceEvenly` eats touch targets below ~360 dp at 7+.
- Don't let `VoiceModelManager` construct `AppPreferences` eagerly. `dagger.Lazy<AppPreferences>`.
- Don't add `*.md` spec / plan / research / TODO docs at the repo root. Project memory lives here. Implementation roadmaps belong in conversation context.
- Don't auto-scroll on every streaming token via `LazyListState.scrollToItem(index)`. That fights the user's drag and locks manual scroll mid-generation. The pattern is: track `stickToBottom` from `snapshotFlow { isScrollInProgress }` (re-evaluate when scroll settles via `!canScrollForward`), gate the auto-scroll `LaunchedEffect` on `stickToBottom && !isScrollInProgress`, and use `scrollToItem(last, scrollOffset = Int.MAX_VALUE)` so the bottom of the growing streaming bubble stays in view.
- Don't drop the `:native-server` consumer-rules.pro keeps for `NativeServer`, `NativeServer$*`, `InferenceBridge`, `BindMode`. The native HTTP server's JVM bridge invokes `InferenceBridge.startGeneration / cancelGeneration / onRequestEvent` via JNI on a `NewGlobalRef`'d jobject; renaming or stripping breaks dlsym at runtime.
- Don't keep `com.dark.ai_sd.**` in `app/proguard-rules.pro`. The AAR was removed in commit `9d79a3b` — the rule is dead weight.
- Don't drop the `com.dark.gguf_lib.**` / `com.dark.ai_sherpa.**` keep + `dontwarn` block. The prebuilt AARs are already minified and rely on specific class+method names for JNI lookup.
- Don't strip `lockedUntilMs` and `wiped` from `PasswordScreen`. Both flow through `PasswordViewModel` from `VerifyResult.LockedOut(retryAtMs)` and `VerifyResult.Wiped`. The screen branches `wiped → WipedScreen → "Restart" → finishAffinity + Process.killProcess(myPid)` (post-`hardWipe`, `PolicyEngine.markTampered()` is latched and the process is unrecoverable in-place). `lockedUntilMs > now → LockedOutScreen` with a 500 ms-tick countdown that clears itself once the timestamp passes.
- Don't add a "set panic PIN" path that doesn't go through `SecurityManager.setPanicPin`. It gates on `securityMode == APP_PASSWORD` (NOT `session.isAllowed(AUTH_DISABLE)` — that was a non-deterministic timing trap; see Panic PIN section) and writes the second Argon2id hash into the same encrypted `AuthState` blob. UI lives in `Settings → Privacy` and only renders when `isLockEnabled`. `SettingsViewModel._panicPinSet` mirrors `security.hasPanicPin` and must reset to false on `setPassword`, `disableLock`, and `Wiped`. Same gate change applies to `clearPanicPin` and `disableLock`.
- Don't bypass `ResearchModelClient` from anywhere in the research pipeline. The whole point of the abstraction is the v1↔v2 swap: today `ChatLlmResearchClient` runs every prompt through `InferenceClient.generate`; tomorrow a `NativeResearchClient` will dispatch into a dedicated `:research` process for a custom engine. If you call `InferenceClient.generate` directly from `ResearchCoordinator` you've welded v1 to the chat LLM forever.
- Don't add `ProviderType.RESEARCH` back without also adding the matching engine + service stack. The v2 attempt (rm-graph, SmolLM2-135M, `:research` process, `ai_rmg` AAR) was reverted because the model was below the capability threshold for instruction-following summarization. The enum stays at GGUF/TTS/STT/EMBEDDING until a real v2 candidate ships with a verified-good model.
- Don't write fetched HTML or compressed-text intermediates to disk. Only the final `StructuredDoc` (sealed under `tn.research_documents.user_key.v1`) and the per-run snapshot (sealed under `tn.research_runs.user_key.v1`) are persisted. Source URLs ride in `structuredJson`; bodies are RAM-only.
- Don't downgrade the research vault to plaintext HXS. `research_v1/` is encrypted via the same DEK + HKDF-derived user-key path that `DocumentRepository` and `AppPreferences` use.
- Don't split the research data into a second `HexStorage` instance. hxs.cpp is process-singleton — every `openEncrypted` rebinds `g_crypto.ctx` and `g_encryptor_ref`, but Collections snapshot those at construction. The current crash mitigation is "never DeleteGlobalRef the previous refs" (`hxs.cpp:215, :246, :291`) which leaks ~64 bytes per vault open but keeps prior snapshots valid. Even with that mitigation, multiple `HexStorage` instances inside one repo is wasteful. Use `ResearchRepository`'s pattern: ONE `HexStorage()` opening ONE vault path, with two collections inside (`research_documents` and `research_runs`).
- Don't reintroduce `DeleteGlobalRef` on `g_encryptor_ref` or `g_crypto.ctx` in `hxs/src/main/cpp/hxs.cpp`. ART recycles freed slot indices with bumped serials — the next AEAD callback on a Collection that snapshotted the old ctx hits "JNI ERROR: stale reference with serial X v. current Y" → SIGABRT. Observed crashing both `ResearchRepository.saveDocument` and `AppPreferences.putBoolean`/`flushAll` flows on 2026-04-28. The proper fix is per-instance native handles (each `HexStorage` Kotlin instance gets its own `nativeHandle` carrying its `Manifest` + `Collections` + `g_crypto` + global refs); until that lands, leaking the refs is the safe path.
- Don't reorder or skip the research pipeline phases. The contract is Search → Fetch → Extract → Compress → QuestionGen, repeating per iteration, then Final. The card and `ResearchPhase` snapshot states depend on this order — out-of-order emit will desync the UI animation.
- Don't bump `TAG_MSG_RESEARCH_RUN` away from `14` or `TAG_MSG_RESEARCH_STATE` away from `15`. Older messages without these tags must continue to decode with `researchRunId = null` and `researchState = ""`. Adding new chat-message fields uses TAG ≥ 16.
- Don't reintroduce a runtime-only `researchEvents: Map<String, ResearchEvent>` in HomeViewModel. The card's state must come from `ResearchUiState.fromJson(message.researchState)` because (a) opening a different chat while a run is in flight should NOT bleed the running run's state into a completed chat's card; (b) after process restart, completed research cards must keep showing their Done state — a runtime map collapses to empty and every card regresses to "Planning research…". Persistence on the message itself is the contract. `HomeViewModel.handleResearchEvent` is the single write point: lookup `(chatId, messageId)` via `researchMessages[runId]`, read the message via `chatRepo.getMessageById`, apply the event, write back.
- Don't drop the `try { ... } catch (CancellationException) { InferenceClient.stopGeneration(); throw }` wrapper in `ChatLlmResearchClient.runInference`. Without it, `coordinator.cancel(runId)` cancels the coroutine but the underlying `:inference` service keeps decoding tokens for the rest of the LLM call (compress / questions / final-doc — the dominant time cost), so the user's Stop press takes seconds-to-tens-of-seconds to land. The wrapper makes Stop responsive in the LLM phases; HTTP-fetch phases still wait on the per-URL timeout.
- Don't switch the coordinator's catch-block emits back to suspending `emit(...)`. The catch fires on a cancelled Job, and `withContext(Dispatchers.Default)` throws CE immediately on a cancelled context — so the Cancelled event never reaches the SharedFlow, and the card freezes mid-flight even though native logs show "Generation stop requested". Use `_events.tryEmit(ResearchEvent.Cancelled(...))` (and likewise for `Failed`) — the SharedFlow has 128-slot buffer, tryEmit always succeeds, and the event lands. Same applies to the snapshot write — wrap in `runCatching` so a teardown-time HXS failure can't shadow the original CE.
- Don't drop the optimistic `PHASE_STOPPING` write in `HomeViewModel.cancelResearch`. The native cancel can take 1–3s to actually wind down (interrupt point in prompt-eval), and during that window the user wants visible feedback that their tap was received. The optimistic write transitions the card to "Stopping research…" with no Stop footer instantly; the real Cancelled event overrides it when it arrives. The `applyEvent` gate ensures non-terminal events (a late-arriving Compress, etc.) can't bounce the state out of STOPPING.
- Don't lift the single-research lockdown without thinking it through. While `researchActive.value`, `HomeViewModel.startResearch / sendMessage / loadModel / unloadModel` all early-return. The chat LLM is borrowed for the duration of the run (compress + questions + final-doc); a chat-side `sendMessage` would race the borrowed model, and a `loadModel` / `unloadModel` would yank state mid-run. The lockdown stacks on top of the existing `serverController.isBusy` and `_isGenerating.value` gates.
- Don't move `nativeFetch` out of `:networking/WebNative`. The curl-impersonate Chrome116 fingerprint + CA bundle setup lives there; reimplementing fetch in Kotlin via `HttpURLConnection` would lose the fingerprint and leak the default Android UA on every research request. The JNI wrapper just calls `net::http_execute(...)` — same backend the DDG client uses.
- Don't expose research from any process other than main. The coordinator depends on `:inference` AIDL via `InferenceClient`, which is bound from the main process; spawning a run from `:server` or `:inference` would deadlock on the binder.
- Don't drop the `ResearchBackgroundObserver` registration from `TNApplication.onCreate`. Without it, the cancel-on-background pref does nothing and a half-finished run keeps churning network + LLM after the user leaves the app.
- Don't store the active research model id by mirroring `active_chat_model`. The `active_research_model` key is intentionally separate so v2 can install a dedicated research model alongside (not in place of) the chat GGUF.
- Don't put the `TextDigest` engine back in Kotlin or `:app`. It lives in `gguf_lib/src/main/cpp/text_digest/` so other consumers (including future v2 / future apps consuming the AAR) get the same compression engine via the same JNI symbol. The Kotlin facade `com.dark.gguf_lib.TextDigest` is required because R8 in the gguf_lib release minifies; the keep rule (`-keep class com.dark.gguf_lib.TextDigest { *; }` in `consumer-rules.pro`) preserves it. The `nativeTextDigest` external fn is preserved by the existing `native <methods>` rule on `GGUFNativeLib`.
- Don't reintroduce `ResearchPrompts.compress` or its `MAX_PAGE_CHARS` constant. Compress is now `TextDigest.compress(text, query)`; the LLM prompt path is dead. Bringing it back wastes inference calls and re-couples compress to `InferenceClient.isModelLoaded`.
- Don't drop the binary-fetch path from `ResearchCoordinator.fetchAll`. URLs that look like binary docs (`.pdf`/`.epub`/`.docx`/`.odt`/`.rtf` extensions, or matching `application/pdf|epub|*officedocument*|*opendocument*` content-type prefixes) MUST go through `ddgSearch.fetchBytes` + `DocumentExtractor.extract(bytes, mimeHint, nameHint)`. The existing string-fetch path mangles binary at the JNI `NewStringUTF` boundary (any 0xFF byte becomes U+FFFD, embedded NUL truncates) — feeding that to `extractText` would get noise.
- Don't bypass `ResearchUrlUtil.canonicalize`. The `arxiv.org/abs/X → arxiv.org/pdf/X.pdf` rewrite is what turns DDG search hits for arXiv abstracts into actually-extractable papers. Skipping it lands you on the abstract HTML page (which Readability extracts to a thin metadata blurb) instead of the full paper PDF.
- Don't add a `nativeFetchBytes` variant that returns the body as `String`. The whole point of `WebBytesResponse` is that `body: ByteArray` is lossless across the JNI boundary. The C++ side hands the body in as a `std::string` (which curl populates with arbitrary bytes), and the JNI binding wraps it as `byte[]` via `SetByteArrayRegion`. Going through `NewStringUTF` again breaks PDF/DOCX/EPUB extraction.
- Don't drop the Content-Type header scrape (`find_header(resp->headers, "Content-Type")`) from `nativeFetchBytes`. `ResearchUrlUtil.looksBinaryDoc(url, contentType)` and `DocumentExtractor.extract(bytes, mimeHint=…)` both rely on it; without the actual server-reported MIME, sites that serve `.pdf` URLs with a `text/html` redirect-shim get misrouted, and `nativeRagDetectKind` has to guess from bytes alone (less accurate).
- Don't pass `wt-wt` as a hard-coded fallback if `prefs.researchDdgLocale` is non-empty. The `kl=` parameter on the DDG HTML endpoint is what scopes results to a region; the user explicitly set it, so respect it. The C++ default (`locale.empty() ? "wt-wt" : locale`) is only for when the pref is genuinely empty.
- Don't bump the `nativeSearch` arity without bumping the C++ `Java_com_dark_networking_WebNative_nativeSearch` signature in lock-step. Java/Kotlin signature mismatches against the JNI binding fail at runtime via `UnsatisfiedLinkError` ("no implementation found"), not at compile time. Same applies to `nativeFetchBytes`.
- Don't drop the `synthesizeFollowUp` fallback in `ResearchCoordinator`. After moving to extractive `TextDigest` for compress, smaller chat models often return empty follow-ups at iter 2 (the prompt explicitly tells them "return empty if no useful follow-ups remain"). Without the synthesizer, runs cap at 2 iterations even when `prefs.researchMaxIterations = 5`. The fallback rotates through 6 angle templates (`Latest developments`, `Detailed examples`, `Background and history`, `Different perspectives`, `Specific case studies`, `Common criticism and limitations`) keyed on the original question, skipping ones already in `previousQuestions`. Returns null only when every template has been used — *that* is the legitimate stop condition.
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
