package com.equipx.app.ml

import kotlin.math.max
import kotlin.math.min

/** A decoded box in model-input space (0..1 of the square input), before un-letterboxing. */
data class Candidate(val classIndex: Int, val score: Float, val box: NormalizedBox)

/** Pure-Kotlin YOLO head decoding + class-wise NMS (no TFLite dependency, unit tested). */
object YoloDecoder {
    const val DEFAULT_CONFIDENCE = 0.5f
    const val DEFAULT_IOU = 0.45f

    /**
     * [out] is the flattened `[1,C,N]` (channels-first) or `[1,N,C]` output. Boxes are cx,cy,w,h; scores are already
     * sigmoid with no objectness. The best class per anchor is kept if it reaches [confidence]; NMS is class-wise.
     */
    fun decode(
        out: FloatArray,
        info: ModelInfo,
        confidence: Float = DEFAULT_CONFIDENCE,
        iou: Float = DEFAULT_IOU,
    ): List<Candidate> {
        val n = info.numAnchors
        val nc = info.numClasses
        require(out.size >= n * (4 + nc)) { "output has ${out.size} floats, expected ${n * (4 + nc)}" }
        val scale = if (info.boxesNormalized) 1f else 1f / info.inputSize
        fun at(channel: Int, anchor: Int): Float =
            if (info.channelsFirst) out[channel * n + anchor] else out[anchor * (4 + nc) + channel]

        val found = ArrayList<Candidate>()
        for (a in 0 until n) {
            var best = -1
            var bestScore = confidence
            for (c in 0 until nc) {
                val s = at(4 + c, a)
                if (s >= bestScore) { bestScore = s; best = c }
            }
            if (best < 0) continue
            val cx = at(0, a) * scale
            val cy = at(1, a) * scale
            val w = at(2, a) * scale
            val h = at(3, a) * scale
            if (w <= 0f || h <= 0f) continue
            found += Candidate(best, bestScore, NormalizedBox(cx - w / 2, cy - h / 2, cx + w / 2, cy + h / 2))
        }
        return nms(found, iou)
    }

    /** Class-wise greedy NMS, highest score first. */
    fun nms(candidates: List<Candidate>, iouThreshold: Float = DEFAULT_IOU): List<Candidate> {
        val kept = ArrayList<Candidate>()
        for (c in candidates.sortedByDescending { it.score }) {
            if (kept.none { it.classIndex == c.classIndex && iou(it.box, c.box) > iouThreshold }) kept += c
        }
        return kept
    }

    fun iou(a: NormalizedBox, b: NormalizedBox): Float {
        val iw = min(a.right, b.right) - max(a.left, b.left)
        val ih = min(a.bottom, b.bottom) - max(a.top, b.top)
        if (iw <= 0f || ih <= 0f) return 0f
        val inter = iw * ih
        val union = area(a) + area(b) - inter
        return if (union <= 0f) 0f else inter / union
    }

    private fun area(b: NormalizedBox) = max(0f, b.right - b.left) * max(0f, b.bottom - b.top)
}

/** Maps between an upright source image and the square letterboxed model input. */
class Letterbox(val srcW: Int, val srcH: Int, val size: Int) {
    val scale: Float = min(size.toFloat() / srcW, size.toFloat() / srcH)
    val scaledW: Int = Math.round(srcW * scale)
    val scaledH: Int = Math.round(srcH * scale)
    val padX: Float = (size - scaledW) / 2f
    val padY: Float = (size - scaledH) / 2f

    /** Box in 0..1 of the model input -> 0..1 of the upright source, clamped. */
    fun unmap(b: NormalizedBox): NormalizedBox {
        fun x(v: Float) = ((v * size - padX) / scaledW).coerceIn(0f, 1f)
        fun y(v: Float) = ((v * size - padY) / scaledH).coerceIn(0f, 1f)
        return NormalizedBox(x(b.left), y(b.top), x(b.right), y(b.bottom))
    }
}

/** Pure-Kotlin packing of letterboxed ARGB pixels into the float32 model input (RGB / 255). */
object InputPacking {
    /** Writes [pixels] (row-major ARGB, size*size) into [dst]: planar R,G,B if [channelsFirst] (NCHW), else interleaved (NHWC). */
    fun pack(pixels: IntArray, channelsFirst: Boolean, dst: java.nio.FloatBuffer) {
        if (channelsFirst) {
            for (shift in intArrayOf(16, 8, 0)) for (p in pixels) dst.put(((p shr shift) and 0xFF) / 255f)
        } else {
            for (p in pixels) {
                dst.put(((p shr 16) and 0xFF) / 255f)
                dst.put(((p shr 8) and 0xFF) / 255f)
                dst.put((p and 0xFF) / 255f)
            }
        }
    }
}
