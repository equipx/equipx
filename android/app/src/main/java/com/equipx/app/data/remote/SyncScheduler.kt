package com.equipx.app.data.remote

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import com.equipx.app.data.ContentRepository
import com.equipx.app.data.ActivitySink
import com.equipx.app.domain.ActivityEvent
import com.equipx.app.domain.EngineResult
import com.equipx.app.domain.FaultAttempt
import com.equipx.app.domain.MissionRules
import com.equipx.app.domain.PointReason
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Runs [ActivitySync] off the UI thread: one background loop that drains whenever woken (new activity, sign-in,
 * network back, app start) and retries failures with [Backoff]. Never throws, never blocks callers.
 */
class SyncScheduler(
    private val sync: ActivitySync,
    scope: CoroutineScope,
) {
    private val wake = Channel<Unit>(Channel.CONFLATED)

    init {
        scope.launch {
            var attempt = 0
            while (true) {
                val result = runCatching { sync.drainOnce() }.getOrElse { DrainResult.Retry(it.message.orEmpty()) }
                when (result) {
                    is DrainResult.Retry -> {
                        val delayMs = Backoff.delayMs(attempt++)
                        // A wake-up (e.g. network restored) cuts the wait short and resets the backoff.
                        if (withTimeoutOrNull(delayMs) { wake.receive() } != null) attempt = 0
                    }
                    else -> {
                        attempt = 0
                        wake.receive()
                    }
                }
            }
        }
    }

    /** Ask for a sync soon (non-blocking, coalesced). */
    fun kick() {
        wake.trySend(Unit)
    }

    /** Best-effort final upload (used before sign-out); bounded so logout is never stuck. */
    suspend fun flush(timeoutMs: Long = 4_000) {
        withTimeoutOrNull(timeoutMs) { runCatching { sync.drainOnce() } }
    }

    /** Kicks a sync whenever a network becomes available. Safe to call once; failures are ignored. */
    fun watchNetwork(context: Context) {
        runCatching {
            val cm = context.applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            cm.registerDefaultNetworkCallback(object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) = kick()
            })
        }
    }
}

/**
 * Turns tracker output into outbox ops. Only signed-in users queue anything; the owner is the Supabase user id.
 * Local points/badges are unaffected: this only copies activity for the server.
 */
class OutboxActivitySink(
    private val store: OutboxStore,
    private val tokens: TokenProvider,
    private val content: ContentRepository,
    private val scheduler: () -> SyncScheduler?,
    private val now: () -> Long = System::currentTimeMillis,
    private val newId: () -> String = { UUID.randomUUID().toString() },
) : ActivitySink {

    private suspend fun enqueue(ops: (owner: String) -> List<OutboxOp>) {
        val owner = tokens.userId() ?: return
        val list = ops(owner)
        if (list.isEmpty()) return
        val t = now()
        store.update { s -> list.fold(s) { acc, op -> acc + op }.pruned(owner, t) }
        scheduler()?.kick()
    }

    override suspend fun onEvent(event: ActivityEvent, result: EngineResult) {
        val t = now()
        when (event) {
            is ActivityEvent.Scan -> enqueue { o -> listOf(OutboxOp.Scan(newId(), o, t, event.deviceId)) }
            is ActivityEvent.PartViewed -> {
                // Only the first view of a part ever earns points server-side, so only that one needs uploading.
                if (result.newEntries.none { it.reason == PointReason.PART_VIEW }) return
                enqueue { o -> listOf(OutboxOp.PartView(newId(), o, t, event.deviceId, event.partId)) }
            }
            is ActivityEvent.QuizCompleted -> {
                val a = event.attempt
                val quiz = content.getDevice(a.deviceId).getOrNull()?.quizzes?.firstOrNull { it.id == a.quizId } ?: return
                enqueue { o ->
                    listOf(
                        OutboxOp.QuizAttempt(
                            newId(), o, a.completedAtMillis.takeIf { it > 0 } ?: t, a.deviceId, a.quizId,
                            a.durationSec.takeIf { it >= 0 },
                            quiz.questions.mapIndexed { i, q ->
                                val sel = a.answers.getOrNull(i)?.takeIf { it >= 0 }
                                OutboxOp.QuizAnswerRow(newId(), q.id, sel, sel != null && sel == q.correctIndex)
                            },
                        ),
                    )
                }
            }
            is ActivityEvent.ProcedureCompleted ->
                enqueue { o -> listOf(OutboxOp.Procedure(newId(), o, t, event.deviceId, event.procedureId)) }
            // Fault attempts carry the chosen option, so they arrive via onFaultAttempt; quiz answers via QuizCompleted;
            // missions via onMissionProgress.
            is ActivityEvent.FaultDiagnosed, is ActivityEvent.QuizAnswered, is ActivityEvent.MissionCompleted -> Unit
        }
    }

    override suspend fun onFaultAttempt(attempt: FaultAttempt) {
        if (attempt.selectedOptionId.isBlank()) return
        enqueue { o ->
            listOf(
                OutboxOp.FaultAttempt(
                    newId(), o, attempt.timestampMillis.takeIf { it > 0 } ?: now(),
                    attempt.deviceId, attempt.faultId, attempt.selectedOptionId, attempt.correct,
                ),
            )
        }
    }

    override suspend fun onMissionProgress(missionId: String, newTaskIds: List<String>, complete: Boolean) {
        val mission = content.getMissions().getOrNull()?.firstOrNull { it.id == missionId } ?: return
        val t = now()
        enqueue { o ->
            val tasks = newTaskIds.mapNotNull { tid ->
                val idx = mission.tasks.indexOfFirst { it.id == tid }
                if (idx < 0) null else OutboxOp.MissionTask(missionTaskId(o, missionId, idx + 1), o, t, missionId, idx + 1)
            }
            tasks + if (complete) listOf(OutboxOp.MissionAward(missionTaskId(o, missionId, 0), o, t, missionId)) else emptyList()
        }
    }

    companion object {
        /** Deterministic, so re-enqueueing the same mission step never creates a second row. */
        fun missionTaskId(owner: String, missionId: String, order: Int): String =
            UUID.nameUUIDFromBytes("$owner:$missionId:$order".toByteArray()).toString()
    }
}
