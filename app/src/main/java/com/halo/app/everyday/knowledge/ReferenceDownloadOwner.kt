package com.halo.app.everyday.knowledge

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import com.halo.app.everyday.EverydayWriteAuthority
import com.halo.app.everyday.collectors.ResearchRobotsPolicy
import java.io.*
import java.time.LocalDate
import java.util.UUID
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.json.JSONObject
import org.jsoup.Jsoup

data class DownloadedReference(val uri: Uri, val fileName: String)

/** Fixed optional catalogues. No recursive crawl, arbitrary model URL or bundled corpus. */
class ReferenceDownloadOwner(private val context: Context, private val transfer: ReferenceTransfer,
    private val authority: () -> (() -> Unit), private val power: () -> Boolean,
    private val retainedInputs: suspend () -> Set<String> = { emptySet() },
    private val ownedUri: (File) -> Uri = { file -> FileProvider.getUriForFile(context, "${context.packageName}.captures", file) }) {
    private val lock = Mutex()
    private val directory get() = File(context.filesDir, "reference-downloads").apply { mkdirs() }
    /** Hold the same lease through preview publication, so orphan cleanup cannot race another preview. */
    suspend fun <T> downloadForReview(id: ReferenceStarterId, review: suspend (DownloadedReference) -> T): T = lock.withLock { withContext(Dispatchers.IO) {
        val guard = authority(); guard()
        EverydayWriteAuthority.run(guard) {
            val retainedNames = retainedInputs().mapNotNull { handle ->
                val uri = Uri.parse(handle)
                if (uri.authority == "${context.packageName}.captures" && uri.pathSegments.firstOrNull() == "reference-libraries") uri.lastPathSegment else null
            }.toSet()
            directory.listFiles().orEmpty().filter { file ->
                file.name.matches(Regex("[0-9a-f-]{36}\\.(zip|jsonl)(\\.part)?")) && file.name !in retainedNames
            }.forEach { file -> guard(); EverydayWriteAuthority.requireCurrent(); file.delete() }
            val file = download(id)
            try { review(file) }
            catch (error: Throwable) {
                withContext(NonCancellable + Dispatchers.IO) {
                    // A cancelled return can follow a successful preview commit. Keep that resumable input.
                    runCatching { if (file.uri.toString() !in retainedInputs()) releaseOwnedInput(file.uri.toString()) }
                }
                throw error
            }
        }
    } }
    private suspend fun download(id: ReferenceStarterId): DownloadedReference = withContext(Dispatchers.IO) {
        val guard = authority(); guard(); require(power()) { "Choose Power before downloading; local import/search still work offline" }
        EverydayWriteAuthority.run(guard) {
            val extension = if (id == ReferenceStarterId.MEDLINEPLUS) "zip" else "jsonl"
            val name = when (id) { ReferenceStarterId.MEDLINEPLUS -> "MedlinePlus-health-topics"; ReferenceStarterId.PREPAREDNESS -> "Emergency-preparedness-CDC"; else -> "General-reference-Wikipedia" } + ".$extension"
            val part = File(directory, "${UUID.randomUUID()}.$extension.part")
            val finished = File(directory, "${UUID.randomUUID()}.$extension")
            fun checkStorage(reserved: Long = 0) {
                guard(); EverydayWriteAuthority.requireCurrent()
                require(power()) { "Reference download needs Power" }
                require(context.filesDir.usableSpace >= ReferenceLimits.FREE_RESERVE_BYTES + reserved) { "Keep 512 MiB free before downloading" }
                require(listOf(directory, File(context.filesDir, "material-images")).sumOf { dir -> dir.listFiles().orEmpty().sumOf { it.length() } } + context.getDatabasePath("halo.db").let {
                    it.length() + File(it.path + "-wal").length() + File(it.path + "-shm").length()
                } + reserved <= ReferenceLimits.STORAGE_BYTES) { "Managed reference storage budget reached" }
            }
            try {
                checkStorage()
                FileOutputStream(part).use { file ->
                    val output = object : FilterOutputStream(file) {
                        private var written = 0L
                        override fun write(value: Int) { checkStorage(1); written++; require(written <= ReferenceLimits.INPUT_BYTES); out.write(value) }
                        override fun write(bytes: ByteArray, offset: Int, length: Int) { checkStorage(length.toLong()); written += length; require(written <= ReferenceLimits.INPUT_BYTES); out.write(bytes, offset, length) }
                    }
                    when (id) {
                        ReferenceStarterId.MEDLINEPLUS -> {
                            val index = transfer.bytes("https://medlineplus.gov/xml.html".toHttpUrl(), 1024L * 1024)
                            val url = latestMedlineZip(index.toString(Charsets.UTF_8))
                            transfer.fetch(url, ReferenceLimits.INPUT_BYTES, output)
                        }
                        ReferenceStarterId.PREPAREDNESS -> {
                            val robots = ResearchRobotsPolicy.parse(transfer.bytes("https://www.cdc.gov/robots.txt".toHttpUrl(), 65536).toString(Charsets.UTF_8))
                            require(robots.crawlDelaySeconds <= 60) { "Source requests longer spacing; use an official data download instead" }
                            for ((index, address) in preparednessUrls.withIndex()) {
                                currentCoroutineContext().ensureActive(); checkStorage()
                                val url = address.toHttpUrl(); require(robots.allows(url.encodedPath + if (url.encodedQuery != null) "?${url.encodedQuery}" else "")) { "This source disallows indexing this page" }
                                if (index > 0 && robots.crawlDelaySeconds > 0) delay(robots.crawlDelaySeconds * 1000)
                                val bytes = transfer.bytes(url, 2L * 1024 * 1024, checkIndexing = true)
                                writeEntry(output, cdcEntry(bytes, address))
                            }
                        }
                        ReferenceStarterId.WIKIPEDIA -> {
                            for (title in wikipediaTitles) {
                                currentCoroutineContext().ensureActive(); checkStorage()
                                val request = WikipediaArticleRequest(title = title)
                                val bytes = transfer.bytes(request.endpoint("en"), 2L * 1024 * 1024)
                                WikipediaArticleParser.entries(bytes, "en", request).forEach { writeEntry(output, it) }
                            }
                        }
                    }
                    output.flush(); file.fd.sync()
                }
                guard(); checkStorage(); require(part.renameTo(finished)) { "Could not retain downloaded file" }
                DownloadedReference(ownedUri(finished), name)
            } catch (error: Throwable) { part.delete(); finished.delete(); throw error }
        }
    }

    fun releaseOwnedInput(uri: String) {
        val value = Uri.parse(uri)
        if (value.authority == "${context.packageName}.captures" && value.pathSegments.size == 2 && value.pathSegments[0] == "reference-libraries") {
            val name = value.pathSegments[1]
            if (name.matches(Regex("[0-9a-f-]{36}\\.(zip|jsonl)"))) File(directory, name).delete()
        }
    }
    companion object {
        val preparednessUrls = listOf(
            "https://www.cdc.gov/water-emergency/about/",
            "https://www.cdc.gov/water-emergency/about/how-to-create-and-store-an-emergency-water-supply.html",
            "https://www.cdc.gov/food-safety/foods/keep-food-safe-after-emergency.html",
            "https://www.cdc.gov/winter-weather/prevention/index.html",
            "https://www.cdc.gov/heat-health/risk-factors/",
            "https://www.cdc.gov/floods/safety/reentering-your-flooded-home-safety.html",
        )
        val wikipediaTitles = listOf("Water", "Food preservation", "Navigation", "First aid", "Emergency management", "Electricity", "Solar System", "Knot")
        fun latestMedlineZip(html: String): HttpUrl {
            val document = Jsoup.parse(html, "https://medlineplus.gov/xml.html")
            return document.select("a[href]").mapNotNull { anchor -> runCatching { anchor.absUrl("href").toHttpUrl() }.getOrNull() }
                .filter { it.isHttps && it.host == "medlineplus.gov" && it.query == null && it.encodedPath.matches(Regex("/xml/mplus_topics_compressed_[0-9]{4}-[0-9]{2}-[0-9]{2}\\.zip")) }
                .maxByOrNull { LocalDate.parse(it.encodedPath.substringAfter("compressed_").removeSuffix(".zip")) }
                ?: error("The official MedlinePlus index has no supported compressed topic file")
        }
        fun cdcEntry(bytes: ByteArray, url: String): ReferenceEntry {
            require(url in preparednessUrls)
            val document = Jsoup.parse(bytes.toString(Charsets.UTF_8), url)
            val restrictions = document.select("meta[name=robots],meta[name=halo]").joinToString(",") { it.attr("content") }.lowercase(java.util.Locale.ROOT)
            require(listOf("noindex", "noarchive", "none").none { it in restrictions }) { "Source disallows retaining this page" }
            val updated = document.selectFirst("meta[property=article:modified_time],meta[name=CDC:last_updated],meta[name=CDC:last_reviewed]")?.attr("content")?.take(40)?.takeIf { it.isNotBlank() }
            val title = document.selectFirst("h1")?.text()?.take(500) ?: document.title().take(500)
            document.select("script,style,noscript,iframe,form,nav,footer,header,svg,template,figure").remove()
            val main = document.selectFirst("main") ?: document.selectFirst("[role=main]") ?: error("Source has no supported article body")
            require(!main.text().contains('©') && !main.text().contains("copyright", true)) { "This page needs a separate reuse review" }
            val body = ReferenceHtmlText.extract(main)
            val id = java.security.MessageDigest.getInstance("SHA-256").digest(url.toByteArray()).joinToString("") { "%02x".format(it) }
            return ReferenceEntry(id, title, body, "en", url, "CDC", null,
                "CDC public-domain text. Source: CDC. Free original at source URL. Halo is not endorsed by CDC, HHS or the US Government.", updated).also(ReferenceLimits::validate)
        }
        private fun writeEntry(output: OutputStream, entry: ReferenceEntry) {
            ReferenceLimits.validate(entry)
            val row = JSONObject().put("id", entry.id).put("title", entry.title).put("text", entry.text).put("language", entry.language)
                .put("publisher", entry.publisher).put("license", entry.license).put("format", entry.format.name).put("images", com.halo.app.everyday.collectors.SourceImages.encode(entry.images)).apply {
                    entry.url?.let { put("url", it) }; entry.revision?.let { put("revision", it) }; entry.updatedAt?.let { put("updatedAt", it) }
                }
            output.write((row.toString() + "\n").toByteArray(Charsets.UTF_8))
        }
    }
}
