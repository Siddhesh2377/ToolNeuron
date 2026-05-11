# HXD — Hex Downloader

A private, self-contained download engine for ToolNeuron. Designed for downloading large files (AI models, datasets) with full background survival, automatic resume, and zero leakage to the Android system.

---

## How HXD differs from Android's DownloadManager

| Feature | Android DownloadManager | HXD |
|---|---|---|
| **Visibility to other apps** | Entries appear in system `Downloads` content provider | Fully private — no system content provider entries |
| **MediaStore indexing** | Files auto-indexed by `MediaScanner` | Never indexed — files go exactly where you put them |
| **Cookie sharing** | Shares cookies with system `CookieManager` | No cookies sent unless explicitly set |
| **User-Agent** | Sends `AndroidDownloadManager/version` fingerprint | Empty by default — no fingerprint |
| **Notification control** | System-controlled UI | Full control — grouped, no sound, lock-screen hidden |
| **Resume granularity** | Internal, opaque | Explicit: byte-level, `Range` header, survives cold restart |
| **Dependency** | System service (can be disabled by OEMs) | None — fully self-contained |
| **Progress API** | `BroadcastReceiver` with polling | `Flow<HxdState>` — reactive, no polling |
| **File finalization** | Non-atomic (can leave partial files) | Atomic `rename()` — either complete or nothing |
| **Checksum** | Not supported | Optional SHA-256 verification post-download |
| **Authorization headers** | Not supported | Arbitrary headers per-download |
| **File I/O layer** | Java | C++ (POSIX `write`/`fsync`/`rename`) |
| **Survives swipe from recents** | Yes | Yes (`stopWithTask="false"`) |
| **Survives screen lock** | Yes | Yes (ForegroundService) |

---

## Architecture

```
┌────────────────────────────────────────────────────────┐
│  App / ViewModel                                       │
│  HxdManager.enqueue(ctx, url, destPath, options)       │
│  HxdManager.observe(id) → Flow<HxdState>               │
└──────────────────────┬─────────────────────────────────┘
                       │ startForegroundService
┌──────────────────────▼─────────────────────────────────┐
│  HxdService  (ForegroundService, START_STICKY)         │
│  ┌─────────────────────────────────────────────────┐   │
│  │  Dispatcher coroutine (Dispatchers.IO)          │   │
│  │  Semaphore(3) — max 3 concurrent downloads      │   │
│  └──────────┬──────────┬─────────────┬─────────────┘   │
│             │          │             │                  │
│   ┌─────────▼──┐ ┌─────▼──┐ ┌───────▼──┐              │
│   │ Download 1 │ │Download│ │ Download │              │
│   │ coroutine  │ │   2    │ │    3     │              │
│   └─────┬──────┘ └────┬───┘ └────┬─────┘              │
│         │             │          │                     │
│   HttpURLConnection (system TLS, redirect, Range)      │
│         │ 64 KB chunks                                 │
│   ┌─────▼──────────────────────────────────────────┐   │
│   │  HxdNative (JNI) → libdownload_manager.so      │   │
│   │  DownloadEngine (C++)                          │   │
│   │  • POSIX write() to .hxd_tmp                  │   │
│   │  • Rolling speed window (5 s, 30 samples)     │   │
│   │  • fsync + atomic rename on complete          │   │
│   │  • cancel / pause signalling (atomic flags)   │   │
│   └────────────────────────────────────────────────┘   │
│                                                        │
│  HxdNotification — grouped progress, IMPORTANCE_LOW    │
└────────────────────────────────────────────────────────┘
```

---

## C++ Engine

The native layer (`libdownload_manager.so`) handles everything that touches disk:

### `download_engine.cpp`
- **`prepare(id, destPath)`** — opens `<destPath>.hxd_tmp` in append mode if it already exists (resume), or creates it fresh. Returns the resume offset.
- **`write_chunk(id, data, offset, len)`** — writes raw bytes via POSIX `write()`. Returns `false` immediately if cancel or pause was signalled, or on I/O error (disk full, etc.).
- **`complete(id)`** — calls `fsync()` then `rename()`. The rename is atomic on Linux — you either get the complete file or nothing. No partial final files.
- **`fail(id)`** — closes the file descriptor. The `.hxd_tmp` is preserved for retry.
- **`cancel(id)` / `pause(id)`** — sets an atomic flag; the next `write_chunk()` sees it and returns `false`.
- **Speed tracking** — rolling deque of `(time_ms, downloaded_bytes)` samples, capped at 30 entries / 5 seconds. `calc_speed()` divides byte delta by time delta.

### `persistence.cpp`
Saves/loads the download queue to a compact binary file (`hxd_queue.bin`) in the app's internal files directory. Format: HXDQ magic + version + count + per-task record. Used to survive process death — on restart, `HxdService.onCreate()` calls `restoreQueue()` which re-enqueues in-progress tasks; they resume via `Range` headers.

---

## Features

### Automatic Resume
If a download is interrupted (network loss, process kill, low battery), the `.hxd_tmp` file is preserved. On the next attempt:
1. C++ `prepare()` detects the partial file and returns its size as `resumeOffset`.
2. Kotlin sets `Range: bytes=resumeOffset-` on the HTTP connection.
3. If the server responds `206 Partial Content`, download continues from the byte it stopped at.
4. If the server responds `200 OK` (no Range support), the file is re-downloaded from scratch.

### Concurrency
- 3 downloads run in parallel (configurable at compile time via `MAX_CONCURRENT`).
- Additional enqueued downloads wait in a Kotlin `ArrayDeque` — no thread blocking.
- Each active download is a child coroutine with a `SupervisorJob`, so one failure doesn't affect others.

### Privacy
- **No User-Agent** by default. Pass `HxdOptions(userAgent = "MyAgent/1.0")` to set one explicitly.
- **No cookies** — `Cookie: ""` is always sent, preventing accidental cookie leakage from the system `CookieManager`.
- **No system content provider** — files are never registered in `MediaStore` or `Downloads`.
- **Lock screen hidden** — notification visibility is `VISIBILITY_SECRET`.
- **Authorization headers** — pass `HxdOptions(headers = mapOf("Authorization" to "Bearer $token"))` for gated downloads (e.g. HuggingFace gated models).

### Checksum Verification
```kotlin
HxdManager.enqueue(
    context, url, destPath,
    HxdOptions(expectedSha256 = "abc123...")
)
```
SHA-256 is computed in Kotlin (`MessageDigest`) as bytes are streamed. If the final digest doesn't match, the file is deleted and the task is marked `FAILED`.

### Atomic Finalization
The file is written to `<destPath>.hxd_tmp` during download. On completion: `fsync()` + `rename()`. The rename is an atomic syscall — the final file either exists in full or not at all. There are no half-written final files.

---

## Kotlin API

### Enqueue

```kotlin
val id = HxdManager.enqueue(
    context  = context,
    url      = "https://huggingface.co/model.gguf",
    destPath = "${filesDir}/models/model.gguf",
    options  = HxdOptions(
        expectedSha256 = "abc123...",         // optional
        headers        = mapOf(
            "Authorization" to "Bearer $hfToken"
        )
    )
)
```

### Observe

```kotlin
// Single download
lifecycleScope.launch {
    HxdManager.observe(id).collect { state ->
        state ?: return@collect
        progressBar.progress = (state.progress * 100).toInt()
        speedLabel.text      = state.speedFormatted
        when (state.status) {
            HxdStatus.COMPLETED -> showSuccess()
            HxdStatus.FAILED    -> showError(state.error)
            else                -> {}
        }
    }
}

// All downloads
lifecycleScope.launch {
    HxdManager.tasks.collect { all ->
        recyclerAdapter.submit(all)
    }
}
```

### Control

```kotlin
HxdManager.pause(id)               // pauses, preserves partial file
HxdManager.resume(context, id)     // re-queues, continues via Range header
HxdManager.cancel(id)              // cancels and deletes partial file
HxdManager.cancelAll()             // cancels everything
```

---

## Permissions

Declared in the module manifest (merged automatically):

| Permission | Why |
|---|---|
| `INTERNET` | HTTP/HTTPS downloads |
| `FOREGROUND_SERVICE` | Required for ForegroundService |
| `FOREGROUND_SERVICE_DATA_SYNC` | Android 14+ correct foreground type for file downloads |
| `POST_NOTIFICATIONS` | Required for download progress notification (Android 13+) |

**The host app must request `POST_NOTIFICATIONS` at runtime on Android 13+.** HXD cannot do this from a library module.

---

## Service survival

| Scenario | Behaviour |
|---|---|
| App goes to background | Service continues — ForegroundService |
| Screen locks | Service continues — ForegroundService |
| User swipes app from recents | Service continues — `stopWithTask="false"` |
| Process killed by OS (low memory) | Service restarted by `START_STICKY`; queue restored from `hxd_queue.bin` |
| Device rebooted | Service does NOT auto-start — app must call `enqueue()` or `restoreQueue()` on next launch |

---

## File layout

```
<app internal storage>/
├── hxd_queue.bin              ← persisted download queue (binary, HXDQ format)
└── <your dest dir>/
    ├── model.gguf             ← completed download (final)
    └── model.gguf.hxd_tmp    ← in-progress or paused download (partial)
```

---

## Build config

| Property | Value |
|---|---|
| Min SDK | 29 (Android 10) |
| C++ standard | C++17 |
| ABI | arm64-v8a, x86_64 |
| LTO | Thin LTO (release builds) |
| Symbol visibility | hidden (all internal symbols stripped) |
| Stack protection | `-fstack-protector-strong` |
| Linker hardening | `-z relro`, `-z now`, `-z noexecstack` |
| Dead code elimination | `--gc-sections`, `--icf=all` |
| External dependencies | **None** |

---

## Dependencies

| Dependency | Purpose |
|---|---|
| `androidx.core:core-ktx` | `NotificationCompat`, `startForegroundService` |
| `kotlinx-coroutines-android` | `CoroutineScope`, `Semaphore`, `Flow`, `StateFlow` |
| *(C++) Android system `liblog`* | Logging only |
| *(C++) POSIX* | `open`, `write`, `fsync`, `rename`, `stat` |

No OkHttp, no Retrofit, no Ktor, no libcurl, no libsodium.
