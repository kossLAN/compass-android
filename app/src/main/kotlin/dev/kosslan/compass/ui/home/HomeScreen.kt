package dev.kosslan.compass.ui.home

import android.content.Intent
import android.provider.Settings
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.kosslan.compass.R
import dev.kosslan.compass.ffi.ExitNodeStatus
import dev.kosslan.compass.ffi.LighthouseState
import dev.kosslan.compass.ffi.LighthouseStatus
import dev.kosslan.compass.ffi.NodeStatus
import dev.kosslan.compass.ui.LocalAppContainer
import dev.kosslan.compass.ui.hostOf
import dev.kosslan.compass.ui.plainText
import dev.kosslan.compass.ui.theme.mono
import kotlinx.coroutines.launch

@Composable
fun HomeScreen(onOpenSettings: () -> Unit) {
    val container = LocalAppContainer.current
    val model = viewModel { HomeViewModel(container.tunnel, container.settings, container.network) }
    val state by model.state.collectAsStateWithLifecycle()
    val start = rememberVpnStarter(model::connect)

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp),
    ) {
        Header(state)

        Box(contentAlignment = Alignment.Center, modifier = Modifier.weight(1f).fillMaxWidth()) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                ActivateButton(
                    status = state.status,
                    onToggle = { enable ->
                        when {
                            !enable -> model.disconnect()
                            state.configured -> start()
                            else -> onOpenSettings()
                        }
                    },
                )
                StatusText(state)
            }
        }

        if (!state.configured) {
            FilledTonalButton(
                onClick = onOpenSettings,
                modifier = Modifier.padding(bottom = 24.dp),
            ) {
                Text(stringResource(R.string.home_add_network))
            }
        }

        AnimatedVisibility(
            visible =
                state.lighthouses.isNotEmpty() || state.exitNode != null || state.privateDnsWarning,
        ) {
            Details(state)
        }
    }
}

@Composable
private fun Header(state: HomeUiState) {
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    val label = stringResource(R.string.clip_address)
    val node = state.nodes.firstOrNull()

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxWidth().padding(top = 24.dp),
    ) {
        Text(
            text = node?.hostname ?: state.hostname,
            style = MaterialTheme.typography.headlineSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )

        if (node != null) {
            Text(
                text = state.nodes.joinToString(" · ", transform = NodeStatus::fqdn),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                modifier = Modifier.padding(top = 8.dp),
            ) {
                state.nodes.flatMap(NodeStatus::addresses).distinct().forEach { address ->
                    AssistChip(
                        onClick = {
                            scope.launch {
                                clipboard.setClipEntry(
                                    plainText(label, address),
                                )
                            }
                        },
                        label = { Text(address, style = MaterialTheme.typography.mono) },
                        leadingIcon = {
                            Icon(
                                painterResource(R.drawable.ic_copy),
                                contentDescription = stringResource(R.string.copy_address),
                                modifier = Modifier.size(16.dp),
                            )
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun StatusText(state: HomeUiState) {
    AnimatedContent(
        targetState = state.status,
        transitionSpec = { fadeIn() togetherWith fadeOut() },
        contentKey = { it::class },
        label = "status",
    ) { status ->
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = stringResource(status.label),
                style = MaterialTheme.typography.headlineMedium,
            )

            val supporting =
                when (status) {
                    ConnectionStatus.Off -> {
                        stringResource(R.string.status_off_hint)
                    }

                    ConnectionStatus.Connecting -> {
                        stringResource(R.string.status_connecting_hint)
                    }

                    ConnectionStatus.Connected if state.peersTotal == 0 -> {
                        stringResource(R.string.peers_none)
                    }

                    ConnectionStatus.Connected -> {
                        pluralStringResource(
                            R.plurals.peers_online,
                            state.peersTotal,
                            state.peersOnline,
                            state.peersTotal,
                        )
                    }

                    is ConnectionStatus.Degraded -> {
                        status.reason
                    }

                    is ConnectionStatus.Failed -> {
                        status.reason
                    }
                }

            Text(
                text = supporting,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 4.dp, start = 16.dp, end = 16.dp),
            )
        }
    }
}

@Composable
private fun Details(state: HomeUiState) {
    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier =
            Modifier
                .fillMaxWidth()
                .heightIn(max = 280.dp)
                .verticalScroll(rememberScrollState())
                .padding(bottom = 16.dp),
    ) {
        if (state.privateDnsWarning) {
            PrivateDnsCard(state.privateDns.orEmpty())
        }

        state.exitNode?.let { ExitNodeCard(it) }

        if (state.lighthouses.isNotEmpty()) {
            Card(
                colors =
                    CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainer,
                    ),
            ) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.padding(16.dp),
                ) {
                    Text(
                        text = stringResource(R.string.home_lighthouses),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    state.lighthouses.forEach { LighthouseRow(it) }
                }
            }
        }
    }
}

@Composable
private fun PrivateDnsCard(server: String) {
    val context = LocalContext.current
    val colors = MaterialTheme.colorScheme

    Card(colors = CardDefaults.cardColors(containerColor = colors.errorContainer)) {
        Column(modifier = Modifier.padding(start = 16.dp, top = 16.dp, end = 8.dp, bottom = 4.dp)) {
            Text(
                text = stringResource(R.string.home_private_dns, server),
                style = MaterialTheme.typography.bodyMedium,
                color = colors.onErrorContainer,
                modifier = Modifier.padding(end = 8.dp),
            )
            TextButton(
                onClick = { context.startActivity(Intent(Settings.ACTION_WIRELESS_SETTINGS)) },
                modifier = Modifier.align(Alignment.End),
            ) {
                Text(stringResource(R.string.home_private_dns_settings))
            }
        }
    }
}

@Composable
private fun ExitNodeCard(exitNode: ExitNodeStatus) {
    val colors = MaterialTheme.colorScheme
    val container = if (exitNode.active) colors.secondaryContainer else colors.errorContainer
    val summary = if (exitNode.active) R.string.exit_node_active else R.string.exit_node_blocked

    Card(
        colors = CardDefaults.cardColors(containerColor = container),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(16.dp)) {
            Icon(painterResource(R.drawable.ic_exit_node), contentDescription = null)
            Spacer(Modifier.width(16.dp))
            Column {
                Text(
                    text = stringResource(R.string.home_exit_node, exitNode.peer),
                    style = MaterialTheme.typography.titleSmall,
                )
                Text(text = stringResource(summary), style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun LighthouseRow(lighthouse: LighthouseStatus) {
    val colors = MaterialTheme.colorScheme
    val (dot, label) =
        when (val state = lighthouse.state) {
            LighthouseState.Connecting -> {
                colors.tertiary to stringResource(R.string.lighthouse_connecting)
            }

            LighthouseState.Connected -> {
                colors.primary to stringResource(R.string.lighthouse_connected)
            }

            is LighthouseState.Unreachable -> {
                colors.error to state.reason
            }

            is LighthouseState.Rejected -> {
                colors.error to state.reason
            }
        }

    Row(verticalAlignment = Alignment.CenterVertically) {
        StatusDot(dot)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                hostOf(lighthouse.url),
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = listOfNotNull(lighthouse.mesh, label).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = colors.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }

        if (lighthouse.relay) {
            Text(
                text = stringResource(R.string.lighthouse_relay),
                style = MaterialTheme.typography.labelSmall,
                color = colors.onSecondaryContainer,
                modifier =
                    Modifier
                        .background(colors.secondaryContainer, CircleShape)
                        .padding(horizontal = 8.dp, vertical = 2.dp),
            )
        }
    }
}

@Composable
fun StatusDot(
    color: Color,
    modifier: Modifier = Modifier,
) {
    Box(modifier.size(10.dp).background(color, CircleShape))
}

@get:StringRes
val ConnectionStatus.label: Int
    get() =
        when (this) {
            ConnectionStatus.Off -> R.string.status_off
            ConnectionStatus.Connecting -> R.string.status_connecting
            ConnectionStatus.Connected -> R.string.status_connected
            is ConnectionStatus.Degraded -> R.string.status_degraded
            is ConnectionStatus.Failed -> R.string.status_failed
        }
