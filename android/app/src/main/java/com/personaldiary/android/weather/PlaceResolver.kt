package com.personaldiary.android.weather

import android.content.Context
import android.location.Geocoder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale

class PlaceResolver(private val context: Context) {
    @Suppress("DEPRECATION")
    suspend fun labelFor(lat: Double, lon: Double): String = withContext(Dispatchers.IO) {
        try {
            if (!Geocoder.isPresent()) return@withContext fallback(lat, lon)
            val geocoder = Geocoder(context, Locale.CHINA)
            val list = geocoder.getFromLocation(lat, lon, 1)
            val addr = list?.firstOrNull() ?: return@withContext fallback(lat, lon)
            val city = addr.locality
                ?: addr.subAdminArea
                ?: addr.adminArea
                ?: addr.featureName
            val district = addr.subLocality
            when {
                !city.isNullOrBlank() && !district.isNullOrBlank() && district != city ->
                    "$city·$district"
                !city.isNullOrBlank() -> city
                else -> fallback(lat, lon)
            }
        } catch (_: Exception) {
            fallback(lat, lon)
        }
    }

    private fun fallback(lat: Double, lon: Double): String =
        String.format(Locale.US, "%.2f, %.2f", lat, lon)
}
