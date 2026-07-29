package com.sparkbox.android.data

import org.json.JSONObject
import java.io.File

private val CONTEXT_RANK = mapOf("phone" to 3, "desktop" to 2, "manual" to 1, "" to 0)

fun mergeDayContext(server: DayContext?, incoming: DayContext): DayContext {
    if (server == null) {
        val updated = incoming.updatedAt.ifBlank { incoming.contextUpdatedAt }
        return incoming.copy(updatedAt = updated)
    }
    val inRank = CONTEXT_RANK[incoming.contextSource] ?: 0
    val srvRank = CONTEXT_RANK[server.contextSource] ?: 0
    val winner = when {
        inRank > srvRank -> incoming
        inRank < srvRank -> server
        (incoming.contextUpdatedAt) > (server.contextUpdatedAt) -> incoming
        else -> server
    }
    val updated = winner.updatedAt.ifBlank { winner.contextUpdatedAt }
    return winner.copy(
        date = winner.date.ifBlank { incoming.date.ifBlank { server.date } },
        updatedAt = updated.ifBlank { incoming.updatedAt.ifBlank { server.updatedAt } },
    )
}

class DayStore(private val diaryRoot: File) {
    init {
        diaryRoot.mkdirs()
    }

    fun pathFor(entryDate: String): File {
        val parts = entryDate.split("-")
        require(parts.size == 3) { "bad date $entryDate" }
        val (y, m, _) = parts
        return File(diaryRoot, "$y/$m/$entryDate.day.json")
    }

    fun read(entryDate: String): DayContext? {
        val f = pathFor(entryDate)
        if (!f.isFile) return null
        return try {
            val json = JSONObject(f.readText(Charsets.UTF_8))
            val temp = if (json.isNull("temp_c")) null else json.optDouble("temp_c")
            DayContext(
                date = json.optString("date", entryDate),
                location = json.optString("location", ""),
                weather = json.optString("weather", ""),
                tempC = temp,
                device = json.optString("device", ""),
                contextSource = json.optString("context_source", ""),
                contextUpdatedAt = json.optString("context_updated_at", ""),
                updatedAt = json.optString("updated_at", ""),
            )
        } catch (_: Exception) {
            null
        }
    }

    fun getOrEmpty(entryDate: String): DayContext =
        read(entryDate) ?: DayContext(date = entryDate)

    fun write(ctx: DayContext) {
        val file = pathFor(ctx.date)
        file.parentFile?.mkdirs()
        val payload = JSONObject()
            .put("date", ctx.date)
            .put("location", ctx.location)
            .put("weather", ctx.weather)
            .put("device", ctx.device)
            .put("context_source", ctx.contextSource)
            .put("context_updated_at", ctx.contextUpdatedAt)
            .put("updated_at", ctx.updatedAt.ifBlank { ctx.contextUpdatedAt })
        if (ctx.tempC != null) payload.put("temp_c", ctx.tempC)
        file.writeText(payload.toString(2), Charsets.UTF_8)
    }

    fun mergeWrite(incoming: DayContext): DayContext {
        val merged = mergeDayContext(read(incoming.date), incoming)
        write(merged)
        return merged
    }

    fun exists(entryDate: String): Boolean = pathFor(entryDate).isFile

    fun listDates(): List<String> {
        if (!diaryRoot.exists()) return emptyList()
        return diaryRoot.walkTopDown()
            .filter { it.isFile && it.name.endsWith(".day.json") }
            .map { it.name.removeSuffix(".day.json") }
            .filter { it.matches(Regex("""\d{4}-\d{2}-\d{2}""")) }
            .toList()
    }
}
