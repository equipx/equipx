package com.equipx.app.ui.missions

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Checklist
import androidx.compose.material.icons.outlined.EmojiEvents
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Quiz
import androidx.compose.material.icons.outlined.TouchApp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.equipx.app.data.ActivityTracker
import com.equipx.app.data.ContentRepository
import com.equipx.app.data.ProgressRepository
import com.equipx.app.domain.Mission
import com.equipx.app.domain.MissionRules
import com.equipx.app.domain.MissionState
import com.equipx.app.domain.MissionTask
import com.equipx.app.domain.TaskState
import com.equipx.app.ui.appViewModel
import com.equipx.app.ui.components.AccentChip
import com.equipx.app.ui.components.Callout
import com.equipx.app.ui.components.EmptyState
import com.equipx.app.ui.components.EquipXCard
import com.equipx.app.ui.components.EquipXProgressBar
import com.equipx.app.ui.components.EquipXTopBar
import com.equipx.app.ui.components.ErrorState
import com.equipx.app.ui.components.IconTile
import com.equipx.app.ui.components.LoadingState
import com.equipx.app.ui.components.Pill
import com.equipx.app.ui.components.PrimaryButton
import com.equipx.app.ui.components.TabTitle
import com.equipx.app.ui.theme.Accent
import com.equipx.app.ui.theme.EquipXColors
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

sealed interface MissionsUiState {
    data object Loading : MissionsUiState
    data class Error(val message: String) : MissionsUiState
    data class Loaded(
        val missions: List<Mission>,
        val missionState: MissionState,
        val completed: Set<String>,
    ) : MissionsUiState {
        fun done(m: Mission): List<String> =
            if (m.id in completed) m.tasks.map { it.id } else missionState.doneTasks[m.id].orEmpty()
    }
}

class MissionsViewModel(
    private val content: ContentRepository,
    private val progress: ProgressRepository,
    private val tracker: ActivityTracker,
) : ViewModel() {
    private val _state = MutableStateFlow<MissionsUiState>(MissionsUiState.Loading)
    val state: StateFlow<MissionsUiState> = _state.asStateFlow()

    /** Open mission, hoisted here so switching bottom tabs (which leaves the composition) keeps it. */
    private val _selectedId = MutableStateFlow<String?>(null)
    val selectedId: StateFlow<String?> = _selectedId.asStateFlow()
    fun select(id: String?) { _selectedId.value = id }

    init { load() }

    fun load() {
        _state.value = MissionsUiState.Loading
        viewModelScope.launch {
            content.getMissions()
                .onSuccess { missions ->
                    combine(progress.missionState, progress.progress) { ms, p -> ms to p.completedMissions }
                        .collect { (ms, done) -> _state.value = MissionsUiState.Loaded(missions, ms, done) }
                }
                .onFailure { _state.value = MissionsUiState.Error("Could not load missions.") }
        }
    }

    fun activate(missionId: String) {
        viewModelScope.launch { tracker.activateMission(missionId) }
    }
}

@Composable
fun MissionsScreen(
    onGoToScan: () -> Unit,
    onOpenExplorer: (deviceId: String) -> Unit,
    onOpenProcedure: (deviceId: String, procedureId: String) -> Unit,
    onOpenQuiz: (deviceId: String, quizId: String) -> Unit,
) {
    val vm = appViewModel { MissionsViewModel(it.contentRepository, it.progressRepository, it.activityTracker) }
    val state by vm.state.collectAsStateWithLifecycle()
    val selectedId by vm.selectedId.collectAsStateWithLifecycle()
    val selected = (state as? MissionsUiState.Loaded)?.missions?.firstOrNull { it.id == selectedId }
    BackHandler(enabled = selected != null) { vm.select(null) }

    MissionsContent(
        state = state,
        selected = selected,
        onRetry = vm::load,
        onSelect = vm::select,
        onStart = { selected?.let { vm.activate(it.id) } },
        onAction = { task ->
            selected?.let { vm.activate(it.id) }
            when (task.type) {
                MissionRules.TYPE_SCAN -> onGoToScan()
                MissionRules.TYPE_IDENTIFY -> onOpenExplorer(task.deviceId)
                MissionRules.TYPE_PROCEDURE -> task.procedureId?.let { onOpenProcedure(task.deviceId, it) }
                MissionRules.TYPE_QUIZ -> task.quizId?.let { onOpenQuiz(task.deviceId, it) }
            }
        },
    )
}

/** Stateless missions tab (list, or the detail of [selected]). */
@Composable
fun MissionsContent(
    state: MissionsUiState,
    selected: Mission?,
    onRetry: () -> Unit,
    onSelect: (String?) -> Unit,
    onStart: () -> Unit,
    onAction: (MissionTask) -> Unit,
) {
    Column(Modifier.fillMaxSize().background(EquipXColors.Canvas)) {
        if (selected != null) EquipXTopBar(title = selected.title, onBack = { onSelect(null) })
        else TabTitle("Missions", subtitle = "Guided tasks that earn points and badges")
        when (val s = state) {
            MissionsUiState.Loading -> LoadingState()
            is MissionsUiState.Error -> ErrorState(s.message, onRetry = onRetry)
            is MissionsUiState.Loaded -> when {
                s.missions.isEmpty() -> EmptyState(
                    "No missions yet",
                    message = "Guided missions will appear here when they are added to the app content.",
                    icon = Icons.Outlined.EmojiEvents,
                )
                selected != null -> MissionDetail(selected, s, onAction = onAction, onStart = onStart)
                else -> MissionList(s, onOpen = { onSelect(it) })
            }
        }
    }
}

private val MissionAccent = Accent(EquipXColors.GoldTint, Color(0xFFB88700))

@Composable
private fun MissionList(s: MissionsUiState.Loaded, onOpen: (String) -> Unit) {
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(s.missions, key = { it.id }) { m ->
            val done = s.done(m)
            val complete = MissionRules.isComplete(m, done)
            val active = s.missionState.activeMissionId == m.id
            EquipXCard(Modifier.fillMaxWidth(), onClick = { onOpen(m.id) }) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        IconTile(if (complete) Icons.Outlined.CheckCircle else Icons.Outlined.EmojiEvents, if (complete) Accent(EquipXColors.SuccessTint, EquipXColors.Success) else MissionAccent, size = 48.dp)
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(m.title, style = MaterialTheme.typography.titleMedium)
                            when {
                                complete -> Pill("Complete", background = EquipXColors.SuccessTint, contentColor = EquipXColors.Success)
                                active -> Pill("In progress", background = EquipXColors.AccentTint, contentColor = EquipXColors.Accent)
                            }
                        }
                    }
                    if (m.description.isNotBlank()) Text(m.description, style = MaterialTheme.typography.bodyMedium, color = EquipXColors.TextSecondary)
                    EquipXProgressBar(MissionRules.progressFraction(m, done), color = if (complete) EquipXColors.Success else EquipXColors.Accent)
                    Text("${m.tasks.count { it.id in done }} of ${m.tasks.size} tasks", style = MaterialTheme.typography.labelMedium, color = EquipXColors.TextSecondary)
                }
            }
        }
    }
}

@Composable
private fun MissionDetail(
    mission: Mission,
    state: MissionsUiState.Loaded,
    onAction: (MissionTask) -> Unit,
    onStart: () -> Unit,
) {
    val done = state.done(mission)
    val states = MissionRules.taskStates(mission, done)
    val complete = MissionRules.isComplete(mission, done)
    val isActive = state.missionState.activeMissionId == mission.id
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            EquipXCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp).animateContentSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (mission.description.isNotBlank()) Text(mission.description, style = MaterialTheme.typography.bodyLarge)
                    EquipXProgressBar(MissionRules.progressFraction(mission, done), color = if (complete) EquipXColors.Success else EquipXColors.Accent, height = 10.dp)
                    Text("${mission.tasks.count { it.id in done }} of ${mission.tasks.size} tasks done", style = MaterialTheme.typography.labelMedium, color = EquipXColors.TextSecondary)
                    when {
                        complete -> Callout(title = "Mission complete", text = "+50 points and the Mission Complete badge.")
                        isActive -> AccentChip("Active mission", Accent(EquipXColors.AccentTint, EquipXColors.Accent))
                        else -> PrimaryButton("Start mission", onClick = onStart, modifier = Modifier.fillMaxWidth())
                    }
                }
            }
        }
        items(mission.tasks.size) { i ->
            val task = mission.tasks[i]
            TaskRow(i + 1, task, states[i], isActive = isActive, onAction = { onAction(task) })
        }
    }
}

private fun taskIcon(type: String): ImageVector = when (type) {
    MissionRules.TYPE_SCAN -> Icons.Outlined.CameraAlt
    MissionRules.TYPE_IDENTIFY -> Icons.Outlined.TouchApp
    MissionRules.TYPE_PROCEDURE -> Icons.Outlined.Checklist
    MissionRules.TYPE_QUIZ -> Icons.Outlined.Quiz
    else -> Icons.Outlined.EmojiEvents
}

@Composable
private fun TaskRow(number: Int, task: MissionTask, state: TaskState, isActive: Boolean, onAction: () -> Unit) {
    val active = state == TaskState.ACTIVE
    EquipXCard(
        Modifier.fillMaxWidth().animateContentSize(),
        containerColor = if (active) EquipXColors.AccentTint else if (state == TaskState.LOCKED) EquipXColors.Surface else Color.White,
        borderColor = if (active) EquipXColors.Accent else EquipXColors.Border,
        elevation = if (active) 4.dp else if (state == TaskState.LOCKED) 0.dp else 1.dp,
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                when (state) {
                    TaskState.DONE -> IconTile(Icons.Outlined.CheckCircle, Accent(EquipXColors.SuccessTint, EquipXColors.Success), size = 40.dp)
                    TaskState.ACTIVE -> IconTile(taskIcon(task.type), Accent(Color.White, EquipXColors.Accent), size = 40.dp)
                    TaskState.LOCKED -> IconTile(Icons.Outlined.Lock, Accent(EquipXColors.Border, EquipXColors.TextSecondary), size = 40.dp)
                }
                Text(
                    "$number. ${task.title}",
                    style = MaterialTheme.typography.titleSmall,
                    color = if (state == TaskState.LOCKED) EquipXColors.TextSecondary else EquipXColors.Text,
                    modifier = Modifier.weight(1f),
                )
            }
            AnimatedVisibility(active) {
                PrimaryButton(actionLabel(task, isActive), onClick = onAction, modifier = Modifier.fillMaxWidth())
            }
        }
    }
}

private fun actionLabel(task: MissionTask, isActive: Boolean): String = when (task.type) {
    MissionRules.TYPE_SCAN -> "Open scanner"
    MissionRules.TYPE_IDENTIFY -> "Open 3D explorer"
    MissionRules.TYPE_PROCEDURE -> "Start procedure"
    MissionRules.TYPE_QUIZ -> "Start quiz (pass at ${task.minScorePct}%)"
    else -> if (isActive) "Continue" else "Start"
}
