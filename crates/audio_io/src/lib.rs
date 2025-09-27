use std::{
    fs,
    path::PathBuf,
    sync::{
        atomic::{AtomicBool, Ordering},
        Arc,
    },
};

use anyhow::{Context, Result};
use async_channel::Receiver;
use hound::{SampleFormat, WavSpec, WavWriter};
use instrumentation::{emit_log, LogContext};
use thiserror::Error;
use tracing::Level;

use tts_core::AudioChunk;

#[derive(Debug, Error)]
pub enum AudioError {
    #[error("playback cancelled")]
    Cancelled,
}

#[derive(Debug)]
pub struct AudioSink {
    output_dir: PathBuf,
    cancel_flag: Arc<AtomicBool>,
}

impl AudioSink {
    pub fn new(output_dir: PathBuf) -> Result<Self> {
        fs::create_dir_all(&output_dir)?;
        inspect_audio_device();
        Ok(Self {
            output_dir,
            cancel_flag: Arc::new(AtomicBool::new(false)),
        })
    }

    pub fn cancel(&self) {
        self.cancel_flag.store(true, Ordering::SeqCst);
    }

    pub fn reset(&self) {
        self.cancel_flag.store(false, Ordering::SeqCst);
    }

    pub fn stream_to_disk(&self, audio_rx: Receiver<Result<AudioChunk>>) -> Result<Vec<PathBuf>> {
        let mut written = Vec::new();
        let mut index = 0usize;
        while let Ok(chunk_result) = audio_rx.recv_blocking() {
            if self.cancel_flag.load(Ordering::SeqCst) {
                emit_log(
                    file!(),
                    line!(),
                    "audio_io::AudioSink::stream_to_disk",
                    "playback cancelled mid-stream",
                    LogContext {
                        level: Level::WARN,
                        ..Default::default()
                    },
                );
                return Err(AudioError::Cancelled.into());
            }
            match chunk_result {
                Ok(chunk) => {
                    let path = self.write_wav(&chunk, index)?;
                    written.push(path);
                    index += 1;
                }
                Err(err) => {
                    emit_log(
                        file!(),
                        line!(),
                        "audio_io::AudioSink::stream_to_disk",
                        &format!("skipping audio chunk due to error: {err}"),
                        LogContext {
                            level: Level::ERROR,
                            ..Default::default()
                        },
                    );
                }
            }
        }
        Ok(written)
    }

    fn write_wav(&self, chunk: &AudioChunk, index: usize) -> Result<PathBuf> {
        let filename = format!("segment_{index:03}.wav");
        let path = self.output_dir.join(filename);
        let spec = WavSpec {
            channels: 1,
            sample_rate: chunk.sample_rate,
            bits_per_sample: 32,
            sample_format: SampleFormat::Float,
        };
        let mut writer = WavWriter::create(&path, spec)
            .with_context(|| format!("failed to create wav at {}", path.display()))?;
        for sample in &chunk.pcm {
            writer.write_sample(*sample)?;
        }
        writer.finalize()?;
        emit_log(
            file!(),
            line!(),
            "audio_io::AudioSink::write_wav",
            &format!("wrote {} samples", chunk.pcm.len()),
            LogContext {
                level: Level::INFO,
                ..Default::default()
            },
        );
        Ok(path)
    }
}

fn inspect_audio_device() {
    emit_log(
        file!(),
        line!(),
        "audio_io::inspect_audio_device",
        "device probing disabled in headless environment",
        LogContext {
            level: Level::INFO,
            ..Default::default()
        },
    );
}

#[cfg(test)]
mod tests {
    use super::*;
    use async_channel::unbounded;
    use std::time::Duration;
    use tempfile::tempdir;
    use tts_core::{SpeechSegment, TtsEngine, VoiceConfig};

    #[test]
    fn writes_audio_files() {
        let dir = tempdir().unwrap();
        let sink = AudioSink::new(dir.path().to_path_buf()).unwrap();
        let engine = TtsEngine::new(VoiceConfig::default());
        let (seg_tx, seg_rx) = unbounded();
        seg_tx
            .send_blocking(SpeechSegment {
                text: "audio test".into(),
                estimated_duration: Duration::from_millis(400),
            })
            .unwrap();
        drop(seg_tx);
        let audio_rx = engine.speak(seg_rx);
        let results = sink.stream_to_disk(audio_rx).unwrap();
        assert_eq!(results.len(), 1);
        assert!(results[0].exists());
    }
}
