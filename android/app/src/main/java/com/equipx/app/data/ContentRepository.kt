package com.equipx.app.data

import android.content.res.AssetManager
import com.equipx.app.domain.Device
import com.equipx.app.domain.DeviceSummary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

interface ContentRepository {
    /** Device list; failure when `content/index.json` is missing or invalid. */
    suspend fun getIndex(): Result<List<DeviceSummary>>

    /** Full content for one device; failure when missing or invalid. */
    suspend fun getDevice(id: String): Result<Device>

    /** True when the bundled asset at [path] (relative to `assets/`, e.g. `models/x.glb`) exists. */
    suspend fun assetExists(path: String): Boolean
}

/** Reads `assets/content` JSON files (synced from /content; read-only for the app). */
class AssetContentRepository(private val assets: AssetManager) : ContentRepository {

    private fun read(path: String): String =
        assets.open(path).bufferedReader().use { it.readText() }

    override suspend fun getIndex(): Result<List<DeviceSummary>> = withContext(Dispatchers.IO) {
        runCatching { ContentParser.parseIndex(read("content/index.json")).devices }
    }

    override suspend fun getDevice(id: String): Result<Device> = withContext(Dispatchers.IO) {
        runCatching { ContentParser.parseDevice(read("content/$id.json")) }
    }

    override suspend fun assetExists(path: String): Boolean = withContext(Dispatchers.IO) {
        runCatching { assets.open(path).use { true } }.getOrDefault(false)
    }
}
