package com.tileshell.feature.livetiles.money

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneOffset

/** Card spends, bills due (statement + reminders) and bill payments. */
class CardBillsTest {
    private val zone = ZoneOffset.UTC
    private fun at(date: String): Long = LocalDate.parse(date).atTime(10, 0).toInstant(zone).toEpochMilli()
    // As MoneyCapture reads them: a transaction first, else a card alert.
    private fun alert(text: String, date: String) = parseMoneyMessage("VM-HDFCBK", text, "sms", at(date))!!.also { assertTrue(it.alert) }
    private fun txn(text: String, date: String) = parseMoneyMessage("VM-HDFCBK", text, "sms", at(date))!!.also { assertFalse(it.alert) }

    @Test
    fun `due dates in the ways banks write them`() {
        val oct1 = LocalDate.of(2026, 10, 1)
        assertEquals(LocalDate.of(2026, 10, 15), dueDateOf("Rs 5,000 is due on 15-Oct-26 on your card", oct1))
        assertEquals(LocalDate.of(2026, 10, 15), dueDateOf("payment due date: 15/10/2026", oct1))
        assertEquals(LocalDate.of(2026, 10, 15), dueDateOf("pay on or before 15th October", oct1))
        assertEquals(LocalDate.of(2026, 10, 15), dueDateOf("total due Rs 900, due by Oct 15, 2026", oct1))
        assertEquals(LocalDate.of(2026, 10, 15), dueDateOf("due date 2026-10-15", oct1))
        // No year: the next one, so a December message's 5 Jan is next year.
        assertEquals(LocalDate.of(2027, 1, 5), dueDateOf("due on 5 Jan", LocalDate.of(2026, 12, 20)))
        assertNull(dueDateOf("your statement is ready", oct1))
    }

    @Test
    fun `spends, bills due and payments are told apart`() {
        assertEquals(CardKind.SPEND, cardKind(txn("Rs.450 spent on HDFC Bank Credit Card xx4321 at AMAZON on 2-Oct", "2026-10-02")))
        assertEquals(CardKind.DUE, cardKind(alert("HDFC Bank Credit Card xx4321 statement: total due Rs.12,500, due on 15-Oct-26", "2026-10-01")))
        assertEquals(CardKind.PAYMENT, cardKind(alert("Payment of Rs 12,500 received towards your HDFC Bank Credit Card xx4321. Thank you", "2026-10-10")))
        // The bank's side of a bill payment is not spending.
        val fromBank = txn("Rs 12,500 debited from a/c XX5678 towards your credit card xx4321 bill", "2026-10-10")
        assertEquals(CardKind.PAYMENT, cardKind(fromBank))
        assertTrue(isCardBillPayment(fromBank))
    }

    @Test
    fun `a statement and its reminders are one bill, next month's is its own`() {
        val statement = alert("HDFC Bank Credit Card xx4321 statement: total due Rs.12,500, min due Rs.625, due on 15-Oct-26", "2026-10-01")
        // A reminder quoting the minimum still joins by its due date.
        val reminder1 = alert("Reminder: Rs.625 minimum due on HDFC Bank Credit Card xx4321 is due on 15-Oct-26", "2026-10-10")
        val reminder2 = alert("HDFC Bank Credit Card xx4321: total due Rs.12,500 is due on 15-Oct-26", "2026-10-13")
        val nextMonth = alert("HDFC Bank Credit Card xx4321 statement: total due Rs.12,500, due on 15-Nov-26", "2026-11-01")
        val groups = groupCardMessages(listOf(statement, reminder1, reminder2, nextMonth), zone)
        assertEquals(2, groups.size)
        val bills = dueBills(groups, zone)
        val october = bills.first { it.dueDate == LocalDate.of(2026, 10, 15) }
        assertEquals(1_250_000L, october.amountPaise) // the statement's total, not the minimum
        assertEquals(statement, october.statement)
        assertEquals(2, october.reminders)
        assertEquals("statement 1 oct · 2 reminders", billSources(october))
    }

    @Test
    fun `bills in due-date order, paid ones last`() {
        val hdfc = alert("HDFC Bank Credit Card xx4321: total due Rs.12,500, due on 20-Oct-26", "2026-10-01")
        val icici = alert("ICICI Bank Credit Card xx9876: total due Rs.3,000, due on 12-Oct-26", "2026-10-02")
        val sbi = alert("SBI Card xx1111 statement: total due Rs.800, due on 9-Oct-26", "2026-09-25")
        val paidSbi = alert("Payment of Rs 800 received towards your SBI Card xx1111. Thank you", "2026-10-05")
        val bills = dueBills(groupCardMessages(listOf(hdfc, icici, sbi, paidSbi), zone), zone)
        assertEquals(listOf("9876", "4321", "1111"), bills.map { it.group.latest.account })
        assertFalse(bills[0].paid)
        assertTrue(bills[2].paid)
    }

    @Test
    fun `the bank's debit and the card's receipt are one payment`() {
        val fromBank = txn("Rs 12,500 debited from a/c XX5678 towards your credit card xx4321 bill", "2026-10-10")
        val received = alert("Payment of Rs 12,500 received towards your HDFC Bank Credit Card xx4321. Thank you", "2026-10-11")
        val groups = groupCardMessages(listOf(fromBank, received), zone)
        assertEquals(1, groups.size)
        assertEquals(2, groups.first().members.size)
    }

    @Test
    fun `a payment received on any card is a payment, not a spend`() {
        val sbi = alert("Payment of Rs 800 received towards your SBI Card xx1111. Thank you", "2026-10-05")
        assertEquals(CardKind.PAYMENT, cardKind(sbi))
        assertTrue(sbi.credit)
        assertEquals("1111", sbi.account)
    }

    @Test
    fun `when a bill is due`() {
        val bill = dueBills(groupCardMessages(listOf(alert("HDFC Bank Credit Card xx4321: total due Rs.900, due on 15-Oct-26", "2026-10-01")), zone), zone).single()
        assertEquals("due 15 oct · in 3 days", dueLabel(bill, LocalDate.of(2026, 10, 12)))
        assertEquals("due 15 oct · tomorrow", dueLabel(bill, LocalDate.of(2026, 10, 14)))
        assertEquals("due today", dueLabel(bill, LocalDate.of(2026, 10, 15)))
        assertEquals("overdue by 2 days · due 15 oct", dueLabel(bill, LocalDate.of(2026, 10, 17)))
        assertEquals("paid · due 15 oct", dueLabel(bill.copy(paid = true), LocalDate.of(2026, 10, 17)))
    }
}
