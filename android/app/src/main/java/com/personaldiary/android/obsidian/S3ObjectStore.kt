package com.personaldiary.android.obsidian

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.net.URLEncoder
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.TimeUnit
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

data class S3Config(
    val endpoint: String = "",
    val region: String = "us-east-1",
    val bucket: String = "",
    val accessKey: String = "",
    val secretKey: String = "",
    val prefix: String = "",
) {
    val enabled: Boolean
        get() = endpoint.isNotBlank() && bucket.isNotBlank() &&
            accessKey.isNotBlank() && secretKey.isNotBlank()
}

/**
 * Minimal S3-compatible client (list / get / put) via OkHttp + SigV4.
 * Works with MinIO / COS / OSS when path-style or virtual-host is configured via endpoint.
 */
class S3ObjectStore(private val config: S3Config) {
    private val http = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    fun listMarkdownKeys(folderPrefix: String): List<String> {
        if (!config.enabled) return emptyList()
        val prefix = joinPrefix(config.prefix, folderPrefix).trimStart('/')
        val params = "list-type=2&prefix=${enc(prefix)}"
        val path = "/${config.bucket}"
        val xml = signedGet(path, params)
        val keys = mutableListOf<String>()
        val re = Regex("""<Key>([^<]+)</Key>""")
        re.findAll(xml).forEach { m ->
            val key = m.groupValues[1]
            if (key.endsWith(".md", ignoreCase = true)) keys += key
        }
        return keys
    }

    fun getObject(key: String): String {
        val path = "/${config.bucket}/${key.trimStart('/')}"
        return signedGet(path, "")
    }

    fun putObject(key: String, body: String) {
        val path = "/${config.bucket}/${key.trimStart('/')}"
        signedPut(path, body.toByteArray(Charsets.UTF_8), "text/markdown; charset=utf-8")
    }

    private fun joinPrefix(a: String, b: String): String {
        val left = a.trim().trim('/')
        val right = b.trim().trim('/')
        return when {
            left.isEmpty() -> right
            right.isEmpty() -> left
            else -> "$left/$right"
        }
    }

    private fun enc(s: String): String =
        URLEncoder.encode(s, Charsets.UTF_8.name()).replace("+", "%20")

    private fun baseUrl(): String = config.endpoint.trim().trimEnd('/')

    private fun signedGet(canonicalPath: String, query: String): String {
        val url = if (query.isBlank()) {
            "${baseUrl()}$canonicalPath"
        } else {
            "${baseUrl()}$canonicalPath?$query"
        }
        val headers = sigHeaders("GET", canonicalPath, query, ByteArray(0), "")
        val req = Request.Builder().url(url).get().apply {
            headers.forEach { (k, v) -> header(k, v) }
        }.build()
        http.newCall(req).execute().use { resp ->
            val body = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) error("S3 GET ${resp.code}: $body")
            return body
        }
    }

    private fun signedPut(canonicalPath: String, payload: ByteArray, contentType: String) {
        val url = "${baseUrl()}$canonicalPath"
        val headers = sigHeaders("PUT", canonicalPath, "", payload, contentType)
        val req = Request.Builder()
            .url(url)
            .put(payload.toRequestBody(contentType.toMediaType()))
            .apply { headers.forEach { (k, v) -> header(k, v) } }
            .build()
        http.newCall(req).execute().use { resp ->
            val body = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) error("S3 PUT ${resp.code}: $body")
        }
    }

    private fun sigHeaders(
        method: String,
        canonicalPath: String,
        query: String,
        payload: ByteArray,
        contentType: String,
    ): Map<String, String> {
        val amzDate = amzDate()
        val dateStamp = amzDate.substring(0, 8)
        val payloadHash = sha256Hex(payload)
        val host = hostOf(baseUrl())
        val headers = linkedMapOf(
            "host" to host,
            "x-amz-content-sha256" to payloadHash,
            "x-amz-date" to amzDate,
        )
        if (contentType.isNotBlank()) headers["content-type"] = contentType
        val signedHeaders = headers.keys.sorted().joinToString(";")
        val canonicalHeaders = headers.keys.sorted().joinToString("") { k ->
            "$k:${headers[k]}\n"
        }
        val canonicalQuery = canonicalizeQuery(query)
        val canonicalRequest = listOf(
            method,
            canonicalPath,
            canonicalQuery,
            canonicalHeaders,
            signedHeaders,
            payloadHash,
        ).joinToString("\n")
        val credentialScope = "$dateStamp/${config.region}/s3/aws4_request"
        val stringToSign = listOf(
            "AWS4-HMAC-SHA256",
            amzDate,
            credentialScope,
            sha256Hex(canonicalRequest.toByteArray(Charsets.UTF_8)),
        ).joinToString("\n")
        val signingKey = getSignatureKey(config.secretKey, dateStamp, config.region, "s3")
        val signature = hmacHex(signingKey, stringToSign)
        val auth =
            "AWS4-HMAC-SHA256 Credential=${config.accessKey}/$credentialScope, " +
                "SignedHeaders=$signedHeaders, Signature=$signature"
        val out = headers.toMutableMap()
        out["Authorization"] = auth
        return out
    }

    private fun canonicalizeQuery(query: String): String {
        if (query.isBlank()) return ""
        return query.split('&')
            .map { part ->
                val i = part.indexOf('=')
                if (i < 0) enc(part) to ""
                else enc(part.substring(0, i)) to enc(part.substring(i + 1))
            }
            .sortedBy { it.first }
            .joinToString("&") { (k, v) -> if (v.isEmpty()) k else "$k=$v" }
    }

    private fun hostOf(url: String): String {
        val without = url.removePrefix("https://").removePrefix("http://")
        return without.substringBefore('/')
    }

    private fun amzDate(): String {
        val fmt = SimpleDateFormat("yyyyMMdd'T'HHmmss'Z'", Locale.US)
        fmt.timeZone = TimeZone.getTimeZone("UTC")
        return fmt.format(Date())
    }

    private fun sha256Hex(data: ByteArray): String {
        val d = MessageDigest.getInstance("SHA-256").digest(data)
        return d.joinToString("") { "%02x".format(it) }
    }

    private fun sha256Hex(s: String): String = sha256Hex(s.toByteArray(Charsets.UTF_8))

    private fun hmac(key: ByteArray, data: String): ByteArray {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(key, "HmacSHA256"))
        return mac.doFinal(data.toByteArray(Charsets.UTF_8))
    }

    private fun hmacHex(key: ByteArray, data: String): String =
        hmac(key, data).joinToString("") { "%02x".format(it) }

    private fun getSignatureKey(
        key: String,
        dateStamp: String,
        regionName: String,
        serviceName: String,
    ): ByteArray {
        val kDate = hmac("AWS4$key".toByteArray(Charsets.UTF_8), dateStamp)
        val kRegion = hmac(kDate, regionName)
        val kService = hmac(kRegion, serviceName)
        return hmac(kService, "aws4_request")
    }
}
