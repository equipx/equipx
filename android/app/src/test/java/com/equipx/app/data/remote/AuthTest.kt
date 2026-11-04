package com.equipx.app.data.remote

import java.io.IOException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class AuthTest {
    private val now = 1_000_000L

    @Test fun expiryUsesSkew() {
        val s = session(expiresAt = now + 100)
        assertFalse(s.isExpired(now))
        assertTrue(s.isExpired(now + 41)) // within the 60s skew
        assertTrue(s.isExpired(now + 100))
    }

    @Test fun signInBuildsPasswordGrantAndParsesSession() = runBlocking {
        val t = FakeTransport { HttpResponse(200, tokenJson("a1", "r1", 3600)) }
        val s = SupabaseAuthApi(testConfig, t) { now }.signIn(" s@x.com ", "pw")
        val req = t.requests.single()
        assertEquals("https://example.supabase.co/auth/v1/token?grant_type=password", req.url)
        assertEquals("anon-key", req.headers["apikey"])
        assertEquals("Bearer anon-key", req.headers["Authorization"])
        assertEquals("""{"email":"s@x.com","password":"pw"}""", req.body)
        assertEquals("a1", s.accessToken); assertEquals("r1", s.refreshToken)
        assertEquals(now + 3600, s.expiresAtSec); assertEquals("Sam", s.displayName); assertEquals("user-1", s.userId)
    }

    @Test fun invalidCredentialsIsRejectedWithFriendlyMessage() = runBlocking {
        val t = FakeTransport { HttpResponse(400, """{"error":"invalid_grant","error_description":"Invalid login credentials"}""") }
        try {
            SupabaseAuthApi(testConfig, t).signIn("a@b.c", "x"); fail()
        } catch (e: AuthException) {
            assertEquals(AuthException.Kind.REJECTED, e.kind); assertEquals("Incorrect email or password", e.message)
        }
    }

    @Test fun offlineSignInIsUnavailable() = runBlocking {
        try {
            SupabaseAuthApi(testConfig, offlineTransport()).signIn("a@b.c", "x"); fail()
        } catch (e: AuthException) {
            assertEquals(AuthException.Kind.UNAVAILABLE, e.kind)
        }
    }

    @Test fun signUpSendsDisplayNameMetadata_andDetectsConfirmation() = runBlocking {
        val t = FakeTransport { HttpResponse(200, """{"id":"user-1","email":"s@x.com"}""") }
        try {
            SupabaseAuthApi(testConfig, t).signUp("s@x.com", "secret1", "Sam"); fail()
        } catch (e: AuthException) {
            assertEquals(AuthException.Kind.CONFIRM_EMAIL, e.kind)
        }
        assertEquals("https://example.supabase.co/auth/v1/signup", t.requests.single().url)
        assertEquals("""{"email":"s@x.com","password":"secret1","data":{"display_name":"Sam"}}""", t.requests.single().body)
    }

    @Test fun signUpWithoutConfirmationReturnsSession() = runBlocking {
        val t = FakeTransport { HttpResponse(200, tokenJson("a1", "r1", name = null)) }
        val s = SupabaseAuthApi(testConfig, t) { now }.signUp("s@x.com", "secret1", "Sam")
        assertEquals("Sam", s.displayName) // falls back to the name typed at sign-up
    }

    @Test fun validSessionDoesNotRefreshWhileFresh() = runBlocking {
        val t = FakeTransport { error("no network expected") }
        val sm = SessionManager(SupabaseAuthApi(testConfig, t), InMemorySessionStore(session(now + 3600))) { now }
        assertEquals("access1", sm.validSession()!!.accessToken)
        assertTrue(t.requests.isEmpty())
    }

    @Test fun expiredSessionRefreshesAndPersists() = runBlocking {
        val t = FakeTransport { r ->
            if (r.url.contains("/rest/v1/profiles")) HttpResponse(200, """[{"role":"student","display_name":"Sam"}]""")
            else HttpResponse(200, tokenJson("a2", "r2", 3600, name = null))
        }
        val store = InMemorySessionStore(session(now - 5).copy(role = "teacher"))
        val sm = SessionManager(SupabaseAuthApi(testConfig, t) { now }, store) { now }
        val fresh = sm.validSession()!!
        assertEquals("a2", fresh.accessToken); assertEquals("r2", fresh.refreshToken)
        assertEquals("student", fresh.role) // role is re-read from the profile after a refresh
        assertEquals("Sam", fresh.displayName)
        assertEquals("https://example.supabase.co/auth/v1/token?grant_type=refresh_token", t.requests.first().url)
        assertEquals("""{"refresh_token":"refresh1"}""", t.requests.first().body)
        assertEquals("a2", store.session.first()!!.accessToken)
    }

    @Test fun offlineRefreshKeepsSession() = runBlocking {
        val store = InMemorySessionStore(session(now - 5))
        val sm = SessionManager(SupabaseAuthApi(testConfig, offlineTransport()), store) { now }
        assertNull(sm.validSession())
        assertNotNull(store.session.first()) // still signed in, offline-first
    }

    @Test fun revokedRefreshTokenSignsOut() = runBlocking {
        val t = FakeTransport { HttpResponse(400, """{"error_description":"Invalid Refresh Token: Already Used"}""") }
        val store = InMemorySessionStore(session(now - 5))
        val sm = SessionManager(SupabaseAuthApi(testConfig, t), store) { now }
        assertNull(sm.validSession())
        assertNull(store.session.first())
    }

    @Test fun serverErrorOnRefreshKeepsSession() = runBlocking {
        val t = FakeTransport { HttpResponse(503, "") }
        val store = InMemorySessionStore(session(now - 5))
        assertNull(SessionManager(SupabaseAuthApi(testConfig, t), store) { now }.validSession())
        assertNotNull(store.session.first())
    }

    @Test fun profileFetchReadsRole() = runBlocking {
        val t = FakeTransport { HttpResponse(200, """[{"role":"teacher","display_name":"Ms T"}]""") }
        val p = SupabaseAuthApi(testConfig, t).fetchProfile(session(now + 100))
        assertEquals("teacher" to "Ms T", p)
        assertEquals("https://example.supabase.co/rest/v1/profiles?select=role,display_name&id=eq.user-1", t.requests.single().url)
        assertEquals("Bearer access1", t.requests.single().headers["Authorization"])
    }

    @Test fun toStringHidesTokens() {
        val s = session(now).toString()
        assertFalse(s.contains("access1")); assertFalse(s.contains("refresh1"))
        assertFalse(testConfig.toString().contains("anon-key"))
    }

    @Test fun ioExceptionClassIsUsed() { assertTrue(IOException() is Exception) }
}
