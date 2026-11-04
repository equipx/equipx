package com.equipx.app.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import com.equipx.app.domain.QuizAttempt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.serialization.builtins.ListSerializer

interface ProgressRepository {
    /** Set of "deviceId:partId" the user has viewed. */
    val viewedParts: Flow<Set<String>>
    val points: Flow<Int>

    /** Set of "deviceId:procedureId" the user has completed. */
    val completedProcedures: Flow<Set<String>>

    /** All saved quiz attempts, oldest first. */
    val quizAttempts: Flow<List<QuizAttempt>>

    /** Returns true when this was the first time the part was viewed. */
    suspend fun markPartViewed(deviceId: String, partId: String): Boolean
    suspend fun addPoints(amount: Int)

    /** Returns true when this was the first completion of the procedure. */
    suspend fun markProcedureCompleted(deviceId: String, procedureId: String): Boolean
    suspend fun saveQuizAttempt(attempt: QuizAttempt)
}

class DataStoreProgressRepository(private val store: DataStore<Preferences>) : ProgressRepository {
    private val viewedKey = stringSetPreferencesKey("viewed_parts")
    private val pointsKey = intPreferencesKey("points")
    private val proceduresKey = stringSetPreferencesKey("completed_procedures")
    private val attemptsKey = stringPreferencesKey("quiz_attempts_json")
    private val attemptsSerializer = ListSerializer(QuizAttempt.serializer())

    override val viewedParts: Flow<Set<String>> = store.data.map { it[viewedKey] ?: emptySet() }
    override val points: Flow<Int> = store.data.map { it[pointsKey] ?: 0 }
    override val completedProcedures: Flow<Set<String>> = store.data.map { it[proceduresKey] ?: emptySet() }
    override val quizAttempts: Flow<List<QuizAttempt>> = store.data.map { it[attemptsKey] }.distinctUntilChanged().map { decodeAttempts(it) }.flowOn(Dispatchers.Default)

    private fun decodeAttempts(json: String?): List<QuizAttempt> =
        if (json.isNullOrBlank()) emptyList()
        else runCatching { EquipXJson.decodeFromString(attemptsSerializer, json) }.getOrDefault(emptyList())

    override suspend fun markPartViewed(deviceId: String, partId: String): Boolean {
        var first = false
        store.edit { prefs ->
            val current = prefs[viewedKey] ?: emptySet()
            val key = "$deviceId:$partId"
            if (key !in current) {
                first = true
                prefs[viewedKey] = current + key
                prefs[pointsKey] = (prefs[pointsKey] ?: 0) + FIRST_PART_VIEW_POINTS
            }
        }
        return first
    }

    override suspend fun addPoints(amount: Int) {
        store.edit { it[pointsKey] = (it[pointsKey] ?: 0) + amount }
    }

    override suspend fun markProcedureCompleted(deviceId: String, procedureId: String): Boolean {
        var first = false
        store.edit { prefs ->
            val current = prefs[proceduresKey] ?: emptySet()
            val key = "$deviceId:$procedureId"
            if (key !in current) {
                first = true
                prefs[proceduresKey] = current + key
            }
        }
        return first
    }

    override suspend fun saveQuizAttempt(attempt: QuizAttempt) {
        store.edit { prefs ->
            val updated = (decodeAttempts(prefs[attemptsKey]) + attempt).takeLast(MAX_ATTEMPTS)
            prefs[attemptsKey] = EquipXJson.encodeToString(attemptsSerializer, updated)
        }
    }

    private companion object {
        const val FIRST_PART_VIEW_POINTS = 2
        const val MAX_ATTEMPTS = 200
    }
}
