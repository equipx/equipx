package com.equipx.app.data

import com.equipx.app.domain.Device
import com.equipx.app.domain.DeviceIndex
import com.equipx.app.domain.HotspotsFile
import kotlinx.serialization.json.Json

/** Shared JSON configuration; unknown keys are ignored so content can evolve ahead of the app. */
val EquipXJson = Json {
    ignoreUnknownKeys = true
    isLenient = true
    coerceInputValues = true
}

object ContentParser {
    fun parseIndex(text: String): DeviceIndex = EquipXJson.decodeFromString(DeviceIndex.serializer(), text)
    fun parseDevice(text: String): Device = EquipXJson.decodeFromString(Device.serializer(), text)
    fun parseHotspots(text: String): HotspotsFile = EquipXJson.decodeFromString(HotspotsFile.serializer(), text)
}
