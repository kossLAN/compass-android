package dev.kosslan.compass.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.kosslan.compass.ffi.CompassException
import dev.kosslan.compass.ffi.publicKey
import dev.kosslan.compass.settings.DEFAULT_LISTEN_PORT
import dev.kosslan.compass.settings.Lighthouse
import dev.kosslan.compass.settings.Mesh
import dev.kosslan.compass.settings.MeshProblem
import dev.kosslan.compass.settings.SettingsRepository
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
import java.util.UUID

data class SettingsUiState(
    val hostname: String? = null,
    val defaultHostname: String = "",
    val listenPort: Int = DEFAULT_LISTEN_PORT,
    val publicKey: String? = null,
    val meshes: List<Mesh> = emptyList(),
    val meshNames: Map<String, String> = emptyMap(),
)

data class MeshDraft(
    val id: String = UUID.randomUUID().toString(),
    val saved: Boolean = false,
    val lighthouses: List<Lighthouse> = listOf(Lighthouse("", "")),
    val problem: MeshProblem? = null,
    val done: Boolean = false,
)

class SettingsViewModel(
    private val settings: SettingsRepository,
    tunnel: TunnelController,
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
                    .associate { it.endpoint to it.mesh }
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
                        val endpoints = mesh.lighthouses.map(Lighthouse::endpoint)
                        val domain = endpoints.firstNotNullOfOrNull(domains::get)
                        mesh.id to (domain ?: endpoints.firstOrNull().orEmpty())
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
            )
    }

    fun setEndpoint(
        index: Int,
        endpoint: String,
    ) = changeLighthouse(index) { it.copy(endpoint = endpoint) }

    fun setPublicKey(
        index: Int,
        publicKey: String,
    ) = changeLighthouse(index) { it.copy(publicKey = publicKey) }

    fun addLighthouse() = change { it.copy(lighthouses = it.lighthouses + Lighthouse("", "")) }

    fun removeLighthouse(index: Int) =
        change { draft ->
            draft.copy(lighthouses = draft.lighthouses.filterIndexed { i, _ -> i != index })
        }

    fun dismiss() {
        editing.value = null
    }

    fun save() =
        launch {
            val draft = editing.value ?: return@launch
            val current = settings.current()
            val mesh =
                Mesh(
                    id = draft.id,
                    lighthouses =
                        draft.lighthouses
                            .map { Lighthouse(it.endpoint.trim(), it.publicKey.trim()) }
                            .filterNot { it.endpoint.isEmpty() && it.publicKey.isEmpty() },
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

    private fun changeLighthouse(
        index: Int,
        transform: (Lighthouse) -> Lighthouse,
    ) = change { draft ->
        draft.copy(
            lighthouses =
                draft.lighthouses.mapIndexed { i, lighthouse ->
                    if (i == index) transform(lighthouse) else lighthouse
                },
        )
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

private fun derivePublicKey(privateKey: String): String? =
    try {
        publicKey(privateKey)
    } catch (_: CompassException) {
        null
    }
