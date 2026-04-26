# ToolNeuron

Privacy-first, fully offline, on-device AI assistant for Android.

ToolNeuron runs language models, vision-language models, retrieval over your own documents, and on-device speech (TTS + STT) — all without sending anything off your phone.

## What it is

- **On-device LLM chat.** GGUF models running locally via a separate inference process. Streaming, multi-turn, sampling controls, prompt cache, KV-window eviction.
- **Vision-language models (VLM).** Drop in a base GGUF + colocated mmproj projector and attach images directly in chat.
- **RAG over your documents.** Embed your own PDFs / text and query them locally with a small on-device embedder.
- **Voice — TTS and STT.** Streaming text-to-speech on assistant messages and tap-to-talk speech-to-text input. sherpa-onnx under the hood.
- **Remote Server.** An embedded OpenAI-compatible HTTP server that exposes your loaded model over the LAN with a bundled offline web UI. Bearer-token authenticated, rate-limited.
- **HuggingFace Explorer.** Search / filter / download GGUF (and TTS / STT) models directly from HuggingFace, no account required for public repos.
- **Hardened lock screen.** App PIN with Argon2id, panic PIN, exponential lockout backoff, hard-wipe threshold, clock-rollback defense, FLAG_SECURE on PIN entry. Vault-grade encrypted storage, Keystore-wrapped device key (StrongBox / TEE preferred).
- **Material 3 Compose UI.** Setup → theme → models → chat. Themes, palettes, dynamic spacing, dark/light auto.

## What it is *not*

- **Not a Google Play Services app.** No Play Services, no FCM, no Play Integrity, no Firebase.
- **Not connected to telemetry.** No analytics, no crash reporting upstream, no remote config.
- **Not a cloud-API frontend.** It does not call OpenAI, Anthropic, Google, or any other inference vendor. Only models that live on your device run.
- **Not a tool-calling agent platform.** Tool calling, image generation, plugin marketplaces, and Termux integration are explicitly out of scope.
- **Not rooted-only or developer-only.** Runs on stock Android 10+. If your device is rooted, the app warns you once that other root-capable apps could read its on-disk state.
- **Not minSdk-permissive.** minSdk 29; arm64-v8a + x86_64 only.

## Build

Debug builds and verification:

```sh
./gradlew :app:compileDebugKotlin   # type / API check
./gradlew :app:installDebug         # install on connected device
```

Release builds are produced from Android Studio. Provide the four signing keys in `local.properties`:

```
TN_KEYSTORE_PATH=/path/to/your/keystore
TN_KEYSTORE_PASSWORD=...
TN_KEY_ALIAS=...
TN_KEY_PASSWORD=...
```

If the keys are missing, release falls back to unsigned so the dev flow stays unblocked.

## License

See `LICENSE`.
