package com.halo.app.everyday.knowledge

import android.content.Context
import android.graphics.BitmapFactory
import androidx.room.withTransaction
import com.halo.app.data.AppDatabase
import com.halo.app.data.knowledge.*
import com.halo.app.everyday.*
import com.halo.app.everyday.collectors.*
import java.io.*
import java.security.MessageDigest
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.json.JSONArray

/** Pins the original text/source scope; a cache file itself never grants access. */
data class MaterialImageTarget(val sourceId: String, val sourceVersion: Long, val revision: String, val entryId: String, val library: Boolean = false)
data class SavedMaterialImage(val image: SourceImage, val file: File?)
data class MaterialImageReceipt(val available: Int, val saved: Int, val failed: Int)
class MaterialImageRepository(private val context: Context, private val database: AppDatabase,
    private val sources: CollectorRepository, private val libraries: ReferenceLibraryRepository,
    private val transfer: ReferenceTransfer, private val authority: () -> (() -> Unit),
    private val kids: () -> Boolean, private val power: () -> Boolean, private val policyRevision: () -> Any = { Unit }) {
    fun readIdentity(): Any { authority().invoke(); EverydayWriteAuthority.requireCurrent(); return policyRevision() }
    private val mutex = Mutex()
    private val dao get() = database.referenceDao()
    private val directory get() = File(context.filesDir, "material-images").apply { mkdirs() }
    private fun key(target: MaterialImageTarget, image: SourceImage) = MessageDigest.getInstance("SHA-256")
        .digest("${target.sourceId}|${target.revision}|${target.entryId}|${image.url}".toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
    private suspend fun references(target: MaterialImageTarget): List<SourceImage> {
        val source = requireNotNull(sources.source(target.sourceId))
        require(source.version == target.sourceVersion && source.selected && source.enabled && !source.muted && (!kids() || source.childApproved))
        return if (target.library) {
            SourceImages.decode(JSONArray(libraries.entry(source.id, source.version, target.revision, target.entryId).imagesJson))
        } else {
            val state = requireNotNull(sources.state(source.id)); require(state.source == source && state.snapshot?.contentRevision == target.revision)
            requireNotNull((state.snapshot?.payload as? CollectorPayload.Articles)?.items?.find { it.id == target.entryId }).images
        }.also { SourceImages.encode(it) }
    }
    suspend fun local(target: MaterialImageTarget): List<SavedMaterialImage> = withContext(Dispatchers.IO) {
        val guard = authority(); val check: () -> Unit = { guard(); EverydayWriteAuthority.requireCurrent() }
        check(); val epoch = EverydayReadAuthority.capture()
        val images = references(target)
        val values = images.map { image -> val id = key(target, image)
            SavedMaterialImage(image, if (dao.image(id) != null) File(directory, "$id.image").takeIf { it.isFile } else null)
        }
        require(references(target) == images); check(); EverydayReadAuthority.requireCurrent(epoch); values
    }
    suspend fun fetch(target: MaterialImageTarget): MaterialImageReceipt = mutex.withLock { withContext(Dispatchers.IO) {
        val check = authority(); check(); require(!kids() && power()) { "An adult saves source images in Power" }
        EverydayWriteAuthority.run(check) {
            val original = references(target); var saved = 0; var failed = 0
            val parent = currentCoroutineContext().job
            val watcher = CoroutineScope(currentCoroutineContext()).launch(start = CoroutineStart.UNDISPATCHED) {
                sources.observe().first { rows -> rows.find { it.source.id == target.sourceId }?.let { state ->
                    state.source.version != target.sourceVersion || !state.source.selected || !state.source.enabled || state.source.muted ||
                        (!target.library && state.snapshot?.contentRevision != target.revision)
                } != false }
                parent.cancel(CancellationException("Image source selection changed"))
            }
            try {
            prune()
            val robots = mutableMapOf<String, ResearchRobotsPolicy>()
            val requestedHosts = mutableSetOf<String>()
            for (image in original) {
                check(); ensureActive(); require(!kids() && power() && references(target) == original)
                val id = key(target, image); val file = File(directory, "$id.image"); val part = File(directory, "$id.part")
                if (dao.image(id) != null && file.isFile) { saved++; continue }
                try {
                    budget(IMAGE_BYTES)
                    val url = image.url.toHttpUrl()
                    val rules = robots[url.host] ?: ResearchRobotsPolicy.parse(transfer.bytes(url.newBuilder().encodedPath("/robots.txt").query(null).fragment(null).build(), 65536).toString(Charsets.UTF_8)).also { robots[url.host] = it }
                    require(rules.crawlDelaySeconds <= 60 && rules.allows(url.encodedPath + (url.encodedQuery?.let { "?$it" } ?: "")))
                    if (!requestedHosts.add(url.host) && rules.crawlDelaySeconds > 0) delay(rules.crawlDelaySeconds * 1000)
                    FileOutputStream(part).use { output -> transfer.fetch(image.url.toHttpUrl(), IMAGE_BYTES, output, checkIndexing = true, imageOnly = true); output.fd.sync() }
                    require(part.length() in 1..IMAGE_BYTES)
                    val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    BitmapFactory.decodeFile(part.path, options)
                    require(options.outWidth > 0 && options.outHeight > 0 && options.outWidth.toLong() * options.outHeight <= MAX_PIXELS && options.outMimeType in setOf("image/jpeg", "image/png", "image/webp")) { "Unsupported or oversized picture" }
                    check(); require(!kids() && power() && references(target) == original)
                    database.withTransaction {
                        check(); EverydayWriteAuthority.requireCurrent(); require(references(target) == original)
                        require(part.renameTo(file)); dao.putImage(MaterialImageEntity(id, target.sourceId, System.currentTimeMillis(), file.length()))
                        check(); EverydayWriteAuthority.requireCurrent()
                    }
                    saved++
                } catch (cancelled: CancellationException) { throw cancelled }
                catch (_: Exception) { failed++; if (dao.image(id) == null) file.delete() }
                finally { part.delete() }
            }
            check(); require(references(target) == original && !kids() && power())
            MaterialImageReceipt(original.size, saved, failed)
            } finally { watcher.cancel() }
        }
    } }
    private suspend fun prune() {
        val rows = dao.images(); val ids = rows.map { "${it.id}.image" }.toSet()
        directory.listFiles().orEmpty().filter { it.name !in ids }.forEach { it.delete() }
        var total = directory.listFiles().orEmpty().sumOf { it.length() }
        for (row in rows) {
            val file = File(directory, "${row.id}.image")
            if (total + IMAGE_BYTES > CACHE_BYTES || !file.isFile) { total -= file.length(); file.delete(); dao.removeImage(row.id) }
        }
    }
    private fun budget(reserved: Long) {
        require(directory.listFiles().orEmpty().sumOf { it.length() } + reserved <= CACHE_BYTES)
        require(context.filesDir.usableSpace >= ReferenceLimits.FREE_RESERVE_BYTES + reserved)
        val db = context.getDatabasePath("halo.db")
        val used = listOf(db, File(db.path + "-wal"), File(db.path + "-shm")).sumOf { it.length() } +
            listOf(directory, File(context.filesDir,"reference-downloads")).sumOf { folder -> folder.listFiles().orEmpty().sumOf { it.length() } }
        require(used + reserved <= ReferenceLimits.STORAGE_BYTES)
    }
    companion object { const val IMAGE_BYTES = 2L * 1024 * 1024; const val CACHE_BYTES = 64L * 1024 * 1024; const val MAX_PIXELS = 4_000_000L }
}
