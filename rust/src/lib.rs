mod client;
mod error;
mod keys;
mod logging;
mod settings;
mod status;
mod tun;

pub use client::CompassClient;
pub use error::{CompassError, LighthouseUrlError, TunError};
pub use keys::{generate_private_key, public_key, sanitize_hostname};
pub use logging::{LogLevel, LogSink, init_logging};
pub use settings::{
    ClientSettings, IpPrefix, LighthouseSettings, UnderlyingNetwork, normalize_lighthouse_url,
};
pub use status::{
    ExitNodeStatus, LighthouseState, LighthouseStatus, NodeStatus, PathKind, PeerStatus, Phase,
    StatusReport,
};
pub use tun::{TunProvider, TunShape};

uniffi::setup_scaffolding!();
