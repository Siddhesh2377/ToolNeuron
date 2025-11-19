<div align="center">

# ToolNeuron
### Your AI Hub in Your Pocket

[![Platform](https://img.shields.io/badge/Platform-Android_8.0%2B-3DDC84?logo=android&logoColor=white)](https://github.com/Siddhesh2377/NeuroVerse/releases)
[![Latest Release](https://img.shields.io/badge/Release-Beta_5.1-blue?logo=github)](https://github.com/Siddhesh2377/NeuroVerse/releases/latest)
[![License](https://img.shields.io/badge/License-Apache_2.0-green.svg)](LICENSE)
[![Discord](https://img.shields.io/badge/Join-Discord-5865F2?logo=discord&logoColor=white)](https://discord.gg/mVPwHDhrAP)

**A privacy-focused mobile ecosystem for AI inference** — Run offline models locally or connect to 100+ cloud models. No subscriptions. No data harvesting. Complete control.

[**Download Beta 5.1**](https://github.com/Siddhesh2377/NeuroVerse/releases/latest) • [**Join Discord**](https://discord.gg/mVPwHDhrAP) • [**Documentation**](https://github.com/Siddhesh2377/NeuroVerse/wiki)

</div>

---

## 🎯 What is ToolNeuron?

ToolNeuron is an open-source Android application that brings enterprise-grade AI capabilities to your smartphone. It bridges the gap between **privacy** (limited offline apps) and **power** (cloud-dependent platforms) through a hybrid architecture that puts you in control.

**Run AI Your Way:**
- **🔒 Privacy Mode:** Execute quantized GGUF models entirely on-device using `llama.cpp`. Your data never leaves your phone.
- **⚡ Power Mode:** Connect to 100+ premium models (GPT-4, Claude 3.5, Llama 3, Gemini) via OpenRouter for complex tasks.
- **🔄 Hybrid Intelligence:** Seamlessly switch between modes mid-conversation while preserving context.

---

## ✨ Key Features

### 🧠 Dual Inference Engine
- **Local Execution:** Run GGUF models offline with zero latency
- **Cloud Orchestration:** Access 100+ models through a unified OpenRouter API
- **Smart Streaming:** Real-time token generation with intelligent context management

### 🎙️ Premium Offline TTS
- **11 Professional Voices:** 5 American Female, 2 American Male, 2 British Female, 2 British Male
- **Powered by Sherpa-ONNX:** Runs entirely on CPU/NPU with zero network calls
- **Zero Latency:** Instant voice synthesis with no cloud dependencies

### 🔌 Extensible Plugin System
- **Web Search:** Real-time information retrieval
- **Web Scraper:** Extract and inject context from any URL
- **DataHub:** Mount JSON/text datasets to dynamically enhance model knowledge
- **Coming Soon:** Code execution, image processing, document analysis

### 💾 Advanced Context Management
- **Conversation Persistence:** Full chat history retention
- **Dynamic Datasets:** Attach custom knowledge bases without model retraining
- **Context Preservation:** Switch models mid-conversation without losing context

---

## 📸 Screenshots

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

## 🆚 Why Choose ToolNeuron?

| Feature | ToolNeuron | Traditional AI Apps |
|:--------|:----------:|:-------------------:|
| **Offline GGUF Models** | ✅ Native Support | ❌ Cloud Only |
| **Model Freedom** | ✅ 100+ Options | ❌ Vendor Lock-in |
| **Content Policy** | ✅ Uncensored | ❌ Heavy Filtering |
| **Privacy Architecture** | ✅ Local-First | ❌ Server Logging |
| **Offline TTS** | ✅ 11 Premium Voices | ❌ Cloud Dependencies |
| **Pricing** | ✅ Free (BYOK/Model) | 💰 $20-60/month |
| **Open Source** | ✅ Apache 2.0 | ❌ Proprietary |

---

## 🚀 Quick Start

### Installation

#### Method 1: Install APK (Recommended)
1. Download the latest release from [**GitHub Releases**](https://github.com/Siddhesh2377/NeuroVerse/releases/latest)
2. Install `ToolNeuron-Beta-5.1.apk` on Android 8.0+ (Android 14+ recommended)
3. Grant necessary permissions when prompted

#### Method 2: Build from Source
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

### Initial Setup

**Option A: Privacy Mode (Offline)**
1. Download a GGUF model from [HuggingFace](https://huggingface.co/models) (e.g., `Llama-3-8B-Q4_K_M.gguf`)
2. Open ToolNeuron → **Settings** → **Local Models** → **Import Model**
3. Select your downloaded GGUF file
4. Start chatting completely offline

**Option B: Power Mode (Cloud)**
1. Generate an API key at [OpenRouter.ai](https://openrouter.ai)
2. Open ToolNeuron → **Settings** → **API Configuration**
3. Paste your OpenRouter API key
4. Access 100+ models instantly

---

## 💻 System Requirements

### Minimum
- **OS:** Android 8.0+ (API 26)
- **RAM:** 4GB
- **Storage:** 2GB free space
- **Use Case:** Cloud models only

### Recommended (Local Inference)
- **OS:** Android 14+
- **RAM:** 8GB+
- **Processor:** Snapdragon 8 Gen 1 or equivalent
- **Storage:** 5GB+ (for local models)
- **NPU:** Optional but improves performance

---

## 🗺️ Development Roadmap

### Q1 2026: Sensory Integration
- [ ] **Advanced TTS:** Multi-voice conversation simulation
- [ ] **Speech-to-Text:** Offline voice input via Whisper/Sherpa
- [ ] **Export System:** Save conversations, code snippets, and DataHub configs

### Q2 2026: Universal Runtime
- [ ] **Format Expansion:** Native TFLite & ONNX execution
- [ ] **Image Generation:** On-device Stable Diffusion (quantized)
- [ ] **Vector Memory:** Long-term context retention using embeddings

### Q3 2026: Ecosystem Maturity
- [ ] **Multi-Modal Models:** Vision capabilities (LLaVA, GPT-4V)
- [ ] **Cross-Device Sync:** Desktop companion apps (Windows/Linux)
- [ ] **Plugin Marketplace:** Community-contributed extensions

---

## 🏗️ Architecture

ToolNeuron is built with modern Android architecture principles:

- **Language:** Kotlin
- **UI Framework:** Jetpack Compose
- **Local Inference:** `llama.cpp` (C++ with JNI bindings)
- **TTS Engine:** Sherpa-ONNX
- **API Layer:** Retrofit + OkHttp
- **Database:** Room (SQLite)
- **Async Operations:** Kotlin Coroutines + Flow

---

## 🤝 Contributing

We welcome contributions from the community! Here's how you can help:

1. **Fork** the repository
2. Create a **feature branch** (`git checkout -b feature/AmazingFeature`)
3. **Commit** your changes (`git commit -m 'Add AmazingFeature'`)
4. **Push** to the branch (`git push origin feature/AmazingFeature`)
5. Open a **Pull Request**

**Areas we need help:**
- 🐛 Bug reports and fixes
- 📝 Documentation improvements
- 🌍 Translations (i18n)
- 🧪 Testing on various Android devices
- ✨ Feature suggestions and implementations

---

## 📄 License

Distributed under the **Apache 2.0 License**. See [`LICENSE`](LICENSE) for more information.

This means you can:
- ✅ Use commercially
- ✅ Modify and distribute
- ✅ Use privately
- ✅ Patent use

With conditions:
- 📋 License and copyright notice
- 📝 State changes made to code

---

## 🙏 Acknowledgments

ToolNeuron stands on the shoulders of giants:

- **[llama.cpp](https://github.com/ggerganov/llama.cpp)** — The backbone of efficient local inference
- **[Sherpa-ONNX](https://github.com/k2-fsa/sherpa-onnx)** — Powering our premium offline TTS
- **[OpenRouter](https://openrouter.ai)** — Unified API gateway to 100+ models
- **[Jetpack Compose](https://developer.android.com/jetpack/compose)** — Modern Android UI toolkit

---

## 📞 Support & Community

- **Discord:** [Join our community](https://discord.gg/mVPwHDhrAP) for support and discussions
- **Issues:** [Report bugs or request features](https://github.com/Siddhesh2377/NeuroVerse/issues)
- **Discussions:** [Ask questions and share ideas](https://github.com/Siddhesh2377/NeuroVerse/discussions)

---

<div align="center">

### ⭐ Star this project if you find it useful!

**Built with ❤️ by [Siddhesh2377](https://github.com/Siddhesh2377) and the Open Source Community**

[Report Bug](https://github.com/Siddhesh2377/NeuroVerse/issues) • [Request Feature](https://github.com/Siddhesh2377/NeuroVerse/issues) • [View Releases](https://github.com/Siddhesh2377/NeuroVerse/releases)

</div>