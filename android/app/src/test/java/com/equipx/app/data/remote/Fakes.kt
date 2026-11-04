package com.equipx.app.data.remote

import java.io.IOException

val testConfig = SupabaseConfig("https://example.supabase.co/", "anon-key")

class FakeTransport(private val handler: (HttpRequest) -> HttpResponse) : HttpTransport {
    val requests = mutableListOf<HttpRequest>()
    override suspend fun execute(request: HttpRequest): HttpResponse {
        requests += request
        return handler(request)
    }
}

fun offlineTransport() = FakeTransport { throw IOException("offline") }

fun session(expiresAt: Long, access: String = "access1", refresh: String = "refresh1") =
    AuthSession(access, refresh, expiresAt, userId = "user-1", email = "s@x.com", displayName = "Sam")

fun tokenJson(access: String, refresh: String, expiresIn: Long = 3600, name: String? = "Sam") = """
    {"access_token":"$access","token_type":"bearer","expires_in":$expiresIn,"refresh_token":"$refresh",
     "user":{"id":"user-1","email":"s@x.com","user_metadata":{${name?.let { "\"display_name\":\"$it\"" }.orEmpty()}}}}
""".trimIndent()

fun testIds() = ContentIds(
    quizzes = mapOf("dmm/basics" to "quiz-uuid"),
    questions = mapOf("quiz-uuid/q1" to "q1-uuid", "quiz-uuid/q2" to "q2-uuid"),
    procedures = mapOf("dmm/measure" to "proc-uuid"),
    faults = mapOf("dmm/no_display" to "fault-uuid"),
    options = mapOf("fault-uuid/dead_battery" to "opt-uuid"),
    missions = mapOf("dmm_first_steps" to "mission-uuid"),
    tasks = mapOf("mission-uuid/1" to "task1-uuid", "mission-uuid/2" to "task2-uuid"),
)

class FakeIdSource(var ids: ContentIds? = testIds()) : ContentIdSource {
    var refreshes = 0
    override suspend fun cached() = ids
    override suspend fun refresh(accessToken: String): ContentIds { refreshes++; return ids ?: ContentIds() }
}
