# NeuroVerse

**NeuroVerse** is a modular, privacy‑first AI assistant for Android designed to run entirely offline. It combines on‑device language models, a dynamic plugin system, and a secure memory architecture to deliver device automation without relying on the cloud.

<p align="center">
  <img src="https://img.shields.io/badge/Built%20With-Kotlin%20%7C%20Jetpack%20Compose-purple" />
</p>

---

## What is NeuroVerse?

NeuroVerse converts natural‑language prompts into structured commands, executes them through a flexible plugin framework, and stores context in an encrypted symbolic memory called **NeuronTree**. The entire pipeline—from text or speech input to device automation—runs locally.

---

## Official Plugins Repository

Production‑ready, first‑party plugins live in a separate repository:

**Neuro‑V‑Sys Plugins** → [https://github.com/Siddhesh2377/Neuro-V-Sys-Plugins](https://github.com/Siddhesh2377/Neuro-V-Sys-Plugins)

This repository contains independently buildable Android library modules 
* `ai-chat`
* `app-io`
* `demo-macro`
---
Each module can be packaged into a distributable **plugin.zip** containing `plugin.aar` and `Manifest.json`, which the host app (NeuroVerse) can discover and load at runtime.

### How Plugins Integrate with NeuroVerse

* **Manifest**: Each plugin defines an `entryClass` in `src/main/Manifest.json` (e.g., `com.mp.ai_chat.ChatScreenPlugin`).
* **Packaging**: Plugins are distributed as `plugin.zip` with at least `plugin.aar` and `Manifest.json`.
* **Loading**: NeuroVerse scans user‑provided plugin zips, validates their manifest, and loads the entry class to register UI surfaces and actions.
* **UI**: Plugins may expose Jetpack Compose screens that the host renders within the app’s navigation.

> For build/packaging details, see the README in the plugins repository. A shared Gradle task (e.g., `exportPluginZip`) can be configured per module to produce the correct zip artifact.

---

## Core Features

| Feature                 | Summary                                                                                                       |
| ----------------------- | ------------------------------------------------------------------------------------------------------------- |
| Natural Language → JSON | On‑device inference with `llama.cpp` (GGUF) and a lightweight Kotlin wrapper to generate structured commands. |
| Secure Memory           | **NeuronTree** data is encrypted using hardware‑backed keys.                                                  |
| Offline‑First           | No telemetry or external APIs; all processing is local.                                                       |
| Dynamic Plugin System   | Runtime‑loaded plugin bundles add tasks, Compose UIs, and automation routines.                                |
| Modern UI               | Clean Material 3 interface built with Jetpack Compose.                                                        |

---

## Architecture Overview

| Layer          | Responsibility                                   |
| -------------- | ------------------------------------------------ |
| UI             | Jetpack Compose, plugin‑provided screens         |
| Inference      | `llama.cpp` (GGUF) for language understanding    |
| Command Engine | Translates JSON actions into runnable tasks      |
| Task Manager   | Manages plugin coroutines and execution context  |
| Memory         | Encrypted NeuronTree storage                     |
| Automation     | AccessibilityService bridges tasks to UI actions |

---

## Technical Stack

* Kotlin, Jetpack Compose, Coroutines
* `llama.cpp` / GGUF models
* Room Database with Keystore‑backed encryption
* Compose Navigation and scoped state management
* AccessibilityService for automation

---

## Getting Started

1. **Clone NeuroVerse** and open in Android Studio.
2. **Install a model** compatible with `llama.cpp` (GGUF).
3. **(Optional) Add Plugins**

    * Build plugins from the **Neuro‑V‑Sys Plugins** repository.
    * Import the resulting `plugin.zip` bundles into NeuroVerse using the in‑app importer or by placing them in the app‑specific plugin directory (see host configuration).

---

## Contributing

Contributions are welcome in the following areas:

* Plugin system improvements
* AI model integration and optimisation
* Task library extensions
* UI and user experience improvements

**How to contribute**

1. Fork this repository.
2. Create a feature branch.
3. Follow the coding guidelines.
4. Document changes clearly and open a pull request.

---

## Licence and Commercial Use

NeuroVerse is released for personal, educational, and non‑commercial use only. Commercial deployment, redistribution, or integration is prohibited without written permission.

To request a commercial licence, contact **[siddheshsonar2377@gmail.com](mailto:siddheshsonar2377@gmail.com)**.

![Licence: custom](https://img.shields.io/badge/licence-custom-blue)

---

## Author

**Siddhesh Sonar**
Android Developer · AI Enthusiast · Open‑Source Contributor
[GitHub @Siddhesh2377](https://github.com/Siddhesh2377)

---

## Acknowledgements

* [`llama.cpp`](https://github.com/ggml-org/llama.cpp)
* [`SmolChat-Android`](https://github.com/shubham0204/SmolChat-Android)
* JetBrains, 
* Android Open Source Project, 
* GitHub
