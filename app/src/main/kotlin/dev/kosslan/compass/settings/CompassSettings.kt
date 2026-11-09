package dev.kosslan.compass.settings

import kotlinx.serialization.Serializable

const val DEFAULT_LISTEN_PORT = 51821

@Serializable
data class CompassSettings(
    val privateKey: String = "",
    val hostname: String? = null,
    val listenPort: Int = DEFAULT_LISTEN_PORT,
    val meshes: List<Mesh> = emptyList(),
)

/**
 * The lighthouses that share one auth key, which together serve one mesh, so their CA and exit
 * node are kept in one place.
 */
@Serializable
data class Mesh(
    val id: String,
    val lighthouses: List<String>,
    val authKey: String,
    val caPem: String? = null,
    val exitPeer: String? = null,
)
