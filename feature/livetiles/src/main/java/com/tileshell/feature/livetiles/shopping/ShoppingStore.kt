package com.tileshell.feature.livetiles.shopping

import android.content.Context
import android.os.Handler
import android.os.HandlerThread
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

/**
 * The shopping hub's orders, kept only on this phone in `files/shopping_log.txt` (one tab-separated line each),
 * newest update first. Writes happen on a background thread; [orders] is the live list. Not in backups.
 */
object ShoppingStore {
    private const val FILE = "shopping_log.txt"

    private val _orders = MutableStateFlow<List<Order>>(emptyList())
    val orders: StateFlow<List<Order>> = _orders.asStateFlow()

    @Volatile private var loaded = false
    private val thread by lazy { HandlerThread("shopping-store").apply { start() } }
    private val handler by lazy { Handler(thread.looper) }

    /** Loads the log once per process (idempotent), off the main thread. */
    fun ensureLoaded(context: Context) {
        if (loaded) return
        loaded = true
        val app = context.applicationContext
        handler.post {
            val lines = runCatching { File(app.filesDir, FILE).readLines() }.getOrDefault(emptyList())
            val now = System.currentTimeMillis()
            _orders.value = lines.mapNotNull(ShoppingCodec::decode)
                .filter { now - it.updated <= (if (it.status.closed) KEEP_CLOSED_MS else KEEP_OPEN_MS) }
                .sortedByDescending { it.updated }
        }
    }

    /** Folds one notification's update into the orders. */
    fun update(context: Context, u: OrderUpdate) {
        ensureLoaded(context)
        val app = context.applicationContext
        handler.post {
            val next = mergeOrder(_orders.value, u)
            if (next == _orders.value) return@post
            _orders.value = next
            write(app, next)
        }
    }

    /** Removes one order (the hub's ✕). */
    fun remove(context: Context, key: String) {
        val app = context.applicationContext
        handler.post {
            val next = _orders.value.filterNot { it.key == key }
            _orders.value = next
            write(app, next)
        }
    }

    fun clear(context: Context) {
        val app = context.applicationContext
        handler.post {
            _orders.value = emptyList()
            runCatching { File(app.filesDir, FILE).delete() }
        }
    }

    private fun write(context: Context, list: List<Order>) {
        runCatching {
            val file = File(context.filesDir, FILE)
            val tmp = File(context.filesDir, "$FILE.tmp")
            tmp.writeText(list.joinToString("\n", transform = ShoppingCodec::encode))
            tmp.renameTo(file)
        }
    }
}

/** One [Order] per line, tab-separated; blank fields for nulls. Pure, unit-tested. */
object ShoppingCodec {
    fun encode(o: Order): String = listOf(
        o.key, o.merchant, o.title, o.status.name, o.eta.orEmpty(), o.otp.orEmpty(), o.sourcePackage,
        if (o.food) "f" else "", o.firstSeen.toString(), o.updated.toString(),
    ).joinToString("\t") { it.replace('\t', ' ').replace('\n', ' ') }

    fun decode(line: String): Order? {
        val f = line.split("\t")
        if (f.size < 10) return null
        return Order(
            key = f[0],
            merchant = f[1],
            title = f[2],
            status = OrderStatus.entries.find { it.name == f[3] } ?: return null,
            eta = f[4].ifEmpty { null },
            otp = f[5].ifEmpty { null },
            sourcePackage = f[6],
            food = f[7] == "f",
            firstSeen = f[8].toLongOrNull() ?: return null,
            updated = f[9].toLongOrNull() ?: return null,
        )
    }
}
