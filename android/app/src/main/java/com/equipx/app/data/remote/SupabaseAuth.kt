package com.equipx.app.data.remote

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.equipx.app.data.AuthMode
import com.equipx.app.data.AuthRepository
import com.equipx.app.data.EquipXJson
import com.equipx.app.domain.Role
import com.equipx.app.domain.User
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put

/** Persisted login. Tokens are secrets: this class never prints them. */
@Serializable
data class AuthSession(
    val accessToken: String,
    val refreshToken: String,
    /** Unix seconds when [accessToken] expires. */
    val expiresAtSec: Long,
    val userId: String,
    val email: String,
    val displayName: String = "",
    val role: String = "student",
    /** True when [role] is the default because the profile could not be read; it is re-fetched later. */
    val roleFallback: Boolean = false,
) {
    /** True when the access token is expired or about to be (within [skewSec]). */
    fun isExpired(nowSec: Long, skewSec: Long = 60): Boolean = nowSec + skewSec >= expiresAtSec

    fun toUser() = User(email, displayName.ifBlank { email.substringBefore('@') }, if (role == "teacher") Role.TEACHER else Role.STUDENT)

    override fun toString() = "AuthSession(userId=$userId, email=$email, role=$role, expiresAtSec=$expiresAtSec)"
}

class AuthException(message: String, val kind: Kind, val status: Int = 0) : Exception(message) {
    enum class Kind {
        /** Wrong credentials / bad input / user exists: show the message. */
        REJECTED,
        /** Refresh token no longer valid: the session must be dropped. */
        SESSION_INVALID,
        /** Sign-up worked but the project requires email confirmation first. */
        CONFIRM_EMAIL,
        /** Offline or server trouble: keep local state, try again later. */
        UNAVAILABLE,
    }
}

/** GoTrue + profile calls. Pure request/response logic over [HttpTransport]. */
class SupabaseAuthApi(
    private val config: SupabaseConfig,
    private val transport: HttpTransport,
    private val nowSec: () -> Long = { System.currentTimeMillis() / 1000 },
) {
    suspend fun signIn(email: String, password: String): AuthSession {
        val body = buildJsonObject { put("email", email.trim()); put("password", password) }
        return sessionFrom(post("token?grant_type=password", body), fallbackName = "")
    }

    /** Throws [AuthException.Kind.CONFIRM_EMAIL] when the project needs email confirmation (no session returned). */
    suspend fun signUp(email: String, password: String, displayName: String): AuthSession {
        val body = buildJsonObject {
            put("email", email.trim())
            put("password", password)
            put("data", buildJsonObject { put("display_name", displayName.trim()) })
        }
        val resp = post("signup", body)
        val obj = parseObject(resp.body)
        if (obj["access_token"] == null) {
            throw AuthException("Check your email to confirm your account, then sign in.", AuthException.Kind.CONFIRM_EMAIL)
        }
        return sessionFrom(resp, fallbackName = displayName.trim())
    }

    suspend fun refresh(old: AuthSession): AuthSession {
        val body = buildJsonObject { put("refresh_token", old.refreshToken) }
        val resp = try {
            post("token?grant_type=refresh_token", body)
        } catch (e: AuthException) {
            // 400/401 on refresh means the refresh token is revoked/expired/used: the session is gone.
            if (e.kind == AuthException.Kind.REJECTED) throw AuthException(e.message ?: "Session expired", AuthException.Kind.SESSION_INVALID, e.status)
            throw e
        }
        val fresh = sessionFrom(resp, fallbackName = old.displayName)
        return fresh.copy(displayName = fresh.displayName.ifBlank { old.displayName }, role = old.role, roleFallback = old.roleFallback)
    }

    /** Best-effort server-side sign out (revokes the refresh token). Errors are ignored by callers. */
    suspend fun signOut(session: AuthSession) {
        transport.execute(HttpRequest("POST", config.authUrl("logout"), config.headers(session.accessToken), "{}"))
    }

    /** Reads own profile (RLS: read own). Returns (role, displayName) or null on any failure. */
    suspend fun fetchProfile(session: AuthSession): Pair<String, String>? = try {
        val resp = transport.execute(
            HttpRequest("GET", config.restUrl("profiles?select=role,display_name&id=eq.${session.userId}"), config.headers(session.accessToken)),
        )
        if (!resp.isSuccess) null else {
            val row = (EquipXJson.parseToJsonElement(resp.body) as? JsonArray)?.firstOrNull() as? JsonObject
            row?.let { (it["role"]?.jsonPrimitive?.contentOrNull ?: "student") to (it["display_name"]?.jsonPrimitive?.contentOrNull ?: "") }
        }
    } catch (_: Exception) {
        null
    }

    private suspend fun post(path: String, body: JsonObject): HttpResponse {
        val resp = try {
            transport.execute(HttpRequest("POST", config.authUrl(path), config.headers(null), body.toString()))
        } catch (e: IOException) {
            throw AuthException("No connection. Check your network and try again.", AuthException.Kind.UNAVAILABLE)
        }
        if (resp.isSuccess) return resp
        val kind = if (resp.code >= 500 || resp.code == 429) AuthException.Kind.UNAVAILABLE else AuthException.Kind.REJECTED
        throw AuthException(errorMessage(resp), kind, resp.code)
    }

    private fun errorMessage(resp: HttpResponse): String {
        val obj = runCatching { parseObject(resp.body) }.getOrNull()
        val raw = listOf("error_description", "msg", "message", "error")
            .firstNotNullOfOrNull { obj?.get(it)?.jsonPrimitive?.contentOrNull }
        return when {
            raw == null -> "Request failed (${resp.code})"
            raw.contains("Invalid login credentials", ignoreCase = true) -> "Incorrect email or password"
            raw.contains("already registered", ignoreCase = true) -> "An account with this email already exists"
            else -> raw
        }
    }

    private fun parseObject(text: String): JsonObject = EquipXJson.parseToJsonElement(text).jsonObject

    private fun sessionFrom(resp: HttpResponse, fallbackName: String): AuthSession {
        val obj = try {
            parseObject(resp.body)
        } catch (e: Exception) {
            throw AuthException("Unexpected server response", AuthException.Kind.UNAVAILABLE, resp.code)
        }
        val access = obj["access_token"]?.jsonPrimitive?.contentOrNull
        val refresh = obj["refresh_token"]?.jsonPrimitive?.contentOrNull
        val user = obj["user"] as? JsonObject
        val id = user?.get("id")?.jsonPrimitive?.contentOrNull
        if (access == null || refresh == null || id == null) {
            throw AuthException("Unexpected server response", AuthException.Kind.UNAVAILABLE, resp.code)
        }
        val expiresAt = obj["expires_at"]?.jsonPrimitive?.longOrNull
            ?: (nowSec() + (obj["expires_in"]?.jsonPrimitive?.longOrNull ?: 3600L))
        val meta = user["user_metadata"] as? JsonObject
        val name = meta?.get("display_name")?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() } ?: fallbackName
        return AuthSession(
            accessToken = access,
            refreshToken = refresh,
            expiresAtSec = expiresAt,
            userId = id,
            email = user["email"]?.jsonPrimitive?.contentOrNull.orEmpty(),
            displayName = name,
        )
    }
}

interface SessionStore {
    val session: Flow<AuthSession?>
    suspend fun save(session: AuthSession?)
}

class DataStoreSessionStore(private val store: DataStore<Preferences>) : SessionStore {
    private val key = stringPreferencesKey("supabase_session_json")

    override val session: Flow<AuthSession?> = store.data
        .map { p -> p[key]?.let { runCatching { EquipXJson.decodeFromString(AuthSession.serializer(), it) }.getOrNull() } }
        .distinctUntilChanged()

    override suspend fun save(session: AuthSession?) {
        store.edit {
            if (session == null) it.remove(key) else it[key] = EquipXJson.encodeToString(AuthSession.serializer(), session)
        }
    }
}

class InMemorySessionStore(initial: AuthSession? = null) : SessionStore {
    private val state = MutableStateFlow(initial)
    override val session: Flow<AuthSession?> = state
    override suspend fun save(session: AuthSession?) { state.value = session }
}

/** Gives callers a usable access token, refreshing when it is (nearly) expired. Offline keeps the session. */
interface TokenProvider {
    /** Signed-in user id without any network (null when signed out). */
    suspend fun userId(): String?

    /** A session whose access token is valid now, or null when signed out / refresh impossible right now. */
    suspend fun validSession(forceRefresh: Boolean = false): AuthSession?
}

class SessionManager(
    private val api: SupabaseAuthApi,
    private val store: SessionStore,
    private val nowSec: () -> Long = { System.currentTimeMillis() / 1000 },
) : TokenProvider {
    private val mutex = Mutex()
    val session: Flow<AuthSession?> get() = store.session

    override suspend fun userId(): String? = store.session.first()?.userId

    override suspend fun validSession(forceRefresh: Boolean): AuthSession? = mutex.withLock {
        val current = store.session.first() ?: return@withLock null
        if (!forceRefresh && !current.isExpired(nowSec())) {
            if (!current.roleFallback) return@withLock current
            return@withLock withProfile(current).also { if (it != current) store.save(it) }
        }
        try {
            val fresh = withProfile(api.refresh(current))
            store.save(fresh)
            fresh
        } catch (e: AuthException) {
            if (e.kind == AuthException.Kind.SESSION_INVALID) store.save(null)
            null
        } catch (e: IOException) {
            // Offline: keep the (expired) session so the user stays signed in and local data keeps working.
            null
        }
    }

    /** Re-reads the profile role/name; keeps the old values when the fetch fails. */
    private suspend fun withProfile(s: AuthSession): AuthSession =
        api.fetchProfile(s)?.let { (role, name) -> s.copy(role = role, roleFallback = false, displayName = s.displayName.ifBlank { name }) } ?: s

    /** The stored session as is, without any refresh or network. */
    suspend fun current(): AuthSession? = store.session.first()

    suspend fun establish(session: AuthSession) = mutex.withLock { store.save(session) }
    suspend fun clear() = mutex.withLock { store.save(null) }
}

class SupabaseAuthRepository(
    private val api: SupabaseAuthApi,
    private val sessions: SessionManager,
    /** Runs before sign-out clears the session (e.g. a last sync attempt). Must not throw. */
    private val beforeLogout: suspend () -> Unit = {},
) : AuthRepository {
    override val mode = AuthMode.SUPABASE
    override val currentUser: Flow<User?> = sessions.session.map { it?.toUser() }.distinctUntilChanged()

    override suspend fun login(email: String, password: String): Result<User> = runAuth {
        api.signIn(email, password)
    }

    override suspend fun register(email: String, password: String, displayName: String): Result<User> = runAuth {
        api.signUp(email, password, displayName)
    }

    private suspend fun runAuth(block: suspend () -> AuthSession): Result<User> = try {
        var s = block()
        val profile = api.fetchProfile(s)
        s = if (profile != null) s.copy(role = profile.first, displayName = s.displayName.ifBlank { profile.second }) else s.copy(roleFallback = true)
        sessions.establish(s)
        Result.success(s.toUser())
    } catch (e: AuthException) {
        Result.failure(e)
    } catch (e: IOException) {
        Result.failure(AuthException("No connection. Check your network and try again.", AuthException.Kind.UNAVAILABLE))
    }

    private companion object {
        const val LOGOUT_STEP_TIMEOUT_MS = 4_000L
    }

    override suspend fun logout() {
        try {
            try {
                withTimeoutOrNull(LOGOUT_STEP_TIMEOUT_MS) { beforeLogout() }
                // Current token as is (no refresh): if it is expired the server call just fails and the session is cleared anyway.
                sessions.current()?.let { s -> withTimeoutOrNull(LOGOUT_STEP_TIMEOUT_MS) { api.signOut(s) } }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // best effort
            }
        } finally {
            withContext(NonCancellable) { sessions.clear() }
        }
    }
}
