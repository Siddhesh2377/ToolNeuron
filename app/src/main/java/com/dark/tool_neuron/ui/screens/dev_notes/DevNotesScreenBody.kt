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
# Dev Notes — v3.0

This build is rebuilt around one thing: keep your stuff on your phone. Models, chats, documents, crypto state. All local.

## The rebuild

Old builds spread data across SharedPreferences, Room, plain files, raw model blobs. It worked. Every extra store was also a way for something to leak.

So storage got rewritten. There's one engine now, HXS, that owns every persisted byte. Sealed on disk, integrity-checked on read, keyed off a hardware-backed Android Keystore alias (StrongBox where the phone has it, TEE otherwise).

The trade: your old data didn't migrate. No shim, no back-compat. If you had stuff in there, it's gone. I won't ask you to do that again.

## What's in

Local LLM chat, RAG over your own documents, vision models (auto-loaded mmproj), voice (Whisper STT and sherpa TTS), and a Remote Server that exposes the loaded model on your Wi-Fi over an OpenAI-compatible API. HF Explorer lives in-app with the full filter taxonomy, so you don't have to tab over to the website to find a model.

## What's out

Tool calling, image generation, the plugin hub. Cut, not parked. Tool calling gave models too many ways to reach off-device for too little gain. Image gen was a heavy native surface for a feature that wasn't central. The plugin hub only existed to feed tool calling.

## Security

I did a three-sided audit before this release: known-weakness patterns, active tamper attempts, and a survey of recent breach reports and academic papers. Five real bypasses came out of it. All five are closed:

- The vault opens under a Keystore-wrapped DEK now. Used to be a plaintext file you could `cat`.
- Every protected feature is gated by a native policy engine. The old code handed a Kotlin boolean back across the JNI boundary, which is exactly as bad as it sounds.
- Argon2id moved from 64 MB / 2 iterations (web-login territory) to 128 MB / 4 iterations. About 1.5 s per guess on a Pixel 8, which is the smallest of the wins but the easiest to brag about.
- Three free wrong PINs, then 1m / 5m / 15m / 1h / 4h / 12h / 24h, full wipe at ten. Optional panic PIN, clock-rollback defence, auto-lock the moment the app goes to background.
- The Frida / Xposed / debugger checks were sitting in the binary with zero call sites. Embarrassing. Wired into boot now.

Still on me:

- HXS write-ahead log flushes cleartext during commits. Real bug. Fix lives in the HXS core and I haven't got there yet.
- No TLS on the Remote Server. LAN-only by design — only run it on networks you trust.
- No Play Integrity. Adding it pulls in Google Play Services, which would undo most of what this app is about.

## Gaps you'll notice

Sherpa VAD runs but isn't wired to a UI trigger. Downloads progress through the notification, no dedicated screen yet. The RAG index UI is thin. Remote Server is HTTP-only — TLS, mDNS, and QR pairing are Phase 2.

That's where it's at.
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
