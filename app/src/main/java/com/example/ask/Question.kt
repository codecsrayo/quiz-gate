package com.example.ask

import org.json.JSONArray
import org.json.JSONObject

data class Question(
    val id: String,
    val domain: String,
    val domainLabel: Map<String, String>,
    val questionType: String,
    val scenarioBased: Boolean,
    val synthetic: Boolean,
    val q: Map<String, String>,
    val options: Map<String, List<String>>,
    val answer: Int,
    val answers: List<Int>,
    val explanation: Map<String, String>,
    val tip: Map<String, String>,
) {
    val isMultiResponse: Boolean
        get() = questionType == "multiple-response" || answers.size > 1

    fun correctAnswers(): Set<Int> =
        if (answers.isNotEmpty()) answers.toSet() else setOf(answer)

    fun statement(lang: String): String = q[lang] ?: q["es"] ?: q.values.firstOrNull().orEmpty()
    fun optionsFor(lang: String): List<String> =
        options[lang] ?: options["es"] ?: options.values.firstOrNull().orEmpty()
    fun explanationFor(lang: String): String =
        explanation[lang] ?: explanation["es"] ?: explanation.values.firstOrNull().orEmpty()
    fun tipFor(lang: String): String =
        tip[lang] ?: tip["es"] ?: tip.values.firstOrNull().orEmpty()
    fun domainText(lang: String): String =
        domainLabel[lang] ?: domainLabel["es"] ?: domain
}

object QuestionParser {

    fun parseList(json: String): List<Question> {
        val arr = JSONArray(json)
        val out = ArrayList<Question>(arr.length())
        for (i in 0 until arr.length()) {
            val obj = arr.getJSONObject(i)
            runCatching { parseOne(obj) }.getOrNull()?.let { out.add(it) }
        }
        return out
    }

    private fun parseOne(o: JSONObject): Question = Question(
        id = o.optString("id"),
        domain = o.optString("domain"),
        domainLabel = stringMap(o.optJSONObject("domainLabel")),
        questionType = o.optString("type", o.optString("questionType", "multiple-choice")),
        scenarioBased = o.optBoolean("scenarioBased", false),
        synthetic = o.optBoolean("synthetic", false),
        q = stringMap(o.optJSONObject("q")),
        options = stringListMap(o.optJSONObject("options")),
        answer = o.optInt("answer", 0),
        answers = intList(o.optJSONArray("answers")),
        explanation = stringMap(o.optJSONObject("explanation")),
        tip = stringMap(o.optJSONObject("tip")),
    )

    private fun stringMap(o: JSONObject?): Map<String, String> {
        if (o == null) return emptyMap()
        val m = HashMap<String, String>(o.length())
        val it = o.keys()
        while (it.hasNext()) {
            val k = it.next()
            m[k] = o.optString(k)
        }
        return m
    }

    private fun stringListMap(o: JSONObject?): Map<String, List<String>> {
        if (o == null) return emptyMap()
        val m = HashMap<String, List<String>>(o.length())
        val it = o.keys()
        while (it.hasNext()) {
            val k = it.next()
            val arr = o.optJSONArray(k) ?: continue
            val list = ArrayList<String>(arr.length())
            for (i in 0 until arr.length()) list.add(arr.optString(i))
            m[k] = list
        }
        return m
    }

    private fun intList(arr: JSONArray?): List<Int> {
        if (arr == null) return emptyList()
        val out = ArrayList<Int>(arr.length())
        for (i in 0 until arr.length()) out.add(arr.optInt(i))
        return out
    }
}
