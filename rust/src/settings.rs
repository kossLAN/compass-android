use std::collections::BTreeSet;
use std::net::IpAddr;

use compass_config::{DnsConfig, ExitNodeConfig, MAX_LIGHTHOUSES};
use compass_core::{
    CaCertificate, ClientSettings as CoreSettings, LighthouseSettings as CoreLighthouse, Underlying,
};
use compass_proto::{AuthKey, DnsName, PrivateKey};
use ipnet::IpNet;
use url::{Host, Url};

use crate::error::{CompassError, LighthouseUrlError};

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

/// Lighthouses that share an auth key serve one mesh, and must agree on `exit_peer`.
#[derive(Debug, Clone, PartialEq, Eq, uniffi::Record)]
pub struct LighthouseSettings {
    pub url: String,
    pub auth_key: String,
    pub ca_pem: Option<String>,
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
        let urls: BTreeSet<&Url> = lighthouses
            .iter()
            .map(|lighthouse| &lighthouse.url)
            .collect();

        if urls.len() != lighthouses.len() {
            return Err(CompassError::invalid("a lighthouse is listed twice"));
        }

        Ok(Self {
            private_key: Some(
                PrivateKey::from_base64(&settings.private_key).map_err(CompassError::invalid)?,
            ),
            hostname: Some(label(&settings.hostname)?),
            listen_port: settings.listen_port,
            lighthouses,
        })
    }
}

/// Checks a lighthouse URL as the client does, and gives the form the client reports it in.
#[uniffi::export]
pub fn normalize_lighthouse_url(url: String) -> Result<String, LighthouseUrlError> {
    lighthouse_url(&url).map(String::from)
}

fn lighthouse_url(raw: &str) -> Result<Url, LighthouseUrlError> {
    let url: Url = raw
        .trim()
        .parse()
        .map_err(|_| LighthouseUrlError::Invalid)?;
    let loopback = match url.host() {
        Some(Host::Domain(domain)) => domain == "localhost",
        Some(Host::Ipv4(ip)) => ip.is_loopback(),
        Some(Host::Ipv6(ip)) => ip.is_loopback(),
        None => false,
    };

    match url.scheme() {
        "https" => Ok(url),
        "http" if loopback => Ok(url),
        "http" => Err(LighthouseUrlError::Insecure),
        _ => Err(LighthouseUrlError::Invalid),
    }
}

fn lighthouse(settings: LighthouseSettings) -> Result<CoreLighthouse, CompassError> {
    let url = lighthouse_url(&settings.url)
        .map_err(|error| CompassError::invalid(format!("\"{}\" {error}", settings.url)))?;

    if settings.auth_key.trim().is_empty() {
        return Err(CompassError::invalid(format!("\"{url}\" has no auth key")));
    }

    Ok(CoreLighthouse {
        auth_key: AuthKey::new(settings.auth_key.trim()),
        ca: settings.ca_pem.map(CaCertificate::Pem),
        dns: DnsConfig::default(),
        exit_node: ExitNodeConfig {
            peer: settings.exit_peer.as_deref().map(label).transpose()?,
            advertise: None,
        },
        url,
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
