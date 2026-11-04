package com.equipx.app.domain

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PointsRulesTest {
    private val d1 = LocalDate.of(2026, 11, 5)
    private val d2 = LocalDate.of(2026, 11, 6)

    private fun award(e: ActivityEvent, h: List<PointEntry> = emptyList()) = PointsRules.award(e, h, 1L)

    @Test fun scanAwardsTen() {
        val r = award(ActivityEvent.Scan("dmm", d1))
        assertEquals(1, r.size); assertEquals(10, r[0].amount); assertEquals(PointReason.SCAN, r[0].reason)
    }

    @Test fun scanOncePerDevicePerDay() {
        val h = award(ActivityEvent.Scan("dmm", d1))
        assertTrue(award(ActivityEvent.Scan("dmm", d1), h).isEmpty())
        assertEquals(1, award(ActivityEvent.Scan("scope", d1), h).size)
    }

    @Test fun scanDayIsUtcMidnightBoundary() {
        val beforeMidnight = java.time.Instant.parse("2026-11-05T23:59:59Z").toEpochMilli()
        val atMidnight = java.time.Instant.parse("2026-11-06T00:00:00Z").toEpochMilli()
        assertEquals(d1, PointsRules.scanDay(beforeMidnight))
        assertEquals(d2, PointsRules.scanDay(atMidnight))
    }

    @Test fun scanAwardedAgainAcrossUtcMidnight() {
        val h = award(ActivityEvent.Scan("dmm", d1))
        assertEquals(10, award(ActivityEvent.Scan("dmm", d2), h).single().amount)
    }

    @Test fun firstPartViewOnly() {
        val h = award(ActivityEvent.PartViewed("dmm", "lcd"))
        assertEquals(2, h.single().amount)
        assertTrue(award(ActivityEvent.PartViewed("dmm", "lcd", fromExplorer = true), h).isEmpty())
        assertEquals(1, award(ActivityEvent.PartViewed("dmm", "com"), h).size)
        assertEquals(1, award(ActivityEvent.PartViewed("scope", "lcd"), h).size)
    }

    @Test fun quizCorrectOncePerQuestion() {
        assertTrue(award(ActivityEvent.QuizAnswered("dmm:q:1", false)).isEmpty())
        val h = award(ActivityEvent.QuizAnswered("dmm:q:1", true))
        assertEquals(5, h.single().amount)
        assertTrue(award(ActivityEvent.QuizAnswered("dmm:q:1", true), h).isEmpty())
        assertEquals(1, award(ActivityEvent.QuizAnswered("dmm:q:2", true), h).size)
    }

    @Test fun wrongThenCorrectStillAwardsOnce() {
        val h = award(ActivityEvent.QuizAnswered("k", false))
        assertEquals(5, award(ActivityEvent.QuizAnswered("k", true), h).single().amount)
    }

    @Test fun faultFixedFirstCorrectOnly() {
        assertTrue(award(ActivityEvent.FaultDiagnosed("dmm:f", false)).isEmpty())
        val h = award(ActivityEvent.FaultDiagnosed("dmm:f", true))
        assertEquals(20, h.single().amount)
        assertTrue(award(ActivityEvent.FaultDiagnosed("dmm:f", true), h).isEmpty())
    }

    @Test fun missionOnce() {
        val h = award(ActivityEvent.MissionCompleted("m1"))
        assertEquals(50, h.single().amount)
        assertTrue(award(ActivityEvent.MissionCompleted("m1"), h).isEmpty())
        assertEquals(1, award(ActivityEvent.MissionCompleted("m2"), h).size)
    }

    @Test fun procedureAndQuizCompletionAwardNothing() {
        assertTrue(award(ActivityEvent.ProcedureCompleted("dmm", "p")).isEmpty())
        val attempt = QuizAttempt("dmm", "q", total = 1, correct = 1)
        assertTrue(award(ActivityEvent.QuizCompleted(attempt)).isEmpty())
    }

    @Test fun sameKeyDifferentReasonIsNotDeduped() {
        val h = listOf(PointEntry(PointReason.PART_VIEW, 2, "x", 0))
        assertEquals(1, award(ActivityEvent.FaultDiagnosed("x", true), h).size)
    }
}

class BadgeRulesTest {
    private fun unlocked(p: BadgeProgress) = BadgeRules.earned(p)

    @Test fun nothingAtZero() = assertTrue(unlocked(BadgeProgress()).isEmpty())

    @Test fun firstScanAtOne() {
        assertTrue(Badge.FIRST_SCAN in unlocked(BadgeProgress(scans = 1)))
    }

    @Test fun partSpotterThresholdIsTen() {
        assertTrue(Badge.PART_SPOTTER !in unlocked(BadgeProgress(distinctPartsViewed = 9)))
        assertTrue(Badge.PART_SPOTTER in unlocked(BadgeProgress(distinctPartsViewed = 10)))
    }

    @Test fun safetyFirstAtOneProcedure() {
        assertTrue(Badge.SAFETY_FIRST !in unlocked(BadgeProgress()))
        assertTrue(Badge.SAFETY_FIRST in unlocked(BadgeProgress(proceduresCompleted = 1)))
    }

    @Test fun quizAceNeedsPerfect() {
        assertTrue(Badge.QUIZ_ACE !in unlocked(BadgeProgress()))
        assertTrue(Badge.QUIZ_ACE in unlocked(BadgeProgress(hasPerfectQuiz = true)))
    }

    @Test fun faultFinderThresholdIsThree() {
        assertTrue(Badge.FAULT_FINDER !in unlocked(BadgeProgress(distinctFaultsFixed = 2)))
        assertTrue(Badge.FAULT_FINDER in unlocked(BadgeProgress(distinctFaultsFixed = 3)))
    }

    @Test fun missionCompleteAtOne() {
        assertTrue(Badge.MISSION_COMPLETE in unlocked(BadgeProgress(missionsCompleted = 1)))
    }

    @Test fun newlyUnlockedSkipsAlreadyUnlocked() {
        val p = BadgeProgress(scans = 1, proceduresCompleted = 1)
        assertEquals(listOf(Badge.SAFETY_FIRST), BadgeRules.newlyUnlocked(p, setOf("first_scan")))
        assertTrue(BadgeRules.newlyUnlocked(p, setOf("first_scan", "safety_first")).isEmpty())
    }
}

class ProgressEngineTest {
    private val day = LocalDate.of(2026, 1, 1)

    @Test fun scanUnlocksFirstScanBadgeOnce() {
        val r1 = ProgressEngine.apply(ProgressState(), ActivityEvent.Scan("dmm", day), 5L)
        assertEquals(listOf(Badge.FIRST_SCAN), r1.newBadges)
        assertEquals(10, r1.state.points)
        val r2 = ProgressEngine.apply(r1.state, ActivityEvent.Scan("dmm", day), 6L)
        assertTrue(r2.newBadges.isEmpty()); assertTrue(r2.newEntries.isEmpty())
        assertEquals(10, r2.state.points)
    }

    @Test fun tenthDistinctPartUnlocksPartSpotter() {
        var s = ProgressState()
        var last: EngineResult? = null
        for (i in 1..10) { last = ProgressEngine.apply(s, ActivityEvent.PartViewed("dmm", "p$i"), 1L); s = last.state }
        assertEquals(listOf(Badge.PART_SPOTTER), last!!.newBadges)
        assertEquals(20, s.points)
    }

    @Test fun perfectQuizAndMissionAndFaults() {
        var s = ProgressState()
        s = ProgressEngine.apply(s, ActivityEvent.QuizCompleted(QuizAttempt("d", "q", total = 5, correct = 4)), 1).state
        assertTrue(!s.hasPerfectQuiz)
        val r = ProgressEngine.apply(s, ActivityEvent.QuizCompleted(QuizAttempt("d", "q", total = 5, correct = 5)), 1)
        assertEquals(listOf(Badge.QUIZ_ACE), r.newBadges)
        s = r.state
        for (i in 1..3) s = ProgressEngine.apply(s, ActivityEvent.FaultDiagnosed("d:f$i", true), 1).state
        assertTrue(s.unlockedBadges.any { it.id == "fault_finder" })
        assertEquals(60, s.points)
        s = ProgressEngine.apply(s, ActivityEvent.MissionCompleted("m"), 1).state
        assertEquals(110, s.points)
        assertTrue(s.unlockedBadges.any { it.id == "mission_complete" })
    }

    @Test fun legacyViewedPartsBecomeLedger() {
        val ledger = ProgressEngine.legacyLedger(setOf("dmm:a", "dmm:b"))
        assertEquals(4, PointsRules.total(ledger))
        // and are not re-awarded
        val s = ProgressState(ledger = ledger, viewedParts = setOf("dmm:a", "dmm:b"))
        assertTrue(ProgressEngine.apply(s, ActivityEvent.PartViewed("dmm", "a"), 1).newEntries.isEmpty())
    }
}
