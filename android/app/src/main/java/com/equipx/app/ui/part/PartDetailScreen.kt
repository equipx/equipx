package com.equipx.app.ui.part

import androidx.compose.foundation.background
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.Row
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ViewInAr
import com.equipx.app.ui.components.DeviceIconTile
import com.equipx.app.ui.components.EquipXCard
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.equipx.app.data.ContentRepository
import com.equipx.app.data.ActivityTracker
import com.equipx.app.domain.ActivityEvent
import com.equipx.app.domain.Device
import com.equipx.app.domain.Part
import com.equipx.app.ui.appViewModel
import com.equipx.app.ui.components.Callout
import com.equipx.app.ui.components.EquipXTopBar
import com.equipx.app.ui.components.ErrorState
import com.equipx.app.ui.components.LoadingState
import com.equipx.app.ui.components.Pill
import com.equipx.app.ui.components.PrimaryButton
import com.equipx.app.ui.theme.EquipXColors
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface PartDetailState {
    data object Loading : PartDetailState
    data class Error(val message: String) : PartDetailState
    data class Loaded(val device: Device, val part: Part) : PartDetailState
}

class PartDetailViewModel(
    private val deviceId: String,
    private val partId: String,
    private val content: ContentRepository,
    private val tracker: ActivityTracker,
) : ViewModel() {
    private val _state = MutableStateFlow<PartDetailState>(PartDetailState.Loading)
    val state: StateFlow<PartDetailState> = _state.asStateFlow()

    init { load() }

    fun load() {
        _state.value = PartDetailState.Loading
        viewModelScope.launch {
            content.getDevice(deviceId)
                .onSuccess { d ->
                    val part = d.parts.firstOrNull { it.id == partId }
                    if (part == null) {
                        _state.value = PartDetailState.Error("This part could not be found.")
                    } else {
                        _state.value = PartDetailState.Loaded(d, part)
                        tracker.post(ActivityEvent.PartViewed(deviceId, partId, fromExplorer = false))
                    }
                }
                .onFailure { _state.value = PartDetailState.Error("Could not load this part.") }
        }
    }
}

@Composable
fun PartDetailScreen(
    deviceId: String,
    partId: String,
    onBack: () -> Unit,
    onShowIn3D: () -> Unit,
) {
    val vm = appViewModel(key = "part-$deviceId-$partId") {
        PartDetailViewModel(deviceId, partId, it.contentRepository, it.activityTracker)
    }
    val state by vm.state.collectAsStateWithLifecycle()
    Column(Modifier.fillMaxSize().background(EquipXColors.Canvas)) {
        EquipXTopBar(title = (state as? PartDetailState.Loaded)?.part?.name ?: "Part", onBack = onBack)
        when (val s = state) {
            PartDetailState.Loading -> LoadingState()
            is PartDetailState.Error -> ErrorState(s.message, onRetry = vm::load)
            is PartDetailState.Loaded -> Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .navigationBarsPadding()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                EquipXCard(Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        DeviceIconTile(s.device.id, s.device.category, size = 52.dp)
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            if (s.part.number > 0) Text("PART ${s.part.number}", style = MaterialTheme.typography.labelSmall, color = EquipXColors.Accent)
                            Text(s.part.name.ifBlank { s.part.id }, style = MaterialTheme.typography.headlineSmall)
                            Text(s.device.name, style = MaterialTheme.typography.bodySmall, color = EquipXColors.TextSecondary)
                        }
                    }
                }
                Section("Function", s.part.function)
                Section("How to use", s.part.usage)
                Callout(
                    title = "Safety",
                    text = if (s.part.safety.isEmpty()) "Follow your lab's safety rules and ask your instructor before using this part."
                    else s.part.safety.joinToString("\n") { "• $it" },
                )
                PrimaryButton("Show in 3D", onClick = onShowIn3D, modifier = Modifier.fillMaxWidth(), icon = Icons.Outlined.ViewInAr)
            }
        }
    }
}

@Composable
private fun Section(title: String, body: String) {
    EquipXCard(Modifier.fillMaxWidth(), elevation = 1.dp) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(title.uppercase(), style = MaterialTheme.typography.labelSmall, color = EquipXColors.Accent)
            Text(body.ifBlank { "Details for this part are coming soon." }, style = MaterialTheme.typography.bodyLarge)
        }
    }
}
