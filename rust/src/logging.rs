use std::fmt::{self, Write as _};
use std::sync::Arc;

use tracing::field::{Field, Visit};
use tracing::{Event, Level, Subscriber};
use tracing_log::NormalizeEvent as _;
use tracing_subscriber::EnvFilter;
use tracing_subscriber::layer::{Context, Layer, SubscriberExt as _};
use tracing_subscriber::util::SubscriberInitExt as _;

use crate::error::CompassError;

#[derive(Debug, Clone, Copy, PartialEq, Eq, uniffi::Enum)]
pub enum LogLevel {
    Error,
    Warn,
    Info,
    Debug,
    Trace,
}

#[uniffi::export(with_foreign)]
pub trait LogSink: Send + Sync {
    fn log(&self, level: LogLevel, target: String, message: String);
}

#[uniffi::export]
pub fn init_logging(sink: Arc<dyn LogSink>, filter: String) -> Result<(), CompassError> {
    let filter = EnvFilter::try_new(filter).map_err(CompassError::invalid)?;
    tracing_subscriber::registry()
        .with(filter)
        .with(SinkLayer { sink })
        .try_init()
        .map_err(CompassError::invalid)?;

    // Android discards stderr, where panics are reported by default.
    std::panic::set_hook(Box::new(|panic| tracing::error!("{panic}")));
    Ok(())
}

struct SinkLayer {
    sink: Arc<dyn LogSink>,
}

impl<S> Layer<S> for SinkLayer
where
    S: Subscriber,
{
    fn on_event(&self, event: &Event<'_>, _context: Context<'_, S>) {
        let normalized = event.normalized_metadata();
        let metadata = normalized.as_ref().unwrap_or_else(|| event.metadata());
        let level = match *metadata.level() {
            Level::ERROR => LogLevel::Error,
            Level::WARN => LogLevel::Warn,
            Level::INFO => LogLevel::Info,
            Level::DEBUG => LogLevel::Debug,
            Level::TRACE => LogLevel::Trace,
        };
        let mut message = Message::default();
        event.record(&mut message);
        self.sink
            .log(level, metadata.target().to_owned(), message.text);
    }
}

#[derive(Default)]
struct Message {
    text: String,
}

impl Visit for Message {
    fn record_debug(&mut self, field: &Field, value: &dyn fmt::Debug) {
        if field.name().starts_with("log.") {
            return;
        }

        if field.name() == "message" {
            self.text.insert_str(0, &format!("{value:?}"));
        } else {
            let _ = write!(self.text, " {}={value:?}", field.name());
        }
    }
}
