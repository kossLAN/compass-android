package dev.kosslan.compass

import android.app.Application
import android.util.Log
import dev.kosslan.compass.ffi.CompassException
import dev.kosslan.compass.ffi.initLogging
import dev.kosslan.compass.tunnel.LogcatSink
import dev.kosslan.compass.tunnel.TunnelNotifications

private const val LOG_FILTER = "info,hickory_server=warn,rtc_ice=warn"

class CompassApplication : Application() {
    val container by lazy { AppContainer(this) }

    override fun onCreate() {
        super.onCreate()
        TunnelNotifications.createChannel(this)

        try {
            initLogging(LogcatSink, LOG_FILTER)
        } catch (error: CompassException) {
            Log.w("compass", "logging from the client is unavailable", error)
        }
    }
}
