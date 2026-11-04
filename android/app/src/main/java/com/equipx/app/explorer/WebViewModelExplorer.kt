package com.equipx.app.explorer

import android.annotation.SuppressLint
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.webkit.ConsoleMessage
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.webkit.WebViewAssetLoader
import org.json.JSONObject

const val ASSET_BASE_URL = "https://appassets.androidplatform.net/assets/"
private const val EXPLORER_PAGE = ASSET_BASE_URL + "web/explorer.html"

/** Handle for a [WebViewModelExplorer]; commands issued before the page is ready are queued. */
@Stable
class WebViewExplorerController : ModelExplorer {
    internal var webView: WebView? = null
    internal var pageReady = false
    /** Called (on the main thread) when a load neither succeeds nor fails within [LOAD_TIMEOUT_MS]. */
    internal var onTimeout: (String) -> Unit = {}
    private val queued = ArrayList<String>()
    private val main = Handler(Looper.getMainLooper())
    private val timeoutRunnable = Runnable {
        onTimeout("The 3D viewer did not respond. Check your connection to the app files and try again.")
    }

    /** Runs [script] now if the page is ready, otherwise queues it (in order) until [onPageReady]. */
    private fun js(script: String) {
        main.post {
            if (pageReady) webView?.evaluateJavascript(script, null) else queued.add(script)
        }
    }

    private fun q(s: String?) = if (s == null) "null" else JSONObject.quote(s)

    override fun loadModel(glbUrl: String, hotspotsUrl: String?, cameraOrbit: String?) {
        main.post {
            main.removeCallbacks(timeoutRunnable)
            main.postDelayed(timeoutRunnable, LOAD_TIMEOUT_MS)
        }
        js("loadModel(${q(glbUrl)}, ${q(hotspotsUrl)}, ${q(cameraOrbit)})")
    }

    override fun selectPart(partId: String?) = js("selectPart(${q(partId)})")
    override fun focusPart(partId: String) = js("focusPart(${q(partId)})")
    override fun resetCamera() = js("resetCamera()")

    internal fun cancelTimeout() { main.removeCallbacks(timeoutRunnable) }

    internal fun onPageReady() {
        pageReady = true
        val wv = webView ?: return
        queued.forEach { wv.evaluateJavascript(it, null) }
        queued.clear()
    }

    internal fun onDisposed() {
        main.removeCallbacks(timeoutRunnable)
        queued.clear()
        webView = null
        pageReady = false
    }

    private companion object {
        const val LOAD_TIMEOUT_MS = 15_000L
    }
}

@Composable
fun rememberWebViewExplorerController(): WebViewExplorerController = remember { WebViewExplorerController() }

private class JsBridge(
    private val controller: WebViewExplorerController,
    private val listener: () -> ModelExplorerListener,
) {
    private val main = Handler(Looper.getMainLooper())

    @JavascriptInterface
    fun onHotspotTap(partId: String) { main.post { listener().onHotspotTap(partId) } }

    @JavascriptInterface
    fun onModelLoaded() { main.post { controller.cancelTimeout(); listener().onModelLoaded() } }

    @JavascriptInterface
    fun onModelError(message: String) { main.post { controller.cancelTimeout(); listener().onModelError(message) } }
}

/** `<model-viewer>` in a WebView, served offline from `assets/web` through [WebViewAssetLoader]. */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun WebViewModelExplorer(
    controller: WebViewExplorerController,
    listener: ModelExplorerListener,
    modifier: Modifier = Modifier,
) {
    val currentListener = rememberUpdatedState(listener)

    controller.onTimeout = { currentListener.value.onModelError(it) }

    AndroidView(
        modifier = modifier,
        factory = { context ->
            val loader = WebViewAssetLoader.Builder()
                .setDomain("appassets.androidplatform.net")
                .addPathHandler("/assets/", WebViewAssetLoader.AssetsPathHandler(context))
                .build()
            WebView(context).apply {
                // Without explicit MATCH_PARENT the WebView wraps content and the 100%-height page collapses to 0px.
                layoutParams = android.view.ViewGroup.LayoutParams(
                    android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                    android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                )
                setBackgroundColor(0xFFF7F7F5.toInt())
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.allowFileAccess = false
                settings.allowContentAccess = false
                WebView.setWebContentsDebuggingEnabled(true)
                webChromeClient = object : WebChromeClient() {
                    override fun onConsoleMessage(m: ConsoleMessage): Boolean {
                        Log.i("EquipXWeb", "${m.messageLevel()} ${m.message()} (${m.sourceId()}:${m.lineNumber()})")
                        return true
                    }
                }
                addJavascriptInterface(JsBridge(controller) { currentListener.value }, "EquipX")
                webViewClient = object : WebViewClient() {
                    override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? =
                        loader.shouldInterceptRequest(request.url)

                    override fun onPageFinished(view: WebView, url: String?) {
                        controller.onPageReady()
                    }
                }
                controller.pageReady = false
                controller.webView = this
                loadUrl(EXPLORER_PAGE)
            }
        },
        onRelease = { wv ->
            wv.removeJavascriptInterface("EquipX")
            wv.destroy()
            controller.onDisposed()
        },
    )
}
