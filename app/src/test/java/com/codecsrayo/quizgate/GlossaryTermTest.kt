package com.codecsrayo.quizgate

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GlossaryTermTest {

    private fun termJson(symbol: String, domains: List<String>?): String {
        val o = JSONObject()
        o.put("symbol", symbol)
        o.put("name", "")
        o.put("category", "")
        o.put("desc", JSONObject().put("es", "d"))
        if (domains != null) o.put("domains", JSONArray(domains))
        return o.toString()
    }

    @Test
    fun parses_domains_when_present_and_populated() {
        val json = "[${termJson("IAM", listOf("security", "technology"))}]"
        val terms = GlossaryParser.parseList(json)
        assertEquals(1, terms.size)
        assertEquals(listOf("security", "technology"), terms[0].domains)
    }

    @Test
    fun parses_empty_domains_array() {
        val json = "[${termJson("EC2", emptyList())}]"
        val terms = GlossaryParser.parseList(json)
        assertEquals(1, terms.size)
        assertTrue(terms[0].domains.isEmpty())
    }

    @Test
    fun defaults_to_empty_when_domains_field_absent() {
        val json = "[${termJson("S3", null)}]"
        val terms = GlossaryParser.parseList(json)
        assertEquals(1, terms.size)
        assertTrue(terms[0].domains.isEmpty())
    }

    @Test
    fun defaults_to_empty_when_domains_is_not_an_array() {
        val o = JSONObject()
        o.put("symbol", "VPC")
        o.put("name", "")
        o.put("category", "")
        o.put("desc", JSONObject().put("es", "d"))
        o.put("domains", "security")
        val json = "[${o}]"
        val terms = GlossaryParser.parseList(json)
        assertEquals(1, terms.size)
        assertTrue(terms[0].domains.isEmpty())
    }

    @Test
    fun parse_list_round_trip_with_multiple_terms() {
        val json = "[" +
            termJson("IAM", listOf("security", "technology")) +
            "," +
            termJson("EC2", null) +
            "]"
        val terms = GlossaryParser.parseList(json)
        assertEquals(2, terms.size)
        assertEquals(listOf("security", "technology"), terms[0].domains)
        assertTrue(terms[1].domains.isEmpty())
        assertEquals("IAM", terms[0].symbol)
        assertEquals("EC2", terms[1].symbol)
    }
}
