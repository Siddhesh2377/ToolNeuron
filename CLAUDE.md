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
- `:download_manager`, `:networking`, `:rag-doc-lib` — ancillary modules.

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

### AppPreferences — encrypted HXS + sealed AuthState

`HexStorage.openEncrypted(path, appKey=DEK, userKey=HKDF(DEK, info="tn.app_prefs.user_key.v1"), encryptor)`. Auth-critical state rides a second AEAD layer: `writeAuthState`/`readAuthState` use key `HKDF(DEK, info="tn.app_prefs.auth_key.v1")` and AAD `"tn.auth_state.v1"`. Ordinary flags (`onboarding_complete`, `tc_accepted`, `setup_done`, server settings, etc.) are plain encrypted records.

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

`hardWipe()`: `session.clear()` → `PolicyEngine.invalidateSession()` → `PolicyEngine.markTampered()` → `prefs.clearAuthState()` → `keyStore.wipe()`.

Panic PIN: `SecurityManager.setPanicPin(pin)` (active session required) writes a second Argon2id hash. `verifyPassword` tries real first; on mismatch, if `hasPanic`, tries panic. Panic match → `hardWipe()` + `VerifyResult.Wiped` (UX-indistinguishable from "attempts exceeded").

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

Dedicated screen replacing the inline Settings search (2026-04-25 redesign).

- VM: `viewmodel/HfExplorerViewModel.kt`. Search query, results, history (persisted as JSONArray on `prefs.hfSearchHistory`, max 20). Filters: `HfSort` (RELEVANCE, DOWNLOADS, LIKES, AUTHOR_AZ), `HfFileFilter` (ALL, GGUF, MMPROJ), `HfFileSizeBucket` (ANY, UNDER_1GB, UNDER_4GB, UNDER_8GB), `HfDownloadsBucket` (ANY, 100+, 1k+, 10k+, 1M+), `authorFilter`, `hideGated`, `showGatedOnly`, `hideAdded`. `loadRepoDetail`, `visibleResults`, `visibleFiles`, `addRepository`, `resetFilters`.
- Routes: `NavScreens.HfExplorer`, `NavScreens.HfRepoDetail("hf_repo/{repoPath}")` with `routeFor(repoPath)` URL-encoding the path.
- `ui/screens/hf_explorer/HfExplorerScreen.kt` — hero search bar with `ImeAction.Search` + `KeyboardActions(onSearch=…)`, quick sort chips, collapsible Advanced Filters, history strip, result cards (circular author-initials avatar, downloads / likes pills, gated badge), empty state.
- `ui/screens/hf_explorer/HfRepoDetailScreen.kt` — header (downloads / likes / total GGUF size), file filter card (type + size bucket), file rows (monospace path + size).
- `ui/screens/model_store/RepositorySettings.kt` carries `HfExplorerLauncherCard` — primary-tinted Surface, 40 dp circular icon badge, two-line text + arrow.
- HTTP: `repo/HuggingFaceApi.kt` — `fetchJson`, `fetchJsonArray`, `fetchJsonArrayResult`, `headStatus`; URL builders `modelInfoUrl`, `modelTreeUrl`, `resolveFileUrl`, `searchGgufUrl`. Replaced 60+ LOC of duplicate boilerplate.

---

## RAG attachments

The Action Window's third tab is **Attach** (formerly Tools). It shows the current chat's attachments and a single full-width "Add attachment" button. Tapping it opens `AttachmentPickerDialog` with two paths:

- **Pick from previous chats** — opens `PrevChatsPickerDialog`, a full-screen `Dialog` with a list grouped by source chat title. Tapping a row re-attaches the document to the active chat.
- **Pick from storage** — launches `ActivityResultContracts.OpenDocument` with the existing MIME filter (text/*, pdf, json, xml, rtf, epub, odt, docx, pptx, xlsx).

### Persistence model

Every attached document is stored content-addressed by SHA-256 of its bytes:

- `<filesDir>/chat_documents/sources/<sourceId>.bin` — raw bytes, written once per unique content; multiple chats sharing the same content share the file.
- `chat_documents` HXS plaintext collection — `(id, chatId, sourceId, name, mimeType, chunkCount, sizeBytes, addedAt)`. Persisted across restarts. **Do not** call `documentRepo.clearAll()` from `RagManager.init` — that's the previous (wrong) behavior that wiped doc history every boot.
- `id` is the compound `<chatId>:<sourceId>`. Same content attached to two chats produces two records sharing one `sourceId.bin` blob.

`RagManager.hydrateChat(chatId)` re-ingests persisted records into the live RAG engine on chat-open (the engine itself is rebuilt fresh per process). It tracks `ingestedDocIds: MutableSet<String>` to avoid duplicate ingests; the set clears on `engine.close()`.

`RagManager.attachExisting(currentChatId, source)` is the prev-chat re-attach: builds the new compound docId, re-reads `<sourceId>.bin`, calls `engine.ingestBytes(...)`, persists the new record. Idempotent — if the chat already has the same `sourceId`, returns the existing record.

`RagManager.removeDocument(docId)` removes the chunks from the engine + record from HXS, and deletes `<sourceId>.bin` only when no other record references that sourceId (`documentRepo.countWithSource(sourceId) == 0`).

### Extension badge

`model/DocExtension.kt` enum maps mime + filename to a `(label, tint)` pair (PDF/DOCX/XLSX/PPTX/ODT/EPUB/RTF/MD/HTML/JSON/XML/CSV/TXT/OTHER). `ExtensionBadge` in `ui/components/action_window/Attachments.kt` renders a rounded card with the label centered, tinted from the entry's color. Used in the Attach tab and the prev-chats picker.

### PlusMenu cleanup

The PlusMenu's old "Documents" button is gone — attachments live entirely in the Attach tab now. PlusMenu shows only Thinking when `supportsThinking`; if not supported, `PlusMenuCard` returns null.

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
- Don't drop `-Wl,-z,max-page-size=16384` in any owned native CMake target. Android 15+ / Play Store requires 0x4000 LOAD alignment on arm64 + x86_64. Verify: `unzip -p libs/ai_sherpa-release.aar jni/arm64-v8a/libai_sherpa.so > /tmp/s.so && readelf -l /tmp/s.so | awk '/LOAD/{getline;print $NF}'` → `0x4000`.
- Don't `secureWipe` the userKey passed to `HexStorage.openEncrypted`/`createEncrypted`. `hxs.cpp` keeps a `NewGlobalRef`; zeroing turns every later AEAD op into a zero-key op (silent decrypt failure on next launch).
- Don't eagerly construct `AppKeyStore` or `AppPreferences` from any process other than main. `InferenceService` runs in `:inference`. `TNApplication.isMainProcess()` early-returns; integrity / pref Hilt fields must be `dagger.Lazy<T>`.
- Don't wrap individual screens in their own `Scaffold`. One Scaffold = `AppScaffold`. Per-route bars go in `AppTopBar.kt` / `AppBottomBar.kt` `when` blocks.
- Don't set `isMinifyEnabled = true` on any library module. Only `:app` minifies. Library minification collides on `Type a.a is defined multiple times` against pre-minified prebuilts.
- Don't remove the per-step `visual` composables in guide detail screens. Update them if the real UI changes.
- Don't key the TOFU `.so` manifest by absolute path. Filenames + `nativeLibraryDir` resolve.
- Don't verify the `.so` manifest across app updates without rebinding to install identity. Mismatched `{signerHash, longVersionCode, lastUpdateTime}` triggers re-TOFU, not hard-fail.
- Don't re-add a root hard-fail. One-time `RootWarningDialog`, gated on `rootWarningShown`.
- Don't re-add a plaintext HXS container for the bootstrap DEK. Raw XOR-masked `k.bin` only.
- Don't skip `migrateLegacyIfNeeded()` in `AppKeyStore.init`.
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
