package com.personaldiary.android.data

import android.content.Context
import android.net.Uri
import java.io.File

class DiaryRepository(dataRoot: File) {
    private val md = MarkdownStore(File(dataRoot, "diary"))
    private val assets = AssetStore(File(dataRoot, "assets"))

    val dataRoot: File = dataRoot.also { it.mkdirs() }

    fun getOrCreate(entryDate: String): DiaryEntry {
        val fm = md.readFrontMatter(entryDate)
        val body = md.readBody(entryDate)
        val title = fm.title.ifBlank {
            body.lineSequence().map { it.trim() }.firstOrNull { it.isNotEmpty() }
                ?.removePrefix("#")?.trim()
                ?.take(80)
                ?: entryDate
        }
        return DiaryEntry(
            entryDate = entryDate,
            title = title,
            body = body,
            location = fm.location,
            weather = fm.weather,
            tempC = fm.tempC,
            contextSource = fm.contextSource,
            contextUpdatedAt = fm.contextUpdatedAt,
            imageRels = assets.listRels(entryDate),
        )
    }

    fun save(entry: DiaryEntry): DiaryEntry {
        val title = extractTitle(entry.body, entry.entryDate)
        md.write(
            entryDate = entry.entryDate,
            body = entry.body,
            title = title,
            location = entry.location,
            weather = entry.weather,
            tempC = entry.tempC,
            contextSource = entry.contextSource,
            contextUpdatedAt = entry.contextUpdatedAt,
        )
        return entry.copy(title = title, imageRels = assets.listRels(entry.entryDate))
    }

    fun saveContext(entryDate: String, snap: WeatherSnapshot, force: Boolean = false): DiaryEntry {
        val current = getOrCreate(entryDate)
        val rank = mapOf("phone" to 3, "desktop" to 2, "manual" to 1, "" to 0)
        val old = rank[current.contextSource] ?: 0
        val neu = rank["phone"] ?: 3
        if (!force && neu < old) return current
        return save(
            current.copy(
                location = snap.location,
                weather = snap.weather,
                tempC = snap.tempC,
                contextSource = "phone",
                contextUpdatedAt = DiaryEntry.nowIso(),
            )
        )
    }

    fun listEntries(): List<DiaryEntry> =
        md.listDates().map { getOrCreate(it) }.filter { it.body.isNotBlank() || it.hasContext }

    fun saveImage(context: Context, entryDate: String, uri: Uri): Pair<DiaryEntry, String> {
        val rel = assets.saveFromUri(context, entryDate, uri)
        val entry = getOrCreate(entryDate)
        val marker = "\n![$rel]($rel)\n"
        val body = if (entry.body.contains("]($rel)")) entry.body else entry.body.trimEnd() + marker
        val saved = save(entry.copy(body = body))
        return saved to rel
    }

    fun absoluteAsset(rel: String): File = assets.absolute(rel)

    private fun extractTitle(body: String, fallback: String): String {
        for (line in body.lineSequence()) {
            val s = line.trim()
            if (s.isEmpty()) continue
            return if (s.startsWith("#")) s.trimStart('#').trim().ifBlank { fallback } else s.take(80)
        }
        return fallback
    }
}
