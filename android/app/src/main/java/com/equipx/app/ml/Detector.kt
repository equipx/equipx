package com.equipx.app.ml

import android.graphics.Bitmap

/**
 * Camera frame handed to a [Detector]. Pixels are lazy: [bitmapProvider] converts the YUV frame to an upright-agnostic
 * ARGB bitmap (in sensor orientation; [rotationDegrees] says how to rotate it to upright) and is only valid during
 * the `detect` call, so detectors that throttle never pay for the conversion on skipped frames.
 */
class Frame(
    val width: Int,
    val height: Int,
    val rotationDegrees: Int,
    val timestampNanos: Long,
    val bitmapProvider: (() -> Bitmap?)? = null,
)

/** Normalized (0..1) box in the upright preview coordinate space. */
data class NormalizedBox(val left: Float, val top: Float, val right: Float, val bottom: Float)

data class Detection(
    val label: String,
    val deviceId: String,
    val confidence: Float,
    val box: NormalizedBox,
)

/** Device-level detector. Called on the camera analysis executor, one frame at a time. */
interface Detector {
    fun detect(frame: Frame): List<Detection>
    fun reset() {}

    /** True for the scripted [FakeDetector]; the Scan screen shows a "Demo detector" label. */
    val isDemo: Boolean get() = false

    /** Release native resources. Safe to call more than once; `detect` after close returns an empty list. */
    fun close() {}
}
