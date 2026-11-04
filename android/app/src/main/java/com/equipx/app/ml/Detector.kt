package com.equipx.app.ml

/** Camera frame metadata handed to a [Detector]. Pixel access is added with the real detector. */
class Frame(
    val width: Int,
    val height: Int,
    val rotationDegrees: Int,
    val timestampNanos: Long,
)

/** Normalized (0..1) box in the upright preview coordinate space. */
data class NormalizedBox(val left: Float, val top: Float, val right: Float, val bottom: Float)

data class Detection(
    val label: String,
    val deviceId: String,
    val confidence: Float,
    val box: NormalizedBox,
)

/** Device-level detector. `TfliteDetector` will implement this later. */
interface Detector {
    fun detect(frame: Frame): List<Detection>
    fun reset() {}
}
