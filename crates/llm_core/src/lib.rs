use std::{collections::HashMap, fs, path::PathBuf, thread, time::Duration};

use anyhow::{anyhow, Context, Result};
use async_channel::{unbounded, Receiver};
use instrumentation::{emit_log, LogContext};
use once_cell::sync::Lazy;
use rand::seq::SliceRandom;
use rand::thread_rng;
use regex::Regex;
use serde::{Deserialize, Serialize};
use tracing::Level;

static CORPUS_PATH: &str = "assets/lfm2_prompt_corpus.txt";

static TOKEN_PATTERN: Lazy<Regex> = Lazy::new(|| Regex::new(r"[A-Za-z0-9']+").unwrap());

#[derive(Debug, Clone, Serialize, Deserialize, PartialEq, Eq)]
pub enum ChatRole {
    System,
    User,
    Assistant,
}

#[derive(Debug, Clone, Serialize, Deserialize, PartialEq, Eq)]
pub struct ChatMessage {
    pub role: ChatRole,
    pub content: String,
}

#[derive(Debug, Clone)]
pub struct ChatTemplate {
    system_prompt: String,
}

impl ChatTemplate {
    pub fn new(system_prompt: impl Into<String>) -> Self {
        Self {
            system_prompt: system_prompt.into(),
        }
    }

    pub fn render(&self, history: &[ChatMessage]) -> String {
        let mut buffer = String::new();
        buffer.push_str("<|system|>\n");
        buffer.push_str(&self.system_prompt);
        buffer.push_str("\n<|end|>\n");
        for message in history {
            let role_tag = match message.role {
                ChatRole::System => "system",
                ChatRole::User => "user",
                ChatRole::Assistant => "assistant",
            };
            buffer.push_str(&format!(
                "<|{role_tag}|>\n{}\n<|end|>\n",
                message.content.trim()
            ));
        }
        buffer.push_str("<|assistant|>\n");
        buffer
    }
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct SamplingParams {
    pub max_tokens: usize,
    pub delay_per_token_ms: u64,
    pub temperature: f32,
    pub top_p: f32,
}

impl Default for SamplingParams {
    fn default() -> Self {
        Self {
            max_tokens: 160,
            delay_per_token_ms: 150,
            temperature: 0.7,
            top_p: 0.9,
        }
    }
}

#[derive(Debug, Clone, Copy, Serialize, Deserialize)]
pub enum PerformanceProfile {
    Battery,
    Balanced,
    Turbo,
}

impl PerformanceProfile {
    fn params(&self) -> SamplingParams {
        match self {
            PerformanceProfile::Battery => SamplingParams {
                max_tokens: 120,
                delay_per_token_ms: 240,
                temperature: 0.6,
                top_p: 0.85,
            },
            PerformanceProfile::Balanced => SamplingParams::default(),
            PerformanceProfile::Turbo => SamplingParams {
                max_tokens: 220,
                delay_per_token_ms: 80,
                temperature: 0.95,
                top_p: 0.98,
            },
        }
    }
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct LlmConfig {
    pub system_prompt: String,
    pub profile: PerformanceProfile,
    pub corpus: Option<PathBuf>,
}

impl Default for LlmConfig {
    fn default() -> Self {
        Self {
            system_prompt: "You are LFM2, an offline-first assistant.".into(),
            profile: PerformanceProfile::Balanced,
            corpus: None,
        }
    }
}

#[derive(Debug)]
struct BigramModel {
    transitions: HashMap<Option<String>, Vec<String>>,
}

impl BigramModel {
    fn load_from_corpus(path: PathBuf) -> Result<Self> {
        let data = fs::read_to_string(&path)
            .with_context(|| format!("failed to load corpus from {}", path.display()))?;
        let mut transitions: HashMap<Option<String>, Vec<String>> = HashMap::new();
        let mut previous: Option<String> = None;
        for token in TOKEN_PATTERN.find_iter(&data) {
            let token = token.as_str().to_lowercase();
            transitions
                .entry(previous.clone())
                .or_default()
                .push(token.clone());
            previous = Some(token);
        }
        Ok(Self { transitions })
    }

    fn sample_next(&self, previous: &Option<String>, params: &SamplingParams) -> Option<String> {
        let mut rng = thread_rng();
        let mut choices = self
            .transitions
            .get(previous)
            .or_else(|| self.transitions.get(&None))?
            .clone();
        if choices.is_empty() {
            return None;
        }
        choices.shuffle(&mut rng);
        let limit = ((choices.len() as f32) * params.top_p).ceil() as usize;
        let limited = &choices[..limit.min(choices.len())];
        limited.choose(&mut rng).cloned()
    }

    fn generate(&self, prompt: &str, params: &SamplingParams) -> Vec<String> {
        let mut tokens = Vec::with_capacity(params.max_tokens);
        let mut previous = prompt.split_whitespace().last().map(|s| {
            s.trim_matches(|c: char| !c.is_alphanumeric())
                .to_lowercase()
        });
        for _ in 0..params.max_tokens {
            let next = match self.sample_next(&previous, params) {
                Some(token) => token,
                None => break,
            };
            previous = Some(next.clone());
            tokens.push(next);
        }
        tokens
    }
}

#[derive(Debug)]
pub struct LlmEngine {
    template: ChatTemplate,
    bigram: BigramModel,
    params: SamplingParams,
    profile: PerformanceProfile,
}

impl LlmEngine {
    pub fn new(system_prompt: impl Into<String>) -> Result<Self> {
        Self::from_config(LlmConfig {
            system_prompt: system_prompt.into(),
            profile: PerformanceProfile::Balanced,
            corpus: None,
        })
    }

    pub fn from_config(config: LlmConfig) -> Result<Self> {
        let system_prompt = config.system_prompt;
        let profile = config.profile;
        let corpus_path = config
            .corpus
            .unwrap_or_else(|| PathBuf::from(env!("CARGO_MANIFEST_DIR")).join(CORPUS_PATH));
        let bigram = BigramModel::load_from_corpus(corpus_path)?;
        emit_log(
            file!(),
            line!(),
            "llm_core::LlmEngine::new",
            "initialised bigram language model",
            LogContext {
                level: Level::INFO,
                ..Default::default()
            },
        );
        Ok(Self {
            template: ChatTemplate::new(system_prompt),
            bigram,
            params: profile.params(),
            profile,
        })
    }

    pub fn stream_chat(&self, history: &[ChatMessage]) -> Result<Receiver<String>> {
        let prompt = self.template.render(history);
        let tokens = self.bigram.generate(&prompt, &self.params);
        if tokens.is_empty() {
            return Err(anyhow!("model returned no tokens"));
        }
        let (tx, rx) = unbounded();
        let delay = Duration::from_millis(self.params.delay_per_token_ms);
        emit_log(
            file!(),
            line!(),
            "llm_core::LlmEngine::stream_chat",
            &format!(
                "streaming {} tokens under {:?} profile",
                tokens.len(),
                self.profile
            ),
            LogContext {
                level: Level::INFO,
                ..Default::default()
            },
        );
        let profile = self.profile;
        thread::spawn(move || {
            let start = std::time::Instant::now();
            let mut emitted = 0usize;
            for token in tokens {
                if tx.send_blocking(token.clone()).is_err() {
                    break;
                }
                emitted += 1;
                thread::sleep(delay);
            }
            let elapsed = start.elapsed().as_secs_f32().max(0.001);
            let tokens_per_sec = emitted as f32 / elapsed;
            emit_log(
                file!(),
                line!(),
                "llm_core::LlmEngine::stream_chat",
                &format!("profile {:?} emitted {emitted} tokens in {elapsed:.2}s ({tokens_per_sec:.2} tok/s)", profile),
                LogContext { level: Level::INFO, ..Default::default() },
            );
        });
        Ok(rx)
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn renders_chat_template() {
        let template = ChatTemplate::new("system prompt");
        let rendered = template.render(&[
            ChatMessage {
                role: ChatRole::User,
                content: "Hello".into(),
            },
            ChatMessage {
                role: ChatRole::Assistant,
                content: "Hi".into(),
            },
        ]);
        assert!(rendered.contains("<|system|>"));
        assert!(rendered.contains("<|user|>"));
        assert!(rendered.contains("<|assistant|>"));
    }

    #[test]
    fn generates_tokens() {
        let engine = LlmEngine::new("You are LFM2, an on-device assistant.").unwrap();
        let history = vec![ChatMessage {
            role: ChatRole::User,
            content: "Test prompt".into(),
        }];
        let rx = engine.stream_chat(&history).unwrap();
        let mut count = 0;
        while count < 5 {
            let token = rx.recv_blocking().unwrap();
            assert!(!token.is_empty());
            count += 1;
        }
    }
}
