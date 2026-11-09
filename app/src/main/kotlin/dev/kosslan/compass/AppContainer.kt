package dev.kosslan.compass

import android.content.Context
import android.net.ConnectivityManager
import androidx.datastore.core.DataStoreFactory
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.dataStoreFile
import dev.kosslan.compass.settings.CompassSettings
import dev.kosslan.compass.settings.SecretCipher
import dev.kosslan.compass.settings.SettingsRepository
import dev.kosslan.compass.settings.SettingsSerializer
import dev.kosslan.compass.tunnel.NetworkMonitor
import dev.kosslan.compass.tunnel.TunnelController

class AppContainer(
    context: Context,
) {
    private val store =
        DataStoreFactory.create(
            serializer = SettingsSerializer(SecretCipher("compass-settings")),
            corruptionHandler = ReplaceFileCorruptionHandler { CompassSettings() },
            produceFile = { context.dataStoreFile("settings.bin") },
        )

    val settings = SettingsRepository(store, context.contentResolver)

    val network = NetworkMonitor(context.getSystemService(ConnectivityManager::class.java))

    val tunnel =
        TunnelController(
            context.applicationContext,
            settings,
            network,
        )
}
