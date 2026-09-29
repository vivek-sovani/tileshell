package com.tileshell.feature.livetiles.money

import android.content.Context
import android.os.Handler
import android.os.HandlerThread
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

/**
 * The money hub's transactions, kept only on this phone in
 * `files/money_log.txt` (one tab-separated line each), newest first, for a
 * year. Writes happen on a background thread; [transactions] is the live list.
 */
object MoneyStore {
    private const val FILE = "money_log.txt"
    const val KEEP_MS = 365L * 24 * 60 * 60 * 1000

    private val _transactions = MutableStateFlow<List<MoneyTxn>>(emptyList())
    val transactions: StateFlow<List<MoneyTxn>> = _transactions.asStateFlow()

    @Volatile private var loaded = false
    private val thread by lazy { HandlerThread("money-store").apply { start() } }
    private val handler by lazy { Handler(thread.looper) }

    /** Loads the log once per process (idempotent), off the main thread. */
    fun ensureLoaded(context: Context) {
        if (loaded) return
        loaded = true
        val app = context.applicationContext
        handler.post {
            val lines = runCatching { File(app.filesDir, FILE).readLines() }.getOrDefault(emptyList())
            val now = System.currentTimeMillis()
            _transactions.value = lines.mapNotNull(MoneyCodec::decode)
                .filter { now - it.time <= KEEP_MS }
                .sortedByDescending { it.time }
        }
    }

    /** Adds [txn] unless it repeats a recent one (see [isDuplicateTxn]). */
    fun add(context: Context, txn: MoneyTxn) {
        ensureLoaded(context)
        val app = context.applicationContext
        handler.post {
            val current = _transactions.value
            if (isDuplicateTxn(txn, current)) return@post
            val now = System.currentTimeMillis()
            val next = (listOf(txn) + current).filter { now - it.time <= KEEP_MS }.sortedByDescending { it.time }
            _transactions.value = next
            write(app, next)
        }
    }

    /** Removes one transaction (swiped away in the hub). */
    fun remove(context: Context, txn: MoneyTxn) {
        val app = context.applicationContext
        handler.post {
            val next = _transactions.value.filterNot { it == txn }
            _transactions.value = next
            write(app, next)
        }
    }

    /** Puts back a transaction just removed (the hub's "undo"). */
    fun restore(context: Context, txn: MoneyTxn) {
        val app = context.applicationContext
        handler.post {
            if (txn in _transactions.value) return@post
            val next = (_transactions.value + txn).sortedByDescending { it.time }
            _transactions.value = next
            write(app, next)
        }
    }

    fun clear(context: Context) {
        val app = context.applicationContext
        handler.post {
            _transactions.value = emptyList()
            runCatching { File(app.filesDir, FILE).delete() }
        }
    }

    private fun write(context: Context, list: List<MoneyTxn>) {
        runCatching {
            val file = File(context.filesDir, FILE)
            val tmp = File(context.filesDir, "$FILE.tmp")
            tmp.writeText(list.joinToString("\n", transform = MoneyCodec::encode))
            tmp.renameTo(file)
        }
    }
}

/** One [MoneyTxn] per line, tab-separated; blank fields for nulls. Pure, unit-tested. */
object MoneyCodec {
    fun encode(t: MoneyTxn): String = listOf(
        t.time.toString(), t.amountPaise.toString(), if (t.credit) "c" else "d",
        clean(t.counterparty), t.account.orEmpty(), t.bank.orEmpty(), t.method.orEmpty(),
        t.balancePaise?.toString().orEmpty(), clean(t.sourcePackage),
        clean(t.sender), escape(t.message),
    ).joinToString("\t")

    fun decode(line: String): MoneyTxn? {
        val f = line.split("\t")
        if (f.size < 9) return null
        return MoneyTxn(
            time = f[0].toLongOrNull() ?: return null,
            amountPaise = f[1].toLongOrNull() ?: return null,
            credit = f[2] == "c",
            counterparty = f[3],
            account = f[4].ifEmpty { null },
            bank = f[5].ifEmpty { null },
            method = f[6].ifEmpty { null },
            balancePaise = f[7].toLongOrNull(),
            sourcePackage = f[8],
            sender = f.getOrNull(9).orEmpty(),
            message = f.getOrNull(10)?.let(::unescape).orEmpty(),
        )
    }

    private fun clean(s: String) = s.replace('\t', ' ').replace('\n', ' ')

    /** Keeps a multi-line message on one line: backslash, newline and tab escaped. */
    internal fun escape(s: String): String = buildString {
        s.forEach { c ->
            when (c) {
                '\\' -> append("\\\\")
                '\n' -> append("\\n")
                '\t' -> append("\\t")
                '\r' -> Unit
                else -> append(c)
            }
        }
    }

    internal fun unescape(s: String): String = buildString {
        var i = 0
        while (i < s.length) {
            val c = s[i]
            if (c == '\\' && i + 1 < s.length) {
                when (s[i + 1]) {
                    'n' -> append('\n')
                    't' -> append('\t')
                    else -> append(s[i + 1])
                }
                i += 2
            } else {
                append(c)
                i++
            }
        }
    }
}
