package com.equipx.app.ui.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Checklist
import androidx.compose.material.icons.outlined.EmojiEvents
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Percent
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.outlined.BugReport
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.equipx.app.LocalAppContainer
import com.equipx.app.domain.Badge
import com.equipx.app.domain.ProgressState
import com.equipx.app.domain.QuizScorer
import com.equipx.app.domain.Role
import com.equipx.app.ui.components.EquipXTopBar
import com.equipx.app.ui.components.HairlineDivider
import com.equipx.app.ui.components.ListRow
import com.equipx.app.ui.components.Pill
import com.equipx.app.ui.components.SecondaryButton
import com.equipx.app.ui.components.SectionHeader
import com.equipx.app.ui.gamification.badgeIcon
import com.equipx.app.ui.theme.EquipXColors
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date

@Composable
fun ProfileScreen(onOpenCredits: () -> Unit, onLoggedOut: () -> Unit) {
    val c = LocalAppContainer.current
    val user by c.authRepository.currentUser.collectAsStateWithLifecycle(initialValue = null)
    val progress by c.progressRepository.progress.collectAsStateWithLifecycle(initialValue = ProgressState())
    val attempts by c.progressRepository.quizAttempts.collectAsStateWithLifecycle(initialValue = emptyList())
    val scope = rememberCoroutineScope()
    val accuracy = QuizScorer.aggregate(attempts)
    val unlocked = progress.unlockedBadges.map { it.id }.toSet()
    val recent = progress.ledger.takeLast(15).reversed()

    Column(Modifier.fillMaxSize()) {
        EquipXTopBar(title = "Profile")
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).navigationBarsPadding().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(user?.name ?: "Guest", style = MaterialTheme.typography.titleLarge)
                user?.let { Pill(if (it.role == Role.TEACHER) "Teacher" else "Student") }
            }
            Text(user?.email.orEmpty(), style = MaterialTheme.typography.bodyMedium, color = EquipXColors.TextSecondary)

            ListRow(title = "Points", meta = "${progress.points}", icon = Icons.Outlined.Star)
            ListRow(
                title = "Quiz accuracy",
                meta = if (accuracy.totalQuestions == 0) "No quizzes taken yet"
                else "${accuracy.accuracyPercent}% across ${accuracy.attempts} attempt${if (accuracy.attempts == 1) "" else "s"}",
                icon = Icons.Outlined.Percent,
            )
            ListRow(title = "Parts viewed", meta = "${progress.viewedParts.size}", icon = Icons.Outlined.Visibility)
            ListRow(title = "Procedures completed", meta = "${progress.completedProcedures.size}", icon = Icons.Outlined.Checklist)
            ListRow(title = "Faults fixed", meta = "${progress.fixedFaults.size}", icon = Icons.Outlined.BugReport)
            ListRow(title = "Missions completed", meta = "${progress.completedMissions.size}", icon = Icons.Outlined.EmojiEvents)

            SectionHeader("Badges", count = unlocked.size)
            Badge.entries.chunked(3).forEach { row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.forEach { b -> BadgeTile(b, b.id in unlocked, Modifier.weight(1f)) }
                    repeat(3 - row.size) { Box(Modifier.weight(1f)) }
                }
            }

            SectionHeader("Recent activity")
            if (recent.isEmpty()) {
                Text("Scan a device, explore parts or take a quiz to earn points.", style = MaterialTheme.typography.bodySmall, color = EquipXColors.TextSecondary)
            } else {
                recent.forEach { e ->
                    ListRow(
                        title = e.reason.title,
                        meta = historyMeta(e.key, e.timestampMillis),
                        trailing = { Pill("+${e.amount}", contentColor = EquipXColors.Success) },
                    )
                    HairlineDivider()
                }
            }

            ListRow(title = "Credits & licenses", meta = "3D models and open-source software", icon = Icons.Outlined.Info, onClick = onOpenCredits)
            SecondaryButton(
                text = "Log out",
                modifier = Modifier.fillMaxWidth(),
                onClick = {
                    scope.launch {
                        c.authRepository.logout()
                        onLoggedOut()
                    }
                },
            )
        }
    }
}

private fun historyMeta(key: String, millis: Long): String {
    val what = key.split(':').joinToString(" · ") { it.replace('_', ' ') }
    if (millis <= 0L) return what
    return what + " · " + DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(millis))
}

@Composable
private fun BadgeTile(badge: Badge, unlocked: Boolean, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(6.dp)
    Column(
        modifier
            .clip(shape)
            .background(if (unlocked) Color.White else EquipXColors.Surface)
            .border(1.dp, if (unlocked) EquipXColors.Accent else EquipXColors.Border, shape)
            .padding(10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Icon(
            badgeIcon(badge),
            contentDescription = if (unlocked) "${badge.title}, unlocked" else "${badge.title}, locked",
            tint = if (unlocked) EquipXColors.Accent else EquipXColors.Border,
            modifier = Modifier.size(28.dp),
        )
        Text(
            badge.title,
            style = MaterialTheme.typography.labelMedium,
            color = if (unlocked) EquipXColors.Text else EquipXColors.TextSecondary,
            textAlign = TextAlign.Center,
        )
        Text(badge.description, style = MaterialTheme.typography.labelSmall, color = EquipXColors.TextSecondary, textAlign = TextAlign.Center)
    }
}
