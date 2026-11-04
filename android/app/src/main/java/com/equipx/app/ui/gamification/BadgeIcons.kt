package com.equipx.app.ui.gamification

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BugReport
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.EmojiEvents
import androidx.compose.material.icons.outlined.Quiz
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import com.equipx.app.domain.Badge
import com.equipx.app.ui.theme.Accent

fun badgeIcon(b: Badge): ImageVector = when (b) {
    Badge.FIRST_SCAN -> Icons.Outlined.CameraAlt
    Badge.PART_SPOTTER -> Icons.Outlined.Visibility
    Badge.SAFETY_FIRST -> Icons.Outlined.Shield
    Badge.QUIZ_ACE -> Icons.Outlined.Quiz
    Badge.FAULT_FINDER -> Icons.Outlined.BugReport
    Badge.MISSION_COMPLETE -> Icons.Outlined.EmojiEvents
}

/** Each badge has its own soft colour (used when unlocked; locked badges are greyed). */
fun badgeAccent(b: Badge): Accent = when (b) {
    Badge.FIRST_SCAN -> Accent(Color(0xFFE6F1FC), Color(0xFF2383E2))
    Badge.PART_SPOTTER -> Accent(Color(0xFFEEE9F8), Color(0xFF6940A5))
    Badge.SAFETY_FIRST -> Accent(Color(0xFFE3F3F0), Color(0xFF0F7B6C))
    Badge.QUIZ_ACE -> Accent(Color(0xFFFBF3DB), Color(0xFFB88700))
    Badge.FAULT_FINDER -> Accent(Color(0xFFFDEEDD), Color(0xFFD9730D))
    Badge.MISSION_COMPLETE -> Accent(Color(0xFFFAE8F3), Color(0xFFAD1A72))
}
