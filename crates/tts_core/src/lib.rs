use std::{
    collections::HashMap,
    f32::consts::PI,
    sync::Arc,
    thread,
    time::{Duration, Instant},
};

use anyhow::Result;
use async_channel::{unbounded, Receiver};
use instrumentation::{emit_log, LogContext};
use rand::Rng;
use regex::Regex;
use serde::{Deserialize, Serialize};
use thiserror::Error;
use tracing::Level;

const SAMPLE_RATE: u32 = 22_050;
const PHONEME_DURATION_MS: u64 = 120;

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct VoiceConfig {
    pub voice: String,
    pub speed_multiplier: f32,
    pub gain: f32,
}

impl Default for VoiceConfig {
    fn default() -> Self {
        Self {
            voice: "copper-default".into(),
            speed_multiplier: 1.0,
            gain: 0.9,
        }
    }
}

#[derive(Debug, Clone, Serialize, Deserialize, PartialEq)]
pub struct SpeechSegment {
    pub text: String,
    pub estimated_duration: Duration,
}

#[derive(Debug, Clone)]
pub struct AudioChunk {
    pub pcm: Vec<f32>,
    pub sample_rate: u32,
    pub segment: SpeechSegment,
}

#[derive(Debug, Error)]
pub enum TtsError {
    #[error("empty text segment")]
    EmptySegment,
}

#[derive(Debug)]
pub struct TtsEngine {
    config: VoiceConfig,
    phoneme_map: Arc<HashMap<char, &'static str>>,
    freq_map: Arc<HashMap<&'static str, f32>>,
}

impl TtsEngine {
    pub fn new(config: VoiceConfig) -> Self {
        Self {
            config,
            phoneme_map: Arc::new(build_phoneme_map()),
            freq_map: Arc::new(build_frequency_map()),
        }
    }

    pub fn segment_tokens(&self, token_rx: Receiver<String>) -> Receiver<SpeechSegment> {
        let (tx, rx) = unbounded();
        thread::spawn(move || {
            let mut buffer = String::new();
            let mut last_emit = Instant::now();
            let punctuation = Regex::new(r"[.!?]\s*$").unwrap();
            while let Ok(token) = token_rx.recv_blocking() {
                if !buffer.is_empty() {
                    buffer.push(' ');
                }
                buffer.push_str(&token);
                let elapsed = last_emit.elapsed();
                if buffer.len() > 80
                    || punctuation.is_match(buffer.as_str())
                    || elapsed >= Duration::from_secs(3)
                {
                    let segment_text = buffer.trim().to_string();
                    if !segment_text.is_empty() {
                        let duration = estimate_duration(&segment_text);
                        if tx
                            .send_blocking(SpeechSegment {
                                text: segment_text,
                                estimated_duration: duration,
                            })
                            .is_err()
                        {
                            break;
                        }
                    }
                    buffer.clear();
                    last_emit = Instant::now();
                }
            }
            if !buffer.trim().is_empty() {
                let segment_text = buffer.trim().to_string();
                let duration = estimate_duration(&segment_text);
                let _ = tx.send_blocking(SpeechSegment {
                    text: segment_text,
                    estimated_duration: duration,
                });
            }
        });
        rx
    }

    pub fn speak(&self, segments: Receiver<SpeechSegment>) -> Receiver<Result<AudioChunk>> {
        let (tx, rx) = unbounded();
        let config = self.config.clone();
        let phoneme_map = Arc::clone(&self.phoneme_map);
        let freq_map = Arc::clone(&self.freq_map);
        thread::spawn(move || {
            while let Ok(segment) = segments.recv_blocking() {
                if segment.text.trim().is_empty() {
                    let _ = tx.send_blocking(Err(TtsError::EmptySegment.into()));
                    continue;
                }
                emit_log(
                    file!(),
                    line!(),
                    "tts_core::TtsEngine::speak",
                    &format!(
                        "rendering segment '{}...'",
                        &segment.text.chars().take(32).collect::<String>()
                    ),
                    LogContext {
                        level: Level::INFO,
                        ..Default::default()
                    },
                );
                match synthesise_segment(&segment, &config, &phoneme_map, &freq_map) {
                    Ok(chunk) => {
                        if tx.send_blocking(Ok(chunk)).is_err() {
                            break;
                        }
                    }
                    Err(err) => {
                        let _ = tx.send_blocking(Err(err));
                    }
                }
            }
        });
        rx
    }
}

fn estimate_duration(text: &str) -> Duration {
    let base = text.split_whitespace().count() as u64 * PHONEME_DURATION_MS;
    Duration::from_millis(base.max(PHONEME_DURATION_MS))
}

fn build_phoneme_map() -> HashMap<char, &'static str> {
    [
        ('a', "AA"),
        ('b', "B"),
        ('c', "K"),
        ('d', "D"),
        ('e', "EH"),
        ('f', "F"),
        ('g', "G"),
        ('h', "HH"),
        ('i', "IH"),
        ('j', "JH"),
        ('k', "K"),
        ('l', "L"),
        ('m', "M"),
        ('n', "N"),
        ('o', "OW"),
        ('p', "P"),
        ('q', "K"),
        ('r', "R"),
        ('s', "S"),
        ('t', "T"),
        ('u', "UW"),
        ('v', "V"),
        ('w', "W"),
        ('x', "KS"),
        ('y', "Y"),
        ('z', "Z"),
        (' ', "SP"),
    ]
    .into_iter()
    .collect()
}

fn build_frequency_map() -> HashMap<&'static str, f32> {
    [
        ("AA", 220.0),
        ("B", 120.0),
        ("K", 180.0),
        ("D", 160.0),
        ("EH", 240.0),
        ("F", 190.0),
        ("G", 170.0),
        ("HH", 200.0),
        ("IH", 250.0),
        ("JH", 210.0),
        ("L", 140.0),
        ("M", 130.0),
        ("N", 150.0),
        ("OW", 260.0),
        ("P", 155.0),
        ("R", 145.0),
        ("S", 300.0),
        ("T", 310.0),
        ("UW", 230.0),
        ("V", 205.0),
        ("W", 195.0),
        ("KS", 320.0),
        ("Y", 215.0),
        ("Z", 305.0),
        ("SP", 0.0),
    ]
    .into_iter()
    .collect()
}

fn synthesise_segment(
    segment: &SpeechSegment,
    config: &VoiceConfig,
    phoneme_map: &HashMap<char, &'static str>,
    freq_map: &HashMap<&'static str, f32>,
) -> Result<AudioChunk> {
    let mut rng = rand::thread_rng();
    let mut pcm = Vec::new();
    for ch in segment.text.to_lowercase().chars() {
        let phoneme = phoneme_map.get(&ch).cloned().unwrap_or("SP");
        let freq = freq_map.get(phoneme).cloned().unwrap_or(0.0);
        let duration_ms = if phoneme == "SP" {
            PHONEME_DURATION_MS / 2
        } else {
            PHONEME_DURATION_MS
        };
        let samples = (SAMPLE_RATE as f32 * (duration_ms as f32 / 1000.0) / config.speed_multiplier)
            .round() as usize;
        for i in 0..samples {
            let sample = if freq == 0.0 {
                0.0
            } else {
                let noise: f32 = rng.gen_range(-0.02..0.02);
                (2.0 * PI * freq * i as f32 / SAMPLE_RATE as f32).sin() * config.gain + noise
            };
            pcm.push(sample);
        }
        // inter-phoneme smoothing
        pcm.push(0.0);
    }
    Ok(AudioChunk {
        pcm,
        sample_rate: SAMPLE_RATE,
        segment: segment.clone(),
    })
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn segments_tokens() {
        let engine = TtsEngine::new(VoiceConfig::default());
        let (tx, rx) = unbounded();
        let segments_rx = engine.segment_tokens(rx);
        tx.send_blocking("Hello".into()).unwrap();
        tx.send_blocking("world.".into()).unwrap();
        drop(tx);
        let segment = segments_rx.recv_blocking().unwrap();
        assert!(segment.text.contains("Hello"));
    }

    #[test]
    fn synthesises_audio() {
        let engine = TtsEngine::new(VoiceConfig::default());
        let (seg_tx, seg_rx) = unbounded();
        seg_tx
            .send_blocking(SpeechSegment {
                text: "Test tone".into(),
                estimated_duration: Duration::from_millis(500),
            })
            .unwrap();
        drop(seg_tx);
        let audio_rx = engine.speak(seg_rx);
        let chunk = audio_rx.recv_blocking().unwrap().unwrap();
        assert!(chunk.pcm.len() > 100);
        assert_eq!(chunk.sample_rate, SAMPLE_RATE);
    }
}
