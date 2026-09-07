package com.tileshell.core.data

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test

class WeatherPlaceApiTest {

    @Test
    fun `parses a place with admin area and country`() {
        val json = """
            {"results":[{"latitude":18.5204,"longitude":73.8567,"name":"Pune","admin1":"Maharashtra","country":"India"}]}
        """.trimIndent()
        val results = parseWeatherPlaceResults(json)
        assertEquals(1, results.size)
        assertEquals(WeatherPlaceResult(18.5204, 73.8567, "Pune, Maharashtra, India"), results[0])
    }

    @Test
    fun `a place with no admin1 skips the blank part instead of leaving a stray comma`() {
        val json = """
            {"results":[{"latitude":1.35,"longitude":103.82,"name":"Marina Bay","country":"Singapore"}]}
        """.trimIndent()
        assertEquals("Marina Bay, Singapore", parseWeatherPlaceResults(json)[0].displayName)
    }

    @Test
    fun `a city-state whose name equals its own country collapses to one segment, not a repeated name`() {
        val json = """
            {"results":[{"latitude":1.35,"longitude":103.82,"name":"Singapore","country":"Singapore"}]}
        """.trimIndent()
        assertEquals("Singapore", parseWeatherPlaceResults(json)[0].displayName)
    }

    @Test
    fun `multiple results are all returned in order`() {
        val json = """
            {"results":[
                {"latitude":51.5074,"longitude":-0.1278,"name":"London","admin1":"England","country":"United Kingdom"},
                {"latitude":42.9834,"longitude":-81.233,"name":"London","admin1":"Ontario","country":"Canada"}
            ]}
        """.trimIndent()
        val results = parseWeatherPlaceResults(json)
        assertEquals(2, results.size)
        assertEquals("London, England, United Kingdom", results[0].displayName)
        assertEquals("London, Ontario, Canada", results[1].displayName)
    }

    @Test
    fun `a result missing coordinates is skipped rather than crashing`() {
        val json = """{"results":[{"name":"Nowhere"}]}"""
        assertEquals(emptyList<WeatherPlaceResult>(), parseWeatherPlaceResults(json))
    }

    @Test
    fun `no results array or malformed json returns empty, never throws`() {
        assertEquals(emptyList<WeatherPlaceResult>(), parseWeatherPlaceResults(JSONObject().toString()))
        assertEquals(emptyList<WeatherPlaceResult>(), parseWeatherPlaceResults("not json"))
    }

    @Test
    fun `label drops a blank admin1 instead of leaving an empty segment`() {
        val r = JSONObject().put("admin1", "").put("country", "India")
        assertEquals("Pune, India", weatherPlaceLabel("Pune", r))
    }

    @Test
    fun `label collapses a repeated part instead of showing it twice`() {
        // A place whose own name is identical to its country's — the earlier
        // "Singapore" case above, isolated at the label-builder level.
        val r = JSONObject().put("admin1", "").put("country", "Singapore")
        assertEquals("Singapore", weatherPlaceLabel("Singapore", r))
    }
}
