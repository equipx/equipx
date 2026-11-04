package com.equipx.app.ui.credits

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material.icons.outlined.TextFields
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.equipx.app.data.ContentRepository
import com.equipx.app.ui.appViewModel
import com.equipx.app.ui.components.EquipXTopBar
import com.equipx.app.ui.components.ErrorState
import com.equipx.app.ui.components.HairlineDivider
import com.equipx.app.ui.components.ListRow
import com.equipx.app.ui.components.LoadingState
import com.equipx.app.ui.components.SectionHeader
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** One 3D model line: [credit] is null for generated models. */
data class ModelCredit(val deviceName: String, val credit: String?)

sealed interface CreditsState {
    data object Loading : CreditsState
    data class Error(val message: String) : CreditsState
    data class Loaded(val models: List<ModelCredit>) : CreditsState
}

class CreditsViewModel(private val content: ContentRepository) : ViewModel() {
    private val _state = MutableStateFlow<CreditsState>(CreditsState.Loading)
    val state: StateFlow<CreditsState> = _state.asStateFlow()

    init { load() }

    fun load() {
        _state.value = CreditsState.Loading
        viewModelScope.launch {
            content.getIndex()
                .onSuccess { index ->
                    val models = index.map { s ->
                        async {
                            val credit = content.getDevice(s.id).getOrNull()?.model?.credit?.trim()?.takeIf { it.isNotEmpty() }
                            ModelCredit(s.name, credit)
                        }
                    }.awaitAll()
                    _state.value = CreditsState.Loaded(models)
                }
                .onFailure { _state.value = CreditsState.Error("Could not load the credits.") }
        }
    }
}

@Composable
fun CreditsScreen(onBack: () -> Unit) {
    val vm = appViewModel { CreditsViewModel(it.contentRepository) }
    val state by vm.state.collectAsStateWithLifecycle()
    Column(Modifier.fillMaxSize().background(com.equipx.app.ui.theme.EquipXColors.Canvas)) {
        EquipXTopBar(title = "Credits & licenses", onBack = onBack)
        when (val s = state) {
            CreditsState.Loading -> LoadingState()
            is CreditsState.Error -> ErrorState(s.message, onRetry = vm::load)
            is CreditsState.Loaded -> LazyColumn(
                Modifier.fillMaxSize().padding(horizontal = 16.dp),
                contentPadding = PaddingValues(bottom = 24.dp + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()),
            ) {
                item { SectionHeader("3D models") }
                items(s.models.size) { i ->
                    val m = s.models[i]
                    ListRow(
                        title = m.deviceName,
                        meta = m.credit ?: "Generated model",
                        icon = Icons.Outlined.Memory,
                    )
                    HairlineDivider()
                }
                item { SectionHeader("Open-source software") }
                item {
                    ListRow(
                        title = "model-viewer",
                        meta = "Google. Apache License 2.0. Renders the interactive 3D models.",
                        icon = Icons.Outlined.Code,
                    )
                    HairlineDivider()
                    ListRow(
                        title = "Inter",
                        meta = "The Inter Project Authors. SIL Open Font License 1.1. App typeface.",
                        icon = Icons.Outlined.TextFields,
                    )
                    HairlineDivider()
                }
            }
        }
    }
}
