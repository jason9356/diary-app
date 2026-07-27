package com.personaldiary.android.data

import android.content.Context
import android.net.Uri
import java.io.File
import java.util.UUID

class AssetStore(private val assetsRoot: File) {
    private val allowed = setOf("png", "jpg", "jpeg", "gif", "webp", "bmp")

    init {
        assetsRoot.mkdirs()
    }

    fun dirFor(entryDate: String): File = File(assetsRoot, entryDate).also { it.mkdirs() }

    fun listRels(entryDate: String): List<String> {
        val dir = File(assetsRoot, entryDate)
        if (!dir.isDirectory) return emptyList()
        return dir.listFiles()
            ?.filter { it.isFile && it.extension.lowercase() in allowed }
            ?.sortedBy { it.name }
            ?.map { "assets/$entryDate/${it.name}" }
            .orEmpty()
    }

    fun absolute(rel: String): File = File(assetsRoot.parentFile, rel)

    fun saveFromUri(context: Context, entryDate: String, uri: Uri): String {
        val nameGuess = uri.lastPathSegment?.substringAfterLast('/') ?: "image.jpg"
        val ext = nameGuess.substringAfterLast('.', "jpg").lowercase().let {
            if (it in allowed) it else "jpg"
        }
        val dest = File(dirFor(entryDate), "${UUID.randomUUID().toString().take(12)}.$ext")
        context.contentResolver.openInputStream(uri)?.use { input ->
            dest.outputStream().use { output -> input.copyTo(output) }
        } ?: error("无法读取图片")
        return "assets/$entryDate/${dest.name}"
    }
}
