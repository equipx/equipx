package com.equipx.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.equipx.app.ui.gamification.BadgeToastHost
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.lifecycleScope
import com.equipx.app.ui.nav.EquipXNavHost
import com.equipx.app.ui.nav.Routes
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import com.equipx.app.ui.theme.EquipXTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        val splash = installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val container = (application as EquipXApp).container
        // Null until the stored session is read; the system splash stays up until then, then we route straight in.
        var startRoute by mutableStateOf<String?>(null)
        splash.setKeepOnScreenCondition { startRoute == null }
        lifecycleScope.launch {
            val user = withTimeoutOrNull(3_000) { runCatching { container.authRepository.currentUser.first() }.getOrNull() }
            startRoute = if (user != null) Routes.MAIN else Routes.LOGIN
        }
        setContent {
            CompositionLocalProvider(LocalAppContainer provides container) {
                EquipXTheme {
                    Box(Modifier.fillMaxSize()) {
                        startRoute?.let { EquipXNavHost(it) }
                        BadgeToastHost(container.activityTracker, Modifier.align(Alignment.TopCenter))
                    }
                }
            }
        }
    }
}
