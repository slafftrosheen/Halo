package com.halo.app.everyday.collectors

import java.io.InputStream
import java.text.Normalizer
import java.time.Instant
import java.time.ZoneId
import java.util.Locale
import java.util.zip.GZIPInputStream
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class OfflineCity(val id: String, val name: String, val countryCode: String, val latitude: Double,
    val longitude: Double, val zoneId: String, val population: Long) {
    fun weatherPlace() = WeatherPlace(name, latitude, longitude, zoneId)
    fun time(at: Instant) = at.atZone(ZoneId.of(zoneId))
}

/** Bundled GeoNames data. Searching, choosing a city and reading world time require no network or location permission. */
class OfflineCityCatalogue(private val openAsset: () -> InputStream) {
    private data class Entry(val city: OfflineCity, val nameKey: String, val searchKey: String)
    private val lock = Mutex()
    @Volatile private var entries: List<Entry>? = null
    suspend fun search(query: String, countryCode: String? = null, limit: Int = 30): List<OfflineCity> = withContext(Dispatchers.Default) {
        require(query.length <= 120 && limit in 1..100)
        require(countryCode == null || countryPattern.matches(countryCode))
        val words = normalize(query).trim().split(wordSeparators).filter { it.isNotBlank() }
        val joined = words.joinToString(" ")
        val candidates = load().filterIndexed { index, entry ->
            if (index % 128 == 0) ensureActive()
            (countryCode == null || entry.city.countryCode == countryCode) && words.all { it in entry.searchKey }
        }
        candidates.sortedWith(compareBy<Entry> { when {
            words.isEmpty() -> 2
            it.nameKey == joined -> 0
            it.nameKey.startsWith(joined) -> 1
            else -> 2
        } }.thenByDescending { it.city.population }.thenBy { it.city.name }.thenBy { it.city.id }).take(limit).map { it.city }
    }
    suspend fun get(id: String): OfflineCity? = load().firstOrNull { it.city.id == id }?.city
    private suspend fun load(): List<Entry> = entries ?: lock.withLock {
        entries ?: withContext(Dispatchers.IO) {
            val loaded = mutableListOf<Entry>()
            var characters = 0L
            GZIPInputStream(openAsset()).bufferedReader(Charsets.UTF_8).useLines { lines ->
                lines.forEachIndexed { index, line ->
                    ensureActive(); require(index < 50_000 && line.length <= 5000)
                    characters += line.length; require(characters <= 16 * 1024 * 1024)
                    val columns = line.split('\t'); require(columns.size == 9)
                    val zone = columns[6]
                    // Older Android time-zone databases can lack a newly introduced zone; do not guess its offset.
                    if (runCatching { ZoneId.of(zone) }.isFailure) return@forEachIndexed
                    val city = OfflineCity(columns[0], columns[1], columns[3], columns[4].toDouble(), columns[5].toDouble(), zone, columns[7].toLong())
                    city.weatherPlace()
                    require(cityIdPattern.matches(city.id) && city.population >= 0 && countryPattern.matches(city.countryCode))
                    loaded += Entry(city, normalize(city.name), normalize("${city.name} ${columns[2]} ${city.countryCode} ${columns[8]}"))
                }
            }
            loaded.toList().also { entries = it }
        }
    }
    companion object {
        const val ATTRIBUTION = "GeoNames · CC BY 4.0"
        const val ATTRIBUTION_URL = "https://www.geonames.org/"
        // The bundled catalogue has tens of thousands of rows. Compile validators once,
        // rather than repeatedly invoking Android's regular-expression compiler on first open.
        private val cityIdPattern = Regex("[0-9]{1,12}")
        private val countryPattern = Regex("[A-Z]{2}")
        private val wordSeparators = Regex("\\s+")
        private val marks = Regex("\\p{M}+")
        private fun normalize(value: String) = marks.replace(Normalizer.normalize(value, Normalizer.Form.NFKD), "").lowercase(Locale.ROOT)
    }
}
