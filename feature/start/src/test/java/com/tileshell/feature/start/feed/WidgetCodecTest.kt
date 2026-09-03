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

    @Test
    fun `round-trips halfWidth, missing field defaults to false`() {
        val data = WidgetData(listOf(HostedWidget(7, 120, halfWidth = true), HostedWidget(42, 200, halfWidth = false)))
        assertEquals(data, WidgetCodec.decode(WidgetCodec.encode(data)))
        // A pre-existing save file written before halfWidth existed (3 columns only).
        assertEquals(listOf(HostedWidget(5, 160, 0, false)), WidgetCodec.decode("5,160,0").widgets)
    }

    @Test
    fun `round-trips stackId for a grouped pair alongside an un-stacked widget`() {
        val data = WidgetData(
            listOf(
                HostedWidget(7, 120, halfWidth = true, stackId = 7),
                HostedWidget(42, 120, halfWidth = true, stackId = 7),
                HostedWidget(9, 200, stackId = null),
            ),
        )
        assertEquals(data, WidgetCodec.decode(WidgetCodec.encode(data)))
    }

    @Test
    fun `a save file written before stackId existed decodes as un-stacked`() {
        // Four columns only — no stackId field at all.
        assertEquals(
            listOf(HostedWidget(5, 160, 0, true, stackId = null)),
            WidgetCodec.decode("5,160,0,true").widgets,
        )
    }

    @Test
    fun `an un-stacked widget writes a blank stackId column that decodes to null`() {
        val encoded = WidgetCodec.encode(WidgetData(listOf(HostedWidget(5, 160))))
        assertEquals("5,160,0,false,", encoded)
        assertEquals(listOf(HostedWidget(5, 160, 0, false, stackId = null)), WidgetCodec.decode(encoded).widgets)
    }

    @Test
    fun `a save file written while the format still carried customKind-customConfig columns still decodes`() {
        // Seven columns — the two removed synthetic-"gadget"-card columns are
        // simply ignored now.
        assertEquals(
            listOf(HostedWidget(5, 160, 0, true, stackId = null)),
            WidgetCodec.decode("5,160,0,true,,stock,stock:single|AAPL|Apple Inc.").widgets,
        )
    }
}
