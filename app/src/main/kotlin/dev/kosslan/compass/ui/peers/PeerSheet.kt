package dev.kosslan.compass.ui.peers

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.kosslan.compass.R
import dev.kosslan.compass.ffi.PathKind
import dev.kosslan.compass.ffi.PeerStatus
import dev.kosslan.compass.ui.formatBytes
import dev.kosslan.compass.ui.handshakeAge
import dev.kosslan.compass.ui.hostOf
import dev.kosslan.compass.ui.plainText
import dev.kosslan.compass.ui.theme.mono
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PeerSheet(
    item: PeerItem,
    exitActive: Boolean,
    onUseExit: (Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    val peer = item.peer

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
            Header(peer)

            Section(R.string.peer_addresses)
            Copyable(peer.fqdn)
            peer.addresses.forEach { Copyable(it) }

            Section(R.string.peer_connection)
            Connection(peer)

            if (peer.exitNode) {
                ExitNode(item, exitActive, onUseExit)
            }

            Spacer(Modifier.size(24.dp))
        }
    }
}

@Composable
private fun Header(peer: PeerStatus) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(horizontal = 24.dp),
    ) {
        PeerDot(peer.online)
        Spacer(Modifier.width(16.dp))
        Column {
            Text(
                peer.hostname,
                style = MaterialTheme.typography.headlineSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                peer.fqdn,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun Section(title: Int) {
    Text(
        text = stringResource(title),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 24.dp, top = 16.dp, bottom = 4.dp),
    )
}

@Composable
private fun Copyable(text: String) {
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    val label = stringResource(R.string.clip_address)
    val copy: () -> Unit = { scope.launch { clipboard.setClipEntry(plainText(label, text)) } }

    ListItem(
        headlineContent = { Text(text, style = MaterialTheme.typography.mono) },
        trailingContent = {
            IconButton(onClick = copy) {
                Icon(
                    painterResource(R.drawable.ic_copy),
                    contentDescription = stringResource(R.string.peer_copy, text),
                )
            }
        },
        colors = sheetItem(),
        modifier = Modifier.clickable(onClick = copy).padding(start = 8.dp),
    )
}

@Composable
private fun Connection(peer: PeerStatus) {
    val context = LocalContext.current
    val path =
        when (val kind = peer.path) {
            is PathKind.Direct -> {
                stringResource(R.string.path_direct_endpoint, kind.endpoint)
            }

            is PathKind.Relay if peer.online -> {
                stringResource(
                    R.string.path_relay,
                    hostOf(kind.lighthouse),
                )
            }

            is PathKind.Relay -> {
                stringResource(R.string.path_offline)
            }

            PathKind.None -> {
                stringResource(R.string.path_none)
            }
        }
    val handshake =
        peer.lastHandshakeSecs?.let { handshakeAge(it).toString() }
            ?: stringResource(R.string.peer_no_handshake)
    val transfer =
        stringResource(
            R.string.peer_transfer_value,
            context.formatBytes(peer.rxBytes),
            context.formatBytes(peer.txBytes),
        )

    Detail(R.string.peer_path, path)
    Detail(R.string.peer_handshake, handshake)
    Detail(R.string.peer_transfer, transfer)
}

@Composable
private fun Detail(
    title: Int,
    value: String,
) {
    ListItem(
        headlineContent = { Text(stringResource(title)) },
        supportingContent = { Text(value) },
        colors = sheetItem(),
        modifier = Modifier.padding(start = 8.dp),
    )
}

@Composable
private fun ExitNode(
    item: PeerItem,
    exitActive: Boolean,
    onUseExit: (Boolean) -> Unit,
) {
    val enabled = item.meshId != null
    val summary = if (exitActive) R.string.exit_node_active else R.string.exit_node_blocked

    ListItem(
        headlineContent = { Text(stringResource(R.string.peer_use_exit)) },
        supportingContent =
            if (item.chosenExit) {
                { Text(stringResource(summary)) }
            } else {
                null
            },
        trailingContent = {
            Switch(
                checked = item.chosenExit,
                onCheckedChange = null,
                enabled = enabled,
            )
        },
        colors = sheetItem(),
        modifier =
            Modifier
                .toggleable(
                    value = item.chosenExit,
                    enabled = enabled,
                    role = Role.Switch,
                    onValueChange = onUseExit,
                ).padding(start = 8.dp),
    )
}

@Composable
private fun sheetItem() = ListItemDefaults.colors(containerColor = Color.Transparent)
