package com.sparkbox.android.data

import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneOffset

/** Inspiration card (formerly diary note). Stored as Markdown with UUID id. */
data class SparkEntry(
    val id: String,
    val entryDate: String,
    val title: String = entryDate,
    val body: String = "",
    val createdAt: String = SparkDates.nowIso(),
    val updatedAt: String = SparkDates.nowIso(),
    val writingDurationSec: Int = 0,
    val imageRels: List<String> = emptyList(),
    val tags: List<String> = emptyList(),
    val pinned: Boolean = false,
)

typealias InspirationCard = SparkEntry

data class DayContext(
    val date: String,
    val location: String = "",
    val weather: String = "",
    val tempC: Double? = null,
    /** Short device label, e.g. OPPO Find X / Windows PC */
    val device: String = "",
    val contextSource: String = "",
    val contextUpdatedAt: String = "",
    val updatedAt: String = "",
) {
    val hasContext: Boolean
        get() = location.isNotBlank() || weather.isNotBlank() || tempC != null || device.isNotBlank()

    fun weatherLine(): String {
        val wx = weather.trim()
        return when {
            tempC != null && wx.isNotEmpty() -> "$wx ${SparkDates.formatTemp(tempC)}"
            tempC != null -> SparkDates.formatTemp(tempC)
            else -> wx
        }
    }

    fun contextLine(): String {
        val parts = mutableListOf<String>()
        if (location.isNotBlank()) parts += location
        val w = weatherLine()
        if (w.isNotBlank()) parts += w
        if (device.isNotBlank()) parts += device
        return parts.joinToString(" · ")
    }
}

data class WeatherSnapshot(
    val location: String,
    val weather: String,
    val tempC: Double,
)

data class TimelineDay(
    val date: String,
    val context: DayContext,
    val notes: List<SparkEntry>,
)

data class NativeTodo(
    val id: String,
    val text: String,
    val detail: String = "",
    val done: Boolean = false,
    /** task | note | errand | other */
    val kind: String = "task",
    /** ISO date or datetime; blank if none */
    val dueAt: String = "",
    /** 0 none, 1..3 low/med/high */
    val priority: Int = 0,
    /** 0 none, 1..3 low/med/high */
    val urgency: Int = 0,
    val createdAt: String = SparkDates.nowIso(),
    val updatedAt: String = SparkDates.nowIso(),
)

object SparkDates {
    fun today(): String = LocalDate.now().toString()

    fun nowIso(): String = OffsetDateTime.now(ZoneOffset.UTC).toString()

    fun formatTemp(t: Double): String {
        val s = if (t % 1.0 == 0.0) t.toInt().toString() else t.toString()
        return "${s}°"
    }
}

/** Image markdown helpers — keep links on disk, hide broken previews in the editor. */
object MarkdownImages {
    private val imgRe = Regex("""!\[[^\]]*]\([^)]+\)""")

    fun extract(body: String): List<String> =
        imgRe.findAll(body).map { it.value }.toList()

    fun stripForDisplay(body: String): String =
        body.replace(imgRe, "［图片］")
            .replace(Regex("\n{3,}"), "\n\n")
            .trim()

    fun mergeEditorText(editorMarkdown: String, canonicalBody: String): String {
        val text = editorMarkdown
            .replace(imgRe, "")
            .replace("［图片］", "")
            .replace(Regex("\n{3,}"), "\n\n")
            .trim()
        val images = extract(canonicalBody)
        return when {
            text.isBlank() && images.isEmpty() -> ""
            text.isBlank() -> images.joinToString("\n\n")
            images.isEmpty() -> text
            else -> text + "\n\n" + images.joinToString("\n\n")
        }
    }

    /** Pull #tags from body lines (not in code fences). */
    fun extractHashTags(body: String): List<String> {
        val tags = linkedSetOf<String>()
        var inFence = false
        for (line in body.lineSequence()) {
            if (line.trimStart().startsWith("```") || line.trimStart().startsWith("~~~")) {
                inFence = !inFence
                continue
            }
            if (inFence) continue
            HASH_TAG.findAll(line).forEach { tags += it.groupValues[1] }
        }
        return tags.toList()
    }

    private val HASH_TAG = Regex("""(?<!\w)#([\w\u4e00-\u9fff\-]+)""")
}
