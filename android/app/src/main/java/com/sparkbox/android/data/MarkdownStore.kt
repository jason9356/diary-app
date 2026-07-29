package com.sparkbox.android.data

import org.json.JSONObject
import java.io.File
import java.util.UUID
import java.util.concurrent.atomic.AtomicReference

/**
 * v2 layout: diary/YYYY/MM/<id>.md
 */
class MarkdownStore(
    private val diaryRoot: File,
    private val assetsRoot: File? = null,
) {
    data class FrontMatter(
        val date: String = "",
        val title: String = "",
        val id: String = "",
        val createdAt: String = "",
        val updatedAt: String = "",
        val writingDurationSec: Int = 0,
        val tags: List<String> = emptyList(),
        val pinned: Boolean = false,
        // Legacy v1 (migration only)
        val location: String = "",
        val weather: String = "",
        val tempC: Double? = null,
        val contextSource: String = "",
        val contextUpdatedAt: String = "",
    )

    private val indexFile = File(diaryRoot, ".id-index.json")
    private val idIndex = AtomicReference<MutableMap<String, String>?>(null)

    init {
        diaryRoot.mkdirs()
    }

    fun pathFor(entryId: String, entryDate: String): File {
        val parts = entryDate.split("-")
        require(parts.size == 3) { "bad date $entryDate" }
        val (y, m, _) = parts
        return File(diaryRoot, "$y/$m/$entryId.md")
    }

    fun exists(entryId: String, entryDate: String): Boolean =
        pathFor(entryId, entryDate).isFile

    /** O(1) id → date via sidecar index (rebuilt if missing). */
    fun dateForId(entryId: String): String? {
        val map = ensureIndex()
        val date = map[entryId] ?: return null
        if (exists(entryId, date)) return date
        // Stale index entry
        invalidateIndex()
        return ensureIndex()[entryId]?.takeIf { exists(entryId, it) }
    }

    fun invalidateIndex() {
        idIndex.set(null)
        if (indexFile.isFile) indexFile.delete()
    }

    private fun ensureIndex(): MutableMap<String, String> {
        idIndex.get()?.let { return it }
        synchronized(this) {
            idIndex.get()?.let { return it }
            val loaded = loadIndexFile() ?: rebuildIndex()
            idIndex.set(loaded)
            return loaded
        }
    }

    private fun loadIndexFile(): MutableMap<String, String>? {
        if (!indexFile.isFile) return null
        return try {
            val root = JSONObject(indexFile.readText(Charsets.UTF_8))
            val notes = root.optJSONObject("notes") ?: return null
            val out = mutableMapOf<String, String>()
            val keys = notes.keys()
            while (keys.hasNext()) {
                val id = keys.next()
                val date = notes.optString(id)
                if (id.isNotBlank() && date.isNotBlank()) out[id] = date
            }
            out.ifEmpty { null }
        } catch (_: Exception) {
            null
        }
    }

    private fun rebuildIndex(): MutableMap<String, String> {
        val out = mutableMapOf<String, String>()
        if (diaryRoot.exists()) {
            diaryRoot.walkTopDown()
                .filter { it.isFile && it.extension == "md" }
                .forEach { path ->
                    try {
                        if (DATE_FILE_RE.matches(path.name)) return@forEach
                        val stem = path.nameWithoutExtension
                        if (!UUID_RE.matches(stem)) return@forEach
                        val text = path.readText(Charsets.UTF_8)
                        val (_, fm) = parse(text)
                        val entryDate = fm.date
                        if (entryDate.isNotBlank()) out[stem] = entryDate
                    } catch (_: Exception) {
                        // Skip corrupt notes when rebuilding the id index.
                    }
                }
        }
        persistIndex(out)
        return out
    }

    private fun persistIndex(map: Map<String, String>) {
        try {
            diaryRoot.mkdirs()
            val notes = JSONObject()
            for ((id, date) in map) notes.put(id, date)
            indexFile.writeText(
                JSONObject().put("notes", notes).toString(),
                Charsets.UTF_8,
            )
        } catch (_: Exception) {
            // Index is a cache; ignore write failures.
        }
    }

    private fun indexPut(entryId: String, entryDate: String) {
        val map = ensureIndex()
        if (map[entryId] == entryDate) return
        map[entryId] = entryDate
        persistIndex(map)
    }

    private fun indexRemove(entryId: String) {
        val map = ensureIndex()
        if (map.remove(entryId) != null) persistIndex(map)
    }

    fun readBody(entryId: String, entryDate: String): String {
        val f = pathFor(entryId, entryDate)
        if (!f.isFile) return ""
        val (body, _) = parse(f.readText(Charsets.UTF_8))
        return body
    }

    fun readRaw(entryId: String, entryDate: String): String {
        val f = pathFor(entryId, entryDate)
        return if (f.isFile) f.readText(Charsets.UTF_8) else ""
    }

    fun writeRaw(entryId: String, entryDate: String, markdown: String) {
        val file = pathFor(entryId, entryDate)
        file.parentFile?.mkdirs()
        file.writeText(markdown, Charsets.UTF_8)
        indexPut(entryId, entryDate)
    }

    fun readFrontMatter(entryId: String, entryDate: String): FrontMatter {
        val f = pathFor(entryId, entryDate)
        if (!f.isFile) return FrontMatter(date = entryDate, id = entryId)
        val (_, fm) = parse(f.readText(Charsets.UTF_8))
        return fm.copy(
            date = fm.date.ifBlank { entryDate },
            id = fm.id.ifBlank { entryId },
        )
    }

    fun write(
        entryId: String,
        entryDate: String,
        body: String,
        title: String = entryDate,
        createdAt: String = "",
        updatedAt: String = "",
        writingDurationSec: Int = 0,
        tags: List<String> = emptyList(),
        pinned: Boolean = false,
    ) {
        val file = pathFor(entryId, entryDate)
        file.parentFile?.mkdirs()
        val lines = mutableListOf(
            "---",
            "date: $entryDate",
            "title: ${yamlEscape(title.ifBlank { entryDate })}",
            "id: $entryId",
        )
        if (createdAt.isNotBlank()) lines += "created_at: $createdAt"
        if (updatedAt.isNotBlank()) lines += "updated_at: $updatedAt"
        if (writingDurationSec > 0) lines += "writing_duration_sec: $writingDurationSec"
        if (tags.isNotEmpty()) lines += "tags: [${tags.joinToString(", ") { yamlEscape(it) }}]"
        if (pinned) lines += "pinned: true"
        lines += "---"
        val content = lines.joinToString("\n") + "\n\n" + body.trimEnd() + "\n"
        file.writeText(content, Charsets.UTF_8)
        indexPut(entryId, entryDate)
    }

    fun listNoteIds(): List<Pair<String, String>> =
        ensureIndex().entries.map { it.key to it.value }

    /** One filesystem pass: parse each note once (avoids double-read on home refresh). */
    fun listParsedNotes(): List<ParsedNote> {
        if (!diaryRoot.exists()) {
            invalidateIndex()
            return emptyList()
        }
        val found = mutableListOf<ParsedNote>()
        val rebuilt = mutableMapOf<String, String>()
        diaryRoot.walkTopDown()
            .filter { it.isFile && it.extension == "md" }
            .forEach { path ->
                try {
                    if (DATE_FILE_RE.matches(path.name)) return@forEach
                    val stem = path.nameWithoutExtension
                    if (!UUID_RE.matches(stem)) return@forEach
                    val text = path.readText(Charsets.UTF_8)
                    val (body, fm) = parse(text)
                    val entryDate = fm.date
                    if (entryDate.isBlank()) return@forEach
                    rebuilt[stem] = entryDate
                    found += ParsedNote(
                        id = stem,
                        date = entryDate,
                        body = body,
                        frontMatter = fm.copy(
                            date = entryDate,
                            id = fm.id.ifBlank { stem },
                        ),
                    )
                } catch (_: Exception) {
                    // Corrupt / unreadable markdown must not crash the home list.
                }
            }
        idIndex.set(rebuilt)
        persistIndex(rebuilt)
        return found.sortedByDescending { it.date }
    }

    data class ParsedNote(
        val id: String,
        val date: String,
        val body: String,
        val frontMatter: FrontMatter,
    )

    fun migrateV1Layout(dayStore: DayStore): Int {
        if (!diaryRoot.exists()) return 0
        var count = 0
        val legacyFiles = diaryRoot.walkTopDown()
            .filter { it.isFile && DATE_FILE_RE.matches(it.name) }
            .sortedBy { it.path }
            .toList()
        for (path in legacyFiles) {
            val entryDate = path.nameWithoutExtension
            val text = path.readText(Charsets.UTF_8)
            val (body, fm) = parse(text)
            val entryId = fm.id.ifBlank { UUID.randomUUID().toString() }
            val date = fm.date.ifBlank { entryDate }

            val dayCtx = extractLegacyContext(text, entryDate)
            if (dayCtx != null && dayCtx.hasContext) {
                dayStore.mergeWrite(dayCtx)
            }

            val newBody = body.replace("assets/$entryDate/", "assets/$entryId/")
            writeRaw(entryId, date, renderV2(newBody, fm.copy(id = entryId, date = date)))

            if (assetsRoot != null) {
                val oldAssets = File(assetsRoot, entryDate)
                if (oldAssets.isDirectory) {
                    val newAssets = File(assetsRoot, entryId).also { it.mkdirs() }
                    oldAssets.listFiles()?.filter { it.isFile }?.forEach { ap ->
                        val target = File(newAssets, ap.name)
                        if (!target.exists()) ap.renameTo(target)
                    }
                    oldAssets.listFiles()?.takeIf { it.isEmpty() }?.let { oldAssets.delete() }
                }
            }

            path.delete()
            count++
        }
        if (count > 0) invalidateIndex()
        return count
    }

    fun parse(text: String): Pair<String, FrontMatter> {
        if (!text.startsWith("---")) return text to FrontMatter()
        val end = text.indexOf("\n---", 3)
        if (end < 0) return text to FrontMatter()
        val raw = text.substring(3, end).trim('\n')
        val body = text.substring(end + 4).trimStart('\n')
        val map = parseYamlMap(raw)
        val temp = map["temp_c"]?.toDoubleOrNull()
        val duration = map["writing_duration_sec"]?.toDoubleOrNull()?.toInt() ?: 0
        return body to FrontMatter(
            date = map["date"].orEmpty(),
            title = map["title"].orEmpty(),
            id = map["id"].orEmpty(),
            createdAt = map["created_at"].orEmpty(),
            updatedAt = map["updated_at"].orEmpty(),
            writingDurationSec = duration,
            tags = parseTags(map["tags"].orEmpty()),
            pinned = map["pinned"].orEmpty().equals("true", ignoreCase = true),
            location = map["location"].orEmpty(),
            weather = map["weather"].orEmpty(),
            tempC = temp,
            contextSource = map["context_source"].orEmpty(),
            contextUpdatedAt = map["context_updated_at"].orEmpty(),
        )
    }

    private fun renderV2(body: String, fm: FrontMatter): String {
        val title = fm.title.ifBlank { fm.date.ifBlank { "untitled" } }
        val lines = mutableListOf(
            "---",
            "date: ${fm.date}",
            "title: ${yamlEscape(title)}",
            "id: ${fm.id}",
        )
        if (fm.createdAt.isNotBlank()) lines += "created_at: ${fm.createdAt}"
        if (fm.updatedAt.isNotBlank()) lines += "updated_at: ${fm.updatedAt}"
        if (fm.writingDurationSec > 0) lines += "writing_duration_sec: ${fm.writingDurationSec}"
        if (fm.tags.isNotEmpty()) {
            lines += "tags: [${fm.tags.joinToString(", ") { yamlEscape(it) }}]"
        }
        if (fm.pinned) lines += "pinned: true"
        lines += "---"
        return lines.joinToString("\n") + "\n\n" + body.trimEnd() + "\n"
    }

    private fun parseTags(raw: String): List<String> {
        val s = raw.trim()
        if (s.isEmpty()) return emptyList()
        val inner = when {
            s.startsWith("[") && s.endsWith("]") -> s.substring(1, s.length - 1)
            else -> s
        }
        return inner.split(',')
            .map { it.trim().trim('"').trim('\'') }
            .filter { it.isNotEmpty() }
            .distinct()
    }

    private fun extractLegacyContext(text: String, entryDate: String): DayContext? {
        if (!text.startsWith("---")) return null
        val end = text.indexOf("\n---", 3)
        if (end < 0) return null
        val raw = text.substring(3, end)
        val map = parseYamlMap(raw)
        if (!map.keys.any { it in setOf("location", "weather", "temp_c", "context_source") }) {
            return null
        }
        return DayContext(
            date = entryDate,
            location = map["location"].orEmpty(),
            weather = map["weather"].orEmpty(),
            tempC = map["temp_c"]?.toDoubleOrNull(),
            contextSource = map["context_source"].orEmpty().ifBlank { "desktop" },
            contextUpdatedAt = map["context_updated_at"].orEmpty().ifBlank {
                map["updated_at"].orEmpty()
            },
            updatedAt = map["context_updated_at"].orEmpty().ifBlank {
                map["updated_at"].orEmpty()
            },
        )
    }

    private fun parseYamlMap(raw: String): Map<String, String> {
        val map = mutableMapOf<String, String>()
        raw.lineSequence().forEach { line ->
            val trimmed = line.trim()
            if (trimmed.isEmpty() || trimmed.startsWith("#")) return@forEach
            val idx = trimmed.indexOf(':')
            if (idx <= 0) return@forEach
            val key = trimmed.substring(0, idx).trim()
            if (key.isEmpty()) return@forEach
            map[key] = unquoteYamlScalar(trimmed.substring(idx + 1).trim())
        }
        return map
    }

    private fun unquoteYamlScalar(raw: String): String {
        if (raw.length >= 2) {
            val q = raw.first()
            if ((q == '"' || q == '\'') && raw.last() == q) {
                val inner = raw.substring(1, raw.length - 1)
                return if (q == '"') {
                    inner.replace("\\\"", "\"").replace("\\\\", "\\")
                } else {
                    inner.replace("''", "'")
                }
            }
        }
        return raw
    }

    private fun yamlEscape(value: String): String {
        return if (value.isEmpty() || value.any { it in ":#{}[],&*?|>!%@`'\"" || it == '\\' || it.isWhitespace() }) {
            "\"${value.replace("\\", "\\\\").replace("\"", "\\\"")}\""
        } else value
    }

    companion object {
        private val DATE_FILE_RE = Regex("""^\d{4}-\d{2}-\d{2}\.md$""")
        private val UUID_RE = Regex(
            """^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$"""
        )
    }
}
