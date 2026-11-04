package com.equipx.app.ml

/** Scripted detector: after [delayMs] of frames it reports a centred digital multimeter. */
class FakeDetector(
    private val delayMs: Long = 1500L,
    private val clock: () -> Long = { System.nanoTime() / 1_000_000 },
) : Detector {

    @Volatile private var firstFrameAt: Long? = null

    override fun detect(frame: Frame): List<Detection> {
        val now = clock()
        val start = firstFrameAt ?: now.also { firstFrameAt = it }
        if (now - start < delayMs) return emptyList()
        return listOf(
            Detection(
                label = "Digital Multimeter",
                deviceId = "digital_multimeter",
                confidence = 0.92f,
                box = NormalizedBox(0.2f, 0.25f, 0.8f, 0.75f),
            )
        )
    }

    override fun reset() {
        firstFrameAt = null
    }
}
