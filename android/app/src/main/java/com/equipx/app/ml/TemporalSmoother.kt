package com.equipx.app.ml

/**
 * Debounces per-frame detections: a device appears once it was seen in at least [minHits] of the last [window]
 * processed frames (and in the current one), and disappears after [holdMs] without being seen.
 */
class TemporalSmoother(
    private val window: Int = 5,
    private val minHits: Int = 3,
    private val holdMs: Long = 1000L,
) {
    private val history = ArrayDeque<Set<String>>()
    private val active = LinkedHashMap<String, Pair<Detection, Long>>() // deviceId -> (last detection, last seen ms)

    fun update(detections: List<Detection>, nowMs: Long): List<Detection> {
        // One detection per device per frame: keep the most confident.
        val byDevice = detections.groupBy { it.deviceId }.mapValues { (_, v) -> v.maxBy { it.confidence } }
        history.addLast(byDevice.keys.toSet())
        while (history.size > window) history.removeFirst()
        for ((id, d) in byDevice) {
            val hits = history.count { id in it }
            if (hits >= minHits || id in active) active[id] = d to nowMs
        }
        return current(nowMs)
    }

    /** Detections still shown at [nowMs] (also call on skipped frames so the hold timeout expires). */
    fun current(nowMs: Long): List<Detection> {
        active.entries.removeAll { nowMs - it.value.second > holdMs }
        return active.values.map { it.first }.sortedByDescending { it.confidence }
    }

    fun reset() {
        history.clear()
        active.clear()
    }
}
