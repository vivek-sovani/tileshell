package com.tileshell.feature.livetiles.money

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MoneyParserTest {
    private fun parse(text: String, title: String = "AX-HDFCBK") = parseMoneyTxn(title, text, "com.messages", 1_000L)

    @Test
    fun `hdfc upi debit`() {
        val t = parse("Rs.450.00 debited from a/c **1234 on 28-09-26 to VPA swiggy@icici (UPI Ref No 123456789012). Not you? Call 18002586161")!!
        assertEquals(45000L, t.amountPaise)
        assertFalse(t.credit)
        assertEquals("1234", t.account)
        assertEquals("upi", t.method)
        assertEquals("swiggy@icici", t.counterparty)
    }

    @Test
    fun `sbi credit with balance`() {
        val t = parse(
            "Your A/C XXXXX8890 Credited INR 42,000.00 on 28/09/26 -Deposit by transfer from ACME LTD. Avl Bal INR 64,210.50-SBI",
            title = "VM-SBIINB",
        )!!
        assertTrue(t.credit)
        assertEquals(4_200_000L, t.amountPaise)
        assertEquals("8890", t.account)
        assertEquals("sbi", t.bank)
        assertEquals(6_421_050L, t.balancePaise)
        assertEquals("acme ltd", t.counterparty)
    }

    @Test
    fun `card spend`() {
        val u = parse("Spent Rs 1,299 on your ICICI Bank Credit Card XX5521 at AMAZON on 27-Sep. Avl Lmt: Rs 88,000", title = "ICICIB")!!
        assertEquals(129_900L, u.amountPaise)
        assertEquals("card", u.method)
        assertEquals("5521", u.account)
        assertEquals("amazon", u.counterparty)
        assertEquals("icici", u.bank)
    }

    @Test
    fun `payment app notification`() {
        val t = parseMoneyTxn("Payment successful", "You paid ₹200 to Rahul S", "com.google.android.apps.nbu.paisa.user", 5L)!!
        assertEquals(20_000L, t.amountPaise)
        assertFalse(t.credit)
        assertEquals("rahul s", t.counterparty)
    }

    @Test
    fun `otp, due notices, offers and requests are ignored`() {
        assertNull(parse("123456 is your OTP for a payment of Rs 499 at Flipkart"))
        assertNull(parse("Your credit card bill of Rs 5,400 is due on 05-Oct"))
        assertNull(parse("Rs 2,000 will be debited from your a/c for SIP on 01-Oct"))
        assertNull(parse("Get a pre-approved loan offer of Rs 5,00,000"))
        assertNull(parse("Rahul has requested Rs 300 via UPI"))
        assertNull(parse("Hello, how are you?"))
    }

    @Test
    fun `duplicates within five minutes are the same payment`() {
        val a = parse("Rs 450 debited from a/c XX1234 to swiggy UPI")!!
        val b = a.copy(time = a.time + 60_000L, sourcePackage = "com.gpay")
        val later = a.copy(time = a.time + 10 * 60_000L)
        val otherDirection = a.copy(credit = true)
        assertTrue(isDuplicateTxn(b, listOf(a)))
        assertFalse(isDuplicateTxn(later, listOf(a)))
        assertFalse(isDuplicateTxn(otherDirection, listOf(a)))
    }

    @Test
    fun `rupee formatting uses indian grouping`() {
        assertEquals("₹450", formatRupees(45000))
        assertEquals("₹42,000", formatRupees(4_200_000))
        assertEquals("₹1,23,456.50", formatRupees(12_345_650))
    }

    @Test
    fun `money app classification`() {
        assertEquals(MoneyAppKind.PAYMENT, moneyAppKind("com.google.android.apps.nbu.paisa.user", "Google Pay"))
        assertEquals(MoneyAppKind.PAYMENT, moneyAppKind("com.phonepe.app", "PhonePe"))
        assertEquals(MoneyAppKind.BANK, moneyAppKind("com.snapwork.hdfc", "HDFC Bank"))
        assertEquals(MoneyAppKind.BANK, moneyAppKind("com.sbi.lotusintouch", "YONO SBI"))
        assertEquals(MoneyAppKind.BANK, moneyAppKind("com.csam.icici.bank.imobile", "iMobile Pay"))
        assertNull(moneyAppKind("com.biology.app", "Biology Notes"))
        assertNull(moneyAppKind("com.spotify.music", "Spotify"))
        assertNotNull(moneyAppKind("in.org.npci.upiapp", "BHIM"))
    }
}

class MoneyCodecTest {
    @Test
    fun `full message survives newlines, tabs and backslashes`() {
        val t = MoneyTxn(1L, 100L, true, "a", null, null, null, null, "p", sender = "AX-HDFCBK", message = "line one\nrs 1\tpaid \\ ok")
        assertEquals(t, MoneyCodec.decode(MoneyCodec.encode(t)))
    }

    @Test
    fun `older nine-column lines still decode, without a message`() {
        val t = MoneyCodec.decode("5\t100\td\tshop\t\t\t\t\tpkg")
        assertEquals("", t?.message)
        assertEquals("shop", t?.counterparty)
    }

    @Test
    fun `round trip, with and without optional fields`() {
        val full = MoneyTxn(10L, 45000L, false, "swiggy\tx", "1234", "hdfc", "upi", 2_310_800L, "com.messages")
        val bare = MoneyTxn(20L, 100L, true, "rahul", null, null, null, null, "com.gpay")
        assertEquals(full.copy(counterparty = "swiggy x"), MoneyCodec.decode(MoneyCodec.encode(full)))
        assertEquals(bare, MoneyCodec.decode(MoneyCodec.encode(bare)))
        assertNull(MoneyCodec.decode("garbage"))
    }
}

class MoneyHubLogicTest {
    private fun txn(time: Long, paise: Long, credit: Boolean, account: String? = "1234", bank: String? = "hdfc", method: String? = "upi") =
        MoneyTxn(time, paise, credit, "x", account, bank, method, null, "p")

    @Test
    fun `month totals count only this month`() {
        val now = java.util.Calendar.getInstance().apply { set(2026, 8, 28, 12, 0) }.timeInMillis
        val lastMonth = java.util.Calendar.getInstance().apply { set(2026, 7, 30, 12, 0) }.timeInMillis
        val totals = monthTotals(listOf(txn(now, 45000, false), txn(now, 4_200_000, true), txn(lastMonth, 99_900, false)), now)
        assertEquals(45000L, totals.spent)
        assertEquals(4_200_000L, totals.received)
        assertEquals("sep", totals.label)
    }

    @Test
    fun `filters list accounts then upi`() {
        val list = listOf(txn(1, 1, false), txn(2, 1, false, account = "8890", bank = "sbi", method = "neft"), txn(3, 1, false, method = "card"))
        assertEquals(listOf("hdfc ·1234", "sbi ·8890", "upi"), moneyFilters(list))
        assertTrue(moneyFilterKey(list[1], "sbi ·8890"))
        assertFalse(moneyFilterKey(list[1], "upi"))
    }
}

class BankMessageTest {
    @Test fun transactionIsBank() {
        assertTrue(com.tileshell.feature.livetiles.money.isBankMessage("AX-HDFCBK", "Rs.500.00 debited from a/c **1234 on 28-09-26 to VPA shop@okaxis"))
    }
    @Test fun otpForPaymentIsBank() {
        assertTrue(com.tileshell.feature.livetiles.money.isBankMessage("VM-ICICIB", "OTP 482913 for txn of INR 1,200 on card XX4321"))
    }
    @Test fun chatWithAmountIsNot() {
        assertFalse(com.tileshell.feature.livetiles.money.isBankMessage("anand", "dinner was rs 500 each, send when free"))
    }
    @Test fun plainChatIsNot() {
        assertFalse(com.tileshell.feature.livetiles.money.isBankMessage("mom", "call me when you reach"))
    }
}

class CardMoneyTest {
    @Test fun cardAppsGetTheirOwnKind() {
        assertEquals(MoneyAppKind.CARD, moneyAppKind("com.dreamplug.androidapp", "CRED"))
        assertEquals(MoneyAppKind.CARD, moneyAppKind("com.x", "OneCard"))
        assertEquals(MoneyAppKind.CARD, moneyAppKind("com.x", "HDFC Bank MyCards"))
        assertEquals(MoneyAppKind.CARD, moneyAppKind("com.x", "SBI Card"))
        assertEquals(MoneyAppKind.BANK, moneyAppKind("com.x", "HDFC Bank"))
        assertNull(moneyAppKind("com.x", "Business Card Scanner"))
    }

    @Test fun cardSpendIsACardTxn() {
        val t = parseMoneyTxn("VM-HDFCBK", "Rs.1,250.00 spent on HDFC Bank Credit Card xx4321 at AMAZON on 29-09-26", "sms", 1L)
        assertNotNull(t)
        assertTrue(isCardTxn(t!!))
        val upi = parseMoneyTxn("AX-SBIINB", "Rs.300 debited from a/c XX1234 to VPA shop@okaxis via UPI", "sms", 1L)
        assertFalse(isCardTxn(upi!!))
    }

    @Test fun billDueBecomesAnAlertWithTheTotal() {
        val body = "Your HDFC Bank Credit Card xx4321 statement is generated. Total due Rs.12,450.00, minimum due Rs.620.00, due by 05-Oct"
        assertNull(parseMoneyTxn("VM-HDFCBK", body, "sms", 1L))
        val a = parseCardAlert("VM-HDFCBK", body, "sms", 1L)
        assertNotNull(a)
        assertTrue(a!!.alert)
        assertEquals(1_245_000L, a.amountPaise)
        assertEquals("statement", a.counterparty)
        assertEquals("4321", a.account)
    }

    @Test fun cardPaymentReceivedAlert() {
        val a = parseCardAlert("AX-ICICIB", "Payment of Rs 5,000 received towards your ICICI Bank Credit Card XX9876. Thank you", "sms", 1L)
        assertTrue(a!!.credit)
        assertEquals("card payment received", a.counterparty)
    }

    @Test fun nonCardBillIsNotAnAlert() {
        assertNull(parseCardAlert("MSEDCL", "Your electricity bill of Rs 840 is due on 10-Oct. Pay by card or UPI", "sms", 1L))
        assertNull(parseCardAlert("VM-HDFCBK", "OTP 123456 for payment of Rs 500 on credit card xx4321 due", "sms", 1L))
    }

    @Test fun alertSurvivesTheCodec() {
        val a = parseCardAlert("VM-HDFCBK", "HDFC Bank Credit Card xx4321: total due Rs.900, due on 5 Oct", "sms", 7L)!!
        assertEquals(a, MoneyCodec.decode(MoneyCodec.encode(a)))
    }
}
