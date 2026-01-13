# ToolNeuron

### Privacy-First AI Assistant for Android

[![Platform](https://img.shields.io/badge/Platform-Android_8.0%2B-3DDC84?logo=android&logoColor=white)](https://github.com/Siddhesh2377/ToolNeuron)
[![License](https://img.shields.io/badge/License-Apache_2.0-green.svg)](LICENSE)
[![Release](https://img.shields.io/badge/Release-1.0_Beta-blue)](https://github.com/Siddhesh2377/ToolNeuron/releases)
[![Discord](https://img.shields.io/badge/Discord-Join-5865F2?logo=discord&logoColor=white)](https://discord.gg/mVPwHDhrAP)

ToolNeuron is an offline-first AI assistant for Android that runs large language models and image generation completely on-device. No cloud dependencies, no subscriptions, complete privacy.

[Download APK](https://github.com/Siddhesh2377/ToolNeuron/releases) • [Join Discord](https://discord.gg/mVPwHDhrAP) • [Report Issue](https://github.com/Siddhesh2377/ToolNeuron/issues)

---

## What It Does

ToolNeuron runs AI models directly on your Android device. Import any GGUF model (Llama, Mistral, Gemma) or Stable Diffusion 1.5 model and start generating text or images completely offline.

**Core Features:**
- Run any GGUF format language model locally
- Generate images with Stable Diffusion 1.5 (censored or uncensored)
- Encrypted local storage for conversations and generated content
- Custom model configurations and sampling parameters
- Complete offline operation with zero data transmission

**Privacy Architecture:**
- All processing occurs on-device
- AES-256-GCM encryption for stored data
- No telemetry, no logging, no cloud dependencies
- Open source for full transparency

---

## Screenshots

<table>
  <tr>
    <td><img src="./docs/img/Empty-Chat.png" alt="Chat Interface" width="200"/></td>
    <td><img src="./docs/img/Text+Image.png" alt="Text and Image Generation" width="200"/></td>
    <td><img src="./docs/img/Generated-Image.png" alt="Image Output" width="200"/></td>
  </tr>
  <tr>
    <td><img src="./docs/img/Image-Gen_Progress.png" alt="Generation Progress" width="200"/></td>
    <td><img src="./docs/img/LoadCustom-Model.png" alt="Model Loading" width="200"/></td>
    <td><img src="./docs/img/System-Info-UI.png" alt="System Information" width="200"/></td>
  </tr>
</table>

---

## Installation

### Requirements

**Minimum (Text Generation Only):**
- Android 8.0+ (API 26)
- 6GB RAM
- 4GB free storage

**Recommended (Text + Image Generation):**
- Android 10+
- 8GB RAM (12GB for smooth operation)
- 8GB free storage
- Snapdragon 8 Gen 1 or equivalent

### Install

1. Download the latest APK from [Releases](https://github.com/Siddhesh2377/ToolNeuron/releases)
2. Enable installation from unknown sources in Android settings
3. Install the APK
4. Launch ToolNeuron

---

## Quick Start

### Text Generation

1. Download a GGUF model from [Hugging Face](https://huggingface.co/models?other=gguf)
   - Recommended: Llama-3-8B-Q4_K_M.gguf (4.5GB)
   - Budget: TinyLlama-1.1B-Q4_K_M.gguf (669MB)

2. Open ToolNeuron and navigate to model selection
3. Import your downloaded GGUF file
4. Wait for model to load, then start chatting

### Image Generation

1. Download Stable Diffusion 1.5 model (ONNX format)
   - Censored or uncensored variants available
   - Model size: ~2GB

2. Navigate to image generation settings
3. Import your SD model
4. Enter prompt and generate (30-90 seconds depending on device)

### Model Sources

- [Hugging Face GGUF Models](https://huggingface.co/models?other=gguf)
- [Hugging Face Stable Diffusion](https://huggingface.co/models?other=stable-diffusion)
- Community-shared models via Discord

---

## Technical Details

### Architecture

**Language:** Kotlin + C++ (JNI bindings)  
**UI Framework:** Jetpack Compose  
**Text Inference:** llama.cpp (GGUF support)  
**Image Inference:** Stable Diffusion 1.5 C++ implementation  
**Storage:** Room (SQLite) + AES-256-GCM encryption  
**Async:** Kotlin Coroutines + Flow  

### Memory Management

- Efficient context caching for faster inference
- Memory-mapped model loading
- Automatic RAM optimization based on device capabilities
- Background processing with WorkManager

### Storage System

- Write-Ahead Logging (WAL) for crash recovery
- LZ4 compression for efficient storage
- Content deduplication via SHA-256 hashing
- Encrypted block storage with hardware-backed keys

---

## System Performance

**Text Generation (7B GGUF Q4):**
- 6GB RAM: 2-4 tokens/sec
- 8GB RAM: 4-8 tokens/sec
- 12GB RAM: 8-15 tokens/sec

**Image Generation (SD 1.5):**
- Mid-range (SD 8 Gen 1): 60-90 seconds
- Flagship (SD 8 Gen 3): 30-50 seconds

Performance varies based on model size, quantization, and device hardware.

---

## Comparison

| Feature | ToolNeuron | Cloud AI Apps | Other Local AI |
|---------|------------|---------------|----------------|
| **Text Generation** | Any GGUF model | Cloud only | Limited models |
| **Image Generation** | SD 1.5 offline | Cloud only | None |
| **Privacy** | Complete offline | Server logging | Varies |
| **Cost** | Free | $20+/month | Free/Paid |
| **Internet Required** | No | Yes | Varies |
| **Data Encryption** | AES-256-GCM | N/A | Varies |
| **Open Source** | Apache 2.0 | Proprietary | Varies |

---

## Roadmap

### Version 1.0 (Current)
- Text generation with GGUF models
- Image generation with SD 1.5
- Encrypted local storage
- Custom model loading
- Conversation history

### Version 1.1 (Q1 2026)
- Text-to-Speech (TTS) integration
- Speech-to-Text (STT) support
- Model quantization tools
- Performance optimizations

### Version 1.2 (Q2 2026)
- RAG (Retrieval Augmented Generation) system
- Plugin architecture
- Multi-modal support (vision models)
- Desktop companion app

### Version 2.0 (Q3 2026)
- ONNX runtime support
- Multiple inference backends
- Advanced memory management
- Collaborative features

---

## Building from Source

```bash
# Clone repository
git clone https://github.com/Siddhesh2377/ToolNeuron.git
cd ToolNeuron

# Open in Android Studio (latest stable)
# Sync Gradle dependencies

# Build APK
./gradlew assembleRelease

# Install on device
./gradlew installRelease
```

**Requirements:**
- Android Studio Ladybug or newer
- JDK 17
- Android SDK 34
- NDK 26.x (for C++ components)

---

## Contributing

Contributions are welcome. Please follow these guidelines:

1. Fork the repository
2. Create a feature branch (`git checkout -b feature/improvement`)
3. Make your changes with clear commit messages
4. Test on real Android devices when possible
5. Submit a Pull Request

**Priority Areas:**
- Bug fixes and stability improvements
- Performance optimizations
- Documentation and examples
- Device compatibility testing
- UI/UX enhancements

---

## Privacy & Security

### Data Collection
ToolNeuron collects **zero data**. All processing occurs entirely on your device.

### What Stays Local
- All conversations and chat history
- Generated images and content
- Model configurations
- User preferences

### Encryption
- AES-256-GCM for all stored data
- Hardware-backed key storage (Android KeyStore)
- Encrypted database for conversations

### Verification
ToolNeuron is fully open source. Audit the code yourself or review community security assessments.

---

## License

Licensed under the Apache License 2.0. See [LICENSE](LICENSE) for details.

**Commercial Use Permitted:** Use ToolNeuron in commercial products without restrictions.

---

## Acknowledgments

Built with these open-source projects:

- [llama.cpp](https://github.com/ggerganov/llama.cpp) by Georgi Gerganov - Efficient LLM inference
- [local-dream](https://github.com/xororz/local-dream) - Text-to-image generation
- [Jetpack Compose](https://developer.android.com/jetpack/compose) - Modern Android UI

---

## Support

- **Discord:** [Join Community](https://discord.gg/mVPwHDhrAP)
- **Issues:** [Report Bug](https://github.com/Siddhesh2377/ToolNeuron/issues)
- **Email:** siddheshsonar2377@gmail.com

---

## FAQ

**Q: Does this work offline?**  
A: Yes. All AI processing happens on your device with zero internet requirement.

**Q: How much storage do I need?**  
A: Depends on models you use. Budget 5-8GB for typical setup (one 7B GGUF + SD 1.5).

**Q: Will this drain my battery?**  
A: Local AI is power-intensive. Keep device charged during long sessions.

**Q: Is my data private?**  
A: Yes. Nothing leaves your device. Verify in the open-source code.

**Q: Can I use my own models?**  
A: Yes. Any GGUF format model or SD 1.5 checkpoint works.

**Q: Why is image generation slow?**  
A: SD 1.5 is computationally expensive. 30-90 seconds is normal on mobile hardware.

---

<div align="center">

**Built by [Siddhesh Sonar](https://github.com/Siddhesh2377)**

Privacy-first AI for everyone

[⭐ Star this repository](https://github.com/Siddhesh2377/ToolNeuron) if you find it useful

</div>
