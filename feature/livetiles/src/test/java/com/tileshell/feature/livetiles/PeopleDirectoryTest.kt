package com.tileshell.feature.livetiles

import org.junit.Assert.assertEquals
import org.junit.Test

class InitialsForTest {

    @Test
    fun `two-word name uses first letter of each word`() {
        assertEquals("AM", initialsFor("aarav mehta"))
        assertEquals("MP", initialsFor("Meera Patil"))
    }

    @Test
    fun `single-word name uses its first two letters`() {
        assertEquals("MA", initialsFor("madonna"))
    }

    @Test
    fun `blank name falls back to a placeholder`() {
        assertEquals("?", initialsFor(""))
        assertEquals("?", initialsFor("   "))
    }

    @Test
    fun `extra whitespace between words is ignored`() {
        assertEquals("AM", initialsFor("  aarav   mehta  "))
    }
}

class GroupContactsByLetterTest {

    private fun person(name: String) = PersonSummary(contactId = name.hashCode().toLong(), lookupKey = name, name = name, photoUri = null)

    @Test
    fun `groups by uppercase first letter, preserving input order within a section`() {
        val people = listOf(person("aarav mehta"), person("anita kulkarni"), person("meera patil"), person("mohan rao"))
        val sections = groupContactsByLetter(people)
        assertEquals(listOf("A", "M"), sections.map { it.first })
        assertEquals(listOf("aarav mehta", "anita kulkarni"), sections[0].second.map { it.name })
        assertEquals(listOf("meera patil", "mohan rao"), sections[1].second.map { it.name })
    }

    @Test
    fun `empty input yields no sections`() {
        assertEquals(emptyList<Pair<String, List<PersonSummary>>>(), groupContactsByLetter(emptyList()))
    }

    @Test
    fun `a name with no letters falls back to a hash section`() {
        val sections = groupContactsByLetter(listOf(person("123")))
        assertEquals("1", sections.single().first)
    }
}
