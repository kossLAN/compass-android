package dev.kosslan.compass.ui.peers

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.kosslan.compass.R
import dev.kosslan.compass.ffi.PathKind
import dev.kosslan.compass.ui.LocalAppContainer
import dev.kosslan.compass.ui.home.StatusDot
import dev.kosslan.compass.ui.hostOf
import dev.kosslan.compass.ui.theme.Online
import dev.kosslan.compass.ui.theme.mono

@Composable
fun PeersScreen() {
    val container = LocalAppContainer.current
    val model = viewModel { PeersViewModel(container.tunnel, container.settings) }
    val state by model.state.collectAsStateWithLifecycle()
    var selected by rememberSaveable { mutableStateOf<String?>(null) }

    if (state.peers.isEmpty()) {
        Empty(if (state.connected) R.string.peers_none else R.string.peers_disconnected)
        return
    }

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        item {
            Text(
                text = stringResource(R.string.nav_peers),
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.padding(start = 4.dp, top = 8.dp, bottom = 8.dp),
            )
        }

        items(state.peers, key = { it.peer.fqdn }) { item ->
            PeerCard(
                item = item,
                onClick = { selected = item.peer.fqdn },
                modifier = Modifier.animateItem(),
            )
        }
    }

    state.peers.firstOrNull { it.peer.fqdn == selected }?.let { item ->
        PeerSheet(
            item = item,
            exitActive = state.exitNode?.active == true,
            onUseExit = { model.useExitNode(item, it) },
            onDismiss = { selected = null },
        )
    }
}

@Composable
private fun Empty(message: Int) {
    Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize().padding(32.dp)) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                painterResource(R.drawable.ic_peers),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.outline,
                modifier = Modifier.size(56.dp),
            )
            Spacer(Modifier.size(16.dp))
            Text(
                text = stringResource(message),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun PeerCard(
    item: PeerItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val peer = item.peer

    ElevatedCard(onClick = onClick, modifier = modifier.fillMaxWidth()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
        ) {
            PeerDot(peer.online)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        peer.hostname,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (item.chosenExit) {
                        Spacer(Modifier.width(6.dp))
                        Icon(
                            painterResource(R.drawable.ic_exit_node),
                            contentDescription = stringResource(R.string.peer_use_exit),
                            tint = colors.primary,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                }
                Text(
                    text = peer.addresses.firstOrNull() ?: peer.fqdn,
                    style = MaterialTheme.typography.mono,
                    color = colors.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.width(12.dp))
            PathBadge(peer.path, peer.online)
        }
    }
}

@Composable
internal fun PeerDot(online: Boolean) {
    StatusDot(if (online) Online else MaterialTheme.colorScheme.outline)
}

@Composable
private fun PathBadge(
    path: PathKind,
    online: Boolean,
) {
    val colors = MaterialTheme.colorScheme
    val text =
        when (path) {
            is PathKind.Direct -> {
                stringResource(R.string.path_direct)
            }

            is PathKind.Relay if online -> {
                stringResource(
                    R.string.path_relay,
                    hostOf(path.lighthouse),
                )
            }

            is PathKind.Relay -> {
                stringResource(R.string.path_offline)
            }

            PathKind.None -> {
                stringResource(R.string.path_none)
            }
        }
    val direct = path is PathKind.Direct
    val container = if (direct) colors.primaryContainer else colors.surfaceContainerHighest
    val content = if (direct) colors.onPrimaryContainer else colors.onSurfaceVariant

    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = content,
        maxLines = 1,
        modifier =
            Modifier
                .background(
                    container,
                    CircleShape,
                ).padding(horizontal = 10.dp, vertical = 4.dp),
    )
}
