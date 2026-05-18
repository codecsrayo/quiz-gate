package com.codecsrayo.quizgate

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

object JsonFetcher {
    private const val TAG = "JsonFetcher"

    suspend fun get(url: String): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            Log.i(TAG, "GET $url")
            val conn = (URL(url).openConnection() as HttpURLConnection).apply {
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
                    Log.w(TAG, "HTTP $code on $url — body=${errBody.take(500)}")
                    error("HTTP $code")
                }
                val body = conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
                Log.i(TAG, "HTTP $code on $url — ${body.length} bytes")
                body
            } finally {
                conn.disconnect()
            }
        }
    }
}
