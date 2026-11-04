package com.equipx.app.ui.troubleshoot

import androidx.compose.foundation.background
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import com.equipx.app.ui.components.DeviceIconTile
import com.equipx.app.ui.components.EquipXCard
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
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
import com.equipx.app.data.ActivityTracker
import com.equipx.app.data.ContentRepository
import com.equipx.app.data.ProgressRepository
import com.equipx.app.domain.ActivityEvent
import com.equipx.app.domain.Device
import com.equipx.app.domain.Fault
import com.equipx.app.domain.FaultAttempt
import com.equipx.app.domain.Part
import com.equipx.app.domain.PointReason
import com.equipx.app.explorer.rememberWebViewExplorerController
import com.equipx.app.ui.appViewModel
import com.equipx.app.ui.components.Callout
import com.equipx.app.ui.components.EmptyState
import com.equipx.app.ui.components.EquipXTopBar
import com.equipx.app.ui.components.ErrorState
import com.equipx.app.ui.components.HairlineDivider
import com.equipx.app.ui.components.LoadingState
import com.equipx.app.ui.components.Pill
import com.equipx.app.ui.components.PrimaryButton
import com.equipx.app.ui.components.SecondaryButton
import com.equipx.app.ui.explorer.ExplorerPane
import com.equipx.app.ui.explorer.PartSheetContent
import com.equipx.app.ui.theme.EquipXColors
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class FaultStage { Symptom, Inspect, Choose, Feedback }

sealed interface FaultUiState {
    data object Loading : FaultUiState
    data class Error(val message: String) : FaultUiState
    data class Active(
        val device: Device,
        val fault: Fault,
        val stage: FaultStage = FaultStage.Symptom,
        /** Distinct inspected part ids in tap order. */
        val inspected: List<String> = emptyList(),
        val tapCount: Int = 0,
        val selectedPartId: String? = null,
        val selectionToken: Int = 0,
        val sheetOpen: Boolean = false,
        val chosenOptionId: String? = null,
        val correct: Boolean = false,
        val pointsAwarded: Int = 0,
    ) : FaultUiState {
        /** After a few taps, point at a related part that has not been inspected yet. */
        val hintPart: Part?
            get() {
                if (tapCount < HINT_AFTER_TAPS) return null
                val id = fault.relatedPartIds.firstOrNull { it !in inspected } ?: return null
                return device.parts.firstOrNull { it.id == id }
            }
        val hintsUnlocked: Boolean get() = tapCount >= HINT_AFTER_TAPS
    }

    companion object { const val HINT_AFTER_TAPS = 3 }
}

class TroubleshootViewModel(
    private val deviceId: String,
    private val faultId: String,
    private val content: ContentRepository,
    private val progress: ProgressRepository,
    private val tracker: ActivityTracker,
) : ViewModel() {
    private val _state = MutableStateFlow<FaultUiState>(FaultUiState.Loading)
    val state: StateFlow<FaultUiState> = _state.asStateFlow()

    init { load() }

    fun load() {
        _state.value = FaultUiState.Loading
        viewModelScope.launch {
            content.getDevice(deviceId)
                .onSuccess { d ->
                    val f = d.faults.firstOrNull { it.id == faultId }
                    _state.value = if (f == null) FaultUiState.Error("This scenario could not be found.") else FaultUiState.Active(d, f)
                }
                .onFailure { _state.value = FaultUiState.Error("Could not load this scenario.") }
        }
    }

    private fun active(f: (FaultUiState.Active) -> FaultUiState.Active) =
        _state.update { (it as? FaultUiState.Active)?.let(f) ?: it }

    fun startInspecting() = active { it.copy(stage = FaultStage.Inspect) }
    fun backToInspect() = active { it.copy(stage = FaultStage.Inspect, chosenOptionId = null) }
    fun goChoose() = active { it.copy(stage = FaultStage.Choose, sheetOpen = false) }
    fun chooseOption(id: String) = active { it.copy(chosenOptionId = id) }
    fun dismissSheet() = active { it.copy(sheetOpen = false) }

    /** A part was tapped in 3D: record it as inspected, show its sheet. */
    fun inspect(partId: String) {
        active {
            it.copy(
                inspected = if (partId in it.inspected) it.inspected else it.inspected + partId,
                tapCount = it.tapCount + 1,
                selectedPartId = partId,
                selectionToken = it.selectionToken + 1,
                sheetOpen = true,
            )
        }
        tracker.post(ActivityEvent.PartViewed(deviceId, partId, fromExplorer = false))
    }

    fun submit() {
        val s = _state.value as? FaultUiState.Active ?: return
        val chosen = s.chosenOptionId ?: return
        if (s.stage != FaultStage.Choose) return
        val correct = chosen == s.fault.correctOptionId
        // Show feedback immediately; points are filled in once the engine has answered.
        _state.value = s.copy(stage = FaultStage.Feedback, correct = correct, pointsAwarded = 0)
        // Queue the award first, synchronously, in the app-lifetime tracker: leaving the screen cannot cancel it.
        val award = tracker.postForResult(ActivityEvent.FaultDiagnosed("$deviceId:$faultId", correct))
        viewModelScope.launch {
            runCatching {
                progress.saveFaultAttempt(
                    FaultAttempt(deviceId, faultId, chosen, correct, s.inspected, System.currentTimeMillis()),
                )
            }
        }
        viewModelScope.launch {
            val result = award.await()
            val pts = result.newEntries.filter { it.reason == PointReason.FAULT_FIXED }.sumOf { it.amount }
            active { it.copy(pointsAwarded = pts) }
        }
    }

    /** Back to the symptom for another go. */
    fun retry() = active { it.copy(stage = FaultStage.Symptom, inspected = emptyList(), tapCount = 0, selectedPartId = null, chosenOptionId = null, sheetOpen = false, correct = false, pointsAwarded = 0) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TroubleshootScreen(
    deviceId: String,
    faultId: String,
    onBack: () -> Unit,
    onOpenPart: (String) -> Unit,
) {
    val vm = appViewModel(key = "fault-$deviceId-$faultId") {
        TroubleshootViewModel(deviceId, faultId, it.contentRepository, it.progressRepository, it.activityTracker)
    }
    val state by vm.state.collectAsStateWithLifecycle()
    Column(Modifier.fillMaxSize().background(EquipXColors.Canvas)) {
        EquipXTopBar(title = "Troubleshoot", onBack = onBack)
        when (val s = state) {
            FaultUiState.Loading -> LoadingState()
            is FaultUiState.Error -> ErrorState(s.message, onRetry = vm::load)
            is FaultUiState.Active -> when (s.stage) {
                FaultStage.Symptom -> SymptomContent(s, onStart = vm::startInspecting)
                FaultStage.Inspect -> InspectContent(s, vm)
                FaultStage.Choose -> ChooseContent(s, onPick = vm::chooseOption, onBack = vm::backToInspect, onSubmit = vm::submit)
                FaultStage.Feedback -> FeedbackContent(s, onRetry = vm::retry, onDone = onBack)
            }
        }
    }

    val s = state as? FaultUiState.Active
    val partId = s?.selectedPartId
    if (s != null && s.stage == FaultStage.Inspect && s.sheetOpen && partId != null) {
        ModalBottomSheet(
            onDismissRequest = vm::dismissSheet,
            sheetState = rememberModalBottomSheetState(),
            containerColor = Color.White,
            dragHandle = null,
        ) {
            PartSheetContent(
                partId = partId,
                part = s.device.parts.firstOrNull { it.id == partId },
                onFullDetails = { vm.dismissSheet(); onOpenPart(partId) },
            )
        }
    }
}

@Composable
private fun SymptomContent(s: FaultUiState.Active, onStart: () -> Unit) {
    Column(
        Modifier.fillMaxSize().navigationBarsPadding().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            DeviceIconTile(s.device.id, s.device.category, size = 40.dp)
            Text(s.device.name, style = MaterialTheme.typography.titleSmall, color = EquipXColors.TextSecondary)
        }
        Text("SYMPTOM", style = MaterialTheme.typography.labelSmall, color = EquipXColors.Danger)
        Text(s.fault.symptom.ifBlank { s.fault.id.replace('_', ' ') }, style = MaterialTheme.typography.headlineSmall)
        Callout(
            title = "Your task",
            text = "Inspect the device in 3D by tapping parts, work out what is wrong, then choose the most likely cause.",
        )
        PrimaryButton("Start inspecting", onClick = onStart, modifier = Modifier.fillMaxWidth())
    }
}

@Composable
private fun InspectContent(s: FaultUiState.Active, vm: TroubleshootViewModel) {
    val controller = rememberWebViewExplorerController()
    Column(Modifier.fillMaxSize()) {
        // Bounded height for the WebView: a share of the column, never wrap-content.
        ExplorerPane(
            device = s.device,
            controller = controller,
            selectedPartId = s.selectedPartId,
            selectionToken = s.selectionToken,
            onHotspotTap = vm::inspect,
            modifier = Modifier.fillMaxWidth().weight(1f),
        )
        HairlineDivider()
        Column(
            Modifier.fillMaxWidth().weight(0.8f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(s.fault.symptom, style = MaterialTheme.typography.titleSmall)
            Text(
                if (s.inspected.isEmpty()) "Tap numbered parts in 3D to inspect them."
                else "Inspected ${s.inspected.size}: " + s.inspected.joinToString(", ") { id -> s.device.parts.firstOrNull { it.id == id }?.name?.ifBlank { null } ?: id },
                style = MaterialTheme.typography.bodySmall,
                color = EquipXColors.TextSecondary,
            )
            val hint = s.hintPart
            if (hint != null) Callout(title = "Hint", text = "Take a closer look at the ${hint.name.ifBlank { hint.id }}.")
            else if (s.hintsUnlocked && s.fault.relatedPartIds.isNotEmpty()) Callout(title = "Hint", text = "You have checked the key parts. Ready to diagnose?")
        }
        HairlineDivider()
        Row(Modifier.fillMaxWidth().navigationBarsPadding().padding(12.dp)) {
            PrimaryButton("Diagnose", onClick = vm::goChoose, enabled = s.inspected.isNotEmpty(), modifier = Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun ChooseContent(s: FaultUiState.Active, onPick: (String) -> Unit, onBack: () -> Unit, onSubmit: () -> Unit) {
    Column(Modifier.fillMaxSize().navigationBarsPadding()) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(s.fault.symptom, style = MaterialTheme.typography.titleLarge)
            Text("What is the most likely cause?", style = MaterialTheme.typography.bodyMedium, color = EquipXColors.TextSecondary)
            if (s.fault.options.isEmpty()) EmptyState("No answer options for this scenario")
            s.fault.options.forEachIndexed { i, o ->
                OptionRow(('A' + i).toString(), o.text.ifBlank { o.id }, selected = o.id == s.chosenOptionId, onClick = { onPick(o.id) })
            }
        }
        HairlineDivider()
        Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SecondaryButton("Inspect more", onClick = onBack, modifier = Modifier.weight(1f))
            PrimaryButton("Submit", onClick = onSubmit, enabled = s.chosenOptionId != null, modifier = Modifier.weight(1f))
        }
    }
}

@Composable
private fun OptionRow(letter: String, text: String, selected: Boolean, onClick: () -> Unit) {
    EquipXCard(
        Modifier.fillMaxWidth().animateContentSize(),
        onClick = onClick,
        containerColor = if (selected) EquipXColors.AccentTint else Color.White,
        borderColor = if (selected) EquipXColors.Accent else EquipXColors.Border,
        elevation = if (selected) 4.dp else 1.dp,
    ) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 12.dp).heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Box(
                Modifier.size(36.dp).clip(CircleShape).background(if (selected) EquipXColors.Accent else EquipXColors.AccentTint),
                contentAlignment = Alignment.Center,
            ) { Text(letter, style = MaterialTheme.typography.labelLarge, color = if (selected) Color.White else EquipXColors.Accent) }
            Text(text, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        }
    }
}

@Composable
private fun FeedbackContent(s: FaultUiState.Active, onRetry: () -> Unit, onDone: () -> Unit) {
    val correctText = s.fault.options.firstOrNull { it.id == s.fault.correctOptionId }?.text.orEmpty()
    val chosenText = s.fault.options.firstOrNull { it.id == s.chosenOptionId }?.text.orEmpty()
    Column(Modifier.fillMaxSize().navigationBarsPadding()) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(
                    Modifier.size(56.dp).clip(CircleShape).background(if (s.correct) EquipXColors.SuccessTint else EquipXColors.DangerTint),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        if (s.correct) Icons.Outlined.CheckCircle else Icons.Outlined.ErrorOutline,
                        contentDescription = null,
                        tint = if (s.correct) EquipXColors.Success else EquipXColors.Danger,
                        modifier = Modifier.size(32.dp),
                    )
                }
                Text(if (s.correct) "Correct diagnosis" else "Not quite", style = MaterialTheme.typography.headlineSmall)
            }
            if (s.pointsAwarded > 0) Pill("+${s.pointsAwarded} points", background = EquipXColors.SuccessTint, contentColor = EquipXColors.Success)
            if (!s.correct) Text("You chose: $chosenText", style = MaterialTheme.typography.bodyMedium, color = EquipXColors.TextSecondary)
            Text("Cause: $correctText", style = MaterialTheme.typography.titleSmall)
            if (s.fault.explanation.isNotBlank()) Callout(title = "Why", text = s.fault.explanation)
            if (s.fault.relatedPartIds.isNotEmpty()) {
                Text("Parts that mattered", style = MaterialTheme.typography.titleSmall, color = EquipXColors.TextSecondary)
                s.fault.relatedPartIds.forEach { id ->
                    val name = s.device.parts.firstOrNull { it.id == id }?.name?.ifBlank { null } ?: id
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(name, style = MaterialTheme.typography.bodyMedium)
                        if (id in s.inspected) Pill("Inspected", background = EquipXColors.SuccessTint, contentColor = EquipXColors.Success) else Pill("Missed")
                    }
                }
            }
        }
        HairlineDivider()
        Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SecondaryButton("Try again", onClick = onRetry, modifier = Modifier.weight(1f))
            PrimaryButton("Done", onClick = onDone, modifier = Modifier.weight(1f))
        }
    }
}
