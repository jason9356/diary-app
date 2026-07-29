package com.sparkbox.android.data

import android.os.Build

object DeviceLabels {
    /** Short label for read meta — keep under ~18 chars when possible. */
    fun currentPhone(): String {
        val brand = Build.BRAND.orEmpty().trim()
        val model = Build.MODEL.orEmpty().trim()
        val raw = when {
            brand.isBlank() -> model
            model.isBlank() -> brand
            model.contains(brand, ignoreCase = true) -> model
            else -> "$brand $model"
        }.replace(Regex("\\s+"), " ").trim()
        return shorten(raw.ifBlank { "手机" }, 18)
    }

    fun shorten(text: String, max: Int): String {
        val t = text.trim().replace(Regex("\\s+"), " ")
        if (t.length <= max) return t
        return t.take(max - 1).trimEnd() + "…"
    }
}
