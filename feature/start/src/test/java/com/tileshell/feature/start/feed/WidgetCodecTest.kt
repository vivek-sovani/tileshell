package com.tileshell.feature.start.feed

import org.junit.Assert.assertEquals
import org.junit.Test

/** Unit tests for the [WidgetCodec] round-trip and its tolerance to bad lines. */
class WidgetCodecTest {

    @Test
    fun `round-trips widgets with heights`() {
        val data = WidgetData(listOf(HostedWidget(7, 120), HostedWidget(42, 200)))
        assertEquals(data, WidgetCodec.decode(WidgetCodec.encode(data)))
    }

    @Test
    fun `empty decodes to no widgets`() {
        assertEquals(WidgetData(), WidgetCodec.decode(""))
    }

    @Test
    fun `bad lines are dropped, missing height defaults`() {
        val decoded = WidgetCodec.decode("garbage\n9\n5,160")
        assertEquals(listOf(HostedWidget(9, 110), HostedWidget(5, 160)), decoded.widgets)
    }

    @Test
    fun `round-trips a custom width for square widgets`() {
        val data = WidgetData(listOf(HostedWidget(7, 180, 180), HostedWidget(42, 200)))
        assertEquals(data, WidgetCodec.decode(WidgetCodec.encode(data)))
    }
}
