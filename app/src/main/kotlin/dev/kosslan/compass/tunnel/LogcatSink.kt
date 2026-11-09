package dev.kosslan.compass.tunnel

import android.util.Log
import dev.kosslan.compass.ffi.LogLevel
import dev.kosslan.compass.ffi.LogSink

private const val TAG = "compass"

object LogcatSink : LogSink {
    override fun log(
        level: LogLevel,
        target: String,
        message: String,
    ) {
        val priority =
            when (level) {
                LogLevel.ERROR -> Log.ERROR
                LogLevel.WARN -> Log.WARN
                LogLevel.INFO -> Log.INFO
                LogLevel.DEBUG -> Log.DEBUG
                LogLevel.TRACE -> Log.VERBOSE
            }
        Log.println(priority, TAG, "$target: $message")
    }
}
