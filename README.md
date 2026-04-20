# ToolNeuron (Remote API & WebUI)

**Offline AI assistant for Android.** Run LLMs, generate images, search documents — all on-device. No cloud. No subscriptions. No data leaves your phone. Use as local AI server for text and image generation.


---

## What It Does

- **Text generation** — Load any GGUF model (Llama, Mistral, Gemma, Phi, Qwen, etc.) and chat with it locally
- **Image generation** — Stable Diffusion 1.5 on-device, with inpainting support
- **Image tools** — Upscale and segment images locally (depth, style transfer, inpainting coming soon)
- **RAG** — Inject PDFs, Word docs, Excel, EPUB into conversations with semantic search
- **Plugins** — Web search, file manager, calculator, notepad, date/time, system info, dev utils — all callable by the LLM
- **AI memory** — The AI remembers facts about you across conversations, with deduplication and a forgetting curve
- **Text-to-speech** — 10 voices, 5 languages, on-device synthesis
- **Encrypted storage** — AES-256-GCM with hardware-backed keys for all chat data
- **System backup** — Export everything as an encrypted `.tnbackup` file
- **Remote Access** — OpenAI-compatible API and built-in Web UI for network-wide access

---

## Remote API & Web UI

ToolNeuron includes a high-performance, OpenAI-compatible remote server. When enabled in **Settings**, you can access ToolNeuron from any device on your local network.

### URL Scheme
- **Web UI:** `http://<device-ip>:11434/`
- **API Base:** `http://<device-ip>:11434/v1`
- **Chat Completions:** `http://<device-ip>:11434/v1/chat/completions`
- **Images:** `http://<device-ip>:11434/v1/images/generations`
- **Models:** `http://<device-ip>:11434/v1/models`
- **System Status:** `http://<device-ip>:11434/api/ps`

### Web UI Features
Access the built-in interface at the root URL to enjoy:
- **Chat Studio:** Real-time streaming conversation with specialized model selection and performance metrics (tokens/s).
- **Image Studio:** Powerful text-to-image generation with resolution and iteration controls, and a session gallery.
- **System Dashboard:** Live monitoring of CPU clusters, RAM usage, and detailed status of currently loaded models.
- **Responsive Design:** Optimized for both mobile and desktop browsers with a modern dark theme.

---

## Requirements

| | Minimum | Recommended |
|---|---------|-------------|
| **Android** | 10 (API 29) | 12+ |
| **RAM** | 6 GB | 8–12 GB |
| **Storage** | 4 GB free | 10 GB free |
| **CPU** | ARM64 or x86_64 | Snapdragon 8 Gen 1+ |

---

## Getting Started

### 1. Install

[Google Play](https://play.google.com/store/apps/details?id=com.dark.tool_neuron) or [GitHub Releases](https://github.com/Siddhesh2377/ToolNeuron/releases).

### 2. Get a model

**From the in-app Model Store (recommended):**
1. Open the drawer menu → Model Store
2. Add a HuggingFace repository (e.g. `bartowski/Phi-3.5-mini-instruct-GGUF`)
3. Pick a quantization and download

**Or manually:**
1. Download a `.gguf` file from [HuggingFace](https://huggingface.co/models?other=gguf)
2. Use the model picker in ToolNeuron to load it

### 3. Chat

Select your model, wait for it to load, start typing. Responses stream in real-time.

### Recommended models for getting started

| Use case | Model | Size |
|----------|-------|------|
| Quick test | Qwen3.5 0.8B Q4_K_M | ~600 MB |
| General use | Qwen3.5 4B Q4_K_M | ~2.8 GB |
| Power users | Qwen3.5 9B Q4_K_M | ~5.5 GB |

> Pick Q4_K_M for a good balance between quality and size. Use Q6_K if your device has the RAM for it.

---

## Features

### Text Generation
- Any GGUF model works — load via file picker (no storage permissions needed, uses SAF)
- Configurable parameters: temperature, top-k, top-p, min-p, repeat penalty, context length
- Function calling with grammar-constrained JSON output
- Thinking mode for models that support it
- Per-model configs saved to database

### Image Generation
- Stable Diffusion 1.5 (censored and uncensored variants)
- Text-to-image and inpainting
- Configurable steps, CFG scale, seed, negative prompts, schedulers

### Image Tools
| Tool | Status |
|------|--------|
| Upscaling | Ready |
| Segmentation (MobileSAM) | Ready |
| Depth estimation | Model pending |
| Style transfer | Model pending |
| LaMa inpainting | Model pending |

### RAG (Document Intelligence)
Create knowledge bases from:
- **Files** — PDF, Word (.doc/.docx), Excel (.xls/.xlsx), EPUB, TXT
- **Text** — Paste any text content
- **Chat history** — Convert past conversations into searchable knowledge
- **Neuron Packets** — Import encrypted `.neuron` RAG files

The RAG pipeline uses hybrid retrieval: FTS4 BM25 + vector search + Reciprocal Rank Fusion + Maximal Marginal Relevance. Results are injected into the conversation context automatically.

Encrypted RAGs support admin passwords and read-only user access.

### Plugin System
7 built-in plugins the LLM can call during conversations:

| Plugin | What it does |
|--------|-------------|
| **Web Search** | Search the web and scrape content |
| **File Manager** | List, read, create files |
| **Calculator** | Math expressions and unit conversion |
| **Notepad** | Save and retrieve notes |
| **Date & Time** | Current time, timezone conversion, date math |
| **System Info** | RAM, battery, storage, device details |
| **Dev Utils** | Hash, encode, format, text transforms |

### AI Memory
Inspired by [Mem0](https://github.com/mem0ai/mem0). After conversations, the LLM extracts facts about you and stores them for future context. Deduplication via Jaccard similarity, with a forgetting curve so stale memories decay. You can view, edit, and delete memories from the Memory screen.

### Text-to-Speech
On-device TTS via Supertonic (ONNX Runtime). 10 voices (5 female, 5 male), 5 languages (EN, KR, ES, PT, FR). Adjustable speed and quality. Auto-speak option reads responses aloud.

### Hardware Tuning
Auto-detects CPU topology (P-cores, E-cores) and recommends thread count, context size, and cache settings. Three modes: Performance, Balanced, Power Saver.

### System Backup
Export everything to an encrypted `.tnbackup` file (PBKDF2 + AES-256-GCM):
- Chat history, AI memories, personas, knowledge graphs
- Model configs and app settings
- RAG files and AI models (optional, can be large)

---

## Privacy

- **Zero data collection.** No telemetry, no analytics, no crash reporting.
- **Everything stays on-device.** Conversations, generated images, documents, TTS audio — none of it leaves your phone.
- **Encrypted storage.** AES-256-GCM with Android KeyStore. On supported devices, keys live in the Trusted Execution Environment.
- **No storage permissions.** Models load through Android's file picker (SAF). The app can't access arbitrary files.
- **Open source.** Read the code yourself.

---

## Building from Source

### Prerequisites
- Android Studio Meerkat (2025.1.1)+
- JDK 17
- Android SDK 36+, NDK 26.x

### Build

```bash
git clone https://github.com/Siddhesh2377/ToolNeuron.git
cd ToolNeuron

# Debug
./gradlew assembleDebug
./gradlew installDebug

# Release
./gradlew assembleRelease
```

APKs land in `app/build/outputs/apk/`.

If you hit NDK issues, make sure NDK 26.x is installed via SDK Manager. For memory issues during build, bump the Gradle heap in `gradle.properties`:

```properties
org.gradle.jvmargs=-Xmx4096m
```

---

## Architecture

| Layer | Technology |
|-------|-----------|
| Language | Kotlin, C++ (JNI) |
| UI | Jetpack Compose |
| Text inference | llama.cpp |
| Image inference | LocalDream (SD 1.5) |
| TTS | Supertonic (ONNX Runtime) |
| Database | Room + UMS (custom binary format) |
| Encryption | AES-256-GCM, Android KeyStore |
| DI | Dagger Hilt |
| Async | Kotlin Coroutines + Flow |

### Modules
| Module | Purpose |
|--------|---------|
| `app` | Main Android application |
| `ums` | Unified Memory System — binary record storage with JNI |
| `neuron-packet` | Encrypted RAG packet format with access control |
| `memory-vault` | Legacy encrypted storage (read-only, used for migration) |
| `system_encryptor` | Native encryption primitives |
| `file_ops` | Native file operations |

---

## Contributing

See [CONTRIBUTORS.md](CONTRIBUTORS.md) for the project ecosystem and related repos.

### How to contribute
1. Fork the repo
2. Create a feature branch: `git checkout -b feature/your-feature`
3. Make focused commits with clear messages
4. **Test on a real device** — emulators don't reflect real performance
5. Open a PR with a description of what you changed and how you tested it

### Priority areas
- Bug fixes and stability
- Device compatibility testing (especially mid-range phones)
- Performance improvements
- Documentation and translations
- New plugins

### What not to do
- Don't submit untested code
- Don't add cloud dependencies or telemetry
- Don't break offline functionality
- Don't add broad storage permissions

---

## Security

If you find a security vulnerability:
1. **Do not** open a public GitHub issue
2. Email siddheshsonar2377@gmail.com
3. Include reproduction steps
4. Allow reasonable time for a fix before disclosure

---

## Acknowledgments

- [llama.cpp](https://github.com/ggerganov/llama.cpp) — LLM inference engine
- [LocalDream](https://github.com/xororz/local-dream) — Stable Diffusion on Android
- [ONNX Runtime](https://onnxruntime.ai/) — TTS inference
- [Mem0](https://github.com/mem0ai/mem0) — AI memory architecture inspiration
- [SillyTavern](https://github.com/SillyTavern/SillyTavern) — Character card format reference
- [Apache POI](https://poi.apache.org/), [PDFBox-Android](https://github.com/TomRoush/PdfBox-Android), [EpubLib](https://github.com/psiegman/epublib) — Document parsing
- [Jetpack Compose](https://developer.android.com/jetpack/compose), [Room](https://developer.android.com/training/data-storage/room), [Hilt](https://dagger.dev/hilt/), [OkHttp](https://square.github.io/okhttp/), [Coil 3](https://coil-kt.github.io/coil/), [Jsoup](https://jsoup.org/)

---

## License

[Apache License 2.0](LICENSE) — use it, modify it, distribute it. Attribution appreciated.

---

Built by [Siddhesh Sonar](https://github.com/Siddhesh2377)

[Star the repo](https://github.com/Siddhesh2377/ToolNeuron) · [Report a bug](https://github.com/Siddhesh2377/ToolNeuron/issues) · [Join Discord](https://discord.gg/mVPwHDhrAP)
