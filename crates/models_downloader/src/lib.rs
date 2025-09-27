use std::{
    collections::HashMap,
    fs::{self, File},
    io::{Read, Seek, SeekFrom, Write},
    path::{Path, PathBuf},
};

use anyhow::{Context, Result};
use dirs::data_dir;
use indicatif::{ProgressBar, ProgressStyle};
use instrumentation::{emit_log, LogContext};
use reqwest::blocking::{Client, Response};
use reqwest::header::{HeaderMap, HeaderValue, RANGE};
use serde::{Deserialize, Serialize};
use sha2::{Digest, Sha256};
use thiserror::Error;
use tracing::Level;
use url::Url;

const MANIFEST_FILE: &str = "downloads.json";
const LICENSE_DIR: &str = "licenses";

#[derive(Debug, Clone, Serialize, Deserialize, PartialEq, Eq)]
pub struct ModelAsset {
    pub id: String,
    pub url: String,
    pub filename: String,
    pub sha256: String,
    pub license: String,
    pub bytes: u64,
    pub default_variant: Option<String>,
}

#[derive(Debug, Clone, Serialize, Deserialize, Default)]
struct DownloadManifest {
    assets: HashMap<String, DownloadRecord>,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
struct DownloadRecord {
    path: PathBuf,
    sha256: String,
    license: String,
    bytes: u64,
    last_verified: chrono::DateTime<chrono::Utc>,
}

#[derive(Debug, Error)]
pub enum DownloaderError {
    #[error("asset URL is invalid: {0}")]
    InvalidUrl(String),
    #[error("checksum mismatch for {0}")]
    ChecksumMismatch(String),
    #[error("missing content length header for {0}")]
    MissingContentLength(String),
}

#[derive(Debug)]
pub struct ModelDownloader {
    base_dir: PathBuf,
    client: Client,
}

impl ModelDownloader {
    pub fn new(custom_dir: Option<PathBuf>) -> Result<Self> {
        let base = if let Some(dir) = custom_dir {
            dir
        } else {
            data_dir()
                .map(|p| p.join("lil-speaker"))
                .unwrap_or_else(|| PathBuf::from("./.lil-speaker"))
        };
        fs::create_dir_all(&base)
            .with_context(|| format!("failed to create model directory at {}", base.display()))?;
        let client = Client::builder().build()?;
        emit_log(
            file!(),
            line!(),
            "models_downloader::new",
            &format!("initialised downloader in {}", base.display()),
            LogContext {
                level: Level::INFO,
                ..Default::default()
            },
        );
        Ok(Self {
            base_dir: base,
            client,
        })
    }

    pub fn base_dir(&self) -> &Path {
        &self.base_dir
    }

    pub fn download_all(&self, assets: &[ModelAsset]) -> Result<Vec<PathBuf>> {
        let mut manifest = self.load_manifest()?;
        let mut results = Vec::with_capacity(assets.len());
        for asset in assets {
            let path = self.download_asset(asset, &mut manifest)?;
            results.push(path);
        }
        self.save_manifest(&manifest)?;
        Ok(results)
    }

    fn manifest_path(&self) -> PathBuf {
        self.base_dir.join(MANIFEST_FILE)
    }

    fn load_manifest(&self) -> Result<DownloadManifest> {
        let manifest_path = self.manifest_path();
        if manifest_path.exists() {
            let contents = fs::read(&manifest_path)
                .with_context(|| format!("failed reading manifest {}", manifest_path.display()))?;
            Ok(serde_json::from_slice(&contents)?)
        } else {
            Ok(DownloadManifest::default())
        }
    }

    fn save_manifest(&self, manifest: &DownloadManifest) -> Result<()> {
        let path = self.manifest_path();
        let serialised = serde_json::to_vec_pretty(manifest)?;
        fs::write(&path, serialised)
            .with_context(|| format!("failed writing manifest {}", path.display()))?;
        Ok(())
    }

    fn ensure_license_notice(&self, asset: &ModelAsset) -> Result<()> {
        let license_dir = self.base_dir.join(LICENSE_DIR);
        fs::create_dir_all(&license_dir)?;
        let notice_path = license_dir.join(format!("{}.txt", asset.id));
        if !notice_path.exists() {
            fs::write(&notice_path, &asset.license)?;
        }
        Ok(())
    }

    fn download_asset(
        &self,
        asset: &ModelAsset,
        manifest: &mut DownloadManifest,
    ) -> Result<PathBuf> {
        let url =
            Url::parse(&asset.url).map_err(|_| DownloaderError::InvalidUrl(asset.url.clone()))?;
        let target_path = self.base_dir.join(&asset.filename);
        if let Some(existing) = manifest.assets.get(&asset.id) {
            if target_path.exists() && existing.sha256 == asset.sha256 {
                emit_log(
                    file!(),
                    line!(),
                    "models_downloader::download_asset",
                    &format!("skipping {} (already verified)", asset.id),
                    LogContext {
                        level: Level::INFO,
                        ..Default::default()
                    },
                );
                return Ok(target_path);
            }
        }

        self.ensure_license_notice(asset)?;

        let mut headers = HeaderMap::new();
        let mut downloaded = 0u64;
        let tmp_path = target_path.with_extension("part");
        if tmp_path.exists() {
            downloaded = tmp_path.metadata()?.len();
            if downloaded > 0 {
                headers.insert(
                    RANGE,
                    HeaderValue::from_str(&format!("bytes={downloaded}-")).unwrap(),
                );
            }
        }

        let total_size = self.fetch_total_size(&url)?;
        let pb = ProgressBar::new(total_size.unwrap_or(asset.bytes));
        pb.set_style(
            ProgressStyle::with_template("{bar:40.cyan/blue} {bytes:>8}/{total_bytes:8} {msg}")
                .unwrap()
                .progress_chars("=>-"),
        );
        if downloaded > 0 {
            pb.set_position(downloaded);
            pb.set_message("resuming");
        }

        let mut response = self
            .client
            .get(url.clone())
            .headers(headers)
            .send()
            .with_context(|| format!("request failed for {}", url))?;

        let expected = total_size.unwrap_or(asset.bytes);
        self.write_response(&mut response, &tmp_path, downloaded, expected, &pb)?;

        pb.finish_with_message("verifying");
        fs::rename(&tmp_path, &target_path)?;
        self.verify_checksum(&target_path, &asset.sha256)?;
        manifest.assets.insert(
            asset.id.clone(),
            DownloadRecord {
                path: target_path.clone(),
                sha256: asset.sha256.clone(),
                license: asset.license.clone(),
                bytes: expected,
                last_verified: chrono::Utc::now(),
            },
        );
        emit_log(
            file!(),
            line!(),
            "models_downloader::download_asset",
            &format!("downloaded {} ({} bytes)", asset.id, expected),
            LogContext {
                level: Level::INFO,
                ..Default::default()
            },
        );
        Ok(target_path)
    }

    fn fetch_total_size(&self, url: &Url) -> Result<Option<u64>> {
        let response = self.client.head(url.clone()).send();
        match response {
            Ok(resp) => Ok(resp
                .headers()
                .get(reqwest::header::CONTENT_LENGTH)
                .and_then(|v| v.to_str().ok())
                .and_then(|s| s.parse::<u64>().ok())),
            Err(err) => {
                emit_log(
                    file!(),
                    line!(),
                    "models_downloader::fetch_total_size",
                    &format!("HEAD request failed for {url}: {err}"),
                    LogContext {
                        level: Level::WARN,
                        ..Default::default()
                    },
                );
                Ok(None)
            }
        }
    }

    fn write_response(
        &self,
        response: &mut Response,
        tmp_path: &Path,
        mut downloaded: u64,
        expected: u64,
        pb: &ProgressBar,
    ) -> Result<()> {
        let mut file = if tmp_path.exists() {
            let mut file = File::options().append(true).open(tmp_path)?;
            file.seek(SeekFrom::End(0))?;
            file
        } else {
            File::create(tmp_path)?
        };

        let mut buffer = [0u8; 8192];
        loop {
            let read = response.read(&mut buffer)?;
            if read == 0 {
                break;
            }
            file.write_all(&buffer[..read])?;
            downloaded += read as u64;
            pb.set_position(downloaded.min(expected));
        }

        file.flush()?;
        Ok(())
    }

    fn verify_checksum(&self, path: &Path, expected: &str) -> Result<()> {
        let mut file = File::open(path)?;
        let mut hasher = Sha256::new();
        let mut buf = [0u8; 8192];
        loop {
            let read = file.read(&mut buf)?;
            if read == 0 {
                break;
            }
            hasher.update(&buf[..read]);
        }
        let digest = format!("{:x}", hasher.finalize());
        if digest != expected {
            return Err(DownloaderError::ChecksumMismatch(path.display().to_string()).into());
        }
        Ok(())
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use std::io::Write;
    use tempfile::tempdir;

    #[test]
    fn downloads_and_verifies_asset() {
        let dir = tempdir().unwrap();
        let asset_path = dir.path().join("asset.bin");
        let mut file = File::create(&asset_path).unwrap();
        write!(file, "test payload").unwrap();
        drop(file);

        let data = fs::read(&asset_path).unwrap();
        let sha256 = format!("{:x}", Sha256::digest(&data));

        let server = httptest::Server::run();
        use httptest::{matchers::request, responders::status_code, Expectation};
        server.expect(
            Expectation::matching(request::method_path("HEAD", "/asset")).respond_with(
                status_code(200).append_header("content-length", data.len().to_string()),
            ),
        );
        server.expect(
            Expectation::matching(request::method_path("GET", "/asset"))
                .respond_with(status_code(200).body(data.clone())),
        );

        let downloader = ModelDownloader::new(Some(dir.path().join("downloads"))).unwrap();
        let asset = ModelAsset {
            id: "test".into(),
            url: server.url_str("/asset"),
            filename: "asset.bin".into(),
            sha256,
            license: "MIT".into(),
            bytes: data.len() as u64,
            default_variant: Some("Q4_0".into()),
        };

        let result = downloader.download_all(&[asset]).unwrap();
        assert_eq!(result.len(), 1);
        assert!(result[0].exists());
    }
}
