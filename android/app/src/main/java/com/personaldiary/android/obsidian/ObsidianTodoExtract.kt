package com.personaldiary.android.obsidian

import com.personaldiary.android.data.ObsidianTodo

/**
 * Mirrors obsidian-diary-todo-board extract rules:
 * ordered list items with a head status tag; skip completed label.
 */
data class TagRule(
    val open: String = "【",
    val close: String = "】",
    val completedLabel: String = "已完成",
    val boldCompleted: Boolean = true,
)

object ObsidianTodoExtract {
    private val ORDERED_PREFIX = Regex("""^(\s*)(\d+)\.\s+(.*)$""")

    fun extract(
        filePath: String,
        markdown: String,
        rule: TagRule = TagRule(),
    ): List<ObsidianTodo> {
        val lines = markdown.split("\r\n", "\n")
        val results = mutableListOf<ObsidianTodo>()
        var inFence = false
        for (i in lines.indices) {
            val line = lines[i]
            if (line.trimStart().startsWith("```") || line.trimStart().startsWith("~~~")) {
                inFence = !inFence
                continue
            }
            if (inFence) continue
            if (line.trimStart().startsWith(">")) continue
            val m = ORDERED_PREFIX.matchEntire(line) ?: continue
            val indent = m.groupValues[1]
            val number = m.groupValues[2]
            val rest = m.groupValues[3]
            val head = parseHeadTag(rest, rule) ?: continue
            if (head.tagInner == rule.completedLabel) continue
            val content = head.after.trim()
            if (content.isEmpty()) continue
            results += ObsidianTodo(
                filePath = filePath,
                lineIndex = i,
                originalLine = line,
                content = content,
                indent = indent,
                number = number,
                tagInner = head.tagInner,
            )
        }
        return results
    }

    fun toCompletedLine(originalLine: String, rule: TagRule = TagRule()): String? {
        val m = ORDERED_PREFIX.matchEntire(originalLine) ?: return null
        val indent = m.groupValues[1]
        val number = m.groupValues[2]
        val rest = m.groupValues[3]
        val head = parseHeadTag(rest, rule) ?: return null
        if (head.tagInner == rule.completedLabel) return null
        var gap = if (head.after.isNotEmpty()) head.gap else ""
        if (rule.boldCompleted && head.after.isNotEmpty() && gap.isEmpty()) gap = " "
        val tag = formatCompletedTag(rule)
        return "$indent$number. $tag$gap${head.after}".trimEnd()
    }

    fun formatCompletedTag(rule: TagRule): String {
        val core = "${rule.open}${rule.completedLabel}${rule.close}"
        return if (rule.boldCompleted) "**$core**" else core
    }

    private data class Head(
        val tagInner: String,
        val gap: String,
        val after: String,
    )

    private fun parseHeadTag(rest: String, rule: TagRule): Head? {
        val o = Regex.escape(rule.open)
        val c = Regex.escape(rule.close)
        val re = Regex(
            """^(\*{1,2}|_{1,2})?\s*($o((?:(?!$c).)*)$c)(\*{1,2}|_{1,2})?(\s*)(.*)$""",
        )
        val m = re.matchEntire(rest.trim()) ?: return null
        return Head(
            tagInner = m.groupValues[3].trim(),
            gap = m.groupValues[5],
            after = m.groupValues[6],
        )
    }
}
