package com.personaldiary.android.data

import android.content.Context
import android.net.Uri
import java.io.File
import java.util.UUID

class DiaryRepository(dataRoot: File) {
    private val md = MarkdownStore(File(dataRoot, "diary"), File(dataRoot, "assets"))
    private val days = DayStore(File(dataRoot, "diary"))
    private val assets = AssetStore(File(dataRoot, "assets"))

    val dataRoot: File = dataRoot.also { it.mkdirs() }

    init {
        md.migrateV1Layout(days)
    }

    fun getById(entryId: String): DiaryEntry? {
        for ((id, date) in md.listNoteIds()) {
            if (id == entryId) return loadEntry(id, date)
        }
        return null
    }

    fun getOrCreate(entryId: String, entryDate: String): DiaryEntry {
        if (md.exists(entryId, entryDate)) return loadEntry(entryId, entryDate)
        val now = DiaryDates.nowIso()
        return DiaryEntry(
            id = entryId,
            entryDate = entryDate,
            title = entryDate,
            createdAt = now,
            updatedAt = now,
        )
    }

    fun createNote(entryDate: String): DiaryEntry {
        val id = UUID.randomUUID().toString()
        val now = DiaryDates.nowIso()
        val entry = DiaryEntry(
            id = id,
            entryDate = entryDate,
            title = entryDate,
            createdAt = now,
            updatedAt = now,
        )
        md.write(
            entryId = id,
            entryDate = entryDate,
            body = "",
            title = entryDate,
            createdAt = now,
            updatedAt = now,
            tags = emptyList(),
        )
        return entry
    }

    fun listForDate(entryDate: String): List<DiaryEntry> =
        md.listNoteIds()
            .filter { (_, date) -> date == entryDate }
            .map { (id, date) -> loadEntry(id, date) }
            .sortedByDescending { it.updatedAt }

    /** Flat card list for the home screen (pinned first, then newest). */
    fun listAllNotes(): List<DiaryEntry> =
        md.listParsedNotes()
            .map { parsed ->
                val fm = parsed.frontMatter
                val title = fm.title.ifBlank { extractTitle(parsed.body, parsed.date) }
                val now = DiaryDates.nowIso()
                DiaryEntry(
                    id = fm.id.ifBlank { parsed.id },
                    entryDate = fm.date.ifBlank { parsed.date },
                    title = title,
                    body = parsed.body,
                    createdAt = fm.createdAt.ifBlank { now },
                    updatedAt = fm.updatedAt.ifBlank { now },
                    writingDurationSec = fm.writingDurationSec,
                    imageRels = assets.listRels(parsed.id),
                    tags = mergeTags(fm.tags, MarkdownImages.extractHashTags(parsed.body)),
                    pinned = fm.pinned,
                )
            }
            .sortedWith(
                compareByDescending<DiaryEntry> { it.pinned }
                    .thenByDescending { it.entryDate }
                    .thenByDescending { it.updatedAt },
            )

    fun allTags(): List<String> =
        listAllNotes().flatMap { it.tags }.distinct().sorted()

    fun listTimeline(): List<TimelineDay> {
        val noteDates = md.listNoteIds().map { it.second }.toSet()
        val dayDates = days.listDates().toSet()
        val allDates = (noteDates + dayDates).sortedDescending()
        return allDates.map { date ->
            TimelineDay(
                date = date,
                context = days.getOrEmpty(date),
                notes = listForDate(date).filter { it.body.isNotBlank() || it.title != date },
            )
        }.filter { it.notes.isNotEmpty() || it.context.hasContext }
    }

    fun getDayContext(entryDate: String): DayContext = days.getOrEmpty(entryDate)

    fun save(entry: DiaryEntry): DiaryEntry {
        val title = extractTitle(entry.body, entry.entryDate)
        val now = DiaryDates.nowIso()
        val id = entry.id.ifBlank { UUID.randomUUID().toString() }
        val created = entry.createdAt.ifBlank { now }
        val previous = if (md.exists(id, entry.entryDate)) loadEntry(id, entry.entryDate) else null
        val tags = mergeTags(entry.tags, MarkdownImages.extractHashTags(entry.body))
        val updated = if (
            previous != null &&
            previous.body == entry.body &&
            previous.tags == tags &&
            previous.pinned == entry.pinned &&
            previous.updatedAt.isNotBlank()
        ) {
            previous.updatedAt
        } else {
            now
        }
        md.write(
            entryId = id,
            entryDate = entry.entryDate,
            body = entry.body,
            title = title,
            createdAt = created,
            updatedAt = updated,
            writingDurationSec = entry.writingDurationSec,
            tags = tags,
            pinned = entry.pinned,
        )
        return entry.copy(
            id = id,
            title = title,
            createdAt = created,
            updatedAt = updated,
            imageRels = assets.listRels(id),
            tags = tags,
            pinned = entry.pinned,
        )
    }

    fun applyRemoteMarkdown(entryId: String, entryDate: String, markdown: String): DiaryEntry {
        md.writeRaw(entryId, entryDate, markdown)
        return loadEntry(entryId, entryDate)
    }

    fun readRaw(entryId: String, entryDate: String): String = md.readRaw(entryId, entryDate)

    fun saveContext(entryDate: String, snap: WeatherSnapshot, force: Boolean = false): DayContext {
        val current = days.getOrEmpty(entryDate)
        val rank = mapOf("phone" to 3, "desktop" to 2, "manual" to 1, "" to 0)
        val old = rank[current.contextSource] ?: 0
        val neu = rank["phone"] ?: 3
        if (!force && neu < old) return current
        val now = DiaryDates.nowIso()
        return days.mergeWrite(
            DayContext(
                date = entryDate,
                location = snap.location,
                weather = snap.weather,
                tempC = snap.tempC,
                device = DeviceLabels.currentPhone(),
                contextSource = "phone",
                contextUpdatedAt = now,
                updatedAt = now,
            )
        )
    }

    fun applyRemoteDay(entryDate: String, payload: org.json.JSONObject): DayContext {
        val temp = if (payload.isNull("temp_c")) null else payload.optDouble("temp_c")
        val incoming = DayContext(
            date = entryDate,
            location = payload.optString("location", ""),
            weather = payload.optString("weather", ""),
            tempC = temp,
            device = payload.optString("device", ""),
            contextSource = payload.optString("context_source", ""),
            contextUpdatedAt = payload.optString("context_updated_at", ""),
            updatedAt = payload.optString("updated_at", ""),
        )
        return days.mergeWrite(incoming)
    }

    fun writeDayContext(ctx: DayContext): DayContext = days.mergeWrite(ctx)

    fun saveImage(context: Context, entryId: String, entryDate: String, uri: Uri): String {
        save(getOrCreate(entryId, entryDate))
        return assets.saveFromUri(context, entryId, uri)
    }

    fun absoluteAsset(rel: String): File = assets.absolute(rel)

    fun assetFile(entryId: String, name: String): File = assets.assetFile(entryId, name)

    fun listAssetFiles(entryId: String): List<File> = assets.listFiles(entryId)

    private fun loadEntry(entryId: String, entryDate: String): DiaryEntry {
        val fm = md.readFrontMatter(entryId, entryDate)
        val body = md.readBody(entryId, entryDate)
        val title = fm.title.ifBlank { extractTitle(body, entryDate) }
        val now = DiaryDates.nowIso()
        return DiaryEntry(
            id = fm.id.ifBlank { entryId },
            entryDate = fm.date.ifBlank { entryDate },
            title = title,
            body = body,
            createdAt = fm.createdAt.ifBlank { now },
            updatedAt = fm.updatedAt.ifBlank { now },
            writingDurationSec = fm.writingDurationSec,
            imageRels = assets.listRels(entryId),
            tags = mergeTags(fm.tags, MarkdownImages.extractHashTags(body)),
            pinned = fm.pinned,
        )
    }

    private fun mergeTags(a: List<String>, b: List<String>): List<String> =
        (a + b).map { it.trim() }.filter { it.isNotEmpty() }.distinct()

    private fun extractTitle(body: String, fallback: String): String {
        for (line in body.lineSequence()) {
            val s = line.trim()
            if (s.isEmpty()) continue
            return if (s.startsWith("#")) s.trimStart('#').trim().ifBlank { fallback } else s.take(80)
        }
        return fallback
    }
}
