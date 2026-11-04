package com.equipx.app.ui.scan

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.equipx.app.data.ActivityTracker
import com.equipx.app.data.ContentRepository
import com.equipx.app.domain.ActivityEvent
import com.equipx.app.domain.DeviceSummary
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
    private val tracker: ActivityTracker,
) : ViewModel() {

    private val _detections = MutableStateFlow<List<Detection>>(emptyList())
    val detections: StateFlow<List<Detection>> = _detections.asStateFlow()

    /** deviceId -> display name from the content index (falls back to the detector label). */
    private val _names = MutableStateFlow<Map<String, String>>(emptyMap())
    val names: StateFlow<Map<String, String>> = _names.asStateFlow()

    /** All devices, for the manual "Choose device" sheet. */
    private val _devices = MutableStateFlow<List<DeviceSummary>>(emptyList())
    val devices: StateFlow<List<DeviceSummary>> = _devices.asStateFlow()

    init {
        viewModelScope.launch {
            content.getIndex().onSuccess { list ->
                _names.value = list.associate { it.id to it.name }
                _devices.value = list
            }
        }
    }

    /** Called on the camera analysis thread. */
    fun onFrame(frame: Frame) {
        val result = detector.detect(frame)
        if (result != _detections.value) _detections.value = result
    }

    /** The student tapped a detection chip: that counts as a scan of the device. */
    fun onChipTapped(deviceId: String) {
        tracker.post(ActivityEvent.Scan(deviceId, com.equipx.app.domain.PointsRules.scanDay(System.currentTimeMillis())))
    }

    /**
     * Manual device choice (fallback when detection fails). Deliberately NOT a scan: it posts no [ActivityEvent.Scan],
     * so it awards no scan points/badge and cannot complete a mission's scan task. Scanning must be real.
     */
    fun onManualChoice(@Suppress("UNUSED_PARAMETER") deviceId: String) = Unit

    fun reset() {
        detector.reset()
        _detections.value = emptyList()
    }
}
