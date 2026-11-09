package dev.kosslan.compass.ui.settings

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.kosslan.compass.BuildConfig
import dev.kosslan.compass.R
import dev.kosslan.compass.settings.Mesh
import dev.kosslan.compass.settings.isHostname
import dev.kosslan.compass.settings.isListenPort
import dev.kosslan.compass.ui.LocalAppContainer
import dev.kosslan.compass.ui.hostOf
import dev.kosslan.compass.ui.plainText
import dev.kosslan.compass.ui.theme.mono
import kotlinx.coroutines.launch

private enum class Dialog { Hostname, ListenPort, RegenerateKey }

@Composable
fun SettingsScreen() {
    val container = LocalAppContainer.current
    val context = LocalContext.current
    val model =
        viewModel {
            SettingsViewModel(
                container.settings,
                container.tunnel,
                context.contentResolver,
            )
        }
    val state by model.state.collectAsStateWithLifecycle()
    val draft by model.draft.collectAsStateWithLifecycle()
    var dialog by rememberSaveable { mutableStateOf<Dialog?>(null) }

    LazyColumn(
        contentPadding = PaddingValues(vertical = 16.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        item {
            Text(
                text = stringResource(R.string.nav_settings),
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.padding(start = 20.dp, top = 8.dp, bottom = 8.dp),
            )
        }

        item { SectionTitle(R.string.settings_device) }
        item {
            Entry(
                title = stringResource(R.string.settings_hostname),
                summary =
                    state.hostname
                        ?: stringResource(
                            R.string.settings_hostname_default,
                            state.defaultHostname,
                        ),
                onClick = { dialog = Dialog.Hostname },
            )
        }
        item {
            Entry(
                title = stringResource(R.string.settings_listen_port),
                summary = state.listenPort.toString(),
                onClick = { dialog = Dialog.ListenPort },
            )
        }

        item { SectionTitle(R.string.settings_networks) }
        items(state.meshes, key = Mesh::id) { mesh ->
            MeshCard(mesh, state.meshNames[mesh.id].orEmpty(), onClick = { model.edit(mesh) })
        }
        item {
            FilledTonalButton(
                onClick = model::add,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
            ) {
                Icon(
                    painterResource(R.drawable.ic_add),
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Text(
                    stringResource(R.string.settings_add_network),
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
        }

        item { SectionTitle(R.string.settings_identity) }
        item { PublicKey(state.publicKey) }
        item {
            Entry(
                title = stringResource(R.string.settings_regenerate_key),
                summary = stringResource(R.string.settings_regenerate_key_summary),
                onClick = { dialog = Dialog.RegenerateKey },
            )
        }

        item { SectionTitle(R.string.settings_system) }
        item {
            Entry(
                title = stringResource(R.string.settings_always_on),
                summary = stringResource(R.string.settings_always_on_summary),
                onClick = { context.startActivity(Intent(Settings.ACTION_VPN_SETTINGS)) },
            )
        }
        item {
            Entry(
                title = stringResource(R.string.settings_version),
                summary = BuildConfig.VERSION_NAME,
                onClick = null,
            )
        }
    }

    when (dialog) {
        Dialog.Hostname -> {
            TextDialog(
                title = stringResource(R.string.settings_hostname),
                initial = state.hostname.orEmpty(),
                placeholder = state.defaultHostname,
                supporting = stringResource(R.string.settings_hostname_hint),
                valid = { it.isEmpty() || isHostname(it) },
                onDismiss = { dialog = null },
                onConfirm = { model.setHostname(it) },
            )
        }

        Dialog.ListenPort -> {
            TextDialog(
                title = stringResource(R.string.settings_listen_port),
                initial = state.listenPort.toString(),
                supporting = stringResource(R.string.settings_listen_port_hint),
                keyboard = KeyboardType.Number,
                valid = { isListenPort(it.toIntOrNull()) },
                onDismiss = { dialog = null },
                onConfirm = { model.setListenPort(it.toInt()) },
            )
        }

        Dialog.RegenerateKey -> {
            AlertDialog(
                onDismissRequest = { dialog = null },
                title = { Text(stringResource(R.string.settings_regenerate_key)) },
                text = { Text(stringResource(R.string.settings_regenerate_key_warning)) },
                confirmButton = {
                    TextButton(onClick = {
                        model.regenerateKey()
                        dialog = null
                    }) { Text(stringResource(R.string.action_regenerate)) }
                },
                dismissButton = {
                    TextButton(
                        onClick = { dialog = null },
                    ) { Text(stringResource(R.string.action_cancel)) }
                },
            )
        }

        null -> {}
    }

    draft?.let { MeshEditorSheet(it, model) }
}

@Composable
private fun SectionTitle(title: Int) {
    Text(
        text = stringResource(title),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 20.dp, top = 24.dp, bottom = 4.dp),
    )
}

@Composable
private fun Entry(
    title: String,
    summary: String,
    onClick: (() -> Unit)?,
) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = { Text(summary, maxLines = 2, overflow = TextOverflow.Ellipsis) },
        modifier = if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier,
    )
}

@Composable
private fun MeshCard(
    mesh: Mesh,
    name: String,
    onClick: () -> Unit,
) {
    ElevatedCard(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.padding(16.dp),
        ) {
            Text(
                hostOf(name),
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = mesh.lighthouses.joinToString("\n", transform = ::hostOf),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text =
                    listOfNotNull(
                        mesh.exitPeer?.let { stringResource(R.string.mesh_exit_peer, it) },
                        mesh.caPem?.let { stringResource(R.string.mesh_custom_ca) },
                    ).joinToString(" · "),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@Composable
private fun PublicKey(key: String?) {
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    val label = stringResource(R.string.settings_public_key)

    ListItem(
        headlineContent = { Text(stringResource(R.string.settings_public_key)) },
        supportingContent = {
            Text(
                key ?: stringResource(R.string.settings_public_key_missing),
                style = MaterialTheme.typography.mono,
            )
        },
        trailingContent = {
            if (key != null) {
                IconButton(
                    onClick = { scope.launch { clipboard.setClipEntry(plainText(label, key)) } },
                ) {
                    Icon(
                        painterResource(R.drawable.ic_copy),
                        contentDescription = stringResource(R.string.copy_public_key),
                    )
                }
            }
        },
    )
}

@Composable
private fun TextDialog(
    title: String,
    initial: String,
    supporting: String,
    valid: (String) -> Boolean,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
    placeholder: String? = null,
    keyboard: KeyboardType = KeyboardType.Text,
) {
    var value by rememberSaveable { mutableStateOf(initial) }
    val ok = valid(value.trim())

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = value,
                onValueChange = { value = it },
                singleLine = true,
                isError = !ok,
                placeholder = placeholder?.let { { Text(it) } },
                supportingText = { Text(supporting) },
                keyboardOptions = KeyboardOptions(keyboardType = keyboard),
            )
        },
        confirmButton = {
            TextButton(enabled = ok, onClick = {
                onConfirm(value.trim())
                onDismiss()
            }) { Text(stringResource(R.string.action_save)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}
