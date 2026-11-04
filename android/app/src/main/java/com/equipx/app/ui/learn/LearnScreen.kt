package com.equipx.app.ui.learn

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.equipx.app.data.ContentRepository
import com.equipx.app.data.ProgressRepository
import com.equipx.app.domain.DeviceSummary
import com.equipx.app.ui.appViewModel
import com.equipx.app.ui.components.AccentChip
import com.equipx.app.ui.components.DeviceIconTile
import com.equipx.app.ui.components.EmptyState
import com.equipx.app.ui.components.EquipXCard
import com.equipx.app.ui.components.ErrorState
import com.equipx.app.ui.components.LoadingState
import com.equipx.app.ui.components.ProgressRing
import com.equipx.app.ui.components.TabTitle
import com.equipx.app.ui.theme.CategoryAccents
import com.equipx.app.ui.theme.EquipXColors
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** [viewedCount] is the number of this device's parts the student has viewed. */
data class LearnItem(val summary: DeviceSummary, val partCount: Int?, val viewedCount: Int = 0) {
    /** Share of parts viewed, 0..1 (0 when the part count is unknown). */
    val progress: Float get() = if (partCount == null || partCount <= 0) 0f else (viewedCount.toFloat() / partCount).coerceIn(0f, 1f)
}

sealed interface LearnUiState {
    data object Loading : LearnUiState
    data class Loaded(val devices: List<LearnItem>) : LearnUiState
    data class Error(val message: String) : LearnUiState
}

/** Loads the device list with part counts and combines it with the viewed-parts progress. Shared by Home and Learn. */
class LearnViewModel(private val content: ContentRepository, private val progress: ProgressRepository) : ViewModel() {
    private val _state = MutableStateFlow<LearnUiState>(LearnUiState.Loading)
    val state: StateFlow<LearnUiState> = _state.asStateFlow()

    init { load() }

    fun load() {
        _state.value = LearnUiState.Loading
        viewModelScope.launch {
            content.getIndex()
                .onSuccess { index ->
                    // Part counts come from the full device files; a device that fails to load still shows (without a count).
                    val parts = index.map { d -> async { d to content.getDevice(d.id).getOrNull()?.parts?.map { it.id }?.toSet() } }.awaitAll()
                    progress.viewedParts.collect { viewed ->
                        _state.value = LearnUiState.Loaded(parts.map { (d, ids) ->
                            LearnItem(d, ids?.size, ids?.let { s -> viewed.count { it.startsWith(d.id + ":") && it.removePrefix(d.id + ":") in s } } ?: 0)
                        })
                    }
                }
                .onFailure { _state.value = LearnUiState.Error("Could not load the device list.") }
        }
    }
}

@Composable
fun LearnScreen(onOpenDevice: (String) -> Unit) {
    val vm = appViewModel { LearnViewModel(it.contentRepository, it.progressRepository) }
    val state by vm.state.collectAsStateWithLifecycle()
    LearnContent(state, onRetry = vm::load, onOpenDevice = onOpenDevice)
}

/** Stateless Learn tab: 2-column grid of device cards with a progress ring (parts viewed %). */
@Composable
fun LearnContent(state: LearnUiState, onRetry: () -> Unit, onOpenDevice: (String) -> Unit) {
    Column(Modifier.fillMaxSize().background(EquipXColors.Canvas)) {
        TabTitle("Learn", subtitle = "Pick a device to explore its parts, procedures and quizzes")
        when (val s = state) {
            LearnUiState.Loading -> LoadingState()
            is LearnUiState.Error -> ErrorState(s.message, onRetry = onRetry, title = "Could not load equipment")
            is LearnUiState.Loaded -> if (s.devices.isEmpty()) {
                EmptyState("No devices yet", message = "Equipment will appear here once content is added.", icon = Icons.Outlined.Memory)
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(s.devices, key = { it.summary.id }) { item -> DeviceGridCard(item, onClick = { onOpenDevice(item.summary.id) }) }
                }
            }
        }
    }
}

/** Device card used by the Learn grid and the Home device grid. */
@Composable
fun DeviceGridCard(item: LearnItem, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val d = item.summary
    val accent = CategoryAccents.forCategory(d.category)
    EquipXCard(modifier.fillMaxWidth(), onClick = onClick) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
                DeviceIconTile(d.id, d.category, size = 48.dp)
                ProgressRing(item.progress, size = 44.dp, stroke = 4.dp, color = accent.strong, trackColor = accent.tint) {
                    Text("${(item.progress * 100).toInt()}%", style = MaterialTheme.typography.labelSmall, color = EquipXColors.TextSecondary)
                }
            }
            Text(d.name, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis, minLines = 2)
            if (d.category.isNotBlank()) AccentChip(d.category, accent)
            item.partCount?.let {
                Text(
                    "${item.viewedCount} of $it parts viewed",
                    style = MaterialTheme.typography.bodySmall,
                    color = EquipXColors.TextSecondary,
                )
            }
        }
    }
}
