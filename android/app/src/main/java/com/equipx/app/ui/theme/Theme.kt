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
    primaryContainer = EquipXColors.AccentTint,
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
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(24.dp),
)

/** Light theme: Notion palette, 12-16dp card radii, soft elevation on cards, 1dp borders for tinted surfaces. */
@Composable
fun EquipXTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = EquipXColorScheme,
        typography = EquipXTypography,
        shapes = EquipXShapes,
        content = content,
    )
}
