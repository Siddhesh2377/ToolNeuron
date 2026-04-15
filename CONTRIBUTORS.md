# Contributors & Related Projects

ToolNeuron is built across multiple repositories. Here's how they fit together.

---

## Project Ecosystem

### [ToolNeuron](https://github.com/Siddhesh2377/ToolNeuron)
The main Android app. UI, chat engine, RAG pipeline, plugin system, AI memory, backup/restore — everything the user interacts with.

### [Ai-Systems-New](https://github.com/Siddhesh2377/Ai-Systems-New)
Core AI system libraries that power ToolNeuron's inference. Native modules for GGUF model loading, Stable Diffusion, TTS, and embedding generation. These get compiled into `.aar` libraries consumed by the main app.

### [llama.cpp-android](https://github.com/Siddhesh2377/llama.cpp-android)
Android-specific fork/integration of [llama.cpp](https://github.com/ggerganov/llama.cpp). Handles the JNI bridge between Kotlin and the native C++ inference engine. Optimized for ARM64 with device-aware thread management.

---

## How They Connect

```
ToolNeuron (app)
    |
    +-- Ai-Systems-New (native AI libraries)
    |       |
    |       +-- llama.cpp-android (GGUF inference JNI bridge)
    |       +-- Stable Diffusion engine
    |       +-- TTS engine (ONNX Runtime)
    |       +-- Embedding engine
    |
    +-- ums (Unified Memory System, in-repo module)
    +-- neuron-packet (encrypted RAG format, in-repo module)
    +-- system_encryptor (native crypto, in-repo module)
```

---

## Contributing

Contributions are welcome across all three repos. If you're working on:

- **UI, chat, plugins, RAG, memory** — contribute to [ToolNeuron](https://github.com/Siddhesh2377/ToolNeuron)
- **Inference performance, model loading, native crashes** — contribute to [Ai-Systems-New](https://github.com/Siddhesh2377/Ai-Systems-New) or [llama.cpp-android](https://github.com/Siddhesh2377/llama.cpp-android)

See the main [README](README.md) for contribution guidelines.

---

## Maintainer

**[Siddhesh Sonar](https://github.com/Siddhesh2377)** — creator and primary maintainer of all three repositories.

---

## Contributors

<!-- Add contributors here as the project grows -->

Want to see your name here? Check the open issues on any of the repos above and submit a PR.
