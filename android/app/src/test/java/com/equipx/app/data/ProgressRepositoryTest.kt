package com.equipx.app.data

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import com.equipx.app.domain.ActivityEvent
import com.equipx.app.domain.Badge
import com.equipx.app.domain.Mission
import com.equipx.app.domain.MissionTask
import com.equipx.app.domain.PointEntry
import com.equipx.app.domain.PointReason
import com.equipx.app.domain.QuizAttempt
import java.io.File
import java.time.LocalDate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.builtins.ListSerializer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ProgressRepositoryTest {
    private lateinit var dir: File
    private lateinit var scope: CoroutineScope
    private lateinit var store: androidx.datastore.core.DataStore<androidx.datastore.preferences.core.Preferences>
    private lateinit var repo: DataStoreProgressRepository

    private val viewedKey = stringSetPreferencesKey("viewed_parts")
    private val attemptsKey = stringPreferencesKey("quiz_attempts_json")
    private val ledgerKey = stringPreferencesKey("points_ledger_json")
    private val ledgerBackupKey = stringPreferencesKey("points_ledger_json_corrupt_backup")

    @Before fun setUp() {
        dir = File.createTempFile("equipx", "ds").also { it.delete(); it.mkdirs() }
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        store = PreferenceDataStoreFactory.create(scope = scope) { File(dir, "t.preferences_pb") }
        repo = DataStoreProgressRepository(store)
    }

    @After fun tearDown() {
        scope.cancel()
        dir.deleteRecursively()
    }

    private val mission = Mission(
        "m1", tasks = listOf(MissionTask("t1", "scan", "Scan", "dmm")),
    )

    private class FakeContent(private val missions: List<Mission>) : ContentRepository {
        override suspend fun getIndex() = Result.success(emptyList<com.equipx.app.domain.DeviceSummary>())
        override suspend fun getDevice(id: String) = Result.failure<com.equipx.app.domain.Device>(IllegalStateException())
        override suspend fun getMissions() = Result.success(missions)
        override suspend fun assetExists(path: String) = false
    }

    private suspend fun <T> eventually(block: suspend () -> T?): T = withTimeout(10_000) {
        var v = block()
        while (v == null) { kotlinx.coroutines.delay(20); v = block() }
        v
    }

    @Test fun migrationDerivesPerfectQuizAndBadgesSilentlyAndIsIdempotent() = runBlocking {
        val perfect = QuizAttempt("dmm", "q", total = 5, correct = 5)
        store.edit { p ->
            p[viewedKey] = (1..10).map { "dmm:p$it" }.toSet()
            p[attemptsKey] = EquipXJson.encodeToString(ListSerializer(QuizAttempt.serializer()), listOf(perfect))
        }
        repo.migrate()
        val s1 = repo.progress.first()
        assertTrue(s1.hasPerfectQuiz)
        assertEquals(setOf("part_spotter", "quiz_ace"), s1.unlockedBadges.map { it.id }.toSet())
        assertEquals(20, s1.points)

        repo.migrate()
        val s2 = repo.progress.first()
        assertEquals(s1.unlockedBadges, s2.unlockedBadges)
        assertEquals(s1.ledger, s2.ledger)

        // First real event after migration announces only genuinely new badges.
        val r = repo.record(ActivityEvent.Scan("dmm", LocalDate.of(2026, 1, 1)))
        assertEquals(listOf(Badge.FIRST_SCAN), r.newBadges)
    }

    @Test fun ledgerIsNotCappedAndTotalNeverDrops() = runBlocking {
        val big = (1..6000).map { PointEntry(PointReason.PART_VIEW, 2, "d:p$it", 1L) }
        store.edit { it[ledgerKey] = EquipXJson.encodeToString(ListSerializer(PointEntry.serializer()), big) }
        repo.record(ActivityEvent.PartViewed("d", "new"))
        val s = repo.progress.first()
        assertEquals(6001, s.ledger.size)
        assertEquals(12002, s.points)
    }

    @Test fun corruptLedgerIsBackedUpNotOverwrittenSilently() = runBlocking {
        store.edit { it[ledgerKey] = "{not json"; it[viewedKey] = setOf("d:a") }
        repo.record(ActivityEvent.PartViewed("d", "b"))
        val prefs = store.data.first()
        assertEquals("{not json", prefs[ledgerBackupKey])
        assertEquals(4, repo.progress.first().points) // rebuilt from legacy viewed parts (a) + new part (b)
    }

    @Test fun applyMissionStepStoresProgressAndAwardsAtomically() = runBlocking {
        val results = repo.applyMissionStep { st ->
            MissionStep(st.copy(activeMissionId = null, doneTasks = st.doneTasks + ("m1" to listOf("t1"))), listOf("m1"))
        }
        assertEquals(1, results.size)
        val s = repo.progress.first()
        assertTrue("m1" in s.completedMissions)
        assertEquals(50, s.points)
        assertTrue(s.unlockedBadges.any { it.id == "mission_complete" })
        assertEquals(listOf("t1"), repo.missionState.first().doneTasks["m1"])
        // Repeating is idempotent.
        repo.applyMissionStep { MissionStep(it, listOf("m1")) }
        assertEquals(50, repo.progress.first().points)
    }

    @Test fun trackerCompletionPathAwardsMissionOnLastTask() = runBlocking {
        val tracker = ActivityTracker(repo, FakeContent(listOf(mission)), scope)
        tracker.activateMission("m1")
        tracker.record(ActivityEvent.Scan("dmm", LocalDate.of(2026, 1, 1)))
        val s = eventually { repo.progress.first().takeIf { "m1" in it.completedMissions } }
        assertEquals(60, s.points) // scan 10 + mission 50
        assertNull(repo.missionState.first().activeMissionId)
        val pending = eventually { tracker.pendingBadges.value.takeIf { Badge.MISSION_COMPLETE in it } }
        assertTrue(Badge.FIRST_SCAN in pending)
        tracker.acknowledgeBadge(Badge.FIRST_SCAN)
        assertFalse(Badge.FIRST_SCAN in tracker.pendingBadges.value)
    }

    @Test fun trackerStartRepairsMissionWithAllTasksDoneButNoAward() = runBlocking {
        repo.updateMissionState { it.copy(activeMissionId = "m1", doneTasks = mapOf("m1" to listOf("t1"))) }
        assertFalse("m1" in repo.progress.first().completedMissions)
        val tracker = ActivityTracker(repo, FakeContent(listOf(mission)), scope)
        val s = eventually { repo.progress.first().takeIf { "m1" in it.completedMissions } }
        assertEquals(50, s.points)
        assertNull(repo.missionState.first().activeMissionId)
        assertNotNull(eventually { tracker.pendingBadges.value.takeIf { Badge.MISSION_COMPLETE in it } })
    }

    @Test fun trackerAppliesEventsInCallOrder() = runBlocking {
        val tracker = ActivityTracker(repo, FakeContent(emptyList()), scope)
        repeat(20) { tracker.post(ActivityEvent.PartViewed("d", "p$it")) }
        val last = tracker.record(ActivityEvent.PartViewed("d", "last"))
        assertEquals(21, last.state.viewedParts.size)
        assertEquals(21 * 2, last.state.points)
    }
}
