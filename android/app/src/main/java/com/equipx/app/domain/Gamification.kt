package com.equipx.app.domain

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import kotlinx.serialization.Serializable

/** Something the student did that may earn points, badges or mission progress. */
sealed interface ActivityEvent {
    /** A device was scanned (scan chip tapped). [date] is the UTC calendar day (see [PointsRules.scanDay]). */
    data class Scan(val deviceId: String, val date: LocalDate) : ActivityEvent

    /** [fromExplorer] is true when the part was tapped in the 3D explorer or its parts drawer. */
    data class PartViewed(val deviceId: String, val partId: String, val fromExplorer: Boolean = false) : ActivityEvent

    /** [questionKey] is globally unique, e.g. `deviceId:quizId:questionId`. */
    data class QuizAnswered(val questionKey: String, val correct: Boolean) : ActivityEvent
    data class QuizCompleted(val attempt: QuizAttempt) : ActivityEvent

    /** [faultKey] is `deviceId:faultId`. */
    data class FaultDiagnosed(val faultKey: String, val correct: Boolean) : ActivityEvent
    data class ProcedureCompleted(val deviceId: String, val procedureId: String) : ActivityEvent
    data class MissionCompleted(val missionId: String) : ActivityEvent
}

@Serializable
enum class PointReason(val points: Int, val title: String) {
    SCAN(10, "Scanned a device"),
    PART_VIEW(2, "Viewed a new part"),
    QUIZ_CORRECT(5, "Correct quiz answer"),
    FAULT_FIXED(20, "Fault diagnosed"),
    MISSION_COMPLETE(50, "Mission complete"),
}

/** One row of the points ledger. [key] identifies what was rewarded and drives dedupe together with [reason]. */
@Serializable
data class PointEntry(
    val reason: PointReason = PointReason.PART_VIEW,
    val amount: Int = 0,
    val key: String = "",
    val timestampMillis: Long = 0L,
)

/** Pure points rules (mirrors the Supabase functions). */
object PointsRules {
    /** The UTC calendar day used for scan dedupe. */
    fun scanDay(nowMillis: Long): LocalDate = Instant.ofEpochMilli(nowMillis).atZone(ZoneOffset.UTC).toLocalDate()

    /** Ledger entries earned by [event] given the existing [history]; empty when already rewarded. */
    fun award(event: ActivityEvent, history: List<PointEntry>, nowMillis: Long): List<PointEntry> {
        val candidate: Pair<PointReason, String>? = when (event) {
            // Once per device per UTC calendar day (matches the Supabase function).
            is ActivityEvent.Scan -> PointReason.SCAN to "${event.deviceId}:${event.date}"
            // First view of each part, ever.
            is ActivityEvent.PartViewed -> PointReason.PART_VIEW to "${event.deviceId}:${event.partId}"
            // First time each question is answered correctly.
            is ActivityEvent.QuizAnswered -> if (event.correct) PointReason.QUIZ_CORRECT to event.questionKey else null
            // First correct diagnosis per scenario.
            is ActivityEvent.FaultDiagnosed -> if (event.correct) PointReason.FAULT_FIXED to event.faultKey else null
            // Once per mission.
            is ActivityEvent.MissionCompleted -> PointReason.MISSION_COMPLETE to event.missionId
            is ActivityEvent.QuizCompleted, is ActivityEvent.ProcedureCompleted -> null
        }
        val (reason, key) = candidate ?: return emptyList()
        if (history.any { it.reason == reason && it.key == key }) return emptyList()
        return listOf(PointEntry(reason, reason.points, key, nowMillis))
    }

    fun total(ledger: List<PointEntry>): Int = ledger.sumOf { it.amount }
}

enum class Badge(val id: String, val title: String, val description: String) {
    FIRST_SCAN("first_scan", "First Scan", "Scan your first device"),
    PART_SPOTTER("part_spotter", "Part Spotter", "View 10 different parts"),
    SAFETY_FIRST("safety_first", "Safety First", "Complete a procedure"),
    QUIZ_ACE("quiz_ace", "Quiz Ace", "Score 100% on a quiz"),
    FAULT_FINDER("fault_finder", "Fault Finder", "Diagnose 3 different faults"),
    MISSION_COMPLETE("mission_complete", "Mission Complete", "Finish a mission");

    companion object {
        fun byId(id: String): Badge? = entries.firstOrNull { it.id == id }
    }
}

@Serializable
data class UnlockedBadge(val id: String, val unlockedAtMillis: Long = 0L)

/** Aggregate numbers badges are computed from. */
data class BadgeProgress(
    val scans: Int = 0,
    val distinctPartsViewed: Int = 0,
    val proceduresCompleted: Int = 0,
    val hasPerfectQuiz: Boolean = false,
    val distinctFaultsFixed: Int = 0,
    val missionsCompleted: Int = 0,
)

object BadgeRules {
    const val PART_SPOTTER_PARTS = 10
    const val FAULT_FINDER_FAULTS = 3

    fun earned(p: BadgeProgress): Set<Badge> = buildSet {
        if (p.scans >= 1) add(Badge.FIRST_SCAN)
        if (p.distinctPartsViewed >= PART_SPOTTER_PARTS) add(Badge.PART_SPOTTER)
        if (p.proceduresCompleted >= 1) add(Badge.SAFETY_FIRST)
        if (p.hasPerfectQuiz) add(Badge.QUIZ_ACE)
        if (p.distinctFaultsFixed >= FAULT_FINDER_FAULTS) add(Badge.FAULT_FINDER)
        if (p.missionsCompleted >= 1) add(Badge.MISSION_COMPLETE)
    }

    /** Badges earned by [progress] that are not in [alreadyUnlocked] (badge ids), in declaration order. */
    fun newlyUnlocked(progress: BadgeProgress, alreadyUnlocked: Set<String>): List<Badge> =
        earned(progress).filter { it.id !in alreadyUnlocked }.sortedBy { it.ordinal }
}

/** Everything the engine needs and updates. */
data class ProgressState(
    val ledger: List<PointEntry> = emptyList(),
    val unlockedBadges: List<UnlockedBadge> = emptyList(),
    val scanCount: Int = 0,
    val viewedParts: Set<String> = emptySet(),
    val completedProcedures: Set<String> = emptySet(),
    val hasPerfectQuiz: Boolean = false,
    val fixedFaults: Set<String> = emptySet(),
    val completedMissions: Set<String> = emptySet(),
) {
    val points: Int get() = PointsRules.total(ledger)

    fun badgeProgress() = BadgeProgress(
        scans = scanCount,
        distinctPartsViewed = viewedParts.size,
        proceduresCompleted = completedProcedures.size,
        hasPerfectQuiz = hasPerfectQuiz,
        distinctFaultsFixed = fixedFaults.size,
        missionsCompleted = completedMissions.size,
    )
}

data class EngineResult(
    val state: ProgressState,
    val newEntries: List<PointEntry>,
    val newBadges: List<Badge>,
)

object ProgressEngine {
    fun apply(state: ProgressState, event: ActivityEvent, nowMillis: Long): EngineResult {
        var s = state
        s = when (event) {
            is ActivityEvent.Scan -> s.copy(scanCount = s.scanCount + 1)
            is ActivityEvent.PartViewed -> s.copy(viewedParts = s.viewedParts + "${event.deviceId}:${event.partId}")
            is ActivityEvent.ProcedureCompleted -> s.copy(completedProcedures = s.completedProcedures + "${event.deviceId}:${event.procedureId}")
            is ActivityEvent.QuizCompleted -> {
                val a = event.attempt
                if (a.total > 0 && a.correct >= a.total) s.copy(hasPerfectQuiz = true) else s
            }
            is ActivityEvent.FaultDiagnosed -> if (event.correct) s.copy(fixedFaults = s.fixedFaults + event.faultKey) else s
            is ActivityEvent.MissionCompleted -> s.copy(completedMissions = s.completedMissions + event.missionId)
            is ActivityEvent.QuizAnswered -> s
        }
        val entries = PointsRules.award(event, s.ledger, nowMillis)
        s = s.copy(ledger = s.ledger + entries)
        val badges = BadgeRules.newlyUnlocked(s.badgeProgress(), s.unlockedBadges.map { it.id }.toSet())
        s = s.copy(unlockedBadges = s.unlockedBadges + badges.map { UnlockedBadge(it.id, nowMillis) })
        return EngineResult(s, entries, badges)
    }

    /** Old builds stored only a viewed-parts set (+2 each); rebuild an equivalent ledger. */
    fun legacyLedger(viewedParts: Set<String>): List<PointEntry> =
        viewedParts.sorted().map { PointEntry(PointReason.PART_VIEW, PointReason.PART_VIEW.points, it, 0L) }

    /**
     * One-time upgrade of [state] read from old/corrupt storage: derives [ProgressState.hasPerfectQuiz] from
     * stored quiz [attempts] and silently marks every already-earned badge as unlocked (no toasts).
     * Idempotent: running it again changes nothing.
     */
    fun migrate(state: ProgressState, attempts: List<QuizAttempt>, nowMillis: Long): ProgressState {
        val perfect = state.hasPerfectQuiz || attempts.any { it.total > 0 && it.correct >= it.total }
        val s = state.copy(hasPerfectQuiz = perfect)
        val have = s.unlockedBadges.map { it.id }.toSet()
        val missing = BadgeRules.newlyUnlocked(s.badgeProgress(), have)
        return s.copy(unlockedBadges = s.unlockedBadges + missing.map { UnlockedBadge(it.id, nowMillis) })
    }
}

/** A saved troubleshooting attempt. */
@Serializable
data class FaultAttempt(
    val deviceId: String,
    val faultId: String,
    val selectedOptionId: String = "",
    val correct: Boolean = false,
    val inspectedPartIds: List<String> = emptyList(),
    val timestampMillis: Long = 0L,
)
