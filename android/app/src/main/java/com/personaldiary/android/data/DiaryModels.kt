package com.personaldiary.android.data

import java.time.LocalDate
import java.time.OffsetDateTime

data class DiaryEntry(
    val id: String,
    val entryDate: String,
    val title: String = entryDate,
    val body: String = "",
    val createdAt: String = DiaryDates.nowIso(),
    val updatedAt: String = DiaryDates.nowIso(),
    val writingDurationSec: Int = 0,
    val imageRels: List<String> = emptyList(),
)

data class DayContext(
    val date: String,
    val location: String = "",
    val weather: String = "",
    val tempC: Double? = null,
    val contextSource: String = "",
    val contextUpdatedAt: String = "",
    val updatedAt: String = "",
) {
    val hasContext: Boolean
        get() = location.isNotBlank() || weather.isNotBlank() || tempC != null

    fun contextLine(): String {
        val parts = mutableListOf<String>()
        if (location.isNotBlank()) parts += location
        val wx = weather.trim()
        when {
            tempC != null && wx.isNotEmpty() -> parts += "$wx ${DiaryDates.formatTemp(tempC)}"
            tempC != null -> parts += DiaryDates.formatTemp(tempC)
            wx.isNotEmpty() -> parts += wx
        }
        return parts.joinToString(" · ")
    }
}

data class WeatherSnapshot(
    val location: String,
    val weather: String,
    val tempC: Double,
)

data class TimelineDay(
    val date: String,
    val context: DayContext,
    val notes: List<DiaryEntry>,
)

object DiaryDates {
    fun today(): String = LocalDate.now().toString()

    fun nowIso(): String = OffsetDateTime.now().toString()

    fun formatTemp(t: Double): String {
        val s = if (t % 1.0 == 0.0) t.toInt().toString() else t.toString()
        return "${s}°"
    }
}
