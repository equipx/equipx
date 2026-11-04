package com.equipx.app.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import com.equipx.app.domain.ActivityEvent
import com.equipx.app.domain.EngineResult
import com.equipx.app.domain.FaultAttempt
import com.equipx.app.domain.MissionState
import com.equipx.app.domain.PointEntry
import com.equipx.app.domain.ProgressEngine
import com.equipx.app.domain.ProgressState
import com.equipx.app.domain.QuizAttempt
import com.equipx.app.domain.UnlockedBadge
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer

/** Result of a mission bookkeeping step: the new [state] plus ids of missions finished by it (awarded atomically). */
data class MissionStep(val state: MissionState, val completed: List<String> = emptyList())

interface ProgressRepository {
    /** Full gamification state (ledger, badges, counters). */
    val progress: Flow<ProgressState>

    /** Set of "deviceId:partId" the user has viewed. */
    val viewedParts: Flow<Set<String>>
    val points: Flow<Int>

    /** Set of "deviceId:procedureId" the user has completed. */
    val completedProcedures: Flow<Set<String>>

    /** Set of "deviceId:faultId" diagnosed correctly. */
    val fixedFaults: Flow<Set<String>>

    /** All saved quiz attempts, oldest first. */
    val quizAttempts: Flow<List<QuizAttempt>>
    val faultAttempts: Flow<List<FaultAttempt>>
    val missionState: Flow<MissionState>

    /** Applies [event] through the points/badge engine atomically and persists the result. */
    suspend fun record(event: ActivityEvent): EngineResult
    suspend fun saveQuizAttempt(attempt: QuizAttempt)
    suspend fun saveFaultAttempt(attempt: FaultAttempt)

    /** Atomically transforms the mission bookkeeping; returns the new state. */
    suspend fun updateMissionState(transform: (MissionState) -> MissionState): MissionState

    /**
     * Atomically (one storage edit) stores the new mission bookkeeping AND awards [MissionStep.completed] missions
     * (points, completedMissions, badges). Safe to repeat: awards are deduped by the ledger.
     * Returns one [EngineResult] per completed mission.
     */
    suspend fun applyMissionStep(transform: (MissionState) -> MissionStep): List<EngineResult>

    /** Runs the one-time silent upgrade of old/corrupt stored progress. Idempotent. */
    suspend fun migrate()
}

class DataStoreProgressRepository(private val store: DataStore<Preferences>) : ProgressRepository {
    // Legacy keys kept: viewed_parts, completed_procedures, quiz_attempts_json. Legacy "points" is superseded by the ledger.
    private val viewedKey = stringSetPreferencesKey("viewed_parts")
    private val proceduresKey = stringSetPreferencesKey("completed_procedures")
    private val attemptsKey = stringPreferencesKey("quiz_attempts_json")
    private val ledgerKey = stringPreferencesKey("points_ledger_json")
    private val badgesKey = stringPreferencesKey("badges_json")
    private val scanCountKey = intPreferencesKey("scan_count")
    private val perfectKey = booleanPreferencesKey("perfect_quiz")
    private val faultsKey = stringSetPreferencesKey("fixed_faults")
    private val missionsDoneKey = stringSetPreferencesKey("completed_missions")
    private val faultAttemptsKey = stringPreferencesKey("fault_attempts_json")
    private val activeMissionKey = stringPreferencesKey("active_mission")
    private val missionProgressKey = stringPreferencesKey("mission_progress_json")
    private val ledgerBackupKey = stringPreferencesKey("points_ledger_json_corrupt_backup")
    private val badgesBackupKey = stringPreferencesKey("badges_json_corrupt_backup")

    private val attemptsSerializer = ListSerializer(QuizAttempt.serializer())
    private val faultAttemptsSerializer = ListSerializer(FaultAttempt.serializer())
    private val ledgerSerializer = ListSerializer(PointEntry.serializer())
    private val badgesSerializer = ListSerializer(UnlockedBadge.serializer())
    private val missionProgressSerializer = MapSerializer(String.serializer(), ListSerializer(String.serializer()))

    /** Raw stored values; compared before decoding so unrelated preference changes never trigger JSON work. */
    private data class Raw(
        val ledger: String?, val badges: String?, val scans: Int, val viewed: Set<String>,
        val procedures: Set<String>, val perfect: Boolean, val faults: Set<String>, val missions: Set<String>,
    )

    private fun rawOf(p: Preferences) = Raw(
        p[ledgerKey], p[badgesKey], p[scanCountKey] ?: 0, p[viewedKey] ?: emptySet(),
        p[proceduresKey] ?: emptySet(), p[perfectKey] ?: false, p[faultsKey] ?: emptySet(), p[missionsDoneKey] ?: emptySet(),
    )

    private fun decode(raw: Raw): ProgressState {
        val ledger = (if (raw.ledger == null) null else decodeOrNull(ledgerSerializer, raw.ledger))
            ?: ProgressEngine.legacyLedger(raw.viewed)
        return ProgressState(
            ledger = ledger,
            unlockedBadges = if (raw.badges.isNullOrBlank()) emptyList() else decodeOrNull(badgesSerializer, raw.badges) ?: emptyList(),
            scanCount = raw.scans,
            viewedParts = raw.viewed,
            completedProcedures = raw.procedures,
            hasPerfectQuiz = raw.perfect,
            fixedFaults = raw.faults,
            completedMissions = raw.missions,
        )
    }

    private fun <T> decodeOrNull(s: kotlinx.serialization.KSerializer<List<T>>, json: String): List<T>? =
        runCatching { EquipXJson.decodeFromString(s, json) }.getOrNull()

    private fun warn(msg: String) {
        runCatching { android.util.Log.w("EquipXProgress", msg) }
    }

    /**
     * Loads progress inside an edit. When the ledger key is missing (old build) or stored JSON is unreadable, rebuilds
     * from legacy data, derives perfect-quiz from stored attempts, silently unlocks earned badges and persists
     * (so later launches skip this). Unreadable raw JSON is copied to a backup key, never silently discarded.
     */
    private fun loadForUpdate(prefs: MutablePreferences, nowMillis: Long): ProgressState {
        val raw = rawOf(prefs)
        val ledgerBad = raw.ledger != null && decodeOrNull(ledgerSerializer, raw.ledger) == null
        val badgesBad = !raw.badges.isNullOrBlank() && decodeOrNull(badgesSerializer, raw.badges) == null
        if (ledgerBad) {
            warn("points ledger unreadable; backed up raw value and rebuilding from legacy data")
            if (prefs[ledgerBackupKey] == null) prefs[ledgerBackupKey] = raw.ledger!!
        }
        if (badgesBad) {
            warn("badges unreadable; backed up raw value and re-deriving")
            if (prefs[badgesBackupKey] == null) prefs[badgesBackupKey] = raw.badges!!
        }
        val state = decode(raw)
        if (raw.ledger == null || ledgerBad || badgesBad) {
            val attempts = decodeOrNull(attemptsSerializer, prefs[attemptsKey].orEmpty()) ?: emptyList()
            val migrated = ProgressEngine.migrate(state, attempts, nowMillis)
            write(prefs, migrated)
            return migrated
        }
        return state
    }

    private fun <T> decodeList(s: kotlinx.serialization.KSerializer<List<T>>, json: String?): List<T> =
        if (json.isNullOrBlank()) emptyList() else runCatching { EquipXJson.decodeFromString(s, json) }.getOrDefault(emptyList())

    private fun write(prefs: MutablePreferences, s: ProgressState) {
        prefs[ledgerKey] = EquipXJson.encodeToString(ledgerSerializer, s.ledger)
        prefs[badgesKey] = EquipXJson.encodeToString(badgesSerializer, s.unlockedBadges)
        prefs[scanCountKey] = s.scanCount
        prefs[viewedKey] = s.viewedParts
        prefs[proceduresKey] = s.completedProcedures
        prefs[perfectKey] = s.hasPerfectQuiz
        prefs[faultsKey] = s.fixedFaults
        prefs[missionsDoneKey] = s.completedMissions
    }

    override val progress: Flow<ProgressState> =
        store.data.map { rawOf(it) }.distinctUntilChanged().map { decode(it) }.flowOn(Dispatchers.Default)

    override val viewedParts: Flow<Set<String>> = store.data.map { it[viewedKey] ?: emptySet() }.distinctUntilChanged()
    override val completedProcedures: Flow<Set<String>> = store.data.map { it[proceduresKey] ?: emptySet() }.distinctUntilChanged()
    override val fixedFaults: Flow<Set<String>> = store.data.map { it[faultsKey] ?: emptySet() }.distinctUntilChanged()
    override val points: Flow<Int> = progress.map { it.points }.distinctUntilChanged()

    override val quizAttempts: Flow<List<QuizAttempt>> =
        store.data.map { it[attemptsKey] }.distinctUntilChanged().map { decodeList(attemptsSerializer, it) }.flowOn(Dispatchers.Default)
    override val faultAttempts: Flow<List<FaultAttempt>> =
        store.data.map { it[faultAttemptsKey] }.distinctUntilChanged().map { decodeList(faultAttemptsSerializer, it) }.flowOn(Dispatchers.Default)

    override val missionState: Flow<MissionState> =
        store.data.map { it[activeMissionKey] to it[missionProgressKey] }.distinctUntilChanged()
            .map { (active, json) -> MissionState(active, decodeMissionProgress(json)) }.flowOn(Dispatchers.Default)

    private fun decodeMissionProgress(json: String?): Map<String, List<String>> =
        if (json.isNullOrBlank()) emptyMap()
        else runCatching { EquipXJson.decodeFromString(missionProgressSerializer, json) }.getOrDefault(emptyMap())

    override suspend fun record(event: ActivityEvent): EngineResult {
        var result: EngineResult? = null
        store.edit { prefs ->
            val now = System.currentTimeMillis()
            val r = ProgressEngine.apply(loadForUpdate(prefs, now), event, now)
            write(prefs, r.state)
            result = r
        }
        return result!!
    }

    override suspend fun saveQuizAttempt(attempt: QuizAttempt) {
        store.edit { prefs ->
            val updated = (decodeList(attemptsSerializer, prefs[attemptsKey]) + attempt).takeLast(MAX_ATTEMPTS)
            prefs[attemptsKey] = EquipXJson.encodeToString(attemptsSerializer, updated)
        }
    }

    override suspend fun saveFaultAttempt(attempt: FaultAttempt) {
        store.edit { prefs ->
            val updated = (decodeList(faultAttemptsSerializer, prefs[faultAttemptsKey]) + attempt).takeLast(MAX_ATTEMPTS)
            prefs[faultAttemptsKey] = EquipXJson.encodeToString(faultAttemptsSerializer, updated)
        }
    }

    override suspend fun updateMissionState(transform: (MissionState) -> MissionState): MissionState {
        var result = MissionState()
        store.edit { prefs ->
            val current = MissionState(prefs[activeMissionKey], decodeMissionProgress(prefs[missionProgressKey]))
            val next = transform(current)
            if (next.activeMissionId == null) prefs.remove(activeMissionKey) else prefs[activeMissionKey] = next.activeMissionId
            prefs[missionProgressKey] = EquipXJson.encodeToString(missionProgressSerializer, next.doneTasks)
            result = next
        }
        return result
    }

    override suspend fun applyMissionStep(transform: (MissionState) -> MissionStep): List<EngineResult> {
        val results = mutableListOf<EngineResult>()
        store.edit { prefs ->
            val now = System.currentTimeMillis()
            var state = loadForUpdate(prefs, now)
            val step = transform(MissionState(prefs[activeMissionKey], decodeMissionProgress(prefs[missionProgressKey])))
            val next = step.state
            if (next.activeMissionId == null) prefs.remove(activeMissionKey) else prefs[activeMissionKey] = next.activeMissionId
            prefs[missionProgressKey] = EquipXJson.encodeToString(missionProgressSerializer, next.doneTasks)
            for (id in step.completed) {
                val r = ProgressEngine.apply(state, ActivityEvent.MissionCompleted(id), now)
                state = r.state
                results += r
            }
            if (step.completed.isNotEmpty()) write(prefs, state)
        }
        return results
    }

    override suspend fun migrate() {
        store.edit { prefs -> loadForUpdate(prefs, System.currentTimeMillis()) }
    }

    private companion object {
        const val MAX_ATTEMPTS = 200
    }
}
