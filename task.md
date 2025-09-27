# Project Backlog

Tracking epics and stories for the on-device conversational assistant. Use the checkboxes to mark progress as work completes.

## EPIC A — Project Skeleton & Assets
- [ ] **A1. Flutter app skeleton (Riverpod)** — Create the Flutter app shell with feature modules (`llm_core`, `tts_core`, `audio_io`, `chat_ui`, `models_downloader`). Acceptance: Android arm64 build, Riverpod providers compile, blank chat screen loads, minSdk 24.
- [ ] **A2. First-run model downloader** — Download LFM2 GGUF & Kitten ONNX + voices into `getApplicationDocumentsDirectory()`, SHA-256 verify, resumable. Acceptance: license notices, quant selection default Q4_0, downloads succeed, checksums stored.

## EPIC B — LLM (LFM2-2.6B GGUF) On Device
- [ ] **B1. Build & bundle llama.cpp for Android** — Compile llama.cpp as shared lib for arm64 via NDK; expose to Dart FFI. Acceptance: `libllama.so` loads; unit test calls `llama_backend_init()`.
- [ ] **B2. Integrate `llama_cpp_dart`** — Wire managed isolate API for streaming tokens. Acceptance: >4 tok/s streaming with local model; memory <3 GB.
- [ ] **B3. Apply LFM2 chat template & sampling** — Implement ChatML-like prompt formatting with recommended params.
- [ ] **B4. Token stream segmenter for TTS** — Segment streaming text into speakable chunks respecting punctuation & timeouts.
- [ ] **B5. Performance profiles** — Provide Battery/Balanced/Turbo presets with logging of tokens/sec, temps, throttling.
- [ ] **B6. Fallback models** — Support switching to smaller LFM2 variants without restart.

## EPIC C — TTS (Kitten TTS Nano ONNX) On Device
- [ ] **C1. ONNX Runtime integration** — Add ONNX Runtime plugin; load `kitten_tts_nano_v0_2.onnx` with CPU execution provider.
- [ ] **C2. Voices loader (`voices.npz`)** — Load voices map; expose friendly names.
- [ ] **C3. Discover ONNX I/O contract** — Document ONNX inputs/outputs and preprocessing/postprocessing.
- [ ] **C4. Text → phonemes on device** — Implement offline grapheme-to-phoneme matching Python baseline.
- [ ] **C5. Inference wrapper & streaming playback** — Build `TtsEngine.speak` streaming Float32 PCM into audio playback.
- [ ] **C6. Barge-in & cancel** — Support stopping playback quickly and restarting cleanly.

## EPIC D — Wave Visualizer
- [ ] **D1. Waveform + spectrum view** — Render waveform and optional FFT bars at 60 fps with <5% CPU overhead.

## EPIC E — Chat UI & Session Management
- [ ] **E1. Streaming chat bubbles** — Display live token streaming, sentence-to-TTS queue, retries on failure.
- [ ] **E2. Voice & speed controls** — Adjust voice, speaking rate, gain, persisting per conversation.

## EPIC F — Packaging, Privacy, QA
- [ ] **F1. Storage & updates** — Cache models with semantic version checks and user-approved updates.
- [ ] **F2. Privacy sandbox** — Ensure offline-first behavior, redacted crash logs, opt-in diagnostics.
- [ ] **F3. Bench & thermal tests** — Profile tokens/sec, latency, thermals staying <42 °C over 10-minute chats across devices.

## Status Notes
- Current focus: documentation groundwork (task tracker, UI direction).
- Next candidate tasks: design guidelines for glassmorphic UI, dependency research for llama.cpp and ONNX Runtime mobile.
