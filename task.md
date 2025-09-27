# Project Backlog

Tracking Kotlin/Android milestones for the on-device Lil Speaker app.

## Foundation
- [x] Replace Rust workspace with Android Gradle project skeleton.
- [x] Configure Compose Material 3 theme and launcher assets.
- [x] Implement structured logging helper that satisfies the Sherlock Protocol format.

## Chat experience
- [x] Create chat screen with ViewModel-driven state and Compose UI.
- [x] Implement deterministic local summariser for assistant replies.
- [x] Integrate on-device LLM inference (llama.cpp binding or equivalent).
- [x] Hook up real-time token segmentation to the TTS queue.

## Voice pipeline
- [x] Embed Kitten TTS ONNX runtime for fully local speech.
- [x] Provide playback controls, voice selection, and barge-in support.

## Quality & privacy
- [x] Add coroutine-backed unit coverage for the chat pipeline.
- [x] Expand test suite with UI tests and instrumentation coverage.
- [x] Implement in-app privacy controls and telemetry review toggles.

## Status notes
- Current focus: validating on-device inference performance and thermal envelopes.
- Next steps: benchmark presets across target hardware and tune audio latency buffers.
