package dev.kosslan.compass.settings

import dev.kosslan.compass.ffi.isLighthouseEndpoint
import dev.kosslan.compass.ffi.isPublicKey

const val MAX_LIGHTHOUSES = 8
const val MIN_LISTEN_PORT = 1024
const val MAX_LISTEN_PORT = 65535

private const val MAX_LABEL = 63
private val LABEL = Regex("[a-z0-9]([a-z0-9-]*[a-z0-9])?")

enum class MeshProblem {
    NoLighthouse,
    InvalidEndpoint,
    InvalidPublicKey,
    DuplicateLighthouse,
    TooManyLighthouses,
}

fun isHostname(name: String): Boolean = name.length <= MAX_LABEL && LABEL.matches(name)

fun isListenPort(port: Int?): Boolean = port != null && port in MIN_LISTEN_PORT..MAX_LISTEN_PORT

fun validateMesh(
    mesh: Mesh,
    others: List<Mesh>,
): MeshProblem? {
    val elsewhere = others.filterNot { it.id == mesh.id }
    val keys = (mesh.lighthouses + elsewhere.flatMap(Mesh::lighthouses)).map(Lighthouse::publicKey)

    return when {
        mesh.lighthouses.isEmpty() -> MeshProblem.NoLighthouse
        mesh.lighthouses.any { !isLighthouseEndpoint(it.endpoint) } -> MeshProblem.InvalidEndpoint
        mesh.lighthouses.any { !isPublicKey(it.publicKey) } -> MeshProblem.InvalidPublicKey
        keys.toSet().size != keys.size -> MeshProblem.DuplicateLighthouse
        keys.size > MAX_LIGHTHOUSES -> MeshProblem.TooManyLighthouses
        else -> null
    }
}
