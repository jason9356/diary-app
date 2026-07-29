package com.sparkbox.android.sync

import okhttp3.Credentials
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.TimeUnit

data class WebDavConfig(
    val baseUrl: String,
    val username: String,
    val password: String,
    val rootPath: String = "/sparkbox",
) {
    val enabled: Boolean
        get() = baseUrl.isNotBlank() && username.isNotBlank()
}

data class DavResource(
    val path: String,
    val lastModifiedMs: Long = 0L,
    val etag: String = "",
    val isCollection: Boolean = false,
)

/**
 * Minimal WebDAV client for Vault mirror (PUT/GET/MKCOL/PROPFIND).
 */
class WebDavClient(private val config: WebDavConfig) {
    private val http = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(120, TimeUnit.SECONDS)
        .build()

    private val auth: String =
        Credentials.basic(config.username, config.password)

    private fun rootUrl(): String {
        val base = config.baseUrl.trim().trimEnd('/')
        val root = "/" + config.rootPath.trim().trim('/')
        return base + root
    }

    private fun urlFor(relPath: String): String {
        val rel = relPath.trim().trimStart('/')
        return rootUrl().trimEnd('/') + "/" + rel
    }

    fun ensureRoot() {
        mkcolRecursive(config.rootPath.trim().trim('/'))
    }

    fun putFile(relPath: String, bytes: ByteArray, contentType: String = "application/octet-stream") {
        ensureParentDirs(relPath)
        val body = bytes.toRequestBody(contentType.toMediaType())
        val req = Request.Builder()
            .url(urlFor(relPath))
            .header("Authorization", auth)
            .put(body)
            .build()
        http.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful && resp.code != 201 && resp.code != 204) {
                error("WebDAV PUT ${resp.code}: ${resp.body?.string().orEmpty()}")
            }
        }
    }

    fun deleteFile(relPath: String) {
        val req = Request.Builder()
            .url(urlFor(relPath))
            .header("Authorization", auth)
            .delete()
            .build()
        http.newCall(req).execute().use { resp ->
            if (resp.isSuccessful || resp.code == 404) return
            error("WebDAV DELETE ${resp.code}")
        }
    }

    fun getFile(relPath: String): ByteArray? {
        val req = Request.Builder()
            .url(urlFor(relPath))
            .header("Authorization", auth)
            .get()
            .build()
        http.newCall(req).execute().use { resp ->
            if (resp.code == 404) return null
            if (!resp.isSuccessful) error("WebDAV GET ${resp.code}")
            return resp.body?.bytes()
        }
    }

    fun listResources(prefix: String = ""): List<DavResource> {
        val href = urlFor(prefix) + if (prefix.isNotEmpty() && !prefix.endsWith("/")) "/" else ""
        val propfind = """
            <?xml version="1.0" encoding="utf-8" ?>
            <d:propfind xmlns:d="DAV:">
              <d:prop>
                <d:displayname/>
                <d:getlastmodified/>
                <d:getetag/>
                <d:resourcetype/>
              </d:prop>
            </d:propfind>
        """.trimIndent().toRequestBody("application/xml".toMediaType())
        val req = Request.Builder()
            .url(href)
            .header("Authorization", auth)
            .header("Depth", "infinity")
            .method("PROPFIND", propfind)
            .build()
        http.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful && resp.code != 207) {
                error("WebDAV PROPFIND ${resp.code}")
            }
            return parseResponses(resp.body?.string().orEmpty(), rootUrl())
        }
    }

    /** Legacy path-only list. */
    fun listRelativePaths(prefix: String = ""): List<String> =
        listResources(prefix).filter { !it.isCollection }.map { it.path }

    private fun parseResponses(xml: String, root: String): List<DavResource> {
        val rootNorm = root.trimEnd('/') + "/"
        val blocks = Regex(
            """<(?:D:)?response\b[^>]*>([\s\S]*?)</(?:D:)?response>""",
            RegexOption.IGNORE_CASE,
        ).findAll(xml)
        val out = mutableListOf<DavResource>()
        for (block in blocks) {
            val body = block.groupValues[1]
            val href = Regex(
                """<(?:D:)?href>([^<]+)</(?:D:)?href>""",
                RegexOption.IGNORE_CASE,
            ).find(body)?.groupValues?.get(1)?.trim()?.replace("%20", " ") ?: continue
            val abs = resolveHref(href) ?: continue
            val rel = relativePath(abs, rootNorm) ?: continue
            val isCollection = Regex(
                """<(?:D:)?collection\s*/>""",
                RegexOption.IGNORE_CASE,
            ).containsMatchIn(body) || rel.endsWith("/")
            if (isCollection || rel.isBlank()) continue
            val modifiedRaw = Regex(
                """<(?:D:)?getlastmodified>([^<]+)</(?:D:)?getlastmodified>""",
                RegexOption.IGNORE_CASE,
            ).find(body)?.groupValues?.get(1)?.trim().orEmpty()
            val etag = Regex(
                """<(?:D:)?getetag>([^<]+)</(?:D:)?getetag>""",
                RegexOption.IGNORE_CASE,
            ).find(body)?.groupValues?.get(1)?.trim().orEmpty()
            out += DavResource(
                path = rel.trimEnd('/'),
                lastModifiedMs = parseHttpDate(modifiedRaw),
                etag = etag.trim('"'),
                isCollection = false,
            )
        }
        return out.distinctBy { it.path }
    }

    private fun resolveHref(href: String): String? {
        return when {
            href.startsWith("http") -> href
            href.startsWith("/") -> {
                val origin = Regex("""^(https?://[^/]+)""").find(config.baseUrl)?.groupValues?.get(1)
                    .orEmpty()
                if (origin.isBlank()) null else origin + href
            }
            else -> null
        }
    }

    private fun relativePath(abs: String, rootNorm: String): String? {
        if (abs.startsWith(rootNorm) || abs.trimEnd('/') == rootNorm.trimEnd('/')) {
            return abs.removePrefix(rootNorm).trimStart('/').takeIf { it.isNotBlank() }
        }
        val rootPath = "/" + config.rootPath.trim().trim('/') + "/"
        val idx = abs.indexOf(rootPath)
        if (idx < 0) return null
        return abs.substring(idx + rootPath.length).trimStart('/').takeIf { it.isNotBlank() }
    }

    private fun parseHttpDate(raw: String): Long {
        if (raw.isBlank()) return 0L
        val formats = listOf(
            "EEE, dd MMM yyyy HH:mm:ss zzz",
            "EEE, dd MMM yyyy HH:mm:ss Z",
        )
        for (pattern in formats) {
            try {
                val fmt = SimpleDateFormat(pattern, Locale.US)
                fmt.timeZone = TimeZone.getTimeZone("GMT")
                return fmt.parse(raw)?.time ?: continue
            } catch (_: Exception) {
                // try next
            }
        }
        return 0L
    }

    private fun ensureParentDirs(relPath: String) {
        val parts = relPath.trim('/').split('/').dropLast(1)
        if (parts.isEmpty()) return
        mkcolRecursive(parts.joinToString("/"))
    }

    private fun mkcolRecursive(relDir: String) {
        val segments = relDir.trim('/').split('/').filter { it.isNotBlank() }
        var built = ""
        for (seg in segments) {
            built = if (built.isEmpty()) seg else "$built/$seg"
            mkcol(built)
        }
    }

    private fun mkcol(relDir: String) {
        val req = Request.Builder()
            .url(urlFor(relDir) + "/")
            .header("Authorization", auth)
            .method("MKCOL", ByteArray(0).toRequestBody(null))
            .build()
        http.newCall(req).execute().use { resp ->
            if (resp.isSuccessful || resp.code in setOf(405, 409, 301, 302)) return
        }
    }
}

data class VaultMirrorResult(
    val uploaded: Int = 0,
    val downloaded: Int = 0,
    val skipped: Int = 0,
    val message: String = "",
)

/**
 * Incremental vault mirror: upload/download only when mtime/size/hash differ.
 */
class VaultMirror(
    private val dataRoot: File,
    private val client: WebDavClient,
) {
    private val stateFile = File(dataRoot, ".webdav-state.json")
    private val skewMs = 2_000L

    /** Remember paths to DELETE on next sync (and try immediately when client is available). */
    fun queueRemoteDeletes(paths: List<String>) {
        if (paths.isEmpty()) return
        val (fingerprints, deleted) = loadStateBundle()
        deleted.addAll(paths)
        saveStateBundle(fingerprints, deleted)
    }

    fun sync(): VaultMirrorResult {
        return try {
            client.ensureRoot()
            var up = 0
            var down = 0
            var skipped = 0
            var removed = 0
            val (state, pendingDelete) = loadStateBundle()
            val remote = client.listResources()
                .filter { isVaultPath(it.path) }
                .associateBy { it.path }
            val localPaths = listLocalRelPaths()
            val seen = linkedSetOf<String>()

            val stillPending = linkedSetOf<String>()
            for (rel in pendingDelete) {
                try {
                    client.deleteFile(rel)
                    state.remove(rel)
                    removed++
                } catch (_: Exception) {
                    stillPending += rel
                }
            }

            for (rel in localPaths) {
                seen += rel
                if (rel in stillPending) stillPending.remove(rel)
                val file = File(dataRoot, rel)
                if (!file.isFile) continue
                val localMtime = file.lastModified()
                val localSize = file.length()
                val rem = remote[rel]
                val prev = state[rel]
                val needsUpload = when {
                    rem == null -> true
                    rem.lastModifiedMs > 0L && localMtime > rem.lastModifiedMs + skewMs -> true
                    rem.lastModifiedMs > 0L && rem.lastModifiedMs > localMtime + skewMs -> false
                    prev != null && prev.size == localSize && prev.mtime == localMtime -> false
                    prev != null && prev.hash.isNotBlank() && prev.hash == sha1(file) &&
                        rem.lastModifiedMs in 1..localMtime + skewMs -> false
                    rem.lastModifiedMs <= 0L && prev != null &&
                        prev.size == localSize && prev.mtime == localMtime -> false
                    else -> true
                }
                val needsDownload = rem != null &&
                    rem.lastModifiedMs > localMtime + skewMs &&
                    !needsUpload

                when {
                    needsDownload -> {
                        val bytes = client.getFile(rel) ?: continue
                        file.parentFile?.mkdirs()
                        file.writeBytes(bytes)
                        state[rel] = Fingerprint(file.lastModified(), file.length(), sha1(file))
                        down++
                    }
                    needsUpload -> {
                        client.putFile(rel, file.readBytes(), guessType(rel))
                        state[rel] = Fingerprint(localMtime, localSize, sha1(file))
                        up++
                    }
                    else -> {
                        state[rel] = Fingerprint(localMtime, localSize, prev?.hash ?: sha1(file))
                        skipped++
                    }
                }
            }

            for ((rel, _) in remote) {
                if (rel in seen) continue
                if (!isVaultPath(rel)) continue
                if (rel in stillPending || rel in pendingDelete) {
                    try {
                        client.deleteFile(rel)
                        state.remove(rel)
                        removed++
                        stillPending.remove(rel)
                    } catch (_: Exception) {
                        stillPending += rel
                    }
                    continue
                }
                val local = File(dataRoot, rel)
                if (local.isFile) continue
                val bytes = client.getFile(rel) ?: continue
                local.parentFile?.mkdirs()
                local.writeBytes(bytes)
                state[rel] = Fingerprint(local.lastModified(), local.length(), sha1(local))
                down++
            }

            writeManifest()
            val manifest = File(dataRoot, "manifest.json")
            if (manifest.isFile) {
                client.putFile("manifest.json", manifest.readBytes(), "application/json")
                state["manifest.json"] = Fingerprint(
                    manifest.lastModified(),
                    manifest.length(),
                    sha1(manifest),
                )
                up++
            }
            saveStateBundle(state, stillPending)
            VaultMirrorResult(
                uploaded = up,
                downloaded = down,
                skipped = skipped,
                message = "云盘增量同步：上传 $up，下载 $down，跳过 $skipped" +
                    if (removed > 0) "，删除 $removed" else "",
            )
        } catch (e: Exception) {
            VaultMirrorResult(message = "云盘同步失败：${e.message}")
        }
    }

    private fun isVaultPath(rel: String): Boolean {
        val name = rel.substringAfterLast('/')
        if (name.startsWith(".")) return false
        return rel.startsWith("diary/") ||
            rel.startsWith("assets/") ||
            rel.startsWith("todos/") ||
            rel == "manifest.json"
    }

    private fun listLocalRelPaths(): List<String> {
        val out = mutableListOf<String>()
        for (name in listOf("diary", "assets", "todos")) {
            val dir = File(dataRoot, name)
            if (!dir.exists()) continue
            dir.walkTopDown().filter { it.isFile }.forEach { f ->
                if (f.name.startsWith(".")) return@forEach
                out += f.relativeTo(dataRoot).path.replace('\\', '/')
            }
        }
        return out
    }

    private fun writeManifest() {
        val now = java.time.OffsetDateTime.now().toString()
        val json = """
            {
              "schema_version": 1,
              "exported_at": "$now",
              "device": "android",
              "revision": 0
            }
        """.trimIndent()
        File(dataRoot, "manifest.json").writeText(json, Charsets.UTF_8)
    }

    private fun guessType(rel: String): String = when {
        rel.endsWith(".json") -> "application/json"
        rel.endsWith(".md") -> "text/markdown"
        rel.endsWith(".png") -> "image/png"
        rel.endsWith(".jpg") || rel.endsWith(".jpeg") -> "image/jpeg"
        rel.endsWith(".webp") -> "image/webp"
        else -> "application/octet-stream"
    }

    private data class Fingerprint(val mtime: Long, val size: Long, val hash: String)

    private fun loadStateBundle(): Pair<MutableMap<String, Fingerprint>, MutableSet<String>> {
        if (!stateFile.isFile) return mutableMapOf<String, Fingerprint>() to mutableSetOf()
        return try {
            val root = JSONObject(stateFile.readText(Charsets.UTF_8))
            val files = root.optJSONObject("files") ?: JSONObject()
            val out = mutableMapOf<String, Fingerprint>()
            val keys = files.keys()
            while (keys.hasNext()) {
                val path = keys.next()
                val obj = files.optJSONObject(path) ?: continue
                out[path] = Fingerprint(
                    mtime = obj.optLong("mtime"),
                    size = obj.optLong("size"),
                    hash = obj.optString("hash"),
                )
            }
            val deleted = mutableSetOf<String>()
            val arr = root.optJSONArray("deleted")
            if (arr != null) {
                for (i in 0 until arr.length()) {
                    val p = arr.optString(i)
                    if (p.isNotBlank()) deleted += p
                }
            }
            out to deleted
        } catch (_: Exception) {
            mutableMapOf<String, Fingerprint>() to mutableSetOf()
        }
    }

    private fun saveStateBundle(
        state: Map<String, Fingerprint>,
        deleted: Collection<String>,
    ) {
        try {
            val files = JSONObject()
            for ((path, fp) in state) {
                files.put(
                    path,
                    JSONObject()
                        .put("mtime", fp.mtime)
                        .put("size", fp.size)
                        .put("hash", fp.hash),
                )
            }
            val delArr = org.json.JSONArray()
            deleted.forEach { delArr.put(it) }
            stateFile.writeText(
                JSONObject()
                    .put("files", files)
                    .put("deleted", delArr)
                    .toString(),
                Charsets.UTF_8,
            )
        } catch (_: Exception) {
            // local cache only
        }
    }

    private fun sha1(file: File): String {
        return try {
            val digest = MessageDigest.getInstance("SHA-1")
            file.inputStream().use { input ->
                val buf = ByteArray(8 * 1024)
                while (true) {
                    val n = input.read(buf)
                    if (n <= 0) break
                    digest.update(buf, 0, n)
                }
            }
            digest.digest().joinToString("") { "%02x".format(it) }
        } catch (_: Exception) {
            ""
        }
    }
}
