package com.tileshell.feature.start.feed

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/** Unit tests for [isHalfWidthWidget] and [packWidgetRows]. */
class WidgetSlotTest {

    @Test
    fun `isHalfWidthWidget is true when comfortably under half the row`() {
        assertTrue(isHalfWidthWidget(naturalWidthDp = 100f, feedContentWidthDp = 360))
    }

    @Test
    fun `isHalfWidthWidget is false when wider than half the row`() {
        assertFalse(isHalfWidthWidget(naturalWidthDp = 300f, feedContentWidthDp = 360))
    }

    @Test
    fun `isHalfWidthWidget is false at exactly the threshold boundary`() {
        // 55% of 360 = 198; a widget declaring exactly that isn't "comfortably" narrower.
        assertTrue(isHalfWidthWidget(naturalWidthDp = 198f, feedContentWidthDp = 360))
        assertFalse(isHalfWidthWidget(naturalWidthDp = 199f, feedContentWidthDp = 360))
    }

    @Test
    fun `isHalfWidthWidget is false for zero or unknown width`() {
        assertFalse(isHalfWidthWidget(naturalWidthDp = 0f, feedContentWidthDp = 360))
        assertFalse(isHalfWidthWidget(naturalWidthDp = -1f, feedContentWidthDp = 360))
    }

    private fun full(id: Int) = HostedWidget(id, heightDp = 120, halfWidth = false)
    private fun half(id: Int) = HostedWidget(id, heightDp = 120, halfWidth = true)

    @Test
    fun `all full widgets each get their own row`() {
        val rows = packWidgetRows(listOf(full(1), full(2), full(3)))
        assertEquals(listOf(listOf(full(1)), listOf(full(2)), listOf(full(3))), rows)
    }

    @Test
    fun `even count of half widgets pair up`() {
        val rows = packWidgetRows(listOf(half(1), half(2), half(3), half(4)))
        assertEquals(listOf(listOf(half(1), half(2)), listOf(half(3), half(4))), rows)
    }

    @Test
    fun `odd count of half widgets leaves a trailing solo half row`() {
        val rows = packWidgetRows(listOf(half(1), half(2), half(3)))
        assertEquals(listOf(listOf(half(1), half(2)), listOf(half(3))), rows)
    }

    @Test
    fun `a full widget flushes a pending half widget to its own row first`() {
        val rows = packWidgetRows(listOf(half(1), full(2), half(3)))
        assertEquals(listOf(listOf(half(1)), listOf(full(2)), listOf(half(3))), rows)
    }

    @Test
    fun `mixed order pairs consecutive halves and gives full widgets their own row`() {
        val rows = packWidgetRows(listOf(full(1), half(2), half(3), full(4), half(5)))
        assertEquals(
            listOf(listOf(full(1)), listOf(half(2), half(3)), listOf(full(4)), listOf(half(5))),
            rows,
        )
    }

    @Test
    fun `empty list packs to no rows`() {
        assertEquals(emptyList<List<HostedWidget>>(), packWidgetRows(emptyList()))
    }

    // reorderWidgets mirrors com.tileshell.feature.start.reorderTiles's splice
    // algorithm, keyed by widget id instead of tile id.
    private val order = listOf(full(1), full(2), full(3), full(4), full(5))
    private fun ids(widgets: List<HostedWidget>) = widgets.map { it.widgetId }

    @Test
    fun `forwardDrag lands after target`() {
        assertEquals(listOf(2, 3, 4, 1, 5), ids(reorderWidgets(order, dragId = 1, targetId = 4)))
    }

    @Test
    fun `backwardDrag lands before target`() {
        assertEquals(listOf(1, 5, 2, 3, 4), ids(reorderWidgets(order, dragId = 5, targetId = 2)))
    }

    @Test
    fun `adjacent forward swaps neighbours`() {
        assertEquals(listOf(2, 1, 3, 4, 5), ids(reorderWidgets(order, dragId = 1, targetId = 2)))
    }

    @Test
    fun `dragToFirst moves to front`() {
        assertEquals(listOf(4, 1, 2, 3, 5), ids(reorderWidgets(order, dragId = 4, targetId = 1)))
    }

    @Test
    fun `dragToLast moves to end`() {
        assertEquals(listOf(1, 3, 4, 5, 2), ids(reorderWidgets(order, dragId = 2, targetId = 5)))
    }

    @Test
    fun `sameWidget is a no-op and returns the same instance`() {
        assertSame(order, reorderWidgets(order, dragId = 3, targetId = 3))
    }

    @Test
    fun `missing dragId is a no-op`() {
        assertSame(order, reorderWidgets(order, dragId = 999, targetId = 3))
    }

    @Test
    fun `missing targetId is a no-op`() {
        assertSame(order, reorderWidgets(order, dragId = 3, targetId = 999))
    }

    @Test
    fun `reorderWidgets does not mutate the input`() {
        val input = order.toList()
        reorderWidgets(input, dragId = 1, targetId = 5)
        assertEquals(listOf(1, 2, 3, 4, 5), ids(input))
    }
}
