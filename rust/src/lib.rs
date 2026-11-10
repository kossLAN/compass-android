mod client;
mod error;
mod keys;
mod logging;
mod settings;
mod status;
mod tun;

pub use client::CompassClient;
pub use error::{CompassError, TunError};
pub use keys::{generate_private_key, is_public_key, public_key, sanitize_hostname};
pub use logging::{LogLevel, LogSink, init_logging};
pub use settings::{
    ClientSettings, IpPrefix, LighthouseSettings, UnderlyingNetwork, is_lighthouse_endpoint,
};
pub use status::{
    ExitNodeStatus, LighthouseState, LighthouseStatus, NodeStatus, PathKind, PeerStatus, Phase,
    StatusReport,
};
pub use tun::{TunProvider, TunShape};

uniffi::setup_scaffolding!();
