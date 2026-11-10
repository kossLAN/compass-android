use compass_proto::{
    ExitNodeStatus as CoreExitNode, LighthouseState as CoreLighthouseState,
    LighthouseStatus as CoreLighthouse, NodeStatus as CoreNode, PathKind as CorePath,
    PeerStatus as CorePeer, Phase as CorePhase, StatusReport as CoreReport,
};

#[derive(Debug, Clone, PartialEq, Eq, uniffi::Enum)]
pub enum Phase {
    Connecting,
    Running,
    Degraded { reason: String },
    Failed { reason: String },
}

#[derive(Debug, Clone, PartialEq, Eq, uniffi::Enum)]
pub enum LighthouseState {
    Connecting,
    Connected,
    Unreachable { reason: String },
    Rejected { reason: String },
}

#[derive(Debug, Clone, PartialEq, Eq, uniffi::Enum)]
pub enum PathKind {
    Direct { endpoint: String },
    Relay { lighthouse: String },
    None,
}

#[derive(Debug, Clone, PartialEq, Eq, uniffi::Record)]
pub struct LighthouseStatus {
    pub endpoint: String,
    pub mesh: Option<String>,
    pub state: LighthouseState,
    pub relay: bool,
}

#[derive(Debug, Clone, PartialEq, Eq, uniffi::Record)]
pub struct NodeStatus {
    pub hostname: String,
    pub fqdn: String,
    pub addresses: Vec<String>,
}

#[derive(Debug, Clone, PartialEq, Eq, uniffi::Record)]
pub struct ExitNodeStatus {
    pub peer: String,
    pub active: bool,
}

#[derive(Debug, Clone, PartialEq, Eq, uniffi::Record)]
pub struct PeerStatus {
    pub hostname: String,
    pub fqdn: String,
    pub addresses: Vec<String>,
    pub online: bool,
    pub exit_node: bool,
    pub path: PathKind,
    pub last_handshake_secs: Option<u64>,
    pub rx_bytes: u64,
    pub tx_bytes: u64,
}

#[derive(Debug, Clone, PartialEq, Eq, uniffi::Record)]
pub struct StatusReport {
    pub phase: Phase,
    pub lighthouses: Vec<LighthouseStatus>,
    pub nodes: Vec<NodeStatus>,
    pub exit_node: Option<ExitNodeStatus>,
    pub peers: Vec<PeerStatus>,
}

fn strings<T>(items: &[T]) -> Vec<String>
where
    T: ToString,
{
    items.iter().map(ToString::to_string).collect()
}

impl From<CoreReport> for StatusReport {
    fn from(report: CoreReport) -> Self {
        Self {
            phase: match report.phase {
                CorePhase::Connecting => Phase::Connecting,
                CorePhase::Running => Phase::Running,
                CorePhase::Degraded(reason) => Phase::Degraded { reason },
                CorePhase::Failed(reason) => Phase::Failed { reason },
            },
            lighthouses: report.lighthouses.into_iter().map(Into::into).collect(),
            nodes: report.nodes.into_iter().map(Into::into).collect(),
            exit_node: report.exit_node.map(Into::into),
            peers: report.peers.into_iter().map(Into::into).collect(),
        }
    }
}

impl From<CoreLighthouse> for LighthouseStatus {
    fn from(lighthouse: CoreLighthouse) -> Self {
        Self {
            endpoint: lighthouse.endpoint,
            mesh: lighthouse.mesh.map(|mesh| mesh.to_string()),
            state: match lighthouse.state {
                CoreLighthouseState::Connecting => LighthouseState::Connecting,
                CoreLighthouseState::Connected => LighthouseState::Connected,
                CoreLighthouseState::Unreachable(reason) => LighthouseState::Unreachable { reason },
                CoreLighthouseState::Rejected(reason) => LighthouseState::Rejected { reason },
            },
            relay: lighthouse.relay,
        }
    }
}

impl From<CoreNode> for NodeStatus {
    fn from(node: CoreNode) -> Self {
        Self {
            hostname: node.hostname.to_string(),
            fqdn: node.fqdn,
            addresses: strings(&node.addresses),
        }
    }
}

impl From<CoreExitNode> for ExitNodeStatus {
    fn from(exit_node: CoreExitNode) -> Self {
        Self {
            peer: exit_node.peer.to_string(),
            active: exit_node.active,
        }
    }
}

impl From<CorePeer> for PeerStatus {
    fn from(peer: CorePeer) -> Self {
        Self {
            hostname: peer.hostname.to_string(),
            fqdn: peer.fqdn,
            addresses: strings(&peer.addresses),
            online: peer.online,
            exit_node: peer.exit_node,
            path: match peer.path {
                CorePath::Direct(endpoint) => PathKind::Direct {
                    endpoint: endpoint.to_string(),
                },
                CorePath::Relay(lighthouse) => PathKind::Relay { lighthouse },
                CorePath::None => PathKind::None,
            },
            last_handshake_secs: peer.last_handshake_secs,
            rx_bytes: peer.rx_bytes,
            tx_bytes: peer.tx_bytes,
        }
    }
}
