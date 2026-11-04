package com.equipx.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
import com.equipx.app.ui.nav.EquipXNavHost
import com.equipx.app.ui.theme.EquipXTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val container = (application as EquipXApp).container
        setContent {
            CompositionLocalProvider(LocalAppContainer provides container) {
                EquipXTheme { EquipXNavHost() }
            }
        }
    }
}
