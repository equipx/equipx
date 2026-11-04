package com.equipx.app.domain

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MissionRulesTest {
    private val mission = Mission(
        id = "m", title = "M", deviceId = "dmm",
        tasks = listOf(
            MissionTask("t1", "scan", "Scan", "dmm"),
            MissionTask("t2", "identify_part", "Find", "dmm", partId = "com_jack"),
            MissionTask("t3", "complete_procedure", "Proc", "dmm", procedureId = "p"),
            MissionTask("t4", "pass_quiz", "Quiz", "dmm", quizId = "q", minScorePct = 70),
        ),
    )
    private val scan = ActivityEvent.Scan("dmm", LocalDate.of(2026, 1, 1))
    private val part = ActivityEvent.PartViewed("dmm", "com_jack", fromExplorer = true)
    private val proc = ActivityEvent.ProcedureCompleted("dmm", "p")
    private fun quiz(correct: Int, total: Int) = ActivityEvent.QuizCompleted(QuizAttempt("dmm", "q", total = total, correct = correct))

    @Test fun fullChainInOrder() {
        var done = emptyList<String>()
        for (e in listOf(scan, part, proc, quiz(7, 10))) done = MissionRules.advance(mission, done, e, active = true)
        assertEquals(listOf("t1", "t2", "t3", "t4"), done)
        assertTrue(MissionRules.isComplete(mission, done))
    }

    @Test fun laterTasksDoNotCountBeforeEarlierOnes() {
        var done = MissionRules.advance(mission, emptyList(), proc, true)
        assertTrue(done.isEmpty())
        done = MissionRules.advance(mission, done, part, true)
        assertTrue(done.isEmpty())
        done = MissionRules.advance(mission, done, scan, true)
        assertEquals(listOf("t1"), done)
        // the earlier procedure event is not remembered
        assertEquals(listOf("t1"), MissionRules.advance(mission, done, scan, true))
    }

    @Test fun taskStatesAreDoneActiveLocked() {
        assertEquals(listOf(TaskState.ACTIVE, TaskState.LOCKED, TaskState.LOCKED, TaskState.LOCKED), MissionRules.taskStates(mission, emptyList()))
        assertEquals(listOf(TaskState.DONE, TaskState.DONE, TaskState.ACTIVE, TaskState.LOCKED), MissionRules.taskStates(mission, listOf("t1", "t2")))
        assertEquals(0.5f, MissionRules.progressFraction(mission, listOf("t1", "t2")))
    }

    @Test fun nothingAdvancesWhenMissionNotActive() {
        assertTrue(MissionRules.advance(mission, emptyList(), scan, active = false).isEmpty())
        assertEquals(listOf("t1"), MissionRules.advance(mission, listOf("t1"), part, active = false))
    }

    @Test fun partIdentifyOnlyFromExplorerOfThatPart() {
        val done = listOf("t1")
        assertEquals(done, MissionRules.advance(mission, done, ActivityEvent.PartViewed("dmm", "com_jack", fromExplorer = false), true))
        assertEquals(done, MissionRules.advance(mission, done, ActivityEvent.PartViewed("dmm", "other", fromExplorer = true), true))
        assertEquals(done, MissionRules.advance(mission, done, ActivityEvent.PartViewed("scope", "com_jack", fromExplorer = true), true))
        assertEquals(listOf("t1", "t2"), MissionRules.advance(mission, done, part, true))
    }

    @Test fun scanMustMatchDevice() {
        assertTrue(MissionRules.advance(mission, emptyList(), ActivityEvent.Scan("scope", LocalDate.of(2026, 1, 1)), true).isEmpty())
    }

    @Test fun quizBoundaryAtExactlySeventyPercent() {
        val done = listOf("t1", "t2", "t3")
        assertEquals(done, MissionRules.advance(mission, done, quiz(6, 10), true))
        assertEquals(done, MissionRules.advance(mission, done, quiz(69, 100), true))
        assertEquals(done + "t4", MissionRules.advance(mission, done, quiz(7, 10), true))
        assertEquals(done + "t4", MissionRules.advance(mission, done, quiz(70, 100), true))
        assertEquals(done, MissionRules.advance(mission, done, quiz(0, 0), true))
    }

    @Test fun quizMustMatchQuizId() {
        val done = listOf("t1", "t2", "t3")
        val other = ActivityEvent.QuizCompleted(QuizAttempt("dmm", "other", total = 10, correct = 10))
        assertEquals(done, MissionRules.advance(mission, done, other, true))
    }

    @Test fun emptyMissionIsNeverComplete() {
        assertFalse(MissionRules.isComplete(Mission("e"), emptyList()))
    }
}
