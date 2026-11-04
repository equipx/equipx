package com.equipx.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class QuizScorerTest {

    private fun quiz(n: Int) = Quiz(
        id = "q",
        title = "Q",
        questions = (0 until n).map { QuizQuestion("q$it", "p$it", listOf("a", "b", "c", "d"), correctIndex = it % 4) },
    )

    @Test fun zeroQuestions() {
        val r = QuizScorer.score(quiz(0), emptyList())
        assertEquals(0, r.total)
        assertEquals(0, r.correct)
        assertEquals(0, r.accuracyPercent)
        assertEquals(0, QuizScorer.accuracyPercent(0, 0))
    }

    @Test fun allCorrect() {
        val q = quiz(4)
        val r = QuizScorer.score(q, q.questions.map { it.correctIndex })
        assertEquals(4, r.correct)
        assertEquals(100, r.accuracyPercent)
        assertEquals(0, r.wrong)
        assertEquals(0, r.unanswered)
    }

    @Test fun allWrong() {
        val q = quiz(4)
        val r = QuizScorer.score(q, q.questions.map { (it.correctIndex + 1) % 4 })
        assertEquals(0, r.correct)
        assertEquals(4, r.wrong)
        assertEquals(0, r.accuracyPercent)
        assertEquals(AnswerStatus.WRONG, r.results[0].status)
    }

    @Test fun timeoutLeavesTrailingUnanswered() {
        val q = quiz(5)
        val r = QuizScorer.score(q, listOf(0, 1)) // answered 2 correctly, then time ran out
        assertEquals(2, r.correct)
        assertEquals(3, r.unanswered)
        assertEquals(40, r.accuracyPercent)
        assertEquals(QuizScorer.UNANSWERED, r.results[4].selectedIndex)
    }

    @Test fun nullAndOutOfRangeAnswersAreUnanswered() {
        val q = quiz(3)
        val r = QuizScorer.score(q, listOf(null, 9, -1, 3, 3))
        assertEquals(3, r.unanswered)
        assertEquals(0, r.correct)
    }

    @Test fun roundingHalfUp() {
        assertEquals(33, QuizScorer.accuracyPercent(1, 3))
        assertEquals(67, QuizScorer.accuracyPercent(2, 3))
        assertEquals(13, QuizScorer.accuracyPercent(1, 8)) // 12.5 -> 13
        assertEquals(88, QuizScorer.accuracyPercent(7, 8)) // 87.5 -> 88
        assertEquals(100, QuizScorer.accuracyPercent(10, 10))
        assertEquals(100, QuizScorer.accuracyPercent(12, 10)) // clamped
        assertEquals(0, QuizScorer.accuracyPercent(-1, 10))
    }

    private fun attempt(correct: Int, total: Int, at: Long = 0) =
        QuizAttempt("d", "q", correct = correct, total = total, completedAtMillis = at)

    @Test fun aggregateIsQuestionWeighted() {
        val s = QuizScorer.aggregate(listOf(attempt(1, 2), attempt(9, 10)))
        assertEquals(2, s.attempts)
        assertEquals(12, s.totalQuestions)
        assertEquals(10, s.totalCorrect)
        assertEquals(83, s.accuracyPercent) // 10/12 = 83.3
    }

    @Test fun aggregateEmptyAndZeroTotalAttempts() {
        assertEquals(0, QuizScorer.aggregate(emptyList()).accuracyPercent)
        val s = QuizScorer.aggregate(listOf(attempt(0, 0)))
        assertEquals(0, s.attempts)
        assertEquals(0, s.accuracyPercent)
    }

    @Test fun bestPicksHighestThenLatest() {
        assertNull(QuizScorer.best(emptyList()))
        val a = attempt(5, 10, at = 1)
        val b = attempt(8, 10, at = 2)
        val c = attempt(8, 10, at = 3)
        assertEquals(c, QuizScorer.best(listOf(a, b, c)))
    }

    @Test fun attemptAccuracyProperty() {
        assertEquals(67, attempt(2, 3).accuracyPercent)
    }
}
