# ToolNeuron

### Privacy-First AI Assistant for Android - Complete On-Device Intelligence

[![Platform](https://img.shields.io/badge/Platform-Android_12%2B-3DDC84?logo=android&logoColor=white)](https://github.com/Siddhesh2377/ToolNeuron)
[![License](https://img.shields.io/badge/License-Apache_2.0-green.svg)](LICENSE)
[![Release](https://img.shields.io/badge/Release-2.1.0-blue)](https://github.com/Siddhesh2377/ToolNeuron/releases)
[![Discord](https://img.shields.io/badge/Discord-Join-5865F2?logo=discord&logoColor=white)](https://discord.gg/mVPwHDhrAP)

<p align="left">
  <a href="https://play.google.com/store/apps/details?id=com.dark.tool_neuron">
    <img src="https://play.google.com/intl/en_us/badges/static/images/badges/en_badge_web_generic.png"
         alt="Get it on Google Play"
         height="80"/>
  </a>
</p>

ToolNeuron is the most advanced offline-first AI assistant for Android, featuring complete on-device processing with enterprise-grade encryption, intelligent document understanding through RAG (Retrieval-Augmented Generation), text-to-speech, an extensible plugin system, AI character cards (TavernAI v2 compatible), persistent AI memory, and sophisticated memory management. Your data never leaves your device. No cloud dependencies. No subscriptions. True digital sovereignty.

[Download APK](https://github.com/Siddhesh2377/ToolNeuron/releases) ·
[Join Discord](https://discord.gg/mVPwHDhrAP) ·
[Report Issue](https://github.com/Siddhesh2377/ToolNeuron/issues)

---

## Why ToolNeuron?

**Complete Privacy**: Hardware-backed AES-256-GCM encryption. Zero telemetry. All processing happens on your device.

**Sophisticated RAG System**: Inject and query documents (PDF, Word, Excel, EPUB) with semantic search and encrypted knowledge bases.

**Remote API Access**: New in v2.1.0! Access ToolNeuron from your local network via an OpenAI-compatible API. Use it with LangChain, AutoGPT, or custom scripts.

**Secure Memory Vault**: Crash-recoverable encrypted storage with Write-Ahead Logging, LZ4 compression, and content deduplication.

**Offline-First**: Works completely offline after model downloads. No internet required for AI inference.

**On-Device TTS**: Text-to-speech with 10 voices, 5 languages, adjustable speed and quality — all processed locally.

**AI Character Cards**: Full TavernAI v2 compatible persona system — import/export character cards, avatar images, template variables (`{{char}}`/`{{user}}`), and post-history reinforcement for consistent roleplay.

**Persistent AI Memory**: Mem0-inspired memory system that learns about you across conversations — automatic fact extraction, deduplication, forgetting curve, and persona-aware filtering.

**Plugin System**: 7 built-in plugins — web search, file manager, calculator, clipboard, date/time, device info, and developer utilities — extensible with custom plugins.

**Advanced Features**: Function calling, multi-modal generation, customizable inference parameters, and concurrent model downloads.

---

## Table of Contents

- [Features Overview](#features-overview)
- [Remote API Access](#remote-api-access)
- [Text Generation](#text-generation)
- [Image Generation](#image-generation)
- [Text-to-Speech (TTS)](#text-to-speech-tts)
- [AI Personas & Character Cards](#ai-personas--character-cards)
- [AI Memory System](#ai-memory-system)
- [Plugin System](#plugin-system)
- [RAG System (Document Intelligence)](#rag-system-document-intelligence)
- [Memory Vault (Secure Storage)](#memory-vault-secure-storage)
- [Document Processing](#document-processing)
- [Model Management](#model-management)
- [Privacy & Security](#privacy--security)
- [Installation](#installation)
- [Quick Start](#quick-start)
- [Technical Details](#technical-details)
- [Use Cases](#use-cases)
- [Building from Source](#building-from-source)
- [Roadmap](#roadmap)
- [FAQ](#faq)

---

## Features Overview

### Core Capabilities

| Feature | Description |
|---------|-------------|
| **Remote API** | **v2.1.0**: OpenAI-compatible API for remote access over local network with NSD discovery |
| **Text Generation** | Run any GGUF model locally (Llama, Mistral, Gemma, Phi, Qwen, etc.) with streaming output |
| **Image Generation** | Stable Diffusion 1.5 with censored & uncensored variants, inpainting support |
| **Text-to-Speech** | On-device TTS with 10 voices, 5 languages, adjustable speed and denoising steps |
| **AI Character Cards** | TavernAI v2 compatible personas with import/export, avatar images, template vars, post-history reinforcement |
| **AI Memory** | Persistent memory across conversations — automatic fact extraction, deduplication, forgetting curve |
| **Plugin System** | 7 plugins (web search, file manager, calculator, clipboard, date/time, device info, dev utils) with tool calling |
| **RAG System** | Document injection with hybrid search (BM25 + vector + RRF + MMR), encrypted knowledge bases |
| **Memory Vault** | Hardware-backed AES-256-GCM encryption, WAL crash recovery, LZ4 compression |
| **Document Processing** | Parse PDF, Word (.doc/.docx), Excel (.xls/.xlsx), EPUB, and plain text |
| **Model Store** | Browse and download models from HuggingFace — General, Coding, Medical, Uncensored categories |
| **Function Calling** | Grammar-constrained tool calling with multi-turn agent execution (up to 5 rounds) |
| **Secure Storage** | Content deduplication, three-tier caching, automatic defragmentation |
| **No Permissions** | Load models without storage permissions using Android SAF |

---

## Remote API Access

New in version 2.1.0, ToolNeuron can now be used as an AI server on your local network. The API is fully **OpenAI-compatible**, enabling seamless integration with existing tools like LangChain, AutoGPT, and custom applications.

### Quick Start

1. Enable Remote API in ToolNeuron Settings
2. Get your device IP from Settings > About > IP address
3. Connect from any machine on your WiFi network:

```bash
curl http://<device-ip>:11434/v1/chat/completions \
  -H "Content-Type: application/json" \
  -d '{
    "model": "llama-2-7b",
    "messages": [{"role": "user", "content": "Hello!"}]
  }'
```

### Key Features

- **OpenAI Compatibility**: Use standard `/v1/chat/completions` and `/v1/images/generations` endpoints
- **Streaming Support**: Real-time token streaming via Server-Sent Events (SSE)
- **Network Discovery**: Auto-discoverable as `ToolNeuron-API.local` (mDNS)
- **Default Port**: 11434 (compatible with Ollama tools)
- **Multi-turn Conversations**: Full conversation history with role-based messages
- **Adjustable Parameters**: Temperature, max tokens, top-p sampling, penalties, and more

### Available Endpoints

| Endpoint | Method | Description |
|----------|--------|-------------|
| `/` | GET | Health check |
| `/v1/models` | GET | List available GGUF text models |
| `/v1/image-models` | GET | List available Stable Diffusion image models |
| `/v1/chat/completions` | POST | Text generation (streaming or non-streaming) |
| `/v1/images/generations` | POST | Generate images with optional model selection |

### Documentation

For comprehensive API documentation, examples, integration guides, and troubleshooting:

📖 **[Full Remote API Documentation](docs/REMOTE_API.md)**

Includes:
- Detailed API examples for all endpoints
- Image model listing and selection
- Request/response formats and parameters
- Python, Node.js, and LangChain integration examples
- Network configuration and troubleshooting
- Performance optimization tips
- Security best practices

### OpenWebUI Integration

🌐 **[OpenWebUI Setup Guide](docs/OPENWEBUI_SETUP.md)**

Complete guide for connecting ToolNeuron to OpenWebUI:
- Step-by-step setup instructions
- Troubleshooting common issues
- Advanced configuration options
- Performance optimization
- Security best practices

---

## AI Personas & Character Cards

Full character card system compatible with **TavernAI v2 / SillyTavern** format. Create, edit, import, and export AI personas with rich personality definitions.
...
