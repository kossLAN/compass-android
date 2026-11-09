package dev.kosslan.compass.ui.settings

import android.content.ContentResolver
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.kosslan.compass.ffi.CompassException
import dev.kosslan.compass.ffi.publicKey
import dev.kosslan.compass.settings.DEFAULT_LISTEN_PORT
import dev.kosslan.compass.settings.LighthouseUrl
import dev.kosslan.compass.settings.Mesh
import dev.kosslan.compass.settings.MeshProblem
import dev.kosslan.compass.settings.SettingsRepository
import dev.kosslan.compass.settings.parseLighthouse
import dev.kosslan.compass.settings.validateMesh
import dev.kosslan.compass.tunnel.TunnelController
import dev.kosslan.compass.tunnel.TunnelState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException
import java.io.InputStream
import java.util.UUID

private const val MAX_CERTIFICATE_BYTES = 64 * 1024

data class SettingsUiState(
    val hostname: String? = null,
    val defaultHostname: String = "",
    val listenPort: Int = DEFAULT_LISTEN_PORT,
    val publicKey: String? = null,
    val meshes: List<Mesh> = emptyList(),
    val meshNames: Map<String, String> = emptyMap(),
)

/** A network being edited; kept here rather than in saved state since it holds an auth key. */
data class MeshDraft(
    val id: String = UUID.randomUUID().toString(),
    val saved: Boolean = false,
    val lighthouses: List<String> = listOf(""),
    val authKey: String = "",
    val caPem: String? = null,
    val problem: MeshProblem? = null,
    val done: Boolean = false,
)

class SettingsViewModel(
    private val settings: SettingsRepository,
    tunnel: TunnelController,
    private val resolver: ContentResolver,
) : ViewModel() {
    private val publicKeys =
        settings.settings
            .map { it.privateKey }
            .distinctUntilChanged()
            .map { key -> key.takeIf(String::isNotEmpty)?.let(::derivePublicKey) }
            .flowOn(Dispatchers.Default)

    /** The mesh domain each lighthouse serves, as the running client reports it. */
    private val domains =
        tunnel.state
            .map { state ->
                (state as? TunnelState.Running)
                    ?.status
                    ?.lighthouses
                    .orEmpty()
                    .associate { it.url to it.mesh }
            }.distinctUntilChanged()

    val state: StateFlow<SettingsUiState> =
        combine(settings.settings, publicKeys, domains) { current, key, domains ->
            SettingsUiState(
                hostname = current.hostname,
                defaultHostname = settings.defaultHostname,
                listenPort = current.listenPort,
                publicKey = key,
                meshes = current.meshes,
                meshNames =
                    current.meshes.associate { mesh ->
                        val domain = mesh.lighthouses.firstNotNullOfOrNull(domains::get)
                        mesh.id to (domain ?: mesh.lighthouses.firstOrNull().orEmpty())
                    },
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsUiState())

    private val editing = MutableStateFlow<MeshDraft?>(null)
    val draft: StateFlow<MeshDraft?> = editing.asStateFlow()

    init {
        viewModelScope.launch { settings.ensureIdentity() }
    }

    fun setHostname(hostname: String?) = launch { settings.setHostname(hostname) }

    fun setListenPort(port: Int) = launch { settings.setListenPort(port) }

    fun regenerateKey() = launch { settings.regenerateKey() }

    fun add() {
        editing.value = MeshDraft()
    }

    fun edit(mesh: Mesh) {
        editing.value =
            MeshDraft(
                id = mesh.id,
                saved = true,
                lighthouses = mesh.lighthouses,
                authKey = mesh.authKey,
                caPem = mesh.caPem,
            )
    }

    fun setLighthouse(
        index: Int,
        url: String,
    ) = change { draft ->
        draft.copy(lighthouses = draft.lighthouses.toMutableList().apply { set(index, url) })
    }

    fun addLighthouse() = change { it.copy(lighthouses = it.lighthouses + "") }

    fun removeLighthouse(index: Int) =
        change { draft ->
            draft.copy(lighthouses = draft.lighthouses.filterIndexed { i, _ -> i != index })
        }

    fun setAuthKey(authKey: String) = change { it.copy(authKey = authKey) }

    fun clearCertificate() = change { it.copy(caPem = null) }

    fun loadCertificate(uri: Uri) =
        launch {
            val pem =
                withContext(Dispatchers.IO) {
                    try {
                        resolver.openInputStream(uri)?.use(::readCertificate)
                    } catch (_: IOException) {
                        null
                    } catch (_: SecurityException) {
                        null
                    }
                }

            when (pem) {
                null -> fail(MeshProblem.UnreadableCertificate)
                is Certificate.TooLarge -> fail(MeshProblem.LargeCertificate)
                is Certificate.Read -> change { it.copy(caPem = pem.pem) }
            }
        }

    fun dismiss() {
        editing.value = null
    }

    fun save() =
        launch {
            val draft = editing.value ?: return@launch
            val current = settings.current()
            val lighthouses = draft.lighthouses.map(String::trim).filter(String::isNotEmpty)
            val mesh =
                Mesh(
                    id = draft.id,
                    lighthouses =
                        lighthouses.map { url ->
                            (parseLighthouse(url) as? LighthouseUrl.Valid)?.url ?: url
                        },
                    authKey = draft.authKey.trim(),
                    caPem = draft.caPem,
                    exitPeer = current.meshes.find { it.id == draft.id }?.exitPeer,
                )
            val problem = validateMesh(mesh, current.meshes)

            if (problem != null) {
                fail(problem)
            } else {
                settings.saveMesh(mesh)
                editing.update { it?.copy(done = true) }
            }
        }

    fun delete() =
        launch {
            editing.value?.let { settings.deleteMesh(it.id) }
            editing.update { it?.copy(done = true) }
        }

    private fun change(transform: (MeshDraft) -> MeshDraft) {
        editing.update { draft -> draft?.let { transform(it).copy(problem = null) } }
    }

    private fun fail(problem: MeshProblem) {
        editing.update { it?.copy(problem = problem) }
    }

    private fun launch(block: suspend () -> Unit) {
        viewModelScope.launch { block() }
    }
}

private sealed interface Certificate {
    data class Read(
        val pem: String,
    ) : Certificate

    data object TooLarge : Certificate
}

private fun readCertificate(input: InputStream): Certificate {
    val bytes = input.readAtMost(MAX_CERTIFICATE_BYTES + 1)

    return if (bytes.size > MAX_CERTIFICATE_BYTES) {
        Certificate.TooLarge
    } else {
        Certificate.Read(bytes.decodeToString())
    }
}

private fun InputStream.readAtMost(limit: Int): ByteArray {
    val buffer = ByteArray(limit)
    var length = 0

    while (length < limit) {
        val read = read(buffer, length, limit - length)

        if (read < 0) {
            break
        }

        length += read
    }

    return buffer.copyOf(length)
}

private fun derivePublicKey(privateKey: String): String? =
    try {
        publicKey(privateKey)
    } catch (_: CompassException) {
        null
    }
