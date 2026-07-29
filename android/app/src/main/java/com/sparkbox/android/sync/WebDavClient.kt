package com.sparkbox.android.sync

import okhttp3.Credentials
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
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

    fun listRelativePaths(prefix: String = ""): List<String> {
        val href = urlFor(prefix) + if (prefix.isNotEmpty() && !prefix.endsWith("/")) "/" else ""
        val propfind = """
            <?xml version="1.0" encoding="utf-8" ?>
            <d:propfind xmlns:d="DAV:">
              <d:prop><d:displayname/><d:getlastmodified/><d:resourcetype/></d:prop>
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
            val xml = resp.body?.string().orEmpty()
            return parseHrefList(xml, rootUrl())
        }
    }

    private fun parseHrefList(xml: String, root: String): List<String> {
        val rootNorm = root.trimEnd('/') + "/"
        val hrefRe = Regex("""<(?:D:)?href>([^<]+)</(?:D:)?href>""", RegexOption.IGNORE_CASE)
        return hrefRe.findAll(xml).mapNotNull { m ->
            var href = m.groupValues[1].trim()
            // Decode basic %20
            href = href.replace("%20", " ")
            val abs = when {
                href.startsWith("http") -> href
                href.startsWith("/") -> {
                    val baseHost = config.baseUrl.trim().trimEnd('/').substringBefore("/dav")
                        .substringBefore("/remote.php")
                    // Prefer absolute against baseUrl host
                    val origin = Regex("""^(https?://[^/]+)""").find(config.baseUrl)?.groupValues?.get(1).orEmpty()
                    origin + href
                }
                else -> return@mapNotNull null
            }
            if (!abs.startsWith(rootNorm) && abs.trimEnd('/') != root.trimEnd('/')) {
                // try path-only match after root path segment
                val rootPath = "/" + config.rootPath.trim().trim('/') + "/"
                val idx = abs.indexOf(rootPath)
                if (idx < 0) return@mapNotNull null
                val rel = abs.substring(idx + rootPath.length).trimStart('/')
                return@mapNotNull rel.takeIf { it.isNotBlank() && !it.endsWith("/") }
            }
            abs.removePrefix(rootNorm).trimStart('/').takeIf { it.isNotBlank() && !it.endsWith("/") }
        }.distinct().toList()
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
            // 201 created, 405/409 already exists — ok
            if (resp.isSuccessful || resp.code in setOf(405, 409, 301, 302)) return
            if (resp.code == 405) return
        }
    }
}

data class VaultMirrorResult(
    val uploaded: Int = 0,
    val downloaded: Int = 0,
    val message: String = "",
)

/**
 * Mirror local vault folders diary/, assets/, todos/ to WebDAV root.
 * Whole-file upload of local tree; pull remote files missing or newer by simple overwrite from remote list for todos.json + pull missing assets/md.
 */
class VaultMirror(
    private val dataRoot: File,
    private val client: WebDavClient,
) {
    fun sync(): VaultMirrorResult {
        if (!client.let { true }) return VaultMirrorResult(message = "WebDAV 未配置")
        return try {
            client.ensureRoot()
            var up = 0
            var down = 0
            // Push local
            for (rel in listLocalRelPaths()) {
                val f = File(dataRoot, rel)
                if (!f.isFile) continue
                client.putFile(rel, f.readBytes(), guessType(rel))
                up++
            }
            // Pull remote files not present locally
            for (rel in client.listRelativePaths()) {
                if (!rel.startsWith("diary/") && !rel.startsWith("assets/") && !rel.startsWith("todos/")) continue
                val local = File(dataRoot, rel)
                if (local.isFile) continue
                val bytes = client.getFile(rel) ?: continue
                local.parentFile?.mkdirs()
                local.writeBytes(bytes)
                down++
            }
            // Refresh manifest
            writeManifest()
            client.putFile(
                "manifest.json",
                File(dataRoot, "manifest.json").readBytes(),
                "application/json",
            )
            up++
            VaultMirrorResult(uploaded = up, downloaded = down, message = "云盘镜像完成：上传 $up，下载 $down")
        } catch (e: Exception) {
            VaultMirrorResult(message = "云盘同步失败：${e.message}")
        }
    }

    private fun listLocalRelPaths(): List<String> {
        val out = mutableListOf<String>()
        for (name in listOf("diary", "assets", "todos")) {
            val dir = File(dataRoot, name)
            if (!dir.exists()) continue
            dir.walkTopDown().filter { it.isFile }.forEach { f ->
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
}
