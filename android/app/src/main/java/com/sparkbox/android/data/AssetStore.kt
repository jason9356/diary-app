package com.sparkbox.android.data

import android.content.Context
import android.net.Uri
import java.io.File
import java.util.UUID

class AssetStore(private val assetsRoot: File) {
    private val allowed = setOf("png", "jpg", "jpeg", "gif", "webp", "bmp")

    init {
        assetsRoot.mkdirs()
    }

    fun dirFor(entryId: String): File = File(assetsRoot, entryId).also { it.mkdirs() }

    fun listRels(entryId: String): List<String> {
        val dir = File(assetsRoot, entryId)
        if (!dir.isDirectory) return emptyList()
        return dir.listFiles()
            ?.filter { it.isFile && it.extension.lowercase() in allowed }
            ?.sortedBy { it.name }
            ?.map { "assets/$entryId/${it.name}" }
            .orEmpty()
    }

    fun absolute(rel: String): File = File(assetsRoot.parentFile, rel)

    fun saveFromUri(context: Context, entryId: String, uri: Uri): String {
        val nameGuess = uri.lastPathSegment?.substringAfterLast('/') ?: "image.jpg"
        val ext = nameGuess.substringAfterLast('.', "jpg").lowercase().let {
            if (it in allowed) it else "jpg"
        }
        val dest = File(dirFor(entryId), "${UUID.randomUUID().toString().take(12)}.$ext")
        context.contentResolver.openInputStream(uri)?.use { input ->
            dest.outputStream().use { output -> input.copyTo(output) }
        } ?: error("无法读取图片")
        return "assets/$entryId/${dest.name}"
    }

    fun listFiles(entryId: String): List<File> {
        val dir = File(assetsRoot, entryId)
        if (!dir.isDirectory) return emptyList()
        return dir.listFiles()?.filter { it.isFile }?.sortedBy { it.name }.orEmpty()
    }

    fun assetFile(entryId: String, name: String): File = File(dirFor(entryId), name)

    /** Remove all files under assets/<entryId>/ and the folder itself. */
    fun deleteEntry(entryId: String) {
        val dir = File(assetsRoot, entryId)
        if (!dir.exists()) return
        dir.walkBottomUp().forEach { it.delete() }
    }
}
