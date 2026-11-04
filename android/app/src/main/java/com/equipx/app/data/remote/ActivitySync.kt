package com.equipx.app.data.remote

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.equipx.app.data.EquipXJson
import java.io.IOException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive

/** How one HTTP step ended. */
sealed interface StepOutcome {
    data object Ok : StepOutcome
    data object Unauthorized : StepOutcome

    /** Offline, 5xx, 429...: try again later. */
    data class Transient(val message: String) : StepOutcome

    /** The server will never accept this (RLS, FK, bad ref): drop the op, local data is untouched. */
    data class Permanent(val message: String) : StepOutcome
}

object SyncErrors {
    /** PostgREST error body -> (code, message). */
    private fun parse(body: String): Pair<String?, String?> {
        val obj = runCatching { EquipXJson.parseToJsonElement(body) as? JsonObject }.getOrNull()
        return obj?.get("code")?.jsonPrimitive?.contentOrNull to obj?.get("message")?.jsonPrimitive?.contentOrNull
    }

    fun classify(step: SyncStep, resp: HttpResponse): StepOutcome {
        if (resp.isSuccess) return StepOutcome.Ok
        val (code, message) = parse(resp.body)
        val text = "HTTP ${resp.code}${code?.let { " $it" }.orEmpty()}: ${message.orEmpty()}".trim()
        // unique_violation on insert: the row is already on the server (earlier attempt) -> idempotent success.
        if (step is SyncStep.Insert && code == "23505") return StepOutcome.Ok
        return when {
            resp.code == 401 -> StepOutcome.Unauthorized
            resp.code == 408 || resp.code == 429 || resp.code >= 500 -> StepOutcome.Transient(text)
            else -> StepOutcome.Permanent(text)
        }
    }
}

/** Exponential backoff: 2s, 4s, 8s ... capped at 5 minutes. */
object Backoff {
    fun delayMs(attempt: Int, baseMs: Long = 2_000, capMs: Long = 300_000): Long {
        val shift = attempt.coerceIn(0, 20)
        return (baseMs shl shift).coerceAtMost(capMs)
    }
}

/** Slug -> uuid mapping, fetched from readable columns only (answer-key columns are not selectable by students). */
interface ContentIdSource {
    suspend fun cached(): ContentIds?

    /** Fetches fresh ids; throws [IOException] when offline. */
    suspend fun refresh(accessToken: String): ContentIds
}

class RemoteContentIdSource(
    private val config: SupabaseConfig,
    private val transport: HttpTransport,
    private val store: DataStore<Preferences>? = null,
) : ContentIdSource {
    private val key = stringPreferencesKey("sync_content_ids_json")
    @Volatile private var memory: ContentIds? = null

    override suspend fun cached(): ContentIds? {
        memory?.let { return it }
        val raw = store?.data?.first()?.get(key) ?: return null
        return runCatching { EquipXJson.decodeFromString(ContentIds.serializer(), raw) }.getOrNull()?.also { memory = it }
    }

    override suspend fun refresh(accessToken: String): ContentIds {
        suspend fun rows(query: String): List<JsonObject> {
            val resp = transport.execute(HttpRequest("GET", config.restUrl("$query&limit=5000"), config.headers(accessToken)))
            if (!resp.isSuccess) throw IOException("content ids: HTTP ${resp.code}")
            return (EquipXJson.parseToJsonElement(resp.body) as? JsonArray).orEmpty().mapNotNull { it as? JsonObject }
        }
        fun JsonObject.s(k: String) = this[k]?.jsonPrimitive?.contentOrNull.orEmpty()

        val quizzes = rows("quizzes?select=id,slug,equipment_id").associate { "${it.s("equipment_id")}/${it.s("slug")}" to it.s("id") }
        val questions = rows("quiz_questions?select=id,quiz_id,slug").associate { "${it.s("quiz_id")}/${it.s("slug")}" to it.s("id") }
        val procedures = rows("procedures?select=id,slug,equipment_id").associate { "${it.s("equipment_id")}/${it.s("slug")}" to it.s("id") }
        val faults = rows("fault_scenarios?select=id,slug,equipment_id").associate { "${it.s("equipment_id")}/${it.s("slug")}" to it.s("id") }
        val options = rows("fault_options?select=id,scenario_id,slug").associate { "${it.s("scenario_id")}/${it.s("slug")}" to it.s("id") }
        val missions = rows("missions?select=id,slug").associate { it.s("slug") to it.s("id") }
        val tasks = rows("mission_tasks?select=id,mission_id,sort_order")
            .associate { "${it.s("mission_id")}/${it["sort_order"]?.jsonPrimitive?.intOrNull ?: 0}" to it.s("id") }
        val ids = ContentIds(quizzes, questions, procedures, faults, options, missions, tasks)
        memory = ids
        runCatching {
            store?.edit { it[key] = EquipXJson.encodeToString(ContentIds.serializer(), ids) }
        }
        return ids
    }
}

sealed interface DrainResult {
    /** Nothing left to send for the signed-in user (or nothing to do). */
    data object Idle : DrainResult
    data object NotSignedIn : DrainResult

    /** Something failed transiently: retry with backoff. */
    data class Retry(val message: String) : DrainResult
}

/**
 * Uploads the outbox of the signed-in user, oldest first. Each op is idempotent (client uuids + ignore-duplicates), so a
 * crash or retry mid-op never duplicates rows. Server rejections drop only that op; local progress is never touched.
 */
class ActivitySync(
    private val config: SupabaseConfig,
    private val transport: HttpTransport,
    private val tokens: TokenProvider,
    private val store: OutboxStore,
    private val idSource: ContentIdSource,
) {
    private val mutex = Mutex()

    suspend fun drainOnce(): DrainResult = mutex.withLock {
        var session = tokens.validSession() ?: return DrainResult.NotSignedIn
        val state0 = store.read()
        val mine = state0.ops.filter { it.ownerId == session.userId }
        if (mine.isEmpty() && !state0.badgeCheckPending) return DrainResult.Idle

        var ids: ContentIds = idSource.cached() ?: ContentIds()
        var refreshedIds = false
        suspend fun refreshIds(): String? = try {
            ids = idSource.refresh(session.accessToken); refreshedIds = true; null
        } catch (e: IOException) {
            e.message ?: "offline"
        }
        if (ids.isEmpty && mine.isNotEmpty()) refreshIds()?.let { return DrainResult.Retry(it) }

        suspend fun run(step: SyncStep): StepOutcome {
            var forced = false
            while (true) {
                val outcome = try {
                    SyncErrors.classify(step, transport.execute(SyncRequests.toRequest(step, config, session.accessToken)))
                } catch (e: IOException) {
                    StepOutcome.Transient(e.message ?: "offline")
                }
                if (outcome is StepOutcome.Unauthorized) {
                    if (forced) return StepOutcome.Transient("unauthorized")
                    forced = true
                    session = tokens.validSession(forceRefresh = true) ?: return StepOutcome.Transient("session expired")
                    continue
                }
                return outcome
            }
        }

        for (op in mine) {
            var plan = SyncRequests.plan(op, ids)
            if (plan is Plan.MissingId && !refreshedIds) {
                // Content may have been seeded/updated since the cache was taken.
                refreshIds()?.let { return DrainResult.Retry(it) }
                plan = SyncRequests.plan(op, ids)
            }
            when (plan) {
                is Plan.MissingId -> {
                    drop(op, "unknown on server: ${plan.key}")
                }

                is Plan.Steps -> {
                    var dropped: String? = null
                    for (step in plan.steps) {
                        when (val o = run(step)) {
                            StepOutcome.Ok -> Unit
                            is StepOutcome.Transient -> return DrainResult.Retry(o.message)
                            is StepOutcome.Permanent -> {
                                // An insert rejection invalidates the op; a rejected award (e.g. already granted) only skips that award.
                                if (step is SyncStep.Insert) { dropped = o.message; break }
                            }
                            StepOutcome.Unauthorized -> return DrainResult.Retry("unauthorized")
                        }
                    }
                    if (dropped != null) drop(op, dropped)
                    else store.update { s -> s.copy(ops = s.ops.filterNot { it.id == op.id }, badgeCheckPending = true) }
                }
            }
        }

        if (store.read().badgeCheckPending) {
            when (val o = run(SyncRequests.checkBadges)) {
                is StepOutcome.Transient -> return DrainResult.Retry(o.message)
                StepOutcome.Unauthorized -> return DrainResult.Retry("unauthorized")
                else -> store.update { it.copy(badgeCheckPending = false) }
            }
        }
        DrainResult.Idle
    }

    private suspend fun drop(op: OutboxOp, message: String) {
        store.update { s -> s.copy(ops = s.ops.filterNot { it.id == op.id }, droppedCount = s.droppedCount + 1, lastError = message) }
    }
}
