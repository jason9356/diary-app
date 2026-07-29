package com.personaldiary.android.weather

import android.content.Context
import android.location.Geocoder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * Prefer a precise place label: city + building / road / POI
 * e.g. 「西安市 · XX大厦」rather than only district.
 */
class PlaceResolver(private val context: Context) {
    private val http = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    suspend fun labelFor(lat: Double, lon: Double): String = withContext(Dispatchers.IO) {
        val fromOs = nominatimLabel(lat, lon)
        if (!fromOs.isNullOrBlank()) return@withContext fromOs
        geocoderLabel(lat, lon) ?: fallback(lat, lon)
    }

    @Suppress("DEPRECATION")
    private fun geocoderLabel(lat: Double, lon: Double): String? {
        try {
            if (!Geocoder.isPresent()) return null
            val geocoder = Geocoder(context, Locale.CHINA)
            val addr = geocoder.getFromLocation(lat, lon, 1)?.firstOrNull() ?: return null
            val city = listOfNotNull(addr.locality, addr.subAdminArea, addr.adminArea)
                .firstOrNull { !it.isNullOrBlank() }
                ?.trim()
                .orEmpty()
            val place = sequenceOf(
                addr.featureName,
                listOfNotNull(addr.thoroughfare, addr.subThoroughfare).joinToString("").ifBlank { null },
                addr.premises,
            ).mapNotNull { it?.trim()?.takeIf { s -> s.isNotEmpty() } }
                .firstOrNull { candidate ->
                    candidate != city &&
                        !candidate.matches(Regex("""^\d+(\.\d+)?$""")) &&
                        candidate.length >= 2
                }
            val district = addr.subLocality?.trim().orEmpty()
            return when {
                city.isNotBlank() && !place.isNullOrBlank() -> "$city · $place"
                city.isNotBlank() && district.isNotBlank() && district != city ->
                    "$city · $district"
                city.isNotBlank() -> city
                !place.isNullOrBlank() -> place
                else -> null
            }
        } catch (_: Exception) {
            return null
        }
    }

    private fun nominatimLabel(lat: Double, lon: Double): String? {
        return try {
            val url =
                "https://nominatim.openstreetmap.org/reverse?format=jsonv2&lat=$lat&lon=$lon" +
                    "&accept-language=zh-CN&zoom=18&addressdetails=1"
            val req = Request.Builder()
                .url(url)
                .header("User-Agent", "personal-diary-android/0.4 (solo)")
                .get()
                .build()
            http.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return null
                val json = JSONObject(resp.body?.string().orEmpty())
                val address = json.optJSONObject("address") ?: JSONObject()
                val city = firstNonBlank(
                    address.optString("city"),
                    address.optString("town"),
                    address.optString("municipality"),
                    address.optString("county"),
                    address.optString("state"),
                )
                val place = firstNonBlank(
                    address.optString("building"),
                    address.optString("amenity"),
                    address.optString("shop"),
                    address.optString("office"),
                    address.optString("tourism"),
                    address.optString("leisure"),
                    json.optString("name"),
                    address.optString("road"),
                )
                val suburb = firstNonBlank(
                    address.optString("suburb"),
                    address.optString("neighbourhood"),
                    address.optString("quarter"),
                )
                when {
                    city != null && place != null && place != city -> "$city · $place"
                    city != null && suburb != null && suburb != city -> "$city · $suburb"
                    city != null -> city
                    place != null -> place
                    else -> null
                }
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun firstNonBlank(vararg values: String?): String? =
        values.mapNotNull { it?.trim()?.takeIf { s -> s.isNotEmpty() } }.firstOrNull()

    private fun fallback(lat: Double, lon: Double): String =
        String.format(Locale.US, "%.2f, %.2f", lat, lon)
}
