use std::{
    path::PathBuf,
    sync::{Arc, Mutex},
    thread,
};

use anyhow::Result;
use async_channel::unbounded;
use instrumentation::{emit_log, LogContext};
use serde::{Deserialize, Serialize};
use tracing::Level;

use llm_core::{ChatMessage, ChatRole, LlmConfig, LlmEngine, PerformanceProfile};
use tts_core::{SpeechSegment, TtsEngine, VoiceConfig};

use audio_io::AudioSink;

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct SessionConfig {
    pub system_prompt: String,
    pub voice: VoiceConfig,
    pub profile: PerformanceProfile,
    pub corpus: Option<PathBuf>,
}

impl Default for SessionConfig {
    fn default() -> Self {
        Self {
            system_prompt: "You are LFM2, an offline-first assistant. Provide concise and actionable responses.".into(),
            voice: VoiceConfig::default(),
            profile: PerformanceProfile::Balanced,
            corpus: None,
        }
    }
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct ConversationArtifacts {
    pub transcript: String,
    pub tokens: Vec<String>,
    pub audio_files: Vec<PathBuf>,
    pub segments: Vec<SpeechSegment>,
}

pub struct ChatController {
    engine: LlmEngine,
    tts: TtsEngine,
}

impl ChatController {
    pub fn new(config: SessionConfig) -> Result<Self> {
        let SessionConfig {
            system_prompt,
            voice,
            profile,
            corpus,
        } = config;
        let engine = LlmEngine::from_config(LlmConfig {
            system_prompt,
            profile,
            corpus,
        })?;
        let tts = TtsEngine::new(voice);
        Ok(Self { engine, tts })
    }

    pub fn run(&self, prompt: &str, audio_sink: &AudioSink) -> Result<ConversationArtifacts> {
        emit_log(
            file!(),
            line!(),
            "chat_ui::ChatController::run",
            &format!("starting chat with prompt: {prompt}"),
            LogContext {
                level: Level::INFO,
                ..Default::default()
            },
        );
        let history = vec![ChatMessage {
            role: ChatRole::User,
            content: prompt.to_string(),
        }];
        let llm_rx = self.engine.stream_chat(&history)?;
        let (token_tx, token_rx) = unbounded();
        let tokens = Arc::new(Mutex::new(Vec::new()));
        let tokens_clone = Arc::clone(&tokens);
        thread::spawn(move || {
            while let Ok(token) = llm_rx.recv_blocking() {
                if token_tx.send_blocking(token.clone()).is_err() {
                    break;
                }
                if let Ok(mut guard) = tokens_clone.lock() {
                    guard.push(token);
                }
            }
            drop(token_tx);
        });

        let segments_rx = self.tts.segment_tokens(token_rx);
        let segments_collector = Arc::new(Mutex::new(Vec::new()));
        let collector_clone = Arc::clone(&segments_collector);
        let (seg_tap_tx, seg_tap_rx) = unbounded();
        thread::spawn(move || {
            while let Ok(segment) = segments_rx.recv_blocking() {
                if seg_tap_tx.send_blocking(segment.clone()).is_err() {
                    break;
                }
                if let Ok(mut guard) = collector_clone.lock() {
                    guard.push(segment);
                }
            }
            drop(seg_tap_tx);
        });

        let audio_rx = self.tts.speak(seg_tap_rx);
        let audio_files = audio_sink.stream_to_disk(audio_rx)?;

        let final_tokens = tokens.lock().unwrap().clone();
        let segments = segments_collector.lock().unwrap().clone();
        let transcript = final_tokens.join(" ");
        Ok(ConversationArtifacts {
            transcript,
            tokens: final_tokens,
            audio_files,
            segments,
        })
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use audio_io::AudioSink;
    use tempfile::tempdir;

    #[test]
    fn orchestrates_chat() {
        let dir = tempdir().unwrap();
        let sink = AudioSink::new(dir.path().to_path_buf()).unwrap();
        let controller = ChatController::new(SessionConfig::default()).unwrap();
        let artifacts = controller.run("Explain offline privacy.", &sink).unwrap();
        assert!(!artifacts.transcript.is_empty());
        assert!(!artifacts.tokens.is_empty());
        assert!(!artifacts.audio_files.is_empty());
        assert!(!artifacts.segments.is_empty());
    }
}
