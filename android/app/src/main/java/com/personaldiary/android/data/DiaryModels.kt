package com.personaldiary.android.data

import java.time.LocalDate
import java.time.OffsetDateTime
import java.util.UUID

data class DiaryEntry(
    val id: String = UUID.randomUUID().toString(),
    val entryDate: String,
    val title: String = entryDate,
    val body: String = "",
    val createdAt: String = nowIso(),
    val updatedAt: String = nowIso(),
    val writingDurationSec: Int = 0,
    val location: String = "",
    val weather: String = "",
    val tempC: Double? = null,
    val contextSource: String = "",
    val contextUpdatedAt: String = "",
    val imageRels: List<String> = emptyList(),
) {
    val hasContext: Boolean
        get() = location.isNotBlank() || weather.isNotBlank() || tempC != null

    fun contextLine(): String {
        val parts = mutableListOf<String>()
        if (location.isNotBlank()) parts += location
        val wx = weather.trim()
        when {
            tempC != null && wx.isNotEmpty() -> parts += "$wx ${formatTemp(tempC)}"
            tempC != null -> parts += formatTemp(tempC)
            wx.isNotEmpty() -> parts += wx
        }
        return parts.joinToString(" · ")
    }

    companion object {
        fun today(): String = LocalDate.now().toString()

        fun nowIso(): String = OffsetDateTime.now().toString()

        fun formatTemp(t: Double): String {
            val s = if (t % 1.0 == 0.0) t.toInt().toString() else t.toString()
            return "${s}°"
        }
    }
}

data class WeatherSnapshot(
    val location: String,
    val weather: String,
    val tempC: Double,
)
