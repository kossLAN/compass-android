package dev.kosslan.compass.ui.home

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import dev.kosslan.compass.R

private const val RESTING_ANGLE = -40f
private const val WOBBLE = 18f
private val SETTLE =
    spring<Float>(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow)
private val SWING = tween<Float>(durationMillis = 700, easing = FastOutSlowInEasing)

@Composable
fun ActivateButton(
    status: ConnectionStatus,
    onToggle: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val haptics = LocalHapticFeedback.current
    val active = status.active
    val connecting = status == ConnectionStatus.Connecting

    val container by animateColorAsState(
        targetValue =
            when (status) {
                ConnectionStatus.Off -> colors.surfaceContainerHighest
                ConnectionStatus.Connecting -> colors.primaryContainer
                ConnectionStatus.Connected -> colors.primary
                is ConnectionStatus.Degraded -> colors.tertiaryContainer
                is ConnectionStatus.Failed -> colors.errorContainer
            },
        animationSpec = tween(durationMillis = 450),
        label = "container",
    )
    val content by animateColorAsState(
        targetValue =
            when (status) {
                ConnectionStatus.Off -> colors.onSurfaceVariant
                ConnectionStatus.Connecting -> colors.onPrimaryContainer
                ConnectionStatus.Connected -> colors.onPrimary
                is ConnectionStatus.Degraded -> colors.onTertiaryContainer
                is ConnectionStatus.Failed -> colors.onErrorContainer
            },
        animationSpec = tween(durationMillis = 450),
        label = "content",
    )
    val diameter by animateDpAsState(
        targetValue = if (active) 196.dp else 176.dp,
        animationSpec =
            spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessLow,
            ),
        label = "diameter",
    )
    val corners by animateIntAsState(
        targetValue = if (status == ConnectionStatus.Connected) 32 else 50,
        animationSpec = spring(stiffness = Spring.StiffnessLow),
        label = "corners",
    )

    val needle = remember { Animatable(RESTING_ANGLE) }
    val halo = remember { Animatable(0f) }

    LaunchedEffect(connecting, active) {
        when {
            connecting -> {
                while (true) {
                    needle.animateTo(WOBBLE, SWING)
                    needle.animateTo(-WOBBLE, SWING)
                }
            }

            active -> {
                needle.animateTo(0f, SETTLE)
            }

            else -> {
                needle.animateTo(RESTING_ANGLE, SETTLE)
            }
        }
    }

    LaunchedEffect(connecting) {
        halo.snapTo(0f)

        if (connecting) {
            halo.animateTo(
                targetValue = 1f,
                animationSpec = infiniteRepeatable(tween(durationMillis = 1_600)),
            )
        }
    }

    val description = stringResource(R.string.activate_description)
    val state = stringResource(status.label)
    val haloColor = colors.primary

    Box(
        contentAlignment = Alignment.Center,
        modifier =
            modifier
                .size(260.dp)
                .drawBehind {
                    if (connecting) {
                        val radius = diameter.toPx() / 2 * (1f + halo.value * 0.3f)
                        val alpha = 0.28f * (1f - halo.value)
                        drawCircle(haloColor.copy(alpha = alpha), radius = radius)
                    }
                },
    ) {
        Surface(
            checked = active,
            onCheckedChange = { enabled ->
                haptics.performHapticFeedback(
                    if (enabled) HapticFeedbackType.ToggleOn else HapticFeedbackType.ToggleOff,
                )
                onToggle(enabled)
            },
            shape = RoundedCornerShape(percent = corners),
            color = container,
            contentColor = content,
            shadowElevation = if (active) 8.dp else 2.dp,
            modifier =
                Modifier.size(diameter).semantics {
                    contentDescription = description
                    stateDescription = state
                    role = Role.Switch
                },
        ) {
            Box(contentAlignment = Alignment.Center) {
                Needle(
                    color = content,
                    hub = container,
                    modifier =
                        Modifier
                            .size(diameter * 0.5f)
                            .graphicsLayer { rotationZ = needle.value },
                )
            }
        }
    }
}

@Composable
private fun Needle(
    color: Color,
    hub: Color,
    modifier: Modifier,
) {
    Canvas(modifier) {
        val half = size.width * 0.14f
        val north =
            Path().apply {
                moveTo(center.x, 0f)
                lineTo(center.x + half, center.y)
                lineTo(center.x - half, center.y)
                close()
            }
        val south =
            Path().apply {
                moveTo(center.x - half, center.y)
                lineTo(center.x + half, center.y)
                lineTo(center.x, size.height)
                close()
            }

        drawPath(north, color)
        drawPath(south, color.copy(alpha = 0.35f))
        drawCircle(hub, radius = half * 0.45f)
    }
}
