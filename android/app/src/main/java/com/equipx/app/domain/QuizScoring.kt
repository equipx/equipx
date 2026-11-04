package com.equipx.app.domain

import kotlinx.serialization.Serializable

/** A finished quiz attempt, stored locally. [answers] holds one selected option index per question, or [QuizScorer.UNANSWERED]. */
@Serializable
data class QuizAttempt(
    val deviceId: String,
    val quizId: String,
    val quizTitle: String = "",
    val completedAtMillis: Long = 0L,
    val total: Int = 0,
    val correct: Int = 0,
    val answers: List<Int> = emptyList(),
    val durationSec: Int = 0,
    val timedOut: Boolean = false,
) {
    val accuracyPercent: Int get() = QuizScorer.accuracyPercent(correct, total)
}

enum class AnswerStatus { CORRECT, WRONG, UNANSWERED }

data class QuestionResult(
    val questionId: String,
    val selectedIndex: Int,
    val correctIndex: Int,
    val status: AnswerStatus,
) {
    val isCorrect: Boolean get() = status == AnswerStatus.CORRECT
}

data class QuizResult(
    val total: Int,
    val correct: Int,
    val wrong: Int,
    val unanswered: Int,
    val accuracyPercent: Int,
    val results: List<QuestionResult>,
)

/** Accuracy across several attempts. */
data class AccuracySummary(
    val attempts: Int,
    val totalQuestions: Int,
    val totalCorrect: Int,
    val accuracyPercent: Int,
)

/** Pure scoring logic for quizzes. */
object QuizScorer {
    const val UNANSWERED = -1

    /**
     * Scores [answers] (selected option index per question, null or negative = unanswered) against [quiz].
     * Missing trailing answers (for example after a timeout) count as unanswered; extra answers are ignored;
     * an index outside the option range counts as unanswered. Unanswered questions are never correct.
     */
    fun score(quiz: Quiz, answers: List<Int?>): QuizResult {
        val results = quiz.questions.mapIndexed { i, q ->
            val raw = answers.getOrNull(i)
            val sel = if (raw != null && raw in q.options.indices) raw else UNANSWERED
            val status = when {
                sel == UNANSWERED -> AnswerStatus.UNANSWERED
                sel == q.correctIndex -> AnswerStatus.CORRECT
                else -> AnswerStatus.WRONG
            }
            QuestionResult(q.id, sel, q.correctIndex, status)
        }
        val correct = results.count { it.status == AnswerStatus.CORRECT }
        val wrong = results.count { it.status == AnswerStatus.WRONG }
        val unanswered = results.count { it.status == AnswerStatus.UNANSWERED }
        return QuizResult(results.size, correct, wrong, unanswered, accuracyPercent(correct, results.size), results)
    }

    /** Whole-number percentage, rounded half up; 0 when [total] is not positive. Result is clamped to 0..100. */
    fun accuracyPercent(correct: Int, total: Int): Int {
        if (total <= 0 || correct <= 0) return 0
        val c = correct.coerceAtMost(total)
        return ((c.toLong() * 200 + total) / (2L * total)).toInt()
    }

    /** Question-weighted accuracy: total correct over total questions across all [attempts]. */
    fun aggregate(attempts: List<QuizAttempt>): AccuracySummary {
        val valid = attempts.filter { it.total > 0 }
        val total = valid.sumOf { it.total }
        val correct = valid.sumOf { it.correct.coerceIn(0, it.total) }
        return AccuracySummary(valid.size, total, correct, accuracyPercent(correct, total))
    }

    /** Best (highest accuracy, then most recent) attempt, or null. */
    fun best(attempts: List<QuizAttempt>): QuizAttempt? =
        attempts.filter { it.total > 0 }
            .maxWithOrNull(compareBy<QuizAttempt>({ it.accuracyPercent }, { it.completedAtMillis }))
}
