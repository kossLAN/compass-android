package dev.kosslan.compass.tunnel

import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.VpnService
import androidx.core.app.ServiceCompat
import dev.kosslan.compass.CompassApplication
import dev.kosslan.compass.R
import dev.kosslan.compass.ffi.TunException
import dev.kosslan.compass.ffi.TunProvider
import dev.kosslan.compass.ffi.TunShape
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class CompassVpnService : VpnService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var session: Job? = null

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int,
    ): Int {
        if (intent?.action == ACTION_DISCONNECT) {
            val running = session

            if (running != null) {
                running.cancel()
            } else {
                stopSelf()
            }

            return START_NOT_STICKY
        }

        ServiceCompat.startForeground(
            this,
            TunnelNotifications.ID,
            TunnelNotifications.running(this),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_MANIFEST,
        )

        if (session?.isActive != true) {
            start()
        }

        return START_NOT_STICKY
    }

    private fun start() {
        val tunnel = (application as CompassApplication).container.tunnel
        val previous = session
        val job =
            scope.launch {
                previous?.join()
                tunnel.run(Tunnel())
            }

        session = job
        job.invokeOnCompletion {
            scope.launch {
                if (session === job) {
                    ServiceCompat.stopForeground(
                        this@CompassVpnService,
                        ServiceCompat.STOP_FOREGROUND_REMOVE,
                    )
                    stopSelf()
                }
            }
        }
    }

    override fun onRevoke() {
        session?.cancel()
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private inner class Tunnel : TunProvider {
        override fun establish(shape: TunShape): Int {
            val builder =
                Builder()
                    .setSession(getString(R.string.app_name))
                    .setConfigureIntent(TunnelNotifications.openApp(this@CompassVpnService))
                    .setMtu(shape.mtu.toInt())
                    .setBlocking(false)
                    .addDisallowedApplication(packageName)

            shape.addresses.forEach { builder.addAddress(it.address, it.prefixLength.toInt()) }
            shape.routes.forEach { builder.addRoute(it.address, it.prefixLength.toInt()) }
            shape.dnsServers.forEach(builder::addDnsServer)
            shape.searchDomains.forEach(builder::addSearchDomain)

            val descriptor =
                builder.establish()
                    ?: throw TunException.Failed(getString(R.string.error_vpn_not_allowed))
            return descriptor.detachFd()
        }
    }

    companion object {
        private const val ACTION_CONNECT = "dev.kosslan.compass.action.CONNECT"
        private const val ACTION_DISCONNECT = "dev.kosslan.compass.action.DISCONNECT"

        fun connectIntent(context: Context) =
            Intent(context, CompassVpnService::class.java).setAction(ACTION_CONNECT)

        fun disconnectIntent(context: Context) =
            Intent(context, CompassVpnService::class.java).setAction(ACTION_DISCONNECT)
    }
}
