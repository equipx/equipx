package com.equipx.app.ui.explorer

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext

/**
 * "Place in AR" via Google Scene Viewer (T6.5). Scene Viewer needs a web-hosted GLB, so the models are served publicly
 * from the GitHub repo through jsDelivr (this replaced the earlier Supabase Storage plan). Only the model file is
 * fetched remotely; everything else in the app stays offline-first.
 */
object ArConfig {
    /** Single place to change where the AR models are hosted. File name is `<deviceId>.glb`. */
    const val MODELS_BASE_URL = "https://cdn.jsdelivr.net/gh/equipx/equipx@main/admin/public/models/"
    const val SCENE_VIEWER_URL = "https://arvr.google.com/scene-viewer/1.0"
    const val GOOGLE_APP_PACKAGE = "com.google.android.googlequicksearchbox"

    fun modelUrl(deviceId: String) = "$MODELS_BASE_URL$deviceId.glb"

    fun sceneViewerUri(deviceId: String, title: String, mode: String): Uri =
        Uri.parse(SCENE_VIEWER_URL).buildUpon()
            .appendQueryParameter("file", modelUrl(deviceId))
            .appendQueryParameter("mode", mode)
            .appendQueryParameter("title", title)
            .build()
}

/** Opens Scene Viewer in AR; falls back to 3D-only, then to any browser, when the Google app is unavailable. */
fun launchPlaceInAr(context: Context, deviceId: String, title: String): Boolean {
    fun view(mode: String, withPackage: Boolean) = Intent(Intent.ACTION_VIEW, ArConfig.sceneViewerUri(deviceId, title, mode)).apply {
        if (withPackage) setPackage(ArConfig.GOOGLE_APP_PACKAGE)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    val attempts = listOf(view("ar_preferred", true), view("3d_only", true), view("3d_only", false))
    for (intent in attempts) {
        try {
            context.startActivity(intent)
            return true
        } catch (_: ActivityNotFoundException) {
            // try the next fallback
        } catch (_: SecurityException) {
        }
    }
    return false
}

/** Live "has internet" flag backed by a [ConnectivityManager.NetworkCallback] (initial value from current capabilities). */
@Composable
fun rememberIsOnline(): Boolean {
    val context = LocalContext.current
    var online by remember { mutableStateOf(currentlyOnline(context)) }
    DisposableEffect(context) {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) { online = true }
            override fun onLost(network: Network) { online = currentlyOnline(context) }
            override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
                online = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            }
        }
        val registered = runCatching {
            cm?.registerNetworkCallback(
                NetworkRequest.Builder().addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET).build(),
                callback,
            )
        }.isSuccess
        onDispose { if (registered) runCatching { cm?.unregisterNetworkCallback(callback) } }
    }
    return online
}

private fun currentlyOnline(context: Context): Boolean {
    val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return false
    val caps = cm.getNetworkCapabilities(cm.activeNetwork ?: return false) ?: return false
    return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
}
