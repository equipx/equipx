package com.equipx.app.data.remote

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SyncTest {
    private val at = 1_700_000_000_000L // 2023-11-14T22:13:20Z
    private val ids = testIds()

    private fun steps(op: OutboxOp): List<SyncStep> = (SyncRequests.plan(op, ids) as Plan.Steps).steps
    private fun insert(op: OutboxOp, i: Int = 0) = steps(op)[i] as SyncStep.Insert
    private fun JsonObject.keys() = keys.toSet()

    // ---- JSON shape vs migration columns -------------------------------------------------------------------------

    @Test fun scanRowMatchesColumns() {
        val ins = insert(OutboxOp.Scan("scan-1", "user-1", at, "dmm"))
        assertEquals("scans", ins.table)
        val row = ins.rows.single()
        assertEquals(setOf("id", "equipment_id", "created_at"), row.keys()) // user_id defaults to auth.uid()
        assertEquals("scan-1", row["id"]!!.jsonPrimitive.content)
        assertEquals("dmm", row["equipment_id"]!!.jsonPrimitive.content)
        assertEquals("2023-11-14T22:13:20Z", row["created_at"]!!.jsonPrimitive.content)
        // award: ref is scans.id
        assertEquals("""{"p_reason":"scan","p_ref":"scan-1"}""", (steps(OutboxOp.Scan("scan-1", "u", at, "dmm"))[1] as SyncStep.Rpc).body.toString())
    }

    @Test fun partViewRowAndAwardRef() {
        val op = OutboxOp.PartView("pv-1", "u", at, "dmm", "com_jack")
        assertEquals(setOf("id", "equipment_id", "part_id", "created_at"), insert(op).rows.single().keys())
        assertEquals("part_views", insert(op).table)
        assertEquals("""{"p_reason":"first_part_view","p_ref":"dmm:com_jack"}""", (steps(op)[1] as SyncStep.Rpc).body.toString())
    }

    @Test fun quizAttemptAndAnswersMatchColumns_noIsCorrect() {
        val op = OutboxOp.QuizAttempt(
            "att-1", "u", at, "dmm", "basics", 42,
            listOf(
                OutboxOp.QuizAnswerRow("ans-1", "q1", 2, true),
                OutboxOp.QuizAnswerRow("ans-2", "q2", null, false),
            ),
        )
        val s = steps(op)
        val attempt = s[0] as SyncStep.Insert
        assertEquals("quiz_attempts", attempt.table)
        assertEquals(setOf("id", "quiz_id", "duration_sec", "created_at"), attempt.rows.single().keys())
        assertEquals("quiz-uuid", attempt.rows.single()["quiz_id"]!!.jsonPrimitive.content)
        val answers = s[1] as SyncStep.Insert
        assertEquals("quiz_answers", answers.table)
        assertEquals(2, answers.rows.size)
        answers.rows.forEach { assertEquals(setOf("id", "attempt_id", "question_id", "selected_index", "created_at"), it.keys()) }
        assertEquals("att-1", answers.rows[0]["attempt_id"]!!.jsonPrimitive.content)
        assertEquals("q1-uuid", answers.rows[0]["question_id"]!!.jsonPrimitive.content)
        assertEquals(2, answers.rows[0]["selected_index"]!!.jsonPrimitive.content.toInt())
        assertEquals(JsonNull, answers.rows[1]["selected_index"]) // timed out => null
        // Only the locally-correct answer asks for points; ref = quiz_answers.id
        val rpcs = s.filterIsInstance<SyncStep.Rpc>()
        assertEquals(listOf("""{"p_reason":"quiz_correct","p_ref":"ans-1"}"""), rpcs.map { it.body.toString() })
    }

    @Test fun faultAttemptMapsOptionAndOnlyAwardsWhenCorrect() {
        val ok = OutboxOp.FaultAttempt("fa-1", "u", at, "dmm", "no_display", "dead_battery", true)
        val row = insert(ok).rows.single()
        assertEquals("fault_attempts", insert(ok).table)
        assertEquals(setOf("id", "scenario_id", "option_id", "created_at"), row.keys()) // no is_correct
        assertEquals("fault-uuid", row["scenario_id"]!!.jsonPrimitive.content)
        assertEquals("opt-uuid", row["option_id"]!!.jsonPrimitive.content)
        assertEquals("""{"p_reason":"fault_fixed","p_ref":"fa-1"}""", (steps(ok)[1] as SyncStep.Rpc).body.toString())
        assertEquals(1, steps(ok.copy(locallyCorrect = false)).size)
    }

    @Test fun procedureRow() {
        val op = OutboxOp.Procedure("pc-1", "u", at, "dmm", "measure")
        val ins = insert(op)
        assertEquals("procedure_completions", ins.table)
        assertEquals(setOf("id", "procedure_id", "created_at"), ins.rows.single().keys()) // duration omitted when unknown
        assertEquals("proc-uuid", ins.rows.single()["procedure_id"]!!.jsonPrimitive.content)
        assertTrue("duration_sec" in insert(op.copy(durationSec = 9)).rows.single().keys())
    }

    @Test fun missionProgressAndAward() {
        val t = OutboxOp.MissionTask("mp-1", "u", at, "dmm_first_steps", 2)
        val ins = insert(t)
        assertEquals("mission_progress", ins.table)
        assertEquals(setOf("id", "mission_id", "task_id", "completed_at"), ins.rows.single().keys())
        assertEquals("mission-uuid", ins.rows.single()["mission_id"]!!.jsonPrimitive.content)
        assertEquals("task2-uuid", ins.rows.single()["task_id"]!!.jsonPrimitive.content)
        val award = steps(OutboxOp.MissionAward("ma-1", "u", at, "dmm_first_steps")).single() as SyncStep.Rpc
        assertEquals("""{"p_reason":"mission_complete","p_ref":"mission-uuid"}""", award.body.toString())
    }

    @Test fun missingServerIdIsReported() {
        val p = SyncRequests.plan(OutboxOp.Procedure("x", "u", at, "dmm", "nope"), ids)
        assertTrue(p is Plan.MissingId)
    }

    @Test fun httpRequestShape() {
        val step = insert(OutboxOp.Scan("scan-1", "u", at, "dmm"))
        val r = SyncRequests.toRequest(step, testConfig, "tok")
        assertEquals("POST", r.method)
        assertEquals("https://example.supabase.co/rest/v1/scans?on_conflict=id", r.url)
        assertEquals("resolution=ignore-duplicates,return=minimal", r.headers["Prefer"])
        assertEquals("Bearer tok", r.headers["Authorization"])
        assertTrue(Json.parseToJsonElement(r.body!!) is JsonArray)
        val rpc = SyncRequests.toRequest(SyncRequests.checkBadges, testConfig, "tok")
        assertEquals("https://example.supabase.co/rest/v1/rpc/check_badges", rpc.url)
        assertEquals("{}", rpc.body)
    }

    // ---- outbox serialization / idempotency ----------------------------------------------------------------------

    private val allOps: List<OutboxOp> = listOf(
        OutboxOp.Scan("1", "u", at, "dmm"),
        OutboxOp.PartView("2", "u", at, "dmm", "com_jack"),
        OutboxOp.QuizAttempt("3", "u", at, "dmm", "basics", null, listOf(OutboxOp.QuizAnswerRow("3a", "q1", null, false))),
        OutboxOp.FaultAttempt("4", "u", at, "dmm", "no_display", "dead_battery", true),
        OutboxOp.Procedure("5", "u", at, "dmm", "measure", 12),
        OutboxOp.MissionTask("6", "u", at, "m", 1),
        OutboxOp.MissionAward("7", "u", at, "m"),
    )

    @Test fun outboxRoundTripsEveryOpType() {
        val state = OutboxState(allOps, badgeCheckPending = true, droppedCount = 2, lastError = "x")
        assertEquals(state, OutboxCodec.decode(OutboxCodec.encode(state)))
    }

    @Test fun corruptOutboxDecodesToNull_emptyIsEmpty() {
        assertEquals(null, OutboxCodec.decode("{not json"))
        assertEquals(OutboxState(), OutboxCodec.decode(null))
    }

    @Test fun enqueueingSameIdTwiceKeepsOne() = runBlocking {
        val store = InMemoryOutboxStore()
        val id = com.equipx.app.data.remote.OutboxActivitySink.missionTaskId("u", "m", 1)
        repeat(2) { store.update { it + OutboxOp.MissionTask(id, "u", at, "m", 1) } }
        assertEquals(1, store.read().ops.size)
        assertEquals(id, OutboxActivitySink.missionTaskId("u", "m", 1)) // deterministic
        assertTrue(id != OutboxActivitySink.missionTaskId("u", "m", 2))
    }

    // ---- sync engine ---------------------------------------------------------------------------------------------

    private fun okSession() = InMemorySessionStore(session(expiresAt = 4_000_000_000L))
    private fun tokens(store: SessionStore = okSession()) =
        SessionManager(SupabaseAuthApi(testConfig, FakeTransport { error("unexpected auth call") }), store) { 1_000L }

    private fun engine(t: HttpTransport, outbox: OutboxStore, idSource: ContentIdSource = FakeIdSource(), tp: TokenProvider = tokens()) =
        ActivitySync(testConfig, t, tp, outbox, idSource)

    @Test fun drainUploadsInOrderThenChecksBadgesAndEmptiesOutbox() = runBlocking {
        val outbox = InMemoryOutboxStore(OutboxState(listOf(
            OutboxOp.Scan("scan-1", "user-1", at, "dmm"),
            OutboxOp.Procedure("pc-1", "user-1", at, "dmm", "measure"),
        )))
        val t = FakeTransport { HttpResponse(201, "") }
        assertEquals(DrainResult.Idle, engine(t, outbox).drainOnce())
        assertEquals(
            listOf("rest/v1/scans", "rest/v1/rpc/award_points", "rest/v1/procedure_completions", "rest/v1/rpc/check_badges"),
            t.requests.map { it.url.substringAfter(".co/").substringBefore('?') },
        )
        val s = outbox.read()
        assertTrue(s.ops.isEmpty()); assertEquals(false, s.badgeCheckPending)
    }

    @Test fun otherUsersOpsAreNotUploaded() = runBlocking {
        val outbox = InMemoryOutboxStore(OutboxState(listOf(OutboxOp.Scan("s", "someone-else", at, "dmm"))))
        val t = FakeTransport { error("no request expected") }
        assertEquals(DrainResult.Idle, engine(t, outbox).drainOnce())
        assertEquals(1, outbox.read().ops.size)
    }

    @Test fun offlineKeepsEverythingAndRetries() = runBlocking {
        val outbox = InMemoryOutboxStore(OutboxState(listOf(OutboxOp.Scan("scan-1", "user-1", at, "dmm"))))
        assertTrue(engine(offlineTransport(), outbox).drainOnce() is DrainResult.Retry)
        assertEquals(1, outbox.read().ops.size)
    }

    @Test fun serverErrorIsTransientAndKeepsOp() = runBlocking {
        val outbox = InMemoryOutboxStore(OutboxState(listOf(OutboxOp.Scan("scan-1", "user-1", at, "dmm"))))
        val t = FakeTransport { HttpResponse(503, "") }
        assertTrue(engine(t, outbox).drainOnce() is DrainResult.Retry)
        assertEquals(1, outbox.read().ops.size)
    }

    @Test fun duplicateInsertCountsAsUploaded() = runBlocking {
        val outbox = InMemoryOutboxStore(OutboxState(listOf(OutboxOp.Scan("scan-1", "user-1", at, "dmm"))))
        val t = FakeTransport { r ->
            if (r.url.contains("/scans")) HttpResponse(409, """{"code":"23505","message":"duplicate key"}""") else HttpResponse(200, "0")
        }
        assertEquals(DrainResult.Idle, engine(t, outbox).drainOnce())
        assertTrue(outbox.read().ops.isEmpty())
    }

    @Test fun permanentInsertRejectionDropsOnlyThatOp() = runBlocking {
        val outbox = InMemoryOutboxStore(OutboxState(listOf(
            OutboxOp.Scan("bad", "user-1", at, "unknown_device"),
            OutboxOp.Scan("good", "user-1", at, "dmm"),
        )))
        val t = FakeTransport { r ->
            if (r.body.orEmpty().contains("unknown_device")) HttpResponse(409, """{"code":"23503","message":"fk violation"}""")
            else HttpResponse(201, "")
        }
        assertEquals(DrainResult.Idle, engine(t, outbox).drainOnce())
        val s = outbox.read()
        assertTrue(s.ops.isEmpty()); assertEquals(1, s.droppedCount); assertTrue(s.lastError!!.contains("23503"))
    }

    @Test fun rejectedAwardDoesNotDropTheUploadedRow() = runBlocking {
        val outbox = InMemoryOutboxStore(OutboxState(listOf(OutboxOp.Scan("scan-1", "user-1", at, "dmm"))))
        val t = FakeTransport { r ->
            if (r.url.contains("award_points")) HttpResponse(404, """{"code":"P0002","message":"scan not found"}""") else HttpResponse(201, "")
        }
        assertEquals(DrainResult.Idle, engine(t, outbox).drainOnce())
        assertEquals(0, outbox.read().droppedCount)
    }

    @Test fun unknownContentIdTriggersOneRefreshThenDrops() = runBlocking {
        val outbox = InMemoryOutboxStore(OutboxState(listOf(OutboxOp.Procedure("p", "user-1", at, "dmm", "gone"))))
        val src = FakeIdSource()
        val t = FakeTransport { HttpResponse(201, "") }
        assertEquals(DrainResult.Idle, engine(t, outbox, src).drainOnce())
        assertEquals(1, src.refreshes)
        assertEquals(1, outbox.read().droppedCount)
        assertTrue(t.requests.none { it.url.contains("procedure_completions") })
    }

    @Test fun unauthorizedForcesRefreshOnceAndRetries() = runBlocking {
        val outbox = InMemoryOutboxStore(OutboxState(listOf(OutboxOp.Procedure("pc-1", "user-1", at, "dmm", "measure"))))
        val store = okSession()
        var refreshed = false
        val api = FakeTransport { r ->
            if (r.url.contains("/auth/v1/token")) { refreshed = true; HttpResponse(200, tokenJson("fresh", "r2", name = null)) }
            else if (r.headers["Authorization"] == "Bearer access1") HttpResponse(401, """{"message":"JWT expired"}""")
            else HttpResponse(201, "")
        }
        val sm = SessionManager(SupabaseAuthApi(testConfig, api) { 1_000L }, store) { 1_000L }
        assertEquals(DrainResult.Idle, ActivitySync(testConfig, api, sm, outbox, FakeIdSource()).drainOnce())
        assertTrue(refreshed)
        assertTrue(outbox.read().ops.isEmpty())
    }

    @Test fun signedOutDoesNothing() = runBlocking {
        val outbox = InMemoryOutboxStore(OutboxState(listOf(OutboxOp.Scan("s", "user-1", at, "dmm"))))
        val t = FakeTransport { error("no request expected") }
        assertEquals(DrainResult.NotSignedIn, engine(t, outbox, tp = tokens(InMemorySessionStore(null))).drainOnce())
        assertEquals(1, outbox.read().ops.size)
    }

    @Test fun backoffDoublesAndCaps() {
        assertEquals(listOf(2_000L, 4_000L, 8_000L), (0..2).map { Backoff.delayMs(it) })
        assertEquals(300_000L, Backoff.delayMs(50))
    }
}
