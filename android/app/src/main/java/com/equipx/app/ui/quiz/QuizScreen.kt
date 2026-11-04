package com.equipx.app.ui.quiz

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Cancel
import androidx.compose.material.icons.outlined.RemoveCircleOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.equipx.app.domain.AnswerStatus
import com.equipx.app.ui.appViewModel
import com.equipx.app.ui.components.Callout
import com.equipx.app.ui.components.EmptyState
import com.equipx.app.ui.components.EquipXTopBar
import com.equipx.app.ui.components.ErrorState
import com.equipx.app.ui.components.HairlineDivider
import com.equipx.app.ui.components.LoadingState
import com.equipx.app.ui.components.Pill
import com.equipx.app.ui.components.PrimaryButton
import com.equipx.app.ui.components.SecondaryButton
import com.equipx.app.ui.theme.EquipXColors

private val Shape6 = RoundedCornerShape(6.dp)
private val SuccessTint = Color(0xFFE7F3F1)
private val DangerTint = Color(0xFFFBE9E9)

internal fun formatClock(totalSec: Int): String {
    val t = totalSec.coerceAtLeast(0)
    return "%d:%02d".format(t / 60, t % 60)
}

@Composable
fun QuizScreen(deviceId: String, quizId: String, onBack: () -> Unit) {
    val vm = appViewModel(key = "quiz-$deviceId-$quizId") {
        QuizViewModel(deviceId, quizId, it.contentRepository, it.progressRepository)
    }
    val state by vm.state.collectAsStateWithLifecycle()
    var confirmExit by rememberSaveable { mutableStateOf(false) }
    val inProgress = state is QuizUiState.Active

    // Leaving mid-quiz would discard the attempt: ask first.
    BackHandler(enabled = inProgress) { confirmExit = true }

    Column(Modifier.fillMaxSize().background(Color.White)) {
        val s = state
        EquipXTopBar(
            title = when (s) {
                is QuizUiState.Active -> s.quiz.title
                is QuizUiState.Finished -> s.quiz.title
                is QuizUiState.Empty -> s.quiz.title
                else -> "Quiz"
            }.ifBlank { "Quiz" },
            onBack = { if (inProgress) confirmExit = true else onBack() },
            actions = {
                if (s is QuizUiState.Active && s.remainingSec != null) {
                    Pill(
                        formatClock(s.remainingSec),
                        modifier = Modifier.padding(end = 12.dp),
                        contentColor = if (s.remainingSec <= 10) EquipXColors.Danger else EquipXColors.Text,
                    )
                }
            },
        )
        when (s) {
            QuizUiState.Loading -> LoadingState()
            is QuizUiState.Error -> ErrorState(s.message, onRetry = vm::load)
            is QuizUiState.Empty -> EmptyState("This quiz has no questions yet")
            is QuizUiState.Active -> ActiveQuiz(s, onSelect = vm::select, onNext = vm::next)
            is QuizUiState.Finished -> ResultContent(s, onRetry = vm::start, onDone = onBack)
        }
    }

    if (confirmExit) {
        AlertDialog(
            onDismissRequest = { confirmExit = false },
            containerColor = Color.White,
            title = { Text("Leave this quiz?", style = MaterialTheme.typography.titleMedium) },
            text = { Text("Your progress in this attempt will be lost.", style = MaterialTheme.typography.bodyMedium) },
            confirmButton = { TextButton(onClick = { confirmExit = false; onBack() }) { Text("Leave", color = EquipXColors.Danger) } },
            dismissButton = { TextButton(onClick = { confirmExit = false }) { Text("Keep going") } },
        )
    }
}

@Composable
private fun ActiveQuiz(s: QuizUiState.Active, onSelect: (Int) -> Unit, onNext: () -> Unit) {
    val q = s.question
    val selected = s.answers[s.index]
    Column(Modifier.fillMaxSize()) {
        Column(
            Modifier.weight(1f).verticalScroll(remember(s.index) { ScrollState(0) }).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                "Question ${s.index + 1} of ${s.quiz.questions.size}",
                style = MaterialTheme.typography.labelMedium,
                color = EquipXColors.TextSecondary,
            )
            LinearProgressIndicator(
                progress = { (s.index + (if (s.revealed) 1 else 0)).toFloat() / s.quiz.questions.size },
                modifier = Modifier.fillMaxWidth(),
                color = EquipXColors.Accent,
                trackColor = EquipXColors.Border,
            )
            Text(q.prompt, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 4.dp))
            q.options.forEachIndexed { i, text ->
                OptionRow(
                    letter = ('A' + i).toString(),
                    text = text,
                    state = when {
                        !s.revealed -> OptionState.Idle
                        i == q.correctIndex -> OptionState.Correct
                        i == selected -> OptionState.Wrong
                        else -> OptionState.Dimmed
                    },
                    onClick = { onSelect(i) },
                )
            }
            if (s.revealed) {
                Callout(
                    title = if (selected == q.correctIndex) "Correct" else "Not quite",
                    text = q.explanation.ifBlank { "The correct answer is ${('A' + q.correctIndex.coerceIn(0, 25))}." },
                )
            }
        }
        if (s.revealed) {
            HairlineDivider()
            PrimaryButton(
                if (s.isLast) "See results" else "Next question",
                onClick = onNext,
                modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(12.dp),
            )
        }
    }
}

private enum class OptionState { Idle, Correct, Wrong, Dimmed }

@Composable
private fun OptionRow(letter: String, text: String, state: OptionState, onClick: () -> Unit) {
    val border = when (state) {
        OptionState.Correct -> EquipXColors.Success
        OptionState.Wrong -> EquipXColors.Danger
        else -> EquipXColors.Border
    }
    val bg = when (state) {
        OptionState.Correct -> SuccessTint
        OptionState.Wrong -> DangerTint
        else -> Color.White
    }
    Row(
        Modifier
            .fillMaxWidth()
            .clip(Shape6)
            .background(bg)
            .border(1.dp, border, Shape6)
            .clickable(enabled = state == OptionState.Idle, onClick = onClick)
            .padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(letter, style = MaterialTheme.typography.labelLarge, color = EquipXColors.TextSecondary)
        Text(
            text,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyMedium,
            color = if (state == OptionState.Dimmed) EquipXColors.TextSecondary else EquipXColors.Text,
        )
    }
}

@Composable
private fun ResultContent(s: QuizUiState.Finished, onRetry: () -> Unit, onDone: () -> Unit) {
    val r = s.result
    Column(Modifier.fillMaxSize()) {
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (s.timedOut) Callout(title = "Time is up", text = "The quiz was submitted automatically. Unanswered questions count as incorrect.")
            Text("${r.correct} / ${r.total}", style = MaterialTheme.typography.headlineLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Pill("Accuracy ${r.accuracyPercent}%")
                Pill("Time ${formatClock(s.durationSec)}")
                if (r.unanswered > 0) Pill("${r.unanswered} unanswered")
            }
            Text("Review", style = MaterialTheme.typography.titleSmall, color = EquipXColors.TextSecondary, modifier = Modifier.padding(top = 12.dp))
            HairlineDivider()
            s.quiz.questions.forEachIndexed { i, q ->
                val res = r.results[i]
                Column(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Top) {
                        when (res.status) {
                            AnswerStatus.CORRECT -> Icon(Icons.Outlined.CheckCircle, "Correct", tint = EquipXColors.Success, modifier = Modifier.size(20.dp))
                            AnswerStatus.WRONG -> Icon(Icons.Outlined.Cancel, "Incorrect", tint = EquipXColors.Danger, modifier = Modifier.size(20.dp))
                            AnswerStatus.UNANSWERED -> Icon(Icons.Outlined.RemoveCircleOutline, "Unanswered", tint = EquipXColors.TextSecondary, modifier = Modifier.size(20.dp))
                        }
                        Text("${i + 1}. ${q.prompt}", style = MaterialTheme.typography.titleSmall)
                    }
                    Column(Modifier.padding(start = 28.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        val yours = q.options.getOrNull(res.selectedIndex)
                        Text(
                            "Your answer: ${yours ?: "No answer"}",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (res.isCorrect) EquipXColors.Success else EquipXColors.Danger,
                        )
                        if (!res.isCorrect) {
                            Text(
                                "Correct answer: ${q.options.getOrNull(q.correctIndex).orEmpty()}",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Medium,
                            )
                        }
                        if (q.explanation.isNotBlank()) {
                            Text(q.explanation, style = MaterialTheme.typography.bodySmall, color = EquipXColors.TextSecondary)
                        }
                    }
                }
                HairlineDivider()
            }
        }
        HairlineDivider()
        Row(
            Modifier.fillMaxWidth().navigationBarsPadding().padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SecondaryButton("Try again", onClick = onRetry, modifier = Modifier.weight(1f))
            PrimaryButton("Done", onClick = onDone, modifier = Modifier.weight(1f))
        }
    }
}
