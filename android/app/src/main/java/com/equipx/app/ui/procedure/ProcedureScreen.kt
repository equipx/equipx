package com.equipx.app.ui.procedure

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.equipx.app.data.ContentRepository
import com.equipx.app.data.ActivityTracker
import com.equipx.app.domain.ActivityEvent
import com.equipx.app.domain.Device
import com.equipx.app.domain.Procedure
import com.equipx.app.explorer.rememberWebViewExplorerController
import com.equipx.app.ui.appViewModel
import com.equipx.app.ui.components.EmptyState
import com.equipx.app.ui.components.EquipXTopBar
import com.equipx.app.ui.components.ErrorState
import com.equipx.app.ui.components.HairlineDivider
import com.equipx.app.ui.components.LoadingState
import com.equipx.app.ui.components.Pill
import com.equipx.app.ui.components.PrimaryButton
import com.equipx.app.ui.components.SecondaryButton
import com.equipx.app.ui.explorer.ExplorerPane
import com.equipx.app.ui.theme.EquipXColors
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface ProcedureState {
    data object Loading : ProcedureState
    data class Error(val message: String) : ProcedureState
    data class Running(
        val device: Device,
        val procedure: Procedure,
        val currentStep: Int = 0,
        val checked: Set<Int> = emptySet(),
        val completed: Boolean = false,
    ) : ProcedureState {
        val lastStep: Int get() = procedure.steps.lastIndex
        val isLast: Boolean get() = currentStep >= lastStep
    }
}

class ProcedureViewModel(
    private val deviceId: String,
    private val procedureId: String,
    private val content: ContentRepository,
    private val tracker: ActivityTracker,
) : ViewModel() {
    private val _state = MutableStateFlow<ProcedureState>(ProcedureState.Loading)
    val state: StateFlow<ProcedureState> = _state.asStateFlow()

    init { load() }

    fun load() {
        _state.value = ProcedureState.Loading
        viewModelScope.launch {
            content.getDevice(deviceId)
                .onSuccess { d ->
                    val p = d.procedures.firstOrNull { it.id == procedureId }
                    _state.value = if (p == null) ProcedureState.Error("This procedure could not be found.")
                    else ProcedureState.Running(d, p)
                }
                .onFailure { _state.value = ProcedureState.Error("Could not load this procedure.") }
        }
    }

    private fun running(f: (ProcedureState.Running) -> ProcedureState.Running) =
        _state.update { (it as? ProcedureState.Running)?.let(f) ?: it }

    fun toggle(step: Int) = running { s ->
        s.copy(checked = if (step in s.checked) s.checked - step else s.checked + step)
    }

    fun goTo(step: Int) = running { s -> s.copy(currentStep = step.coerceIn(0, s.lastStep.coerceAtLeast(0))) }

    fun back() = running { s -> s.copy(currentStep = (s.currentStep - 1).coerceAtLeast(0)) }

    /** Checks off the current step and moves on; on the last step completes the procedure and saves it. */
    fun next() {
        val s = _state.value as? ProcedureState.Running ?: return
        val checked = s.checked + s.currentStep
        if (s.isLast) {
            // Finishing confirms every step.
            _state.value = s.copy(checked = s.procedure.steps.indices.toSet(), completed = true)
            tracker.post(ActivityEvent.ProcedureCompleted(deviceId, procedureId))
        } else {
            _state.value = s.copy(checked = checked, currentStep = s.currentStep + 1)
        }
    }

    fun restart() = running { it.copy(currentStep = 0, checked = emptySet(), completed = false) }
}

@Composable
fun ProcedureScreen(
    deviceId: String,
    procedureId: String,
    onBack: () -> Unit,
    onOpenPart: (String) -> Unit,
) {
    val vm = appViewModel(key = "procedure-$deviceId-$procedureId") {
        ProcedureViewModel(deviceId, procedureId, it.contentRepository, it.activityTracker)
    }
    val state by vm.state.collectAsStateWithLifecycle()
    Column(Modifier.fillMaxSize().background(Color.White)) {
        EquipXTopBar(title = (state as? ProcedureState.Running)?.procedure?.title ?: "Procedure", onBack = onBack)
        when (val s = state) {
            ProcedureState.Loading -> LoadingState()
            is ProcedureState.Error -> ErrorState(s.message, onRetry = vm::load)
            is ProcedureState.Running -> when {
                s.procedure.steps.isEmpty() -> EmptyState("This procedure has no steps yet")
                s.completed -> CompletionContent(s, onRestart = vm::restart, onDone = onBack)
                else -> RunnerContent(s, vm, onOpenPart)
            }
        }
    }
}

@Composable
private fun RunnerContent(s: ProcedureState.Running, vm: ProcedureViewModel, onOpenPart: (String) -> Unit) {
    val controller = rememberWebViewExplorerController()
    val steps = s.procedure.steps
    val listState = rememberLazyListState()
    LaunchedEffect(s.currentStep) { listState.animateScrollToItem(s.currentStep) }

    Column(Modifier.fillMaxSize()) {
        // Bounded height for the WebView: it takes a share of the column, never wrap-content.
        ExplorerPane(
            device = s.device,
            controller = controller,
            selectedPartId = steps[s.currentStep].partId,
            selectionToken = s.currentStep,
            onHotspotTap = {},
            modifier = Modifier.fillMaxWidth().weight(1f),
        )
        HairlineDivider()
        Column(Modifier.fillMaxWidth().weight(1.15f)) {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    "Step ${s.currentStep + 1} of ${steps.size}",
                    style = MaterialTheme.typography.labelMedium,
                    color = EquipXColors.TextSecondary,
                )
                LinearProgressIndicator(
                    progress = { s.checked.size.toFloat() / steps.size },
                    modifier = Modifier.fillMaxWidth(),
                    color = EquipXColors.Accent,
                    trackColor = EquipXColors.Border,
                )
            }
            LazyColumn(Modifier.weight(1f).padding(horizontal = 16.dp), state = listState) {
                itemsIndexed(steps) { i, step ->
                    val partName = s.device.parts.firstOrNull { it.id == step.partId }?.name
                    StepRow(
                        index = i,
                        text = step.text,
                        checked = i in s.checked,
                        current = i == s.currentStep,
                        partName = partName ?: step.partId.replace('_', ' '),
                        onToggle = { vm.toggle(i) },
                        onSelect = { vm.goTo(i) },
                        onOpenPart = { onOpenPart(step.partId) },
                    )
                }
            }
            HairlineDivider()
            Row(
                Modifier.fillMaxWidth().navigationBarsPadding().padding(12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                SecondaryButton("Back", onClick = vm::back, enabled = s.currentStep > 0, modifier = Modifier.weight(1f))
                PrimaryButton(if (s.isLast) "Finish" else "Next", onClick = vm::next, modifier = Modifier.weight(2f))
            }
        }
    }
}

@Composable
private fun StepRow(
    index: Int,
    text: String,
    checked: Boolean,
    current: Boolean,
    partName: String,
    onToggle: () -> Unit,
    onSelect: () -> Unit,
    onOpenPart: () -> Unit,
) {
    val shape = RoundedCornerShape(6.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
            .clip(shape)
            .background(if (current) EquipXColors.Surface else Color.White)
            .border(1.dp, if (current) EquipXColors.Accent else Color.Transparent, shape)
            .clickable(onClick = onSelect)
            .padding(end = 8.dp, top = 2.dp, bottom = 2.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Checkbox(
            checked = checked,
            onCheckedChange = { onToggle() },
            colors = CheckboxDefaults.colors(checkedColor = EquipXColors.Success, uncheckedColor = EquipXColors.TextSecondary),
        )
        Column(Modifier.weight(1f).padding(vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                "${index + 1}. $text",
                style = if (current) MaterialTheme.typography.titleSmall else MaterialTheme.typography.bodyMedium,
                color = if (checked && !current) EquipXColors.TextSecondary else EquipXColors.Text,
            )
            if (current) {
                Box(Modifier.clickable(onClick = onOpenPart)) { Pill("Part: $partName", contentColor = EquipXColors.Accent) }
            }
        }
    }
}

@Composable
private fun CompletionContent(s: ProcedureState.Running, onRestart: () -> Unit, onDone: () -> Unit) {
    Column(
        Modifier.fillMaxSize().navigationBarsPadding().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(Icons.Outlined.CheckCircle, contentDescription = null, tint = EquipXColors.Success, modifier = Modifier.size(56.dp))
        Text("Procedure complete", style = MaterialTheme.typography.headlineSmall)
        Text(
            "You completed all ${s.procedure.steps.size} steps of \"${s.procedure.title}\".",
            style = MaterialTheme.typography.bodyMedium,
            color = EquipXColors.TextSecondary,
            textAlign = TextAlign.Center,
        )
        Column(Modifier.fillMaxWidth().padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            PrimaryButton("Done", onClick = onDone, modifier = Modifier.fillMaxWidth())
            SecondaryButton("Do it again", onClick = onRestart, modifier = Modifier.fillMaxWidth())
        }
    }
}
