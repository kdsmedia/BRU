package com.altomedia.beruang

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.altomedia.beruang.ui.AppNavHost
import com.altomedia.beruang.ui.theme.BeruangTheme
import com.altomedia.beruang.ui.theme.BgBody

/**
 * Single-activity entry point. The splash screen is shown while auth state
 * is being resolved, then hands off to the Compose navigation host.
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            BeruangTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = BgBody) {
                    AppNavHost()
                }
            }
        }
    }
}
