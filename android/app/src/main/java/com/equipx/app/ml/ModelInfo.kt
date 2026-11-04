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
            require(inShape.size == 4) { "input shape must be [1,H,W,3], got $inShape" }
            require(inShape[1] == inShape[2]) { "non-square input not supported: $inShape" }
            require(inShape[3] == 3) { "input must be NHWC with 3 channels: $inShape" }
            input["layout"]?.jsonPrimitive?.contentOrNull?.let { require(it.equals("NHWC", true)) { "unsupported input layout $it" } }
            input["dtype"]?.jsonPrimitive?.contentOrNull?.let { require(it == "float32") { "unsupported input dtype $it (float32 only)" } }
            val output = root["output"]?.jsonObject ?: throw IllegalArgumentException("missing output")
            val outShape = ints(output["shape"])
            require(outShape.size == 3 && outShape[0] == 1) { "output shape must be 3D with batch 1: $outShape" }
            val ch = 4 + classes.size
            val channelsFirst = when {
                outShape[1] == ch && outShape[2] != ch -> true
                outShape[2] == ch && outShape[1] != ch -> false
                outShape[1] == ch -> true // ambiguous square: trust the notebook's own check
                else -> throw IllegalArgumentException("output shape $outShape does not match ${ch} channels")
            }
            val units = output["box_units"]?.jsonPrimitive?.contentOrNull.orEmpty()
            return ModelInfo(
                classes = classes,
                inputSize = inShape[1],
                channelsFirst = channelsFirst,
                numAnchors = if (channelsFirst) outShape[2] else outShape[1],
                boxesNormalized = !units.startsWith("pixels", ignoreCase = true),
            )
        }

        private fun ints(e: kotlinx.serialization.json.JsonElement?): List<Int> =
            (e as? JsonArray)?.map { it.jsonPrimitive.intOrNull ?: throw IllegalArgumentException("bad shape") }
                ?: throw IllegalArgumentException("missing shape")
    }
}

