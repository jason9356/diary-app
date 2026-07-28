package com.personaldiary.android.sync

import android.content.Context
import com.personaldiary.android.data.DayContext
import com.personaldiary.android.data.DiaryEntry
import com.personaldiary.android.data.DiaryRepository
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

class SyncPrefs(context: Context) {
    private val sp = context.getSharedPreferences("diary_sync", Context.MODE_PRIVATE)

    var endpoint: String
        get() = sp.getString("endpoint", "").orEmpty()
        set(v) = sp.edit().putString("endpoint", v.trim()).apply()

    var token: String
        get() = sp.getString("token", "").orEmpty()
        set(v) = sp.edit().putString("token", v.trim()).apply()

    var cursor: Int
        get() = sp.getInt("cursor", 0)
        set(v) = sp.edit().putInt("cursor", v).apply()

    val enabled: Boolean get() = endpoint.isNotBlank() && token.isNotBlank()
}

data class SyncResult(
    val pushed: Int = 0,
    val pulled: Int = 0,
    val assetsUp: Int = 0,
    val assetsDown: Int = 0,
    val message: String = "",
)

class SyncClient(
    private val repo: DiaryRepository,
    private val prefs: SyncPrefs,
) {
    private val http = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    private fun base(): String {
        var b = prefs.endpoint.trim().trimEnd('/')
        if (!b.endsWith("/v1")) b = "$b/v1"
        return b
    }

    fun sync(entryDate: String): SyncResult {
        if (!prefs.enabled) return SyncResult(message = "未配置同步地址 / Token")
        var pushed = 0
        var pulled = 0
        var up = 0
        var down = 0
        return try {
            checkProtocol()?.let { return SyncResult(message = it) }

            val since = prefs.cursor
            val changes = getJson("/changes?since=$since")
            var rev = changes.optInt("revision", since)
            val todayEntryIds = repo.listForDate(entryDate).map { it.id }.toSet()
            val arr = changes.optJSONArray("changes") ?: JSONArray()

            for (i in 0 until arr.length()) {
                val ch = arr.getJSONObject(i)
                val kind = ch.optString("kind", "entry")
                if (kind == "day") {
                    val d = ch.optString("date")
                    if (d.isBlank() || d == entryDate) continue
                    if (pullDay(d)) pulled++
                    continue
                }
                if (ch.optBoolean("deleted")) continue
                val entryId = ch.optString("id")
                val chDate = ch.optString("date", "")
                if (entryId.isBlank()) continue
                if (chDate == entryDate || entryId in todayEntryIds) continue
                if (pullEntry(entryId)) {
                    pulled++
                    down += downloadAssets(entryId)
                }
            }

            syncDay(entryDate) { p, pl, u, d ->
                pushed += p
                pulled += pl
                up += u
                down += d
            }

            prefs.cursor = rev
            val after = getJson("/changes?since=0")
            rev = after.optInt("revision", rev)
            prefs.cursor = rev

            SyncResult(
                pushed = pushed,
                pulled = pulled,
                assetsUp = up,
                assetsDown = down,
                message = "同步完成：推送 $pushed，拉取 $pulled，上传图 $up，下载图 $down",
            )
        } catch (e: Exception) {
            SyncResult(message = "同步失败：${e.message}")
        }
    }

    private fun checkProtocol(): String? {
        val req = Request.Builder().url(base() + "/health").get().build()
        http.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) return "health 失败 HTTP ${resp.code}"
            val payload = JSONObject(resp.body?.string().orEmpty())
            if (payload.optInt("protocol", 0) != 2) {
                return "同步服务不是 protocol v2，请升级服务端"
            }
        }
        return null
    }

    private fun syncDay(day: String, tally: (Int, Int, Int, Int) -> Unit) {
        var pushed = 0
        var pulled = 0
        var up = 0
        var down = 0
        val localNotes = repo.listForDate(day)
        val remoteIds = allEntryChangesForDay(day).mapNotNull { it.optString("id").takeIf(String::isNotBlank) }.toSet()

        for (entryId in remoteIds) {
            val remote = fetchEntry(entryId) ?: continue
            if (remote.optBoolean("deleted")) continue
            val local = repo.getById(entryId)
            val remoteUpdated = remote.optString("updated_at")
            val localUpdated = local?.updatedAt.orEmpty()
            when {
                local == null || remoteUpdated > localUpdated -> {
                    applyServerEntry(remote)
                    pulled++
                    down += downloadAssets(entryId)
                }
                remoteUpdated < localUpdated -> {
                    up += uploadAssets(entryId)
                    if (pushEntry(local)) pushed++
                    down += downloadAssets(entryId)
                }
                else -> {
                    down += downloadAssets(entryId)
                    up += uploadAssets(entryId)
                }
            }
        }

        for (local in localNotes) {
            if (local.id !in remoteIds && local.body.trim().isNotEmpty()) {
                up += uploadAssets(local.id)
                if (pushEntry(local)) pushed++
            }
        }

        syncDayContext(day) { p, pl ->
            pushed += p
            pulled += pl
        }
        tally(pushed, pulled, up, down)
    }

    private fun syncDayContext(day: String, tally: (Int, Int) -> Unit) {
        var pushed = 0
        var pulled = 0
        val remote = fetchDay(day)
        val local = repo.getDayContext(day)
        if (remote != null) {
            val remoteUpdated = remote.optString("updated_at")
            val localUpdated = local.updatedAt
            when {
                remoteUpdated > localUpdated -> {
                    repo.applyRemoteDay(day, remote)
                    pulled++
                }
                remoteUpdated < localUpdated && local.hasContext -> {
                    if (pushDay(local)) pushed++
                }
            }
        } else if (local.hasContext) {
            if (pushDay(local)) pushed++
        }
        tally(pushed, pulled)
    }

    private fun allEntryChangesForDay(day: String): List<JSONObject> {
        val payload = getJson("/changes?since=0")
        val arr = payload.optJSONArray("changes") ?: return emptyList()
        return buildList {
            for (i in 0 until arr.length()) {
                val ch = arr.getJSONObject(i)
                if (ch.optString("kind", "entry") != "entry") continue
                if (ch.optString("date") != day) continue
                if (ch.optBoolean("deleted")) continue
                add(ch)
            }
        }
    }

    private fun fetchEntry(entryId: String): JSONObject? {
        val req = authRequest("/entries/$entryId").get().build()
        http.newCall(req).execute().use { resp ->
            if (resp.code == 404) return null
            val text = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) error("fetch entry HTTP ${resp.code}: $text")
            return JSONObject(text)
        }
    }

    private fun fetchDay(entryDate: String): JSONObject? {
        val req = authRequest("/days/$entryDate").get().build()
        http.newCall(req).execute().use { resp ->
            if (resp.code == 404) return null
            val text = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) error("fetch day HTTP ${resp.code}: $text")
            return JSONObject(text)
        }
    }

    private fun authRequest(path: String): Request.Builder =
        Request.Builder()
            .url(base() + path)
            .header("Authorization", "Bearer ${prefs.token}")
            .header("User-Agent", "diary-app-android/0.4")

    private fun getJson(path: String): JSONObject {
        val req = authRequest(path).get().build()
        http.newCall(req).execute().use { resp ->
            val body = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) error("HTTP ${resp.code}: $body")
            return JSONObject(body)
        }
    }

    private fun pushEntry(entry: DiaryEntry): Boolean {
        var markdown = repo.readRaw(entry.id, entry.entryDate)
        if (markdown.isBlank()) markdown = buildMd(entry)
        if (markdown.isBlank()) return false
        val assets = JSONArray()
        repo.listAssetFiles(entry.id).forEach { f ->
            assets.put(
                JSONObject()
                    .put("name", f.name)
                    .put("sha256", sha256(f.readBytes())),
            )
        }
        val payload = JSONObject()
            .put("date", entry.entryDate)
            .put("updated_at", entry.updatedAt)
            .put("created_at", entry.createdAt)
            .put("writing_duration_sec", entry.writingDurationSec)
            .put("deleted", false)
            .put("deleted_at", JSONObject.NULL)
            .put("markdown", markdown)
            .put("assets", assets)
        val body = payload.toString().toRequestBody("application/json".toMediaType())
        val req = authRequest("/entries/${entry.id}").put(body).build()
        http.newCall(req).execute().use { resp ->
            val text = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) error("push HTTP ${resp.code}: $text")
            applyServerEntry(JSONObject(text))
            return true
        }
    }

    private fun pushDay(ctx: DayContext): Boolean {
        val payload = JSONObject()
            .put("date", ctx.date)
            .put("location", ctx.location)
            .put("weather", ctx.weather)
            .put("context_source", ctx.contextSource)
            .put("context_updated_at", ctx.contextUpdatedAt)
            .put("updated_at", ctx.updatedAt.ifBlank { ctx.contextUpdatedAt })
        if (ctx.tempC != null) payload.put("temp_c", ctx.tempC)
        val body = payload.toString().toRequestBody("application/json".toMediaType())
        val req = authRequest("/days/${ctx.date}").put(body).build()
        http.newCall(req).execute().use { resp ->
            val text = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) error("push day HTTP ${resp.code}: $text")
            repo.applyRemoteDay(ctx.date, JSONObject(text))
            return true
        }
    }

    private fun pullEntry(entryId: String): Boolean {
        val req = authRequest("/entries/$entryId").get().build()
        http.newCall(req).execute().use { resp ->
            if (resp.code == 404) return false
            val text = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) error("pull HTTP ${resp.code}: $text")
            val json = JSONObject(text)
            if (json.optBoolean("deleted")) return false
            applyServerEntry(json)
            return true
        }
    }

    private fun pullDay(entryDate: String): Boolean {
        val json = fetchDay(entryDate) ?: return false
        repo.applyRemoteDay(entryDate, json)
        return true
    }

    private fun applyServerEntry(payload: JSONObject) {
        val markdown = payload.optString("markdown")
        if (markdown.isBlank()) return
        var entryId = payload.optString("id")
        var entryDate = payload.optString("date")
        val (_, fm) = MarkdownStoreParse.parse(markdown)
        if (entryId.isBlank()) entryId = fm.id
        if (entryDate.isBlank()) entryDate = fm.date
        if (entryId.isBlank() || entryDate.isBlank()) return
        repo.applyRemoteMarkdown(entryId, entryDate, markdown)
    }

    private fun uploadAssets(entryId: String): Int {
        var n = 0
        for (f in repo.listAssetFiles(entryId)) {
            val bytes = f.readBytes()
            val digest = sha256(bytes)
            val body = bytes.toRequestBody("application/octet-stream".toMediaType())
            val encoded = URLEncoder.encode(f.name, Charsets.UTF_8.name()).replace("+", "%20")
            val req = authRequest("/assets/$entryId/$encoded")
                .put(body)
                .header("X-Content-SHA256", digest)
                .build()
            http.newCall(req).execute().use { resp ->
                if (resp.isSuccessful) n++
            }
        }
        return n
    }

    private fun downloadAssets(entryId: String): Int {
        val json = fetchEntry(entryId) ?: return 0
        val arr = json.optJSONArray("assets") ?: return 0
        var n = 0
        for (i in 0 until arr.length()) {
            val name = arr.getJSONObject(i).optString("name")
            if (name.isBlank()) continue
            val dest = repo.assetFile(entryId, name)
            if (dest.exists()) continue
            val encoded = URLEncoder.encode(name, Charsets.UTF_8.name()).replace("+", "%20")
            val req = authRequest("/assets/$entryId/$encoded").get().build()
            http.newCall(req).execute().use { resp ->
                val bytes = resp.body?.bytes()
                if (resp.isSuccessful && bytes != null) {
                    dest.parentFile?.mkdirs()
                    dest.writeBytes(bytes)
                    n++
                }
            }
        }
        return n
    }

    private fun buildMd(entry: DiaryEntry): String {
        val lines = mutableListOf(
            "---",
            "date: ${entry.entryDate}",
            "title: ${entry.title}",
            "id: ${entry.id}",
            "created_at: ${entry.createdAt}",
            "updated_at: ${entry.updatedAt}",
        )
        if (entry.writingDurationSec > 0) {
            lines += "writing_duration_sec: ${entry.writingDurationSec}"
        }
        lines += "---"
        return lines.joinToString("\n") + "\n\n" + entry.body.trimEnd() + "\n"
    }

    private fun sha256(data: ByteArray): String {
        val dig = MessageDigest.getInstance("SHA-256").digest(data)
        return dig.joinToString("") { "%02x".format(it) }
    }
}

private object MarkdownStoreParse {
    fun parse(text: String): Pair<String, ParsedFm> {
        if (!text.startsWith("---")) return text to ParsedFm()
        val end = text.indexOf("\n---", 3)
        if (end < 0) return text to ParsedFm()
        val raw = text.substring(3, end).trim('\n')
        val body = text.substring(end + 4).trimStart('\n')
        val map = mutableMapOf<String, String>()
        raw.lineSequence().forEach { line ->
            val idx = line.indexOf(':')
            if (idx > 0) map[line.substring(0, idx).trim()] = line.substring(idx + 1).trim().trim('"')
        }
        return body to ParsedFm(
            date = map["date"].orEmpty(),
            id = map["id"].orEmpty(),
        )
    }

    data class ParsedFm(val date: String = "", val id: String = "")
}
