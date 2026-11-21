# ToolNeuron — Repo Guide

Project memory for this repo. **When a change affects architecture, security behavior, public APIs, JNI contracts, persisted layouts, or scope, update this file in the same change.** A future session reads this to reconstruct intent.

---

## Scope

Privacy-first, offline-only on-device AI assistant. No Google Play services, no telemetry, no analytics.

In scope: on-device LLM chat, RAG over user documents, VLM, voice (TTS+STT), Remote Server with bundled web UI, HF Explorer, web search, image generation (`:ai_sd`), first-party plugin system, Dynamic Island overlay, Downloads screen. Out of scope: tool calling, Termux integration.

Package `com.dark.tool_neuron` · minSdk 29 · targetSdk 36 · abiFilters `arm64-v8a`, `x86_64`.

### Modules

- `:app` — Compose UI, viewmodels, Hilt graph, activities, services.
- `:hxs` — encrypted key-value store (Kotlin wrapper + C++ core).
- `:hxs_encryptor` — crypto + integrity: Argon2id, AES-GCM/ChaCha20-Poly1305, BoringSSL, ML-KEM-768, ML-DSA-65, Ed25519, HKDF, `SecureBuffer`, plus the native policy / auth / boot-integrity stack.
- `:native-server` — embedded OpenAI-compatible HTTP server (cpp-httplib + nlohmann/json header-only; no BoringSSL / OpenSSL / zlib).
- `:plugin-api` — pure-Kotlin contract for plugin authors (`Plugin`, `PluginContext`, `PluginCapability`, `PluginManifest`, `OnnxApi`, `HxsApi`, `NetworkApi`). Compose deps are `compileOnly`.
- `:plugin-exc` — plugin host: `PluginExecutor`, `PluginLoader` (DexClassLoader from `<filesDir>/plugins/<id>/classes.dex`), `PluginRegistry`, `CapabilityGate` (PolicyEngine session + manifest cap), `PluginContainerActivity`, `PluginDock`, `OnnxApiImpl` (onnxruntime-android 1.20.0, AI_ONNX), `HxsApiImpl` (collection `plugin_<id>`, HXS_READ/WRITE), `NetworkApiImpl` (INTERNET).
- `:download_manager` (`HxdManager`), `:networking` (`WebNative`, curl-impersonate) — ancillary.

Prebuilt AARs in `libs/`: `gguf_lib-release.aar` (chat/VLM/embedding), `ai_sherpa-release.aar` (TTS/STT), `ai_sd-release.aar` (Stable Diffusion; currently the **debug** AAR copied under the release name because R8 mangled `StableDiffusionManager.Companion.getInstance`; swap once `:ai_sd` consumer-rules keep `com.dark.ai_sd.StableDiffusionManager$Companion`).

---

## Build & release

- Verify: `./gradlew :app:compileDebugKotlin`. Install: `./gradlew :app:installDebug`. Never `assemble + adb install`.
- Release is built from Android Studio, signed via `local.properties` keys `TN_KEYSTORE_PATH / TN_KEYSTORE_PASSWORD / TN_KEY_ALIAS / TN_KEY_PASSWORD`; missing keys fall back to unsigned.
- Native: `./gradlew :hxs_encryptor:externalNativeBuildDebug`. clangd `'openssl/mem.h' not found` is a false positive; build-green is truth.
- Instrumented tests: `./gradlew :app:connectedDebugAndroidTest`. Tests that mutate native global state call `PolicyEngine.resetForTesting()` in `@Before` and `BootIntegrity.setRelaxedForTesting(true)` before any `hardFail` path, otherwise the process `_exit(1)`s.

---

## Hard rules

1. **HXS-only persistence.** No SharedPreferences, Room, DataStore, or raw files. Sole exception: `app_bootstrap/k.bin`.
2. **Security logic lives in C++/JNI.** Kotlin wraps native; auth/trust/policy decisions cross JNI as opaque token or bool. No Kotlin `if (verify)` gating.
3. **No comments** except a one-liner `//` for a non-obvious WHY. No banners, block comments, or docstrings on internal/private code.
4. **No inline fully-qualified class names.** Import at the top.
5. **ViewModels live in `com.dark.tool_neuron.viewmodel`**, never next to their screen.
6. **Commits:** conventional, one line, no `Co-Authored-By` trailer, never push or commit unless asked, never skip hooks.
7. **Research/exploration subagents run on Sonnet at low effort** unless the user overrides.
8. **No TODOs, stubs, or half-implementations.**
9. **One Scaffold**: root `AppScaffold`. Screens take `innerPadding: PaddingValues` and render plain `Column`/`LazyColumn`/`Box`. Per-route bars go in `AppTopBar.kt` / `AppBottomBar.kt` `when` blocks.
10. **Only `:app` minifies.** Library modules must not (R8 collides with pre-minified prebuilts: `Type a.a is defined multiple times`). Library keep rules go in each module's `consumer-rules.pro`.
11. **No spec/plan/research `*.md` in the repo.** Roadmaps live in conversation context.

---

## Security architecture

### Layers

- **UI:** PasswordScreen / SetupPasswordScreen wrapped in `SecureScreen` (FLAG_SECURE). `AppScaffold` watches `shouldLock` and re-routes to PasswordScreen.
- **App Kotlin (`data/`):** `SecurityManager` (only auth API), `SessionHolder` (opaque 32-byte token), `AppLockObserver`, `NativeIntegrity`, `AccessibilityGuard`, `RootGuard`, `PinStrength`, `AppPreferences`, `AppKeyStore`, `AuthState`, `VerifyResult`, `LockoutPolicy`, `KeyFingerprint`.
- **Native (`libhxs_encryptor.so`):** `PolicyEngine` (single `is_allowed(feature_id, token)` gate), `AuthNative`, `BootIntegrity`, `IntegrityGuard`, `CryptoEngine`, `HybridKem`, `HybridSign`.

### Keystore DEK

- `AppKeyStore` reads/writes `filesDir/app_bootstrap/k.bin`: `[magic "TNDK"(4)][version(1)][iv_len(2)][iv][ct_len(2)][ct]`, byte-XOR masked with a hardcoded 32-byte key. The XOR is obfuscation; protection is the Keystore-wrapped `ct`.
- Alias `toolneuron_vault_dek_v1`: AES-256-GCM, StrongBox with TEE fallback. NOT `setUserAuthenticationRequired(true)` (chicken-and-egg with setup).
- First launch generates a 32-byte DEK; every launch reads, unmasks, unwraps. `backing()` classifies STRONGBOX / TEE / SOFTWARE_FALLBACK / UNKNOWN.
- `migrateLegacyIfNeeded()` runs in `AppKeyStore.init`. It fires when `app_bootstrap/` has stale files OR `app_prefs/` has files but `k.bin` is missing (post-`hardWipe` state); it wipes bootstrap + prefs + alias and re-bootstraps. Don't tighten it to bootstrap-only.
- `wipe()` is scorched-earth: null the cached DEK reference (**never zero it in place** — HXS holds the same array via `NewGlobalRef`), delete everything under `filesDir` and `cacheDir`, delete the alias.

### Signer-bound user-keys (v2)

Every per-vault key is `HKDF(ikm = DEK, salt = installSignerHash, info = "tn.<scope>.user_key.v2")`. `installSignerHash()` = SHA-256 of the first signer cert (`GET_SIGNATURES` fallback on API < 28), cached, cleared on `wipe()`. This closes the same-device replaced-APK attack: the repacked build can unwrap the DEK (uid-scoped alias) but derives different user-keys, so AEAD fails and `openOrRebuild` hands it a fresh empty vault.

- Every `encryptor.deriveKey(ikm = dek, ...)` in app code MUST use `salt = keyStore.installSignerHash()`. Sites: `AppPreferences.init`, `AppPreferences.deriveAuthKey`, `DocumentRepository.init`, `RagKeywordIndex.init`, `ChatRepository.init`, `SourceFileVault.keyFor`, `DownloadHistoryRepository`.
- If the signer lookup returns null/empty, throw `SecurityException` and refuse to bootstrap. No zero/empty fallback.
- Bumping an info string requires bumping the suffix (`v2` → `v3`); the change is destructive (see below), so make it deliberately.
- The DEK is only ever unwrapped in `:app`. `:server` and `:inference` never open HXS or derive keys.

### Vault inventory

| Vault | Sealed under | Notes |
|---|---|---|
| `app_bootstrap/k.bin` | Keystore alias | See above. |
| `app_prefs/` | `tn.app_prefs.user_key.v2` | All prefs. AuthState has a second AEAD layer: `tn.app_prefs.auth_key.v2`, AAD `"tn.auth_state.v1"`. |
| `chat_store_v2/` | `tn.chats.user_key.v2` | Chats + messages. Legacy plaintext `chat_store/` deleted on first v2 boot. |
| `chat_documents_meta_v1/` | `tn.chat_documents.user_key.v2` | RAG doc metadata. Dir name is historical. |
| `chat_documents/sources_v2/` | per-file AEAD (`SourceFileVault`) | `<sourceId>.bin` = `[iv(12)][ct][tag(16)]`; key `HKDF(DEK, signerHash, "tn.chat_doc_source.user_key.v2@<sourceId>")`; AAD = sourceId bytes. Legacy plaintext `sources/` deleted on first v2 boot. |
| `rag_keyword_v1/` | `tn.rag_keyword.user_key.v2` | Native BM25 chunk records. |
| `download_history_v1/` | `tn.download_history.user_key.v2` | Capped at 50 newest. |

**v1 → v2 migration is destructive**: `openOrRebuild` tries `openEncrypted` with the v2 key; on failure it wipes the dir and recreates it. Existing users lose PIN, chats, and RAG attachments once. The Keystore alias (and DEK) is preserved.

Rules: don't downgrade `ChatRepository` / `DocumentRepository` to `openPlaintext`. Don't bypass `SourceFileVault` for source bytes. Don't re-create the legacy plaintext dirs. Don't `secureWipe` the userKey passed to `openEncrypted`/`createEncrypted` (`hxs.cpp` keeps a global ref; zeroing makes every AEAD op a zero-key op).

### AuthState v4

```
version(1)=4, security_mode(1) [0=NONE,1=APP_PASSWORD],
salt_len(2)+salt, hash_len(2)+hash, failed_attempts(2), next_attempt_at_ms(8),
has_panic(1) [panic_salt_len(2)+panic_salt, panic_hash_len(2)+panic_hash],
last_seen_now_ms(8)
```

Decoder accepts v1–v4 and zero-fills missing fields. Bump `AuthState.VERSION` and the decoder when extending.

### Native auth + PolicyEngine

- `auth::setup(pin) → {salt[16], hash[32]}` — Argon2id `t=4 / m=131072 KiB / p=1 / out=32`. **Never go below this.** Constants in `auth.h`.
- `auth::verify(pin, salt, hash) → 32-byte session token | null` — constant-time `CRYPTO_memcmp`, then `policy::register_session`.
- Gate order in `is_allowed`: (1) tampered → false (latched); (2) `fid ≥ 1000` (PRO_*) → false (license flip-point); (3) unauth features (APP_LAUNCH, OPEN_VAULT, AUTH_SETUP, AUTH_VERIFY, UI_PASSWORD_SCREEN, UI_SETUP_SCREEN, UI_INTRO) → true; (4) passthrough (`security_mode == NONE`) → true; (5) `session_active && CRYPTO_memcmp(token)`.
- Mutators: `register_session`, `invalidate_session`, `set_passthrough`, `mark_tampered`, `reset_for_testing`. Feature IDs in `policy_engine.h` mirror `PolicyEngine.Feature` in Kotlin; keep in sync.
- Don't route a gated feature around `PolicyEngine.isAllowed`. Don't expand the unauth set without a threat-model review. Don't revert `verifyPassword` to `Boolean`; the contract is `VerifyResult`.
- Argon2id takes ~1.5 s: never call `setPassword` / `verifyPassword` / `setPanicPin` on the main thread (`viewModelScope.launch { withContext(Dispatchers.Default) { … } }`).

### Lockout, wipe, panic PIN

- `LockoutPolicy.backoffMillis(failed)`: first 3 free, then 1m → 5m → 15m → 1h → 4h → 12h → 24h. `WIPE_THRESHOLD = 10` → `hardWipe()`.
- Clock rollback: `lastSeenNowMs` updated on every verify; if `nowMs + 5 min < lastSeenNowMs` the attempt is double-penalized and backoff extends from `max(nowMs, lastSeenNowMs)`.
- `hardWipe()`: `session.clear()` → `invalidateSession()` → `markTampered()` → `prefs.clearAuthState()` → `keyStore.wipe()`. The process is then unrecoverable; `WipedScreen` Restart does `finishAffinity + Process.killProcess`.
- `verifyPassword` order: (1) panic PIN first, match → `hardWipe` + `Wiped`; (2) lockout gate → `LockedOut`; (3) real PIN; (4) bump counter. Panic must work while locked out.
- Panic PIN set/clear and `disableLock` gate on `securityMode == APP_PASSWORD` only. Do NOT gate on a live session or `AUTH_DISABLE`: `ON_STOP` clears the session while dialogs stay up, causing nondeterministic failures. `SettingsViewModel._panicPinSet` mirrors `security.hasPanicPin` and resets on `setPassword`, `disableLock`, `Wiped`.
- `PasswordViewModel._lockedUntilMs` is primed from `snapshotLockoutState().nextAttemptAtMs` at construction so a restart during lockout shows the countdown immediately. `PasswordScreen` keeps `lockedUntilMs` and `wiped` branches.
- PIN: exactly 6 digits; weak PINs (all-same, monotonic ±1, top-20) rejected via `PinStrength.evaluate`.

### Boot integrity

`JNI_OnLoad`: `install_ptrace_self_trace()` then `capture_hook_baselines()` (first 32 bytes of `auth::verify`, `policy::is_allowed`, `boot::hard_fail`).

`TNApplication.onCreate()` (main process only; `isMainProcess()` early-returns):
1. `scanProcessEnvironment()` — hard-fail only on `FAIL_DEBUGGER | FAIL_FRIDA`. `FAIL_XPOSED` goes to `TNApplication.softEnvReasons`.
2. `bootVerify()` — TOFU `.so` hash walk. Manifest v2: `version(1) + signerHashLen(2)+signerHash + versionCode(8) + lastUpdateTime(8) + count(4) + (nameLen(2)+filename + hashLen(2)+hash)*`. A changed `{signerHash, longVersionCode, lastUpdateTime}` re-TOFUs; within one identity a mismatch is `FAIL_LIB_HASH` → hard fail. Key by filename, not absolute path. Also writes `apk_signer_hash_v1` for future licensing.
3. `verifyHookBaselines()`.
4. `accessibilityGuard.scan()` — never hard-fails; results feed the warning dialog. Self-excludes `context.packageName`.
5. `appLockObserver.register()`.

No root hard-fail. `RootGuard.scan()` runs from `ScaffoldViewModel`; if rooted and `rootWarningShown == false`, `RootWarningDialog` shows once with up to three paragraphs (`RootWarning(rootEvidence, tamperEvidence, a11yPackages)`). "I understand" flips `rootWarningShown` and silences all three sources. Rooted users commonly have LSPosed and a11y helpers; hard-failing on those is worse than warning once.

`BootIntegrity.hardFail(reason)` → `markTampered()` + `_exit(1)` unless `setRelaxedForTesting(true)`.

Detection strings in `integrity.cpp` use `HXS_OBF(var, "literal")`. Verify: `strings libhxs_encryptor.so | grep -iE '^(frida|gadget|linjector|xposed|TracerPid)$'` returns nothing. No plaintext detection strings in native code.

### Session + UI lock

`SessionHolder.active` flips on `AuthNative.verify` success and off on `clear()` (which also calls `AuthNative.invalidate()`). `AppLockObserver` clears on `ProcessLifecycleOwner` `ON_STOP` when `isLockEnabled`. `shouldLock = isLockEnabled && !session.active`; `AppScaffold` re-routes to PasswordScreen (`popUpTo(0){inclusive=true}`) except on PasswordScreen / SetupScreen / IntroScreen. `SecureScreen` (FLAG_SECURE) is applied to PIN entry only. `SecureClipboard` sets `EXTRA_IS_SENSITIVE` and clears after 30 s.

### Release hardening

`app/build.gradle.kts`: minify + shrinkResources on, `isDebuggable=false`, `isJniDebuggable=false`. ProGuard strips `Log.d/v/i`, `-repackageclasses ''`, `-allowaccessmodification`. Manifest: `allowBackup=false`; `TempActivity` + `ColorShowcaseActivity` un-exported; `InferenceService` in `:inference`.

Required keeps: `com.dark.gguf_lib.**`, `com.dark.ai_sherpa.**`, `com.dark.ai_sd.**` (+ `dontwarn`); `com.dark.networking.**` and `networking/consumer-rules.pro` keeps for `WebNative`, `WebResponse`, `WebBytesResponse`, `WebSearchResult` (JNI symbols are FQCN-bound; missing keeps = `UnsatisfiedLinkError` on release; verify `WebNative -> com.dark.networking.WebNative` in `mapping.txt`); `:native-server` consumer-rules keep `NativeServer`, `NativeServer$*`, `InferenceBridge`, `BindMode`.

### Pro license hook

Every gated feature goes through `PolicyEngine.isAllowed`; `PRO_*` (≥1000) returns false. To enable: flip the `is_pro_feature` branch to verify a signed blob `{device_id_hash, features_bitmap, expiry_unix, nonce, signature}` (Ed25519 or ML-DSA-65, public key XOR-baked at build). Anchor is the captured `apk_signer_hash_v1`. Device id must be an attested Keystore key fingerprint, never `ANDROID_ID`. Blob lives in its own HXS collection under the same DEK; tamper detection invalidates it.

### Deferred

Encrypt WAL (`hxs/src/main/cpp/wal.cpp`, plaintext even in encrypted mode); native cert pinning; Play Integrity (conflicts with privacy).

---

## File map (security)

- Native `hxs_encryptor/src/main/cpp/`: `policy_engine`, `auth`, `boot_integrity`, `integrity`, `xor_str.h`, `crypto_engine`, `memory_guard`, `pq_kem`, `pq_sign`, `hxs_encryptor.cpp` (JNI + `JNI_OnLoad`), `CMakeLists.txt` (BoringSSL + liboqs via FetchContent; `-march=armv8-a+crypto+sha2` on arm64; don't re-add `OPENSSL_NO_ASM=1`).
- Kotlin `hxs_encryptor/src/main/java/com/dark/hxs_encryptor/`: `HxsEncryptor`, `PolicyEngine`, `AuthNative`, `BootIntegrity`.
- Every owned native CMake target keeps `-Wl,-z,max-page-size=16384`. Verify: `readelf -l <so> | awk '/LOAD/{getline;print $NF}'` → `0x4000`.

---

## Chat + inference

- `InferenceService` runs in `:inference`. `InferenceClient.performBind` uses `BIND_AUTO_CREATE` only (never `BIND_IMPORTANT`: it makes the kernel evict `:app` before the process holding the model mmap).
- `generate` / `generateMultiTurn` / `generateVlm` wait for the service with `withTimeoutOrNull(BIND_TIMEOUT_MS)` and emit `InferenceEvent.Error("Inference service unavailable")` on null.
- All four `ServiceConnection` death paths (`onServiceDisconnected`, `onBindingDied`, `onNullBinding`, `unbind`) call `failPendingLoads`.
- Notification Stop: `PendingIntent.getBroadcast(InferenceStopReceiver, NOTIFICATION_STOP)` → `InferenceClient.requestUserStop(context)` sets `userStopRequested`, `unbind()`s first, then `startService(ACTION_STOP)`. Sending Stop straight to the service gets respawned by the live `BIND_AUTO_CREATE` binding.
- `InferenceService.loadModel`: calls `logDeviceProfile()` once per process (model, SoC, ABIs, MemTotal, cpuinfo `Features:`), and pre-checks RAM: if `memAvail in 1 until modelSize*6/5` return a clear "Not enough free memory" error; skip when `memAvail <= 0`.
- `HomeViewModel.sendMessage` auto-load contract (all three required):
  (a) `ModelStoreViewModel.finalizeNonVlmDownload` marks a new GGUF `isActive=true` when none is active.
  (b) `sendMessage` falls back to `chatModels.value.firstOrNull()?.also { modelRepo.setActive(it.id) }`, persists the user message first, then `try { modelSession.load(active) } catch (Exception)` and checks `loadState is Active` before `runGeneration`. Open `_loadModelWindow` only when zero chat models are installed.
  (c) `HomeScreenBottomBar.canSend = text.isNotBlank() && !isGenerating && (isModelLoaded || installedModels.isNotEmpty())`.
- Editing: user and assistant messages are both editable (`HomeViewModel.editMessage`). User edit: save, delete later messages, regenerate. Assistant edit: save in place only. Messages with `archivedByCompactId != null` can't be edited. Dialog labels: "Save & regenerate" vs "Save".
- Forking: `ChatRepository.forkChat(sourceChatId, atMessageId)` clones messages through the cut into a new chat titled `"<src> (fork)"`, persisted via `TAG_FORKED_FROM = 9`. Gated on `!isGenerating`. Archived messages and `CompactSummary` cards stay forkable (don't add `&& !archived` to `canFork`); compact summary cards get fork only (no delete/edit/regen).
- Chat auto-scroll: no per-token `scrollToItem`. Track `stickToBottom` from `snapshotFlow { isScrollInProgress }`, gate auto-scroll on `stickToBottom && !isScrollInProgress`, use `scrollToItem(last, scrollOffset = Int.MAX_VALUE)`.
- `InferenceService.sdBroadcastLock`: `RemoteCallbackList.beginBroadcast()` is not nestable; the five parallel SD collectors serialize in `synchronized(sdBroadcastLock)` around the whole `fanoutSd` body.
- New chat-message fields use TAG ≥ 16.

---

## VLM

Vision rides on an active GGUF via a separate mmproj. Image data crosses AIDL as `ParcelFileDescriptor[]` only (1 MB binder limit; never `byte[]`). PFD reads happen on `Dispatchers.IO`, not main.

- **Layout:** `<modelsDir>/vlm/<repoLeaf>/{base.gguf, mmproj.gguf}`. A HF repo is VLM iff any `.gguf` has `mmproj` (case-insensitive) in its name. Mmproj is a sibling file, never its own `ModelInfo`. No manual "load projector" UI.
- **AIDL:** `loadVlmProjector(path, threads, imageMinTokens, imageMaxTokens)`, `loadVlmProjectorFromFd`, `releaseVlmProjector`, `isVlmLoaded`, `getVlmInfo`, `getVlmDefaultMarker`, `generateVlm(messagesJson, imageFds, maxTokens, cb)`.
- **Routing:** `InferenceCoordinator.run()` iteration 0 + last user has `imageUris` + `isVlmLoaded` → VLM. `buildMessagesJson(messages, vlmLastUserId)` must prepend `getVlmDefaultMarker()` to the last user message.
- **Auto-load:** `ModelSessionManager.load` calls `releaseVlmProjector()` first, loads base, then loads the colocated mmproj (`VlmPaths.colocatedMmproj`) when the path is under `<modelsDir>/vlm/`; failure sets `vlmAutoLoadError` (shown by `VlmErrorBanner`). `unload()` releases the projector first.
- **Persistence:** `ChatMessage.imageUris` via `TAG_MSG_IMAGES = 8` (URI strings only; bytes never hit disk).
- **Catalog:** `ModelCatalog.fetchRepo` flags mmproj repos; base rows carry `isVlm`, `repoPath`, `mmprojFileName/Uri/SizeBytes`. `ModelStoreViewModel.downloadModel` routes into `vlmModelFile(...)`, enqueues mmproj under the same `modelId`, and finalizes a single `ModelInfo` whose path is the base.
- UI: PlusMenu/input-bar image attach disabled unless `isVlmLoaded` ("VLM required" badge); `InstalledModelCard` tags paths under `models/vlm/`.

---

## Voice (TTS + STT)

sherpa-onnx AAR: VITS + Kokoro TTS, Whisper STT only. **Store-only install** (no BYOM / SAF import). The Store downloads `.tar.bz2` into `<filesDir>/voice/<tts|stt>/<folder>/` (path-traversal-safe extraction in `VoiceArchive`, archive deleted after). First download of each kind becomes active via `finalizeVoiceDownload`.

- **AIDL:** `loadTtsModel(configJson)`, `unloadTtsModel`, `isTtsLoaded`, `synthesize(text, speakerId, speed) → float[]`, `getTtsSampleRate`, `loadSttModel`, `unloadSttModel`, `isSttLoaded`, `recognize(float[], sampleRate)`, `recognizeFromFd`. All batch; no streaming-synthesize method.
- **Streaming TTS** = text-level chunking in `TtsPlayer` (`sanitize` strips markdown; `splitIntoSentences` breaks at `.!?…;\n` after ≥20 chars, or comma/space at ≥180). Each chunk is synthesized on IO and written to a lazy `AudioTrack` (`MODE_STREAM + WRITE_BLOCKING`, recreated on rate change). Keep the mid-chunk cancellation check (`_speakingId.value == messageId`).
- **STT:** `SttRecorder` records 16 kHz mono `ENCODING_PCM_FLOAT` from `VOICE_RECOGNITION` only. `HomeViewModel.stopRecordingAndTranscribe` calls `InferenceClient.recognize`, publishes `_transcribedText`, and **always** `voiceManager.unloadStt()` in `finally`.
- **`VoiceModelManager`:** `@Singleton`, Mutex-serialized loads, uses `dagger.Lazy<AppPreferences>` (never eager in non-main processes). Active ids in `active_tts_model` / `active_stt_model`; empty → first installed. Settings → Voice swaps the pick and calls `unloadTts/Stt()`.
- `dataDir` / `espeak-ng-data` must be filesystem paths, not `content://`. Voice deletes need `deleteRecursively()`.
- Permissions: `RECORD_AUDIO` at first mic tap; no `FOREGROUND_SERVICE_MICROPHONE`.
- UI: Speak/Stop on assistant bubbles when `voiceTtsAvailable` (spinner while loading). The mic button is always rendered; no STT installed → routes to ModelStore. Recording crossfades the input bar into `RecordingEqualizer`. Voice errors use `VlmErrorBanner`.
- `ProviderType` (`GGUF/TTS/STT/EMBEDDING/IMAGE_GEN/IMAGE_UPSCALER`) is canonical; no `modelType` field on `ModelInfo`. `HuggingFaceModel.modelType` is a pre-install hint mapped at insert time. `HomeViewModel.chatModels` filters to GGUF.
- Catalog (`ModelCatalog.BUILT_IN_MODELS`): `vits-piper-en_US-amy-low`, `vits-piper-en_US-libritts-high`, `sherpa-onnx-whisper-tiny-en`, `sherpa-onnx-whisper-tiny`, from sherpa-onnx GitHub releases.

---

## Remote Server

Embedded OpenAI-compatible server for every installed engine: chat GGUF, VLM, embeddings, TTS, STT, image gen / edit / upscale. No TLS, mDNS, or outbound calls.

### Processes

- `:app` — UI, `ServerController` (AIDL client), `InferenceClient`. Only process with HXS/Keystore.
- `:inference` — chat-side llama.cpp + sherpa; untouched by the server.
- `:server` — `RemoteServerService`, per-kind engines, native HTTP server, token in native memory. Foreground (`dataSync`, `stopWithTask="false"`). Never opens HXS; everything arrives via AIDL `start(configJson)`; `rotateToken(newToken)` pushes rotations.

Survival: `handleStart` self-calls `startService(Intent(this, RemoteServerService::class.java))` right before `startForeground` — **don't remove it**; without it, the bind-only service dies when `:app` is swiped away. Reopening the app re-binds and rehydrates via `currentSnapshotJson()` + `recentRequestEventsJson(100)`. Foreground promotion happens inside AIDL `start`, never on bind alone. The notification body opens `MainActivity` with `EXTRA_OPEN_SERVER_SCREEN=true`; its Stop button sends `ACTION_STOP` to `:server`.

### Engines (lazy per kind)

`ServerEngineRegistry` holds one instance per kind, loaded on first request (primary chat/VLM preloaded at start), with per-kind locks (`Mutex` for suspend loaders, `synchronized` for sherpa). Engines are not reaped on idle; only `shutdownAll()` at stop.

| Kind | Wrapper | Backing |
|---|---|---|
| `gguf` | `ServerEngine` | `GGMLEngine` |
| `vlm` | `ServerVlmEngine` | `GGMLEngine` + mmproj |
| `embedding` | `ServerEmbeddingEngine` | `EmbeddingEngine` |
| `tts` | `ServerTtsEngine` | sherpa `OfflineTts` (VITS) |
| `stt` | `ServerSttEngine` | sherpa `OfflineRecognizer` (Whisper) |
| `image_gen` / `image_upscaler` | `ServerImageEngine` | `StableDiffusionManager` |

Rules:
- New routes must go through the registry. New endpoint = new `server_models::Kind`, wrapper, registry method, `InferenceBridge.start<X>` upcall, and `nativeFeedReply*` path. Never touch `GGMLEngine` / `StableDiffusionManager` from a route handler.
- `:server` owns its own sherpa/SD instances; no AIDL into `:inference`, no shared SD state with `:app`. The only coupling is the on-disk `<filesDir>/ai_sd_runtime/` (first extractor wins). `ServerImageEngine.ensureRuntime()` gates on `qnnlibs.tar.xz` existing; image routes return a clean 500 otherwise.
- Voice default inside `:server` is `firstOf(kind)` (install order); clients choose via `model`. Never read "active" prefs in `:server`.
- `chat/completions` routes to VLM only if `oai::extract_images_from_messages` sets `has_images` (an `image_url` part exists). Keep that the single decision point; pre-routing stays pure.
- Streaming (SSE) is chat/VLM only. Embeddings, TTS, STT, image are single-response.

### Routes

Public: `/`, `/index.html`, `/webui`, `/docs`, `/docs/`, `/health` (these are the only paths in `auth::is_public_path`).
Authed: `GET /v1/models` (full enabled-engine catalog: `id`, `type`, `owned_by`; don't shrink to "currently loaded"), `POST /v1/chat/completions`, `/v1/embeddings`, `/v1/audio/speech` (returns `audio/wav`), `/v1/audio/transcriptions` (multipart WAV only; no transcoding), `/v1/images/generations` (`{data:[{b64_json}]}`), `/v1/images/edits` (img2img/inpaint, multipart), `/v1/images/upscale` (multipart).

All non-public routes pass the same `pre_routing_handler`: token bucket (cap 30, refill 1/s) → ban list → bearer compare (constant-time; 20 consecutive fails → 1 h ban). `post_routing_handler` records every request into the 128-entry audit ring and pushes it to `:app` via `InferenceBridge.onRequestEvent`. No per-route auth bypass.

### Native (`native-server/src/main/cpp/`)

`server_core` (httplib lifecycle, routing), `server_auth`, `server_crypto` (getrandom RNG, const-time eq, base64), `server_models` (typed catalog), `server_audit`, `server_rate_limit`, `server_webui`, `server_docs`, `server_staging`, `wav_codec`, `gen_session` (streaming token queue), `reply_session` (single-shot reply), `openai_schema`, `jvm_bridge`, `native_server.cpp` (JNI + `JNI_OnLoad`). CMake pins cpp-httplib v0.18.5 + nlohmann/json v3.11.3, header-only, with the same hardening flags as `:hxs_encryptor`. Never link BoringSSL/OpenSSL/zlib here.

Payload rules:
- Big binaries (multipart image/mask/wav in, TTS wav / PNG out) are staged to `<cacheDir>/server-staging/tn_<rand>_<name>` via `server_staging`; only the **path** crosses JNI, never `byte[]`. Unlinked after the response and purged on stop.
- Chat/VLM stream via `nativeFeedToken/Done/Error`; everything else via `nativeFeedReplyText/Binary/Error`.
- VLM `image_url` accepts only `data:image/...;base64,...`. Network URLs → 400 `invalid_image` (offline scope).
- `set_payload_max_length` = 64 MB; read/write timeouts 60 s / 120 s. Don't lower them.
- `reply_session.wait` timeouts: embeddings 120 s, TTS 180 s, STT 180 s, image gen 600 s, upscale 300 s. Don't make them unbounded.
- Default bind is `ALL_INTERFACES` (0.0.0.0); show both loopback and LAN URLs. Port validated to [1024..65535], default 11434; effective port written back from `nativeBoundPort()`.

### Start config

`ServerController.start()` builds `{token:"tn_sk_<b64url>", port, bindMode:"ALL_INTERFACES|LOOPBACK_ONLY|WIFI_ONLY", webUiHtml, docsHtml, engines:[{id,name,path,type,mmproj_path?,config_json,created,primary}]}` by walking `ModelRepository` (GGUF under `<modelsDir>/vlm/` with a colocated mmproj → `vlm`; `config_json` merges `loadingParamsJson` + `inferenceParamsJson`). `CONTENT_URI` models are skipped (no SAF trampoline across processes). The selected-chat-model pref only sets the `primary` flag; Start is enabled whenever any engine is installed.

### Server-side Kotlin (`service/server/`, `:server` process)

`RemoteServerService` (plain `Service`, not `@AndroidEntryPoint`; `onStartCommand` handles only `ACTION_STOP`), `ServerEngine`, `ServerVlmEngine`, `ServerEmbeddingEngine`, `ServerTtsEngine`, `ServerSttEngine`, `ServerImageEngine`, `ServerEngineRegistry`, `ServerInferenceBridge`, `ServerCatalog`, `ServerWavCodec`, `BindResolver`, `ServerTypes`. `ServerSnapshot` reports the primary engine only.

### Auth, lockdown, keys

- Token: `nativeGenerateToken()` (`tn_sk_` + 32 random bytes b64url), stored only in encrypted `AppPreferences.serverToken`, handed to native at start, zeroed at stop. Reveal gates on `AUTH_VERIFY`; rotate invalidates the old one.
- HXS keys: `server_token`, `server_port`, `server_bind_mode`, `server_auto_start` (reserved), `server_configured`, `server_selected_model`.
- Lockdown: `ScaffoldViewModel.serverRunning` → `AppScaffold` `LaunchedEffect(serverRunning, currentRoute)` routes to `ServerScreen` with `popUpTo(0){inclusive=true}`; `BackHandler(enabled = running) {}` in ServerScreen; drawer shown only when `currentRoute == HomeScreen.route && !serverRunning`. Chat-side load/unload/send and `ModelStoreViewModel.downloadPack/downloadModel/setActive` gate on `!serverController.isBusy`.
- Web UI: `app/src/main/assets/server_webui.html` (Chat / Embeddings / Voice / Image tabs, one SPA) and `server_docs.html`, pushed via `nativeSetWebUiHtml` / `nativeSetDocsHtml`, cleared on stop. The web UI's STT panel accepts `.wav` only.
- Deliberately omitted: TLS, mDNS, QR pairing, dynamic model load over the wire, streaming usage metrics, persisted request log, network image fetch, audio transcoding, per-engine RAM accounting / idle TTL, image-gen progress streaming.

---

## HF Explorer

All HF traffic goes through `:networking` (`WebNative.fetch`, curl-impersonate Chrome116, bundled CA, strict verify). Never `HttpURLConnection` for HF or any other target.

- `repo/HuggingFaceApi.kt` (Hilt singleton): URL builders + `fetchJson/fetchJsonArray/fetchRaw/probe`; failures typed as `HfApiError` (`RateLimited`, `NotFound`, `Forbidden`, `Network`, `Parse`, `Http`). `ModelCatalog` and `RepositoryValidator` inject it.
- `repo/hf/`: `HfClient` (typed endpoints; tags catalog cached 24 h in encrypted `app_prefs` under `hf_tags_catalog_v1` + `hf_tags_catalog_v1_at`, never anywhere else), `HfModels`, `HfJsonParse`. `HuggingFaceExplorer` is a thin compat wrapper for `ModelStoreViewModel`.
- `WebNative.fetch` returns `Result<WebResponse(status, body, error)>`. `Result.failure` is for transport errors only; non-2xx is a successful `WebResponse` so callers can react to 429/404/403.
- `net_jni.cpp` logs host + status only (`host_of(url)`), never full URLs (search queries are PII). Never put device-identifying values in the `headers` map.
- **Search trigger policy:** only the Search button, IME Search, chip toggles, sort changes, and slider release (`onValueChangeFinished`). No per-keystroke quicksearch (500 calls / 5 min unauthenticated limit). `HfClient.quickSearch` exists but must not be wired to typing.
- **URL params for `/api/models` are limited to** `search`, `author`, `filter` (plain tag values, stackable), `gated`, `num_parameters`, `sort`, `limit`, `skip`. Speculative params (`apps`, `inference_provider`, `inference=warm`, prefixed `region:`/`dataset:` filters, `pipeline_tag`, `expand=*`) return HTTP 400. `sort` is camelCase (`trendingScore`, `lastModified`, `createdAt`, `downloads`, `likes`; source: `HfSort.apiKey`). Verify any new param with curl first.
- `HfFilters` is intentionally minimal: `libraries` (default `{"gguf"}`), `author`, `gated`, param range, `sort`. Don't reintroduce post-filter sliders (`minDownloads`, `minLikes`, `recentDays`); the user removed them.
- VM `HfExplorerViewModel`; screens `HfExplorerScreen` (search hero, history, trending, filters card, result cards) and `HfRepoDetailScreen` (header, gated notice, GGUF card, card data, file list, README via `lazyMarkdownItems`).

---

## RAG

The Action Window's third tab is **Attach**. `AttachmentPickerDialog` offers previous-chat docs (`PrevChatsPickerDialog`) or storage (`OpenDocument`). PlusMenu only shows Thinking (when supported).

### Persistence

- Source bytes: content-addressed by SHA-256, written through `SourceFileVault` to `chat_documents/sources_v2/<sourceId>.bin` before any ingest.
- Metadata: encrypted `chat_documents_meta_v1/`, TAG layout `1=id, 2=chatId, 3=name, 4=mimeType, 5=chunkCount, 6=sizeBytes, 7=addedAt, 8=sourceId, 9=isDeepIndexed`. `id = "$chatId:$sourceId"` (never a random UUID) so re-attach is idempotent and `removeDocument` can ref-count the blob (`countWithSource(sourceId) == 0` before deleting it).
- Never call `documentRepo.clearAll()` in `RagManager.init`; records persist across restarts and `hydrateChat(chatId)` re-ingests lazily (tracks `ingestedDocIds`; also repopulates BM25 for text docs, idempotent via `docCount(docId) > 0`). `attachExisting` is idempotent for same-sourceId.
- BM25: native `hxs::RagKeywordIndex` (`hxs/src/main/cpp/rag_keyword.{h,cpp}`), HXS-encrypted collection `rag_chunks` (TAG `1=docId, 2=chatId, 3=sourceId, 4=chunkIndex, 5=text`), in-RAM inverted index rebuilt at startup, ASCII-alnum tokenizer (UTF-8 ≥0x80 passthrough, lowercase, len 2–64), k1=1.2, b=0.75. JNI: `nativeRagIngest/Query/RemoveDocument/Clear/DocCount`. Don't move it back to Kotlin/SQLite (some OEM SQLite lacks FTS5, and plaintext chunks on disk was the old leak).
- Only text-like docs get BM25 (`RagManager.isTextLike(mime, name)`: `text/*`, json/xml/rtf/yaml/javascript, common text/code extensions). PDF/DOCX/EPUB etc. are dense-only until native exposes extracted text (#329). Kotlin-side `RagChunker` (target 1024 chars) is independent of native chunking.

### Retrieval (`RagManager.augment`) — keep this order

1. Optional multi-query (`appPrefs.ragMultiQuery`, `RagQueryRewriter`, 8 s timeout, falls back to single query).
2. Per query: dense `engine.query` (20) + BM25 (20).
3. `rrfFuseMany` (k=60, identity `(docId, chunkIndex)` — dense and BM25 chunk indices differ on purpose) → pool of 12.
4. Optional LLM rerank (`appPrefs.ragSmartRerank`, `RagReranker`, 15 s, 256 tokens; falls back to RRF order).
5. Token budget: `InferenceCoordinator.computeRagBudget` = `contextSize - maxTokens - historyTokens - 256`, clamped 256–4096; chunks counted at chars/4; first chunk truncated if alone over budget; max `FINAL_TOP_N = 8`.
6. Citations: prompt asks for inline `[N]`; `RagCitationMatcher.match` parses markers + 4-gram overlap (≥3 hits). Stored on the assistant message via `TAG_MSG_CITATIONS = 13` as JSON `{sourceId, docId, chunkIndex, score, name, mimeType, snippet, cited}`. `CitationStrip` renders chips; tap opens a dialog.

Multi-query and rerank are opt-in (off by default; each costs an LLM call). Keep their `withTimeoutOrNull`s. Any new Settings → Chat & RAG toggle must write `prefs.<key>` and update its `_<flow>.value` (the `combine(...)` flow has 13 inputs).

### Deep Index

Per-doc "Deep Index" (text docs only, idempotent): `RagDocSummarizer` writes a ≤320-char summary (30 s, 200 tokens); `RagChunker` splits; each chunk is re-ingested as `[Document context: <name> — <summary>]` + chunk under docId `${origDocId}::ctx<idx>`. Sets `ChatDocument.isDeepIndexed` (TAG 9), exposes `deepIndexing: StateFlow<Set<String>>`. `augment` strips `::ctx<n>` so citations group under the parent; `removeDocument` removes every `::ctx*` child first. ~5× native chunk inflation.

### Debug + UI

`Settings → Chat & RAG → Retrieval debug` → `RagDebugScreen` (`NavScreens.RagDebug`, `RagDebugViewModel`, backed by `RagManager.debugQuery`; single-query, tabs Fused/Dense/BM25/Context/Engine). `model/DocExtension.kt` maps mime/filename to `ExtensionBadge` label + tint (`ui/components/action_window/Attachments.kt`).

---

## Web search

Single-shot, LLM-driven (`viewmodel/WebSearchCoordinator.kt`). Triggered by the bottom-bar Web Search toggle or `/search <query>`.

Flow: Plan → GenerateQueries (one LLM call, exactly 3 queries, parsed by `WebSearchPrompts.QUERY_LINE_REGEX = ^\s*(?:\d+[.)\-:]|[-*•])\s+(.+)$`) → Search (`WebSearcher.search(query, 5, idx)` via `WebNative.search`/DDG, deduped by a session-wide `seenUrls`, ≤15 snippets) → Synthesize (one LLM call, markdown with inline `[i]` + Sources) → Done. No URL fetching, no iteration. Don't expand it back into a multi-iteration pipeline (the old Research pipeline was deleted 2026-05-15 as slow and barely better). Don't change the regex (it tolerates small-model deviations). `WebNative.search` is the only allowed pipe.

- **State rides on the message:** `webSearchRunId` (`TAG_MSG_WEBSEARCH_RUN = 14`), `webSearchState` (`TAG_MSG_WEBSEARCH_STATE = 15`, JSON `WebSearchUiState`). `msg.content` holds the user query for the card header. `ChatMessageList` renders `WebSearchCard` when `webSearchRunId != null`. Don't add a runtime-only event map; `HomeViewModel.handleWebSearchEvent` is the single write point (`webSearchMessages[runId]` → apply → write back; evict on terminal).
- **LLM history:** `InferenceCoordinator.buildMessagesJson` swaps `content` for `WebSearchUiState.fromJson(webSearchState).answer.trim()` and skips the message when the answer is blank.
- **Lockdown:** while `webSearchActive`, `sendMessage` / `loadModel` / `unloadModel` early-return (the chat LLM is borrowed).
- `WebSearchCoordinator` must use `tryEmit` (not suspending `emit`) in the `CancellationException` / `Throwable` catch blocks, or the Cancelled event is lost and the card freezes. `_events` has a 64 buffer.
- Files: `model/WebSearchEvent.kt`, `model/WebSearchUiState.kt`, `repo/web_search/{WebSearcher,WebSearchPrompts}.kt`, `ui/screens/web_search/WebSearchCard.kt`.

---

## Image generation (`:ai_sd`)

Tasks: Generate, Edit (img2img), Inpaint, Upscale 4×. LaMa / MobileSAM / depth / AdaIN exist in the AAR's C++ but are not JNI-bound (out of scope until they are).

- **SoC buckets** (`data/SocBucket.kt`, from `Build.SOC_MODEL`, API ≥ 31): `SM8475, SM8450` → `8gen1`; `SM8550(P), QCS8550, QCM8550, SM8650(P), SM8750(P), SM8850(P), SM8735, SM8845` → `8gen2` (HTP V73 is forward-compatible; don't add an `8gen3` bucket without uploading archives); other `SM*` → `min`; non-Qualcomm → null (CPU/MNN only). SDXL rows only for `{SM8650, SM8845, SM8750, SM8750P, SM8850, SM8850P}` (`isSdxlCapable`; SDXL needs 2048-dim dual CLIP, out of scope for the 768-dim pipeline). Re-validate against `xororz/sd-qnn` `tree/main` before changing suffixes.
- **Catalog:** `ModelCatalog.imageModels()` is computed per call. Snapdragon: 5 SD 1.5 NPU rows + SDXL (if capable) + 2 upscalers. Non-Snapdragon: 5 `xororz/sd-mnn` CPU rows only (never offer NPU rows there). `qnn2.28` is baked into URLs together with the `v3` upgrade marker; bump both.
- **Download:** `finalizeImageGenDownload` extracts the ZIP to `<filesDir>/sd_models/<id>/` with a canonical-path traversal check (must start with `target.canonicalPath + File.separator`; skip `..` entries first). `finalizeImageUpscalerDownload` stores a single `.bin` at `<filesDir>/sd_upscalers/<id>/upscaler_<bucket>.bin`. Image models never appear in chat model pickers.
- **Runtime:** `repo/ImageGenManager.kt` (Hilt singleton around `StableDiffusionManager.getInstance`; mutex-guarded `ensureRuntime()` extracts `qnnlibs.tar.xz` into `<filesDir>/ai_sd_runtime/`, internal storage; don't relocate without bumping the SDK version token). Never create a fresh manager per request.
- **Standalone QNN upscaler:** implemented via `sd_pipeline::loadStandaloneQnnUpscaler` (per-request load, needs `ensureQnnSystemReady` from `nativeInitRuntime`); `ImageGenManager.loadUpscaler` dispatches on `.mnn` suffix. Don't restore the `.mnn`-only guard.
- **Upscale cap:** `ImageTaskViewModel.runUpscale` limits input to 1024 max-edge (4× of 2048² ≈ 256 MB OOMs in bitmap creation).
- **Screen:** `ImageTaskScreen` + `ImageTaskTopBar` + single `ImageTaskViewModel` for all four modes (shared prompt/model/preview state). Route `NavScreens.ImageTask`, reached from the drawer.
- **Deps:** `app/build.gradle.kts` must declare `commons-compress` and `org.tukaani:xz:1.12` as `implementation` (file AAR deps pull no transitive POM; missing → `NoClassDefFoundError: XZInputStream`).

---

## Downloads screen

Route `NavScreens.Downloads`, opened from a Download icon in the Model Store top bar (dot badge when `activeCount > 0`).

- `DownloadCoordinator` (`@Singleton`) subscribes to `HxdManager.tasks`, keeps `hxdId → DownloadLabel(displayName, type)`, exposes `activeCount`, and on first terminal transition per task (`recordedTerminals` dedupes) writes a `DownloadHistoryEntry`. Fallback label is `DownloadLabel.fromUrl(url)`.
- `DownloadHistoryRepository` (`@Singleton`): vault `download_history_v1/`, collection `download_history`, TAGs `1=id, 2=displayName, 3=type, 4=status ord, 5=totalBytes, 6=completedAt, 7=error`, `MAX_ENTRIES = 50` pruned inside `insert()`. Don't raise the cap without pagination/grouping.
- **Every `HxdManager.enqueue(...)` must be followed immediately by `coordinator.registerLabel(hxdId, displayName, type)`.** Current sites: `ModelStoreViewModel.downloadModel`, `ImageGenManager.downloadRuntime` (label "AI Image Runtime", type `runtime`).
- Both classes are `:app`-only (they touch HXS). Never inject them in `:server` / `:inference`; use AIDL.
- UI: `DownloadsViewModel` (`activeDownloads`, `history`, `cancel`, `clearHistory`), `DownloadsScreen` (Active / History sections, per-status bodies), `DownloadsTopBar` via `AppTopBar`. Not in v1: retry, per-row delete, pause/resume, search, grouping.

---

## Dynamic Island overlay

`service/island/`. A black pill that morphs into a card over every app (`TYPE_APPLICATION_OVERLAY`), owned by `IslandOverlayService` (foreground, `dataSync`, `stopWithTask="false"`). Window: `WRAP_CONTENT + FLAG_NOT_FOCUSABLE + FLAG_LAYOUT_NO_LIMITS`, `gravity TOP|CENTER_HORIZONTAL`, `x=0`, title `IslandGeometry.OVERLAY_WINDOW_TITLE` ("TnIsland").

### Placement

- Always horizontally centered; **no X movement** (calibration, dodge, or otherwise). The user calibrates Y only (`offsetYDp`).
- Position comes from `WindowManager.LayoutParams.y` via `updateViewLayout`, driven by one `Animatable<Float>` (`animY`) collected through `snapshotFlow`. **Never use Compose `Modifier.offset` for placement** (the window rect doesn't move, so touches land on the old spot and the pill clips at the wrapped bounds).
- Slider changes `snapTo`; dodge changes `animateTo` with `spring(dampingRatio = 0.55f, StiffnessMediumLow)`. Slider drags never animate.
- The service scope uses `AndroidUiDispatcher.Main` (it carries a `MonotonicFrameClock`; plain `Dispatchers.Main` crashes `Animatable.animateTo`).

### Compose surface (`IslandOverlayRoot.kt`, `IslandShapes.kt`)

- One `updateTransition(expanded)` drives a single `progress` float; width, height, corner radius, `pillAlpha = (1 - 2p).coerceIn(0,1)`, and `cardAlpha = ((p - 0.5) * 2).coerceIn(0,1)` are all `lerp`-derived from it (no separate `animateFloat`s: they desync and stutter on settle).
- Spec: `spring(dampingRatio = 0.85f, stiffness = StiffnessMediumLow, visibilityThreshold = 0.0005f)`. Not `motionScheme.defaultSpatialSpec` (its overshoot makes size+corner jitter). Press scale uses `fastSpatialSpec`, mode swap `defaultSpatialSpec<IntOffset>`, fades `fastEffectsSpec`.
- Shape: `islandShape(cornerRadius)` = kyant `ContinuousRoundedRectangle` with its own `IslandContinuity` profile (`extendedFraction 0.6, arcFraction 0.4, bezier/arc curvature 1.2`), deliberately separate from the global `TnContinuity`. Key `remember { islandShape(...) }` on `cornerRadius.roundToInt()` to avoid per-frame allocation.
- Spacing/shape tokens come from `LocalDimens` / `LocalTnShapes` (`spacingSm`, `spacingLg`, `full`, icon sizes). `IslandGeometry` holds only identity constants (PILL_W/H, CARD_W/H/CORNER, PRESS_SCALE 0.92, SWIPE_THRESHOLD_DP 48) and service-shared ones (OUTER_PADDING_DP, DODGE_MARGIN_DP 8, MAX_DODGE_DP 96). Don't duplicate these between the composable and the accessibility service.
- Modes: `IslandMode { ASSISTANT, CONTROL }` is local UI state in `IslandSurface` (resets to ASSISTANT on re-attach; don't move it into the service/store). The mode glyph (TnIcons Sparkles / Sliders) shows in both pill and card.
- Gestures: tap and long-press toggle; horizontal swipe past 48 dp swaps mode in **both** states. Tap and drag live in **separate** `pointerInput` modifiers (one block would make them race for the pointer stream); don't gate the drag detector on `expanded`. Press feedback: `onPress` sets `pressed`, resets in `try { tryAwaitRelease() } finally { pressed = false }`.
- Haptics are required: `CONFIRM` (tap, action buttons), `LONG_PRESS`, `GESTURE_END` (mode swap). Action buttons are not wired yet (prototype).

### Smart dodge (`IslandAccessibilityService`)

Opt-in AccessibilityService (`BIND_ACCESSIBILITY_SERVICE`, `exported="true"` required, config `res/xml/island_accessibility_service.xml`, `canRetrieveWindowContent`). Events limited to `typeWindowStateChanged|typeWindowContentChanged`; coalesce 150 ms; walk `rootInActiveWindow` for visible clickable/long-clickable nodes; no `node.recycle()`.

- Pill rect is computed manually (`(screenWidth - PILL_W)/2`, `statusBarInset + OUTER_PADDING + position.offsetYDp`), **not** from `windows` or the current nudge/dodge (either creates an oscillation loop). Search zone expands by `DODGE_MARGIN_DP`; push-down uses the un-expanded rect: `pushDown = obstacle.bottom + margin - pillRect.top`, max over overlaps, clamped `[0, MAX_DODGE_DP]`, published to `IslandPositionStore.dodgeY`.
- `TYPE_WINDOW_STATE_CHANGED` resets `dodgeY = 0f` first (app-switch boundary); `TYPE_WINDOW_CONTENT_CHANGED` never clears it. `onUnbind` resets too.
- Foreground packages containing `"launcher"` (case-insensitive) publish `dodgeY = 0f` and skip the scan (launchers are all clickable and would pin the pill at the cap). Extend the substring check for odd OEM launchers rather than querying `PackageManager`.
- Enabled state = `IslandPositionStore.accessibilityActive` OR `ENABLED_ACCESSIBILITY_SERVICES` parsing. Prototype screen deep-links to `ACTION_ACCESSIBILITY_SETTINGS`.

Files: `IslandOverlayService`, `IslandComposeView`, `IslandOverlayRoot`, `IslandShapes`, `IslandGeometry`, `IslandPosition`, `IslandPositionStore` (`position`, `dodgeY`, `running`, `accessibilityActive`), `IslandAccessibilityService`, `OverlayLifecycleOwner`, `res/xml/island_accessibility_service.xml`, `island_accessibility_*` strings.

---

## Setup flow

Intro → **TermsConditions** (if `!tcAccepted`) → **DevNotes** (if `!onboardingComplete`) → SetupScreen (lock mode) → SetupPassword (if password) → SetupTheme → ModelSetup → SetupRag → Home.

`ScaffoldViewModel.resolveStartDestination()` order: `tcAccepted`, `onboardingComplete`, `securitySetupDone`, `modelSetupDone`, then `isLockEnabled` → PasswordScreen else Home. T&C must precede DevNotes. Callback chain: `onTermsAccepted → DevNotes`, `onSetupComplete → SetupTheme`, `onThemeSetupComplete → ModelSetup`, `onModelSetupComplete → SetupRag`, `onRagSetupComplete → Home`.

- **TermsConditions:** plain-English screen, no decline button, `BackHandler(true) {}`; `TermsConditionsViewModel` writes `tcAccepted`. Reusable from Settings (no-op + pop when already accepted).
- **DevNotes:** Compose-native welcome cards (`ui/screens/dev_notes/DevNotesScreenBody.kt`, signature `DevNotesScreen(innerPadding)`), plain language, no jargon.
- **SetupTheme:** commits on selection (`SetupThemeViewModel`/`ThemeController`), no `themeSetupDone` pref.
- **ModelSetup packs** (default for non-technical users; `ModelStoreViewModel.downloadPack(packId)` enqueues `PACK_CONTENTS[packId]`): `chat_only` (LFM2 350M), `chat_voice` (+ whisper-tiny-en + vits-piper-en_US-amy-low), `chat_voice_large` (Qwen3 0.6B + same voice). Custom toggle = Browse all (Store) + Pick local file (SAF → `ModelImportTypePicker`). Chat repos resolve through `downloadByQuickStartId` with quant priority `Q4_K_M → Q4_K_S → Q4_0 → Q5_K_M → Q5_K_S → Q8_0`, then smallest by size (don't collapse this list in `TNavigation.kt`).

---

## UI structure notes

- Drawer: two three-item rows only. Chat tools under New Chat (Store / Docs / Server); info at the bottom (Guide / Dev Notes / Credits). Settings is a gear `ActionButton` in the drawer header.
- Credits: `NavScreens.Credits`, fullscreen (included in `AppScaffold.isFullscreen`), `R.raw.credits` via `MediaPlayer` (`remember` + `DisposableEffect`, completion → `onExit()`), non-user-scrollable `verticalScroll` animated with `animateScrollTo(maxValue, tween(duration, LinearEasing))`, colors from `MaterialTheme.colorScheme`.
- App Guide: hub `AppGuideScreen` (3 categories) + detail screens through `GuideDetailLayout(innerPadding, lede, icon, steps, tips)` and `GuideTopBar`. Keep each step's `visual` composable in sync with the real UI. Adding a guide: `GuideEntry` in `guideCategories()`, key in `GuideEntryKeys`, `NavScreens` route, screen, `composable(...)` in `TNavigation`, `when` case in `AppTopBar.kt`.
- Other screens: `ServerScreen`/`ServerTopBar`, `HfExplorer*`, `PlusMenu`, `HomeScreenBottomBar`, `SetupThemeScreen`.

---

## Housekeeping

Update this file in the same change when touching: security architecture or threat model; any auth/API surface; sealed state layouts; feature IDs; persistent HXS keys; integrity checks, obfuscation, or crypto primitives; security-graph DI; release hardening; the Deferred list; or when you discover a new "don't regress" rule. If CLAUDE.md isn't in the diff, the change isn't finished.
