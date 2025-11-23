<div align="center">

# ToolNeuron

### Enterprise-Grade AI Inference for Mobile Devices

[![Platform](https://img.shields.io/badge/Platform-Android_8.0%2B-3DDC84?logo=android&logoColor=white)](https://apkpure.com/p/com.dark.neurov)
[![APKPure](https://img.shields.io/badge/APKPure-Download-01C853)](https://apkpure.com/p/com.dark.neurov)

[![Latest Release](https://img.shields.io/badge/Release-Beta_5.1-blue?logo=github)](https://github.com/Siddhesh2377/NeuroVerse/releases/latest)
[![License](https://img.shields.io/badge/License-Apache_2.0-green.svg)](LICENSE)
[![Discord](https://img.shields.io/badge/Join-Discord-5865F2?logo=discord&logoColor=white)](https://discord.gg/mVPwHDhrAP)

> "Bridging the gap between privacy and power in mobile AI—where enterprise capabilities meet consumer devices."

**A privacy-focused mobile ecosystem for AI inference.** Run offline models locally or connect to 100+ cloud models. No subscriptions. No data harvesting. Complete control.

[![Get it on APKPure](https://img.shields.io/badge/Get_it_on-APKPure-01C853?style=for-the-badge&logo=android&logoColor=white)](https://apkpure.com/p/com.dark.neurov)
 
  [GitHub Releases](https://github.com/Siddhesh2377/NeuroVerse/releases/latest) • [Join Discord](https://discord.gg/mVPwHDhrAP) • [Documentation](https://github.com/Siddhesh2377/NeuroVerse/wiki)

</div>

---

## Overview

ToolNeuron is an open-source Android application that delivers enterprise-grade AI capabilities to smartphones through a hybrid architecture prioritizing user control and data sovereignty.

### Core Philosophy

Traditional mobile AI applications force users to choose between two unsatisfactory options: limited offline functionality or cloud-dependent platforms that compromise privacy. ToolNeuron resolves this false dichotomy.

**Three Operating Modes:**

- **Privacy Mode** — Execute quantized GGUF models entirely on-device using `llama.cpp`. Your data never leaves your phone.
- **Power Mode** — Connect to 100+ premium models (GPT-4, Claude 3.5, Llama 3, Gemini) via OpenRouter for complex tasks.
- **Hybrid Intelligence** — Seamlessly switch between modes mid-conversation while preserving full context.

---

## Key Features

### Dual Inference Engine

**Local Execution**  
Native support for GGUF model formats with zero-latency inference. All processing occurs on-device with no network dependencies.

**Cloud Orchestration**  
Unified API integration through OpenRouter provides access to 100+ state-of-the-art models without vendor lock-in.

**Intelligent Streaming**  
Real-time token generation with context-aware memory management ensures smooth performance across both local and cloud deployments.

### Premium Offline Text-to-Speech

Powered by Sherpa-ONNX, ToolNeuron includes 11 professional-grade voices (5 American Female, 2 American Male, 2 British Female, 2 British Male) that run entirely on CPU/NPU with zero cloud dependencies and near-instantaneous synthesis.

### Extensible Plugin System

**Available Now:**
- Web Search — Real-time information retrieval
- Web Scraper — Extract and inject context from any URL
- DataHub — Mount JSON/text datasets to dynamically enhance model knowledge

**In Development:**
- Code execution environments
- Image processing pipelines
- Document analysis tools

### Advanced Context Management

- **Conversation Persistence** — Full chat history retention with efficient storage
- **Dynamic Datasets** — Attach custom knowledge bases without model retraining
- **Context Preservation** — Switch models mid-conversation without losing thread continuity

---

## Screenshots

<div align="center">
  <table>
    <tr>
      <td align="center"><b>Chat Interface</b><br/>Multi-modal conversations</td>
      <td align="center"><b>Model Hub</b><br/>100+ models at your fingertips</td>
      <td align="center"><b>Code Canvas</b><br/>Syntax highlighting & export</td>
      <td align="center"><b>DataHub</b><br/>Dynamic context injection</td>
    </tr>
    <tr>
      <td><img src="https://github.com/user-attachments/assets/f4d2c28a-a297-4c08-83e5-391f8bd82d89" width="200" alt="Chat Interface"></td>
      <td><img src="https://github.com/user-attachments/assets/257022d7-8d3b-42a3-97c7-589d8f09fa47" width="200" alt="Model Selection"></td>
      <td><img src="https://github.com/user-attachments/assets/4be156dd-cc55-4eb0-9d89-790f8f11db1e" width="200" alt="Code Canvas"></td>
      <td><img src="https://github.com/user-attachments/assets/2e1c4065-14bb-411b-9021-fc4071a04318" width="200" alt="Settings"></td>
    </tr>
  </table>
</div>

---

## Comparative Analysis

| Feature | ToolNeuron | Traditional AI Apps |
|:--------|:----------:|:-------------------:|
| Offline GGUF Models | ✓ Native Support | ✗ Cloud Only |
| Model Freedom | ✓ 100+ Options | ✗ Vendor Lock-in |
| Content Policy | ✓ Uncensored | ✗ Heavy Filtering |
| Privacy Architecture | ✓ Local-First | ✗ Server Logging |
| Offline TTS | ✓ 11 Premium Voices | ✗ Cloud Dependencies |
| Pricing Model | ✓ Free (BYOK/Model) | ✗ $20-60/month |
| Source Availability | ✓ Apache 2.0 | ✗ Proprietary |

---

## Installation

### Method 1: APKPure (Recommended)

Visit [ToolNeuron on APKPure](https://apkpure.com/p/com.dark.neurov) for the latest stable release with automatic update notifications.

### Method 2: Direct APK Download

Download the latest release from [GitHub Releases](https://github.com/Siddhesh2377/NeuroVerse/releases/latest) and install `ToolNeuron-Beta-5.1.apk` on Android 8.0+ devices.

### Method 3: Build from Source

```bash
# Clone repository
git clone https://github.com/Siddhesh2377/NeuroVerse.git
cd NeuroVerse

# Open in Android Studio (Ladybug or newer)
# Sync Gradle dependencies
./gradlew assembleDebug

# Install on connected device
./gradlew installDebug
```

---

## Configuration

### Privacy Mode Setup (Offline)

1. Download a GGUF model from [HuggingFace](https://huggingface.co/models) (recommended: `Llama-3-8B-Q4_K_M.gguf`)
2. Navigate to **Settings → Local Models → Import Model**
3. Select your downloaded GGUF file
4. Begin completely offline inference

### Power Mode Setup (Cloud)

1. Generate an API key at [OpenRouter.ai](https://openrouter.ai)
2. Navigate to **Settings → API Configuration**
3. Enter your OpenRouter API key
4. Access 100+ models immediately

---

## System Requirements

### Minimum Specifications
- **Operating System:** Android 8.0+ (API 26)
- **RAM:** 4GB
- **Storage:** 2GB available space
- **Use Case:** Cloud models only

### Recommended Specifications (Local Inference)
- **Operating System:** Android 14+
- **RAM:** 8GB or greater
- **Processor:** Snapdragon 8 Gen 1 or equivalent
- **Storage:** 5GB+ available space (for local models)
- **NPU:** Optional but significantly improves performance

---

## Development Roadmap

### Q1 2026: Sensory Integration
- Advanced TTS with multi-voice conversation simulation
- Speech-to-Text via offline Whisper/Sherpa implementation
- Comprehensive export system for conversations, code snippets, and DataHub configurations

### Q2 2026: Universal Runtime
- Native TFLite and ONNX execution support
- On-device Stable Diffusion (quantized) for image generation
- Vector memory implementation for long-term context retention using embeddings

### Q3 2026: Ecosystem Maturity
- Multi-modal model support with vision capabilities (LLaVA, GPT-4V)
- Cross-device synchronization with desktop companion applications (Windows/Linux)
- Community-driven plugin marketplace

---

## Technical Architecture

ToolNeuron implements modern Android development patterns:

- **Language:** Kotlin
- **UI Framework:** Jetpack Compose
- **Local Inference:** `llama.cpp` (C++ with JNI bindings)
- **TTS Engine:** Sherpa-ONNX
- **API Layer:** Retrofit + OkHttp
- **Database:** Room (SQLite)
- **Async Operations:** Kotlin Coroutines + Flow

---

## Contributing

We welcome contributions from developers, researchers, and AI enthusiasts. To contribute:

1. Fork the repository
2. Create a feature branch (`git checkout -b feature/AmazingFeature`)
3. Commit your changes with descriptive messages
4. Push to your branch (`git push origin feature/AmazingFeature`)
5. Open a Pull Request with detailed description

**Priority Areas:**
- Bug reports and fixes
- Documentation improvements
- Internationalization (i18n)
- Cross-device testing and optimization
- Feature implementations aligned with roadmap

---

## License

Distributed under the Apache 2.0 License. See [`LICENSE`](LICENSE) for complete terms.

**Permissions:**
- Commercial use
- Modification and distribution
- Private use
- Patent use

**Conditions:**
- License and copyright notice required
- State changes documentation required

---

## Acknowledgments

> "If I have seen further, it is by standing on the shoulders of giants." — Isaac Newton

ToolNeuron builds upon exceptional open-source work:

- **[llama.cpp](https://github.com/ggerganov/llama.cpp)** — Efficient local inference implementation
- **[Sherpa-ONNX](https://github.com/k2-fsa/sherpa-onnx)** — Premium offline text-to-speech synthesis
- **[OpenRouter](https://openrouter.ai)** — Unified API gateway for diverse model ecosystems
- **[Jetpack Compose](https://developer.android.com/jetpack/compose)** — Modern declarative UI framework

---

## Support & Community

- **Discord Community:** [Join discussions and get support](https://discord.gg/mVPwHDhrAP)
- **Issue Tracker:** [Report bugs or request features](https://github.com/Siddhesh2377/NeuroVerse/issues)
- **GitHub Discussions:** [Technical questions and ideas](https://github.com/Siddhesh2377/NeuroVerse/discussions)

---

<div align="center">

**Built by [Siddhesh2377](https://github.com/Siddhesh2377) and the Open Source Community**

If you find ToolNeuron valuable, please consider starring the repository.

[Report Bug](https://github.com/Siddhesh2377/NeuroVerse/issues) • [Request Feature](https://github.com/Siddhesh2377/NeuroVerse/issues) • [View Releases](https://github.com/Siddhesh2377/NeuroVerse/releases) • [Download on APKPure](https://apkpure.com/p/com.dark.neurov)

</div>
