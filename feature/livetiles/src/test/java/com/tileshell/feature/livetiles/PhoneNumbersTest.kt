package com.tileshell.feature.livetiles

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PhoneNumbersTest {
    private fun e(number: String, default: Boolean = false, primary: Boolean = false) =
        PhoneEntry(number, "mobile", default, primary)

    @Test
    fun `default number comes first, then primary, then card order`() {
        val out = orderPhoneNumbers(listOf(e("111"), e("222", primary = true), e("333", default = true), e("444")))
        assertEquals(listOf("333", "222", "111", "444"), out.map { it.number })
    }

    @Test
    fun `without a default the card order is kept`() {
        val out = orderPhoneNumbers(listOf(e("111"), e("222"), e("333")))
        assertEquals(listOf("111", "222", "333"), out.map { it.number })
    }

    @Test
    fun `the same line written two ways is listed once, keeping the preferred copy`() {
        val out = orderPhoneNumbers(listOf(e("098765 43210"), e("+91 98765 43210", default = true)))
        assertEquals(listOf("+91 98765 43210"), out.map { it.number })
        assertTrue(out.single().isDefault)
    }

    @Test
    fun `blank numbers are dropped`() {
        assertEquals(emptyList<PhoneEntry>(), orderPhoneNumbers(listOf(e(" "), e(""))))
    }
}
