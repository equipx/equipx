package com.equipx.app.ui.quiz

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Cancel
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.EmojiEvents
import androidx.compose.material.icons.outlined.RemoveCircleOutline
import androidx.compose.material.icons.outlined.Quiz
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import com.equipx.app.ui.components.EquipXCard
import com.equipx.app.ui.components.EquipXProgressBar
import com.equipx.app.ui.components.EquipXTopBar
import com.equipx.app.ui.components.ErrorState
import com.equipx.app.ui.components.LoadingState
import com.equipx.app.ui.components.Pill
import com.equipx.app.ui.components.PrimaryButton
import com.equipx.app.ui.components.ProgressRing
import com.equipx.app.ui.components.SecondaryButton
import com.equipx.app.ui.theme.EquipXColors

private val OptionShape = RoundedCornerShape(14.dp)

internal fun formatClock(totalSec: Int): String {
    val t = totalSec.coerceAtLeast(0)
    return "%d:%02d".format(t / 60, t % 60)
}

@Composable
fun QuizScreen(deviceId: String, quizId: String, onBack: () -> Unit) {
    val vm = appViewModel(key = "quiz-$deviceId-$quizId") {
        QuizViewModel(deviceId, quizId, it.contentRepository, it.progressRepository, it.activityTracker)
    }
    val state by vm.state.collectAsStateWithLifecycle()
    var confirmExit by rememberSaveable { mutableStateOf(false) }
    val inProgress = state is QuizUiState.Active

    // Leaving mid-quiz would discard the attempt: ask first.
    BackHandler(enabled = inProgress) { confirmExit = true }

    QuizContent(
        state = state,
        onBack = { if (inProgress) confirmExit = true else onBack() },
        onRetryLoad = vm::load,
        onSelect = vm::select,
        onNext = vm::next,
        onRestart = vm::start,
        onDone = onBack,
    )

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

/** Stateless quiz UI (question, feedback and result), previewable with fake [QuizUiState]. */
@Composable
fun QuizContent(
    state: QuizUiState,
    onBack: () -> Unit,
    onRetryLoad: () -> Unit,
    onSelect: (Int) -> Unit,
    onNext: () -> Unit,
    onRestart: () -> Unit,
    onDone: () -> Unit,
) {
    Column(Modifier.fillMaxSize().background(EquipXColors.Canvas)) {
        val s = state
        EquipXTopBar(
            title = when (s) {
                is QuizUiState.Active -> s.quiz.title
                is QuizUiState.Finished -> s.quiz.title
                is QuizUiState.Empty -> s.quiz.title
                else -> "Quiz"
            }.ifBlank { "Quiz" },
            onBack = onBack,
            actions = {
                if (s is QuizUiState.Active && s.remainingSec != null) {
                    Pill(
                        formatClock(s.remainingSec),
                        modifier = Modifier.padding(end = 12.dp),
                        background = if (s.remainingSec <= 10) EquipXColors.DangerTint else Color.White,
                        contentColor = if (s.remainingSec <= 10) EquipXColors.Danger else EquipXColors.Text,
                    )
                }
            },
        )
        when (s) {
            QuizUiState.Loading -> LoadingState()
            is QuizUiState.Error -> ErrorState(s.message, onRetry = onRetryLoad)
            is QuizUiState.Empty -> EmptyState("This quiz has no questions yet", icon = Icons.Outlined.Quiz)
            is QuizUiState.Active -> ActiveQuiz(s, onSelect = onSelect, onNext = onNext)
            is QuizUiState.Finished -> ResultContent(s, onRetry = onRestart, onDone = onDone)
        }
    }
}

@Composable
private fun ActiveQuiz(s: QuizUiState.Active, onSelect: (Int) -> Unit, onNext: () -> Unit) {
    val q = s.question
    val selected = s.answers[s.index]
    val total = s.quiz.questions.size
    Column(Modifier.fillMaxSize()) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Question ${s.index + 1} of $total", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                Text("${s.answers.count { it != null }} answered", style = MaterialTheme.typography.labelMedium, color = EquipXColors.TextSecondary)
            }
            EquipXProgressBar((s.index + (if (s.revealed) 1 else 0)).toFloat() / total)
        }
        Column(
            Modifier.weight(1f).verticalScroll(remember(s.index) { ScrollState(0) }).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            EquipXCard(Modifier.fillMaxWidth()) {
                Text(q.prompt, style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(18.dp))
            }
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
            AnimatedVisibility(visible = s.revealed, enter = fadeIn(tween(250)) + slideInVertically(tween(250)) { it / 4 }) {
                Callout(
                    title = if (selected == q.correctIndex) "Correct" else "Not quite",
                    text = q.explanation.ifBlank { "The correct answer is ${('A' + q.correctIndex.coerceIn(0, 25))}." },
                )
            }
        }
        AnimatedVisibility(visible = s.revealed, enter = fadeIn() + slideInVertically { it }) {
            Surface(shadowElevation = 8.dp, color = Color.White) {
                PrimaryButton(
                    if (s.isLast) "See results" else "Next question",
                    onClick = onNext,
                    modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(12.dp),
                )
            }
        }
    }
}

private enum class OptionState { Idle, Correct, Wrong, Dimmed }

@Composable
private fun OptionRow(letter: String, text: String, state: OptionState, onClick: () -> Unit) {
    val border by animateColorAsState(
        when (state) {
            OptionState.Correct -> EquipXColors.Success
            OptionState.Wrong -> EquipXColors.Danger
            else -> EquipXColors.Border
        },
        tween(300), label = "option-border",
    )
    val bg by animateColorAsState(
        when (state) {
            OptionState.Correct -> EquipXColors.SuccessTint
            OptionState.Wrong -> EquipXColors.DangerTint
            else -> Color.White
        },
        tween(300), label = "option-bg",
    )
    val badgeColor = when (state) {
        OptionState.Correct -> EquipXColors.Success
        OptionState.Wrong -> EquipXColors.Danger
        else -> EquipXColors.AccentTint
    }
    Surface(
        onClick = onClick,
        enabled = state == OptionState.Idle,
        shape = OptionShape,
        color = bg,
        shadowElevation = if (state == OptionState.Idle) 2.dp else 0.dp,
        border = androidx.compose.foundation.BorderStroke(if (state == OptionState.Correct || state == OptionState.Wrong) 2.dp else 1.dp, border),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            Modifier.padding(horizontal = 14.dp, vertical = 12.dp).heightIn(min = 48.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(36.dp).clip(CircleShape).background(badgeColor), contentAlignment = Alignment.Center) {
                when (state) {
                    OptionState.Correct -> Icon(Icons.Outlined.CheckCircle, "Correct", tint = Color.White)
                    OptionState.Wrong -> Icon(Icons.Outlined.Cancel, "Incorrect", tint = Color.White)
                    else -> Text(letter, style = MaterialTheme.typography.labelLarge, color = EquipXColors.Accent)
                }
            }
            Text(
                text,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyLarge,
                color = if (state == OptionState.Dimmed) EquipXColors.TextSecondary else EquipXColors.Text,
            )
        }
    }
}

@Composable
private fun ResultContent(s: QuizUiState.Finished, onRetry: () -> Unit, onDone: () -> Unit) {
    val r = s.result
    val pct = r.accuracyPercent
    val tone = when {
        pct >= 80 -> EquipXColors.Success
        pct >= 50 -> EquipXColors.Accent
        else -> EquipXColors.Danger
    }
    val headline = when {
        pct == 100 -> "Perfect score!"
        pct >= 80 -> "Great job!"
        pct >= 50 -> "Good effort"
        else -> "Keep practising"
    }
    Column(Modifier.fillMaxSize()) {
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (s.timedOut) Callout(title = "Time is up", text = "The quiz was submitted automatically. Unanswered questions count as incorrect.")
            EquipXCard(Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp)) {
                Column(
                    Modifier.fillMaxWidth().padding(vertical = 24.dp, horizontal = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    if (pct >= 80) Icon(Icons.Outlined.EmojiEvents, contentDescription = null, tint = EquipXColors.Gold, modifier = Modifier.size(36.dp))
                    ProgressRing(pct / 100f, size = 160.dp, stroke = 14.dp, color = tone, trackColor = EquipXColors.Border) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("${r.correct}/${r.total}", style = MaterialTheme.typography.headlineLarge)
                            Text("$pct%", style = MaterialTheme.typography.titleMedium, color = tone)
                        }
                    }
                    Text(headline, style = MaterialTheme.typography.headlineSmall)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Pill("Accuracy $pct%", background = EquipXColors.AccentTint, contentColor = EquipXColors.Accent)
                        Pill("Time ${formatClock(s.durationSec)}")
                        if (r.unanswered > 0) Pill("${r.unanswered} unanswered", background = EquipXColors.DangerTint, contentColor = EquipXColors.Danger)
                    }
                }
            }
            Text("Review", style = MaterialTheme.typography.titleLarge, modifier = Modifier.fillMaxWidth().padding(top = 12.dp))
            s.quiz.questions.forEachIndexed { i, q ->
                val res = r.results[i]
                EquipXCard(Modifier.fillMaxWidth(), elevation = 1.dp) {
                    Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Top) {
                            when (res.status) {
                                AnswerStatus.CORRECT -> Icon(Icons.Outlined.CheckCircle, "Correct", tint = EquipXColors.Success, modifier = Modifier.size(22.dp))
                                AnswerStatus.WRONG -> Icon(Icons.Outlined.Cancel, "Incorrect", tint = EquipXColors.Danger, modifier = Modifier.size(22.dp))
                                AnswerStatus.UNANSWERED -> Icon(Icons.Outlined.RemoveCircleOutline, "Unanswered", tint = EquipXColors.TextSecondary, modifier = Modifier.size(22.dp))
                            }
                            Text("${i + 1}. ${q.prompt}", style = MaterialTheme.typography.titleSmall)
                        }
                        Column(Modifier.padding(start = 32.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
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
                }
            }
        }
        Surface(shadowElevation = 8.dp, color = Color.White) {
            Row(
                Modifier.fillMaxWidth().navigationBarsPadding().padding(12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                SecondaryButton("Try again", onClick = onRetry, modifier = Modifier.weight(1f))
                PrimaryButton("Done", onClick = onDone, modifier = Modifier.weight(1f))
            }
        }
    }
}
