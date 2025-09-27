use std::sync::OnceLock;

use anyhow::Result;
use chrono::{DateTime, Utc};
use serde::Serialize;
use serde_json::Value;
use tracing::{event, Level};
use tracing_subscriber::{fmt, EnvFilter};

pub static SHERLOCK_PROMPT: &str = "[Continuous skepticism (Sherlock Protocol)] Could this change affect unexpected files/systems? Any hidden dependencies or cascades? What edge cases and failure modes are unhandled? If stuck, work backward from the desired outcome.";

#[derive(Debug, Default, Serialize)]
pub struct LogEvent<'a> {
    pub filename: &'a str,
    pub timestamp: DateTime<Utc>,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub classname: Option<&'a str>,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub function: Option<&'a str>,
    pub system_section: &'a str,
    pub line_num: u32,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub error: Option<&'a str>,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub db_phase: Option<&'a str>,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub method: Option<&'a str>,
    pub message: &'a str,
    pub sherlock_line: String,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub extra: Option<Value>,
}

#[derive(Debug)]
pub struct LogContext<'a> {
    pub classname: Option<&'a str>,
    pub function: Option<&'a str>,
    pub error: Option<&'a str>,
    pub db_phase: Option<&'a str>,
    pub method: Option<&'a str>,
    pub extra: Option<Value>,
    pub level: Level,
}

impl<'a> LogContext<'a> {
    pub fn level(mut self, level: Level) -> Self {
        self.level = level;
        self
    }
}

impl Default for LogContext<'_> {
    fn default() -> Self {
        Self {
            classname: None,
            function: None,
            error: None,
            db_phase: None,
            method: None,
            extra: None,
            level: Level::INFO,
        }
    }
}

static SUBSCRIBER_GUARD: OnceLock<()> = OnceLock::new();

pub fn init_tracing() -> Result<()> {
    SUBSCRIBER_GUARD.get_or_init(|| {
        let filter = EnvFilter::try_from_default_env().unwrap_or_else(|_| EnvFilter::new("info"));
        fmt()
            .with_env_filter(filter)
            .json()
            .flatten_event(true)
            .with_timer(fmt::time::UtcTime::rfc_3339())
            .init();
    });
    Ok(())
}

pub fn emit_log(
    filename: &str,
    line_num: u32,
    system_section: &str,
    message: &str,
    ctx: LogContext<'_>,
) {
    let level = if ctx.level == Level::ERROR
        || ctx.level == Level::WARN
        || ctx.level == Level::DEBUG
        || ctx.level == Level::TRACE
    {
        ctx.level
    } else {
        Level::INFO
    };

    let event_payload = LogEvent {
        filename,
        timestamp: Utc::now(),
        classname: ctx.classname,
        function: ctx.function,
        system_section,
        line_num,
        error: ctx.error,
        db_phase: ctx.db_phase,
        method: ctx.method,
        message,
        sherlock_line: format!("{SHERLOCK_PROMPT} :: {message}"),
        extra: ctx.extra.clone(),
    };
    match level {
        Level::ERROR => event!(
            Level::ERROR,
            filename = event_payload.filename,
            timestamp = %event_payload.timestamp.to_rfc3339(),
            classname = event_payload.classname.unwrap_or(""),
            function = event_payload.function.unwrap_or(""),
            system_section = event_payload.system_section,
            line_num = event_payload.line_num,
            error = event_payload.error.unwrap_or(""),
            db_phase = event_payload.db_phase.unwrap_or("none"),
            method = event_payload.method.unwrap_or("NONE"),
            message = event_payload.message,
            sherlock_line = event_payload.sherlock_line.as_str(),
            extra = event_payload.extra.as_ref().map(|v| v.to_string()).unwrap_or_else(|| "{}".to_string())
        ),
        Level::WARN => event!(
            Level::WARN,
            filename = event_payload.filename,
            timestamp = %event_payload.timestamp.to_rfc3339(),
            classname = event_payload.classname.unwrap_or(""),
            function = event_payload.function.unwrap_or(""),
            system_section = event_payload.system_section,
            line_num = event_payload.line_num,
            error = event_payload.error.unwrap_or(""),
            db_phase = event_payload.db_phase.unwrap_or("none"),
            method = event_payload.method.unwrap_or("NONE"),
            message = event_payload.message,
            sherlock_line = event_payload.sherlock_line.as_str(),
            extra = event_payload.extra.as_ref().map(|v| v.to_string()).unwrap_or_else(|| "{}".to_string())
        ),
        Level::INFO => event!(
            Level::INFO,
            filename = event_payload.filename,
            timestamp = %event_payload.timestamp.to_rfc3339(),
            classname = event_payload.classname.unwrap_or(""),
            function = event_payload.function.unwrap_or(""),
            system_section = event_payload.system_section,
            line_num = event_payload.line_num,
            error = event_payload.error.unwrap_or(""),
            db_phase = event_payload.db_phase.unwrap_or("none"),
            method = event_payload.method.unwrap_or("NONE"),
            message = event_payload.message,
            sherlock_line = event_payload.sherlock_line.as_str(),
            extra = event_payload.extra.as_ref().map(|v| v.to_string()).unwrap_or_else(|| "{}".to_string())
        ),
        Level::DEBUG => event!(
            Level::DEBUG,
            filename = event_payload.filename,
            timestamp = %event_payload.timestamp.to_rfc3339(),
            classname = event_payload.classname.unwrap_or(""),
            function = event_payload.function.unwrap_or(""),
            system_section = event_payload.system_section,
            line_num = event_payload.line_num,
            error = event_payload.error.unwrap_or(""),
            db_phase = event_payload.db_phase.unwrap_or("none"),
            method = event_payload.method.unwrap_or("NONE"),
            message = event_payload.message,
            sherlock_line = event_payload.sherlock_line.as_str(),
            extra = event_payload.extra.as_ref().map(|v| v.to_string()).unwrap_or_else(|| "{}".to_string())
        ),
        Level::TRACE => event!(
            Level::TRACE,
            filename = event_payload.filename,
            timestamp = %event_payload.timestamp.to_rfc3339(),
            classname = event_payload.classname.unwrap_or(""),
            function = event_payload.function.unwrap_or(""),
            system_section = event_payload.system_section,
            line_num = event_payload.line_num,
            error = event_payload.error.unwrap_or(""),
            db_phase = event_payload.db_phase.unwrap_or("none"),
            method = event_payload.method.unwrap_or("NONE"),
            message = event_payload.message,
            sherlock_line = event_payload.sherlock_line.as_str(),
            extra = event_payload.extra.as_ref().map(|v| v.to_string()).unwrap_or_else(|| "{}".to_string())
        ),
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn emits_log_without_panic() {
        init_tracing().unwrap();
        emit_log(
            "lib.rs",
            42,
            "instrumentation::tests",
            "log smoke test",
            LogContext {
                level: Level::INFO,
                ..Default::default()
            },
        );
    }
}
