package com.equipx.app.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.equipx.app.domain.Role
import com.equipx.app.domain.User
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

interface AuthRepository {
    /** Emits the logged-in user, or null when logged out. Persisted across launches. */
    val currentUser: Flow<User?>
    suspend fun login(email: String, password: String): Result<User>
    suspend fun logout()
}

class MockAuthRepository(private val store: DataStore<Preferences>) : AuthRepository {

    private data class Account(val password: String, val user: User)

    private val accounts = mapOf(
        "student@equipx.app" to Account("student123", User("student@equipx.app", "Student", Role.STUDENT)),
        "teacher@equipx.app" to Account("teacher123", User("teacher@equipx.app", "Teacher", Role.TEACHER)),
    )

    private val sessionKey = stringPreferencesKey("session_email")

    override val currentUser: Flow<User?> = store.data.map { prefs ->
        prefs[sessionKey]?.let { accounts[it]?.user }
    }

    override suspend fun login(email: String, password: String): Result<User> {
        val account = accounts[email.trim().lowercase()]
        if (account == null || account.password != password) {
            return Result.failure(IllegalArgumentException("Incorrect email or password"))
        }
        store.edit { it[sessionKey] = account.user.email }
        return Result.success(account.user)
    }

    override suspend fun logout() {
        store.edit { it.remove(sessionKey) }
    }
}
