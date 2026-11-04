package com.equipx.app.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Small UI flags that should survive restarts (one-time hints). */
interface UiPrefsRepository {
    /** True once the student dismissed the "Tap the numbers" coach mark in the 3D explorer. */
    val explorerCoachDismissed: Flow<Boolean>
    suspend fun dismissExplorerCoach()
}

class DataStoreUiPrefsRepository(private val store: DataStore<Preferences>) : UiPrefsRepository {
    private val coachKey = booleanPreferencesKey("explorer_coach_dismissed")
    override val explorerCoachDismissed: Flow<Boolean> = store.data.map { it[coachKey] ?: false }
    override suspend fun dismissExplorerCoach() { store.edit { it[coachKey] = true } }
}
