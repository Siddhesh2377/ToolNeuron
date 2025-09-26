# ToolNeuron (Previously NeuroVerse)

**Offline Android AI chat with a modular plugin framework**

ToolNeuron is a **secure, offline AI ecosystem for Android devices**. It allows users to run private AI models and dynamic plugins **fully offline**, with **hardware‑grade encryption** ensuring maximum privacy.

---

<p align="center">
  <img src="https://img.shields.io/badge/Platform-Android_14%2B-informational" />
  <img src="https://img.shields.io/badge/Built%20With-Kotlin%20%7C%20Jetpack%20Compose-purple" />
  <a href="#license"><img src="https://img.shields.io/badge/License-Apache_2.0-green" /></a>
  <a href="https://discord.gg/vjGEyQev"><img src="https://img.shields.io/badge/Discord-Join%20ToolNeuron-5865F2?logo=discord&logoColor=white"/></a>
</p>

---

### Note

* This repository's C++ code has been **transferred to a separate repo** for optimization: [AI Core](https://github.com/Siddhesh2377/Ai-Core)

---

## Try it Now

* **Download APK:** [Latest release](https://github.com/Siddhesh2377/NeuroVerse/releases/latest)

> Install the APK, grant permissions, open **AI Chat**, and start chatting. **No cloud account required.**

---

## Preview: Plugin Installation & Tool‑Calling

<p align="center">
  <img src="https://github.com/user-attachments/assets/f50ceb8a-5a32-4fa7-9ab4-e1223b983eb6" width="220">
  <img src="https://github.com/user-attachments/assets/c7187a51-b245-4305-b2d0-9cb2a1e467a9" width="220">
</p>

---

## Current Status

* **Available now:**

  * Local AI chat (on‑device)
  * **Web Search plugin** (tool‑calling from chat)
  * Runtime model switching
  * In‑app updates (check & install)
  * Import models from device & tweak parameters
  * Model Store downloads
  * Plugin manager (enable/disable, inspect metadata like author/role)

* **Coming soon:** Full automation, plugin gallery, advanced on‑device tools

---

## Key Features

* Run **local AI chat** without internet
* **Import custom GGUF models** and tune parameters
* **Dynamic plugin ecosystem** with validation & sandboxing
* **Web Search plugin** for optional online context
* **Switch models at runtime** without restarting
* **In-app updater** for instant fixes/features
* Hardware‑backed security with Android KeyStore

---

## Why ToolNeuron?

Most mobile AI apps rely on the cloud. ToolNeuron flips the paradigm:

* **Offline-first** — data stays on your device
* **Pluggable tools** — add capabilities without app updates
* **Secure by design** — strict plugin validation + sandboxing

> "Honestly, what you're doing is legendary." – Early user on Discord 🌱

Join the community: **[Discord](https://discord.gg/vjGEyQev)**

---

## Official Plugins

First-party plugins: **ToolNeuron System Plugins** — *link placeholder*

* **Web Search** — ✅ Available

> Packaging: `.zip` containing `plugin.aar` and `Manifest.json`

---

## Models & Engine

* **Backend:** `llama.cpp` via **JNI**
* **Model format:** GGUF (user-importable)
* **Controls:** temperature, top‑p, max tokens, etc.
* **Switcher:** change models at runtime from Settings

---

## Security & Privacy

* Fully offline experience
* Zero telemetry/analytics
* Hardware-backed encryption via Android KeyStore
* Strict plugin validation before loading

---

## Get Involved

* Contribute code via pull requests
* Submit feedback & feature requests on **[Discord](https://discord.gg/vjGEyQev)**

Every contribution helps keep ToolNeuron free and open-source.

---

## Roadmap

* ✅ Import custom models
* ✅ Web Search plugin
* ✅ In‑app updates
* 🚀 Plugin gallery for community sharing
* 🔒 End-to-end encryption for chat & storage
* ⚡ Automation tools for advanced workflows
* 🧩 Expand first-party plugin set

---

## License

Licensed under the **Apache License 2.0**. See [`LICENSE`](./LICENSE).

---

## Author

**Siddhesh Sonar**
Android Developer · AI Enthusiast · Open Source Contributor
GitHub: [@Siddhesh2377](https://github.com/Siddhesh2377)

---

## Acknowledgements

* `llama.cpp` and the GGUF community
* Jetpack Compose & Android Open Source Project
* Early users and open-source contributors 🙌
