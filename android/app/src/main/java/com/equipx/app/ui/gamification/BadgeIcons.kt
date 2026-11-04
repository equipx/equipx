package com.equipx.app.ui.gamification

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BugReport
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.EmojiEvents
import androidx.compose.material.icons.outlined.Quiz
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.ui.graphics.vector.ImageVector
import com.equipx.app.domain.Badge

fun badgeIcon(b: Badge): ImageVector = when (b) {
    Badge.FIRST_SCAN -> Icons.Outlined.CameraAlt
    Badge.PART_SPOTTER -> Icons.Outlined.Visibility
    Badge.SAFETY_FIRST -> Icons.Outlined.Shield
    Badge.QUIZ_ACE -> Icons.Outlined.Quiz
    Badge.FAULT_FINDER -> Icons.Outlined.BugReport
    Badge.MISSION_COMPLETE -> Icons.Outlined.EmojiEvents
}
