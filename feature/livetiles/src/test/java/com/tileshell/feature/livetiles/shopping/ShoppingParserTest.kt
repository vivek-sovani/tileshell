package com.tileshell.feature.livetiles.shopping

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ShoppingParserTest {

    private fun parse(title: String, text: String, merchant: String = "Amazon", food: Boolean = false, time: Long = 1_000L, titleIsItem: Boolean = true) =
        parseOrderMessage(title, text, "pkg", merchant, food, time, titleIsItem)

    @Test
    fun `statuses from typical messages`() {
        assertEquals(OrderStatus.DELIVERED, parse("Delivered: Wireless earbuds", "Your package was delivered. Rate it.")?.status)
        assertEquals(OrderStatus.DELIVERED, parse("Amazon", "Your order has been delivered successfully")?.status)
        assertEquals(OrderStatus.OUT_FOR_DELIVERY, parse("Amazon", "Your package is out for delivery. Arriving today by 8 pm.")?.status)
        assertEquals(OrderStatus.OUT_FOR_DELIVERY, parse("Swiggy", "Your delivery partner is on the way")?.status)
        assertEquals(OrderStatus.SHIPPED, parse("Flipkart", "Your order has been shipped. Track with AWB 12345678.")?.status)
        assertEquals(OrderStatus.SHIPPED, parse("Myntra", "Your order is on its way")?.status)
        assertEquals(OrderStatus.PLACED, parse("Amazon", "Thank you for your order. Order #402-1234567-7654321")?.status)
        assertEquals(OrderStatus.PLACED, parse("Zomato", "Restaurant is preparing your order")?.status)
        assertEquals(OrderStatus.CANCELLED, parse("Myntra", "Your order has been cancelled. Refund in 3 days.")?.status)
        assertEquals(OrderStatus.RETURNED, parse("Myntra", "Your return has been picked up")?.status)
    }

    @Test
    fun `a store's business chat message is read`() {
        val u = parse(
            "Amazon India",
            "Arriving today between 08:00 AM and 12:00 PM: Your Amazon package \uD83D\uDCE6 with 2 item(s) is out for delivery with our delivery agent.\n\nOrder ID: 407-2341856-1256365",
            titleIsItem = false,
        )
        assertEquals(OrderStatus.OUT_FOR_DELIVERY, u?.status)
        assertEquals("407-2341856-1256365", u?.ref)
        assertEquals("2 items", u?.title)
    }

    @Test
    fun `amazon style order placed messages are read`() {
        val ordered = parse("Ordered: Sony WH-1000XM5 Wireless Headphones", "Arriving tomorrow")
        assertEquals(OrderStatus.PLACED, ordered?.status)
        assertEquals("Sony WH-1000XM5 Wireless Headphones", ordered?.title)
        assertEquals("tomorrow", ordered?.eta)
        assertEquals(OrderStatus.PLACED, parse("Amazon", "You've ordered Philips air fryer. Arriving Thursday")?.status)
        assertEquals(OrderStatus.PLACED, parse("Amazon Now", "Packing your order. Will arrive in 12 mins")?.status)
        assertEquals("in 12 min", parse("Amazon Now", "Your order will arrive in 12 mins")?.eta)
        assertEquals(OrderStatus.PLACED, parse("Amazon", "Thanks for ordering. Arriving by 9 pm")?.status)
        // Further along statuses still win over the arrival words.
        assertEquals(OrderStatus.OUT_FOR_DELIVERY, parse("Amazon", "Out for delivery: Arriving today")?.status)
        assertEquals(OrderStatus.SHIPPED, parse("Amazon", "Shipped: Mixer grinder. Arriving tomorrow")?.status)
        assertEquals(OrderStatus.DELIVERED, parse("Amazon", "Delivered: Mixer grinder")?.status)
    }

    @Test
    fun `a future delivery is not delivered`() {
        assertNull(parse("Amazon", "Your parcel will be delivered by tomorrow")?.takeIf { it.status == OrderStatus.DELIVERED })
        assertEquals(OrderStatus.SHIPPED, parse("Flipkart", "Shipped: your order will be delivered by Fri")?.status)
    }

    @Test
    fun `messages that are not about an order are ignored`() {
        assertNull(parse("Amazon", "Big sale! Up to 70% off. Order now."))
        assertNull(parse("Amazon", "Your OTP for login is 482910"))
        assertNull(parse("Mom", "are you coming home today?"))
        assertNull(parse("Swiggy", "Order now and get 50% off with code WIN50 — order placed in seconds"))
    }

    @Test
    fun `order numbers are found`() {
        assertEquals("402-1234567-7654321", parse("Amazon", "Order #402-1234567-7654321 has shipped")?.ref)
        assertEquals("OD123456789012345", parse("Flipkart", "Your order OD123456789012345 is out for delivery")?.ref)
        assertEquals("1234567890", parse("Delhivery", "Your shipment is out for delivery. AWB: 1234567890")?.ref)
        assertNull(parse("Swiggy", "Your delivery partner is on the way")?.ref)
    }

    @Test
    fun `the item is found`() {
        assertEquals("Wireless earbuds", parse("Delivered: Wireless earbuds", "Your package was delivered")?.title)
        assertEquals("Running shoes", parse("Myntra", "Your order for Running shoes has been shipped.")?.title)
        assertEquals("Phone case", parse("Amazon", "\"Phone case\" is out for delivery")?.title)
        assertNull(parse("Amazon", "Your order has been shipped")?.title)
    }

    @Test
    fun `arrival times are read`() {
        assertEquals("by 8 pm", parse("Amazon", "Out for delivery. Arriving today by 8 pm")?.eta)
        assertEquals("in 12 min", parse("Swiggy", "Your order is on the way. Arriving in 12 mins")?.eta)
        assertEquals("tomorrow", parse("Flipkart", "Shipped. Delivery expected tomorrow")?.eta)
        assertEquals("12 oct", parse("Flipkart", "Your order has shipped and will arrive by 12 Oct")?.eta)
        assertNull(parse("Amazon", "Your order has been delivered today")?.eta)
    }

    @Test
    fun `only delivery otps are kept`() {
        assertEquals("4821", parse("Blinkit", "Your order is out for delivery. Share OTP 4821 with the delivery partner")?.otp)
        assertNull(parse("Amazon", "Your order is out for delivery. Your login OTP is 4821")?.otp)
    }

    private fun update(status: OrderStatus, ref: String? = "R1", title: String? = "Item", eta: String? = null, time: Long, merchant: String = "Amazon") =
        OrderUpdate(ref, title, status, eta, null, merchant, "pkg", false, time)

    @Test
    fun `updates with an order number fold into one order and only move forward`() {
        var list = emptyList<Order>()
        list = mergeOrder(list, update(OrderStatus.PLACED, time = 1_000))
        list = mergeOrder(list, update(OrderStatus.SHIPPED, time = 2_000))
        list = mergeOrder(list, update(OrderStatus.OUT_FOR_DELIVERY, eta = "by 8 pm", time = 3_000))
        assertEquals(1, list.size)
        assertEquals(OrderStatus.OUT_FOR_DELIVERY, list[0].status)
        assertEquals("by 8 pm", list[0].eta)
        // A late "shipped" repeat does not move it back.
        list = mergeOrder(list, update(OrderStatus.SHIPPED, time = 4_000))
        assertEquals(OrderStatus.OUT_FOR_DELIVERY, list[0].status)
        list = mergeOrder(list, update(OrderStatus.DELIVERED, time = 5_000))
        assertEquals(OrderStatus.DELIVERED, list[0].status)
        assertNull(list[0].eta)
        // Delivered never reverts, but can be returned.
        list = mergeOrder(list, update(OrderStatus.SHIPPED, time = 6_000))
        assertEquals(OrderStatus.DELIVERED, list[0].status)
        list = mergeOrder(list, update(OrderStatus.RETURNED, time = 7_000))
        assertEquals(OrderStatus.RETURNED, list[0].status)
    }

    @Test
    fun `an update with no order number goes to the merchant's open order`() {
        var list = mergeOrder(emptyList(), update(OrderStatus.SHIPPED, ref = null, title = "Veg thali", merchant = "Swiggy", time = 1_000))
        list = mergeOrder(list, update(OrderStatus.OUT_FOR_DELIVERY, ref = null, title = null, merchant = "Swiggy", eta = "in 12 min", time = 2_000))
        assertEquals(1, list.size)
        assertEquals("Veg thali", list[0].title)
        assertEquals(OrderStatus.OUT_FOR_DELIVERY, list[0].status)
        assertEquals("in 12 min", list[0].eta)
    }

    @Test
    fun `different merchants and different order numbers are different orders`() {
        var list = mergeOrder(emptyList(), update(OrderStatus.SHIPPED, ref = "A", merchant = "Amazon", time = 1_000))
        list = mergeOrder(list, update(OrderStatus.SHIPPED, ref = "B", merchant = "Amazon", time = 2_000))
        list = mergeOrder(list, update(OrderStatus.SHIPPED, ref = "A", merchant = "Flipkart", time = 3_000))
        assertEquals(3, list.size)
    }

    @Test
    fun `old orders are dropped and the lists split`() {
        val day = 24L * 60 * 60 * 1000
        var list = mergeOrder(emptyList(), update(OrderStatus.DELIVERED, ref = "OLD", time = 1_000), now = 1_000)
        list = mergeOrder(list, update(OrderStatus.SHIPPED, ref = "NEW", time = 100 * day), now = 100 * day)
        assertEquals(listOf("NEW"), list.map { it.key.substringAfter('|') })
        list = mergeOrder(list, update(OrderStatus.DELIVERED, ref = "D", time = 100 * day + 1), now = 100 * day + 1)
        assertEquals(listOf("NEW"), arrivingOrders(list).map { it.key.substringAfter('|') })
        assertEquals(listOf("D"), pastOrders(list).map { it.key.substringAfter('|') })
    }

    @Test
    fun `arriving is ordered by progress`() {
        var list = mergeOrder(emptyList(), update(OrderStatus.PLACED, ref = "A", time = 3_000))
        list = mergeOrder(list, update(OrderStatus.OUT_FOR_DELIVERY, ref = "B", time = 1_000))
        list = mergeOrder(list, update(OrderStatus.SHIPPED, ref = "C", time = 2_000))
        assertEquals(listOf("B", "C", "A"), arrivingOrders(list).map { it.key.substringAfter('|') })
        assertTrue(OrderStatus.DELIVERED.closed)
        assertFalse(OrderStatus.SHIPPED.closed)
    }

    @Test
    fun `orders round trip through the codec`() {
        val o = Order("amazon|402-1234567-7654321", "Amazon", "Phone\tcase", OrderStatus.OUT_FOR_DELIVERY, "by 8 pm", "4821", "pkg", false, 1L, 2L)
        val back = ShoppingCodec.decode(ShoppingCodec.encode(o))!!
        assertEquals(o.copy(title = "Phone case"), back)
        assertNull(ShoppingCodec.decode("junk"))
        val food = o.copy(food = true, eta = null, otp = null)
        assertEquals(food.copy(title = "Phone case"), ShoppingCodec.decode(ShoppingCodec.encode(food)))
    }

    @Test
    fun `a store is found in a text and in an app label`() {
        assertEquals("Amazon", storeIn("Your Amazon order has shipped")?.name)
        assertEquals("Swiggy", storeIn("Swiggy Instamart: out for delivery")?.name)
        assertEquals("Blue Dart", storeIn("Your Blue Dart shipment")?.name)
        assertNull(storeIn("Mom: bring milk"))
        assertEquals(ShoppingAppKind.FOOD, builtInShoppingKind("some.pkg", "Zomato: Food Delivery"))
        assertEquals(ShoppingAppKind.SHOPPING, builtInShoppingKind("in.amazon.mShop.android.shopping", "Amazon Shopping"))
        assertEquals(ShoppingAppKind.COURIER, builtInShoppingKind("x.y", "DTDC Tracker"))
        assertNull(builtInShoppingKind("com.google.android.gm", "Gmail"))
    }

    @Test
    fun `items are found in real sms wording and a sender code is never one`() {
        assertEquals("Running shoes", parse("MYNTRA", "Your Myntra order for Running shoes has been shipped. Order ID OD123456789012345.", titleIsItem = false)?.title)
        assertEquals("Veg thali", parse("SWIGGY", "Your Swiggy order Veg thali is on the way. Arriving in 12 mins.", titleIsItem = false)?.title)
        assertNull(parse("AMZNIN", "Your Amazon order has been shipped. Order #402-1234567-7654321", titleIsItem = false)?.title)
        assertEquals("Wireless earbuds", parse("AMZNIN", "Your Amazon order #402-1234567-7654321 for Wireless earbuds is out for delivery.", titleIsItem = false)?.title)
        // Even when a title may name the item, a bare sender code does not.
        assertNull(parse("AMZNIN", "Your Amazon order has been shipped")?.title)
        // An app's own title still can.
        assertEquals("Wireless earbuds", parse("Wireless earbuds", "Out for delivery today")?.title)
    }

    @Test
    fun `amazon's other apps are not shopping, its shopping entries and samsung shop are`() {
        val amazonPkg = "in.amazon.mShop.android.shopping"
        assertEquals(ShoppingAppKind.SHOPPING, builtInShoppingKind(amazonPkg, "Amazon"))
        assertEquals(ShoppingAppKind.SHOPPING, builtInShoppingKind(amazonPkg, "Amazon Shopping"))
        assertEquals(ShoppingAppKind.SHOPPING, builtInShoppingKind(amazonPkg, "Amazon Now"))
        assertEquals(ShoppingAppKind.SHOPPING, builtInShoppingKind(amazonPkg, "Amazon Fresh"))
        assertNull(builtInShoppingKind(amazonPkg, "Amazon Pay"))
        assertNull(builtInShoppingKind("com.amazon.mp3", "Amazon Music"))
        assertNull(builtInShoppingKind("com.amazon.avod.thirdpartyclient", "Prime Video"))
        assertNull(builtInShoppingKind("com.amazon.dee.app", "Amazon Alexa"))
        assertNull(builtInShoppingKind("com.amazon.kindle", "Kindle"))
        assertNull(builtInShoppingKind("com.audible.application", "Amazon Audible"))
        assertEquals(ShoppingAppKind.SHOPPING, builtInShoppingKind("com.samsung.x", "Samsung Shop"))
        assertEquals("Samsung Shop", storeIn("Samsung Shop: your order has shipped")?.name)
    }

    @Test
    fun `the tile shows the marked apps, or all when none is marked`() {
        val apps = listOf(ShoppingApp("a", "A", ShoppingAppKind.SHOPPING), ShoppingApp("b", "B", ShoppingAppKind.FOOD), ShoppingApp("c", "C", ShoppingAppKind.COURIER))
        assertEquals(listOf("a", "b", "c"), appsOnTile(apps, emptySet()).map { it.packageName })
        assertEquals(listOf("a", "c"), appsOnTile(apps, setOf("c", "a")).map { it.packageName })
        assertEquals(listOf("a", "b", "c"), appsOnTile(apps, setOf("gone")).map { it.packageName })
    }

    @Test
    fun `store promotions become deals and codes do not`() {
        val d = dealOf("8 PM Deals are live now!", "Unmissable deals from 8 PM to Midnight!", "in.amazon.mShop.android.shopping", "Amazon", false, 5_000L)
        assertEquals("8 PM Deals are live now!", d?.title)
        assertEquals("Unmissable deals from 8 PM to Midnight!", d?.text)
        assertNull(parse("8 PM Deals are live now!", "Unmissable deals from 8 PM to Midnight!"))
        assertNull(dealOf("Amazon", "Your OTP for login is 482910", "p", "Amazon", false, 1L))
        assertNull(dealOf("", "  ", "p", "Amazon", false, 1L))
        // A message that only repeats its title shows once.
        assertEquals("", dealOf("Flat 20% off", "flat 20% off", "p", "Myntra", false, 1L)?.text)
        // No title: the text is the headline.
        assertEquals("Sale ends tonight", dealOf("", "Sale ends tonight", "p", "Myntra", false, 1L)?.title)
    }

    @Test
    fun `a store's chat or deal is claimed by shopping, a friend's chat is not`() {
        // A business chat from a store, an order update and a promotion: shopping's, so people leaves them out.
        assertEquals(true, shoppingClaims("com.whatsapp", "AJIO", "Your order has been shipped. Order ID: 12345-678"))
        assertEquals(true, shoppingClaims("com.whatsapp", "AJIO", "Flat 50% off on all styles, today only!"))
        // A friend, or a store chat that is neither an order nor a deal, stays in people.
        assertEquals(false, shoppingClaims("com.whatsapp", "Anand", "Flat 50% off at ajio, check it out"))
        assertEquals(false, shoppingClaims("com.whatsapp", "AJIO", "Your OTP for login is 482910"))
        // An app that is not a chat, SMS, mail or shopping app is never claimed.
        assertEquals(false, shoppingClaims("com.example.notes", "AJIO", "Flat 50% off on all styles"))
    }

    @Test
    fun `deals merge newest first, dedupe and expire`() {
        val day = 24L * 60 * 60 * 1000
        fun deal(t: String, time: Long) = dealOf(t, "x", "p", "Amazon", false, time)!!
        val a = deal("a", 1_000L)
        val b = deal("b", 2_000L)
        var list = mergeDeal(mergeDeal(emptyList(), a), b)
        assertEquals(listOf("b", "a"), list.map { it.title })
        // The same message again moves to the front once, not twice.
        list = mergeDeal(list, deal("a", 3_000L))
        assertEquals(listOf("a", "b"), list.map { it.title })
        assertEquals(3_000L, list.first().time)
        // Old ones drop.
        list = mergeDeal(list, deal("c", 3_000L + 15 * day))
        assertEquals(listOf("c"), list.map { it.title })
        // Capped.
        var many = emptyList<Deal>()
        for (i in 0 until 70) many = mergeDeal(many, deal("d$i", 10_000L + i))
        assertEquals(MAX_DEALS, many.size)
        assertEquals("d69", many.first().title)
    }

    @Test
    fun `deal codec round trips`() {
        val d = dealOf("Big\ttitle", "line one\nline two", "pkg", "Zomato", true, 42L)!!
        val back = DealCodec.decode(DealCodec.encode(d))
        assertEquals(d.key, back?.key)
        assertEquals("Big title", back?.title)
        assertEquals("line one\nline two", back?.text)
        assertTrue(back?.food == true)
        assertEquals(42L, back?.time)
        assertNull(DealCodec.decode("too\tfew"))
    }

    @Test
    fun `an order keeps its latest full message`() {
        val a = parse("Amazon", "Your package is out for delivery with our agent.\n\nOrder ID: 407-2341856-1256365", titleIsItem = false)!!
        assertEquals("Your package is out for delivery with our agent.\n\nOrder ID: 407-2341856-1256365", a.message)
        val d = parse("Delivered: Phone case", "Your Amazon package was delivered. Order #402-7654321-1111111", time = 9_000L)!!
        assertEquals("Delivered: Phone case\nYour Amazon package was delivered. Order #402-7654321-1111111", d.message)
        // Merged: the order shows the newest message.
        val merged = mergeOrder(mergeOrder(emptyList(), a.copy(ref = "x", time = 1_000L)), d.copy(ref = "x", time = 9_000L))
        assertEquals(d.message, merged.single().message)
        // A repeat of the title adds no second line.
        assertEquals("Order shipped", messageOf("Order shipped", "order shipped"))
        assertEquals("Only a title", messageOf("Only a title", ""))
    }

    @Test
    fun `order codec keeps the message and reads older lines without one`() {
        val o = mergeOrder(emptyList(), parse("Amazon", "Out for delivery today.\nOrder ID: 407-2341856-1256365", titleIsItem = false)!!).single()
        val back = ShoppingCodec.decode(ShoppingCodec.encode(o))!!
        assertEquals(o.message, back.message)
        // A line saved before messages were kept (ten columns).
        val old = ShoppingCodec.encode(o).split("\t").take(10).joinToString("\t")
        assertEquals("", ShoppingCodec.decode(old)!!.message)
    }
}
