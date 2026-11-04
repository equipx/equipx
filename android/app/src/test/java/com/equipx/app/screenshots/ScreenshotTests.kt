package com.equipx.app.screenshots

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import com.equipx.app.data.ContentParser
import com.equipx.app.domain.Device
import com.equipx.app.domain.DeviceSummary
import com.equipx.app.domain.Mission
import com.equipx.app.domain.MissionState
import com.equipx.app.domain.PointEntry
import com.equipx.app.domain.PointReason
import com.equipx.app.domain.ProgressState
import com.equipx.app.domain.QuizAttempt
import com.equipx.app.domain.QuizScorer
import com.equipx.app.domain.Role
import com.equipx.app.domain.UnlockedBadge
import com.equipx.app.domain.User
import com.equipx.app.ml.Detection
import com.equipx.app.ml.NormalizedBox
import com.equipx.app.ui.device.DeviceDetailContent
import com.equipx.app.ui.device.DeviceDetailState
import com.equipx.app.ui.explorer.ExplorerOverlay
import com.equipx.app.ui.home.ContinueItem
import com.equipx.app.ui.home.HomeContent
import com.equipx.app.ui.home.HomeData
import com.equipx.app.ui.home.HomeMission
import com.equipx.app.ui.home.HomeUiState
import com.equipx.app.ui.learn.LearnContent
import com.equipx.app.ui.learn.LearnItem
import com.equipx.app.ui.learn.LearnUiState
import com.equipx.app.ui.missions.MissionsContent
import com.equipx.app.ui.missions.MissionsUiState
import com.equipx.app.ui.profile.ProfileContent
import com.equipx.app.ui.profile.ProfileData
import com.equipx.app.ui.quiz.QuizContent
import com.equipx.app.ui.quiz.QuizUiState
import com.equipx.app.ui.scan.DevicePickerContent
import com.equipx.app.ui.scan.ScanOverlay
import com.equipx.app.ui.theme.EquipXTheme
import com.github.takahirom.roborazzi.captureRoboImage
import java.io.File
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * JVM screenshots of the key screens rendered from stateless content composables with real bundled content and fake
 * progress. Recorded with `./gradlew recordRoborazziDebug` into `src/test/snapshots/` (gitignored); skipped by a plain
 * `testDebugUnitTest`.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h780dp-xhdpi")
class ScreenshotTests {
    @get:Rule val rule = createComposeRule()

    private val contentDir = File("src/main/assets/content")
    private val index: List<DeviceSummary> = ContentParser.parseIndex(File(contentDir, "index.json").readText()).devices
    private fun device(id: String): Device = ContentParser.parseDevice(File(contentDir, "$id.json").readText())
    private val missions: List<Mission> = ContentParser.parseMissions(File(contentDir, "missions.json").readText())
    private val user = User("student@equipx.app", "Student", Role.STUDENT)

    private val learnItems: List<LearnItem> = index.mapIndexed { i, d ->
        val parts = device(d.id).parts.size
        LearnItem(d, parts, viewedCount = listOf(7, 3, 0, 10, 1, 0, 4, 2, 0, 5)[i % 10].coerceAtMost(parts))
    }

    private fun shot(name: String, tallDp: Int? = null, content: @Composable () -> Unit) {
        if (tallDp != null) RuntimeEnvironment.setQualifiers("+w360dp-h${tallDp}dp-xhdpi")
        rule.mainClock.autoAdvance = false
        rule.setContent { EquipXTheme { content() } }
        rule.mainClock.advanceTimeBy(1500)
        rule.onRoot().captureRoboImage("src/test/snapshots/$name.png")
    }

    @Test fun home() = shot("home", tallDp = 1500) {
        HomeContent(
            HomeUiState.Loaded(
                HomeData(
                    greeting = "Good morning", userName = "Student", points = 148, level = com.equipx.app.domain.Levels.of(148),
                    badgesUnlocked = 3,
                    continueItem = ContinueItem("digital_multimeter", "Digital Multimeter", "Measurement", "Last quiz: Multimeter basics (4/5)"),
                    mission = HomeMission(missions.first(), done = 2, total = missions.first().tasks.size, nextTask = missions.first().tasks.getOrNull(2)?.title, isActive = true),
                    devices = learnItems,
                )
            ),
            onRetry = {}, onScan = {}, onBrowse = {}, onOpenDevice = {}, onOpenMissions = {},
        )
    }

    @Test fun learnGrid() = shot("learn_grid", tallDp = 1500) {
        LearnContent(LearnUiState.Loaded(learnItems), onRetry = {}, onOpenDevice = {})
    }

    @Test fun deviceDetail() = shot("device_detail", tallDp = 2200) {
        val d = device("digital_multimeter")
        DeviceDetailContent(
            DeviceDetailState.Loaded(
                d,
                viewedPartIds = d.parts.take(4).map { it.id }.toSet(),
                completedProcedureIds = d.procedures.take(1).map { it.id }.toSet(),
                attempts = listOf(QuizAttempt(d.id, d.quizzes.first().id, d.quizzes.first().title, 1L, total = 5, correct = 4)),
                fixedFaultIds = d.faults.take(1).map { it.id }.toSet(),
            ),
            onBack = {}, onRetry = {}, onExplore = {}, onOpenPart = {}, onOpenProcedure = {}, onOpenQuiz = {}, onOpenFault = {},
        )
    }

    private val cameraBackdrop = Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF3B4048), Color(0xFF1B1D21))))

    @Test fun scanSearching() = shot("scan_searching") {
        Box(cameraBackdrop) { ScanOverlay(emptyList(), index, onOpenDetected = {}, onChooseManually = {}) }
    }

    @Test fun scanDetected() = shot("scan_detected") {
        Box(cameraBackdrop) {
            ScanOverlay(
                listOf(Detection("Digital Multimeter", "digital_multimeter", 0.92f, NormalizedBox(0.2f, 0.25f, 0.8f, 0.7f))),
                index, onOpenDetected = {}, onChooseManually = {},
            )
        }
    }

    @Test fun scanChooser() = shot("scan_manual_chooser", tallDp = 900) {
        // The real chooser is a ModalBottomSheet (separate window); render its content on a sheet-like surface.
        Box(cameraBackdrop) {
            Box(
                Modifier.align(Alignment.BottomCenter).fillMaxWidth().background(Color.White).padding(top = 20.dp),
            ) { DevicePickerContent(index, onPick = {}) }
        }
    }

    @Test fun explorerOverlay() = shot("explorer_controls") {
        val parts = device("digital_multimeter").parts.sortedBy { it.number }
        Box(Modifier.fillMaxSize().background(com.equipx.app.ui.theme.EquipXColors.Surface)) {
            ExplorerOverlay(parts, parts[2].id, modelLoaded = true, showCoach = true, onReset = {}, onOpenParts = {}, onPick = {}, onDismissCoach = {})
        }
    }

    private fun quizState(revealed: Boolean): QuizUiState.Active {
        val q = device("digital_multimeter").quizzes.first()
        val answers = List<Int?>(q.questions.size) { null }.toMutableList()
        answers[1] = if (revealed) (q.questions[1].correctIndex + 1) % q.questions[1].options.size else null
        return QuizUiState.Active(q, index = 1, answers = answers, revealed = revealed, remainingSec = null)
    }

    @Test fun quizQuestion() = shot("quiz_question") {
        QuizContent(quizState(false), {}, {}, {}, {}, {}, {})
    }

    @Test fun quizAnswered() = shot("quiz_answered") {
        QuizContent(quizState(true), {}, {}, {}, {}, {}, {})
    }

    @Test fun quizResult() = shot("quiz_result", tallDp = 1500) {
        val q = device("digital_multimeter").quizzes.first()
        val answers: List<Int?> = q.questions.mapIndexed { i, qq -> if (i == 2) (qq.correctIndex + 1) % qq.options.size else qq.correctIndex }
        QuizContent(
            QuizUiState.Finished(q, QuizScorer.score(q, answers), answers, durationSec = 84, timedOut = false),
            {}, {}, {}, {}, {}, {},
        )
    }

    @Test fun profile() = shot("profile", tallDp = 1700) {
        val now = 1_700_000_000_000L
        val ledger = listOf(
            PointEntry(PointReason.SCAN, 10, "digital_multimeter:2024-01-01", now),
            PointEntry(PointReason.PART_VIEW, 2, "digital_multimeter:lcd_display", now),
            PointEntry(PointReason.QUIZ_CORRECT, 5, "digital_multimeter:dmm_basics:q1", now),
        ) + List(28) { PointEntry(PointReason.QUIZ_CORRECT, 5, "k$it", now) }
        ProfileContent(
            ProfileData(
                user,
                ProgressState(
                    ledger = ledger,
                    unlockedBadges = listOf(UnlockedBadge("first_scan"), UnlockedBadge("safety_first"), UnlockedBadge("quiz_ace")),
                    viewedParts = setOf("a:1", "a:2", "a:3"), completedProcedures = setOf("a:p"), fixedFaults = setOf("a:f"),
                ),
                quizAttempts = 3, quizAccuracyPercent = 80, quizQuestions = 15,
            ),
            onOpenCredits = {}, onLogout = {},
        )
    }

    private fun missionsLoaded() = MissionsUiState.Loaded(
        missions,
        MissionState(activeMissionId = missions.first().id, doneTasks = mapOf(missions.first().id to missions.first().tasks.take(2).map { it.id })),
        completed = setOf(missions.last().id),
    )

    @Test fun missionsList() = shot("missions_list") {
        MissionsContent(missionsLoaded(), selected = null, onRetry = {}, onSelect = {}, onStart = {}, onAction = {})
    }

    @Test fun missionDetail() = shot("mission_detail", tallDp = 1100) {
        MissionsContent(missionsLoaded(), selected = missions.first(), onRetry = {}, onSelect = {}, onStart = {}, onAction = {})
    }

    @Test fun emptyAndErrorStates() = shot("empty_state") {
        MissionsContent(MissionsUiState.Loaded(emptyList(), MissionState(), emptySet()), selected = null, onRetry = {}, onSelect = {}, onStart = {}, onAction = {})
    }
}
