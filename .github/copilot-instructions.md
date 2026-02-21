# Copilot Instructions for ToolNeuron

## Build & Test

```bash
# Build debug APK
./gradlew assembleDebug

# Build release APK (requires signing config in local.properties)
./gradlew assembleRelease

# Run all unit tests
./gradlew test

# Run tests for a single module
./gradlew :app:test
./gradlew :memory-vault:test
./gradlew :neuron-packet:test

# Run a single test class
./gradlew :app:testDebugUnitTest --tests "com.dark.tool_neuron.McpToolMapperTest"
```

**Requirements:** JDK 17, Android SDK 36, NDK 26.x. The `neuron-packet` module requires OpenSSL prebuilt libraries — see `neuron-packet/SETUP.md`.

## Architecture

This is an Android app (Kotlin + C++) that runs LLMs and Stable Diffusion entirely on-device. It's a multi-module Gradle project:

- **`app`** — Main application. Jetpack Compose UI, MVVM with Hilt DI, Room database. Package: `com.dark.tool_neuron`.
- **`memory-vault`** — Encrypted binary storage engine with WAL crash recovery, LZ4 compression, full-text and vector indices. Package: `com.memoryvault`. See `docs/MemoryVault.MD` for the storage format spec.
- **`neuron-packet`** — Secure data export/import with AES-256-GCM encryption. Has both Kotlin and C++ (JNI) sides. Package: `com.neuronpacket`. The C++ code lives in `neuron-packet/src/main/cpp/` and builds via CMake.

### AI inference layer

Native inference is provided by pre-built AAR libraries in `libs/`:
- `ai_gguf-release.aar` — llama.cpp bindings for text generation (GGUF models)
- `ai_sd-release.aar` — Stable Diffusion 1.5 bindings for image generation

These are wrapped by engine classes in `app/.../engine/`:
- `GGUFEngine` — loads GGUF models, generates text, supports function calling with tool grammars
- `DiffusionEngine` — loads SD models, generates images
- `EmbeddingEngine` — generates text embeddings for RAG/vector search

`LLMService` is a bound Android Service that exposes these engines via AIDL IPC.

### Data flow

`UI (Compose screens)` → `ViewModel (@HiltViewModel)` → `Repository` → `Room DAO / MemoryVault`

ViewModels expose `StateFlow` for reactive UI updates. All async work uses Kotlin Coroutines with `viewModelScope`.

## Key Conventions

- **DI:** Hilt everywhere. Activities use `@AndroidEntryPoint`, ViewModels use `@HiltViewModel`. All modules are defined in `app/.../di/HiltModules.kt` and installed in `SingletonComponent`.
- **Navigation:** Jetpack Compose NavHost in `MainActivity`. Routes are defined as a `Screen` sealed class. Uses slide + fade transitions.
- **Serialization:** `kotlinx.serialization` for JSON. Room entities live in `models/table_schema/`.
- **NDK targets:** `arm64-v8a` and `x86_64` only.
- **Build config:** Properties are read from `local.properties` or environment variables via `getProperty()` (defined in each module's `build.gradle.kts`). The `ALIAS` property is used for build config fields.
- **UI constants:** Shared sizing/padding values are in `global/Standards.kt`.
- **Version catalog:** All dependency versions are managed in `gradle/libs.versions.toml`.
