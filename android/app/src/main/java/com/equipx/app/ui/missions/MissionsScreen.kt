package com.equipx.app.ui.missions

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
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
import com.equipx.app.ui.components.Callout
import com.equipx.app.ui.components.EmptyState
import com.equipx.app.ui.components.EquipXTopBar
import com.equipx.app.ui.components.ErrorState
import com.equipx.app.ui.components.HairlineDivider
import com.equipx.app.ui.components.LoadingState
import com.equipx.app.ui.components.Pill
import com.equipx.app.ui.components.PrimaryButton
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
    val loaded = state as? MissionsUiState.Loaded
    val selected = loaded?.missions?.firstOrNull { it.id == selectedId }
    BackHandler(enabled = selected != null) { vm.select(null) }

    Column(Modifier.fillMaxSize().background(Color.White)) {
        EquipXTopBar(title = selected?.title ?: "Missions", onBack = if (selected != null) ({ vm.select(null) }) else null)
        when (val s = state) {
            MissionsUiState.Loading -> LoadingState()
            is MissionsUiState.Error -> ErrorState(s.message, onRetry = vm::load)
            is MissionsUiState.Loaded -> when {
                s.missions.isEmpty() -> EmptyState("No missions yet", message = "Guided missions will appear here when they are added to the app content.")
                selected != null -> MissionDetail(
                    mission = selected,
                    state = s,
                    onAction = { task ->
                        vm.activate(selected.id)
                        when (task.type) {
                            MissionRules.TYPE_SCAN -> onGoToScan()
                            MissionRules.TYPE_IDENTIFY -> onOpenExplorer(task.deviceId)
                            MissionRules.TYPE_PROCEDURE -> task.procedureId?.let { onOpenProcedure(task.deviceId, it) }
                            MissionRules.TYPE_QUIZ -> task.quizId?.let { onOpenQuiz(task.deviceId, it) }
                        }
                    },
                    onStart = { vm.activate(selected.id) },
                )
                else -> MissionList(s, onOpen = { vm.select(it) })
            }
        }
    }
}

@Composable
private fun MissionList(s: MissionsUiState.Loaded, onOpen: (String) -> Unit) {
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        items(s.missions, key = { it.id }) { m ->
            val done = s.done(m)
            val complete = MissionRules.isComplete(m, done)
            val shape = RoundedCornerShape(6.dp)
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(shape)
                    .border(1.dp, EquipXColors.Border, shape)
                    .clickableRow { onOpen(m.id) }
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(m.title, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                    when {
                        complete -> Pill("Complete", contentColor = EquipXColors.Success)
                        s.missionState.activeMissionId == m.id -> Pill("In progress", contentColor = EquipXColors.Accent)
                    }
                }
                if (m.description.isNotBlank()) Text(m.description, style = MaterialTheme.typography.bodySmall, color = EquipXColors.TextSecondary)
                LinearProgressIndicator(
                    progress = { MissionRules.progressFraction(m, done) },
                    modifier = Modifier.fillMaxWidth(),
                    color = if (complete) EquipXColors.Success else EquipXColors.Accent,
                    trackColor = EquipXColors.Border,
                )
                Text("${m.tasks.count { it.id in done }} of ${m.tasks.size} tasks", style = MaterialTheme.typography.labelMedium, color = EquipXColors.TextSecondary)
            }
        }
    }
}

private fun Modifier.clickableRow(onClick: () -> Unit): Modifier = this.then(Modifier.clickable(onClick = onClick))

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
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (mission.description.isNotBlank()) Text(mission.description, style = MaterialTheme.typography.bodyLarge)
                LinearProgressIndicator(
                    progress = { MissionRules.progressFraction(mission, done) },
                    modifier = Modifier.fillMaxWidth(),
                    color = if (complete) EquipXColors.Success else EquipXColors.Accent,
                    trackColor = EquipXColors.Border,
                )
                when {
                    complete -> Callout(title = "Mission complete", text = "+50 points and the Mission Complete badge.")
                    isActive -> Pill("Active mission", contentColor = EquipXColors.Accent)
                    else -> PrimaryButton("Start mission", onClick = onStart, modifier = Modifier.fillMaxWidth())
                }
            }
        }
        items(mission.tasks.size) { i ->
            val task = mission.tasks[i]
            TaskRow(i + 1, task, states[i], isActive = isActive, onAction = { onAction(task) })
        }
    }
}

@Composable
private fun TaskRow(number: Int, task: MissionTask, state: TaskState, isActive: Boolean, onAction: () -> Unit) {
    val shape = RoundedCornerShape(6.dp)
    Column(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(if (state == TaskState.ACTIVE) EquipXColors.Surface else Color.White)
            .border(1.dp, if (state == TaskState.ACTIVE) EquipXColors.Accent else EquipXColors.Border, shape)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            when (state) {
                TaskState.DONE -> Icon(Icons.Outlined.CheckCircle, contentDescription = "Done", tint = EquipXColors.Success, modifier = Modifier.size(22.dp))
                TaskState.ACTIVE -> Icon(Icons.Outlined.RadioButtonUnchecked, contentDescription = "Active", tint = EquipXColors.Accent, modifier = Modifier.size(22.dp))
                TaskState.LOCKED -> Icon(Icons.Outlined.Lock, contentDescription = "Locked", tint = EquipXColors.TextSecondary, modifier = Modifier.size(22.dp))
            }
            Text(
                "$number. ${task.title}",
                style = MaterialTheme.typography.bodyMedium,
                color = if (state == TaskState.LOCKED) EquipXColors.TextSecondary else EquipXColors.Text,
                modifier = Modifier.weight(1f),
            )
        }
        if (state == TaskState.ACTIVE) {
            PrimaryButton(actionLabel(task, isActive), onClick = onAction, modifier = Modifier.fillMaxWidth())
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
