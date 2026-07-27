package com.personaldiary.android.weather

import com.personaldiary.android.data.WeatherSnapshot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class WeatherService(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .build(),
) {
    private val wmoZh = mapOf(
        0 to "晴", 1 to "晴间多云", 2 to "多云", 3 to "阴",
        45 to "雾", 48 to "雾凇",
        51 to "小毛毛雨", 53 to "毛毛雨", 55 to "大毛毛雨",
        61 to "小雨", 63 to "中雨", 65 to "大雨",
        71 to "小雪", 73 to "中雪", 75 to "大雪",
        80 to "小阵雨", 81 to "阵雨", 82 to "强阵雨",
        95 to "雷阵雨", 96 to "雷阵雨伴冰雹", 99 to "强雷阵雨伴冰雹",
    )

    suspend fun fetch(lat: Double, lon: Double, locationLabel: String): WeatherSnapshot? =
        withContext(Dispatchers.IO) {
            val url =
                "https://api.open-meteo.com/v1/forecast?latitude=${"%.4f".format(lat)}" +
                    "&longitude=${"%.4f".format(lon)}&current=temperature_2m,weather_code&timezone=auto"
            val body = httpGet(url) ?: return@withContext null
            val current = JSONObject(body).optJSONObject("current") ?: return@withContext null
            val code = current.optInt("weather_code", 0)
            if (!current.has("temperature_2m")) return@withContext null
            val temp = current.getDouble("temperature_2m")
            WeatherSnapshot(
                location = locationLabel,
                weather = wmoZh[code] ?: "天气",
                tempC = (Math.round(temp * 10.0) / 10.0),
            )
        }

    private fun httpGet(url: String): String? {
        val req = Request.Builder()
            .url(url)
            .header("User-Agent", "DiaryAndroid/0.1 (Open-Meteo)")
            .build()
        client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) return null
            return resp.body?.string()
        }
    }
}
