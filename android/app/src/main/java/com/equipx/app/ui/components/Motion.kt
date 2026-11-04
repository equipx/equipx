package com.equipx.app.ui.components

import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect

/**
 * True when the user turned system animations off ("remove animations"); decorative loops should then stay still.
 * Re-read on every resume so toggling the setting while the app is backgrounded takes effect.
 */
@Composable
fun rememberReducedMotion(): Boolean {
    val context = LocalContext.current
    fun read() = runCatching {
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    }.getOrDefault(false)
    var reduced by remember { mutableStateOf(read()) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { reduced = read() }
    return reduced
}
