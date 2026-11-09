package dev.kosslan.compass

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import dev.kosslan.compass.ui.CompassApp
import dev.kosslan.compass.ui.LocalAppContainer
import dev.kosslan.compass.ui.theme.CompassTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val container = (application as CompassApplication).container

        setContent {
            CompassTheme {
                CompositionLocalProvider(LocalAppContainer provides container) {
                    CompassApp()
                }
            }
        }
    }
}
