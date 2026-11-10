use std::collections::BTreeSet;
use std::net::IpAddr;

use compass_config::{DnsConfig, ExitNodeConfig, MAX_LIGHTHOUSES, is_endpoint};
use compass_core::{
    ClientSettings as CoreSettings, LighthouseSettings as CoreLighthouse, Underlying,
};
use compass_proto::{DnsName, PrivateKey, PublicKey};
use ipnet::IpNet;

use crate::error::CompassError;

#[derive(Debug, Clone, PartialEq, Eq, uniffi::Record)]
pub struct IpPrefix {
    pub address: String,
    pub prefix_length: u8,
}

#[derive(Debug, Clone, PartialEq, Eq, uniffi::Record)]
pub struct ClientSettings {
    pub private_key: String,
    pub hostname: String,
    pub listen_port: u16,
    pub lighthouses: Vec<LighthouseSettings>,
}

/// Lighthouses with the same `mesh` serve one mesh, and must agree on `exit_peer`.
#[derive(Debug, Clone, PartialEq, Eq, uniffi::Record)]
pub struct LighthouseSettings {
    pub endpoint: String,
    pub public_key: String,
    pub mesh: String,
    pub exit_peer: Option<String>,
}

impl From<IpNet> for IpPrefix {
    fn from(net: IpNet) -> Self {
        Self {
            address: net.addr().to_string(),
            prefix_length: net.prefix_len(),
        }
    }
}

#[derive(Debug, Clone, PartialEq, Eq, uniffi::Record)]
pub struct UnderlyingNetwork {
    pub local_addresses: Vec<IpPrefix>,
    pub dns_servers: Vec<String>,
}

impl From<UnderlyingNetwork> for Underlying {
    fn from(network: UnderlyingNetwork) -> Self {
        Self {
            local_networks: network
                .local_addresses
                .iter()
                .filter_map(|prefix| {
                    let address: IpAddr = prefix.address.parse().ok()?;
                    IpNet::new(address, prefix.prefix_length).ok()
                })
                .collect(),
            dns_servers: network
                .dns_servers
                .iter()
                .filter_map(|server| server.parse().ok())
                .collect(),
        }
    }
}

impl TryFrom<ClientSettings> for CoreSettings {
    type Error = CompassError;

    fn try_from(settings: ClientSettings) -> Result<Self, CompassError> {
        if settings.listen_port == 0 {
            return Err(CompassError::invalid("the listen port must not be 0"));
        }

        if settings.lighthouses.is_empty() || settings.lighthouses.len() > MAX_LIGHTHOUSES {
            return Err(CompassError::invalid(format!(
                "list between 1 and {MAX_LIGHTHOUSES} lighthouses"
            )));
        }

        let lighthouses = settings
            .lighthouses
            .into_iter()
            .map(lighthouse)
            .collect::<Result<Vec<_>, _>>()?;
        let keys: BTreeSet<&PublicKey> = lighthouses
            .iter()
            .map(|lighthouse| &lighthouse.key)
            .collect();

        if keys.len() != lighthouses.len() {
            return Err(CompassError::invalid("a lighthouse is listed twice"));
        }

        Ok(Self {
            private_key: PrivateKey::from_base64(&settings.private_key)
                .map_err(CompassError::invalid)?,
            hostname: Some(label(&settings.hostname)?),
            listen_port: settings.listen_port,
            lighthouses,
        })
    }
}

#[uniffi::export]
pub fn is_lighthouse_endpoint(endpoint: String) -> bool {
    is_endpoint(&endpoint)
}

fn lighthouse(settings: LighthouseSettings) -> Result<CoreLighthouse, CompassError> {
    if !is_endpoint(&settings.endpoint) {
        return Err(CompassError::invalid(format!(
            "\"{}\" is not a host and a port",
            settings.endpoint
        )));
    }

    Ok(CoreLighthouse {
        key: settings.public_key.parse().map_err(CompassError::invalid)?,
        mesh: Some(settings.mesh),
        dns: DnsConfig::default(),
        exit_node: ExitNodeConfig {
            peer: settings.exit_peer.as_deref().map(label).transpose()?,
            advertise: None,
        },
        endpoint: settings.endpoint,
    })
}

fn label(name: &str) -> Result<DnsName, CompassError> {
    let name: DnsName = name.parse().map_err(CompassError::invalid)?;

    if !name.is_label() {
        return Err(CompassError::invalid(format!(
            "\"{name}\" must be a single DNS label"
        )));
    }

    Ok(name)
}
