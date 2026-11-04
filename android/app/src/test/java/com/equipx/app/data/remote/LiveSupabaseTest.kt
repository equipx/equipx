package com.equipx.app.data.remote

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

/** Network smoke test; skipped unless run with `-PliveSupabase` (config from android/supabase.properties). Creates nothing. */
class LiveSupabaseTest {
    @Test fun fakeCredentialsAreRejectedByRealAuthServer() = runBlocking {
        assumeTrue(System.getProperty("liveSupabase") == "true")
        val cfg = SupabaseConfig(System.getProperty("supabase.url").orEmpty(), System.getProperty("supabase.anonKey").orEmpty())
        assumeTrue(cfg.isConfigured)
        val api = SupabaseAuthApi(cfg, UrlConnectionTransport())
        try {
            api.signIn("nobody-${System.nanoTime()}@example.invalid", "not-a-real-password")
            throw AssertionError("fake account unexpectedly signed in")
        } catch (e: AuthException) {
            assertEquals(AuthException.Kind.REJECTED, e.kind)
            assertEquals(400, e.status)
            assertEquals("Incorrect email or password", e.message)
        }
        // PostgREST reachable with the anon key; anon has no table privileges, so a 401/403-style rejection is expected.
        val resp = UrlConnectionTransport().execute(HttpRequest("GET", cfg.restUrl("equipment?select=id&limit=1"), cfg.headers(null)))
        println("live: rest/v1/equipment as anon -> HTTP ${resp.code}")
        assertTrue(resp.code in listOf(200, 401, 403))
    }
}
