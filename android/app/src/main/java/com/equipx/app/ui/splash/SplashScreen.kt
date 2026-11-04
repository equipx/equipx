package com.equipx.app.ui.splash

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.foundation.background
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
        delay(150) // the system splash already showed the brand; just a beat before routing
        val user = runCatching { auth.currentUser.first() }.getOrNull()
        if (user != null) loggedIn.value() else loggedOut.value()
    }
    // Matches the system splash (accent background) so the handoff is seamless; nothing to read here.
    Box(Modifier.fillMaxSize().background(EquipXColors.Accent))
}
