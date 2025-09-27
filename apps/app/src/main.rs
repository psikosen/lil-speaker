use std::env;

use anyhow::Result;
use audio_io::AudioSink;
use chat_ui::{ChatController, SessionConfig};
use instrumentation::{emit_log, init_tracing, LogContext};
use llm_core::PerformanceProfile;
use models_downloader::{ModelAsset, ModelDownloader};
use std::collections::HashMap;
use tracing::Level;

fn main() -> Result<()> {
    init_tracing()?;
    let prompt = parse_prompt();
    emit_log(
        file!(),
        line!(),
        "app::main",
        &format!("starting session with prompt length {}", prompt.len()),
        LogContext {
            level: Level::INFO,
            ..Default::default()
        },
    );
    let downloader = ModelDownloader::new(None)?;
    let assets = default_assets();
    let download_paths = downloader.download_all(&assets)?;
    let path_map: HashMap<_, _> = assets
        .iter()
        .zip(download_paths.iter())
        .map(|(asset, path)| (asset.id.clone(), path.clone()))
        .collect();
    for (asset, path) in assets.iter().zip(download_paths.iter()) {
        emit_log(
            file!(),
            line!(),
            "app::main",
            &format!("asset {} available at {}", asset.id, path.display()),
            LogContext {
                level: Level::INFO,
                ..Default::default()
            },
        );
    }

    let audio_dir = downloader.base_dir().join("audio_sessions");
    let audio_sink = AudioSink::new(audio_dir)?;
    let profile = choose_profile();
    let model_id = choose_model_variant(&assets);
    let corpus_path = path_map.get(model_id).cloned();
    let base = SessionConfig::default();
    let mut voice = base.voice.clone();
    if let Ok(voice_name) = env::var("LIL_SPEAKER_VOICE") {
        voice.voice = voice_name;
    }
    if let Ok(speed) = env::var("LIL_SPEAKER_SPEED") {
        if let Ok(value) = speed.parse::<f32>() {
            voice.speed_multiplier = value.clamp(0.5, 2.0);
        }
    }
    if let Ok(gain) = env::var("LIL_SPEAKER_GAIN") {
        if let Ok(value) = gain.parse::<f32>() {
            voice.gain = value.clamp(0.1, 1.2);
        }
    }
    let session = SessionConfig {
        system_prompt: base.system_prompt,
        voice,
        profile,
        corpus: corpus_path.clone(),
    };
    emit_log(
        file!(),
        line!(),
        "app::main",
        &format!("using model variant {model_id} with profile {:?}", profile),
        LogContext {
            level: Level::INFO,
            ..Default::default()
        },
    );
    if let Some(path) = &corpus_path {
        emit_log(
            file!(),
            line!(),
            "app::main",
            &format!("corpus override at {}", path.display()),
            LogContext {
                level: Level::INFO,
                ..Default::default()
            },
        );
    }
    emit_log(
        file!(),
        line!(),
        "app::main",
        &format!(
            "voice={} speed={:.2} gain={:.2}",
            session.voice.voice, session.voice.speed_multiplier, session.voice.gain
        ),
        LogContext {
            level: Level::INFO,
            ..Default::default()
        },
    );
    let controller = ChatController::new(session)?;
    let artifacts = controller.run(&prompt, &audio_sink)?;

    println!("Assistant response:\n{}", artifacts.transcript);
    println!("Generated {} audio segments:", artifacts.audio_files.len());
    for path in artifacts.audio_files {
        println!(" - {}", path.display());
    }

    Ok(())
}

fn parse_prompt() -> String {
    let args: Vec<String> = env::args().skip(1).collect();
    if args.is_empty() {
        "Describe how to verify checksums and manage offline assets.".into()
    } else {
        args.join(" ")
    }
}

fn default_assets() -> Vec<ModelAsset> {
    vec![
        ModelAsset {
            id: "lfm2-q4_0".into(),
            url: "https://huggingface.co/datasets/hf-internal-testing/fixtures/resolve/main/hello.txt".into(),
            filename: "lfm2_q4_0.gguf".into(),
            sha256: "45b71fe98efe5f530b825dce6f5049d738e9c16869f10be4370ab81a9912d4a6".into(),
            license: "LFM2 model stub - MIT".into(),
            bytes: 29,
            default_variant: Some("Q4_0".into()),
        },
        ModelAsset {
            id: "lfm2-q2_k".into(),
            url: "https://huggingface.co/datasets/hf-internal-testing/fixtures/resolve/main/hello.txt".into(),
            filename: "lfm2_q2_k.gguf".into(),
            sha256: "45b71fe98efe5f530b825dce6f5049d738e9c16869f10be4370ab81a9912d4a6".into(),
            license: "LFM2 compact model stub - MIT".into(),
            bytes: 29,
            default_variant: None,
        },
        ModelAsset {
            id: "kitten-tts-nano".into(),
            url: "https://github.com/onnx/models/raw/main/vision/classification/mnist/model/mnist-8.onnx?download=1".into(),
            filename: "kitten_tts_nano_v0_2.onnx".into(),
            sha256: "847cc4343bf3665bac366061f9271516bca9f8f73ea18a75c5575b9616a26337".into(),
            license: "ONNX model sample - Apache-2.0".into(),
            bytes: 293_000,
            default_variant: None,
        },
        ModelAsset {
            id: "voices-map".into(),
            url: "https://github.com/scipy/scipy/raw/main/doc/source/tutorial/data/face.npz".into(),
            filename: "voices.npz".into(),
            sha256: "e825bb68da30c60d886f57e20ba8920320a64c666676c42f6a80dd36e56b665b".into(),
            license: "SciPy face dataset - BSD-3-Clause".into(),
            bytes: 293_000,
            default_variant: None,
        },
    ]
}

fn choose_profile() -> PerformanceProfile {
    match env::var("LIL_SPEAKER_PROFILE")
        .unwrap_or_default()
        .to_lowercase()
        .as_str()
    {
        "battery" => PerformanceProfile::Battery,
        "turbo" => PerformanceProfile::Turbo,
        _ => PerformanceProfile::Balanced,
    }
}

fn choose_model_variant(assets: &[ModelAsset]) -> &str {
    if let Ok(preferred) = env::var("LIL_SPEAKER_MODEL") {
        if assets.iter().any(|asset| asset.id == preferred) {
            return assets
                .iter()
                .find(|asset| asset.id == preferred)
                .map(|a| a.id.as_str())
                .unwrap();
        }
    }
    assets
        .iter()
        .find(|asset| asset.default_variant.is_some())
        .or_else(|| assets.iter().find(|asset| asset.id.contains("q4")))
        .map(|asset| asset.id.as_str())
        .unwrap_or_else(|| assets[0].id.as_str())
}
