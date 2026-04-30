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
# Dev Notes - v3.1

Everything you put into this app stays on your phone. Models, chats, attachments, settings, the lot. None of it leaves the device. And if someone copies your data over to a different build of the app (theirs, not mine), it won't open there either, even on your own phone with root.

## The rebuild

Earlier builds spread data around. SharedPreferences for settings, Room for some chats, plain files for the rest. It worked. Every extra place was also a way for something to leak.

Storage got rewritten. There's one engine now that owns every byte saved to disk. Each record is encrypted, the file checks itself for tampering on read, and the master key sits in your phone's secure chip. StrongBox on Pixels and newer Samsung devices, the TEE on most others. On phones without either, Android falls back to a software key that's still tied to the app and the device.

Old data from previous builds didn't carry over. No shim. If you had things in there, they're gone. Sorry. I won't pull that on you again unless I really have to.

## What's in

Local LLM chat. Vision models work when you load one, the side-car file they need loads with the base. Documents you attach get indexed locally and the model sees the relevant chunks next to your question, with citations on the reply pointing back at which source said what. Voice both ways: Whisper turns what you say into text, sherpa-onnx speaks replies back. A Remote Server you can switch on so other devices on your Wi-Fi can chat with the loaded model through an OpenAI-compatible API, with a small bundled web UI for it. The HuggingFace Explorer lives in the app with the full filter set, so you don't have to leave to find a model.

New in this release: Research. Type /research followed by a question, or tap the compass icon on the input bar. The app runs a DuckDuckGo search, reads the top hits, summarises each one, asks the model what it still doesn't know, then loops. After a few rounds it writes a structured document with sections and citations linking to every source it touched. The research card expands and shows you what each iteration did, search query, fetched URLs, compress numbers, the follow-up questions. Stop is fast now. Pressing it actually reaches the inference engine instead of waiting for it to finish on its own.

## What's out

Tool calling, image generation, the plugin hub. Cut, not parked. Tool calling let models reach off-device too easily, for too little gain. Image gen needed a big native chunk for something that wasn't central. The plugin hub was only ever a launcher for tool calling.

## Security

Before this release I did three passes. Known weak spots in code patterns. Active tamper attempts I could think of. A survey of recent breach reports and academic papers. Five real ways in fell out of that. All five are closed:

- The vault now opens under a key wrapped by the secure chip. Used to be a plaintext file you could read with one shell command.
- Every gated feature now goes through a native policy engine. The old code handed a Kotlin true/false back across the JNI boundary. Yeah.
- The PIN check went from 64 MB / 2 rounds (web-login speed) to 128 MB / 4 rounds (offline-attack speed). Roughly a second and a half per guess on a Pixel 8, which sounds small until you imagine someone trying every six-digit combination.
- Three free tries on a wrong PIN, then 1, 5, 15, 60, 240, 720, 1440 minutes between attempts, full wipe on the tenth one. Optional panic PIN that wipes immediately. The app locks the moment it goes to the background. There's also a defence against someone setting the system clock back to skip the cooldown.
- The Frida / Xposed / debugger checks were already in the binary. Nothing was calling them. Embarrassing. Wired into boot now.

Two bigger pieces landed after that audit:

The encryption keys now mix in a hash of the app's signing certificate. So if someone copies your phone's data over to a clone of the app they built themselves, the keys come out wrong and decryption fails. To the clone, your data just looks like a fresh empty install. They get nothing.

Chat history, the bytes of every document you've attached, and the keyword index for retrieval used to be plaintext on disk. Android's app-data isolation kept normal apps out, but a rooted device could read them. All encrypted now, under the same key chain as everything else.

Still on me:

- The write-ahead log inside the storage engine flushes cleartext during commits. It's a real bug. The fix lives down in the storage core and I haven't got there yet.
- No HTTPS on the Remote Server. It's LAN-only by design. Only run it on networks you trust.
- No Google Play Integrity. Adding it pulls in Play Services, which would undo most of what this app is about.

## Gaps you'll notice

Downloads happen in the background and progress through the notification, there's no dedicated downloads screen yet. The Remote Server speaks plain HTTP. TLS, Bonjour-style auto-discovery, and QR pairing are all Phase 2.

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
