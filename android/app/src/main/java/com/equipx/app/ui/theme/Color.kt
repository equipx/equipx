package com.equipx.app.ui.theme

import androidx.compose.ui.graphics.Color

/** Notion-derived palette, extended with soft tints for depth and per-category accents. */
object EquipXColors {
    val Background = Color(0xFFFFFFFF)
    val Surface = Color(0xFFF7F7F5)
    /** Page background behind cards (a touch darker than white so white cards lift off it). */
    val Canvas = Color(0xFFF4F4F1)
    val Border = Color(0xFFE9E9E7)
    val Text = Color(0xFF37352F)
    val TextSecondary = Color(0xFF787774)
    val Accent = Color(0xFF2383E2)
    val AccentDark = Color(0xFF1B64B0)
    val AccentTint = Color(0xFFE6F1FC)
    val Success = Color(0xFF0F7B6C)
    val SuccessTint = Color(0xFFE3F3F0)
    val Danger = Color(0xFFE03E3E)
    val DangerTint = Color(0xFFFCEAEA)
    val Callout = Color(0xFFFBF3DB)
    val CalloutBar = Color(0xFFDFAB01)
    val Gold = Color(0xFFDFAB01)
    val GoldTint = Color(0xFFFBF3DB)
}

/** Soft accent pair: [tint] for tiles/chips, [strong] for the icon and chip text. */
data class Accent(val tint: Color, val strong: Color)

object CategoryAccents {
    val Measurement = Accent(Color(0xFFE6F1FC), Color(0xFF2383E2))
    val Sources = Accent(Color(0xFFFDEEDD), Color(0xFFD9730D))
    val Assembly = Accent(Color(0xFFFAE8F3), Color(0xFFAD1A72))
    val Training = Accent(Color(0xFFE3F3F0), Color(0xFF0F7B6C))
    val Other = Accent(Color(0xFFEEE9F8), Color(0xFF6940A5))

    fun forCategory(category: String): Accent = when (category.trim().lowercase()) {
        "measurement" -> Measurement
        "sources" -> Sources
        "assembly" -> Assembly
        "training" -> Training
        else -> Other
    }
}
