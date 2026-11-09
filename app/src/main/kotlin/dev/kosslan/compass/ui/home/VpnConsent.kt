package dev.kosslan.compass.ui.home

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager.PERMISSION_GRANTED
import android.net.VpnService
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext

/**
 * Returns an action that starts the VPN, first asking for the system's VPN consent and, on
 * Android 13 and later, for permission to show the notification that comes with it.
 */
@Composable
fun rememberVpnStarter(start: () -> Unit): () -> Unit {
    val context = LocalContext.current
    val currentStart by rememberUpdatedState(start)

    val notifications =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
            currentStart()
        }

    val proceed = {
        if (context.needsNotificationPermission()) {
            notifications.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            currentStart()
        }
    }

    val consent =
        rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            if (VpnService.prepare(context) == null) proceed()
        }

    return {
        val intent = VpnService.prepare(context)
        if (intent == null) proceed() else consent.launch(intent)
    }
}

private fun Context.needsNotificationPermission(): Boolean =
    Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
        checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PERMISSION_GRANTED
