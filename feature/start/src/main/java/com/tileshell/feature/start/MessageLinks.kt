package com.tileshell.feature.start

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.style.TextDecoration

/** A web address or email address found in a message: its place in the text and what opening it should load. */
internal data class MessageLink(val start: Int, val end: Int, val url: String)

private val URL_PATTERN = Regex("""(?i)\b(?:https?://|www\.)[^\s<>"']+""")
private val EMAIL_PATTERN = Regex("""[A-Za-z0-9._%+-]+@[A-Za-z0-9-]+(?:\.[A-Za-z0-9-]+)+""")
private const val TRAILING_PUNCTUATION = ".,;:!?)]}"

/**
 * The links in [text], in order: http(s):// and www. addresses (sentence punctuation after them is left out, and a
 * www. address opens as https), then email addresses (opened as mailto:) that are not part of a web address. Pure,
 * so it is unit-tested.
 */
internal fun findLinks(text: String): List<MessageLink> {
    val links = ArrayList<MessageLink>()
    for (match in URL_PATTERN.findAll(text)) {
        var end = match.range.last + 1
        while (end > match.range.first && text[end - 1] in TRAILING_PUNCTUATION) {
            // A closing bracket belongs to the address when it has its own opening bracket inside it.
            val ch = text[end - 1]
            val open = when (ch) { ')' -> '('; ']' -> '['; '}' -> '{'; else -> null }
            if (open != null && text.substring(match.range.first, end).count { it == open } >= text.substring(match.range.first, end).count { it == ch }) break
            end--
        }
        val raw = text.substring(match.range.first, end)
        if (raw.length <= "www.".length) continue
        links += MessageLink(match.range.first, end, if (raw.startsWith("www.", ignoreCase = true)) "https://$raw" else raw)
    }
    for (match in EMAIL_PATTERN.findAll(text)) {
        val start = match.range.first
        val end = match.range.last + 1
        if (links.any { start < it.end && it.start < end }) continue
        links += MessageLink(start, end, "mailto:${match.value}")
    }
    return links.sortedBy { it.start }
}

/** [text] with its links underlined and tappable; [onOpen] gets the address to open. */
internal fun linkifiedMessage(text: String, linkColor: Color, onOpen: (String) -> Unit): AnnotatedString {
    val links = findLinks(text)
    if (links.isEmpty()) return AnnotatedString(text)
    val style = TextLinkStyles(SpanStyle(color = linkColor, textDecoration = TextDecoration.Underline))
    return buildAnnotatedString {
        var at = 0
        for (link in links) {
            append(text.substring(at, link.start))
            withLink(LinkAnnotation.Clickable(tag = link.url, styles = style, linkInteractionListener = { onOpen(link.url) })) {
                append(text.substring(link.start, link.end))
            }
            at = link.end
        }
        append(text.substring(at))
    }
}
