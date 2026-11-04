package com.equipx.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.Cable
import androidx.compose.material.icons.outlined.DeveloperBoard
import androidx.compose.material.icons.outlined.ElectricalServices
import androidx.compose.material.icons.outlined.LocalFireDepartment
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material.icons.outlined.SettingsInputComponent
import androidx.compose.material.icons.outlined.ShowChart
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material.icons.outlined.Waves
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.equipx.app.ui.theme.Accent
import com.equipx.app.ui.theme.CategoryAccents

/** Icon + soft accent used wherever a device appears. Accent follows the device category. */
data class DeviceStyle(val icon: ImageVector, val accent: Accent)

fun deviceIcon(deviceId: String): ImageVector = when (deviceId) {
    "digital_multimeter" -> Icons.Outlined.Speed
    "analog_multimeter" -> Icons.Outlined.Timer
    "digital_clamp_meter" -> Icons.Outlined.Cable
    "digital_oscilloscope" -> Icons.Outlined.ShowChart
    "function_generator" -> Icons.Outlined.Waves
    "dc_power_supply" -> Icons.Outlined.Bolt
    "soldering_station" -> Icons.Outlined.LocalFireDepartment
    "breadboard_trainer" -> Icons.Outlined.DeveloperBoard
    "insulation_tester" -> Icons.Outlined.ElectricalServices
    "lcr_meter" -> Icons.Outlined.SettingsInputComponent
    else -> Icons.Outlined.Memory
}

fun deviceStyle(deviceId: String, category: String = ""): DeviceStyle =
    DeviceStyle(deviceIcon(deviceId), CategoryAccents.forCategory(category))

/** Tinted rounded tile holding an icon. */
@Composable
fun IconTile(icon: ImageVector, accent: Accent, modifier: Modifier = Modifier, size: Dp = 44.dp, iconSize: Dp = size * 0.5f) {
    Box(
        modifier.size(size).clip(RoundedCornerShape(size * 0.3f)).background(accent.tint),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, contentDescription = null, tint = accent.strong, modifier = Modifier.size(iconSize)) }
}

@Composable
fun DeviceIconTile(deviceId: String, category: String, modifier: Modifier = Modifier, size: Dp = 44.dp) {
    val style = deviceStyle(deviceId, category)
    IconTile(style.icon, style.accent, modifier, size)
}
