package com.equipx.app.ui.quiz

import android.os.SystemClock
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.equipx.app.data.ContentRepository
import com.equipx.app.data.ProgressRepository
import com.equipx.app.domain.Quiz
import com.equipx.app.domain.QuizAttempt
import com.equipx.app.domain.QuizResult
import com.equipx.app.domain.QuizScorer
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface QuizUiState {
    data object Loading : QuizUiState
    data class Error(val message: String) : QuizUiState
    data class Empty(val quiz: Quiz) : QuizUiState
    data class Active(
        val quiz: Quiz,
        val index: Int,
        /** Selected option per question; null = not answered yet. */
        val answers: List<Int?>,
        /** Feedback for the current question is showing (answer locked). */
        val revealed: Boolean,
        val remainingSec: Int?,
    ) : QuizUiState {
        val question get() = quiz.questions[index]
        val isLast: Boolean get() = index >= quiz.questions.lastIndex
    }
    data class Finished(
        val quiz: Quiz,
        val result: QuizResult,
        val answers: List<Int?>,
        val durationSec: Int,
        val timedOut: Boolean,
    ) : QuizUiState
}

class QuizViewModel(
    private val deviceId: String,
    private val quizId: String,
    private val content: ContentRepository,
    private val progress: ProgressRepository,
) : ViewModel() {
    private val _state = MutableStateFlow<QuizUiState>(QuizUiState.Loading)
    val state: StateFlow<QuizUiState> = _state.asStateFlow()

    private var quiz: Quiz? = null
    private var startedAt = 0L
    private var timerJob: Job? = null

    init { load() }

    fun load() {
        _state.value = QuizUiState.Loading
        viewModelScope.launch {
            content.getDevice(deviceId)
                .onSuccess { d ->
                    val q = d.quizzes.firstOrNull { it.id == quizId }
                    val valid = q?.copy(questions = q.questions.filter { it.isValid })
                    if (valid == null) _state.value = QuizUiState.Error("This quiz could not be found.")
                    else { quiz = valid; start() }
                }
                .onFailure { _state.value = QuizUiState.Error("Could not load this quiz.") }
        }
    }

    /** (Re)starts the quiz from the first question. */
    fun start() {
        val q = quiz ?: return
        timerJob?.cancel()
        if (q.questions.isEmpty()) { _state.value = QuizUiState.Empty(q); return }
        startedAt = SystemClock.elapsedRealtime()
        _state.value = QuizUiState.Active(q, 0, List(q.questions.size) { null }, false, q.timeLimitSec?.takeIf { it > 0 })
        q.timeLimitSec?.takeIf { it > 0 }?.let { limit ->
            timerJob = viewModelScope.launch {
                while (true) {
                    delay(250)
                    val remaining = limit - elapsedSec()
                    if (remaining <= 0) { finish(timedOut = true); break }
                    val s = _state.value as? QuizUiState.Active ?: break
                    if (s.remainingSec != remaining) _state.value = s.copy(remainingSec = remaining)
                }
            }
        }
    }

    private fun elapsedSec(): Int = ((SystemClock.elapsedRealtime() - startedAt) / 1000).toInt()

    fun select(option: Int) {
        val s = _state.value as? QuizUiState.Active ?: return
        if (s.revealed) return
        val answers = s.answers.toMutableList().also { it[s.index] = option }
        _state.value = s.copy(answers = answers, revealed = true)
        // Last answer is in: the clock must not be able to flag this attempt as timed out.
        if (s.isLast) timerJob?.cancel()
    }

    fun next() {
        val s = _state.value as? QuizUiState.Active ?: return
        if (!s.revealed) return
        if (s.isLast) finish(timedOut = false) else _state.value = s.copy(index = s.index + 1, revealed = false)
    }

    private fun finish(timedOut: Boolean) {
        val s = _state.value as? QuizUiState.Active ?: return
        timerJob?.cancel()
        val timedOut = timedOut && s.answers.any { it == null }
        val limit = s.quiz.timeLimitSec?.takeIf { it > 0 }
        val duration = elapsedSec().let { if (limit != null) it.coerceAtMost(limit) else it }
        val result = QuizScorer.score(s.quiz, s.answers)
        _state.value = QuizUiState.Finished(s.quiz, result, s.answers, duration, timedOut)
        viewModelScope.launch {
            progress.saveQuizAttempt(
                QuizAttempt(
                    deviceId = deviceId,
                    quizId = s.quiz.id,
                    quizTitle = s.quiz.title,
                    completedAtMillis = System.currentTimeMillis(),
                    total = result.total,
                    correct = result.correct,
                    answers = result.results.map { it.selectedIndex },
                    durationSec = duration,
                    timedOut = timedOut,
                ),
            )
        }
    }

    override fun onCleared() { timerJob?.cancel() }
}
