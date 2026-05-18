package com.codecsrayo.quizgate

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

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

    suspend fun refreshFromNetwork(): Result<Int> {
        val urlStr = Prefs.getApiUrl(context)
        return JsonFetcher.get(urlStr).mapCatching { body ->
            withContext(Dispatchers.IO) {
                val parsed = QuestionParser.parseList(body)
                if (parsed.isEmpty()) {
                    Log.w(TAG, "Empty parse result from $urlStr — body head=${body.take(200)}")
                    error("Respuesta sin preguntas")
                }
                cacheFile.writeText(body, Charsets.UTF_8)
                Prefs.setLastFetch(context, System.currentTimeMillis())
                parsed.size
            }
        }.onFailure { Log.w(TAG, "refreshFromNetwork failed: ${it.message}", it) }
    }

    private companion object {
        const val TAG = "QuizRepo"
    }
}
