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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BugReport
import androidx.compose.material.icons.outlined.Checklist
import androidx.compose.material.icons.outlined.EmojiEvents
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Percent
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.equipx.app.LocalAppContainer
import com.equipx.app.domain.Badge
import com.equipx.app.domain.LevelInfo
import com.equipx.app.domain.Levels
import com.equipx.app.domain.PointEntry
import com.equipx.app.domain.ProgressState
import com.equipx.app.domain.QuizScorer
import com.equipx.app.domain.Role
import com.equipx.app.domain.User
import com.equipx.app.ui.components.Avatar
import com.equipx.app.ui.components.EquipXCard
import com.equipx.app.ui.components.EquipXProgressBar
import com.equipx.app.ui.components.IconTile
import com.equipx.app.ui.components.ListRow
import com.equipx.app.ui.components.Pill
import com.equipx.app.ui.components.SecondaryButton
import com.equipx.app.ui.components.SectionHeader
import com.equipx.app.ui.components.TabTitle
import com.equipx.app.ui.gamification.badgeAccent
import com.equipx.app.ui.gamification.badgeIcon
import com.equipx.app.ui.theme.Accent
import com.equipx.app.ui.theme.EquipXColors
import java.text.DateFormat
import java.util.Date
import kotlinx.coroutines.launch

/** Everything the profile shows, so the content composable is stateless. */
data class ProfileData(
    val user: User?,
    val progress: ProgressState,
    val quizAttempts: Int,
    val quizAccuracyPercent: Int,
    val quizQuestions: Int,
)

@Composable
fun ProfileScreen(onOpenCredits: () -> Unit, onLoggedOut: () -> Unit) {
    val c = LocalAppContainer.current
    val user by c.authRepository.currentUser.collectAsStateWithLifecycle(initialValue = null)
    val progress by c.progressRepository.progress.collectAsStateWithLifecycle(initialValue = ProgressState())
    val attempts by c.progressRepository.quizAttempts.collectAsStateWithLifecycle(initialValue = emptyList())
    val scope = rememberCoroutineScope()
    val acc = QuizScorer.aggregate(attempts)
    ProfileContent(
        data = ProfileData(user, progress, acc.attempts, acc.accuracyPercent, acc.totalQuestions),
        onOpenCredits = onOpenCredits,
        onLogout = {
            scope.launch {
                c.authRepository.logout()
                onLoggedOut()
            }
        },
    )
}

@Composable
fun ProfileContent(data: ProfileData, onOpenCredits: () -> Unit, onLogout: () -> Unit) {
    val progress = data.progress
    val unlocked = progress.unlockedBadges.map { it.id }.toSet()
    val recent = progress.ledger.takeLast(15).reversed()
    val level = Levels.of(progress.points)

    Column(Modifier.fillMaxSize().background(EquipXColors.Canvas)) {
        TabTitle("Profile")
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).navigationBarsPadding().padding(horizontal = 16.dp).padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            HeaderCard(data.user, progress.points, level)

            EquipXCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(horizontal = 12.dp, vertical = 4.dp)) {
                    StatRow("Quiz accuracy", if (data.quizQuestions == 0) "No quizzes taken yet"
                    else "${data.quizAccuracyPercent}% across ${data.quizAttempts} attempt${if (data.quizAttempts == 1) "" else "s"}",
                        Icons.Outlined.Percent, Accent(Color(0xFFEEE9F8), Color(0xFF6940A5)))
                    StatRow("Parts viewed", "${progress.viewedParts.size}", Icons.Outlined.Visibility, Accent(EquipXColors.AccentTint, EquipXColors.Accent))
                    StatRow("Procedures completed", "${progress.completedProcedures.size}", Icons.Outlined.Checklist, Accent(EquipXColors.SuccessTint, EquipXColors.Success))
                    StatRow("Faults fixed", "${progress.fixedFaults.size}", Icons.Outlined.BugReport, Accent(Color(0xFFFDEEDD), Color(0xFFD9730D)))
                    StatRow("Missions completed", "${progress.completedMissions.size}", Icons.Outlined.EmojiEvents, Accent(EquipXColors.GoldTint, Color(0xFFB88700)))
                }
            }

            SectionHeader("Badges", count = unlocked.size, topPadding = 16.dp)
            Badge.entries.chunked(3).forEach { row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    row.forEach { b -> BadgeTile(b, b.id in unlocked, Modifier.weight(1f)) }
                    repeat(3 - row.size) { Box(Modifier.weight(1f)) }
                }
            }

            SectionHeader("Recent activity", topPadding = 16.dp)
            if (recent.isEmpty()) {
                Text("Scan a device, explore parts or take a quiz to earn points.", style = MaterialTheme.typography.bodyMedium, color = EquipXColors.TextSecondary)
            } else {
                EquipXCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(horizontal = 12.dp, vertical = 4.dp)) {
                        recent.forEach { e: PointEntry ->
                            ListRow(
                                title = e.reason.title,
                                meta = historyMeta(e.key, e.timestampMillis),
                                trailing = { Pill("+${e.amount}", background = EquipXColors.SuccessTint, contentColor = EquipXColors.Success) },
                            )
                        }
                    }
                }
            }

            EquipXCard(Modifier.fillMaxWidth().padding(top = 12.dp), onClick = onOpenCredits) {
                ListRow(
                    title = "Credits & licenses",
                    meta = "3D models and open-source software",
                    icon = Icons.Outlined.Info,
                    accent = Accent(EquipXColors.Surface, EquipXColors.TextSecondary),
                    modifier = Modifier.padding(horizontal = 12.dp),
                )
            }
            SecondaryButton(text = "Log out", modifier = Modifier.fillMaxWidth(), onClick = onLogout)
        }
    }
}

@Composable
private fun HeaderCard(user: User?, points: Int, level: LevelInfo) {
    val shape = RoundedCornerShape(20.dp)
    Column(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Brush.linearGradient(listOf(EquipXColors.Accent, EquipXColors.AccentDark)))
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Avatar(user?.name ?: "Guest", size = 64.dp, modifier = Modifier.border(3.dp, Color.White, CircleShape))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(user?.name ?: "Guest", style = MaterialTheme.typography.headlineSmall, color = Color.White)
                Text(user?.email.orEmpty(), style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.85f))
                user?.let { Pill(if (it.role == Role.TEACHER) "Teacher" else "Student", background = Color.White.copy(alpha = 0.2f), contentColor = Color.White, modifier = Modifier.padding(top = 4.dp)) }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            Column {
                Text("$points", style = MaterialTheme.typography.headlineMedium, color = Color.White)
                Text("points", style = MaterialTheme.typography.labelMedium, color = Color.White.copy(alpha = 0.85f))
            }
            Column {
                Text("Level ${level.level}", style = MaterialTheme.typography.headlineMedium, color = Color.White)
                Text("${level.pointsForNext - level.pointsIntoLevel} to next", style = MaterialTheme.typography.labelMedium, color = Color.White.copy(alpha = 0.85f))
            }
        }
        EquipXProgressBar(level.fraction, color = Color.White, trackColor = Color.White.copy(alpha = 0.25f))
    }
}

@Composable
private fun StatRow(title: String, value: String, icon: ImageVector, accent: Accent) {
    ListRow(title = title, meta = value, icon = icon, accent = accent)
}

private fun historyMeta(key: String, millis: Long): String {
    val what = key.split(':').joinToString(" · ") { it.replace('_', ' ') }
    if (millis <= 0L) return what
    return what + " · " + DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(millis))
}

@Composable
private fun BadgeTile(badge: Badge, unlocked: Boolean, modifier: Modifier = Modifier) {
    val accent = badgeAccent(badge)
    EquipXCard(
        modifier,
        containerColor = if (unlocked) Color.White else EquipXColors.Surface,
        borderColor = if (unlocked) accent.strong.copy(alpha = 0.5f) else EquipXColors.Border,
        elevation = if (unlocked) 3.dp else 0.dp,
    ) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Box(contentAlignment = Alignment.BottomEnd) {
                IconTile(
                    badgeIcon(badge),
                    if (unlocked) accent else Accent(EquipXColors.Border, EquipXColors.TextSecondary.copy(alpha = 0.6f)),
                    size = 52.dp,
                )
                if (!unlocked) {
                    Box(Modifier.size(20.dp).clip(CircleShape).background(Color.White), contentAlignment = Alignment.Center) {
                        Icon(Icons.Outlined.Lock, contentDescription = "${badge.title}, locked", tint = EquipXColors.TextSecondary, modifier = Modifier.size(13.dp))
                    }
                }
            }
            Text(
                badge.title,
                style = MaterialTheme.typography.labelLarge,
                color = if (unlocked) EquipXColors.Text else EquipXColors.TextSecondary,
                textAlign = TextAlign.Center,
            )
            Text(badge.description, style = MaterialTheme.typography.labelSmall, color = EquipXColors.TextSecondary, textAlign = TextAlign.Center)
            if (unlocked) Text("Unlocked", style = MaterialTheme.typography.labelSmall, color = accent.strong)
        }
    }
}
