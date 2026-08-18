package com.tileshell.feature.start

import com.tileshell.core.data.settings.IconShape
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Unit tests for [IconShape.toComposeShape] — the mapping lives in
 * `:feature:start` rather than `:core:design` because `IconShape` is a
 * `:core:data` persisted setting and neither core module depends on the
 * other (see `IconCellView.kt`'s doc comment on `toComposeShape`).
 */
class IconCellShapeTest {

    @Test
    fun `original maps to no shape at all`() {
        assertNull(IconShape.ORIGINAL.toComposeShape())
    }

    @Test
    fun `each non-original icon shape maps to a distinct Shape value`() {
        // Constructing a Shape is safe (no Path/Canvas touched until
        // createOutline is called) — this is the guard that IconCellView.kt
        // actually wires three visually distinct shapes rather than aliasing
        // two IconShape entries to the same underlying Shape by mistake.
        // Compared by value (not runtime class): Compose Foundation's
        // CircleShape is itself defined as RoundedCornerShape(50), so CIRCLE
        // and ROUNDED are the same *class* but must still be distinct
        // *values* (different corner percentages) — a class-based check
        // would have falsely failed both here.
        val shapes = IconShape.entries.filter { it != IconShape.ORIGINAL }.mapNotNull { it.toComposeShape() }
        assertEquals(shapes.size, shapes.distinct().size)
    }
}
