package com.tileshell.feature.livetiles

import org.junit.Assert.assertEquals
import org.junit.Test

class ClassifyQuickActionsTest {

    private fun info(title: String, semantic: Int = 0, freeText: Boolean = false) =
        QuickActionInfo(title, semantic, freeText)

    @Test
    fun `whatsapp style buttons without semantics are classified by input and title`() {
        val actions = listOf(info("Reply", freeText = true), info("Mark as read"))
        assertEquals(
            mapOf(QuickAction.REPLY to 0, QuickAction.MARK_READ to 1),
            classifyQuickActions(actions),
        )
    }

    @Test
    fun `gmail style archive and reply are found`() {
        val actions = listOf(info("Archive"), info("Reply", freeText = true))
        assertEquals(
            mapOf(QuickAction.ARCHIVE to 0, QuickAction.REPLY to 1),
            classifyQuickActions(actions),
        )
    }

    @Test
    fun `declared semantic actions win over titles`() {
        val actions = listOf(
            info("Gelesen", semantic = SEMANTIC_MARK_AS_READ),
            info("Antworten", semantic = SEMANTIC_REPLY, freeText = true),
            info("Archivieren", semantic = SEMANTIC_ARCHIVE),
        )
        assertEquals(
            mapOf(QuickAction.MARK_READ to 0, QuickAction.REPLY to 1, QuickAction.ARCHIVE to 2),
            classifyQuickActions(actions),
        )
    }

    @Test
    fun `a reply button with no text input is not offered`() {
        assertEquals(emptyMap<QuickAction, Int>(), classifyQuickActions(listOf(info("Reply"))))
    }

    @Test
    fun `a button declaring some other semantic is ignored even if titled archive`() {
        // SEMANTIC_ACTION_DELETE = 4
        assertEquals(emptyMap<QuickAction, Int>(), classifyQuickActions(listOf(info("archive", semantic = 4))))
    }

    @Test
    fun `the first matching button wins`() {
        val actions = listOf(info("Reply", freeText = true), info("Reply all", freeText = true))
        assertEquals(mapOf(QuickAction.REPLY to 0), classifyQuickActions(actions))
    }
}
