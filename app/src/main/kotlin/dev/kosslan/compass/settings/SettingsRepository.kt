package dev.kosslan.compass.settings

import android.content.ContentResolver
import android.os.Build
import android.provider.Settings
import androidx.datastore.core.DataStore
import dev.kosslan.compass.ffi.ClientSettings
import dev.kosslan.compass.ffi.LighthouseSettings
import dev.kosslan.compass.ffi.generatePrivateKey
import dev.kosslan.compass.ffi.sanitizeHostname
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

private const val FALLBACK_HOSTNAME = "android"

class SettingsRepository(
    private val store: DataStore<CompassSettings>,
    private val resolver: ContentResolver,
) {
    val settings: Flow<CompassSettings> = store.data

    suspend fun current(): CompassSettings = store.data.first()

    suspend fun ensureIdentity(): CompassSettings =
        store.updateData { settings ->
            if (settings.privateKey.isEmpty()) {
                settings.copy(
                    privateKey = generatePrivateKey(),
                )
            } else {
                settings
            }
        }

    suspend fun regenerateKey() {
        store.updateData { it.copy(privateKey = generatePrivateKey()) }
    }

    suspend fun setHostname(hostname: String?) {
        store.updateData { it.copy(hostname = hostname?.trim()?.takeIf(String::isNotEmpty)) }
    }

    suspend fun setListenPort(port: Int) {
        store.updateData { it.copy(listenPort = port) }
    }

    suspend fun saveMesh(mesh: Mesh) {
        store.updateData { settings ->
            val exists = settings.meshes.any { it.id == mesh.id }
            settings.copy(
                meshes =
                    if (exists) {
                        settings.meshes.map { if (it.id == mesh.id) mesh else it }
                    } else {
                        settings.meshes + mesh
                    },
            )
        }
    }

    suspend fun deleteMesh(id: String) {
        store.updateData { settings ->
            settings.copy(
                meshes = settings.meshes.filterNot { it.id == id },
            )
        }
    }

    /** Only one mesh may route internet traffic, so choosing an exit node clears the others. */
    suspend fun setExitPeer(
        meshId: String?,
        peer: String?,
    ) {
        store.updateData { settings ->
            settings.copy(
                meshes =
                    settings.meshes.map { mesh ->
                        mesh.copy(exitPeer = if (mesh.id == meshId) peer else null)
                    },
            )
        }
    }

    val defaultHostname: String by lazy {
        val device = Settings.Global.getString(resolver, Settings.Global.DEVICE_NAME) ?: Build.MODEL
        sanitizeHostname(device) ?: FALLBACK_HOSTNAME
    }

    fun hostname(settings: CompassSettings): String = settings.hostname ?: defaultHostname

    fun clientSettings(settings: CompassSettings): ClientSettings =
        ClientSettings(
            privateKey = settings.privateKey,
            hostname = hostname(settings),
            listenPort = settings.listenPort.toUShort(),
            lighthouses =
                settings.meshes.flatMap { mesh ->
                    mesh.lighthouses.map { lighthouse ->
                        LighthouseSettings(
                            endpoint = lighthouse.endpoint,
                            publicKey = lighthouse.publicKey,
                            mesh = mesh.id,
                            exitPeer = mesh.exitPeer,
                        )
                    }
                },
        )
}
