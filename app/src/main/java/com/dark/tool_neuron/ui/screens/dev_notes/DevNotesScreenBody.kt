package com.dark.tool_neuron.ui.screens.dev_notes

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import com.dark.tool_neuron.ui.components.markdown.LocalMarkdownColors
import com.dark.tool_neuron.ui.components.markdown.lazyMarkdownItems
import com.dark.tool_neuron.ui.components.markdown.rememberMarkdownColors
import com.dark.tool_neuron.ui.theme.LocalDimens

private val DEV_NOTES = """
# Developer Notes

> Read this before you tap anything.

---

## The focus, from now on

ToolNeuron is being rebuilt around one thing: **maximum user privacy with offline LLMs on device**.

Everything runs locally. Everything the app writes to disk is sealed with our own crypto stack. Nothing about your chats, your documents, or what you ask the model leaves the phone unless *you* explicitly send it out.

If a feature doesn't help us serve that goal, it doesn't belong in this build.

---

## Why the storage was rebuilt

This is the real reason.

Older builds spread data across several loosely-managed stores — `SharedPreferences` here, a Room DB there, raw files for models, plaintext metadata for chats. It worked, but it was **scattered and unsecured**. Every extra store was another surface that could leak a piece of you.

**HXS** (Hex Storage) exists to end that:

- **Centralized** — one engine owns every persisted byte. No side channels
- **End-to-end sealed** — every block encrypted and integrity-verified on read. Tampering fails loudly
- **Post-quantum-ready** encryption design
- **No legacy shims** — nothing carries habits from the old format

The long-term goal is a build that could plausibly ship into **regulated environments** — places where offline, auditable, end-to-end private data handling isn't a nice-to-have, it's the requirement. That bar doesn't let you keep a scattered storage layer around.

### Your previous data is gone

If you used an earlier build, that data didn't come with you. No migration, no compatibility shim — on purpose. If something you had is gone, I'm sorry. It won't happen again.

---

## What we build in-house

We don't borrow privacy-critical code. We write it.

- **HXS** native hex-storage engine, C++ core with a Kotlin API
- **HXS Encryptor** symmetric + asymmetric encryption on top of HXS
- **Download Manager** multi-threaded native downloader so model files never touch a third-party SDK
- **Networking** our own HTTP stack, designed to impersonate browser-style fingerprints so you don't need a VPN to browse without being tracked
- **GGUF Engine** on-device LLM inference via `custom-llama.cpp`, ARM64
- **AI Sherpa** on-device speech pipeline via `custom-sherpa-onnx`

---

## What's in scope

This app serves **text and VLM workloads** end-to-end, offline:

- Chat with local LLMs (GGUF, ARM64)
- **RAG** over your own documents — indexing and retrieval all on-device
- **VLM** support — vision-capable models that read images you paste or attach
- Streaming markdown output with inline math
- Adaptive layouts for phones, tablets, foldables
- Material 3 Expressive UI with continuous-corner shapes

---

## What's out of scope — deliberately

These were removed from this build. They're not "hidden for later" — they're out:

- **Tool calling** — gave LLMs too many ways to reach off-device for too little gain. Adds parser, dispatch, sandboxing, and attack surface
- **Image generation** — on-device diffusion (QNN / MNN) carried a heavy native surface for a feature that wasn't central to private text workflows
- **Plugin hub** — only existed to feed tool calling

We chose narrower scope over complexity. The engine and renderer are better for it.

---

## Coming: private browser, no VPN

Tool calling is out, but **you still need the web sometimes**. Plan:

- An in-app browser screen backed by our own **Networking** module
- No third-party webview tracking pipeline, no VPN dependency
- The networking module impersonates standard browser fingerprints so requests look like normal traffic — this is the privacy story, not a VPN tunnel
- From the browser screen you can **copy content or share the link back to the LLM** — you decide what crosses into the chat, not an automated agent

This is still being designed. If you have ideas about fingerprint-masking, quic/tls layering, or DNS handling that keep things private without a VPN, write them down.

---

## Security audit is pending

Before the next major release we're running a security pass on our own code:

- HXS at-rest sealing — any path that bypasses it
- HXS Encryptor key lifecycles — any key living longer than it should
- Download Manager — any URL or header that could leak identifying info
- Networking — fingerprint consistency, cookie isolation, DNS leaks
- Crash and log sinks — no chat content, no model paths, no user data in logs

If you find something, tell us. Quietly.

---

## Known gaps in this build

- Sherpa VAD runs but has no UI trigger yet
- Downloads show progress via foreground notification, no in-app download screen yet
- RAG indexing UI is minimal — the pipeline works, the surface is thin
- Home screen is still light on surfacing what the engine can do

---

*More soon.*
""".trimIndent()

@Composable
fun DevNotesScreen(innerPadding: PaddingValues) {
    val dimens = LocalDimens.current
    val markdownColors = rememberMarkdownColors()

    CompositionLocalProvider(LocalMarkdownColors provides markdownColors) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(
                horizontal = dimens.spacingMd,
                vertical = dimens.spacingMd
            )
        ) {
            lazyMarkdownItems(text = DEV_NOTES, keyPrefix = "dev_notes")
        }
    }
}
