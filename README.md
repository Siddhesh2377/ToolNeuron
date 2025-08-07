# NeuroVerse

**NeuroVerse** is a modular, privacy‑first AI assistant for Android designed to run entirely offline. It combines on‑device language models, dynamic plugins, and a secure memory architecture to give users full control of their phones without relying on the cloud.

<p align="center">
  <img src="https://img.shields.io/badge/Built%20With-Kotlin%20%7C%20Jetpack%20Compose-purple" />
</p>

---

## What is NeuroVerse?

NeuroVerse converts natural‑language prompts into structured commands, executes them through a flexible plugin framework, and stores context in an encrypted symbolic memory called **NeuronTree**. The entire pipeline—from speech or text input to device automation—runs locally.

---

## Core Features

| Feature                 | Summary                                                                                              |
| ----------------------- | ---------------------------------------------------------------------------------------------------- |
| Natural Language → JSON | On‑device inference with `llama.cpp` and a lightweight Kotlin wrapper generates structured commands. |
| Secure Memory           | NeuronTree data is encrypted using hardware‑backed keys.                                             |
| Offline‑First           | No telemetry or external APIs; all processing happens on the device.                                 |
| Dynamic Plugin System   | Runtime‑loaded `.zip` bundles add tasks, Compose UIs, and automation routines.                       |
| Modern UI               | Clean Material 3 interface built with Jetpack Compose.                                               |

---

## Demo

<p align="center">
  <img src="RES/output.gif" alt="Demo" width="260"/>
</p>

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

## Contributing

Contributions are welcome in the following areas:

* Plugin development
* AI model integration and optimisation
* Task library extensions
* UI and user experience improvements

**How to contribute**

1. Fork the repository.
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
[GitHub @Siddhesh2377](https://github.com/Siddhesh2377)

---

## Acknowledgements

* [`llama.cpp`](https://github.com/ggml-org/llama.cpp)
* [`SmolChat‑Android`](https://github.com/shubham0204/SmolChat-Android)
* JetBrains, 
* Android Open Source Project, 
* GitHub
