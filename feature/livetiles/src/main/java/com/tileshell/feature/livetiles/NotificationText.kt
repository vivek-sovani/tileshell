package com.tileshell.feature.livetiles

/** Longest notification text kept for the People hub's expanded row. */
internal const val MAX_FULL_NOTIFICATION_TEXT = 1000

/** How many of a chat's newest messages the expanded row shows. */
internal const val MAX_FULL_NOTIFICATION_MESSAGES = 5

/**
 * The whole message behind a notification, for the People hub's expanded row.
 * Apps often put only a one-line preview in `EXTRA_TEXT` and the real thing
 * elsewhere: a chat's recent messages (`EXTRA_MESSAGES`, oldest first), a long
 * email or message (`EXTRA_BIG_TEXT`), or an inbox list (`EXTRA_TEXT_LINES`).
 * Takes the first of those that exists, never something shorter than [text],
 * and caps the length. Null when there is nothing beyond [text]. Pure.
 */
internal fun fullNotificationText(
    text: String?,
    bigText: String?,
    messages: List<String>,
    lines: List<String>,
): String? {
    val short = text.orEmpty().trim()
    val candidates = listOf(
        messages.map { it.trim() }.filter { it.isNotEmpty() }.takeLast(MAX_FULL_NOTIFICATION_MESSAGES).joinToString("\n"),
        bigText.orEmpty().trim(),
        lines.map { it.trim() }.filter { it.isNotEmpty() }.joinToString("\n"),
    )
    val best = candidates.firstOrNull { it.isNotEmpty() } ?: return null
    val chosen = if (best.length < short.length) short else best
    if (chosen == short) return null
    return if (chosen.length > MAX_FULL_NOTIFICATION_TEXT) chosen.take(MAX_FULL_NOTIFICATION_TEXT - 1).trimEnd() + "…" else chosen
}

/** How many of one notification's messages get their own row in the People hub. */
internal const val MAX_MESSAGE_ROWS = 5

/**
 * What to call a message's sender on its own row: the person's name, with the
 * group's name after it for a group chat ("asha · family"). Falls back to the
 * notification's own title when the message names nobody. Pure.
 */
internal fun messageSenderLabel(person: String?, conversationTitle: String?, isGroup: Boolean, fallbackTitle: String): String {
    val who = person?.trim().orEmpty()
    val group = conversationTitle?.trim().orEmpty()
    return when {
        who.isEmpty() -> group.ifEmpty { fallbackTitle.trim() }
        isGroup && group.isNotEmpty() && !who.equals(group, ignoreCase = true) -> "$who · $group"
        else -> who
    }
}
