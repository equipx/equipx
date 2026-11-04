package com.equipx.app.data.remote

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.equipx.app.data.EquipXJson
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * One unit of local activity waiting to reach the server. Content is referenced by its bundled (slug) ids; they are
 * resolved to server uuids at upload time. [id] is the client-generated uuid of the primary row (idempotency key:
 * inserting it twice is harmless). [ownerId] is the Supabase user that did it.
 */
@Serializable
sealed interface OutboxOp {
    val id: String
    val ownerId: String
    val atMillis: Long

    @Serializable @SerialName("scan")
    data class Scan(override val id: String, override val ownerId: String, override val atMillis: Long, val deviceId: String) : OutboxOp

    @Serializable @SerialName("part_view")
    data class PartView(
        override val id: String, override val ownerId: String, override val atMillis: Long,
        val deviceId: String, val partId: String,
    ) : OutboxOp

    @Serializable
    data class QuizAnswerRow(
        val id: String,
        val questionId: String,
        /** Null = timed out / skipped. */
        val selectedIndex: Int?,
        /** Local verdict; only decides whether `award_points(quiz_correct)` is attempted. The server recomputes it. */
        val locallyCorrect: Boolean,
    )

    @Serializable @SerialName("quiz_attempt")
    data class QuizAttempt(
        override val id: String, override val ownerId: String, override val atMillis: Long,
        val deviceId: String, val quizId: String, val durationSec: Int?, val answers: List<QuizAnswerRow>,
    ) : OutboxOp

    @Serializable @SerialName("fault_attempt")
    data class FaultAttempt(
        override val id: String, override val ownerId: String, override val atMillis: Long,
        val deviceId: String, val faultId: String, val optionId: String, val locallyCorrect: Boolean,
    ) : OutboxOp

    @Serializable @SerialName("procedure")
    data class Procedure(
        override val id: String, override val ownerId: String, override val atMillis: Long,
        val deviceId: String, val procedureId: String, val durationSec: Int? = null,
    ) : OutboxOp

    /** A mission task done. [taskOrder] is the 1-based position in the mission (server `mission_tasks.sort_order`). */
    @Serializable @SerialName("mission_task")
    data class MissionTask(
        override val id: String, override val ownerId: String, override val atMillis: Long,
        val missionId: String, val taskOrder: Int,
    ) : OutboxOp

    /** Every task of [missionId] is done: ask for the mission bonus (after its task rows are uploaded). */
    @Serializable @SerialName("mission_award")
    data class MissionAward(
        override val id: String, override val ownerId: String, override val atMillis: Long,
        val missionId: String,
    ) : OutboxOp
}

@Serializable
data class OutboxState(
    val ops: List<OutboxOp> = emptyList(),
    /** True when something was uploaded since the last successful `check_badges` call. */
    val badgeCheckPending: Boolean = false,
    /** Ops dropped because the server permanently rejected them (kept as a counter + last message for diagnostics). */
    val droppedCount: Int = 0,
    val lastError: String? = null,
    /** Failed (server-side, non-network) upload attempts per op id; see [SyncErrors.MAX_ATTEMPTS]. */
    val attempts: Map<String, Int> = emptyMap(),
)

object OutboxLimits {
    const val MAX_OPS = 2_000
    const val FOREIGN_MAX_AGE_MS = 14L * 24 * 60 * 60 * 1000
}

/**
 * Keeps the outbox bounded: drops other owners' ops older than 14 days, then, above [maxOps], the oldest ops of other
 * owners first and the oldest overall after that. Returns `this` (same instance) when nothing changes.
 */
fun OutboxState.pruned(owner: String, nowMs: Long, maxOps: Int = OutboxLimits.MAX_OPS): OutboxState {
    var kept = ops.filterNot { it.ownerId != owner && it.atMillis < nowMs - OutboxLimits.FOREIGN_MAX_AGE_MS }
    if (kept.size > maxOps) {
        val order = (kept.filter { it.ownerId != owner }.sortedBy { it.atMillis } + kept.sortedBy { it.atMillis })
            .map { it.id }.distinct().take(kept.size - maxOps).toSet()
        kept = kept.filterNot { it.id in order }
    }
    if (kept.size == ops.size) return this
    val ids = kept.mapTo(HashSet()) { it.id }
    return copy(ops = kept, attempts = attempts.filterKeys { it in ids })
}

interface OutboxStore {
    suspend fun read(): OutboxState

    /** Atomic read-modify-write. */
    suspend fun update(transform: (OutboxState) -> OutboxState): OutboxState
}

/** Appends [op] unless an op with the same id is already queued (deterministic ids make re-enqueueing a no-op). */
operator fun OutboxState.plus(op: OutboxOp): OutboxState =
    if (ops.any { it.id == op.id }) this else copy(ops = ops + op)

object OutboxCodec {
    fun encode(state: OutboxState): String = EquipXJson.encodeToString(OutboxState.serializer(), state)

    /** Unreadable data yields an empty outbox; the raw string is the caller's to back up. */
    fun decode(text: String?): OutboxState? =
        if (text.isNullOrBlank()) OutboxState() else runCatching { EquipXJson.decodeFromString(OutboxState.serializer(), text) }.getOrNull()
}

class DataStoreOutboxStore(private val store: DataStore<Preferences>) : OutboxStore {
    private val key = stringPreferencesKey("sync_outbox_json")
    private val backupKey = stringPreferencesKey("sync_outbox_json_corrupt_backup")

    override suspend fun read(): OutboxState = OutboxCodec.decode(store.data.first()[key]) ?: OutboxState()

    override suspend fun update(transform: (OutboxState) -> OutboxState): OutboxState {
        var result = OutboxState()
        store.edit { prefs ->
            val raw = prefs[key]
            val decoded = OutboxCodec.decode(raw)
            val current = decoded ?: run {
                if (prefs[backupKey] == null && raw != null) prefs[backupKey] = raw
                OutboxState()
            }
            result = transform(current)
            // No-op changes (e.g. re-enqueueing a known id) don't rewrite the JSON blob.
            if (decoded == null || result != current) prefs[key] = OutboxCodec.encode(result)
        }
        return result
    }
}

class InMemoryOutboxStore(initial: OutboxState = OutboxState()) : OutboxStore {
    private val mutex = Mutex()
    private var state = initial
    override suspend fun read(): OutboxState = mutex.withLock { state }
    override suspend fun update(transform: (OutboxState) -> OutboxState): OutboxState =
        mutex.withLock { transform(state).also { state = it } }
}
