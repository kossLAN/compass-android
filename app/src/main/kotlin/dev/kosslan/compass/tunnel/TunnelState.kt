package dev.kosslan.compass.tunnel

import dev.kosslan.compass.ffi.StatusReport

sealed interface TunnelState {
    data object Off : TunnelState

    data object Starting : TunnelState

    data class Running(
        val status: StatusReport?,
    ) : TunnelState

    data class Error(
        val message: String,
    ) : TunnelState
}
