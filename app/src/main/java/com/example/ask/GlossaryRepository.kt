package com.example.ask

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

class GlossaryRepository(private val context: Context) {

    private val cacheFile: File
        get() = File(context.filesDir, "glossary_aws.json")

    fun hasCache(): Boolean = cacheFile.exists() && cacheFile.length() > 0

    suspend fun loadCached(): List<GlossaryTerm> = withContext(Dispatchers.IO) {
        if (!cacheFile.exists()) return@withContext emptyList()
        runCatching { GlossaryParser.parseList(cacheFile.readText(Charsets.UTF_8)) }
            .getOrElse { emptyList() }
    }

    suspend fun refreshFromNetwork(): Result<Int> = withContext(Dispatchers.IO) {
        val urlStr = Prefs.getGlossaryApiUrl(context)
        runCatching {
            Log.i(TAG, "GET $urlStr")
            val url = URL(urlStr)
            val conn = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = 10_000
                readTimeout = 20_000
                requestMethod = "GET"
                setRequestProperty("Accept", "application/json, text/plain;q=0.9, */*;q=0.5")
                setRequestProperty("User-Agent", "QuizGate/1.0 (Android)")
            }
            try {
                val code = conn.responseCode
                if (code !in 200..299) {
                    val errBody = runCatching {
                        conn.errorStream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }
                    }.getOrNull().orEmpty()
                    Log.w(TAG, "HTTP $code on $urlStr — body=${errBody.take(500)}")
                    error("HTTP $code")
                }
                val body = conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
                Log.i(TAG, "HTTP $code on $urlStr — ${body.length} bytes")
                val parsed = GlossaryParser.parseList(body)
                if (parsed.isEmpty()) {
                    Log.w(TAG, "Empty parse result from $urlStr — body head=${body.take(200)}")
                    error("Respuesta sin términos")
                }
                cacheFile.writeText(body, Charsets.UTF_8)
                Prefs.setLastGlossaryFetch(context, System.currentTimeMillis())
                parsed.size
            } finally {
                conn.disconnect()
            }
        }.onFailure { Log.w(TAG, "refreshFromNetwork failed: ${it.message}", it) }
    }

    private companion object {
        const val TAG = "GlossaryRepo"
    }
}
