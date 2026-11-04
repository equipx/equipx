package com.equipx.app.domain

import kotlinx.serialization.Serializable

/** Entry of `content/index.json`. */
@Serializable
data class DeviceSummary(
    val id: String,
    val name: String,
    val category: String = "",
    val summary: String = "",
)

@Serializable
data class DeviceIndex(val devices: List<DeviceSummary> = emptyList())

@Serializable
data class ModelInfo(
    val asset: String? = null,
    val hotspots: String? = null,
    val cameraOrbit: String? = null,
    val credit: String? = null,
)

@Serializable
data class LayoutBody(
    val width: Double = 0.0,
    val height: Double = 0.0,
    val depth: Double = 0.0,
    val cornerRadius: Double = 0.0,
    val color: String = "#D9D7D2",
)

@Serializable
data class LayoutPart(
    val id: String,
    val type: String = "",
    val face: String = "front",
    val position: List<Double> = emptyList(),
    val size: List<Double> = emptyList(),
    val color: String? = null,
)

@Serializable
data class Layout(
    val units: String = "m",
    val body: LayoutBody = LayoutBody(),
    val parts: List<LayoutPart> = emptyList(),
)

@Serializable
data class Part(
    val id: String,
    val number: Int = 0,
    val name: String = "",
    val function: String = "",
    val usage: String = "",
    val safety: List<String> = emptyList(),
)

@Serializable
data class ProcedureStep(val text: String, val partId: String)

@Serializable
data class Procedure(
    val id: String,
    val title: String = "",
    val steps: List<ProcedureStep> = emptyList(),
)

@Serializable
data class FaultOption(val id: String, val text: String = "")

@Serializable
data class Fault(
    val id: String,
    val symptom: String = "",
    val options: List<FaultOption> = emptyList(),
    val correctOptionId: String = "",
    val explanation: String = "",
    val relatedPartIds: List<String> = emptyList(),
)

@Serializable
data class QuizQuestion(
    val id: String,
    val prompt: String = "",
    val options: List<String> = emptyList(),
    val correctIndex: Int = 0,
    val explanation: String = "",
) {
    /** False when [correctIndex] does not point at an option (bad content); such questions are skipped. */
    val isValid: Boolean get() = correctIndex in options.indices
}

@Serializable
data class Quiz(
    val id: String,
    val title: String = "",
    val timeLimitSec: Int? = null,
    val questions: List<QuizQuestion> = emptyList(),
)

/** Full content of `content/<deviceId>.json`. */
@Serializable
data class Device(
    val id: String,
    val name: String,
    val category: String = "",
    val summary: String = "",
    val overview: String = "",
    val model: ModelInfo = ModelInfo(),
    val layout: Layout = Layout(),
    val parts: List<Part> = emptyList(),
    val safetyRules: List<String> = emptyList(),
    val procedures: List<Procedure> = emptyList(),
    val faults: List<Fault> = emptyList(),
    val quizzes: List<Quiz> = emptyList(),
)

/** `models/<deviceId>.hotspots.json` (consumed by the web explorer; modelled here for completeness). */
@Serializable
data class HotspotEntry(
    val partId: String,
    val number: Int = 0,
    val position: List<Double> = emptyList(),
    val normal: List<Double> = emptyList(),
)

@Serializable
data class HotspotsFile(val deviceId: String = "", val hotspots: List<HotspotEntry> = emptyList())
