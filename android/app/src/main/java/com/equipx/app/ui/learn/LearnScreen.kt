package com.equipx.app.ui.learn

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.equipx.app.data.ContentRepository
import com.equipx.app.domain.DeviceSummary
import com.equipx.app.ui.appViewModel
import com.equipx.app.ui.components.EquipXTopBar
import com.equipx.app.ui.components.HairlineDivider
import com.equipx.app.ui.components.ListRow
import com.equipx.app.ui.components.Pill
import com.equipx.app.ui.components.LoadingState
import com.equipx.app.ui.components.ErrorState
import com.equipx.app.ui.components.EmptyState
import com.equipx.app.ui.theme.EquipXColors
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch

data class LearnItem(val summary: DeviceSummary, val partCount: Int?)

sealed interface LearnUiState {
    data object Loading : LearnUiState
    data class Loaded(val devices: List<LearnItem>) : LearnUiState
    data class Error(val message: String) : LearnUiState
}

class LearnViewModel(private val content: ContentRepository) : ViewModel() {
    private val _state = MutableStateFlow<LearnUiState>(LearnUiState.Loading)
    val state: StateFlow<LearnUiState> = _state.asStateFlow()

    init { load() }

    fun load() {
        _state.value = LearnUiState.Loading
        viewModelScope.launch {
            content.getIndex()
                .onSuccess { index ->
                    // Part counts come from the full device files; a device that fails to load still shows (without a count).
                    val items = index.map { d -> async { LearnItem(d, content.getDevice(d.id).getOrNull()?.parts?.size) } }.awaitAll()
                    _state.value = LearnUiState.Loaded(items)
                }
                .onFailure { _state.value = LearnUiState.Error("Could not load the device list.") }
        }
    }
}

@Composable
fun LearnScreen(onOpenDevice: (String) -> Unit) {
    val vm = appViewModel { LearnViewModel(it.contentRepository) }
    val state by vm.state.collectAsStateWithLifecycle()
    Column(Modifier.fillMaxSize()) {
        EquipXTopBar(title = "Learn")
        when (val s = state) {
            LearnUiState.Loading -> LoadingState()
            is LearnUiState.Error -> ErrorState(s.message, onRetry = vm::load, title = "Could not load equipment")
            is LearnUiState.Loaded -> if (s.devices.isEmpty()) {
                EmptyState("No devices yet", message = "Equipment will appear here once content is added.")
            } else {
                LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
                    items(s.devices, key = { it.summary.id }) { item ->
                        val d = item.summary
                        ListRow(
                            title = d.name,
                            meta = d.summary.ifBlank { null },
                            icon = Icons.Outlined.Memory,
                            onClick = { onOpenDevice(d.id) },
                            trailing = {
                                Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    if (d.category.isNotBlank()) Pill(d.category)
                                    item.partCount?.let {
                                        Text(
                                            if (it == 1) "1 part" else "$it parts",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = EquipXColors.TextSecondary,
                                        )
                                    }
                                }
                            },
                        )
                        HairlineDivider()
                    }
                }
            }
        }
    }
}
