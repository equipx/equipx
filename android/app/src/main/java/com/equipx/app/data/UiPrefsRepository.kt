package com.equipx.app.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Small UI flags that should survive restarts (one-time hints). */
interface UiPrefsRepository {
    /** True once the student dismissed the "Tap the numbers" coach mark in the 3D explorer. */
    val explorerCoachDismissed: Flow<Boolean>
    suspend fun dismissExplorerCoach()

    /** The device the student last opened (detail, explorer, quiz or procedure); null before any. */
    val lastDeviceId: Flow<String?>
    suspend fun setLastDevice(deviceId: String)
}

class DataStoreUiPrefsRepository(private val store: DataStore<Preferences>) : UiPrefsRepository {
    private val coachKey = booleanPreferencesKey("explorer_coach_dismissed")
    override val explorerCoachDismissed: Flow<Boolean> = store.data.map { it[coachKey] ?: false }
    override suspend fun dismissExplorerCoach() { store.edit { it[coachKey] = true } }

    private val lastDeviceKey = stringPreferencesKey("last_device_id")
    override val lastDeviceId: Flow<String?> = store.data.map { it[lastDeviceKey] }
    override suspend fun setLastDevice(deviceId: String) { store.edit { it[lastDeviceKey] = deviceId } }
}
