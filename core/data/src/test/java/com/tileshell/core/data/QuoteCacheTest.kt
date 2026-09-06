package com.tileshell.core.data

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

class QuoteCacheTest {

    @Before
    fun reset() = QuoteCache.clear()

    @Test
    fun `a repeated request inside the TTL fetches once`() = runTest {
        val calls = AtomicInteger()
        repeat(5) {
            QuoteCache.get("quote:AAPL") { calls.incrementAndGet(); "x" }
        }
        assertEquals(1, calls.get())
    }

    @Test
    fun `distinct keys fetch independently`() = runTest {
        val calls = AtomicInteger()
        QuoteCache.get("quote:AAPL") { calls.incrementAndGet(); "a" }
        QuoteCache.get("quote:MSFT") { calls.incrementAndGet(); "b" }
        // Same symbol, different request kind — must not collide.
        QuoteCache.get("spark:AAPL") { calls.incrementAndGet(); "c" }
        assertEquals(3, calls.get())
    }

    @Test
    fun `the cached value is returned, not just the call suppressed`() = runTest {
        QuoteCache.get("quote:AAPL") { "first" }
        val second = QuoteCache.get<String>("quote:AAPL") { "second" }
        assertEquals("first", second)
    }

    @Test
    fun `an expired entry refetches`() = runTest {
        val calls = AtomicInteger()
        QuoteCache.get("quote:AAPL", nowMillis = 0L) { calls.incrementAndGet(); "x" }
        // Ask again with a clock far past the TTL.
        QuoteCache.get("quote:AAPL", nowMillis = QuoteCache.TTL_MS * 10) { calls.incrementAndGet(); "y" }
        assertEquals(2, calls.get())
    }

    @Test
    fun `concurrent callers for one key fetch once — the case a TTL alone misses`() = runTest {
        val calls = AtomicInteger()
        // This is the widget-worker shape: several instances asking for the same
        // symbol at the same moment, all missing an empty cache.
        val results = (1..8).map {
            async {
                QuoteCache.get("quote:AAPL") {
                    calls.incrementAndGet()
                    delay(20) // a real network call is not instant
                    "shared"
                }
            }
        }.awaitAll()

        assertEquals(1, calls.get())
        assertEquals(List(8) { "shared" }, results)
    }

    @Test
    fun `a null result is cached so a dataless symbol is not retried per instance`() = runTest {
        val calls = AtomicInteger()
        repeat(4) {
            QuoteCache.get<String?>("quote:BOGUS") { calls.incrementAndGet(); null }
        }
        assertEquals(1, calls.get())
    }

    @Test
    fun `a clock that moved backwards refetches rather than pinning a stale entry`() = runTest {
        val calls = AtomicInteger()
        QuoteCache.get("quote:AAPL", nowMillis = 10_000L) { calls.incrementAndGet(); "x" }
        QuoteCache.get("quote:AAPL", nowMillis = 0L) { calls.incrementAndGet(); "y" }
        assertEquals(2, calls.get())
    }
}
