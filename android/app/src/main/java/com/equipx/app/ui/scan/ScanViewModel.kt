package com.equipx.app.ui.scan

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.equipx.app.data.ContentRepository
import com.equipx.app.ml.Detection
import com.equipx.app.ml.Detector
import com.equipx.app.ml.Frame
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class ScanViewModel(
    private val detector: Detector,
    private val content: ContentRepository,
) : ViewModel() {

    private val _detections = MutableStateFlow<List<Detection>>(emptyList())
    val detections: StateFlow<List<Detection>> = _detections.asStateFlow()

    /** deviceId -> display name from the content index (falls back to the detector label). */
    private val _names = MutableStateFlow<Map<String, String>>(emptyMap())
    val names: StateFlow<Map<String, String>> = _names.asStateFlow()

    init {
        viewModelScope.launch {
            content.getIndex().onSuccess { list -> _names.value = list.associate { it.id to it.name } }
        }
    }

    /** Called on the camera analysis thread. */
    fun onFrame(frame: Frame) {
        val result = detector.detect(frame)
        if (result != _detections.value) _detections.value = result
    }

    fun reset() {
        detector.reset()
        _detections.value = emptyList()
    }
}
