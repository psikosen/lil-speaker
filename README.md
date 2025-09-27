# lil-speaker

Lil Speaker is a Rust workspace that simulates an on-device conversational assistant stack. It includes modular crates for model downloading, LLM token streaming, text-to-speech synthesis, audio output, and a CLI orchestrator that ties the pieces together with structured logging.

## Features

- **Model management** — resumable downloads with SHA-256 verification and license notices for required model artifacts.
- **LLM bigram engine** — ChatML-inspired templating, streaming tokens, and performance profiles (Battery/Balanced/Turbo) with telemetry.
- **Token segmentation & TTS** — grapheme-to-phoneme mapping, sine-wave synthesis, and barge-in ready audio queueing.
- **Audio pipeline** — Stream audio chunks into timestamped WAV files with cancellation support.
- **Command-line orchestrator** — Runs the full conversation loop, selects model variants, and exposes environment knobs for voice and performance tuning.
- **Observability** — Canonical JSON logs that include the Sherlock Protocol human-readable line for every event.

## Getting started

### Prerequisites

- Rust toolchain (tested with 1.82+)
- Internet access for the initial asset download executed by the CLI

### Build & test

```bash
cargo fmt
cargo clippy --all-targets -- -D warnings
cargo test
```

### Running the assistant

```bash
cargo run -p app -- "Explain offline-first privacy guidance"
```

Optional environment variables:

- `LIL_SPEAKER_PROFILE` — `battery`, `balanced` (default), or `turbo`.
- `LIL_SPEAKER_MODEL` — `lfm2-q4_0` (default) or `lfm2-q2_k` to switch variants.
- `LIL_SPEAKER_VOICE` — custom voice identifier string.
- `LIL_SPEAKER_SPEED` — float multiplier (0.5–2.0) for speech rate.
- `LIL_SPEAKER_GAIN` — float gain (0.1–1.2) for output amplitude.

Generated audio segments are stored under the platform data directory (e.g., `~/.local/share/lil-speaker/audio_sessions`).

## Required assets

| Asset ID        | Filename                     | Source URL                                                                 | SHA-256                                                             | Notes                              |
|-----------------|------------------------------|---------------------------------------------------------------------------|---------------------------------------------------------------------|------------------------------------|
| `lfm2-q4_0`     | `lfm2_q4_0.gguf`             | https://huggingface.co/datasets/hf-internal-testing/fixtures/resolve/main/hello.txt | `45b71fe98efe5f530b825dce6f5049d738e9c16869f10be4370ab81a9912d4a6` | Default LFM2 prompt corpus stub    |
| `lfm2-q2_k`     | `lfm2_q2_k.gguf`             | https://huggingface.co/datasets/hf-internal-testing/fixtures/resolve/main/hello.txt | `45b71fe98efe5f530b825dce6f5049d738e9c16869f10be4370ab81a9912d4a6` | Memory-constrained fallback stub   |
| `kitten-tts-nano` | `kitten_tts_nano_v0_2.onnx` | https://github.com/onnx/models/raw/main/vision/classification/mnist/model/mnist-8.onnx?download=1 | `847cc4343bf3665bac366061f9271516bca9f8f73ea18a75c5575b9616a26337` | ONNX sample used for TTS pipeline  |
| `voices-map`    | `voices.npz`                 | https://github.com/scipy/scipy/raw/main/doc/source/tutorial/data/face.npz | `e825bb68da30c60d886f57e20ba8920320a64c666676c42f6a80dd36e56b665b` | Voice palette sample data          |

All assets are downloaded to the platform data directory and accompanied by license notices under `licenses/`.

## Project layout

```
apps/app                 CLI entrypoint
crates/audio_io          Audio sink & WAV streaming
crates/chat_ui           Conversation controller & streaming UI logic
crates/instrumentation   Structured logging helpers
crates/llm_core          Bigram LLM engine with profiles
crates/models_downloader Asset downloader and manifest manager
crates/tts_core          Token segmentation and TTS synthesis
```

## Logging

Logs are emitted in JSON with the canonical schema, and each entry contains the Sherlock Protocol note. Set `RUST_LOG=debug` to increase verbosity.

## Continuous skepticism (Sherlock Protocol)

When extending the system, evaluate:

1. Could the change affect unexpected files or systems?
2. Are there hidden dependencies or cascades?
3. What edge cases and failure modes remain unhandled?
4. If stuck, work backward from the desired outcome.

