package com.equipx.app.ml

import android.content.Context
import android.util.Log

/** Picks the real [TfliteDetector] when the bundled model loads, otherwise the scripted [FakeDetector]. */
object DetectorFactory {
    const val MODEL_ASSET = "ml/equipx_detector.tflite"
    const val INFO_ASSET = "ml/model_info.json"
    const val LABELS_ASSET = "ml/labels.txt"
    private const val TAG = "DetectorFactory"

    fun create(context: Context): Detector {
        val assets = context.applicationContext.assets
        return try {
            val have = assets.list("ml").orEmpty().toSet()
            if ("equipx_detector.tflite" !in have || "model_info.json" !in have) {
                Log.i(TAG, "No bundled model in assets/ml, using FakeDetector (demo)")
                return FakeDetector()
            }
            val info = ModelInfo.parse(assets.open(INFO_ASSET).bufferedReader().use { it.readText() })
            val labels = if ("labels.txt" in have) {
                assets.open(LABELS_ASSET).bufferedReader().useLines { l -> l.map { it.trim() }.filter { it.isNotEmpty() }.toList() }
            } else info.classes
            val detector = TfliteDetector(context.applicationContext, info, labels)
            Log.i(TAG, "Using TfliteDetector (${labels.size} classes, input ${info.inputSize})")
            detector
        } catch (t: Throwable) {
            Log.w(TAG, "Model failed to load, using FakeDetector (demo)", t)
            FakeDetector()
        }
    }
}
