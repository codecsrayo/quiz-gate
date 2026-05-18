package com.codecsrayo.quizgate

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class GlossaryRepository(private val context: Context) {

    private val cacheFile: File
        get() = File(context.filesDir, "glossary_aws.json")

    fun hasCache(): Boolean = cacheFile.exists() && cacheFile.length() > 0

    suspend fun loadCached(): List<GlossaryTerm> = withContext(Dispatchers.IO) {
        if (!cacheFile.exists()) return@withContext emptyList()
        runCatching { GlossaryParser.parseList(cacheFile.readText(Charsets.UTF_8)) }
            .getOrElse { emptyList() }
    }

    suspend fun refreshFromNetwork(): Result<Int> {
        val urlStr = Prefs.getGlossaryApiUrl(context)
        return JsonFetcher.get(urlStr).mapCatching { body ->
            withContext(Dispatchers.IO) {
                val parsed = GlossaryParser.parseList(body)
                if (parsed.isEmpty()) {
                    Log.w(TAG, "Empty parse result from $urlStr — body head=${body.take(200)}")
                    error("Respuesta sin términos")
                }
                cacheFile.writeText(body, Charsets.UTF_8)
                Prefs.setLastGlossaryFetch(context, System.currentTimeMillis())
                parsed.size
            }
        }.onFailure { Log.w(TAG, "refreshFromNetwork failed: ${it.message}", it) }
    }

    private companion object {
        const val TAG = "GlossaryRepo"
    }
}
