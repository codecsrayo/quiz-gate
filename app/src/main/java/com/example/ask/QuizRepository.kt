package com.example.ask

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

class QuizRepository(private val context: Context) {

    private val cacheFile: File
        get() = File(context.filesDir, "quiz_practitioner.json")

    fun hasCache(): Boolean = cacheFile.exists() && cacheFile.length() > 0

    fun cacheSizeBytes(): Long = if (cacheFile.exists()) cacheFile.length() else 0L

    suspend fun loadCached(): List<Question> = withContext(Dispatchers.IO) {
        if (!cacheFile.exists()) return@withContext emptyList()
        runCatching { QuestionParser.parseList(cacheFile.readText(Charsets.UTF_8)) }
            .getOrElse { emptyList() }
    }

    suspend fun refreshFromNetwork(): Result<Int> = withContext(Dispatchers.IO) {
        runCatching {
            val url = URL(Prefs.getApiUrl(context))
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
                    error("HTTP $code")
                }
                val body = conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
                val parsed = QuestionParser.parseList(body)
                if (parsed.isEmpty()) error("Respuesta sin preguntas")
                cacheFile.writeText(body, Charsets.UTF_8)
                Prefs.setLastFetch(context, System.currentTimeMillis())
                parsed.size
            } finally {
                conn.disconnect()
            }
        }
    }
}
