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
import com.equipx.app.ui.nav.EquipXNavHost
import com.equipx.app.ui.theme.EquipXTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val container = (application as EquipXApp).container
        setContent {
            CompositionLocalProvider(LocalAppContainer provides container) {
                EquipXTheme {
                    Box(Modifier.fillMaxSize()) {
                        EquipXNavHost()
                        BadgeToastHost(container.activityTracker, Modifier.align(Alignment.TopCenter))
                    }
                }
            }
        }
    }
}
