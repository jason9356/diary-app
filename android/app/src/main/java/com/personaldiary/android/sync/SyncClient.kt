package com.personaldiary.android.sync

import android.content.Context
import com.personaldiary.android.data.DiaryEntry
import com.personaldiary.android.data.DiaryRepository
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
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

    var deviceId: String
        get() {
            val existing = sp.getString("device_id", "").orEmpty()
            if (existing.isNotBlank()) return existing
            val neu = java.util.UUID.randomUUID().toString()
            sp.edit().putString("device_id", neu).apply()
            return neu
        }
        set(v) = sp.edit().putString("device_id", v).apply()

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
            // 1) Pull other changed days first.
            val since = prefs.cursor
            val changes = getJson("/changes?since=$since")
            var rev = changes.optInt("revision", since)
            val arr = changes.optJSONArray("changes") ?: JSONArray()
            for (i in 0 until arr.length()) {
                val ch = arr.getJSONObject(i)
                if (ch.optBoolean("deleted")) continue
                val d = ch.optString("entry_date")
                if (d.isBlank() || d == entryDate) continue
                if (pullEntry(d)) {
                    pulled++
                    down += downloadAssets(d)
                }
            }

            // 2) Reconcile today: do NOT save/bump updated_at before compare.
            val local = repo.getOrCreate(entryDate)
            val remote = fetchEntry(entryDate)
            if (remote != null && !remote.optBoolean("deleted")) {
                val remoteUpdated = remote.optString("updated_at")
                val localUpdated = local.updatedAt
                when {
                    remoteUpdated > localUpdated || local.body.isBlank() -> {
                        val md = remote.optString("markdown")
                        if (md.isNotBlank()) {
                            repo.applyRemoteMarkdown(entryDate, md)
                            pulled++
                            down += downloadAssets(entryDate)
                        }
                    }
                    remoteUpdated < localUpdated -> {
                        up += uploadAssets(entryDate)
                        if (pushEntry(repo.getOrCreate(entryDate))) pushed++
                        down += downloadAssets(entryDate)
                    }
                    else -> {
                        down += downloadAssets(entryDate)
                        up += uploadAssets(entryDate)
                    }
                }
            } else if (local.body.isNotBlank() || local.hasContext) {
                up += uploadAssets(entryDate)
                if (pushEntry(local)) pushed++
            }

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

    private fun fetchEntry(entryDate: String): JSONObject? {
        val req = authRequest("/entries/$entryDate").get().build()
        http.newCall(req).execute().use { resp ->
            if (resp.code == 404) return null
            val text = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) error("fetch HTTP ${resp.code}: $text")
            return JSONObject(text)
        }
    }

    private fun authRequest(path: String): Request.Builder =
        Request.Builder()
            .url(base() + path)
            .header("Authorization", "Bearer ${prefs.token}")
            .header("User-Agent", "diary-app-android/0.3")

    private fun getJson(path: String): JSONObject {
        val req = authRequest(path).get().build()
        http.newCall(req).execute().use { resp ->
            val body = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) error("HTTP ${resp.code}: $body")
            return JSONObject(body)
        }
    }

    private fun pushEntry(entry: DiaryEntry): Boolean {
        val markdown = repo.readRaw(entry.entryDate).ifBlank {
            // should exist after save
            ""
        }
        if (markdown.isBlank()) return false
        val assets = JSONArray()
        repo.listAssetFiles(entry.entryDate).forEach { f ->
            assets.put(
                JSONObject()
                    .put("name", f.name)
                    .put("sha256", sha256(f.readBytes())),
            )
        }
        val payload = JSONObject()
            .put("id", entry.id)
            .put("updated_at", entry.updatedAt)
            .put("created_at", entry.createdAt)
            .put("writing_duration_sec", entry.writingDurationSec)
            .put("deleted", false)
            .put("markdown", markdown)
            .put("assets", assets)
        val body = payload.toString().toRequestBody("application/json".toMediaType())
        val req = authRequest("/entries/${entry.entryDate}").put(body).build()
        http.newCall(req).execute().use { resp ->
            val text = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) error("push HTTP ${resp.code}: $text")
            val json = JSONObject(text)
            val md = json.optString("markdown")
            if (md.isNotBlank()) repo.applyRemoteMarkdown(entry.entryDate, md)
            return true
        }
    }

    private fun pullEntry(entryDate: String): Boolean {
        val req = authRequest("/entries/$entryDate").get().build()
        http.newCall(req).execute().use { resp ->
            if (resp.code == 404) return false
            val text = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) error("pull HTTP ${resp.code}: $text")
            val json = JSONObject(text)
            if (json.optBoolean("deleted")) return false
            val md = json.optString("markdown")
            if (md.isBlank()) return false
            repo.applyRemoteMarkdown(entryDate, md)
            return true
        }
    }

    private fun uploadAssets(entryDate: String): Int {
        var n = 0
        for (f in repo.listAssetFiles(entryDate)) {
            val bytes = f.readBytes()
            val digest = sha256(bytes)
            val body = bytes.toRequestBody("application/octet-stream".toMediaType())
            val req = authRequest("/assets/$entryDate/${f.name}")
                .put(body)
                .header("X-Content-SHA256", digest)
                .build()
            http.newCall(req).execute().use { resp ->
                if (resp.isSuccessful) n++
            }
        }
        return n
    }

    private fun downloadAssets(entryDate: String): Int {
        val json = getJson("/entries/$entryDate")
        val arr = json.optJSONArray("assets") ?: return 0
        var n = 0
        for (i in 0 until arr.length()) {
            val name = arr.getJSONObject(i).optString("name")
            if (name.isBlank()) continue
            val dest = repo.assetFile(entryDate, name)
            if (dest.exists()) continue
            val req = authRequest("/assets/$entryDate/$name").get().build()
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

    private fun sha256(data: ByteArray): String {
        val dig = MessageDigest.getInstance("SHA-256").digest(data)
        return dig.joinToString("") { "%02x".format(it) }
    }
}
