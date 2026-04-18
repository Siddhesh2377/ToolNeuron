# ToolNeuron Remote API & Web UI Change Log

## [1.2.0] - 2026-04-18
### Added
- **Built-in Web UI**: A responsive, browser-based interface served directly from the root URL (`/`).
    - **Chat Studio**: Support for multi-turn conversations with real-time streaming.
    - **Image Studio**: Dedicated interface for Stable Diffusion image generation with resolution controls and session gallery.
    - **System Dashboard**: Real-time monitoring of hardware (CPU/RAM) and in-memory model status.
- **Enhanced Remote API (v1)**:
    - `GET /`: Serves the new Web UI assets.
    - `GET /api/ps`: New monitoring endpoint providing detailed hardware and running model metadata.
    - `GET /v1/chat-models`: Filtered endpoint for text-generation models.
    - `GET /v1/image-models`: Filtered endpoint for diffusion models.
    - Full OpenAI-compliant streaming for Chat Completions using `chat.completion.chunk` format.
- **UI Indicators**: Prominent, centered "Remote API" processing dialog (60% screen size) with contextual icons for different request types.

### Changed
- **Startup Optimization**: The app now skips automatic model loading/offering on startup if the Remote API is enabled, preserving system resources for incoming network requests.
- **Dynamic Loading Stability**: Implemented thread-safe `Mutex` locking and idempotency checks in both GGUF and Diffusion engines to prevent race conditions and redundant loads.
- **Error Reporting**: Overhauled the error propagation stack to provide highly detailed technical reasons (e.g., OOM, JNI errors, File missing) during model loading failures.
- **API Standard Compliance**: Switched to `explicitNulls = false` and introduced `ChatCompletionDelta` to strictly match OpenAI's JSON formatting expectations.

### Fixed
- **Startup Crashes**: Resolved a race condition where Hilt injected `ModelRepository` before UMS/Vault initialization.
- **Package Mismatches**: Standardized package naming to `viewmodel` (lowercase) across the project to fix cross-platform compilation and import issues.
- **Dependency Issues**: Added missing `lifecycle-runtime-compose` and `ktor-server-cors` libraries.
- **Lock-up Issues**: Fixed a deadlock between engine initialization and API-triggered dynamic model loading.

## [1.1.0] - 2026-04-15
### Added
- Initial **Remote API** implementation with OpenAI-compatible endpoints for `/v1/chat/completions`, `/v1/models`, and `/v1/images/generations`.
- **Remote API Settings**: Toggle switch, port configuration, and dynamic IP discovery display in the main settings dialog.
- **Dynamic Model Loading**: Support for switching both text and image models via the `model` parameter in API requests.
- **Base Metrics**: Initial support for tracking token usage (prompt/completion) in API responses.
