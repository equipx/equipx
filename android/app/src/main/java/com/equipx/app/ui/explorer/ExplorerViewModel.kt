package com.equipx.app.ui.explorer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.equipx.app.data.ContentRepository
import com.equipx.app.data.ProgressRepository
import com.equipx.app.domain.Device
import com.equipx.app.domain.Part
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ExplorerUiState(
    val device: Device? = null,
    /** Highlighted + focused part in 3D. */
    val selectedPartId: String? = null,
    /** Bumped on every selection so re-selecting a part focuses the camera on it again. */
    val selectionToken: Int = 0,
    val sheetOpen: Boolean = false,
    val partsListOpen: Boolean = false,
) {
    val selectedPart: Part? get() = device?.parts?.firstOrNull { it.id == selectedPartId }
}

class ExplorerViewModel(
    private val deviceId: String,
    initialPartId: String?,
    private val content: ContentRepository,
    private val progress: ProgressRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(ExplorerUiState(selectedPartId = initialPartId))
    val state: StateFlow<ExplorerUiState> = _state.asStateFlow()

    init { load() }

    private fun load() {
        viewModelScope.launch {
            content.getDevice(deviceId)
                .onSuccess { d -> _state.update { it.copy(device = d) } }
                .onFailure {
                    // Content missing: still try to load the model by convention; placeholder text is used.
                    _state.update { s ->
                        s.copy(device = Device(id = deviceId, name = deviceId.replace('_', ' ').replaceFirstChar { c -> c.uppercase() }))
                    }
                }
        }
    }

    /** Select + focus + open the part sheet (hotspot tap or parts list). */
    fun selectPart(partId: String) {
        _state.update { it.copy(selectedPartId = partId, selectionToken = it.selectionToken + 1, sheetOpen = true, partsListOpen = false) }
        viewModelScope.launch { progress.markPartViewed(deviceId, partId) }
    }

    /** Closes the sheet; the part stays highlighted in 3D. */
    fun dismissSheet() = _state.update { it.copy(sheetOpen = false) }

    fun openPartsList() = _state.update { it.copy(partsListOpen = true) }
    fun closePartsList() = _state.update { it.copy(partsListOpen = false) }

    /** Clears the selection (the screen also resets the camera). */
    fun clearSelection() = _state.update { it.copy(selectedPartId = null, sheetOpen = false, selectionToken = it.selectionToken + 1) }
}
