package dev.kosslan.compass.tunnel

import android.content.Context
import androidx.core.content.ContextCompat
import dev.kosslan.compass.R
import dev.kosslan.compass.ffi.CompassClient
import dev.kosslan.compass.ffi.CompassException
import dev.kosslan.compass.ffi.TunProvider
import dev.kosslan.compass.ffi.UnderlyingNetwork
import dev.kosslan.compass.settings.CompassSettings
import dev.kosslan.compass.settings.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.dropWhile
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.takeWhile
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.security.KeyStore
import kotlin.time.Duration.Companion.seconds

private val STATUS_INTERVAL = 1.seconds

class TunnelController(
    private val context: Context,
    private val settings: SettingsRepository,
    private val network: NetworkMonitor,
) {
    private val current = MutableStateFlow<TunnelState>(TunnelState.Off)
    private val reloadErrors =
        MutableSharedFlow<String>(
            extraBufferCapacity = 4,
            onBufferOverflow = BufferOverflow.DROP_OLDEST,
        )

    val state: StateFlow<TunnelState> = current.asStateFlow()
    val errors: SharedFlow<String> = reloadErrors.asSharedFlow()

    fun connect() {
        ContextCompat.startForegroundService(context, CompassVpnService.connectIntent(context))
    }

    fun disconnect() {
        context.startService(CompassVpnService.disconnectIntent(context))
    }

    /** Runs the client until cancelled, or until the last network is removed. */
    suspend fun run(tun: TunProvider) {
        current.value = TunnelState.Starting
        var client: CompassClient? = null

        try {
            val started = settings.ensureIdentity()

            if (started.meshes.isEmpty()) {
                current.value = TunnelState.Error(context.getString(R.string.error_no_networks))
                return
            }

            val underlying = network.underlying.first()
            val roots = withContext(Dispatchers.IO) { trustedCertificates() }
            client = CompassClient.start(settings.clientSettings(started), underlying, roots, tun)
            current.value = TunnelState.Running(null)
            follow(client, started, underlying)
        } catch (error: CompassException) {
            current.value = TunnelState.Error(describe(error))
        } finally {
            if (current.value !is TunnelState.Error) {
                current.value = TunnelState.Off
            }

            withContext(NonCancellable) { client?.shutdown() }
            client?.close()
        }
    }

    private suspend fun follow(
        client: CompassClient,
        started: CompassSettings,
        underlying: UnderlyingNetwork,
    ) = coroutineScope {
        launch { pollStatus(client) }

        launch {
            network.underlying
                .dropWhile { it == underlying }
                .collect(client::setUnderlyingNetwork)
        }

        settings.settings
            .takeWhile { it.meshes.isNotEmpty() }
            .map(settings::clientSettings)
            .distinctUntilChanged()
            .dropWhile { it == settings.clientSettings(started) }
            .collect { updated ->
                try {
                    client.reload(updated)
                } catch (error: CompassException) {
                    reloadErrors.tryEmit(describe(error))
                }
            }

        coroutineContext.cancelChildren()
    }

    /** Asks the client for its status only while someone is watching. */
    private suspend fun pollStatus(client: CompassClient) {
        current.subscriptionCount
            .map { it > 0 }
            .distinctUntilChanged()
            .collectLatest { watched ->
                while (watched) {
                    current.value = TunnelState.Running(status(client))
                    delay(STATUS_INTERVAL)
                }
            }
    }

    private suspend fun status(client: CompassClient) =
        try {
            client.status()
        } catch (_: CompassException) {
            null
        }

    private fun describe(error: CompassException): String =
        when (error) {
            is CompassException.InvalidSettings -> error.reason
            is CompassException.Stopped -> context.getString(R.string.error_stopped)
        }
}

private fun trustedCertificates(): List<ByteArray> {
    val store = KeyStore.getInstance("AndroidCAStore").apply { load(null) }
    return store.aliases().toList().mapNotNull { store.getCertificate(it)?.encoded }
}
