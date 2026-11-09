use std::io;
use std::sync::Arc;

use compass_core::{TunProvider as CoreProvider, TunShape as CoreShape};
use compass_net::AsyncDevice;

use crate::error::TunError;
use crate::settings::IpPrefix;

#[derive(Debug, Clone, PartialEq, Eq, uniffi::Record)]
pub struct TunShape {
    pub addresses: Vec<IpPrefix>,
    pub routes: Vec<IpPrefix>,
    pub mtu: u16,
    pub dns_servers: Vec<String>,
    pub search_domains: Vec<String>,
}

/// Opens the tunnel, such as with `VpnService.Builder.establish()`, and hands over its file
/// descriptor, detached so that the client owns and closes it.
#[uniffi::export(with_foreign)]
pub trait TunProvider: Send + Sync {
    fn establish(&self, shape: TunShape) -> Result<i32, TunError>;
}

pub struct ForeignTun(pub Arc<dyn TunProvider>);

impl CoreProvider for ForeignTun {
    fn establish(&self, shape: &CoreShape) -> io::Result<AsyncDevice> {
        let shape = TunShape {
            addresses: shape.addresses.iter().copied().map(Into::into).collect(),
            routes: shape
                .routes
                .iter()
                .map(|route| route.trunc().into())
                .collect(),
            mtu: shape.mtu,
            dns_servers: shape.dns_servers.iter().map(ToString::to_string).collect(),
            search_domains: shape.search_domains.clone(),
        };
        let fd = self.0.establish(shape).map_err(io::Error::other)?;

        if fd < 0 {
            return Err(io::Error::from(io::ErrorKind::InvalidInput));
        }

        // SAFETY: the app detached `fd` from its owner and handed it over, so the device is the
        // only thing that uses and closes it.
        #[expect(unsafe_code, reason = "taking ownership of a descriptor from the app")]
        unsafe {
            AsyncDevice::from_fd(fd)
        }
    }
}
