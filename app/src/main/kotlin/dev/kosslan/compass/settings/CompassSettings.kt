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

/** The lighthouses that together serve one mesh, so its exit node is kept in one place. */
@Serializable
data class Mesh(
    val id: String,
    val lighthouses: List<Lighthouse>,
    val exitPeer: String? = null,
) {
    fun serves(endpoint: String): Boolean = lighthouses.any { it.endpoint == endpoint }
}

@Serializable
data class Lighthouse(
    val endpoint: String,
    val publicKey: String,
)
