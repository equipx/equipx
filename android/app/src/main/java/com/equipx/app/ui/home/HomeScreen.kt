package com.equipx.app.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.EmojiEvents
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.equipx.app.data.AuthRepository
import com.equipx.app.data.ContentRepository
import com.equipx.app.data.ProgressRepository
import com.equipx.app.domain.Badge
import com.equipx.app.domain.LevelInfo
import com.equipx.app.domain.Levels
import com.equipx.app.domain.Mission
import com.equipx.app.domain.MissionRules
import com.equipx.app.domain.PointReason
import com.equipx.app.ui.appViewModel
import com.equipx.app.ui.components.Avatar
import com.equipx.app.ui.components.DeviceIconTile
import com.equipx.app.ui.components.EquipXCard
import com.equipx.app.ui.components.EquipXProgressBar
import com.equipx.app.ui.components.ErrorState
import com.equipx.app.ui.components.IconTile
import com.equipx.app.ui.components.LoadingState
import com.equipx.app.ui.components.Pill
import com.equipx.app.ui.components.SectionHeader
import com.equipx.app.ui.learn.DeviceGridCard
import com.equipx.app.ui.learn.LearnItem
import com.equipx.app.ui.theme.Accent
import com.equipx.app.ui.theme.CategoryAccents
import com.equipx.app.ui.theme.EquipXColors
import java.util.Calendar
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/** "Continue where you left off": the device the student last interacted with. */
data class ContinueItem(val deviceId: String, val deviceName: String, val category: String, val caption: String)

/** The mission shown on Home: the active one, otherwise the first unfinished one ([isActive] false). */
data class HomeMission(val mission: Mission, val done: Int, val total: Int, val nextTask: String?, val isActive: Boolean) {
    val fraction: Float get() = if (total == 0) 0f else done.toFloat() / total
}

data class HomeData(
    val greeting: String = "Hello",
    val userName: String = "Student",
    val points: Int = 0,
    val level: LevelInfo = Levels.of(0),
    val badgesUnlocked: Int = 0,
    val badgesTotal: Int = Badge.entries.size,
    val continueItem: ContinueItem? = null,
    val mission: HomeMission? = null,
    val devices: List<LearnItem> = emptyList(),
)

sealed interface HomeUiState {
    data object Loading : HomeUiState
    data class Error(val message: String) : HomeUiState
    data class Loaded(val data: HomeData) : HomeUiState
}

class HomeViewModel(
    private val content: ContentRepository,
    private val progress: ProgressRepository,
    private val auth: AuthRepository,
) : ViewModel() {
    private val _state = MutableStateFlow<HomeUiState>(HomeUiState.Loading)
    val state: StateFlow<HomeUiState> = _state.asStateFlow()

    init { load() }

    fun load() {
        _state.value = HomeUiState.Loading
        viewModelScope.launch {
            val index = content.getIndex().getOrElse {
                _state.value = HomeUiState.Error("Could not load the device list.")
                return@launch
            }
            val counts = index.map { d -> async { d to content.getDevice(d.id).getOrNull()?.parts?.size } }.awaitAll()
            val missions = content.getMissions().getOrNull().orEmpty()
            combine(auth.currentUser, progress.progress, progress.missionState, progress.quizAttempts) { user, p, ms, attempts ->
                val devices = counts.map { (d, n) -> LearnItem(d, n, p.viewedParts.count { it.startsWith(d.id + ":") }) }
                val last = p.ledger.lastOrNull { e -> index.any { e.key.startsWith(it.id + ":") } }
                val lastDevice = last?.let { e -> index.first { e.key.startsWith(it.id + ":") } }
                val lastAttempt = attempts.lastOrNull()
                val cont = when {
                    lastAttempt != null && (last == null || lastAttempt.completedAtMillis >= last.timestampMillis) ->
                        index.firstOrNull { it.id == lastAttempt.deviceId }?.let {
                            ContinueItem(it.id, it.name, it.category, "Last quiz: ${lastAttempt.quizTitle.ifBlank { "Quiz" }} (${lastAttempt.correct}/${lastAttempt.total})")
                        }
                    lastDevice != null -> ContinueItem(lastDevice.id, lastDevice.name, lastDevice.category, captionFor(last.reason))
                    else -> null
                }
                val shown = missions.firstOrNull { it.id == ms.activeMissionId }
                    ?: missions.firstOrNull { it.id !in p.completedMissions }
                val mission = shown?.let { m ->
                    val done = ms.doneTasks[m.id].orEmpty()
                    HomeMission(m, done.size, m.tasks.size, MissionRules.currentTask(m, done)?.title, isActive = m.id == ms.activeMissionId)
                }
                HomeData(
                    greeting = greetingNow(),
                    userName = user?.name ?: "there",
                    points = p.points,
                    level = Levels.of(p.points),
                    badgesUnlocked = p.unlockedBadges.size,
                    continueItem = cont,
                    mission = mission,
                    devices = devices,
                )
            }.collect { _state.value = HomeUiState.Loaded(it) }
        }
    }

    private fun captionFor(r: PointReason) = when (r) {
        PointReason.SCAN -> "You scanned this last. Pick up where you left off."
        PointReason.PART_VIEW -> "Keep exploring its parts."
        PointReason.QUIZ_CORRECT -> "Keep practising with its quizzes."
        PointReason.FAULT_FIXED -> "Try another troubleshooting scenario."
        PointReason.MISSION_COMPLETE -> "Open the device."
    }

    private fun greetingNow(): String {
        val h = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        return when {
            h < 12 -> "Good morning"
            h < 18 -> "Good afternoon"
            else -> "Good evening"
        }
    }
}

@Composable
fun HomeScreen(
    onScan: () -> Unit,
    onBrowse: () -> Unit,
    onOpenDevice: (String) -> Unit,
    onOpenMissions: () -> Unit,
) {
    val vm = appViewModel { HomeViewModel(it.contentRepository, it.progressRepository, it.authRepository) }
    val state by vm.state.collectAsStateWithLifecycle()
    HomeContent(state, vm::load, onScan, onBrowse, onOpenDevice, onOpenMissions)
}

@Composable
fun HomeContent(
    state: HomeUiState,
    onRetry: () -> Unit,
    onScan: () -> Unit,
    onBrowse: () -> Unit,
    onOpenDevice: (String) -> Unit,
    onOpenMissions: () -> Unit,
) {
    Box(Modifier.fillMaxSize().background(EquipXColors.Canvas)) {
        when (state) {
            HomeUiState.Loading -> LoadingState()
            is HomeUiState.Error -> ErrorState(state.message, onRetry)
            is HomeUiState.Loaded -> {
                val d = state.data
                LazyColumn(
                    Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    item { Header(d) }
                    item { StatsHero(d) }
                    d.continueItem?.let { c -> item { ContinueCard(c, onClick = { onOpenDevice(c.deviceId) }) } }
                    d.mission?.let { m -> item { MissionCard(m, onClick = onOpenMissions) } }
                    item {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            QuickAction("Scan a device", "Point the camera", Icons.Outlined.CameraAlt, EquipXColors.Accent, Color.White, onScan, Modifier.weight(1f))
                            QuickAction("Browse equipment", "All ${d.devices.size} devices", Icons.Outlined.GridView, EquipXColors.AccentTint, EquipXColors.Accent, onBrowse, Modifier.weight(1f))
                        }
                    }
                    item { SectionHeader("Equipment", topPadding = 10.dp) }
                    val rows = d.devices.chunked(2)
                    items(rows.size) { i ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            rows[i].forEach { item -> DeviceGridCard(item, onClick = { onOpenDevice(item.summary.id) }, modifier = Modifier.weight(1f)) }
                            if (rows[i].size == 1) Box(Modifier.weight(1f))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Header(d: HomeData) {
    Row(
        Modifier.fillMaxWidth().statusBarsPadding().padding(top = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(d.greeting, style = MaterialTheme.typography.bodyMedium, color = EquipXColors.TextSecondary)
            Text(d.userName, style = MaterialTheme.typography.headlineMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Avatar(d.userName)
    }
}

/** Hero card (the one place a gradient is used): points, level and badges at a glance. */
@Composable
private fun StatsHero(d: HomeData) {
    val shape = RoundedCornerShape(20.dp)
    Column(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Brush.linearGradient(listOf(EquipXColors.Accent, EquipXColors.AccentDark)))
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(Modifier.size(44.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.2f)), contentAlignment = Alignment.Center) {
                Icon(Icons.Outlined.Star, contentDescription = null, tint = Color.White)
            }
            Column(Modifier.weight(1f)) {
                Text("${d.points}", style = MaterialTheme.typography.headlineMedium, color = Color.White)
                Text("points · Level ${d.level.level}", style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.85f))
            }
            Row(
                Modifier.clip(RoundedCornerShape(50)).background(Color.White.copy(alpha = 0.2f)).padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Icon(Icons.Outlined.EmojiEvents, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                Text("${d.badgesUnlocked}/${d.badgesTotal} badges", style = MaterialTheme.typography.labelLarge, color = Color.White)
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            EquipXProgressBar(d.level.fraction, color = Color.White, trackColor = Color.White.copy(alpha = 0.25f))
            Text(
                "${d.level.pointsForNext - d.level.pointsIntoLevel} points to level ${d.level.level + 1}",
                style = MaterialTheme.typography.labelMedium,
                color = Color.White.copy(alpha = 0.85f),
            )
        }
    }
}

@Composable
private fun ContinueCard(c: ContinueItem, onClick: () -> Unit) {
    EquipXCard(Modifier.fillMaxWidth(), onClick = onClick) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            DeviceIconTile(c.deviceId, c.category, size = 52.dp)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("CONTINUE", style = MaterialTheme.typography.labelSmall, color = EquipXColors.Accent)
                Text(c.deviceName, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(c.caption, style = MaterialTheme.typography.bodySmall, color = EquipXColors.TextSecondary, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            Box(Modifier.size(40.dp).clip(CircleShape).background(EquipXColors.AccentTint), contentAlignment = Alignment.Center) {
                Icon(Icons.Outlined.PlayArrow, contentDescription = "Continue", tint = EquipXColors.Accent)
            }
        }
    }
}

@Composable
private fun MissionCard(m: HomeMission, onClick: () -> Unit) {
    val accent = Accent(EquipXColors.GoldTint, EquipXColors.CalloutBar)
    EquipXCard(Modifier.fillMaxWidth(), onClick = onClick) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                IconTile(Icons.Outlined.EmojiEvents, accent, size = 44.dp)
                Column(Modifier.weight(1f)) {
                    Text(if (m.isActive) "ACTIVE MISSION" else "SUGGESTED MISSION", style = MaterialTheme.typography.labelSmall, color = EquipXColors.TextSecondary)
                    Text(m.mission.title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Pill("${m.done}/${m.total}", background = EquipXColors.AccentTint, contentColor = EquipXColors.Accent)
            }
            EquipXProgressBar(m.fraction)
            m.nextTask?.let {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Next: $it", style = MaterialTheme.typography.bodySmall, color = EquipXColors.TextSecondary, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Icon(Icons.AutoMirrored.Outlined.ArrowForward, contentDescription = null, tint = EquipXColors.Accent, modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}

@Composable
private fun QuickAction(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    container: Color,
    content: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val primary = container == EquipXColors.Accent
    EquipXCard(modifier, onClick = onClick, containerColor = container, borderColor = if (primary) container else EquipXColors.Border) {
        Column(Modifier.padding(16.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(icon, contentDescription = null, tint = content, modifier = Modifier.size(28.dp))
            Column {
                Text(title, style = MaterialTheme.typography.titleSmall, color = if (primary) Color.White else EquipXColors.Text)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = if (primary) Color.White.copy(alpha = 0.85f) else EquipXColors.TextSecondary)
            }
        }
    }
}
