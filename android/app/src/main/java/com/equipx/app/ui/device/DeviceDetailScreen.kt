package com.equipx.app.ui.device

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Checklist
import androidx.compose.material.icons.outlined.Quiz
import androidx.compose.material.icons.outlined.ViewInAr
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
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
import com.equipx.app.ui.components.Callout
import com.equipx.app.ui.components.EquipXTopBar
import com.equipx.app.ui.components.ErrorState
import com.equipx.app.ui.components.HairlineDivider
import com.equipx.app.ui.components.ListRow
import com.equipx.app.ui.components.LoadingState
import com.equipx.app.ui.components.NumberBadge
import com.equipx.app.ui.components.Pill
import com.equipx.app.ui.components.SectionHeader
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
    ) : DeviceDetailState
}

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
                    combine(progress.viewedParts, progress.completedProcedures, progress.quizAttempts) { v, c, a -> Triple(v, c, a) }
                        .collect { (v, c, a) ->
                            val prefix = "$deviceId:"
                            _state.update {
                                (it as? DeviceDetailState.Loaded)?.copy(
                                    viewedPartIds = v.filter { k -> k.startsWith(prefix) }.map { k -> k.removePrefix(prefix) }.toSet(),
                                    completedProcedureIds = c.filter { k -> k.startsWith(prefix) }.map { k -> k.removePrefix(prefix) }.toSet(),
                                    attempts = a.filter { x -> x.deviceId == deviceId },
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
) {
    val vm = appViewModel(key = "device-$deviceId") { DeviceDetailViewModel(deviceId, it.contentRepository, it.progressRepository) }
    val state by vm.state.collectAsStateWithLifecycle()
    Column(Modifier.fillMaxSize().background(Color.White)) {
        val title = (state as? DeviceDetailState.Loaded)?.device?.name ?: "Equipment"
        EquipXTopBar(title = title, onBack = onBack)
        when (val s = state) {
            DeviceDetailState.Loading -> LoadingState()
            is DeviceDetailState.Error -> ErrorState(s.message, onRetry = vm::load)
            is DeviceDetailState.Loaded -> DeviceContent(s, onExplore, onOpenPart, onOpenProcedure, onOpenQuiz)
        }
    }
}

@Composable
private fun DeviceContent(
    s: DeviceDetailState.Loaded,
    onExplore: () -> Unit,
    onOpenPart: (String) -> Unit,
    onOpenProcedure: (String) -> Unit,
    onOpenQuiz: (String) -> Unit,
) {
    val d = s.device
    LazyColumn(
        Modifier.fillMaxSize().padding(horizontal = 16.dp),
        contentPadding = PaddingValues(top = 16.dp, bottom = 24.dp + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()),
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (d.category.isNotBlank()) Pill(d.category)
                if (d.overview.isNotBlank()) Text(d.overview, style = MaterialTheme.typography.bodyLarge)
                ExploreCard(onClick = onExplore)
            }
        }
        if (d.parts.isNotEmpty()) {
            item { SectionHeader("Parts", count = d.parts.size) }
            val parts = d.parts.sortedBy { it.number }
            items(parts.size) { i ->
                val p = parts[i]
                ListRow(
                    title = p.name.ifBlank { p.id },
                    meta = p.function.ifBlank { null },
                    leading = { NumberBadge(p.number) },
                    onClick = { onOpenPart(p.id) },
                    trailing = { if (p.id in s.viewedPartIds) DoneIcon() },
                )
                HairlineDivider()
            }
        }
        if (d.procedures.isNotEmpty()) {
            item { SectionHeader("Procedures", count = d.procedures.size) }
            items(d.procedures.size) { i ->
                val p = d.procedures[i]
                ListRow(
                    title = p.title.ifBlank { p.id },
                    meta = "${p.steps.size} steps",
                    icon = Icons.Outlined.Checklist,
                    onClick = { onOpenProcedure(p.id) },
                    trailing = { if (p.id in s.completedProcedureIds) DoneIcon() },
                )
                HairlineDivider()
            }
        }
        if (d.quizzes.isNotEmpty()) {
            item { SectionHeader("Quizzes", count = d.quizzes.size) }
            items(d.quizzes.size) { i ->
                val q = d.quizzes[i]
                val best = QuizScorer.best(s.attempts.filter { it.quizId == q.id })
                val limit = q.timeLimitSec?.let { " · ${formatDuration(it)} limit" }.orEmpty()
                ListRow(
                    title = q.title.ifBlank { q.id },
                    meta = "${q.questions.size} questions$limit",
                    icon = Icons.Outlined.Quiz,
                    onClick = { onOpenQuiz(q.id) },
                    trailing = { if (best != null) Pill("Best ${best.accuracyPercent}%") },
                )
                HairlineDivider()
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

@Composable
private fun DoneIcon() {
    Icon(Icons.Outlined.CheckCircle, contentDescription = "Done", tint = EquipXColors.Success, modifier = Modifier.size(20.dp))
}

@Composable
private fun ExploreCard(onClick: () -> Unit) {
    val shape = RoundedCornerShape(6.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(EquipXColors.Accent)
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(Icons.Outlined.ViewInAr, contentDescription = null, tint = Color.White, modifier = Modifier.size(28.dp))
        Column(Modifier.weight(1f)) {
            Text("Explore in 3D", style = MaterialTheme.typography.titleMedium, color = Color.White)
            Text("Rotate, zoom and tap numbered parts", style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.85f))
        }
    }
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
