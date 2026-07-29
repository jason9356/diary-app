package com.personaldiary.android.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle

/**
 * Display-only Markdown polish. Never written back to COS / disk.
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
    private val multiSpace = Regex("""[ \t]{2,}""")
    private val multiBlank = Regex("""\n{3,}""")
    private val heading = Regex("""^\s{0,3}#{1,6}\s+""", RegexOption.MULTILINE)
    private val quote = Regex("""^\s*>\s?""", RegexOption.MULTILINE)
    private val bullet = Regex("""^\s*[-*+]\s+""", RegexOption.MULTILINE)
    private val ordered = Regex("""^\s*\d+\.\s+""", RegexOption.MULTILINE)

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
        s = multiSpace.replace(s, " ")
        s = multiBlank.replace(s, "\n\n")
        return s.trim()
    }

    /**
     * Theme-colored annotated text for reading — avoids RichText baking black spans.
     */
    fun toAnnotated(raw: String, color: Color): AnnotatedString {
        val prepared = forReading(raw)
            .let { heading.replace(it, "") }
            .let { quote.replace(it, "") }
            .let { bullet.replace(it, "• ") }
            .let { ordered.replace(it, "• ") }
            .trim()
            .ifBlank { "（空）" }

        return buildAnnotatedString {
            var i = 0
            while (i < prepared.length) {
                when {
                    prepared.startsWith("**", i) || prepared.startsWith("__", i) -> {
                        val marker = prepared.substring(i, i + 2)
                        val end = prepared.indexOf(marker, i + 2)
                        if (end > i) {
                            withStyle(SpanStyle(color = color, fontWeight = FontWeight.SemiBold)) {
                                append(prepared.substring(i + 2, end))
                            }
                            i = end + 2
                        } else {
                            withStyle(SpanStyle(color = color)) { append(marker) }
                            i += 2
                        }
                    }
                    prepared[i] == '*' || prepared[i] == '_' -> {
                        val marker = prepared[i]
                        val end = prepared.indexOf(marker, i + 1)
                        if (end > i && !prepared.substring(i + 1, end).contains('\n')) {
                            withStyle(SpanStyle(color = color, fontStyle = FontStyle.Italic)) {
                                append(prepared.substring(i + 1, end))
                            }
                            i = end + 1
                        } else {
                            withStyle(SpanStyle(color = color)) { append(marker) }
                            i += 1
                        }
                    }
                    else -> {
                        val next = nextSpecial(prepared, i)
                        withStyle(SpanStyle(color = color)) {
                            append(prepared.substring(i, next))
                        }
                        i = next
                    }
                }
            }
        }
    }

    private fun nextSpecial(text: String, from: Int): Int {
        for (j in (from + 1) until text.length) {
            if (text[j] == '*' || text[j] == '_') return j
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
