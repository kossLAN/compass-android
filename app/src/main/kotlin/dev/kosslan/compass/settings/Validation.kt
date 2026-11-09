package dev.kosslan.compass.settings

import dev.kosslan.compass.ffi.LighthouseUrlException
import dev.kosslan.compass.ffi.normalizeLighthouseUrl

const val MAX_LIGHTHOUSES = 8
const val MIN_LISTEN_PORT = 1024
const val MAX_LISTEN_PORT = 65535

private const val MAX_LABEL = 63
private val LABEL = Regex("[a-z0-9]([a-z0-9-]*[a-z0-9])?")
private const val PEM_CERTIFICATE = "-----BEGIN CERTIFICATE-----"

enum class MeshProblem {
    NoLighthouse,
    InvalidUrl,
    InsecureUrl,
    DuplicateUrl,
    TooManyLighthouses,
    NoAuthKey,
    InvalidCertificate,
    UnreadableCertificate,
    LargeCertificate,
}

sealed interface LighthouseUrl {
    data class Valid(
        val url: String,
    ) : LighthouseUrl

    data object Invalid : LighthouseUrl

    data object Insecure : LighthouseUrl
}

fun isHostname(name: String): Boolean = name.length <= MAX_LABEL && LABEL.matches(name)

fun isListenPort(port: Int?): Boolean = port != null && port in MIN_LISTEN_PORT..MAX_LISTEN_PORT

/** Checks a lighthouse URL as the client does, giving the form the client reports it in. */
fun parseLighthouse(raw: String): LighthouseUrl =
    try {
        LighthouseUrl.Valid(normalizeLighthouseUrl(raw))
    } catch (_: LighthouseUrlException.Invalid) {
        LighthouseUrl.Invalid
    } catch (_: LighthouseUrlException.Insecure) {
        LighthouseUrl.Insecure
    }

fun validateMesh(
    mesh: Mesh,
    others: List<Mesh>,
): MeshProblem? {
    if (mesh.lighthouses.isEmpty()) {
        return MeshProblem.NoLighthouse
    }

    mesh.lighthouses.forEach { url ->
        when (parseLighthouse(url)) {
            LighthouseUrl.Invalid -> {
                return MeshProblem.InvalidUrl
            }

            LighthouseUrl.Insecure -> {
                return MeshProblem.InsecureUrl
            }

            is LighthouseUrl.Valid -> {}
        }
    }

    val elsewhere = others.filterNot { it.id == mesh.id }
    val urls = mesh.lighthouses + elsewhere.flatMap(Mesh::lighthouses)

    return when {
        urls.toSet().size != urls.size -> MeshProblem.DuplicateUrl
        urls.size > MAX_LIGHTHOUSES -> MeshProblem.TooManyLighthouses
        mesh.authKey.isBlank() -> MeshProblem.NoAuthKey
        mesh.caPem != null && PEM_CERTIFICATE !in mesh.caPem -> MeshProblem.InvalidCertificate
        else -> null
    }
}
