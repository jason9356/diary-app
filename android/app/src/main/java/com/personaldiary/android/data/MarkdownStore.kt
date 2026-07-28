package com.personaldiary.android.data

import java.io.File

/**
 * Shared layout with desktop:
 * diary/YYYY/MM/YYYY-MM-DD.md
 * assets/YYYY-MM-DD/<file>
 */
class MarkdownStore(private val diaryRoot: File) {

    data class FrontMatter(
        val date: String = "",
        val title: String = "",
        val id: String = "",
        val createdAt: String = "",
        val updatedAt: String = "",
        val location: String = "",
        val weather: String = "",
        val tempC: Double? = null,
        val contextSource: String = "",
        val contextUpdatedAt: String = "",
        val writingDurationSec: Int = 0,
    )

    init {
        diaryRoot.mkdirs()
    }

    fun pathFor(entryDate: String): File {
        val parts = entryDate.split("-")
        require(parts.size == 3) { "bad date $entryDate" }
        val (y, m, _) = parts
        return File(diaryRoot, "$y/$m/$entryDate.md")
    }

    fun exists(entryDate: String): Boolean = pathFor(entryDate).isFile

    fun readBody(entryDate: String): String {
        val f = pathFor(entryDate)
        if (!f.isFile) return ""
        val (body, _) = parse(f.readText(Charsets.UTF_8))
        return body
    }

    fun readRaw(entryDate: String): String {
        val f = pathFor(entryDate)
        return if (f.isFile) f.readText(Charsets.UTF_8) else ""
    }

    fun writeRaw(entryDate: String, markdown: String) {
        val file = pathFor(entryDate)
        file.parentFile?.mkdirs()
        file.writeText(markdown, Charsets.UTF_8)
    }

    fun readFrontMatter(entryDate: String): FrontMatter {
        val f = pathFor(entryDate)
        if (!f.isFile) return FrontMatter(date = entryDate)
        val (_, fm) = parse(f.readText(Charsets.UTF_8))
        return if (fm.date.isBlank()) fm.copy(date = entryDate) else fm
    }

    fun write(
        entryDate: String,
        body: String,
        title: String = entryDate,
        id: String = "",
        createdAt: String = "",
        updatedAt: String = "",
        location: String = "",
        weather: String = "",
        tempC: Double? = null,
        contextSource: String = "",
        contextUpdatedAt: String = "",
        writingDurationSec: Int = 0,
    ) {
        val file = pathFor(entryDate)
        file.parentFile?.mkdirs()
        val lines = mutableListOf(
            "---",
            "date: $entryDate",
            "title: ${yamlEscape(title.ifBlank { entryDate })}",
        )
        if (id.isNotBlank()) lines += "id: $id"
        if (createdAt.isNotBlank()) lines += "created_at: $createdAt"
        if (updatedAt.isNotBlank()) lines += "updated_at: $updatedAt"
        if (location.isNotBlank()) lines += "location: ${yamlEscape(location)}"
        if (weather.isNotBlank()) lines += "weather: ${yamlEscape(weather)}"
        if (tempC != null) lines += "temp_c: ${trimNum(tempC)}"
        if (contextSource.isNotBlank()) lines += "context_source: $contextSource"
        if (contextUpdatedAt.isNotBlank()) lines += "context_updated_at: $contextUpdatedAt"
        if (writingDurationSec > 0) lines += "writing_duration_sec: $writingDurationSec"
        lines += "---"
        val content = lines.joinToString("\n") + "\n\n" + body.trimEnd() + "\n"
        file.writeText(content, Charsets.UTF_8)
    }

    fun listDates(): List<String> {
        if (!diaryRoot.exists()) return emptyList()
        return diaryRoot.walkTopDown()
            .filter { it.isFile && it.extension == "md" }
            .map { it.nameWithoutExtension }
            .filter { it.matches(Regex("""\d{4}-\d{2}-\d{2}""")) }
            .sortedDescending()
            .toList()
    }

    fun parse(text: String): Pair<String, FrontMatter> {
        if (!text.startsWith("---")) return text to FrontMatter()
        val end = text.indexOf("\n---", 3)
        if (end < 0) return text to FrontMatter()
        val raw = text.substring(3, end).trim('\n')
        val body = text.substring(end + 4).trimStart('\n')
        val map = mutableMapOf<String, String>()
        raw.lineSequence().forEach { line ->
            val idx = line.indexOf(':')
            if (idx > 0) {
                map[line.substring(0, idx).trim()] = line.substring(idx + 1).trim().trim('"')
            }
        }
        val temp = map["temp_c"]?.toDoubleOrNull()
        val duration = map["writing_duration_sec"]?.toDoubleOrNull()?.toInt() ?: 0
        return body to FrontMatter(
            date = map["date"].orEmpty(),
            title = map["title"].orEmpty(),
            id = map["id"].orEmpty(),
            createdAt = map["created_at"].orEmpty(),
            updatedAt = map["updated_at"].orEmpty(),
            location = map["location"].orEmpty(),
            weather = map["weather"].orEmpty(),
            tempC = temp,
            contextSource = map["context_source"].orEmpty(),
            contextUpdatedAt = map["context_updated_at"].orEmpty(),
            writingDurationSec = duration,
        )
    }

    private fun yamlEscape(value: String): String {
        return if (value.any { it in ":#{}[],&*?|>!%@`'\"" || it == '\\' }) {
            "\"${value.replace("\"", "\\\"")}\""
        } else value
    }

    private fun trimNum(v: Double): String =
        if (v % 1.0 == 0.0) v.toInt().toString() else v.toString()
}
