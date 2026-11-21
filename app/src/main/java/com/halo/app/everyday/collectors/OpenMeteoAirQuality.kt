package com.halo.app.everyday.collectors

import java.time.Instant
import java.util.Locale
import okhttp3.HttpUrl.Companion.toHttpUrl
import kotlin.math.roundToInt

object OpenMeteoAirQuality {
    const val ATTRIBUTION_URL = "https://open-meteo.com/en/docs/air-quality-api"
    private const val aqi = "european_aqi"

    /** Only an explicitly chosen city's two-decimal coordinates leave Halo. */
    fun endpoint(place: WeatherPlace): String = "https://air-quality-api.open-meteo.com/v1/air-quality".toHttpUrl().newBuilder()
        .addQueryParameter("latitude", "%.2f".format(Locale.ROOT, place.latitude))
        .addQueryParameter("longitude", "%.2f".format(Locale.ROOT, place.longitude))
        .addQueryParameter("current", (listOf(aqi) + PollenSpecies.entries.map { it.parameter }).joinToString(","))
        .addQueryParameter("timeformat", "unixtime")
        .build().toString()

    fun parse(bytes: ByteArray, place: WeatherPlace): AirQualityFact {
        require(bytes.size <= CollectorRefreshPolicy.MAX_BODY_BYTES)
        val json = SafeCollectorJson.objectValue(bytes.toString(Charsets.UTF_8))
        require(!json.optBoolean("error")) { "Air quality provider could not serve this place" }
        val current = json.getJSONObject("current")
        val units = json.getJSONObject("current_units")
        require(units.optString("time") == "unixtime")
        val observed = Instant.ofEpochSecond(current.getLong("time"))
        val index = if (current.isNull(aqi)) null else current.getDouble(aqi).also {
            require(it.isFinite() && it in 0.0..10_000.0 && units.optString(aqi) == "EAQI")
        }.roundToInt()
        val pollen = buildMap {
            PollenSpecies.entries.forEach { species ->
                if (!current.isNull(species.parameter)) {
                    val amount = current.getDouble(species.parameter)
                    require(amount.isFinite() && amount in 0.0..1_000_000.0)
                    require(units.optString(species.parameter).replace(" ", "").lowercase(Locale.ROOT) == "grains/m³")
                    put(species, amount)
                }
            }
        }
        require(index != null || pollen.isNotEmpty()) { "No air-quality observations available" }
        return AirQualityFact(place, index, pollen, observed)
    }
}

fun europeanAqiLabel(index: Int): String = when {
    index <= 20 -> "Good"
    index <= 40 -> "Fair"
    index <= 60 -> "Moderate"
    index <= 80 -> "Poor"
    index <= 100 -> "Very poor"
    else -> "Extremely poor"
}
