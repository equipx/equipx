package com.equipx.app.domain

/** Cosmetic level derived from points: one level per [POINTS_PER_LEVEL] points. */
data class LevelInfo(val level: Int, val pointsIntoLevel: Int, val pointsForNext: Int) {
    val fraction: Float get() = if (pointsForNext <= 0) 0f else pointsIntoLevel.toFloat() / pointsForNext
}

object Levels {
    const val POINTS_PER_LEVEL = 100
    fun of(points: Int): LevelInfo {
        val p = points.coerceAtLeast(0)
        return LevelInfo(p / POINTS_PER_LEVEL + 1, p % POINTS_PER_LEVEL, POINTS_PER_LEVEL)
    }
}
