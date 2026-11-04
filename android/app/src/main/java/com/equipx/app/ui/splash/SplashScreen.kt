package com.equipx.app.ui.splash

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.background
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.equipx.app.LocalAppContainer
import com.equipx.app.ui.theme.EquipXColors
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first

@Composable
fun SplashScreen(onLoggedIn: () -> Unit, onLoggedOut: () -> Unit) {
    val auth = LocalAppContainer.current.authRepository
    val loggedIn = rememberUpdatedState(onLoggedIn)
    val loggedOut = rememberUpdatedState(onLoggedOut)
    LaunchedEffect(Unit) {
        delay(700)
        val user = runCatching { auth.currentUser.first() }.getOrNull()
        if (user != null) loggedIn.value() else loggedOut.value()
    }
    Box(Modifier.fillMaxSize().background(Color.White), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(
                Modifier.size(56.dp).clip(RoundedCornerShape(12.dp)).background(EquipXColors.Accent),
                contentAlignment = Alignment.Center,
            ) { Text("X", style = MaterialTheme.typography.headlineSmall, color = Color.White) }
            Text("EquipX", style = MaterialTheme.typography.headlineSmall)
            Text("Learn lab equipment", style = MaterialTheme.typography.bodyMedium, color = EquipXColors.TextSecondary)
        }
    }
}
