package com.halo.app.everyday.collectors

import android.util.AtomicFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest
import java.time.Clock
import java.util.Locale

enum class KnowledgeFormat { WIKIPEDIA, COUNTRIES, POPULATION, TIME_ZONES, LANGUAGES, EXTERNAL }

data class KnowledgePack(val id: String, val title: String, val publisher: String, val sourceUrl: String,
    val format: KnowledgeFormat = KnowledgeFormat.EXTERNAL, val endpoint: String = sourceUrl) {
    val searchable get() = format != KnowledgeFormat.EXTERNAL
}

object KnowledgeCatalogue {
    private const val KIWIX_WIKI = "https://download.kiwix.org/zim/wikipedia"
    private fun wiki(id: String, title: String, language: String, articles: String): KnowledgePack {
        val base = "https://$language.wikipedia.org"
        val titles = java.net.URLEncoder.encode(articles, "UTF-8")
        return KnowledgePack(id, title, "Wikipedia", base, KnowledgeFormat.WIKIPEDIA,
            "$base/w/api.php?action=query&format=json&formatversion=2&prop=extracts%7Cinfo&exintro=1&explaintext=1&exlimit=max&redirects=1&titles=$titles")
    }

    val packs = listOf(
        wiki("wikibaltics", "Wikipedia · Baltic states", "en", "Latvia|Riga|Latvian_language|Baltic_states|Daugavpils|Estonia|Lithuania"),
        wiki("wikibaltics-lv", "Vikipēdija · Baltijas valstis", "lv", "Latvija|Rīga|Latviešu_valoda|Baltijas_valstis|Daugavpils|Igaunija|Lietuva"),
        wiki("wikibaltics-ru", "Википедия · Страны Балтии", "ru", "Латвия|Рига|Латышский_язык|Прибалтика|Даугавпилс|Эстония|Литва"),
        wiki("wikispace", "Wikipedia · Solar System", "en", "Solar_System|Sun|Moon|Earth|Mars|Jupiter|Saturn|Asteroid_belt"),
        KnowledgePack("wb-countries", "Countries and economies", "World Bank",
            "https://data.worldbank.org/country", KnowledgeFormat.COUNTRIES,
            "https://api.worldbank.org/v2/country?format=json&per_page=400"),
        KnowledgePack("wb-population", "Population by economy and region", "World Bank",
            "https://data.worldbank.org/indicator/SP.POP.TOTL", KnowledgeFormat.POPULATION,
            "https://api.worldbank.org/v2/country/all/indicator/SP.POP.TOTL?format=json&mrnev=1&per_page=400"),
        KnowledgePack("iana-zones", "World time zones", "IANA", "https://www.iana.org/time-zones",
            KnowledgeFormat.TIME_ZONES, "https://data.iana.org/time-zones/tzdb/zone1970.tab"),
        KnowledgePack("iana-languages", "Language tags and scripts", "IANA",
            "https://www.iana.org/assignments/language-subtag-registry",
            KnowledgeFormat.LANGUAGES, "https://www.iana.org/assignments/language-subtag-registry/language-subtag-registry"),
        KnowledgePack("kiwix-wikipedia", "All Wikipedia ZIM archives", "Kiwix", "$KIWIX_WIKI/"),
        KnowledgePack("kiwix-wiki-en", "English Wikipedia · summaries · 2026-09", "Kiwix",
            "$KIWIX_WIKI/wikipedia_en_all_mini_2026-09.zim"),
        KnowledgePack("kiwix-wiki-lv", "Latvijas Vikipēdija · bez attēliem · 2026-10", "Kiwix",
            "$KIWIX_WIKI/wikipedia_lv_all_nopic_2026-10.zim"),
        KnowledgePack("kiwix-wiki-ru", "Русская Википедия · краткие статьи · 2026-10", "Kiwix",
            "$KIWIX_WIKI/wikipedia_ru_all_mini_2026-10.zim"),
        KnowledgePack("kiwix-wiki-pl", "Polska Wikipedia · streszczenia · 2026-08", "Kiwix",
            "$KIWIX_WIKI/wikipedia_pl_all_mini_2026-08.zim"),
        KnowledgePack("kiwix-library", "Offline books, wikis and educational collections", "Kiwix", "https://library.kiwix.org/"),
        KnowledgePack("wikimedia-exports", "MediaWiki content exports", "Wikimedia",
            "https://dumps.wikimedia.org/other/mediawiki_content_history/readme.html"),
        KnowledgePack("wikimedia-dumps", "Wikimedia per-wiki and database dumps", "Wikimedia", "https://dumps.wikimedia.org/"),
        KnowledgePack("wikidata-dumps", "Wikidata entity dumps", "Wikidata",
            "https://www.wikidata.org/wiki/Wikidata:Database_download"),
        KnowledgePack("gdelt-events", "Global news event archives", "GDELT", "https://data.gdeltproject.org/events/"),
    )
}

data class KnowledgeEntry(val title: String, val text: String, val url: String)
data class KnowledgeSnapshot(val revision: String, val retrievedAt: String, val entries: List<KnowledgeEntry>)
data class KnowledgePackState(val pack: KnowledgePack, val snapshot: KnowledgeSnapshot?)
data class KnowledgeHit(val pack: KnowledgePack, val revision: String, val retrievedAt: String, val entry: KnowledgeEntry)

/** Bounded publisher snapshots. Download and indexing complete before the previous revision is replaced. */
class OfflineKnowledge(
    private val directory: File,
    private val fetch: suspend (url: String, accept: String) -> ArticleDownload,
    private val downloadAuthority: () -> (() -> Unit),
    private val clock: Clock = Clock.systemUTC(),
) {
    private val lock = Mutex()
    private val downloads = Mutex()
    private val cache = mutableMapOf<String, KnowledgeSnapshot?>()
    private val updates = MutableStateFlow(0L)
    val changes = updates.asStateFlow()

    suspend fun catalogue(): List<KnowledgePackState> = withContext(Dispatchers.IO) {
        lock.withLock { KnowledgeCatalogue.packs.map { KnowledgePackState(it, snapshot(it)) } }
    }

    suspend fun download(packId: String): KnowledgeSnapshot = withContext(Dispatchers.IO) {
        val verifyAuthority = downloadAuthority()
        val pack = KnowledgeCatalogue.packs.single { it.id == packId && it.searchable }
        downloads.withLock {
            verifyAuthority()
            val accept = when (pack.format) {
                KnowledgeFormat.WIKIPEDIA, KnowledgeFormat.COUNTRIES, KnowledgeFormat.POPULATION -> "application/json"
                else -> "text/plain"
            }
            val response = fetch(pack.endpoint, accept)
            val entries = parse(pack, response.bytes.toString(response.charset))
            require(entries.size in 1..12000)
            val digest = MessageDigest.getInstance("SHA-256")
            digest.update(pack.id.toByteArray())
            val revision = digest.digest(response.bytes).joinToString("") { "%02x".format(it) }
            val snapshot = KnowledgeSnapshot(revision, clock.instant().toString(), entries)
            val payload = JSONObject().put("revision", revision).put("retrievedAt", snapshot.retrievedAt)
                .put("entries", JSONArray(entries.map { entry ->
                    JSONObject().put("title", entry.title).put("text", entry.text).put("url", entry.url)
                })).toString().toByteArray()
            require(payload.size <= MAX_INDEX_BYTES)
            lock.withLock {
                verifyAuthority()
                ensureActive()
                check(directory.isDirectory || directory.mkdirs())
                val file = file(pack)
                val output = file.startWrite()
                try {
                    output.write(payload)
                    verifyAuthority()
                    ensureActive()
                    file.finishWrite(output)
                } catch (failure: Throwable) {
                    file.failWrite(output)
                    throw failure
                }
                check(readIndex(pack).contentEquals(payload)) { "Knowledge snapshot could not be saved" }
                cache[pack.id] = snapshot
                updates.value += 1
            }
            snapshot
        }
    }

    suspend fun search(query: String, packId: String? = null, revision: String? = null): List<KnowledgeHit> =
        withContext(Dispatchers.IO) {
            val phrase = normalize(query.trim())
            val terms = phrase.split(Regex("\\s+")).filter { it.isNotEmpty() }
            require(query.length <= 160)
            if (terms.isEmpty()) return@withContext emptyList()
            require(revision == null || packId != null)
            lock.withLock {
                val packs = KnowledgeCatalogue.packs.filter { it.searchable && (packId == null || packId == it.id) }
                require(packId == null || packs.isNotEmpty())
                packs.asSequence().flatMap { pack ->
                    val stored = snapshot(pack)
                    require(revision == null || stored?.revision == revision) { "Knowledge snapshot changed" }
                    stored?.entries.orEmpty().asSequence().mapNotNull { entry ->
                        ensureActive()
                        val title = normalize(entry.title)
                        val text = normalize(entry.text)
                        val score = when {
                            title == phrase || title.substringBefore(" · ") == phrase -> 4
                            title.contains(phrase) -> 3
                            terms.all(title::contains) -> 2
                            terms.all { title.contains(it) || text.contains(it) } -> 1
                            else -> return@mapNotNull null
                        }
                        score to KnowledgeHit(pack, requireNotNull(stored).revision, stored.retrievedAt, entry)
                    }
                }.sortedByDescending { it.first }.take(12).map { it.second }.toList()
            }
        }

    private fun file(pack: KnowledgePack) = AtomicFile(File(directory, "${pack.id}.json"))

    private fun readIndex(pack: KnowledgePack): ByteArray = file(pack).openRead().use { input ->
        val size = input.channel.size()
        require(size in 1L..MAX_INDEX_BYTES.toLong())
        ByteArray(size.toInt()).also { java.io.DataInputStream(input).readFully(it) }
    }

    private fun snapshot(pack: KnowledgePack): KnowledgeSnapshot? = if (!pack.searchable) null else cache.getOrPut(pack.id) {
        runCatching {
            val root = JSONObject(readIndex(pack).toString(Charsets.UTF_8))
            KnowledgeSnapshot(root.getString("revision"), root.getString("retrievedAt"),
                root.getJSONArray("entries").objects().map {
                    KnowledgeEntry(it.getString("title"), it.getString("text"), it.getString("url"))
                })
        }.getOrNull()
    }

    private fun parse(pack: KnowledgePack, text: String): List<KnowledgeEntry> = when (pack.format) {
        KnowledgeFormat.WIKIPEDIA -> SafeCollectorJson.objectValue(text).getJSONObject("query").getJSONArray("pages")
            .objects().mapNotNull { page ->
                val extract = page.optString("extract").trim()
                val revision = page.optLong("lastrevid")
                val pageId = page.optLong("pageid")
                if (pageId <= 0 || revision <= 0 || extract.isEmpty()) null
                else KnowledgeEntry(page.getString("title").take(200), extract.take(6000),
                    "${pack.sourceUrl}/w/index.php?oldid=$revision")
            }
        KnowledgeFormat.COUNTRIES -> worldBankRows(text).mapNotNull { row ->
            val id = row.getString("id")
            val region = row.getJSONObject("region").getString("value")
            if (!id.matches(Regex("[A-Z0-9]{3}")) || region == "Aggregates") return@mapNotNull null
            val fields = listOf("Code $id", "ISO alpha-2: ${row.getString("iso2Code")}", "Region: $region",
                "Income group: ${row.getJSONObject("incomeLevel").getString("value")}") +
                listOfNotNull(row.optString("capitalCity").takeIf { it.isNotBlank() }?.let { "Capital: $it" })
            KnowledgeEntry(row.getString("name").take(200), fields.joinToString("\n").take(2000),
                "https://api.worldbank.org/v2/country/$id?format=json")
        }
        KnowledgeFormat.POPULATION -> worldBankRows(text).mapNotNull { row ->
            val code = row.optString("countryiso3code")
            val year = row.optString("date")
            val value = row.optLong("value", -1)
            if (!code.matches(Regex("[A-Z0-9]{3}")) || !year.matches(Regex("[0-9]{4}")) || value < 0) return@mapNotNull null
            require(row.getJSONObject("indicator").getString("id") == "SP.POP.TOTL")
            val country = row.getJSONObject("country").getString("value")
            KnowledgeEntry("$country · Population".take(200), "World Bank series SP.POP.TOTL · $code · $year: $value people",
                "https://data.worldbank.org/indicator/SP.POP.TOTL?locations=$code")
        }
        KnowledgeFormat.TIME_ZONES -> text.lineSequence().filter { it.isNotBlank() && !it.startsWith('#') }
            .map { line ->
                val fields = line.split('\t', limit = 4)
                require(fields.size >= 3 && fields[0].matches(Regex("[A-Z,]+")) && fields[1].matches(Regex("[0-9+-]+")))
                val zone = fields[2]
                require(zone.matches(Regex("[A-Za-z0-9_+/-]{1,120}")))
                val description = listOf("Country codes: ${fields[0]}", "Coordinates: ${fields[1]}") + fields.drop(3)
                KnowledgeEntry(zone, description.joinToString("\n").take(1000), pack.endpoint)
            }.toList()
        KnowledgeFormat.LANGUAGES -> {
            require(text.startsWith("File-Date:"))
            text.split("\n%%").drop(1).mapNotNull { record ->
                val lines = record.lineSequence().map(String::trim).toList()
                val tag = lines.firstOrNull { it.startsWith("Subtag: ") || it.startsWith("Tag: ") }
                    ?.substringAfter(": ") ?: return@mapNotNull null
                val names = lines.filter { it.startsWith("Description: ") }.map { it.removePrefix("Description: ") }
                if (names.isEmpty()) return@mapNotNull null
                KnowledgeEntry("$tag · ${names.first()}".take(200), lines.filter { it.isNotEmpty() }
                    .joinToString("\n").take(1500), pack.endpoint)
            }
        }
        KnowledgeFormat.EXTERNAL -> error("External archives open at the publisher")
    }

    private fun worldBankRows(text: String): List<JSONObject> {
        val pages = SafeCollectorJson.objectValue("{\"data\":$text}").getJSONArray("data")
        val meta = pages.getJSONObject(0)
        val entries = pages.getJSONArray(1)
        require(meta.getInt("pages") == 1 && meta.getInt("total") == entries.length())
        return entries.objects()
    }

    private fun JSONArray.objects(): List<JSONObject> {
        require(length() <= 12000)
        return List(length(), ::getJSONObject)
    }

    private fun normalize(text: String) = java.text.Normalizer.normalize(text.lowercase(Locale.ROOT), java.text.Normalizer.Form.NFKD)
        .replace(COMBINING_MARKS, "")

    private companion object {
        const val MAX_INDEX_BYTES = 4 * 1024 * 1024
        val COMBINING_MARKS = Regex("\\p{M}+")
    }
}
