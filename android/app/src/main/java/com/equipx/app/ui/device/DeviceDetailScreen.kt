package com.equipx.app.ui.device

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BugReport
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Checklist
import androidx.compose.material.icons.outlined.Quiz
import androidx.compose.material.icons.outlined.ViewInAr
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
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.equipx.app.data.ContentRepository
import com.equipx.app.data.ProgressRepository
import com.equipx.app.domain.Device
import com.equipx.app.domain.QuizAttempt
import com.equipx.app.domain.QuizScorer
import com.equipx.app.ui.appViewModel
import com.equipx.app.ui.components.AccentChip
import com.equipx.app.ui.components.Callout
import com.equipx.app.ui.components.DeviceIconTile
import com.equipx.app.ui.components.EquipXCard
import com.equipx.app.ui.components.EquipXTopBar
import com.equipx.app.ui.components.ErrorState
import com.equipx.app.ui.components.HairlineDivider
import com.equipx.app.ui.components.IconTile
import com.equipx.app.ui.components.ListRow
import com.equipx.app.ui.components.LoadingState
import com.equipx.app.ui.components.NumberBadge
import com.equipx.app.ui.components.Pill
import com.equipx.app.ui.components.ProgressRing
import com.equipx.app.ui.components.SectionHeader
import com.equipx.app.ui.theme.Accent
import com.equipx.app.ui.theme.CategoryAccents
import com.equipx.app.ui.theme.EquipXColors
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface DeviceDetailState {
    data object Loading : DeviceDetailState
    data class Error(val message: String) : DeviceDetailState
    data class Loaded(
        val device: Device,
        val viewedPartIds: Set<String> = emptySet(),
        val completedProcedureIds: Set<String> = emptySet(),
        val attempts: List<QuizAttempt> = emptyList(),
        val fixedFaultIds: Set<String> = emptySet(),
    ) : DeviceDetailState
}

private data class ProgressSnapshot(
    val viewed: Set<String>,
    val procedures: Set<String>,
    val attempts: List<QuizAttempt>,
    val faults: Set<String>,
)

class DeviceDetailViewModel(
    private val deviceId: String,
    private val content: ContentRepository,
    private val progress: ProgressRepository,
) : ViewModel() {
    private val _state = MutableStateFlow<DeviceDetailState>(DeviceDetailState.Loading)
    val state: StateFlow<DeviceDetailState> = _state.asStateFlow()

    init { load() }

    fun load() {
        _state.value = DeviceDetailState.Loading
        viewModelScope.launch {
            content.getDevice(deviceId)
                .onSuccess { device ->
                    _state.value = DeviceDetailState.Loaded(device)
                    combine(progress.viewedParts, progress.completedProcedures, progress.quizAttempts, progress.fixedFaults) { v, c, a, f -> ProgressSnapshot(v, c, a, f) }
                        .collect { (v, c, a, f) ->
                            val prefix = "$deviceId:"
                            _state.update {
                                (it as? DeviceDetailState.Loaded)?.copy(
                                    viewedPartIds = v.filter { k -> k.startsWith(prefix) }.map { k -> k.removePrefix(prefix) }.toSet(),
                                    completedProcedureIds = c.filter { k -> k.startsWith(prefix) }.map { k -> k.removePrefix(prefix) }.toSet(),
                                    attempts = a.filter { x -> x.deviceId == deviceId },
                                    fixedFaultIds = f.filter { k -> k.startsWith(prefix) }.map { k -> k.removePrefix(prefix) }.toSet(),
                                ) ?: it
                            }
                        }
                }
                .onFailure { _state.value = DeviceDetailState.Error("Could not load this device.") }
        }
    }
}

@Composable
fun DeviceDetailScreen(
    deviceId: String,
    onBack: () -> Unit,
    onExplore: () -> Unit,
    onOpenPart: (String) -> Unit,
    onOpenProcedure: (String) -> Unit,
    onOpenQuiz: (String) -> Unit,
    onOpenFault: (String) -> Unit,
) {
    val vm = appViewModel(key = "device-$deviceId") { DeviceDetailViewModel(deviceId, it.contentRepository, it.progressRepository) }
    val state by vm.state.collectAsStateWithLifecycle()
    DeviceDetailContent(state, onBack, vm::load, onExplore, onOpenPart, onOpenProcedure, onOpenQuiz, onOpenFault)
}

/** Stateless device detail (hero header, action row, then the lists). */
@Composable
fun DeviceDetailContent(
    state: DeviceDetailState,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onExplore: () -> Unit,
    onOpenPart: (String) -> Unit,
    onOpenProcedure: (String) -> Unit,
    onOpenQuiz: (String) -> Unit,
    onOpenFault: (String) -> Unit,
) {
    Column(Modifier.fillMaxSize().background(EquipXColors.Canvas)) {
        val title = (state as? DeviceDetailState.Loaded)?.device?.name ?: "Equipment"
        EquipXTopBar(title = title, onBack = onBack)
        when (val s = state) {
            DeviceDetailState.Loading -> LoadingState()
            is DeviceDetailState.Error -> ErrorState(s.message, onRetry = onRetry)
            is DeviceDetailState.Loaded -> DeviceContent(s, onExplore, onOpenPart, onOpenProcedure, onOpenQuiz, onOpenFault)
        }
    }
}

private val ProcedureAccent = Accent(Color(0xFFE3F3F0), Color(0xFF0F7B6C))
private val FaultAccent = Accent(Color(0xFFFDEEDD), Color(0xFFD9730D))
private val QuizAccent = Accent(Color(0xFFEEE9F8), Color(0xFF6940A5))

@Composable
private fun DeviceContent(
    s: DeviceDetailState.Loaded,
    onExplore: () -> Unit,
    onOpenPart: (String) -> Unit,
    onOpenProcedure: (String) -> Unit,
    onOpenQuiz: (String) -> Unit,
    onOpenFault: (String) -> Unit,
) {
    val d = s.device
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    // Section item indexes, computed in the same order the items are emitted below (hero + actions = 0, 1).
    var next = 2
    val partsIdx = if (d.parts.isNotEmpty()) next.also { next += 1 + d.parts.size } else -1
    val procIdx = if (d.procedures.isNotEmpty()) next.also { next += 1 + d.procedures.size } else -1
    val faultIdx = if (d.faults.isNotEmpty()) next.also { next += 1 + d.faults.size } else -1
    val quizIdx = if (d.quizzes.isNotEmpty()) next.also { next += 1 + d.quizzes.size } else -1
    fun scrollTo(i: Int) { if (i >= 0) scope.launch { listState.animateScrollToItem(i) } }

    LazyColumn(
        Modifier.fillMaxSize().padding(horizontal = 16.dp),
        state = listState,
        contentPadding = PaddingValues(top = 4.dp, bottom = 24.dp + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()),
    ) {
        item { HeroHeader(s) }
        item {
            ActionRow(
                d,
                onExplore = onExplore,
                onProcedures = { scrollTo(procIdx) },
                onTroubleshoot = { scrollTo(faultIdx) },
                onQuiz = { scrollTo(quizIdx) },
            )
        }
        if (d.parts.isNotEmpty()) {
            item { SectionHeader("Parts", count = d.parts.size) }
            val parts = d.parts.sortedBy { it.number }
            items(parts.size) { i ->
                val p = parts[i]
                Box(Modifier.padding(bottom = 8.dp)) {
                    EquipXCard(Modifier.fillMaxWidth(), onClick = { onOpenPart(p.id) }, elevation = 1.dp) {
                        ListRow(
                            title = p.name.ifBlank { p.id },
                            meta = p.function.ifBlank { null },
                            leading = { NumberBadge(p.number) },
                            modifier = Modifier.padding(horizontal = 12.dp),
                            trailing = { if (p.id in s.viewedPartIds) DoneIcon() },
                        )
                    }
                }
            }
        }
        if (d.procedures.isNotEmpty()) {
            item { SectionHeader("Procedures", count = d.procedures.size) }
            items(d.procedures.size) { i ->
                val p = d.procedures[i]
                Box(Modifier.padding(bottom = 8.dp)) {
                    EquipXCard(Modifier.fillMaxWidth(), onClick = { onOpenProcedure(p.id) }, elevation = 1.dp) {
                        ListRow(
                            title = p.title.ifBlank { p.id },
                            meta = "${p.steps.size} steps",
                            icon = Icons.Outlined.Checklist,
                            accent = ProcedureAccent,
                            modifier = Modifier.padding(horizontal = 12.dp),
                            trailing = { if (p.id in s.completedProcedureIds) DoneIcon() },
                        )
                    }
                }
            }
        }
        if (d.faults.isNotEmpty()) {
            item { SectionHeader("Troubleshoot", count = d.faults.size) }
            items(d.faults.size) { i ->
                val f = d.faults[i]
                Box(Modifier.padding(bottom = 8.dp)) {
                    EquipXCard(Modifier.fillMaxWidth(), onClick = { onOpenFault(f.id) }, elevation = 1.dp) {
                        ListRow(
                            title = f.symptom.ifBlank { f.id },
                            meta = "Inspect in 3D and diagnose",
                            icon = Icons.Outlined.BugReport,
                            accent = FaultAccent,
                            modifier = Modifier.padding(horizontal = 12.dp),
                            trailing = { if (f.id in s.fixedFaultIds) DoneIcon() },
                        )
                    }
                }
            }
        }
        if (d.quizzes.isNotEmpty()) {
            item { SectionHeader("Quizzes", count = d.quizzes.size) }
            items(d.quizzes.size) { i ->
                val q = d.quizzes[i]
                val best = QuizScorer.best(s.attempts.filter { it.quizId == q.id })
                val limit = q.timeLimitSec?.let { " · ${formatDuration(it)} limit" }.orEmpty()
                Box(Modifier.padding(bottom = 8.dp)) {
                    EquipXCard(Modifier.fillMaxWidth(), onClick = { onOpenQuiz(q.id) }, elevation = 1.dp) {
                        ListRow(
                            title = q.title.ifBlank { q.id },
                            meta = "${q.questions.size} questions$limit",
                            icon = Icons.Outlined.Quiz,
                            accent = QuizAccent,
                            modifier = Modifier.padding(horizontal = 12.dp),
                            trailing = { if (best != null) Pill("Best ${best.accuracyPercent}%", background = EquipXColors.SuccessTint, contentColor = EquipXColors.Success) },
                        )
                    }
                }
            }
        }
        if (d.safetyRules.isNotEmpty()) {
            item { SectionHeader("Safety rules", count = d.safetyRules.size) }
            items(d.safetyRules.size) { i ->
                Callout(d.safetyRules[i], modifier = Modifier.padding(bottom = 8.dp))
            }
        }
    }
}

/** Hero: device icon tile, name, category chip, parts-viewed ring and the overview. */
@Composable
private fun HeroHeader(s: DeviceDetailState.Loaded) {
    val d = s.device
    val accent = CategoryAccents.forCategory(d.category)
    val progress = if (d.parts.isEmpty()) 0f else (s.viewedPartIds.count { id -> d.parts.any { it.id == id } }.toFloat() / d.parts.size)
    val shape = RoundedCornerShape(20.dp)
    Column(
        Modifier
            .fillMaxWidth()
            .padding(top = 4.dp)
            .clip(shape)
            .background(Brush.linearGradient(listOf(accent.tint.copy(alpha = 0.4f), Color.White)))
            .padding(18.dp)
            .animateContentSize(),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            DeviceIconTile(d.id, d.category, size = 64.dp)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(d.name, style = MaterialTheme.typography.headlineSmall)
                if (d.category.isNotBlank()) AccentChip(d.category, accent)
            }
            ProgressRing(progress, size = 60.dp, stroke = 6.dp, color = accent.strong, trackColor = Color.White) {
                Text("${(progress * 100).toInt()}%", style = MaterialTheme.typography.labelLarge)
            }
        }
        if (d.overview.isNotBlank()) Text(d.overview, style = MaterialTheme.typography.bodyMedium)
        if (d.parts.isNotEmpty()) {
            Text(
                "${(progress * d.parts.size).toInt().coerceAtMost(d.parts.size)} of ${d.parts.size} parts viewed",
                style = MaterialTheme.typography.labelMedium,
                color = EquipXColors.TextSecondary,
            )
        }
    }
}

@Composable
private fun ActionRow(d: Device, onExplore: () -> Unit, onProcedures: () -> Unit, onTroubleshoot: () -> Unit, onQuiz: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(top = 14.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        ActionTile("Explore 3D", Icons.Outlined.ViewInAr, null, Accent(EquipXColors.Accent, Color.White), true, onExplore, Modifier.weight(1f))
        ActionTile("Procedures", Icons.Outlined.Checklist, d.procedures.size, ProcedureAccent, d.procedures.isNotEmpty(), onProcedures, Modifier.weight(1f))
        ActionTile("Troubleshoot", Icons.Outlined.BugReport, d.faults.size, FaultAccent, d.faults.isNotEmpty(), onTroubleshoot, Modifier.weight(1f))
        ActionTile("Quiz", Icons.Outlined.Quiz, d.quizzes.size, QuizAccent, d.quizzes.isNotEmpty(), onQuiz, Modifier.weight(1f))
    }
}

@Composable
private fun ActionTile(label: String, icon: ImageVector, count: Int?, accent: Accent, enabled: Boolean, onClick: () -> Unit, modifier: Modifier) {
    val primary = accent.tint == EquipXColors.Accent
    EquipXCard(
        modifier,
        onClick = if (enabled) onClick else null,
        containerColor = if (primary) EquipXColors.Accent else Color.White,
        borderColor = if (primary) EquipXColors.Accent else EquipXColors.Border,
    ) {
        Column(
            Modifier.fillMaxWidth().padding(vertical = 12.dp, horizontal = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (primary) Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(32.dp))
            else IconTile(icon, if (enabled) accent else Accent(EquipXColors.Surface, EquipXColors.TextSecondary), size = 32.dp, iconSize = 20.dp)
            Text(
                label,
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
                color = if (primary) Color.White else if (enabled) EquipXColors.Text else EquipXColors.TextSecondary,
                textAlign = TextAlign.Center,
            )
            if (count != null) Text("$count", style = MaterialTheme.typography.labelSmall, color = EquipXColors.TextSecondary)
        }
    }
}

@Composable
private fun DoneIcon() {
    Icon(Icons.Outlined.CheckCircle, contentDescription = "Done", tint = EquipXColors.Success, modifier = Modifier.size(24.dp))
}

/** "5 min", "90 sec", "1 min 30 sec" style label. */
internal fun formatDuration(totalSec: Int): String {
    val m = totalSec / 60
    val s = totalSec % 60
    return when {
        m == 0 -> "$s sec"
        s == 0 -> "$m min"
        else -> "$m min $s sec"
    }
}
