package dev.kosslan.compass.ui.peers

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.kosslan.compass.ffi.ExitNodeStatus
import dev.kosslan.compass.ffi.PeerStatus
import dev.kosslan.compass.ffi.StatusReport
import dev.kosslan.compass.settings.CompassSettings
import dev.kosslan.compass.settings.SettingsRepository
import dev.kosslan.compass.tunnel.TunnelController
import dev.kosslan.compass.tunnel.TunnelState
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class PeerItem(
    val peer: PeerStatus,
    val meshId: String?,
    val chosenExit: Boolean,
)

data class PeersUiState(
    val connected: Boolean = false,
    val peers: List<PeerItem> = emptyList(),
    val exitNode: ExitNodeStatus? = null,
)

class PeersViewModel(
    tunnel: TunnelController,
    private val settings: SettingsRepository,
) : ViewModel() {
    val state: StateFlow<PeersUiState> =
        combine(tunnel.state, settings.settings) { running, current ->
            val report = (running as? TunnelState.Running)?.status
            PeersUiState(
                connected = report != null,
                peers = report?.let { items(it, current) }.orEmpty(),
                exitNode = report?.exitNode,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PeersUiState())

    fun useExitNode(
        item: PeerItem,
        enabled: Boolean,
    ) {
        viewModelScope.launch {
            if (enabled) {
                settings.setExitPeer(item.meshId, item.peer.hostname)
            } else {
                settings.setExitPeer(null, null)
            }
        }
    }

    /** The mesh a peer belongs to, found through the domain its name ends in. */
    private fun items(
        report: StatusReport,
        settings: CompassSettings,
    ): List<PeerItem> {
        val meshByDomain =
            report.lighthouses
                .mapNotNull { lighthouse ->
                    val mesh = settings.meshes.firstOrNull { lighthouse.url in it.lighthouses }
                    val domain = lighthouse.mesh
                    if (mesh != null && domain != null) domain to mesh else null
                }.toMap()

        return report.peers
            .map { peer ->
                val mesh = meshByDomain[peer.fqdn.removePrefix("${peer.hostname}.")]
                PeerItem(peer, mesh?.id, mesh?.exitPeer == peer.hostname)
            }.sortedWith(compareBy({ !it.peer.online }, { it.peer.hostname }))
    }
}
