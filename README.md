# ToolNeuron

### Privacy-First AI Assistant for Android - Complete On-Device Intelligence

[![Platform](https://img.shields.io/badge/Platform-Android_12%2B-3DDC84?logo=android&logoColor=white)](https://github.com/Siddhesh2377/ToolNeuron)
[![License](https://img.shields.io/badge/License-Apache_2.0-green.svg)](LICENSE)
[![Release](https://img.shields.io/badge/Release-2.0.0-blue)](https://github.com/Siddhesh2377/ToolNeuron/releases)
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

## AI Personas & Character Cards

Full character card system compatible with **TavernAI v2 / SillyTavern** format. Create, edit, import, and export AI personas with rich personality definitions.

### Character Card Fields

| Field | Description |
|-------|-------------|
| **Name** | Character's display name |
| **Avatar** | Image or emoji avatar (stored locally) |
| **Description** | Character background, lore, and traits |
| **Personality** | Core personality traits (PList or prose) |
| **Scenario** | Current scene or context |
| **First Message** | Opening greeting |
| **Alternate Greetings** | Multiple greeting variations |
| **Example Messages** | Dialogue samples for style calibration |
| **Tags** | Categorization tags |
| **Creator Notes** | Author notes (not sent to model) |
| **System Prompt** | Legacy raw system prompt (fallback) |

### Key Features
- **Template Variables**: `{{char}}` resolves to persona name, `{{user}}` resolves to "User" — applied throughout system prompt and messages
- **Identity Framing**: Automatic "You are {{char}}" directive prevents the model from confusing character/user identity
- **Post-History Reinforcement**: Compressed personality reminder injected after chat history (most influential position for small models, based on SillyTavern/MiniMax research)
- **TavernAI v2 Import/Export**: Import `.json` character cards from SillyTavern, Chub.ai, or any TavernAI v2 source. Export your personas to share
- **Avatar Images**: Pick images from gallery, stored locally in app files (no external URI permissions needed)
- **Default Personas**: Ships with Assistant, Luna, CodeBuddy, and Sage

### How It Works
System prompt is assembled from structured fields: description + personality + scenario + example messages. If no structured fields exist, falls back to raw `systemPrompt`. The post-history instruction is injected right before the model generates, providing maximum influence on small on-device models.

---

## AI Memory System

Persistent memory that lets the AI **remember facts about you** across conversations. Inspired by [Mem0](https://github.com/mem0ai/mem0).

### How It Works
1. After each conversation turn, the **MemoryExtractor** uses the loaded LLM to extract user facts
2. Facts are deduplicated using **Jaccard similarity** (threshold 0.7) with AUDN cycle (Add/Update/Delete/Noop)
3. Stored memories are injected into the system prompt as context for future conversations
4. **Persona-aware**: When a character persona is active, the extractor filters out roleplay traits — only real user facts are saved

### Memory Features
- **Automatic Extraction**: Facts extracted from conversations without user intervention
- **Categories**: Personal, Preference, Work, Interest, General
- **Forgetting Curve**: Memories decay over time based on access frequency and recency
- **Memory Browser**: View, search, filter, edit, and delete memories from the AI Memory screen
- **Manual Memories**: Add custom facts the AI should remember
- **Source Tracking**: Each memory links back to the conversation it came from

---

## Text Generation

### Model Support
- **Format**: Any GGUF model (Llama 3, Mistral, Gemma, Phi, Qwen, etc.)
- **Size Range**: 500MB (1B models) to 20GB+ (70B models)
- **Quantization**: All GGUF quantizations supported (Q2_K, Q4_K_M, Q5_K_S, Q6_K, Q8_0, F16, etc.)
- **Model Categories**: General, Medical, Research, Coding, Uncensored, Business, Cybersecurity

### Performance
| Device Tier | RAM | Model Size | Speed |
|-------------|-----|------------|-------|
| Budget | 6GB | 1-3B Q4 | 2-4 tokens/sec |
| Mid-Range | 8GB | 7-8B Q4 | 4-8 tokens/sec |
| Flagship | 12GB+ | 8B Q6 | 8-15 tokens/sec |

*Reports from users: 7-second response times for 8B Q6 models on flagship devices*

### Advanced Features
- **Streaming Output**: Real-time token-by-token generation with `Flow<GenerationEvent>`
- **Custom Parameters**: Temperature, top-k, top-p, min-p, repeat penalty, context length
- **System Prompts**: Configure per-model system prompts
- **Function Calling**: Tool/function calling with grammar-based JSON schema enforcement
- **Model Configuration**: Save and manage configurations per model
- **Device Optimization**: Auto-detect device tier and recommend optimal parameters
- **Memory Management**: Memory-mapped model loading, automatic RAM optimization

### Supported Callbacks
```kotlin
onToken(token: String)           // Real-time token streaming
onToolCall(toolCall: ToolCall)   // Function call detection
onDone(metrics: Metrics)         // Generation completion
onError(error: Throwable)        // Error handling
onMetrics(metrics: Metrics)      // Performance metrics
```

---

## Image Generation

### Stable Diffusion 1.5
- **Models**: Censored and uncensored variants
- **Engine**: LocalDream integration with NPU/CPU support
- **Generation Time**: 30-90 seconds depending on device hardware

### Capabilities
- **Text-to-Image**: Generate images from text prompts
- **Inpainting**: Edit specific regions with mask support
- **Custom Parameters**:
  - Resolution: 512x512, 768x768, 1024x1024
  - Steps: 10-50 inference steps
  - CFG Scale: Prompt adherence control
  - Seed: Reproducible generation
  - Negative Prompts: Exclude unwanted elements
  - Denoise Strength: Inpainting intensity
  - Schedulers: DPM and other scheduler support

### Advanced Features
- **Intermediate Results**: View generation progress with intermediate images
- **Safety Checker**: Optional NSFW content filtering
- **Pony Model Support**: Specialized anime/cartoon models
- **Backend Control**: Start, stop, restart generation backend
- **State Monitoring**: Real-time backend and generation state tracking

---

## Text-to-Speech (TTS)

On-device speech synthesis powered by Supertonic TTS. All processing happens locally — no cloud APIs, no data leaves your device.

### Voice Options
- **10 Voices**: 5 female (F1–F5) and 5 male (M1–M5)
- **5 Languages**: English, Korean, Spanish, Portuguese, French
- **Speed Control**: 0.5x to 2.0x playback speed
- **Denoising Steps**: 1–8 steps (higher = better quality, slower synthesis)

### Features
- **Auto-speak**: Automatically read assistant responses aloud after generation
- **On-demand Loading**: TTS model loads automatically on first use if not preloaded
- **Load on App Start**: Optionally preload the TTS model at launch for instant speech
- **NNAPI Acceleration**: Hardware acceleration on supported devices
- **Playback Controls**: Play, pause, resume, stop with real-time synthesis progress
- **Per-message TTS**: Tap the speak button on any assistant message to hear it

### Settings
All TTS preferences are persisted and configurable from the Settings screen:
- Voice, language, speed, denoising steps
- Auto-speak toggle
- NNAPI hardware acceleration toggle
- Load on app start toggle

---

## Plugin System

Extensible plugin architecture that integrates with LLM tool calling. Plugins execute locally and render custom UI for results.

### Built-in Plugins (7)

#### Web Search
- **Engine**: DuckDuckGo search with configurable result count (5–10)
- **Web Scraping**: CSS selector-based content extraction from search results
- **Safe Search**: Optional safe search filtering
- **Custom UI**: Rich display of search results and scraped content

#### File Manager
- **File Operations**: List files, read text files, create files
- **Document Reading**: PDF and DOCX parsing via DocumentParser
- **Search**: Find files by name across app directories

#### Calculator
- **Expressions**: Supports +, -, *, /, ^, %, parentheses
- **Functions**: sqrt, sin, cos, tan, asin, acos, atan, log, log10, ln, abs, ceil, floor, round
- **Constants**: pi, e
- **Unit Conversion**: Length (m, km, mi, ft, in...), weight (kg, lb, oz...), time (s, min, h, day), data (b, kb, mb, gb, tb), temperature (C, F, K)

#### Clipboard
- **Read**: Access current clipboard content
- **Write**: Copy text to system clipboard

#### Date & Time
- **Current Time**: Get date/time with timezone support
- **Arithmetic**: Add/subtract days, hours, minutes from dates
- **Timezone Conversion**: Convert between any timezones

#### Device Info
- **System**: OS version, device model, CPU architecture
- **Resources**: RAM usage, battery level, storage available
- **Network**: Connectivity status

#### Dev Utils
- **Text Transforms**: uppercase, lowercase, reverse, title_case, snake_case, camel_case, trim
- **Hashing**: MD5, SHA-1, SHA-256, SHA-512
- **UUID Generation**: Bulk generation up to 10 at once
- **Text Statistics**: Character, word, line, and sentence counts
- **JSON**: Formatting and validation
- **Base64**: Encoding and decoding

### Plugin Architecture
- **Tool Calling Integration**: Plugins register as tools the LLM can invoke via grammar-based JSON schema enforcement (LAZY/STRICT modes)
- **Custom UI Rendering**: Each plugin provides Compose UI for displaying results
- **Enable/Disable**: Toggle individual plugins from the Settings screen
- **Execution Metrics**: Tracks execution time, success/failure per plugin call

---

## RAG System (Document Intelligence)

The RAG (Retrieval-Augmented Generation) system enables Tool-Neuron to inject external knowledge into conversations, allowing AI to answer questions based on your documents with semantic understanding.

### RAG Creation Methods

#### 1. From Text
Create knowledge bases from plain text input:
```
- Paste or type text content
- System chunks and embeds automatically
- Instant semantic search capability
```

#### 2. From Files
Parse and embed documents:
- **Supported Formats**: PDF, Word (.doc/.docx), Excel (.xls/.xlsx), EPUB, TXT
- **Multi-Sheet Excel**: Each sheet embedded separately with metadata
- **Table Extraction**: Word tables preserved with structure
- **Automatic Chunking**: Intelligent text segmentation
- **Metadata Tracking**: File name, MIME type, source tracking

#### 3. From Chat History
Convert conversations into queryable knowledge:
- Export chat history as RAG
- Enable AI to reference past conversations
- Preserve conversation context across sessions

#### 4. From Neuron Packets
Import pre-built RAG files:
- `.neuron` packet format
- Encrypted RAG sharing
- Version control and metadata

#### 5. Secure RAG Creation
Enterprise-grade encrypted knowledge bases:
- **Admin Password Protection**: Master password for RAG access
- **Read-Only Users**: Grant limited access without admin privileges
- **Hardware-Backed Encryption**: AES-256-GCM with Android KeyStore
- **User Management**: Add/remove read-only users
- **Access Control**: Fine-grained permission system

### RAG Features

**Query System**:
- **Semantic Search**: Embedding-based similarity search (cosine similarity)
- **Top-K Results**: Return most relevant chunks
- **Context Injection**: Automatically augment prompts with relevant knowledge
- **Multi-RAG Support**: Query across multiple loaded RAGs simultaneously

**RAG Management**:
- **Enable/Disable**: Control which RAGs are active for queries
- **Lazy Loading**: Load RAGs into memory on demand
- **Status Tracking**: INSTALLED, LOADED, LOADING, ERROR states
- **Metadata**: Domain, language, version, tags, embedding model info
- **Size Management**: Track RAG file size, compression ratio
- **Delete/Export**: Remove or share RAG files

**Loading Modes**:
- **Embedded**: RAG stored within app data (persistent)
- **Transient**: Temporary loading from external files

**NeuronGraph Integration**:
- Node-based knowledge representation
- Graph traversal for related concepts
- Serialization/deserialization support

### Embedding Engine
- **Model**: all-MiniLM-L6-v2-Q5_K_M (768-dimensional embeddings)
- **Auto-Download**: Fetches embedding model from HuggingFace on first use
- **Batch Processing**: Efficient batch embedding generation
- **Normalization**: Optional L2 normalization for cosine similarity

### RAG UI Features
- **RAG Overlay**: Transparent overlay shows retrieved context during chat
- **RAG Data Explorer**: Browse all chunks, edit metadata, view embeddings
- **RAG Statistics**: Size, chunk count, embedding coverage
- **Search & Filter**: Full-text search within RAGs
- **Category & Tag Management**: Organize RAG content

---

## Memory Vault (Secure Storage)

The Memory Vault is Tool-Neuron's sophisticated encrypted storage system, providing crash-recoverable, compressed, deduplicated storage with enterprise-grade security.

### Core Architecture

**Hardware-Backed Encryption**:
- **Algorithm**: AES-256-GCM with 96-bit IV
- **Key Storage**: Android KeyStore (hardware-backed on supported devices)
- **Key Migration**: Automatic re-encryption on key rotation
- **Auth-Tagged**: GCM mode provides authentication and integrity

**Write-Ahead Logging (WAL)**:
- **Crash Recovery**: Automatic recovery from crashes/power loss
- **Transaction Safety**: ACID-compliant operations
- **Checkpoint System**: Periodic index checkpointing
- **Rollback Support**: Restore from checkpoints on corruption

**LZ4 Compression**:
- **Fast Compression**: Real-time compression/decompression
- **Ratio Tracking**: Monitor compression efficiency
- **Block-Level**: Compress individual blocks for efficient I/O
- **Configurable**: Adjust compression level

**Content Deduplication**:
- **SHA-256 Hashing**: Identify duplicate content
- **Reference Counting**: Track shared content usage
- **Automatic Cleanup**: Remove unreferenced blocks
- **Storage Efficiency**: Reduce redundant encrypted data

### Data Types

#### Messages
- Full-text indexed conversation messages
- Tokenization for search
- Timestamp tracking
- Category and tag support

#### Files
- Binary file storage with MIME type tracking
- Image, document, and arbitrary file support
- Metadata preservation
- Content deduplication

#### Embeddings
- 768-dimensional vector storage
- Semantic search with cosine similarity
- Batch embedding support
- Normalization options

#### Custom Data
- JSON-serialized custom structures
- Schema-flexible storage
- Queryable metadata

### Caching System

**Three-Tier Architecture**:
1. **L1 Hot Cache**: In-memory cache for frequently accessed items (< 1MB)
2. **L2 Memory-Mapped**: Memory-mapped file access for warm data (< 5MB)
3. **L3 On-Demand**: Disk-based access for cold data

**Cache Metrics**:
- Hit/miss rates
- Eviction tracking
- Memory usage monitoring
- Performance optimization

### Storage Operations

**Search Capabilities**:
- **Full-Text Search**: Tokenized text search across messages
- **Semantic Search**: Embedding-based similarity search
- **Category Filter**: Filter by predefined categories
- **Tag Filter**: Multi-tag filtering support
- **Time Range**: Search within date/time ranges
- **Content Type Filter**: Filter by data type

**Maintenance**:
- **Defragmentation**: Reclaim wasted space from deleted items
- **Index Rebuilding**: Reconstruct search indices
- **Validation**: Integrity checking and corruption detection
- **Backup**: Export vault with compression
- **Restore**: Import from backup files

### Vault Statistics

Monitor storage health:
- **Total Items**: Count by type (messages, files, embeddings, custom)
- **Size Metrics**: Compressed vs uncompressed sizes
- **Compression Ratio**: Efficiency tracking
- **Wasted Space**: Identify fragmentation
- **Time Range**: Earliest and latest item timestamps
- **Content Type Breakdown**: Distribution across data types

### Vault UI Features
- **Vault Dashboard**: Overview of all vault contents
- **Statistics Screen**: Detailed metrics and graphs
- **Data Explorer**: Browse, search, filter all items
- **Metadata Editor**: Edit categories, tags, search text
- **User Management**: Manage vault access credentials (admin, read-only)
- **Logger Screen**: Debug logs with operation timing and encryption metrics

---

## Document Processing

Comprehensive document parsing with format detection and content extraction.

### Supported Formats

#### PDF
- **Engine**: PDFBox-Android
- **Capabilities**: Text extraction, metadata parsing
- **Streaming**: Efficient I/O for large files

#### EPUB
- **Engine**: EpubLib
- **Capabilities**: E-book text extraction, chapter navigation
- **HTML Cleanup**: Automatic tag removal for plain text

#### Microsoft Word
- **Formats**: .docx (Office Open XML), .doc (binary)
- **Engine**: Apache POI
- **Capabilities**:
  - Paragraph extraction
  - Table parsing with structure preservation
  - Formatting metadata

#### Microsoft Excel
- **Formats**: .xlsx (Office Open XML), .xls (binary)
- **Engine**: Apache POI
- **Capabilities**:
  - Multi-sheet support with sheet names
  - Cell type detection (string, numeric, boolean, formula)
  - Formula evaluation
  - Comprehensive cell formatting

#### Plain Text
- **Encodings**: UTF-8, UTF-16, ASCII
- **Line Ending Support**: Unix, Windows, Mac

### Processing Features
- **MIME Type Detection**: Automatic format detection with fallback to file extension
- **Error Handling**: Informative error messages on parse failures
- **Progress Tracking**: Real-time parsing progress for large documents
- **Metadata Extraction**: Title, author, creation date, modification date
- **Logging**: Comprehensive debug logging for troubleshooting

---

## Model Management

### Model Store

**In-App HuggingFace Integration**:
- Browse HuggingFace model repositories
- Search with filters (model type, size, tags)
- Add custom repositories by username/org
- View model metadata (size, quantization, tags, downloads)

**Download Management**:
- **Concurrent Downloads**: Download multiple models simultaneously
- **Progress Tracking**: Real-time progress notifications
- **WorkManager Integration**: Robust background task management
- **Resume Capability**: Resume interrupted downloads
- **Foreground Service**: Persistent downloads (Android 14+ compliant)

**Model Categories**:
- General: General-purpose conversational models
- Medical: Healthcare and medical domain models
- Research: Academic and research-focused models
- Coding: Programming and code generation models
- Uncensored: Unfiltered and uncensored models
- Business: Professional and business domain models
- Cybersecurity: Security and penetration testing models

### Model Configuration

**Per-Model Settings**:

*Loading Parameters*:
- Thread count (auto-detect or manual)
- Context size (512 to 32768+ tokens)
- Quantization options
- GPU layers (if supported)

*Inference Parameters*:
- Temperature (0.0 - 2.0)
- Top-k sampling
- Top-p (nucleus) sampling
- Min-p sampling
- Repeat penalty
- Frequency/presence penalty
- System prompt
- Seed (for reproducibility)

**Configuration Storage**:
- Database-backed persistence
- JSON serialization
- Import/export configurations
- Default configurations per model category

### Model Picker
- Grid/list view of installed models
- Search and filter
- Model details (size, format, loaded status)
- Quick load/unload
- Delete models

---

## Privacy & Security

### Data Collection
**ZERO DATA COLLECTION**. No telemetry, analytics, crash reporting, or tracking of any kind.

### What Stays Local
- All conversations and chat history
- Generated images and files
- Speech synthesis and TTS audio
- Plugin execution results
- Model configurations
- User preferences
- RAG knowledge bases
- Memory vault contents
- Document parsing results

### Encryption Details

**Storage Encryption**:
- **Algorithm**: AES-256-GCM
- **IV**: 96-bit unique per-block
- **Key Derivation**: Android KeyStore hardware-backed
- **Authentication**: GCM auth tag for integrity verification

**Vault Security**:
- Write-Ahead Logging for crash recovery
- Content deduplication prevents re-encryption overhead
- Secure key migration on rotation
- Automatic encryption of all stored data

**RAG Security**:
- Optional encryption for RAG packets
- Admin password protection
- Read-only user access control
- Hardware-backed key storage

### No Permissions Required
- **Storage Access Framework (SAF)**: Load models via file picker
- **Scoped Storage**: Modern Android storage compliance
- **No Broad Access**: App cannot access arbitrary files

### Verification
Fully open source. Audit the code or review community security assessments.

---

## Installation

### System Requirements

**Minimum (Text Only)**:
- Android 12+ (API 31)
- 6GB RAM
- 4GB free storage
- ARM64 or x86_64 processor

**Recommended (Text + Image + TTS + RAG)**:
- Android 13+
- 8GB RAM (12GB preferred)
- 10GB free storage
- Snapdragon 8 Gen 1 or equivalent
- Hardware-backed encryption support

### Download

**Google Play Store (Recommended)**:
[Get it on Play Store](https://play.google.com/store/apps/details?id=com.dark.tool_neuron)

**Direct APK**:
Download from [GitHub Releases](https://github.com/Siddhesh2377/ToolNeuron/releases)

---

## Quick Start

### 1. Get Models

#### Option A: In-App Model Store (Recommended)
1. Open ToolNeuron
2. Navigate to Model Store (drawer menu)
3. Add HuggingFace repository:
   - Example: `QuantFactory/Meta-Llama-3-8B-GGUF`
   - Example: `bartowski/Phi-3.5-mini-instruct-GGUF`
4. Browse models and tap to download
5. Return to home screen and select your model

#### Option B: Manual Download
1. Visit [Hugging Face GGUF Models](https://huggingface.co/models?other=gguf)
2. Download a model file (e.g., `Llama-3-8B-Q4_K_M.gguf`)
3. Open ToolNeuron
4. Use model picker to load from file
5. Grant file access via Android file picker

**Recommended Models**:
| Use Case | Model | Size | Description |
|----------|-------|------|-------------|
| Budget/Testing | TinyLlama-1.1B-Q4_K_M | 669MB | Fast, low resource |
| Balanced | Llama-3-8B-Q4_K_M | 4.5GB | Best quality/performance |
| Maximum Quality | Mistral-7B-Q6_K | 6GB | Highest quality 7B |
| Coding | DeepSeek-Coder-6.7B-Q4 | 4GB | Code generation |
| Medical | Bio-Medical-Llama-3-8B | 4.5GB | Healthcare domain |

### 2. Generate Text

1. Launch ToolNeuron
2. Select or import GGUF model (wait for loading progress)
3. Model loads automatically (status bar shows progress)
4. Start typing your prompt
5. AI streams response in real-time

**Pro Tips**:
- Adjust temperature in model config (0.7 = balanced, 0.3 = focused, 1.0 = creative)
- Increase context size for longer conversations (but uses more RAM)
- Use system prompts to set AI behavior

### 3. Generate Images

1. Download Stable Diffusion 1.5 model from HuggingFace:
   - Search for "stable-diffusion-v1-5"
   - Download `.safetensors` or `.ckpt` file
2. Import into ToolNeuron via model picker
3. Switch to Image generation mode (toggle in chat screen)
4. Enter your prompt (e.g., "a serene mountain landscape at sunset, 4k, photorealistic")
5. Optional: Add negative prompt (e.g., "blurry, low quality, distorted")
6. Tap generate and wait 30-90 seconds
7. Image appears in chat with save option

### 4. Create RAG Knowledge Base

#### From Documents
1. Navigate to RAG menu (drawer)
2. Tap "Create New RAG"
3. Select "From File"
4. Choose document type (PDF, Word, Excel, EPUB, TXT)
5. Pick file via file picker
6. Set RAG name and metadata (optional: enable encryption)
7. Wait for document parsing and embedding
8. RAG appears in RAG list

#### From Text
1. Tap "Create New RAG" → "From Text"
2. Paste or type your content
3. Set metadata (name, category, tags)
4. Tap "Create"
5. System chunks and embeds automatically

#### Using RAGs in Chat
1. Enable desired RAGs in RAG management screen
2. Return to chat
3. RAG overlay button appears (tap to view retrieved context)
4. AI automatically uses relevant RAG content in responses
5. Retrieved chunks show in overlay for transparency

---

## Technical Details

### Architecture

**Core Stack**:
- **Language**: Kotlin (Android), C++ (inference engines via JNI)
- **UI Framework**: Jetpack Compose (declarative UI)
- **Text Inference**: llama.cpp (GGUF engine)
- **Image Inference**: LocalDream (Stable Diffusion 1.5)
- **Database**: Room (SQLite) with AES-256-GCM encryption
- **Async**: Kotlin Coroutines + Flow
- **Dependency Injection**: Dagger Hilt
- **Navigation**: Jetpack Navigation Compose
- **Serialization**: Kotlinx Serialization + Gson

**Custom Modules**:
- `memory-vault`: Encrypted storage with WAL and compression
- `neuron-packet`: RAG packet format with encryption and access control
- `ai_gguf-release.aar`: Native GGUF inference library
- `ai_sd-release.aar`: Native Stable Diffusion library
- `ai_supertonic_tts`: On-device TTS with ONNX Runtime

### Inference Engines

**GGUF Engine** (`GGUFEngine.kt`):
- Native JNI bindings to llama.cpp
- Loading: File path or file descriptor (SAF/content:// URIs)
- Streaming: Token-by-token generation with `Flow<GenerationEvent>`
- Callbacks: `onToken`, `onToolCall`, `onDone`, `onError`, `onMetrics`
- Device Detection: Auto-detect device tier (LOW_END, MID_RANGE, HIGH_END)
- Optimization: Automatic thread/context recommendations

**Diffusion Engine** (`DiffusionEngine.kt`):
- Integration with StableDiffusionManager (LocalDream)
- NPU/CPU backend support
- Text embedding size configuration
- Pony model support
- Intermediate result streaming
- Safety checker toggle

**Embedding Engine** (`EmbeddingEngine.kt`):
- Model: all-MiniLM-L6-v2-Q5_K_M
- Dimensions: 768
- Operations: Single/batch embedding, normalization
- Auto-download on first use

**TTS Engine** (`TTSManager.kt`):
- Supertonic TTS with ONNX Runtime Android
- 10 voices (F1–F5, M1–M5), 5 languages
- Configurable denoising steps (1–8) and speed (0.5x–2.0x)
- Optional NNAPI hardware acceleration
- StateFlow-based reactive state management

**Plugin Engine** (`PluginManager.kt`):
- Plugin registration with tool schemas for LLM integration
- Grammar modes: LAZY (flexible) and STRICT (enforced JSON schema)
- Execution metrics tracking (timing, success/failure)
- Compose UI rendering per plugin result
- Enable/disable per plugin at runtime

### Storage System

**Memory Vault**:
- Block-based storage with headers
- Three-tier caching (L1 hot, L2 memory-mapped, L3 on-demand)
- Write-Ahead Logging for crash recovery
- LZ4 compression with ratio tracking
- SHA-256 content deduplication
- Full-text and semantic search indices

**Database Schema** (Room v6, 5 entities):
- Models table (GGUF/SD metadata)
- ModelConfig table (loading + inference params)
- InstalledRAGs table (RAG metadata with status)
- Personas table (character cards with TavernAI v2 fields)
- AiMemories table (extracted user facts with categories, embeddings, forgetting curve)
- DataStore (preferences — settings, active persona, TTS config)

### Performance Benchmarks

**Text Generation** (8B Q4_K_M on flagship device):
- Model Load Time: 5-15 seconds
- First Token: 1-3 seconds
- Generation Speed: 8-15 tokens/sec
- Context Processing: 500+ tokens/sec

**Image Generation** (SD 1.5):
- Mid-Range (SD 8 Gen 1): 60-90 seconds
- Flagship (SD 8 Gen 3): 30-50 seconds
- Resolution: 512x512 (fastest), 1024x1024 (slower)

**RAG Query**:
- Single RAG (1000 chunks): < 100ms
- Multiple RAGs (5000+ chunks): 100-500ms
- Embedding generation: 50-200ms per chunk

**Memory Usage**:
- Idle: 200-500MB
- 8B Model Loaded: 5-6GB
- With RAGs: +100-500MB
- Vault Cache: 3-5MB

---

## Use Cases

### Privacy-Critical Applications
- **Medical Professionals**: HIPAA-compliant patient data handling
- **Legal Professionals**: Confidential document analysis
- **Journalists**: Protecting source anonymity
- **Therapists**: Private session notes and analysis
- **Researchers**: Sensitive data processing
- **Anyone Valuing Digital Sovereignty**

### Offline Scenarios
- Air travel (no WiFi required)
- Remote locations (rural, wilderness)
- Areas with unreliable internet
- Avoiding mobile data costs
- Military/government secure environments

### Document Intelligence
- **Knowledge Base Creation**: Convert company docs, manuals, research papers into queryable RAGs
- **Study Aid**: Embed textbooks and lecture notes for AI-assisted learning
- **Research**: Query across multiple PDFs and papers simultaneously
- **Legal Document Review**: Search case law and contracts with semantic understanding
- **Medical Reference**: Embed medical literature for clinical decision support

### Creative & Development
- Writing and brainstorming assistance
- Code generation and debugging
- Image generation for content creation
- Learning and research
- Creative storytelling with AI

---

## Building from Source

### Prerequisites
- Android Studio Ladybug (2024.2.1) or newer
- JDK 17
- Android SDK 36+
- Android NDK 26.x
- Git

### Steps

```bash
# Clone repository
git clone https://github.com/Siddhesh2377/ToolNeuron.git
cd ToolNeuron

# Open in Android Studio
# File → Open → Select ToolNeuron folder

# Sync Gradle dependencies (Android Studio will prompt)

# Create local.properties (optional, for signing)
# Add: ALIAS="your_keystore_alias"

# Build release APK
./gradlew assembleRelease

# Install on connected device
./gradlew installRelease

# Or build debug version
./gradlew assembleDebug
./gradlew installDebug
```

### Build Output
- **Release APK**: `app/build/outputs/apk/release/app-release.apk`
- **Debug APK**: `app/build/outputs/apk/debug/app-debug.apk`

### Troubleshooting
- **NDK Issues**: Ensure NDK 26.x is installed via SDK Manager
- **JNI Build Failures**: Check `local.properties` has correct NDK path
- **Memory Issues**: Increase Gradle heap size in `gradle.properties`
  ```properties
  org.gradle.jvmargs=-Xmx4096m
  ```

---

## Roadmap

### Version 2.0.0 (Current - February 2026)

**AI Personas & Character Cards**:
- ✅ TavernAI v2 compatible character card system
- ✅ Full character editor (description, personality, scenario, example messages, alternate greetings, tags)
- ✅ Avatar image support (local storage, Coil 3)
- ✅ Template variables (`{{char}}`, `{{user}}`)
- ✅ Identity framing and post-history reinforcement for small models
- ✅ TavernAI v2 JSON import/export
- ✅ Default personas (Assistant, Luna, CodeBuddy, Sage)

**AI Memory System**:
- ✅ Persistent cross-conversation memory (Mem0-inspired)
- ✅ Automatic fact extraction via LLM
- ✅ AUDN deduplication (Jaccard similarity)
- ✅ Persona-aware extraction (filters out roleplay traits)
- ✅ Memory categories (Personal, Preference, Work, Interest, General)
- ✅ Forgetting curve with access-based reinforcement
- ✅ Memory browser screen (search, filter, edit, delete)

**Enhanced Chat Experience**:
- ✅ Thinking mode toggle for supported models
- ✅ Regenerate last message
- ✅ Code syntax highlighting (toggleable)
- ✅ Improved markdown rendering with LazyList integration
- ✅ Character card quick-access in bottom bar

**RAG Pipeline Improvements**:
- ✅ Hybrid retrieval: FTS4 BM25 + vector search + RRF + MMR
- ✅ Confidence gating for low-quality results
- ✅ RAG enable/disable toggle per chat session
- ✅ Improved RAG management UX

**Model Store Updates**:
- ✅ Uncensored model category with curated repos
- ✅ LiquidAI LFM2-350M added
- ✅ Qwen3 8B added

**Stability & Fixes**:
- ✅ UTF-8 stream decoding fix (smart quotes, accented characters)
- ✅ Surrogate pair handling for emoji above U+FFFF
- ✅ Database migration v4 → v5 → v6
- ✅ Improved UI across all screens (Model Store, RAG Creation, Settings, Home)

### Previous Releases

**Version 1.2.0 (January 2026)**:
- Text generation with any GGUF model
- Image generation with SD 1.5
- HuggingFace repository integration
- Encrypted Memory Vault with WAL
- RAG system with document injection
- Secure RAG creation with encryption
- Document processing (PDF, Word, Excel, EPUB)
- Model configuration editor
- Concurrent model downloads
- Function calling support
- Inpainting support
- Text-to-Speech (10 voices, 5 languages, NNAPI, auto-speak)
- Plugin system with UI (web search, calculator, dev utils)
- Settings screen with persistent preferences

### Upcoming

**Version 2.1 (Q2 2026)**:
- 🚧 Speech-to-Text (STT) support
- 📋 Multi-modal support (vision models like LLaVA, BakLLaVA)
- 📋 Code execution plugin with sandboxing
- 📋 Advanced memory clustering and insights
- 📋 Conversation summarization

**Version 2.2 (Q3 2026)**:
- Additional model formats (ONNX, TFLite, CoreML)
- Desktop companion app (Windows, macOS, Linux)
- Cloud sync with end-to-end encryption (optional)
- Plugin marketplace
- Advanced RAG features (graph-based reasoning)

---

## Comparison

| Feature | ToolNeuron | Cloud AI Apps | Other Local AI Apps |
|---------|------------|---------------|---------------------|
| **Text Generation** | Any GGUF model | Cloud only | Limited models |
| **Image Generation** | SD 1.5 offline | Cloud only | Rare |
| **Text-to-Speech** | On-device, 10 voices | Cloud-based | Rare |
| **Character Cards** | TavernAI v2 compatible | N/A | SillyTavern only |
| **AI Memory** | Persistent cross-session | Some cloud apps | None |
| **Plugin System** | 7 built-in plugins | Cloud-based | None |
| **RAG System** | Hybrid BM25+Vector+RRF | Cloud-based | Basic or none |
| **Document Processing** | PDF/Word/Excel/EPUB | Cloud upload | Limited |
| **Privacy** | Complete offline | Server logging | Varies |
| **Encryption** | AES-256-GCM + WAL | N/A | Rare |
| **Cost** | Free (one-time) | $20-50+/month | Varies |
| **Internet Required** | No (after models) | Yes | Varies |
| **Open Source** | Apache 2.0 | Proprietary | Varies |
| **Storage Permissions** | Not needed (SAF) | N/A | Usually needed |
| **Function Calling** | Grammar-constrained multi-turn | Yes | Rare |
| **Model Store** | In-app HF browser | N/A | Manual download |

---

## Community Testimonials

> "The only LLM frontend capable of running 8B Q6 models on my hardware with lightspeed loading. I'm in military healthcare and privacy is critical. ToolNeuron is the only app that meets my requirements."
> — Senior Healthcare Professional, Netherlands

> "I use ToolNeuron for legal document analysis. The RAG system with encrypted storage gives me confidence that client data stays confidential. No other app comes close."
> — Attorney, United States

> "As a journalist, I can't risk my sources being exposed through cloud AI services. ToolNeuron's offline-first approach is exactly what I needed."
> — Investigative Journalist, Germany

---

## FAQ

### General

**Q: Does this really work completely offline?**
A: Yes. After downloading models and the embedding model, all AI processing (text generation, image generation, RAG queries, document parsing) happens entirely on your device with zero internet dependency.

**Q: How much storage do I need?**
A: Minimum 4GB for a single 7B model. Recommended 10GB for multiple models, SD 1.5, and RAGs. Large setups with many models can use 20GB+.

**Q: Will this drain my battery?**
A: Local AI is computationally intensive. During active generation, battery drain is significant. Keep your device charged during extended use. Idle usage is minimal.

**Q: Is my data actually private?**
A: Yes. Nothing leaves your device. All processing is local. The code is open source - you can verify yourself or review community audits.

**Q: Can I use custom models?**
A: Yes. Any GGUF text model works. For image generation, Stable Diffusion 1.5 checkpoints are supported (.safetensors or .ckpt).

### Technical

**Q: Why don't you need storage permissions?**
A: Android's Storage Access Framework (SAF) allows file picker access without broad storage permissions. Users explicitly select files, granting app access only to chosen files.

**Q: Why is image generation slow?**
A: Stable Diffusion 1.5 requires 20-50 inference steps, each computationally expensive. Mobile hardware is slower than desktop GPUs. 30-90 seconds is normal and optimized.

**Q: Can I run 13B or 70B models?**
A: Depends on device RAM. 13B Q4 needs ~10GB RAM (requires 12GB device). 70B models are impractical on current mobile hardware (need 40GB+ RAM).

**Q: What quantization should I use?**
A: For 8B models:
- **Q4_K_M**: Best balance (4.5GB, good quality)
- **Q5_K_S**: Higher quality (5GB)
- **Q6_K**: Maximum quality (6GB, slower)
- **Q2_K**: Ultra-compressed (2.5GB, lower quality)

**Q: Does RAG work without internet?**
A: Yes. RAG embedding and querying are completely offline after initial embedding model download (~100MB).

**Q: How secure is the encryption?**
A: AES-256-GCM with hardware-backed keys (Android KeyStore) is military-grade encryption. On supported devices, keys are stored in Trusted Execution Environment (TEE) or Secure Element (SE), making extraction extremely difficult.

**Q: Can I share encrypted RAGs?**
A: Yes. Export RAG as `.neuron` packet with encryption enabled. Share the file and password separately. Recipients can import and decrypt with the password.

**Q: How does Text-to-Speech work?**
A: TTS uses the Supertonic TTS engine running entirely on-device via ONNX Runtime. Download the TTS model from the Model Store or directly from the Settings screen under the TTS section, then tap the speak button on any assistant message. You can also enable auto-speak in Settings to have responses read aloud automatically.

**Q: Can I use TTS offline?**
A: Yes. After downloading the TTS model (~100MB), all speech synthesis happens locally with no internet required.

**Q: What do "denoising steps" control in TTS?**
A: Higher steps produce clearer, more natural speech but take longer to synthesize. The default of 2 steps provides a good balance. Increase to 4–8 for maximum quality.

### Troubleshooting

**Q: App crashes on model load?**
A: Likely out of memory. Try:
1. Close other apps
2. Use smaller model (Q4 instead of Q6, or 1B/3B instead of 7B/8B)
3. Reduce context size in model config
4. Restart device to free RAM

**Q: Image generation fails or crashes?**
A: SD 1.5 requires significant RAM. Ensure:
1. Device has 8GB+ RAM
2. No other heavy apps running
3. Try lower resolution (512x512)
4. Restart app and try again

**Q: Models download but won't load?**
A: Check:
1. File is complete (compare size to HuggingFace listing)
2. File is valid GGUF format
3. Model isn't corrupted (re-download if suspicious)
4. Sufficient RAM available

**Q: RAG queries return no results?**
A: Verify:
1. RAG is enabled in RAG management screen
2. RAG loaded successfully (check status)
3. Embedding model downloaded
4. Query is semantically related to RAG content

---

## Contributing

Contributions are welcome! Focus areas:

### Priority Areas
- Bug fixes and stability improvements
- Performance optimizations (inference speed, memory usage)
- Device compatibility testing (especially mid-range devices)
- Documentation improvements and translations
- UI/UX enhancements (accessibility, dark theme refinements)

### Development Areas
- Speech-to-Text (STT) integration
- Multi-modal model support
- Additional plugins and plugin marketplace
- Additional document format support
- Advanced RAG features

### Process
1. **Fork the repository**
2. **Create feature branch**: `git checkout -b feature/your-feature-name`
3. **Make changes** with clear, focused commits
4. **Test on real devices** (emulators don't reflect real performance)
5. **Write clear commit messages** explaining "why" not just "what"
6. **Submit Pull Request** with description of changes and testing done

### Code Style
- Follow Kotlin coding conventions
- Use meaningful variable/function names
- Comment complex logic
- Prefer immutability where possible
- Use Jetpack Compose best practices for UI

### Testing
- Test on multiple devices (low-end, mid-range, flagship)
- Verify memory usage doesn't regress
- Test offline functionality
- Check encryption/decryption operations
- Validate UI on different screen sizes

---

## License

**Apache License 2.0**

See [LICENSE](LICENSE) for full details.

### What This Means
- ✅ Commercial use permitted
- ✅ Modification permitted
- ✅ Distribution permitted
- ✅ Patent use permitted
- ✅ Private use permitted
- ⚠️ Trademark use not permitted
- ⚠️ Liability and warranty disclaimer

Use ToolNeuron in commercial products, modify it, distribute it, all without restrictions. Attribution appreciated but not required.

---

## Acknowledgments

ToolNeuron stands on the shoulders of giants:

### Core Technologies
- [llama.cpp](https://github.com/ggerganov/llama.cpp) - Efficient LLM inference by Georgi Gerganov
- [LocalDream](https://github.com/xororz/local-dream) - Stable Diffusion on Android
- [Jetpack Compose](https://developer.android.com/jetpack/compose) - Modern Android UI framework

### Libraries
- [ONNX Runtime](https://onnxruntime.ai/) - TTS model inference
- [Coil 3](https://coil-kt.github.io/coil/) - Image loading for avatar images
- [Jsoup](https://jsoup.org/) - HTML parsing for web search plugin
- [Apache POI](https://poi.apache.org/) - Microsoft document parsing
- [PDFBox-Android](https://github.com/TomRoush/PdfBox-Android) - PDF processing
- [EpubLib](https://github.com/psiegman/epublib) - EPUB support
- [Room](https://developer.android.com/training/data-storage/room) - Database abstraction
- [Dagger Hilt](https://dagger.dev/hilt/) - Dependency injection
- [OkHttp](https://square.github.io/okhttp/) - HTTP client
- [Retrofit](https://square.github.io/retrofit/) - Type-safe HTTP client

### Inspiration
- [SillyTavern](https://github.com/SillyTavern/SillyTavern) - Character card format and post-history instruction techniques
- [Mem0](https://github.com/mem0ai/mem0) - AI memory architecture (AUDN cycle, extraction patterns)
- Privacy-first movement
- Open source community
- User feedback and feature requests

---

## Support

### Get Help
- **Discord Community**: [Join Server](https://discord.gg/mVPwHDhrAP) - Active community for questions, tips, and discussions
- **GitHub Issues**: [Report Bug/Request Feature](https://github.com/Siddhesh2377/ToolNeuron/issues)
- **Email**: siddheshsonar2377@gmail.com

### Support Development
- ⭐ **Star this repository** if you find it useful
- 🐛 **Report bugs** to help improve stability
- 💡 **Suggest features** to guide development
- 📖 **Improve documentation** for better onboarding
- 🔧 **Contribute code** to add features
- 💬 **Help others** in Discord community

---

## Security

### Reporting Vulnerabilities
If you discover a security vulnerability, please:
1. **Do NOT** open a public GitHub issue
2. Email siddheshsonar2377@gmail.com with details
3. Include steps to reproduce
4. Allow reasonable time for fix before public disclosure

We take security seriously and will respond promptly.

---

<div align="center">

**Built with ❤️ by [Siddhesh Sonar](https://github.com/Siddhesh2377)**

*Privacy-first AI for everyone. Own your data. Own your AI.*

[⭐ Star this repository](https://github.com/Siddhesh2377/ToolNeuron) · [🐛 Report Bug](https://github.com/Siddhesh2377/ToolNeuron/issues) · [💡 Request Feature](https://github.com/Siddhesh2377/ToolNeuron/issues) · [💬 Join Discord](https://discord.gg/mVPwHDhrAP)

---

### Quick Links
[Installation](#installation) · [Quick Start](#quick-start) · [Features](#features-overview) · [RAG System](#rag-system-document-intelligence) · [Memory Vault](#memory-vault-secure-storage) · [FAQ](#faq)

---

**License**: Apache 2.0 · **Version**: 2.0.0 · **Platform**: Android 12+

</div>