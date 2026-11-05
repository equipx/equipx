package com.equipx.app.ml

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** What the Colab export cell writes to `model_info.json`, reduced to what the decoder needs. */
data class ModelInfo(
    val classes: List<String>,
    val inputSize: Int,
    /** True for `[1, 4+nc, anchors]`, false for `[1, anchors, 4+nc]`. */
    val channelsFirst: Boolean,
    val numAnchors: Int,
    /** True if cx,cy,w,h are 0..1 of the input; false if pixels of the input. */
    val boxesNormalized: Boolean,
    /** True for NCHW `[1,3,H,W]` input (planar R,G,B), false for NHWC `[1,H,W,3]`. */
    val inputChannelsFirst: Boolean = false,
) {
    val numClasses: Int get() = classes.size
    val channels: Int get() = 4 + numClasses

    companion object {
        /** Parses the notebook's `model_info.json`. Throws [IllegalArgumentException] if it is unusable. */
        fun parse(text: String): ModelInfo {
            val root = Json.parseToJsonElement(text).jsonObject
            val classes = root["classes"]?.jsonArray?.map { it.jsonPrimitive.content }
                ?: throw IllegalArgumentException("model_info.json: missing classes")
            require(classes.isNotEmpty()) { "model_info.json: empty classes" }
            val input = root["input"]?.jsonObject ?: throw IllegalArgumentException("missing input")
            val inShape = ints(input["shape"])
            require(inShape.size == 4 && inShape[0] == 1) { "input shape must be 4D with batch 1, got $inShape" }
            val declared = input["layout"]?.jsonPrimitive?.contentOrNull?.trim()?.uppercase()
            val inputChannelsFirst = when (declared) {
                "NCHW" -> true
                "NHWC" -> false
                null, "" -> when {
                    inShape[1] == 3 && inShape[3] != 3 -> true
                    inShape[3] == 3 -> false
                    else -> throw IllegalArgumentException("cannot infer input layout from $inShape")
                }
                else -> throw IllegalArgumentException("unsupported input layout $declared")
            }
            val h = if (inputChannelsFirst) inShape[2] else inShape[1]
            val w = if (inputChannelsFirst) inShape[3] else inShape[2]
            val c = if (inputChannelsFirst) inShape[1] else inShape[3]
            require(c == 3) { "input must have 3 channels: $inShape ($declared)" }
            require(h == w) { "non-square input not supported: $inShape" }
            input["dtype"]?.jsonPrimitive?.contentOrNull?.let { require(it == "float32") { "unsupported input dtype $it (float32 only)" } }
            val output = root["output"]?.jsonObject ?: throw IllegalArgumentException("missing output")
            val outShape = ints(output["shape"])
            require(outShape.size == 3 && outShape[0] == 1) { "output shape must be 3D with batch 1: $outShape" }
            val ch = 4 + classes.size
            val shapeGuess = when {
                outShape[1] == ch && outShape[2] != ch -> true
                outShape[2] == ch && outShape[1] != ch -> false
                outShape[1] == ch -> true // ambiguous square: trust the notebook's own check
                else -> throw IllegalArgumentException("output shape $outShape does not match $ch channels")
            }
            val channelsFirst = when (output["layout"]?.jsonPrimitive?.contentOrNull?.trim()?.lowercase()) {
                "channels_first", "nch", "ncn" -> true
                "anchors_first", "channels_last", "nnc" -> false
                else -> shapeGuess
            }
            require(outShape[if (channelsFirst) 1 else 2] == ch) { "output shape $outShape does not match layout/$ch channels" }
            val units = output["box_units"]?.jsonPrimitive?.contentOrNull.orEmpty()
            return ModelInfo(
                classes = classes,
                inputSize = h,
                channelsFirst = channelsFirst,
                numAnchors = if (channelsFirst) outShape[2] else outShape[1],
                boxesNormalized = !units.startsWith("pixels", ignoreCase = true),
                inputChannelsFirst = inputChannelsFirst,
            )
        }

        private fun ints(e: kotlinx.serialization.json.JsonElement?): List<Int> =
            (e as? JsonArray)?.map { it.jsonPrimitive.intOrNull ?: throw IllegalArgumentException("bad shape") }
                ?: throw IllegalArgumentException("missing shape")
    }
}

