# Lil Speaker (Android)

Lil Speaker is now a fully native Android application written in Kotlin and Jetpack Compose. The app provisions the full on-device assistant stack: it downloads and runs the Liquid AI LFM2 2.6B GGUF model through a bundled `llama.cpp` bridge, segments the streamed tokens for Kitten TTS Nano ONNX playback, and exposes privacy-first controls that honour the Sherlock Protocol logging contract.

## Tech stack

- **Android**: Gradle 8.4, Android Gradle Plugin 8.1, Kotlin 1.9, minSdk 24, targetSdk 34
- **UI**: Jetpack Compose Material 3 with adaptive color support
- **State**: `ViewModel` + `StateFlow` coordinate the assistant pipeline
- **Coroutines**: Kotlin Coroutines orchestrate LLM streaming, downloads, and audio synthesis
- **Logging**: Structured JSON logs with the required `Continuous skepticism (Sherlock Protocol)` line appended to every entry
- **Native**: `llama.cpp` compiled at build time via CMake/NDK for GGUF inference
- **Inference runtimes**: ONNX Runtime Mobile drives the Kitten TTS Nano 0.2 voice model

## Features

- **Composable chat surface** — Streaming-friendly layout with a bottom composer, live assistant bubble, and playback controls.
- **On-device LLM** — LFM2 2.6B (GGUF) streaming via native `llama.cpp` with ChatML formatting and performance presets.
- **Segmented voice pipeline** — Token segmenter feeds Kitten TTS Nano 0.2 over ONNX Runtime into a low-latency AudioTrack player with barge-in support.
- **Model management** — Resumable downloads with SHA-256 verification for GGUF, ONNX, and voice assets stored under app-private storage.
- **Privacy-first controls** — In-app telemetry and diagnostics toggles backed by Jetpack DataStore and surfaced in the top app bar.
- **Test coverage** — Unit suites for segmentation and phoneme encoding plus Compose instrumentation tests for UI affordances.

## Getting started

1. Install the Android SDK (API 34) and ensure `JAVA_HOME` points to JDK 17.
2. Provide NDK r26c or newer so Gradle can compile the bundled `llama.cpp` bridge during the build.
3. From the repository root run:
   ```bash
   ./gradlew test
   ```
4. To launch the debug build on a device or emulator (models download on first run):
   ```bash
   ./gradlew installDebug
   ```

## Project layout

```
app/
  src/main/java/com/example/lilspeaker/
    core/logging/    -> Canonical logging helper
    features/assistant/ -> LLM+TTS orchestration pipeline
    features/chat/   -> ViewModel, state, and Compose UI
    features/download/ -> Model downloader with checksum verification
    features/llm/    -> llama.cpp bridge and prompt formatter
    features/privacy/ -> DataStore-backed telemetry controls
    features/tts/    -> Token segmenter, Kitten ONNX runtime, AudioTrack playback
    ui/theme/        -> Material theme definitions
    LilSpeakerApp.kt -> Application entry point
    MainActivity.kt  -> Compose host activity
```

## Logging contract

Each log entry follows the JSON schema:

```json
{
  "filename": "ChatViewModel",
  "timestamp": "2024-01-01T12:00:00.000Z",
  "classname": "ChatViewModel",
  "function": "sendMessage",
  "system_section": "chat_pipeline",
  "line_num": 42,
  "error": "",
  "db_phase": "none",
  "method": "NONE",
  "message": "User submitted message"
}
```

Immediately after the JSON payload the logger prints the human-readable line exactly as required:

```
[Continuous skepticism (Sherlock Protocol)] User submitted message
```

## Continuous skepticism (Sherlock Protocol)

Before merging any feature, double check:

1. Could the change affect unexpected files or systems?
2. Are there hidden dependencies or cascades?
3. What edge cases and failure modes are unhandled?
4. If stuck, work backward from the desired outcome.
