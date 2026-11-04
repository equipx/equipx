package com.equipx.app.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import com.equipx.app.LocalAppContainer

/** Remembers [deviceId] as the student's last-opened device so Home's "Continue" card points at it. */
@Composable
fun RememberLastDevice(deviceId: String) {
    val prefs = LocalAppContainer.current.uiPrefsRepository
    LaunchedEffect(deviceId) { runCatching { prefs.setLastDevice(deviceId) } }
}
