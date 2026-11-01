package com.halo.app.everyday.collectors

import java.time.Instant
import java.time.ZoneId
import org.json.JSONArray
import org.json.JSONObject
import okhttp3.HttpUrl.Companion.toHttpUrl

object OpenMeteoParser {
    /** Only city-scale coordinates and requested weather fields leave the device. */
    fun endpoint(place: WeatherPlace): String = "https://api.open-meteo.com/v1/forecast".toHttpUrl().newBuilder()
        .addQueryParameter("latitude", "%.2f".format(java.util.Locale.ROOT, place.latitude))
        .addQueryParameter("longitude", "%.2f".format(java.util.Locale.ROOT, place.longitude))
        .addQueryParameter("current", "temperature_2m,weather_code")
        .addQueryParameter("daily", "weather_code,temperature_2m_max,temperature_2m_min,precipitation_probability_max")
        .addQueryParameter("timezone", place.zoneId).addQueryParameter("forecast_days", "5")
        .addQueryParameter("timeformat", "unixtime").addQueryParameter("temperature_unit", "celsius").build().toString()

    fun parse(bytes: ByteArray, place: WeatherPlace): WeatherFact {
        require(bytes.size <= CollectorRefreshPolicy.MAX_BODY_BYTES)
        val json = SafeCollectorJson.objectValue(bytes.toString(Charsets.UTF_8))
        require(!json.optBoolean("error", false)) { "Weather provider could not serve this place" }
        val current = json.optJSONObject("current")
        val units = json.optJSONObject("current_units")
        if (current != null) require(units?.optString("temperature_2m") == "°C" && units.optString("time") == "unixtime") { "Weather units are not supported" }
        val observed = current?.takeUnless { it.isNull("time") }?.getLong("time")?.let(Instant::ofEpochSecond)
        val daily = json.optJSONObject("daily")
        val times = daily?.optJSONArray("time") ?: JSONArray()
        require(times.length() <= 16)
        if (times.length() > 0) {
            val dailyUnits = requireNotNull(json.optJSONObject("daily_units"))
            require(dailyUnits.optString("time") == "unixtime" && dailyUnits.optString("temperature_2m_max") == "°C" &&
                dailyUnits.optString("temperature_2m_min") == "°C" && dailyUnits.optString("precipitation_probability_max") == "%")
            listOf("temperature_2m_max", "temperature_2m_min", "precipitation_probability_max", "weather_code").forEach {
                require(daily?.getJSONArray(it)?.length() == times.length()) { "Forecast arrays have different lengths" }
            }
        }
        val zone = ZoneId.of(place.zoneId)
        val days = (0 until times.length()).map { index ->
            WeatherDay(Instant.ofEpochSecond(times.getLong(index)).atZone(zone).toLocalDate(),
                daily?.optJSONArray("temperature_2m_max")?.number(index, -100.0, 80.0),
                daily?.optJSONArray("temperature_2m_min")?.number(index, -100.0, 80.0),
                daily?.optJSONArray("precipitation_probability_max")?.number(index, 0.0, 100.0)?.toInt(),
                daily?.optJSONArray("weather_code")?.number(index, 0.0, 99.0)?.toInt())
        }
        val temperature = current?.number("temperature_2m", -100.0, 80.0)
        val code = current?.number("weather_code", 0.0, 99.0)?.toInt()
        require(days.zipWithNext().all { (first, second) -> first.date < second.date })
        require(temperature != null || days.isNotEmpty()) { "Weather response has no forecast or current observation" }
        return WeatherFact(place, observed, temperature, code, days)
    }
    private fun JSONObject.number(key: String, min: Double, max: Double): Double? =
        if (isNull(key)) null else getDouble(key).also { require(it.isFinite() && it in min..max) }
    private fun JSONArray.number(index: Int, min: Double, max: Double): Double? =
        if (index >= length() || isNull(index)) null else getDouble(index).also { require(it.isFinite() && it in min..max) }
}
