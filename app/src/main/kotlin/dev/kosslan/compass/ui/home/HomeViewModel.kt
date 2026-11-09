package dev.kosslan.compass.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.kosslan.compass.ffi.ExitNodeStatus
import dev.kosslan.compass.ffi.LighthouseStatus
import dev.kosslan.compass.ffi.NodeStatus
import dev.kosslan.compass.ffi.Phase
import dev.kosslan.compass.settings.SettingsRepository
import dev.kosslan.compass.tunnel.NetworkMonitor
import dev.kosslan.compass.tunnel.TunnelController
import dev.kosslan.compass.tunnel.TunnelState
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

sealed interface ConnectionStatus {
    data object Off : ConnectionStatus

    data object Connecting : ConnectionStatus

    data object Connected : ConnectionStatus

    data class Degraded(
        val reason: String,
    ) : ConnectionStatus

    data class Failed(
        val reason: String,
    ) : ConnectionStatus
}

val ConnectionStatus.active: Boolean
    get() = this != ConnectionStatus.Off && this !is ConnectionStatus.Failed

data class HomeUiState(
    val status: ConnectionStatus = ConnectionStatus.Off,
    val configured: Boolean = true,
    val hostname: String = "",
    val nodes: List<NodeStatus> = emptyList(),
    val lighthouses: List<LighthouseStatus> = emptyList(),
    val exitNode: ExitNodeStatus? = null,
    val peersOnline: Int = 0,
    val peersTotal: Int = 0,
    val privateDns: String? = null,
) {
    val privateDnsWarning: Boolean
        get() = privateDns != null && status.active
}

class HomeViewModel(
    private val tunnel: TunnelController,
    settings: SettingsRepository,
    network: NetworkMonitor,
) : ViewModel() {
    val state: StateFlow<HomeUiState> =
        combine(
            tunnel.state,
            settings.settings,
            network.privateDnsServer,
        ) { tunnel, current, privateDns ->
            val report = (tunnel as? TunnelState.Running)?.status
            HomeUiState(
                status =
                    when (tunnel) {
                        TunnelState.Off -> {
                            ConnectionStatus.Off
                        }

                        TunnelState.Starting -> {
                            ConnectionStatus.Connecting
                        }

                        is TunnelState.Error -> {
                            ConnectionStatus.Failed(tunnel.message)
                        }

                        is TunnelState.Running -> {
                            when (val phase = report?.phase) {
                                null, Phase.Connecting -> ConnectionStatus.Connecting
                                Phase.Running -> ConnectionStatus.Connected
                                is Phase.Degraded -> ConnectionStatus.Degraded(phase.reason)
                                is Phase.Failed -> ConnectionStatus.Failed(phase.reason)
                            }
                        }
                    },
                configured = current.meshes.isNotEmpty(),
                hostname = settings.hostname(current),
                nodes = report?.nodes.orEmpty(),
                lighthouses = report?.lighthouses.orEmpty(),
                exitNode = report?.exitNode,
                peersOnline = report?.peers?.count { it.online } ?: 0,
                peersTotal = report?.peers?.size ?: 0,
                privateDns = privateDns,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

    fun connect() = tunnel.connect()

    fun disconnect() = tunnel.disconnect()
}
