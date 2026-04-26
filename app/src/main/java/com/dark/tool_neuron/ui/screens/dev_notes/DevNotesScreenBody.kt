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
- **Native Server** embedded OpenAI-compatible HTTP server (cpp-httplib + nlohmann/json), no BoringSSL dep, header-only

---

## What's in scope

This app serves **text and VLM workloads** end-to-end, offline:

- Chat with local LLMs (GGUF, ARM64)
- **RAG** over your own documents — indexing and retrieval all on-device
- **VLM** support — vision-capable models that auto-load their projector from a colocated `mmproj`
- **Voice** — on-device STT (Whisper) and streaming TTS (VITS / Kokoro) via sherpa-onnx
- **Remote Server** — expose the loaded model on the LAN over an OpenAI-compatible API + a bundled Web UI
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

## Remote Server

The loaded model becomes an OpenAI-compatible HTTP endpoint other devices on your Wi-Fi (or this device's own browser) can call.

- **Embedded native HTTP server.** cpp-httplib + nlohmann/json, both header-only, vendored via FetchContent. No TLS, no zlib, no OpenSSL. Phase 1 is HTTP-only on purpose.
- **Bind modes.** Default is All interfaces so loopback and Wi-Fi both reach it. Loopback-only and Wi-Fi-only are the alternatives. The screen shows two URLs when applicable: "From this device" (`127.0.0.1`) and "From LAN" (your Wi-Fi IP).
- **Bearer-token auth.** A `tn_sk_…` token is generated on first start and stored in the encrypted vault. Reveal in-app gates on an active session. Rotate to invalidate.
- **Lockdown.** While the server is running, the app is pinned to the Server Screen. Drawer hidden, back absorbed. Stop the server to navigate away. The server owns the model lifecycle.
- **`/v1/models`** returns only the currently-loaded model on purpose. Pick what you want in-app first; no dynamic load over the wire.
- **Port** defaults to 11434, persists, clamped to 1024–65535.

### Rewritten Web UI

The bundled chat UI got rebuilt. The old one was functional but uneven. Streaming would jitter, the message list reflowed on every token, the spacing was a bit loose. The new one fixes the parts that bugged me:

- Token updates are throttled to one `requestAnimationFrame` per frame. The streaming bubble uses `contain: layout style` so it can't reflow ancestors. The cursor is a CSS animation, not a DOM mutation.
- Auto-scroll only follows when you're already at the bottom. If you scrolled up to read older context, it stays put.
- System font stack, `100dvh` so the composer respects the keyboard, `safe-area-inset-bottom` for round phones, `backdrop-filter` on the topbar. Dark mode follows the OS.
- About 600 lines total including the markdown renderer.
- Sidebar history in `localStorage`. Rename, delete, clear-all. Bearer token in a Settings dialog, browser-local.

### API docs at `/docs`

Point any browser at `http://<server>:<port>/docs` and you get a documentation site served straight from native memory. Same visual language as the chat UI. Pages: Overview, Authentication, List models, Chat completions, Streaming (SSE), Health, Errors, Rate limits & bans, Examples, Security notes.

The Examples page has copy-paste snippets for the OpenAI Python SDK, OpenWebUI, plain `fetch`, and llama-index. Every code block has a copy button.

Like `/`, `/docs` is on the public-path allowlist. Read the docs without a token.

---

## VLM auto-load

Vision-capable repos publish a base `.gguf` plus an `mmproj-*.gguf` projector. The app downloads each into the same per-repo folder under `models/vlm/<repo>/` and the projector auto-attaches the moment you load the base model. No "load projector" button, no manual pairing. The Plus → Attach image tile is gated on a loaded VLM. Switching to a non-VLM model releases the projector cleanly.

---

## Voice — Store-only install

Speech-to-text (Whisper) and text-to-speech (VITS / Kokoro) ride the same `:inference` process via the sherpa-onnx AAR.

- Installed from the **Store**, filter to `tts` or `stt` chips. Archives are `.tar.bz2`, extracted with per-file progress into `<filesDir>/voice/<kind>/<folder>/`. A sherpa config JSON is auto-built; the archive is deleted after extraction.
- The first install of each kind is auto-selected as the active model.
- **Mic button is always rendered.** If no STT model is installed, tapping it routes to the Store. Permission is requested on first tap.
- **Streaming TTS is faked at the text layer** — the renderer chunks at sentence boundaries and the first chunk plays while later ones synthesize in the background. The speak button on a bubble shows a load indicator and flips to a stop icon while playback runs.
- STT unloads after every transcription (~75 MB freed). TTS sample rate auto-detects from the model on first use.

---

## HF Explorer — every HuggingFace filter, in-app

The Settings tab in the Store has a "Browse HuggingFace" link that opens a full search screen. The point: never tab over to the website to find a model.

What you can filter by:

- **Parameter count.** A range slider with stops at 100M, 500M, 1B, 3B, 7B, 13B, 30B, 70B, 175B, 500B, ∞. Maps to HF's `num_parameters=min:X,max:Y` query param. Drag both handles, reset is one tap.
- **Task.** All 52 pipeline tags HF exposes. The eight most useful are visible; the other 44 sit behind "+44 more".
- **Libraries.** GGUF is selected by default because that's what loads here, but you can flip it off to discover MLX / ONNX / TFLite / Transformers / etc. 57 in total.
- **Apps.** llama.cpp, Ollama, LM Studio, Jan, MLX LM, vLLM, Draw Things.
- **Inference providers.** Groq, Cerebras, SambaNova, Together AI, Novita, Hyperbolic, fal, Nscale.
- **Languages.** BCP-47 codes. Thirteen up front, ~60 under expand.
- **Licenses.** Apache, MIT, Llama-3.x family, Gemma, the CC-BY variants, OpenRAIL. Top ten visible, ~80 behind expand.
- **Regions.** US / EU.
- **Other tags.** `4-bit`, `8-bit`, `moe`, `custom_code`, `endpoints_compatible`, `text-generation-inference`, etc.
- **Quant tags.** Q2_K through Q8_0, the IQ-* series, GPTQ / AWQ / EXL2 / BNB.
- **Trained dataset.** Free text — `imagenet`, `glue`, `common-voice`.
- **Author.** Free text, server-side.
- **Gated.** Tri-state: Any / Open only / Gated only.
- **Inference warm.** Toggle for models that currently have a live HF inference endpoint.

Sort follows HF's five real options: Trending (default), Most downloads, Most likes, Recently updated, Recently created. Result cards now show the pipeline tag pill and the first eight raw tags from the model card. Search history persists across launches. Tap a repo to drill into its file tree with size filters and the total GGUF size.

---

## Setup flow

First launch: Intro → Setup (lock mode) → SetupPassword (if you picked password) → **SetupTheme** → ModelSetup → Home. The theme step lets you pick mode + accent palette before you ever see a chat. Continue lives on the bottom bar.

---

## The security pass happened

I ran a three-sided audit on the stack. One pass looking for known weakness patterns, one trying to actively tamper, one reading breach reports and academic papers to see what techniques show up in the wild.

Five full bypasses turned up. All closed.

- **Plaintext vault.** The app-prefs store was opening in plaintext. Password hash and salt sat on disk with no integrity check. You could `cat` the file. The vault now opens under a Keystore-wrapped key that lives in hardware — StrongBox if your phone has it, TEE otherwise. The wrapped DEK lives at `app_bootstrap/k.bin` as a raw XOR-masked binary; everything else is sealed inside `app_prefs/`.
- **Trust across JNI.** The password check used to hand a Kotlin boolean back across the native boundary. Flip one branch, you were in. It returns an opaque 32-byte session token from native now, and every gated feature asks a native policy engine whether that token is valid.
- **`if (verify)` on disable-lock.** Same class of problem. Same treatment.
- **Soft Argon2id.** 64 MB / 2 iterations, which is fine for a web login and nowhere near enough for an offline brute force. Bumped to 128 MB / 4 iterations / p=1, 32-byte output. One guess takes roughly 1.5 s on a recent phone, and that was the smallest win in the list.
- **Anti-tamper existed, nothing called it.** Frida/Xposed/debugger checks were sitting in the binary with zero call sites. Embarrassing. Wired into the boot sequence now.

### While I had the hood open

- **AuthState v4.** Every auth-critical field — security mode, salt, hash, panic salt/hash, lockout counter, last-seen timestamp — rides a sealed AAD-bound AEAD blob inside the encrypted vault. Two layers, not one.
- **Backoff with teeth.** First three wrong guesses are free. Fourth costs a minute, then 5 m → 15 m → 1 h → 4 h → 12 h → 24 h. Ten wrong and the vault wipes (DEK alias deleted, `k.bin` and `app_prefs/` blown away — fresh setup on next launch).
- **Panic PIN.** Optional. Set a second PIN that looks like it unlocks the app but actually nukes everything. For when someone's standing over you and "I forgot it" isn't going to work.
- **Clock-rollback defence.** Rolling the phone's clock backward to skip the backoff counts as two failed attempts and re-anchors the backoff to the last-seen timestamp.
- **Auto-lock on background.** The session clears the instant the app goes to background. No grace period, no "are you still there".
- **Accessibility allowlist.** Hostile accessibility services read your screen. Release builds refuse to run if one's attached that isn't on the known-good list. Debug builds just warn, because some of us use TalkBack and I don't want to argue with the OS while developing.
- **Root warning, not refusal.** A one-time dialog warns you that other root-capable apps could read the on-disk state, then never shows again. Hard-fail-on-root was removed — your device, your call.
- **Boot-integrity pipeline.** TOFU `.so` hashes (filename-keyed, rebound to install identity on every legitimate update), debugger / Frida / Xposed / hook-baseline checks. Tamper latches the policy engine; everything fails closed.
- **Obfuscated native strings.** The detection strings (`frida`, `xposed`, `TracerPid`, etc.) used to be visible to `strings libhxs_encryptor.so`. I checked — zero plaintext matches now.
- **Clipboard discipline.** Copied messages get the sensitive flag so they don't appear in clipboard history, and they auto-clear after 30 seconds if you haven't overwritten them yourself.
- **FLAG_SECURE on PIN screens.** No screenshots, hidden from recent apps.
- **`Log.d` stripped in release.** Whatever I whispered in development doesn't leak into logcat on your phone.
- **Pro-license hook is wired but disabled.** Every gated feature asks `PolicyEngine.isAllowed(feature, token)`. Pro feature IDs (`>= 1000`) currently return false unconditionally. The flip point is a single branch in `policy_engine.cpp` for a future signed-license blob bound to the APK signer hash captured on first launch.

### What's still not fixed

Rather say it than not.

- The HXS write-ahead log still writes cleartext during commits. Someone with filesystem access could read recent edits before they seal. Real bug. Fix lives in the HXS core and I haven't got there yet.
- No cert pinning. The app is mostly offline so it hasn't come up.
- No Play Integrity. That's a Google Play Services dependency, and adding one would undo a lot of what this app is about.
- No TLS on the Remote Server yet. LAN-only by design; if you need it, run the server only when you're on a trusted network.

### Tests

Forty-nine instrumented tests, green on my physical device and the Pixel emulator. Every item above has a test that would scream if it regressed.

If you find something the tests miss, tell me. Quietly.

---

## Codebase cleanup pass

I ran a sweep on the Kotlin and native sources to remove things that don't earn their keep:

- Stripped fifty-plus decorative `// ── … ──` divider comments.
- Removed KDoc that was just restating the function name.
- Replaced inline fully-qualified class names (`java.security.MessageDigest.getInstance(...)`) with proper imports.
- Centralised a duplicated 4-byte SHA-256 fingerprint helper that lived in two files.
- Killed three `Log.d` calls that were spamming logcat per token / per file extract.
- Deleted the leftover spec, plan, and TODO markdown files at the repo root. Project memory lives in `CLAUDE.md`, not in stale planning docs.
- Tightened `CLAUDE.md` itself. Same content, half the prose.
- New `README.md` that says what this project is and what it isn't, in roughly two screens of text.

If you're wondering where some of the older meandering in this dev-notes file went — yeah, that.

---

## Known gaps in this build

- Sherpa VAD runs but has no UI trigger yet
- Downloads show progress via foreground notification, no in-app download screen yet
- RAG indexing UI is minimal — the pipeline works, the surface is thin
- Home screen is still light on surfacing what the engine can do
- Remote Server is HTTP-only — TLS, mDNS discovery, QR pairing all deferred to Phase 2

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
