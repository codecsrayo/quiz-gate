package com.example.ask

import org.json.JSONArray
import org.json.JSONObject

data class GlossaryTerm(
    val symbol: String,
    val name: String,
    val category: String,
    val synthetic: Boolean = false,
    val desc: Map<String, String>,
    val aliases: List<String> = emptyList(),
    val tags: List<String> = emptyList(),
    val options: Map<String, List<String>> = emptyMap(),
) {
    fun descFor(lang: String): String =
        desc[lang] ?: desc["es"] ?: desc.values.firstOrNull().orEmpty()

    fun optionsFor(lang: String): List<String> =
        options[lang] ?: options["es"] ?: options.values.firstOrNull().orEmpty()
}

object GlossaryParser {

    fun parseList(json: String): List<GlossaryTerm> {
        val arr = JSONArray(json)
        val out = ArrayList<GlossaryTerm>(arr.length())
        for (i in 0 until arr.length()) {
            val obj = arr.getJSONObject(i)
            runCatching { parseOne(obj) }.getOrNull()?.let { out.add(it) }
        }
        return out
    }

    private fun parseOne(o: JSONObject): GlossaryTerm = GlossaryTerm(
        symbol = o.optString("symbol"),
        name = o.optString("name"),
        category = o.optString("category"),
        synthetic = o.optBoolean("synthetic", false),
        desc = stringMap(o.optJSONObject("desc")),
        aliases = stringList(o.optJSONArray("aliases")),
        tags = stringList(o.optJSONArray("tags")),
        options = stringListMap(o.optJSONObject("options")),
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

    private fun stringList(arr: JSONArray?): List<String> {
        if (arr == null) return emptyList()
        val out = ArrayList<String>(arr.length())
        for (i in 0 until arr.length()) out.add(arr.optString(i))
        return out
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
}
