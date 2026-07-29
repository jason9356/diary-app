package com.sparkbox.android.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp

/**
 * Display-only Markdown polish. Never written back to disk.
 * Softens Obsidian / CommonMark noise so mobile reading stays calm.
 */
object DisplayMarkdown {
    private val wikiAlias = Regex("""\[\[([^\]|\n]+)\|([^\]\n]+)\]\]""")
    private val wikiPlain = Regex("""\[\[([^\]\n]+)\]\]""")
    private val wikiEmbed = Regex("""!\[\[[^\]\n]+\]\]""")
    private val mdLink = Regex("""\[([^\]\n]+)]\(([^)\n]+)\)""")
    private val highlight = Regex("""==([^=\n]+)==""")
    private val strike = Regex("""~~([^~\n]+)~~""")
    private val htmlComment = Regex("""<!--[\s\S]*?-->""")
    private val bareUrl = Regex("""<(https?://[^>\s]+)>""")
    private val callout = Regex("""^\s*>\s*\[![^\]]+]\s*""", RegexOption.MULTILINE)
    private val multiBlank = Regex("""\n{3,}""")
    private val headingLine = Regex("""^\s{0,3}(#{1,6})\s+(.*)$""")
    private val quoteLine = Regex("""^\s*>\s?(.*)$""")
    private val unorderedLine = Regex("""^(\s*)[-*+]\s+(.*)$""")
    private val orderedLine = Regex("""^(\s*)(\d+)\.\s+(.*)$""")
    private val fenceLine = Regex("""^\s*```""")

    fun forReading(raw: String): String {
        var s = raw.trim()
        if (s.isEmpty()) return ""

        s = htmlComment.replace(s, "")
        s = wikiEmbed.replace(s, "［附件］")
        s = wikiAlias.replace(s, "$2")
        s = wikiPlain.replace(s, "$1")
        s = highlight.replace(s, "$1")
        s = strike.replace(s, "$1")
        s = bareUrl.replace(s, "$1")
        s = callout.replace(s, "")
        s = mdLink.replace(s) { m ->
            val label = m.groupValues[1].trim()
            label.ifBlank { m.groupValues[2].trim() }
        }
        s = unwrapOrphanMarkers(s, "**")
        s = unwrapOrphanMarkers(s, "__")
        s = multiBlank.replace(s, "\n\n")
        return s.trim()
    }

    /**
     * Theme-colored annotated text for reading — headings, lists, emphasis.
     */
    fun toAnnotated(raw: String, color: Color, baseSp: Float = 16f): AnnotatedString {
        val prepared = forReading(raw).ifBlank { "（空）" }
        val mute = color.copy(alpha = 0.72f)

        return buildAnnotatedString {
            var inFence = false
            val lines = prepared.lines()
            lines.forEachIndexed { index, line ->
                if (fenceLine.containsMatchIn(line)) {
                    inFence = !inFence
                    if (index < lines.lastIndex) append('\n')
                    return@forEachIndexed
                }
                if (inFence) {
                    withStyle(
                        SpanStyle(
                            color = mute,
                            fontFamily = FontFamily.Monospace,
                            fontSize = (baseSp * 0.92f).sp,
                        ),
                    ) {
                        append(line)
                    }
                    if (index < lines.lastIndex) append('\n')
                    return@forEachIndexed
                }

                val heading = headingLine.matchEntire(line)
                if (heading != null) {
                    val level = heading.groupValues[1].length.coerceIn(1, 6)
                    val text = heading.groupValues[2]
                    withStyle(
                        SpanStyle(
                            color = color,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = headingSize(baseSp, level),
                        ),
                    ) {
                        appendInline(text, color)
                    }
                    if (index < lines.lastIndex) append('\n')
                    return@forEachIndexed
                }

                val quote = quoteLine.matchEntire(line)
                if (quote != null) {
                    withStyle(SpanStyle(color = mute, fontStyle = FontStyle.Italic)) {
                        append("｜ ")
                        appendInline(quote.groupValues[1], mute)
                    }
                    if (index < lines.lastIndex) append('\n')
                    return@forEachIndexed
                }

                val unordered = unorderedLine.matchEntire(line)
                if (unordered != null) {
                    val indent = unordered.groupValues[1].length / 2
                    append("  ".repeat(indent))
                    withStyle(SpanStyle(color = color)) { append("• ") }
                    appendInline(unordered.groupValues[2], color)
                    if (index < lines.lastIndex) append('\n')
                    return@forEachIndexed
                }

                val ordered = orderedLine.matchEntire(line)
                if (ordered != null) {
                    val indent = ordered.groupValues[1].length / 2
                    val num = ordered.groupValues[2]
                    append("  ".repeat(indent))
                    withStyle(SpanStyle(color = color, fontWeight = FontWeight.Medium)) {
                        append("$num. ")
                    }
                    appendInline(ordered.groupValues[3], color)
                    if (index < lines.lastIndex) append('\n')
                    return@forEachIndexed
                }

                appendInline(line, color)
                if (index < lines.lastIndex) append('\n')
            }
        }
    }

    private fun AnnotatedString.Builder.appendInline(text: String, color: Color) {
        var i = 0
        while (i < text.length) {
            when {
                text.startsWith("**", i) || text.startsWith("__", i) -> {
                    val marker = text.substring(i, i + 2)
                    val end = text.indexOf(marker, i + 2)
                    if (end > i) {
                        withStyle(SpanStyle(color = color, fontWeight = FontWeight.SemiBold)) {
                            append(text.substring(i + 2, end))
                        }
                        i = end + 2
                    } else {
                        withStyle(SpanStyle(color = color)) { append(marker) }
                        i += 2
                    }
                }
                text.startsWith("`", i) -> {
                    val end = text.indexOf('`', i + 1)
                    if (end > i) {
                        withStyle(
                            SpanStyle(
                                color = color.copy(alpha = 0.88f),
                                fontFamily = FontFamily.Monospace,
                            ),
                        ) {
                            append(text.substring(i + 1, end))
                        }
                        i = end + 1
                    } else {
                        withStyle(SpanStyle(color = color)) { append('`') }
                        i += 1
                    }
                }
                text[i] == '*' || text[i] == '_' -> {
                    val marker = text[i]
                    val end = text.indexOf(marker, i + 1)
                    if (end > i && !text.substring(i + 1, end).contains('\n')) {
                        withStyle(SpanStyle(color = color, fontStyle = FontStyle.Italic)) {
                            append(text.substring(i + 1, end))
                        }
                        i = end + 1
                    } else {
                        withStyle(SpanStyle(color = color)) { append(marker) }
                        i += 1
                    }
                }
                else -> {
                    val next = nextSpecial(text, i)
                    withStyle(SpanStyle(color = color)) {
                        append(text.substring(i, next))
                    }
                    i = next
                }
            }
        }
    }

    private fun headingSize(baseSp: Float, level: Int): TextUnit =
        when (level) {
            1 -> (baseSp * 1.55f).sp
            2 -> (baseSp * 1.35f).sp
            3 -> (baseSp * 1.2f).sp
            4 -> (baseSp * 1.1f).sp
            else -> baseSp.sp
        }

    private fun nextSpecial(text: String, from: Int): Int {
        for (j in (from + 1) until text.length) {
            val c = text[j]
            if (c == '*' || c == '_' || c == '`') return j
        }
        return text.length
    }

    private fun unwrapOrphanMarkers(text: String, marker: String): String {
        if (marker.isEmpty()) return text
        val count = Regex(Regex.escape(marker)).findAll(text).count()
        if (count % 2 == 0) return text
        val idx = text.lastIndexOf(marker)
        if (idx < 0) return text
        return text.removeRange(idx, idx + marker.length)
    }
}
