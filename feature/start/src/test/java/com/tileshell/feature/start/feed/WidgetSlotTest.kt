package com.tileshell.feature.start.feed

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for the feed widgets section's pure layout/grouping logic:
 * [isHalfWidthWidget], [isInMergeZone], [packWidgetRows], [reorderWidgets],
 * [mergeIntoStack], [removeFromStack], and [stackHeightDp].
 */
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

    // isInMergeZone — the 22–78% inner band, on a 0,0 100×100 rect for easy arithmetic.
    private fun mergeZone(x: Float, y: Float) =
        isInMergeZone(0f, 0f, 100f, 100f, pointX = x, pointY = y)

    @Test
    fun `isInMergeZone accepts the centre of the target`() {
        assertTrue(mergeZone(50f, 50f))
    }

    @Test
    fun `isInMergeZone accepts its exact boundaries`() {
        assertTrue(mergeZone(22f, 22f))
        assertTrue(mergeZone(78f, 78f))
    }

    @Test
    fun `isInMergeZone rejects the outer band on either axis`() {
        assertFalse(mergeZone(50f, 10f)) // above
        assertFalse(mergeZone(50f, 90f)) // below
        assertFalse(mergeZone(10f, 50f)) // left of
        assertFalse(mergeZone(90f, 50f)) // right of
    }

    @Test
    fun `isInMergeZone rejects a point outside the rect entirely`() {
        assertFalse(mergeZone(-40f, 50f))
        assertFalse(mergeZone(50f, 160f))
    }

    @Test
    fun `isInMergeZone rejects a degenerate rect rather than dividing by zero`() {
        assertFalse(isInMergeZone(0f, 0f, 0f, 100f, pointX = 0f, pointY = 50f))
        assertFalse(isInMergeZone(0f, 0f, 100f, 0f, pointX = 50f, pointY = 0f))
    }

    @Test
    fun `isInMergeZone accepts a tighter band for an already-stacked target`() {
        // A stack uses roughly its centre third, so most of the card stays reorder
        // territory and a widget can actually be placed beside it.
        fun tight(x: Float, y: Float) =
            isInMergeZone(0f, 0f, 100f, 100f, pointX = x, pointY = y, zoneMin = 0.34f, zoneMax = 0.66f)
        assertTrue(tight(50f, 50f))
        // Inside the default 22-78 band but outside the tight one: reorder, not join.
        assertTrue(mergeZone(28f, 50f))
        assertFalse(tight(28f, 50f))
        assertTrue(mergeZone(50f, 72f))
        assertFalse(tight(50f, 72f))
    }

    @Test
    fun `isInMergeZone is relative to the rect's own offset, not the origin`() {
        // Same 100×100 target, shifted to (200, 300): its centre is (250, 350).
        assertTrue(isInMergeZone(200f, 300f, 100f, 100f, pointX = 250f, pointY = 350f))
        assertFalse(isInMergeZone(200f, 300f, 100f, 100f, pointX = 250f, pointY = 305f))
    }

    private fun full(id: Int) = HostedWidget(id, heightDp = 120, halfWidth = false)
    private fun half(id: Int) = HostedWidget(id, heightDp = 120, halfWidth = true)
    private fun stacked(id: Int, stackId: Int, heightDp: Int = 120, halfWidth: Boolean = false) =
        HostedWidget(id, heightDp = heightDp, halfWidth = halfWidth, stackId = stackId)

    // Row/card expectation builders — rows are packed from cards (a lone widget or a
    // whole stack), so these keep the expectations readable.
    private fun soloRow(w: HostedWidget) = WidgetRow.Single(WidgetCard.Solo(w))
    private fun pairRow(a: WidgetCard, b: WidgetCard) = WidgetRow.Pair(a, b)
    private fun soloCard(w: HostedWidget) = WidgetCard.Solo(w)
    private fun stackCard(vararg m: HostedWidget) = WidgetCard.Stack(m.toList())
    private fun stackRow(vararg m: HostedWidget) = WidgetRow.Single(WidgetCard.Stack(m.toList()))

    @Test
    fun `all full widgets each get their own row`() {
        val rows = packWidgetRows(listOf(full(1), full(2), full(3)))
        assertEquals(listOf(soloRow(full(1)), soloRow(full(2)), soloRow(full(3))), rows)
    }

    @Test
    fun `even count of half widgets pair up`() {
        val rows = packWidgetRows(listOf(half(1), half(2), half(3), half(4)))
        assertEquals(
            listOf(
                pairRow(soloCard(half(1)), soloCard(half(2))),
                pairRow(soloCard(half(3)), soloCard(half(4))),
            ),
            rows,
        )
    }

    @Test
    fun `odd count of half widgets leaves a trailing solo half row`() {
        val rows = packWidgetRows(listOf(half(1), half(2), half(3)))
        assertEquals(listOf(pairRow(soloCard(half(1)), soloCard(half(2))), soloRow(half(3))), rows)
    }

    @Test
    fun `a full widget flushes a pending half widget to its own row first`() {
        val rows = packWidgetRows(listOf(half(1), full(2), half(3)))
        assertEquals(listOf(soloRow(half(1)), soloRow(full(2)), soloRow(half(3))), rows)
    }

    @Test
    fun `mixed order pairs consecutive halves and gives full widgets their own row`() {
        val rows = packWidgetRows(listOf(full(1), half(2), half(3), full(4), half(5)))
        assertEquals(
            listOf(
                soloRow(full(1)),
                pairRow(soloCard(half(2)), soloCard(half(3))),
                soloRow(full(4)),
                soloRow(half(5)),
            ),
            rows,
        )
    }

    @Test
    fun `empty list packs to no rows`() {
        assertEquals(emptyList<WidgetRow>(), packWidgetRows(emptyList()))
    }

    @Test
    fun `a contiguous stack group becomes one Stack row`() {
        val rows = packWidgetRows(listOf(full(1), stacked(2, stackId = 2), stacked(3, stackId = 2), full(4)))
        assertEquals(
            listOf(soloRow(full(1)), stackRow(stacked(2, 2), stacked(3, 2)), soloRow(full(4))),
            rows,
        )
    }

    @Test
    fun `a half-width stack pairs beside a half-width widget instead of taking a whole row`() {
        // Regression: a stack used to be hardcoded to its own row, so a half-width one
        // rendered alone with dead space beside it and nothing could be placed next to it.
        val stackA = stacked(2, stackId = 2, halfWidth = true)
        val stackB = stacked(3, stackId = 2, halfWidth = true)
        val rows = packWidgetRows(listOf(half(1), stackA, stackB, half(4)))
        assertEquals(
            listOf(
                pairRow(soloCard(half(1)), stackCard(stackA, stackB)),
                soloRow(half(4)),
            ),
            rows,
        )
    }

    @Test
    fun `a full-width stack still takes its own row`() {
        val rows = packWidgetRows(listOf(half(1), stacked(2, stackId = 2), stacked(3, stackId = 2)))
        assertEquals(
            listOf(soloRow(half(1)), stackRow(stacked(2, 2), stacked(3, 2))),
            rows,
        )
    }

    @Test
    fun `two half-width stacks pair with each other`() {
        val a1 = stacked(1, stackId = 1, halfWidth = true)
        val a2 = stacked(2, stackId = 1, halfWidth = true)
        val b1 = stacked(3, stackId = 3, halfWidth = true)
        val b2 = stacked(4, stackId = 3, halfWidth = true)
        assertEquals(
            listOf(pairRow(stackCard(a1, a2), stackCard(b1, b2))),
            packWidgetRows(listOf(a1, a2, b1, b2)),
        )
    }

    @Test
    fun `two separate stacks each get their own row`() {
        val rows = packWidgetRows(
            listOf(stacked(1, 1), stacked(2, 1), stacked(3, 3), stacked(4, 3)),
        )
        assertEquals(
            listOf(stackRow(stacked(1, 1), stacked(2, 1)), stackRow(stacked(3, 3), stacked(4, 3))),
            rows,
        )
    }

    @Test
    fun `a stale one-member group is packed as a solo row, never a one-member stack`() {
        val rows = packWidgetRows(listOf(HostedWidget(1, 120, stackId = 9), full(2)))
        assertEquals(
            listOf(soloRow(HostedWidget(1, 120, stackId = 9)), soloRow(full(2))),
            rows,
        )
    }

    @Test
    fun `hitIds exposes both cards of a pair but only a stack's first member`() {
        assertEquals(listOf(1), soloRow(full(1)).hitIds)
        assertEquals(listOf(1, 2), pairRow(soloCard(half(1)), soloCard(half(2))).hitIds)
        assertEquals(listOf(1), stackRow(stacked(1, 1), stacked(2, 1)).hitIds)
    }

    @Test
    fun `a card reports its width and hit id from its first member`() {
        assertEquals(true, stackCard(stacked(7, 7, halfWidth = true), stacked(8, 7, halfWidth = true)).halfWidth)
        assertEquals(false, stackCard(stacked(7, 7), stacked(8, 7)).halfWidth)
        assertEquals(7, stackCard(stacked(7, 7), stacked(8, 7)).hitId)
        assertEquals(3, soloCard(full(3)).hitId)
    }

    @Test
    fun `stackHeightDp is the max of members' own heights`() {
        assertEquals(200, stackHeightDp(listOf(stacked(1, 1, heightDp = 120), stacked(2, 1, heightDp = 200))))
        assertEquals(120, stackHeightDp(listOf(stacked(1, 1, heightDp = 120))))
    }

    // mergeIntoStack — forming and joining groups.

    @Test
    fun `mergeIntoStack groups the dragged widget after the target, adopting the target's id`() {
        val result = mergeIntoStack(listOf(full(1), full(2), full(3)), draggedId = 3, targetId = 1)
        assertEquals(
            listOf(
                HostedWidget(1, 120, stackId = 1),
                HostedWidget(3, 120, stackId = 1),
                full(2),
            ),
            result,
        )
    }

    @Test
    fun `mergeIntoStack joins an existing stack by adopting its stack id`() {
        val widgets = listOf(stacked(1, stackId = 1), stacked(2, stackId = 1), full(3))
        val result = mergeIntoStack(widgets, draggedId = 3, targetId = 2)
        assertEquals(
            listOf(stacked(1, 1), stacked(2, 1), HostedWidget(3, 120, stackId = 1)),
            result,
        )
    }

    @Test
    fun `mergeIntoStack lands after the whole target group, not just the targeted member`() {
        // Targeting the FIRST member of a 2-member stack still appends at the group's end.
        val widgets = listOf(stacked(1, stackId = 1), stacked(2, stackId = 1), full(3))
        val result = mergeIntoStack(widgets, draggedId = 3, targetId = 1)
        assertEquals(listOf(1, 2, 3), result.map { it.widgetId })
        assertTrue(result.all { it.stackId == 1 })
    }

    @Test
    fun `mergeIntoStack dissolves the group the dragged widget left when one member remains`() {
        val widgets = listOf(stacked(1, stackId = 1), stacked(2, stackId = 1), full(3))
        val result = mergeIntoStack(widgets, draggedId = 2, targetId = 3)
        assertEquals(
            listOf(
                full(1), // stackId cleared — a stack of one doesn't exist
                HostedWidget(3, 120, stackId = 3),
                HostedWidget(2, 120, stackId = 3),
            ),
            result,
        )
    }

    @Test
    fun `mergeIntoStack leaves a three-member group intact when one member leaves`() {
        val widgets = listOf(stacked(1, 1), stacked(2, 1), stacked(3, 1), full(4))
        val result = mergeIntoStack(widgets, draggedId = 3, targetId = 4)
        assertEquals(listOf(1, 2, 4, 3), result.map { it.widgetId })
        assertEquals(listOf(1, 1, 4, 4), result.map { it.stackId })
    }

    @Test
    fun `mergeIntoStack is a no-op for the same widget`() {
        val widgets = listOf(full(1), full(2))
        assertSame(widgets, mergeIntoStack(widgets, draggedId = 1, targetId = 1))
    }

    @Test
    fun `mergeIntoStack is a no-op when both are already in the same group`() {
        val widgets = listOf(stacked(1, stackId = 1), stacked(2, stackId = 1))
        assertSame(widgets, mergeIntoStack(widgets, draggedId = 1, targetId = 2))
    }

    @Test
    fun `mergeIntoStack is a no-op for a missing id`() {
        val widgets = listOf(full(1), full(2))
        assertSame(widgets, mergeIntoStack(widgets, draggedId = 999, targetId = 1))
        assertSame(widgets, mergeIntoStack(widgets, draggedId = 1, targetId = 999))
    }

    @Test
    fun `mergeIntoStack does not mutate the input`() {
        val input = listOf(full(1), full(2), full(3))
        mergeIntoStack(input, draggedId = 3, targetId = 1)
        assertEquals(listOf(full(1), full(2), full(3)), input)
    }

    // removeFromStack — leaving a group.

    @Test
    fun `removeFromStack un-stacks one member, leaving a three-member group a stack`() {
        val widgets = listOf(stacked(1, stackId = 1), stacked(2, stackId = 1), stacked(3, stackId = 1))
        val result = removeFromStack(widgets, widgetId = 2)
        assertEquals(
            listOf(stacked(1, 1), HostedWidget(2, 120, stackId = null), stacked(3, 1)),
            result,
        )
    }

    @Test
    fun `removeFromStack dissolves a two-member group entirely`() {
        val widgets = listOf(stacked(1, stackId = 1), stacked(2, stackId = 1))
        assertEquals(listOf(full(1), full(2)), removeFromStack(widgets, widgetId = 1))
    }

    @Test
    fun `removeFromStack is a no-op for an un-stacked or missing widget`() {
        val widgets = listOf(full(1), full(2))
        assertSame(widgets, removeFromStack(widgets, widgetId = 1))
        assertSame(widgets, removeFromStack(widgets, widgetId = 999))
    }

    // reorderWidgets mirrors com.tileshell.feature.start.reorderTiles's splice
    // algorithm, keyed by widget id instead of tile id — and operates on whole
    // stack-aware blocks so a reorder can never split a stack.
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

    @Test
    fun `reorderWidgets moves a whole stack as one block, keeping members together`() {
        val widgets = listOf(full(1), stacked(2, stackId = 2), stacked(3, stackId = 2), full(4))
        assertEquals(listOf(2, 3, 1, 4), ids(reorderWidgets(widgets, dragId = 2, targetId = 1)))
    }

    @Test
    fun `reorderWidgets targeting any stack member targets the whole block`() {
        val widgets = listOf(full(1), stacked(2, stackId = 2), stacked(3, stackId = 2), full(4))
        // Dragging the trailing widget back onto the stack's second member puts it
        // before the whole block, not between its members.
        assertEquals(listOf(1, 4, 2, 3), ids(reorderWidgets(widgets, dragId = 4, targetId = 3)))
    }

    @Test
    fun `reorderWidgets preserves stack membership through a move`() {
        val widgets = listOf(full(1), stacked(2, stackId = 2), stacked(3, stackId = 2))
        val result = reorderWidgets(widgets, dragId = 3, targetId = 1)
        assertEquals(listOf(2, 2, null), result.map { it.stackId })
    }

    @Test
    fun `reorderWidgets is a no-op between two members of the same stack`() {
        val widgets = listOf(stacked(1, stackId = 1), stacked(2, stackId = 1), full(3))
        assertSame(widgets, reorderWidgets(widgets, dragId = 1, targetId = 2))
    }

    // ---- seedMissingBuiltinWidgets (built-in glance card sentinel ids) --------

    @Test
    fun `seedMissingBuiltinWidgets inserts all three built-ins at the front on a fresh list`() {
        val seeded = seedMissingBuiltinWidgets(emptyList())
        assertEquals(
            listOf(BUILTIN_WEATHER_WIDGET_ID, BUILTIN_AGENDA_WIDGET_ID, BUILTIN_NOWPLAYING_WIDGET_ID),
            seeded.map { it.widgetId },
        )
        assertEquals(listOf(true, true, false), seeded.map { it.halfWidth })
    }

    @Test
    fun `seedMissingBuiltinWidgets inserts missing built-ins ahead of existing hosted widgets`() {
        val existing = listOf(full(7))
        val seeded = seedMissingBuiltinWidgets(existing)
        assertEquals(
            listOf(BUILTIN_WEATHER_WIDGET_ID, BUILTIN_AGENDA_WIDGET_ID, BUILTIN_NOWPLAYING_WIDGET_ID, 7),
            seeded.map { it.widgetId },
        )
    }

    @Test
    fun `seedMissingBuiltinWidgets is a no-op once all three already exist`() {
        val existing = listOf(
            HostedWidget(BUILTIN_WEATHER_WIDGET_ID, 0),
            full(7),
            HostedWidget(BUILTIN_AGENDA_WIDGET_ID, 0),
            HostedWidget(BUILTIN_NOWPLAYING_WIDGET_ID, 0),
        )
        assertSame(existing, seedMissingBuiltinWidgets(existing))
    }

    @Test
    fun `seedMissingBuiltinWidgets only inserts the ones actually missing`() {
        val existing = listOf(HostedWidget(BUILTIN_WEATHER_WIDGET_ID, 0), full(7))
        val seeded = seedMissingBuiltinWidgets(existing)
        assertEquals(
            listOf(BUILTIN_AGENDA_WIDGET_ID, BUILTIN_NOWPLAYING_WIDGET_ID, BUILTIN_WEATHER_WIDGET_ID, 7),
            seeded.map { it.widgetId },
        )
    }

    // ---- negative sentinel ids need no special-casing in packing/reordering ---

    @Test
    fun `packWidgetRows packs built-in sentinel ids exactly like any other widget`() {
        val widgets = listOf(
            HostedWidget(BUILTIN_WEATHER_WIDGET_ID, 0, halfWidth = true),
            HostedWidget(BUILTIN_AGENDA_WIDGET_ID, 0, halfWidth = true),
            HostedWidget(BUILTIN_NOWPLAYING_WIDGET_ID, 0, halfWidth = false),
        )
        val rows = packWidgetRows(widgets)
        assertEquals(2, rows.size)
        assertTrue(rows[0] is WidgetRow.Pair)
        assertTrue(rows[1] is WidgetRow.Single)
    }

    @Test
    fun `reorderWidgets moves a built-in sentinel id past a real hosted widget`() {
        val widgets = listOf(
            HostedWidget(BUILTIN_WEATHER_WIDGET_ID, 0, halfWidth = true),
            HostedWidget(BUILTIN_AGENDA_WIDGET_ID, 0, halfWidth = true),
            full(7),
        )
        val result = reorderWidgets(widgets, dragId = BUILTIN_WEATHER_WIDGET_ID, targetId = 7)
        assertEquals(listOf(BUILTIN_AGENDA_WIDGET_ID, 7, BUILTIN_WEATHER_WIDGET_ID), ids(result))
    }
}
