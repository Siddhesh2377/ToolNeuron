# NeuroVerse

**Offline Android AI chat with a modular plugin framework (automation coming soon).**

NeuroVerse is a privacy‑first Android app that runs fully on‑device and showcases a plugin‑based architecture. The current public milestone is **chat‑only** via the first‑party **AI Chat** plugin. The command/automation engine exists in the codebase but is **disabled and not user‑facing yet**.

<p align="center">
  <img src="https://img.shields.io/badge/Platform-Android_14%2B-informational">
  <img src="https://img.shields.io/badge/Built%20With-Kotlin%20%7C%20Jetpack%20Compose-purple">
  <a href="#license"><img src="https://img.shields.io/badge/License-Apache_2.0-green"></a>
</p>

---

## Try it now

* **Download APK:** [latest release](https://github.com/Siddhesh2377/NeuroVerse/releases/latest)
* **Join Discord:** community support and plugin discussions — [https://discord.gg/tG5r9aDF](https://discord.gg/tG5r9aDF)
* <a href="https://www.producthunt.com/products/github-167?embed=true&utm_source=badge-featured&utm_medium=badge&utm_source=badge-github&#0045;cb231061&#0045;046f&#0045;4524&#0045;a405&#0045;cc41969cc199" target="_blank"><img src="https://api.producthunt.com/widgets/embed-image/v1/featured.svg?post_id=1005548&theme=light&t=1755241765667" alt="GitHub - Privacy&#0045;first&#0044;&#0032;on&#0045;device&#0032;LLM&#0032;chat&#0046;&#0032;Modular&#0032;plugins&#0046;&#0032;No&#0032;cloud | Product Hunt" style="width: 250px; height: 54px;" width="250" height="54" /></a>

> First‑run: install the APK, grant permissions, open **AI Chat** and start chatting. No cloud account required.

---

## Preview

<p align="center">
  <img src="https://github.com/user-attachments/assets/f50ceb8a-5a32-4fa7-9ab4-e1223b983eb6" alt="NeuroVerse chat screenshot" width="220">
</p>

---

## Status

* **Current:** Chat‑only experience using the built‑in **AI Chat** plugin.
* **Planned:** Command JSON generation, action validation, and on‑device UI automation are in development and will ship in future releases.

---

## What you can do today

* Local AI chat with a clean Material 3 UI.
* Load first‑party sample plugins to explore the plugin lifecycle and UI embedding.
* Build the app from source and experiment with the plugin API while automation is WIP.

---

## Official plugins

First‑party plugins live here: [https://github.com/Siddhesh2377/Neuro-V-Sys-Plugins](https://github.com/Siddhesh2377/Neuro-V-Sys-Plugins)

Available modules:

* `ai-chat` (current focus)
* `app-io` (under active development)
* `demo-macro` (prototype; not user‑facing yet)

Each module can be exported to a **plugin.zip** containing `plugin.aar` and `Manifest.json`, which NeuroVerse can import at runtime.

### Plugin integration overview

* **Manifest:** declare `entryClass` in `src/main/Manifest.json` (for example, `com.mp.ai_chat.ChatScreenPlugin`).
* **Packaging:** produce `plugin.zip` with `plugin.aar` and `Manifest.json`.
* **Loading:** import the zip from within NeuroVerse; the host validates and registers it.
* **UI:** plugins may provide Jetpack Compose screens rendered within the app.

See the plugin repo README for the `exportPluginZip` task and packaging details.

---

## Features (current vs planned)

| Area       | Current (public)                                                               | Planned (roadmap)                                                 |
| ---------- | ------------------------------------------------------------------------------ | ----------------------------------------------------------------- |
| Chat       | AI Chat plugin with local UI and message history                               | Tool‑aware chat, plugin suggestions                               |
| Models     | Local model support under evaluation; dev builds experiment with GGUF runtimes | Curated on‑device models with auto‑download and versioning        |
| Plugins    | Runtime loading of first‑party zips                                            | Community plugin gallery, ratings, templates                      |
| Automation | Not available in public builds                                                 | JSON command generation, validator, Accessibility‑based execution |
| Memory     | Basic chat history                                                             | Encrypted NeuronTree with scoped recall and user controls         |

---

## Architecture (high level)

| Layer            | Responsibility                                       |
| ---------------- | ---------------------------------------------------- |
| UI               | Jetpack Compose app shell and plugin screens         |
| Plugin Host      | Discovers, validates, and mounts plugin entry points |
| Inference (chat) | Local model adapter and chat loop                    |
| Command Engine   | Present but disabled in public builds                |
| Storage          | Room‑backed data; encryption work in progress        |

---

## Installation

### Install APK

1. Download from the [latest release](https://github.com/Siddhesh2377/NeuroVerse/releases/latest).
2. Install on Android 14 or newer.
3. Open **AI Chat** and start a conversation.

### Build from source

1. Clone the repository and open in Android Studio.
2. Sync Gradle and build `:app`.
3. Run on a physical device for best performance.

---

## Developing plugins

1. Start from the template in **Neuro‑V‑Sys Plugins**.
2. Implement your `entryClass` and any Compose UI.
3. Run the export task to produce `plugin.zip`.
4. Import the zip into NeuroVerse from the Plugins screen.

Open a PR to add your plugin to the community list once automation support ships broadly.

---

## Roadmap

* Ship command generation and validator in a public build
* Accessibility‑based action runner with safety guardrails
* In‑app plugin gallery and submission flow
* Basic task inspector and execution logs
* Small, pre‑bundled model for first‑run offline chat

Feedback is welcome in Issues and Discussions.

---

## Security and privacy

* Chat runs locally; no cloud requirement for the core experience.
* No analytics or telemetry in the core project.
* Always review third‑party plugin code before installing.

---

## Contributing

Contributions are welcome. Focus areas: plugin API, chat UX, model adapters, storage, and future automation.

1. Fork the repository.
2. Create a feature branch.
3. Document changes.
4. Open a pull request with a clear description and screenshots.

Good first issues are labeled accordingly.

---

## License

NeuroVerse is licensed under the **Apache License 2.0**.

See [`LICENSE`](./LICENSE) and [`NOTICE`](./NOTICE). Third‑party components remain under their original licenses.

---

## Author

**Siddhesh Sonar**
Android Developer · AI Enthusiast · Open‑Source Contributor
GitHub: [https://github.com/Siddhesh2377](https://github.com/Siddhesh2377)

---

## Acknowledgements

* `llama.cpp` and the GGUF ecosystem
* `SmolChat-Android`
* JetBrains
* Android Open Source Project
* GitHub
