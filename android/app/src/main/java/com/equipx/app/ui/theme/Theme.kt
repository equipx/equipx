package com.equipx.app.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val EquipXColorScheme = lightColorScheme(
    primary = EquipXColors.Accent,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE7F3F8),
    onPrimaryContainer = EquipXColors.Text,
    secondary = EquipXColors.TextSecondary,
    onSecondary = Color.White,
    background = EquipXColors.Background,
    onBackground = EquipXColors.Text,
    surface = EquipXColors.Background,
    onSurface = EquipXColors.Text,
    surfaceVariant = EquipXColors.Surface,
    onSurfaceVariant = EquipXColors.TextSecondary,
    surfaceTint = Color.Transparent,
    surfaceContainerLowest = EquipXColors.Background,
    surfaceContainerLow = EquipXColors.Background,
    surfaceContainer = EquipXColors.Background,
    surfaceContainerHigh = EquipXColors.Surface,
    surfaceContainerHighest = EquipXColors.Surface,
    outline = EquipXColors.Border,
    outlineVariant = EquipXColors.Border,
    error = EquipXColors.Danger,
    onError = Color.White,
)

private val EquipXShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(6.dp),
    medium = RoundedCornerShape(6.dp),
    large = RoundedCornerShape(6.dp),
    extraLarge = RoundedCornerShape(6.dp),
)

/** Notion-like theme: light only, 6dp shapes, 1dp borders, no elevation. */
@Composable
fun EquipXTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = EquipXColorScheme,
        typography = EquipXTypography,
        shapes = EquipXShapes,
        content = content,
    )
}
