package com.equipx.app.data.remote

import com.equipx.app.BuildConfig
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class HttpRequest(
    val method: String,
    val url: String,
    val headers: Map<String, String> = emptyMap(),
    val body: String? = null,
)

data class HttpResponse(val code: Int, val body: String) {
    val isSuccess: Boolean get() = code in 200..299
}

/** Minimal transport so auth/sync logic is testable with a fake. Implementations throw [java.io.IOException] when offline. */
fun interface HttpTransport {
    suspend fun execute(request: HttpRequest): HttpResponse
}

/** Zero-dependency transport on [HttpURLConnection]. */
class UrlConnectionTransport(private val timeoutMs: Int = 15_000) : HttpTransport {
    override suspend fun execute(request: HttpRequest): HttpResponse = withContext(Dispatchers.IO) {
        val conn = URL(request.url).openConnection() as HttpURLConnection
        try {
            conn.requestMethod = request.method
            conn.connectTimeout = timeoutMs
            conn.readTimeout = timeoutMs
            request.headers.forEach { (k, v) -> conn.setRequestProperty(k, v) }
            request.body?.let { body ->
                conn.doOutput = true
                conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            }
            val code = conn.responseCode
            val stream = if (code in 200..399) conn.inputStream else conn.errorStream
            HttpResponse(code, stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty())
        } finally {
            conn.disconnect()
        }
    }
}

/** Supabase endpoint + anon (public, RLS-protected) key. [toString] never reveals the key. */
class SupabaseConfig(url: String, val anonKey: String) {
    val baseUrl: String = url.trim().trimEnd('/')
    val isConfigured: Boolean get() = baseUrl.startsWith("http") && anonKey.isNotBlank()

    fun authUrl(path: String) = "$baseUrl/auth/v1/$path"
    fun restUrl(path: String) = "$baseUrl/rest/v1/$path"

    /** Headers for a request; [accessToken] null => anon key as bearer (sign-in/sign-up). */
    fun headers(accessToken: String?, extra: Map<String, String> = emptyMap()): Map<String, String> =
        mapOf(
            "apikey" to anonKey,
            "Authorization" to "Bearer ${accessToken ?: anonKey}",
            "Content-Type" to "application/json",
            "Accept" to "application/json",
        ) + extra

    override fun toString() = "SupabaseConfig(baseUrl=$baseUrl, anonKey=<hidden>)"

    companion object {
        fun fromBuildConfig() = SupabaseConfig(BuildConfig.SUPABASE_URL, BuildConfig.SUPABASE_ANON_KEY)
    }
}
