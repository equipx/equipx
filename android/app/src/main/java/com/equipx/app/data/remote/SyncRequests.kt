package com.equipx.app.data.remote

import java.time.Instant
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Bundled content ids (slugs) -> server uuids. Content is seeded on the server from the same files, so slugs match.
 * Keys: quizzes/procedures/faults `"<device>/<slug>"`, questions `"<quizUuid>/<slug>"`, options `"<scenarioUuid>/<slug>"`,
 * missions `"<slug>"`, tasks `"<missionUuid>/<sort_order>"`.
 */
@Serializable
data class ContentIds(
    val quizzes: Map<String, String> = emptyMap(),
    val questions: Map<String, String> = emptyMap(),
    val procedures: Map<String, String> = emptyMap(),
    val faults: Map<String, String> = emptyMap(),
    val options: Map<String, String> = emptyMap(),
    val missions: Map<String, String> = emptyMap(),
    val tasks: Map<String, String> = emptyMap(),
) {
    val isEmpty: Boolean get() = quizzes.isEmpty() && procedures.isEmpty() && faults.isEmpty() && missions.isEmpty()
}

/** One HTTP call the sync makes for an op. */
sealed interface SyncStep {
    /** POST /rest/v1/<table>?on_conflict=id with [rows]; a duplicate (unique violation) counts as success. */
    data class Insert(val table: String, val rows: List<JsonObject>) : SyncStep

    /** POST /rest/v1/rpc/<function> with [body]. */
    data class Rpc(val function: String, val body: JsonObject) : SyncStep
}

sealed interface Plan {
    data class Steps(val steps: List<SyncStep>) : Plan

    /** A bundled id has no server counterpart (yet): [key] describes it. */
    data class MissingId(val key: String) : Plan
}

/** Pure translation of outbox ops to Supabase calls. Column names/types match the supabase init migration. */
object SyncRequests {
    fun iso(millis: Long): String = Instant.ofEpochMilli(millis).toString()

    fun plan(op: OutboxOp, ids: ContentIds): Plan = when (op) {
        is OutboxOp.Scan -> Plan.Steps(
            listOf(
                SyncStep.Insert("scans", listOf(buildJsonObject {
                    put("id", op.id); put("equipment_id", op.deviceId); put("created_at", iso(op.atMillis))
                })),
                award("scan", op.id),
            ),
        )

        is OutboxOp.PartView -> Plan.Steps(
            listOf(
                SyncStep.Insert("part_views", listOf(buildJsonObject {
                    put("id", op.id); put("equipment_id", op.deviceId); put("part_id", op.partId); put("created_at", iso(op.atMillis))
                })),
                award("first_part_view", "${op.deviceId}:${op.partId}"),
            ),
        )

        is OutboxOp.QuizAttempt -> planQuiz(op, ids)

        is OutboxOp.FaultAttempt -> {
            val scenario = ids.faults["${op.deviceId}/${op.faultId}"]
            val option = scenario?.let { ids.options["$it/${op.optionId}"] }
            if (scenario == null) Plan.MissingId("fault ${op.deviceId}/${op.faultId}")
            else if (option == null) Plan.MissingId("fault option ${op.faultId}/${op.optionId}")
            else Plan.Steps(
                buildList {
                    add(SyncStep.Insert("fault_attempts", listOf(buildJsonObject {
                        put("id", op.id); put("scenario_id", scenario); put("option_id", option); put("created_at", iso(op.atMillis))
                    })))
                    if (op.locallyCorrect) add(award("fault_fixed", op.id))
                },
            )
        }

        is OutboxOp.Procedure -> {
            val proc = ids.procedures["${op.deviceId}/${op.procedureId}"]
            if (proc == null) Plan.MissingId("procedure ${op.deviceId}/${op.procedureId}")
            else Plan.Steps(listOf(SyncStep.Insert("procedure_completions", listOf(buildJsonObject {
                put("id", op.id); put("procedure_id", proc)
                op.durationSec?.let { put("duration_sec", it) }
                put("created_at", iso(op.atMillis))
            }))))
        }

        is OutboxOp.MissionTask -> {
            val mission = ids.missions[op.missionId]
            val task = mission?.let { ids.tasks["$it/${op.taskOrder}"] }
            if (mission == null) Plan.MissingId("mission ${op.missionId}")
            else if (task == null) Plan.MissingId("mission task ${op.missionId}#${op.taskOrder}")
            else Plan.Steps(listOf(SyncStep.Insert("mission_progress", listOf(buildJsonObject {
                put("id", op.id); put("mission_id", mission); put("task_id", task); put("completed_at", iso(op.atMillis))
            }))))
        }

        is OutboxOp.MissionAward -> {
            val mission = ids.missions[op.missionId]
            if (mission == null) Plan.MissingId("mission ${op.missionId}") else Plan.Steps(listOf(award("mission_complete", mission)))
        }
    }

    private fun planQuiz(op: OutboxOp.QuizAttempt, ids: ContentIds): Plan {
        val quiz = ids.quizzes["${op.deviceId}/${op.quizId}"] ?: return Plan.MissingId("quiz ${op.deviceId}/${op.quizId}")
        val answerRows = ArrayList<JsonObject>()
        for (a in op.answers) {
            val question = ids.questions["$quiz/${a.questionId}"] ?: return Plan.MissingId("question ${op.quizId}/${a.questionId}")
            answerRows += buildJsonObject {
                put("id", a.id)
                put("attempt_id", op.id)
                put("question_id", question)
                // is_correct is deliberately absent: a server trigger computes it from the hidden answer key.
                put("selected_index", a.selectedIndex?.let { JsonPrimitive(it) } ?: JsonNull)
                put("created_at", iso(op.atMillis))
            }
        }
        return Plan.Steps(
            buildList {
                add(SyncStep.Insert("quiz_attempts", listOf(buildJsonObject {
                    put("id", op.id); put("quiz_id", quiz)
                    op.durationSec?.let { put("duration_sec", it) }
                    put("created_at", iso(op.atMillis))
                })))
                if (answerRows.isNotEmpty()) add(SyncStep.Insert("quiz_answers", answerRows))
                op.answers.filter { it.locallyCorrect }.forEach { add(award("quiz_correct", it.id)) }
            },
        )
    }

    fun award(reason: String, ref: String) = SyncStep.Rpc("award_points", buildJsonObject {
        put("p_reason", reason); put("p_ref", ref)
    })

    val checkBadges = SyncStep.Rpc("check_badges", JsonObject(emptyMap()))

    /** Converts a step to its HTTP request. user_id is omitted from inserts: the column defaults to auth.uid(). */
    fun toRequest(step: SyncStep, config: SupabaseConfig, accessToken: String): HttpRequest = when (step) {
        is SyncStep.Insert -> HttpRequest(
            "POST",
            config.restUrl("${step.table}?on_conflict=id"),
            config.headers(accessToken, mapOf("Prefer" to "resolution=ignore-duplicates,return=minimal")),
            JsonArray(step.rows).toString(),
        )

        is SyncStep.Rpc -> HttpRequest("POST", config.restUrl("rpc/${step.function}"), config.headers(accessToken), step.body.toString())
    }
}
