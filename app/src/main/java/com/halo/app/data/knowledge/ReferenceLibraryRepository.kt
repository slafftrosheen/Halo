package com.halo.app.data.knowledge

import androidx.room.withTransaction
import com.halo.app.data.AppDatabase
import com.halo.app.everyday.*
import com.halo.app.everyday.collectors.*
import com.halo.app.everyday.knowledge.*
import java.io.InputStream
import java.security.MessageDigest
import java.util.Locale
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject

data class ReferenceSearchPage(val entries: List<ReferenceEntryEntity>, val sources: Map<String, CollectorSource>,
    val snapshot: String, val nextOffset: Int?)

/** The source owns policy; this owner owns content generations and their derived index. */
class ReferenceLibraryRepository(
    private val database: AppDatabase, private val sources: CollectorRepository,
    private val openInput: (String) -> InputStream,
    private val freeBytes: () -> Long, private val databaseBytes: () -> Long,
    private val authority: () -> (() -> Unit), private val kids: () -> Boolean,
    private val scope: CoroutineScope,
    private val policyRevision: () -> Any = { Unit },
    private val releaseInput: (String) -> Unit = {},
) {
    private val dao get() = database.referenceDao()
    val libraries = dao.observeLibraries()
    val jobs = dao.observeJobs()
    private val importer = Mutex()
    private val initialization = Mutex()
    private var initialized = false
    private val running = ConcurrentHashMap<String, Job>()
    private val processIdentity = UUID.randomUUID().toString()
    fun readIdentity(): Any { authority().invoke(); return processIdentity to policyRevision() }
    fun requireReadIdentity(expected: Any) { check(readIdentity() == expected) { "Library privacy or family scope changed" } }
    suspend fun initialize() = initialization.withLock { if (!initialized) { dao.recover(System.currentTimeMillis()); initialized = true } }

    private fun adult(): () -> Unit { val check = authority(); check(); require(!kids()) { "An adult imports libraries" }; return { check(); require(!kids()) } }
    private fun roomBudget(reserved: Long = 0) {
        require(freeBytes() >= ReferenceLimits.FREE_RESERVE_BYTES + reserved) { "Keep at least 512 MiB free; choose a smaller library" }
        require(databaseBytes() + reserved <= ReferenceLimits.STORAGE_BYTES) { "The managed storage budget is 2 GiB; remove an unused library or choose a smaller subset" }
    }
    private fun fingerprint(uri: String, check: () -> Unit): Pair<String, Long> {
        val hash = MessageDigest.getInstance("SHA-256"); var count = 0L
        openInput(uri).use { raw ->
            val input = ReferenceImportParser.BoundedInput(raw, ReferenceLimits.INPUT_BYTES)
            val bytes = ByteArray(64 * 1024)
            while (true) { check(); val n = input.read(bytes); if (n < 0) break; hash.update(bytes, 0, n); count += n }
        }
        return hash.digest().joinToString("") { "%02x".format(it) } to count
    }
    private class PreviewComplete : RuntimeException()

    /** Only UI/document-handle owners call this; the assistant cannot provide an arbitrary path. */
    suspend fun preview(uri: String, fileName: String, targetSourceId: String? = null, expectedVersion: Long? = null, origin: String = "USER_IMPORT"): ReferenceImportJobEntity = withContext(Dispatchers.IO) {
        initialize(); val guard = adult(); val context = currentCoroutineContext()
        val check = { guard(); context.ensureActive() }
        EverydayWriteAuthority.run(guard) {
        require(origin in setOf("USER_IMPORT", "MEDLINEPLUS", "PREPAREDNESS", "WIKIPEDIA"))
        require(fileName.isNotBlank() && fileName.length <= 200)
        require((targetSourceId == null) == (expectedVersion == null))
        if (targetSourceId != null) {
            val old = requireNotNull(sources.source(targetSourceId)); require(old.kind == CollectorKind.LOCAL_LIBRARY && old.version == expectedVersion)
        }
        roomBudget(); val (fingerprint, bytes) = fingerprint(uri, check)
        require(dao.byFingerprint(fingerprint) == null) { "This exact library is already installed" }
        val sample = mutableListOf<ReferenceEntry>()
        try {
            openInput(uri).use { input -> ReferenceImportParser.parse(fileName, input, { check() }) { value -> sample.add(value); if (sample.size == 3) throw PreviewComplete() } }
        } catch (error: Exception) {
            if (generateSequence(error as Throwable?) { it.cause }.none { it is PreviewComplete }) throw error
        }
        require(sample.isNotEmpty()) { "No supported entries were found" }
        check()
        val now = System.currentTimeMillis()
        val title = fileName.substringBeforeLast('.').take(200).ifBlank { "Reference library" }
        val job = ReferenceImportJobEntity(UUID.randomUUID().toString(), targetSourceId ?: "library:${UUID.randomUUID()}", expectedVersion,
            uri, fileName, title, ReferenceImportState.REVIEWING.name, UUID.randomUUID().toString(), fingerprint, bytes, 0,
            UUID.randomUUID().toString(), JSONArray(sample.map { JSONObject().put("title", it.title).put("language", it.language)
                .put("text", it.text.take(200)).put("format", it.format.name).put("publisher", it.publisher).put("license", it.license) }).toString(), null, now, now, origin)
        dao.putJob(job); job
        }
    }

    suspend fun recentImports(): List<ReferenceImportJobEntity> { val check = adult(); initialize(); return dao.recentJobs().also { check() } }
    suspend fun job(id: String): ReferenceImportJobEntity? { val check = adult(); initialize(); return dao.job(id).also { check() } }
    suspend fun catalogue(): List<Pair<CollectorSource, ReferenceLibraryEntity>> {
        val check = authority(); check(); initialize(); val stamp = EverydayReadAuthority.capture()
        val values = dao.libraries().mapNotNull { library -> sources.source(library.sourceId)?.takeIf { it.kind == CollectorKind.LOCAL_LIBRARY && (!kids() || visible(it)) }?.let { it to library } }
        check(); EverydayReadAuthority.requireCurrent(stamp); return values
    }
    private fun visible(source: CollectorSource) = source.kind == CollectorKind.LOCAL_LIBRARY &&
        source.selected && source.enabled && !source.muted && (!kids() || source.childApproved)

    suspend fun confirm(id: String, reviewToken: String): ReferenceImportJobEntity = withContext(Dispatchers.IO) {
        initialize(); val check = adult(); roomBudget()
        val queued = EverydayWriteAuthority.run(check) { database.withTransaction {
            val job = requireNotNull(dao.job(id)); require(job.reviewToken == reviewToken && job.state == ReferenceImportState.REVIEWING.name)
            val existing = sources.source(job.sourceId)
            if (existing != null) require(existing.version == job.sourceVersion && existing.kind == CollectorKind.LOCAL_LIBRARY)
            val language = JSONArray(job.previewJson).getJSONObject(0).optString("language", "und")
            val source = existing ?: sources.save(CollectorSource(job.sourceId, job.title, "Imported reference library", CollectorKind.LOCAL_LIBRARY,
                SourceCategory.CUSTOM, language, selected = true, childApproved = false), null)
            if (dao.library(source.id) == null) dao.putLibrary(ReferenceLibraryEntity(source.id, null, job.title, language, null, null, 0))
            job.copy(sourceVersion = source.version, state = ReferenceImportState.QUEUED.name, updatedAt = System.currentTimeMillis()).also { check(); dao.putJob(it) }
        } }
        launchImport(queued, check); queued
    }

    suspend fun resume(id: String, reviewToken: String, expectedSourceVersion: Long?, replacementUri: String? = null): ReferenceImportJobEntity = withContext(Dispatchers.IO) {
        initialize(); val check = adult(); roomBudget()
        running[id]?.join()
        EverydayWriteAuthority.run(check) {
        val job = requireNotNull(dao.job(id)); require(job.reviewToken == reviewToken && job.state in setOf("INTERRUPTED", "FAILED", "CANCELLED"))
        val source = sources.source(job.sourceId)
        if (job.sourceVersion == null) require(source == null && expectedSourceVersion == null) { "Source changed; select a new file to review" }
        else require(source != null && source.kind == CollectorKind.LOCAL_LIBRARY && source.version == expectedSourceVersion) { "Source removed or changed; select a new file to import" }
        val input = replacementUri ?: job.uri
        require(fingerprint(input, check).first == job.fingerprint) { "Select the same original file; a different file needs a new preview" }
        if (source == null) {
            val review = job.copy(uri = input, state = "REVIEWING", processed = 0, message = null, updatedAt = System.currentTimeMillis())
            database.withTransaction { check(); require(dao.job(id) == job && sources.source(job.sourceId) == null); dao.putJob(review) }
            return@run review // A cancelled preview needs a new explicit confirmation, never automatic import.
        }
        val checkpointIntact = dao.count(job.sourceId, job.generation) == job.processed
        val queued = job.copy(uri = input, sourceVersion = source.version, state = "QUEUED", processed = if (checkpointIntact) job.processed else 0, message = null, updatedAt = System.currentTimeMillis())
        database.withTransaction {
            check(); require(dao.job(id) == job && sources.source(source.id) == source)
            if (!checkpointIntact) dao.removeGeneration(job.sourceId, job.generation)
            dao.putJob(queued)
        }
        launchImport(queued, check); queued
        }
    }
    suspend fun cancel(id: String, reviewToken: String) = withContext(Dispatchers.IO) {
        val check = adult(); val job = requireNotNull(dao.job(id)); require(job.reviewToken == reviewToken && job.state != "READY")
        running[id]?.cancelAndJoin()
        EverydayWriteAuthority.run(check) { database.withTransaction {
            val current = requireNotNull(dao.job(id)); check(); require(current.state != "READY")
            dao.putJob(current.copy(state = "CANCELLED", processed = 0, message = "Import cancelled", updatedAt = System.currentTimeMillis()))
            dao.removeGeneration(current.sourceId, current.generation)
        } }
    }
    private fun launchImport(job: ReferenceImportJobEntity, guard: () -> Unit) {
        val worker = scope.launch(Dispatchers.IO, start = CoroutineStart.LAZY) {
            importer.withLock { import(job.id, guard) }
        }
        if (running.putIfAbsent(job.id, worker) != null) { worker.cancel(); error("This import is already active") }
        worker.invokeOnCompletion { running.remove(job.id, worker) }; worker.start()
    }
    private suspend fun import(id: String, guard: () -> Unit) {
        var job = requireNotNull(dao.job(id)); if (job.state != "QUEUED") return
        val context = currentCoroutineContext()
        val check = { guard(); EverydayWriteAuthority.requireCurrent(); context.ensureActive(); require(dao.jobNow(id)?.state !in setOf("CANCELLED", "READY")) }
        try {
            EverydayWriteAuthority.run(guard) {
            check(); roomBudget()
            require(fingerprint(job.uri, check).first == job.fingerprint) { "The selected file changed; select the original file or start a new preview" }
            job = job.copy(state = "IMPORTING", message = null, updatedAt = System.currentTimeMillis()); dao.putJob(job)
            val languages = mutableSetOf<String>()
            var ordinal = 0; var pendingBytes = 0L; val pending = mutableListOf<ReferenceEntryEntity>()
            fun flush() {
                if (pending.isEmpty()) return
                check(); roomBudget(pendingBytes * 6 + 1024 * 1024)
                database.runInTransaction {
                    check(); require(dao.sourceVersionNow(job.sourceId) == job.sourceVersion) { "Source selection or version changed; review before resuming" }
                    dao.insertEntries(pending)
                    roomBudget()
                    job = job.copy(processed = ordinal, updatedAt = System.currentTimeMillis()); dao.putJobNow(job)
                }
                pending.clear(); pendingBytes = 0
            }
            openInput(job.uri).use { input -> ReferenceImportParser.parse(job.fileName, input, { check() }) { value ->
                check(); languages.add(value.language); ordinal++
                if (ordinal > job.processed) {
                    pending.add(ReferenceEntryEntity(sourceId = job.sourceId, generation = job.generation, ordinal = ordinal,
                        entryId = value.id, title = value.title, text = value.text, language = value.language, url = value.url,
                        publisher = value.publisher, revision = value.revision, license = value.license, updatedAt = value.updatedAt, format = value.format.name, imagesJson = SourceImages.encode(value.images).toString()))
                    pendingBytes += value.text.toByteArray(Charsets.UTF_8).size + value.title.toByteArray(Charsets.UTF_8).size
                    if (pending.size >= 16) flush()
                }
            } }
            flush(); require(ordinal > 0)
            job = job.copy(state = "INDEXING", updatedAt = System.currentTimeMillis()); check(); dao.putJob(job)
            EverydayWriteAuthority.run(guard) { database.withTransaction {
                check(); val source = requireNotNull(sources.source(job.sourceId)); require(source.version == job.sourceVersion)
                require(dao.count(job.sourceId, job.generation) == ordinal) { "Duplicate entry IDs or incomplete import" }
                val duplicate = dao.byFingerprint(job.fingerprint); require(duplicate == null || duplicate.sourceId == job.sourceId) { "This exact library is already installed" }
                val prior = requireNotNull(dao.library(job.sourceId)); val language = languages.singleOrNull() ?: "mul"
                val published = sources.save(source.copy(title = job.title, language = language), source.version)
                EverydayReadAuthority.invalidate(); guard()
                dao.putLibrary(prior.copy(generation = job.generation, title = job.title, fingerprint = job.fingerprint, importedAt = System.currentTimeMillis(), entryCount = ordinal, language = language, origin = job.origin))
                job = job.copy(sourceVersion = published.version, state = "READY", message = null, updatedAt = System.currentTimeMillis()); dao.putJob(job)
            } }
            // Old generations are no longer readable after the atomic source/generation change.
            runCatching { dao.removeOtherGenerations(job.sourceId, job.generation) }
            runCatching { releaseInput(job.uri) }
            }
        } catch (error: Exception) {
            withContext(NonCancellable + Dispatchers.IO) {
                val current = dao.job(id) ?: return@withContext
                if (current.state != "READY") {
                    val cancelled = current.state == "CANCELLED"
                    val interrupted = error is CancellationException || error is IllegalStateException
                    val state = if (cancelled) "CANCELLED" else if (interrupted) "INTERRUPTED" else "FAILED"
                    if (state != "INTERRUPTED") dao.removeGeneration(job.sourceId, job.generation)
                    dao.putJob(current.copy(state = state, processed = if (state == "INTERRUPTED") current.processed else 0,
                        message = if (error is CancellationException) "Import interrupted; review and resume" else if (error is SecurityException || error is java.io.FileNotFoundException) "Document access unavailable; reselect the same file" else error.message?.take(300) ?: "Import failed; select a supported file", updatedAt = System.currentTimeMillis()))
                }
            }
            if (error is CancellationException) throw error
        }
    }

    suspend fun query(query: String, sourceId: String? = null, offset: Int = 0, expectedSnapshot: String? = null): ReferenceSearchPage = withContext(Dispatchers.IO) {
        initialize(); val check = authority(); check(); val epoch = EverydayReadAuthority.capture()
        require(query.isNotBlank() && query.length <= 400 && offset in 0..10000)
        val terms = query.lowercase(Locale.ROOT).split(Regex("[^\\p{L}\\p{N}]+" )).filter { it.isNotBlank() }.distinct().take(12)
        require(terms.isNotEmpty()) { "Enter words to search" }
        val allowed = sources.states().map { it.source }.filter { visible(it) && (sourceId == null || it.id == sourceId) }.associateBy { it.id }
        val libraries = dao.libraries().filter { it.sourceId in allowed && it.generation != null }
        val identity = "$processIdentity|${policyRevision()}|$epoch|$query|$sourceId|${libraries.map { Triple(it.sourceId, allowed.getValue(it.sourceId).version, it.generation) }}"
        val snapshot = MessageDigest.getInstance("SHA-256").digest(identity.toByteArray()).joinToString("") { "%02x".format(it) }
        require(expectedSnapshot == null && offset == 0 || expectedSnapshot == snapshot) { "Search scope changed; start again" }
        val rows = if (libraries.isEmpty()) emptyList() else dao.search(terms.joinToString(" OR ") { "\"$it\"" }, query.replace("%", "").replace("_", "") + "%", libraries.map { it.sourceId }, offset, ReferenceLimits.PAGE_SIZE + 1)
        check(); EverydayReadAuthority.requireCurrent(epoch)
        for ((id, source) in allowed) require(sources.source(id) == source) { "Source approval or selection changed" }
        check(); EverydayReadAuthority.requireCurrent(epoch)
        ReferenceSearchPage(rows.take(ReferenceLimits.PAGE_SIZE), allowed, snapshot, if (rows.size > ReferenceLimits.PAGE_SIZE) offset + ReferenceLimits.PAGE_SIZE else null)
    }

    suspend fun entry(sourceId: String, sourceVersion: Long, generation: String, entryId: String): ReferenceEntryEntity = withContext(Dispatchers.IO) {
        val check = authority(); check(); val epoch = EverydayReadAuthority.capture()
        val source = requireNotNull(sources.source(sourceId)); require(visible(source) && source.version == sourceVersion)
        val library = requireNotNull(dao.library(sourceId)); require(library.generation == generation)
        val value = requireNotNull(dao.entry(sourceId, generation, entryId))
        check(); EverydayReadAuthority.requireCurrent(epoch)
        require(sources.source(sourceId) == source && dao.library(sourceId)?.generation == generation)
        check(); EverydayReadAuthority.requireCurrent(epoch)
        value
    }
}
