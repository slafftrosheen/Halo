package com.halo.app.ui.everyday

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.halo.app.data.knowledge.*
import com.halo.app.di.ManualDependencyGraph as Graph
import com.halo.app.everyday.collectors.*
import com.halo.app.everyday.knowledge.ReferenceStarterId
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

data class ReferenceLibraryRow(val source: CollectorSource, val library: ReferenceLibraryEntity)
data class ReferenceLibrariesState(val loading: Boolean = true, val busy: Boolean = false, val kids: Boolean = true, val initializationFailed: Boolean = false,
    val libraries: List<ReferenceLibraryRow> = emptyList(), val imports: List<ReferenceImportJobEntity> = emptyList(),
    val query: String = "", val results: ReferenceSearchPage? = null, val entry: ReferenceEntryEntity? = null,
    val entrySourceVersion: Long? = null, val review: ReferenceImportJobEntity? = null, val error: String? = null)

class ReferenceLibrariesViewModel : ViewModel() {
    private val repository = Graph.referenceLibraries
    private val mutable = MutableStateFlow(ReferenceLibrariesState())
    val state = mutable.asStateFlow()
    private var work: Job? = null
    private var observation: Job? = null
    init { reload() }
    fun reload() {
        observation?.cancel()
        mutable.update { it.copy(loading = true, initializationFailed = false, error = null) }
        observation = viewModelScope.launch {
            try {
            Graph.kidsPreferences.loadedState.first { it }
            Graph.collectorBootstrap.ensureInitialized(); repository.initialize()
            combine(repository.libraries, repository.jobs, Graph.collectorRepository.observe(), Graph.kidsPreferences.enabled,
                Graph.privacyModes.profileChanges) { libraries, jobs, sources, kids, _ ->
                val rows = libraries.mapNotNull { library -> sources.find { it.source.id == library.sourceId }?.source?.takeIf {
                    !kids || SourcesViewModel.childSource(it)
                }?.let { ReferenceLibraryRow(it, library) } }
                Triple(rows, if (kids) emptyList() else jobs, kids)
            }.collect { (rows, jobs, kids) ->
                mutable.update { old ->
                    val entry = old.entry?.takeIf { value -> rows.any { it.source.id == value.sourceId && it.source.version == old.entrySourceVersion &&
                        it.library.generation == value.generation && it.source.selected && it.source.enabled && !it.source.muted } }
                    val policyChanged = rows.map { it.source.id to it.source.version } != old.libraries.map { it.source.id to it.source.version } || kids != old.kids
                    old.copy(loading = false, libraries = rows, imports = jobs, kids = kids, entry = entry,
                        results = if (policyChanged) null else old.results,
                        review = if (kids) null else old.review?.let { review -> jobs.find { it.id == review.id } })
                }
            }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { mutable.update { it.copy(loading = false, initializationFailed = true, error = "Libraries could not be loaded. Retry to read local progress.") } }
        }
    }
    private fun work(action: suspend () -> Unit) {
        if (state.value.busy) return
        mutable.update { it.copy(busy = true, error = null) }
        work = viewModelScope.launch {
            try { action() }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: SecurityException) { mutable.update { it.copy(error = "Document access unavailable; reselect the same file") } }
            catch (error: Exception) { mutable.update { it.copy(error = error.message?.take(300) ?: "This library action could not finish. The previous saved library is kept.") } }
            finally { mutable.update { it.copy(busy = false) } }
        }
    }
    fun query(value: String) { work?.cancel(); mutable.update { it.copy(query = value.take(200), results = null, busy = false, error = null) } }
    fun search(next: Boolean = false) = work {
        val current = state.value
        val page = repository.query(current.query, offset = if (next) current.results?.nextOffset ?: 0 else 0,
            expectedSnapshot = if (next) current.results?.snapshot else null)
        mutable.update { it.copy(results = page, entry = null) }
    }
    fun open(entry: ReferenceEntryEntity, version: Long) = work {
        val value = repository.entry(entry.sourceId, version, entry.generation, entry.entryId)
        mutable.update { it.copy(entry = value, entrySourceVersion = version) }
    }
    fun reportError(value: String) { mutable.update { it.copy(error = value) } }
    fun openExact(sourceId: String, version: Long, generation: String, entryId: String) = work {
        Graph.kidsPreferences.loadedState.first { it }
        val value = repository.entry(sourceId, version, generation, entryId)
        mutable.update { it.copy(entry = value, entrySourceVersion = version) }
    }
    fun closeEntry() { mutable.update { it.copy(entry = null, entrySourceVersion = null) } }
    fun preview(uri: Uri, name: String, sourceId: String? = null, version: Long? = null) = work {
        val job = repository.preview(uri.toString(), name, sourceId, version)
        mutable.update { it.copy(review = job) }
    }
    fun review(job: ReferenceImportJobEntity) { mutable.update { it.copy(review = job, entry = null, error = null) } }
    fun closeReview() { mutable.update { it.copy(review = null) } }
    fun confirm(job: ReferenceImportJobEntity) = work {
        val queued = repository.confirm(job.id, job.reviewToken)
        mutable.update { it.copy(review = queued) }
    }
    fun cancel(job: ReferenceImportJobEntity) = work { repository.cancel(job.id, job.reviewToken); closeReview() }
    fun resume(job: ReferenceImportJobEntity, uri: Uri? = null) = work {
        val source = Graph.collectorRepository.source(job.sourceId)
        val queued = repository.resume(job.id, job.reviewToken, source?.version, uri?.toString())
        mutable.update { it.copy(review = queued) }
    }
    fun download(id: ReferenceStarterId) = work {
        val preview = Graph.referenceDownloads.downloadForReview(id) { file ->
            repository.preview(file.uri.toString(), file.fileName, origin = id.name)
        }
        mutable.update { it.copy(review = preview) }
    }
}
