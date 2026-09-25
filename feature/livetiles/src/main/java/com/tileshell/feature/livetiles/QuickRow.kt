package com.tileshell.feature.livetiles

import android.content.Context

/**
 * One shortcut in the productivity hub's "quick" row. The four built-ins are
 * the default row; the user can pin a task list, a note or an app to it, and
 * remove anything (user-requested).
 */
sealed class QuickItem(val code: String) {
    data object NewNote : QuickItem("note")
    data object NewTask : QuickItem("task")
    data object Calculator : QuickItem("calculator")
    data object Timer : QuickItem("timer")
    data class TaskList(val listId: String) : QuickItem("list:$listId")
    data class Note(val noteId: Long) : QuickItem("opennote:$noteId")
    data class App(val packageName: String) : QuickItem("app:$packageName")

    companion object {
        val BUILT_INS: List<QuickItem> = listOf(NewNote, NewTask, Calculator, Timer)

        /** Parses one stored code; null for anything unrecognised. */
        fun parse(code: String): QuickItem? = when {
            code == NewNote.code -> NewNote
            code == NewTask.code -> NewTask
            code == Calculator.code -> Calculator
            code == Timer.code -> Timer
            code.startsWith("list:") -> code.removePrefix("list:").takeIf { it.isNotBlank() }?.let(::TaskList)
            code.startsWith("opennote:") -> code.removePrefix("opennote:").toLongOrNull()?.let(::Note)
            code.startsWith("app:") -> code.removePrefix("app:").takeIf { it.isNotBlank() }?.let(::App)
            else -> null
        }
    }
}

/** The stored row: one code per line; the built-ins when nothing is stored yet. Pure. */
fun decodeQuickItems(raw: String?): List<QuickItem> =
    raw?.split('\n')?.mapNotNull { QuickItem.parse(it.trim()) }?.distinct() ?: QuickItem.BUILT_INS

fun encodeQuickItems(items: List<QuickItem>): String = items.joinToString("\n") { it.code }

/** [item] appended at the end, unless it's already in the row. Pure. */
fun addQuickItem(items: List<QuickItem>, item: QuickItem): List<QuickItem> =
    if (item in items) items else items + item

fun removeQuickItem(items: List<QuickItem>, item: QuickItem): List<QuickItem> = items - item

private const val QUICK_PREFS = "tileshell.prefs"
private const val QUICK_KEY = "productivity_quick_items"

fun loadQuickItems(context: Context): List<QuickItem> = runCatching {
    decodeQuickItems(context.getSharedPreferences(QUICK_PREFS, Context.MODE_PRIVATE).getString(QUICK_KEY, null))
}.getOrDefault(QuickItem.BUILT_INS)

fun saveQuickItems(context: Context, items: List<QuickItem>) {
    runCatching {
        context.getSharedPreferences(QUICK_PREFS, Context.MODE_PRIVATE)
            .edit().putString(QUICK_KEY, encodeQuickItems(items)).apply()
    }
}
