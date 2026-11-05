package com.halo.app.ui.everyday

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.room.withTransaction
import com.halo.app.HaloApplication
import com.halo.app.data.everyday.CollectorCodec
import com.halo.app.di.ManualDependencyGraph as Graph
import com.halo.app.everyday.collectors.*
import com.halo.app.everyday.organizer.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.time.*

data class SourcesPageState(val loading: Boolean = true, val busy: Boolean = false, val kids: Boolean = true,
    val sources: List<CollectorState> = emptyList(), val error: String? = null, val notice: String? = null,
    val preview: SourcePreview? = null, val draft: CollectorSource? = null, val draftNew: Boolean = false,
    val manualReceipt: CollectorState? = null, val addedSourceId: String? = null)

class SourcesViewModel(private val app: HaloApplication, private val saved: SavedStateHandle) : ViewModel() {
    private val mutable = MutableStateFlow(SourcesPageState(draft = saved.get<String>("sources.draft")?.let {
        runCatching { CollectorCodec.source(it) }.getOrNull() }, draftNew = saved["sources.draftNew"] ?: false, addedSourceId = saved["sources.addedId"]))
    val state = mutable.asStateFlow()
    private var observing: Job? = null
    private var working: Job? = null
    init { retry() }
    fun retry() {
        observing?.cancel()
        mutable.update { it.copy(loading = true, error = null) }
        observing = viewModelScope.launch {
            try {
                app.awaitOrganizerBootstrap(); Graph.kidsPreferences.getEnabled(); Graph.collectorBootstrap.ensureInitialized()
                combine(Graph.collectorRepository.observe(), Graph.kidsPreferences.enabled) { rows, kids ->
                    rows.filter { !kids || childSource(it.source) } to kids
                }.collect { (rows, kids) ->
                    mutable.update { old -> old.copy(sources = rows, kids = kids, loading = false, draft = old.draft.takeUnless { kids }, preview = old.preview.takeUnless { kids },
                        manualReceipt = old.manualReceipt?.takeIf { receipt -> !kids && rows.find { it.source.id == receipt.source.id } == old.sources.find { it.source.id == receipt.source.id } }) }
                    if (kids) { saved.remove<String>("sources.draft"); saved.remove<Boolean>("sources.draftNew") }
                }
            } catch (cancelled: CancellationException) { throw cancelled }
              catch (_: Exception) { mutable.update { it.copy(loading = false, sources = emptyList(), error = "Sources could not be opened. Your saved choices are kept; try again.") } }
        }
    }
    private fun work(action: suspend () -> Unit) {
        if (state.value.busy) return
        mutable.update { it.copy(busy = true, error = null, notice = null) }
        working = viewModelScope.launch {
            try { check(!Graph.kidsPreferences.getEnabled())
                val revision = Graph.kidsPreferences.adultActionRevision()
                com.halo.app.everyday.EverydayWriteAuthority.run({ Graph.kidsPreferences.requireAdultAction(revision) }, action)
            }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: ArticleReviewChanged) { mutable.update { it.copy(error = "This article or its source changed. Cancel this form and reopen the saved article to review it before creating a reminder.") } }
            catch (_: CollectorConflict) { mutable.update { it.copy(error = "This source changed. Reopen its settings before saving again.") } }
            catch (_: com.halo.sdk.provider.RemoteDispatchBlocked) { mutable.update { it.copy(error = "Network preview needs Power and permitted connected services. You can still save the source locally.") } }
            catch (_: IllegalArgumentException) { mutable.update { it.copy(error = "Check the source name, public HTTPS address, language and refresh interval.") } }
            catch (_: Exception) { mutable.update { it.copy(error = "The source action could not finish. Your last saved information is kept.") } }
            finally { mutable.update { it.copy(busy = false) } }
        }
    }
    fun edit(source: CollectorSource?, creating: Boolean = false) {
        if (state.value.busy || state.value.kids) return
        saved["sources.draft"] = source?.let(CollectorCodec::encode); saved["sources.draftNew"] = creating
        mutable.update { it.copy(draft = source, draftNew = creating, error = null, preview = null) }
    }
    fun select(source: CollectorSource, selected: Boolean) = work { Graph.collectorRepository.save(source.copy(selected = selected), source.version) }
    fun save(source: CollectorSource, creating: Boolean) = work {
        Graph.collectorRepository.save(source, if (creating) null else source.version)
        saved.remove<String>("sources.draft"); saved.remove<Boolean>("sources.draftNew")
        mutable.update { it.copy(draft = null, preview = null, notice = "Source settings saved. Selection does not fetch in Local.") }
    }
    fun refresh(source: CollectorSource) = work {
        val current = Graph.collectorRepository.state(source.id)?.source ?: throw CollectorConflict("Missing source")
        if (current.version != source.version) throw CollectorConflict("Changed source")
        val receipt = Graph.collectorCoordinator.refreshDetailed(source.id, manual = true, expectedVersion = source.version)
        mutable.update { it.copy(manualReceipt = receipt.state, notice = if (receipt.admitted && receipt.state?.attempt?.state in setOf(CollectorAttemptState.CURRENT, CollectorAttemptState.NOT_MODIFIED))
            "Source updated. Retrieval and verification times are shown below." else "Refresh did not replace the last saved information. Check the source status below.") }
    }
    fun cancelRefresh(sourceId: String) { Graph.collectorCoordinator.cancel(sourceId) }
    fun openLocal(fact: LocalFact, navigate: (String) -> Unit) {
        viewModelScope.launch {
            try { navigate(EverydayRoutes.local(fact)) }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { mutable.update { it.copy(error = "This linked item is no longer available. Refresh the local source to update its list.") } }
        }
    }
    fun remove(source: CollectorSource) = work {
        Graph.collectorRepository.remove(source.id, source.version)
        mutable.update { it.copy(notice = "Source and its local snapshot removed. Organizer items and notes are kept.") }
    }
    fun clear(source: CollectorSource) = work {
        Graph.collectorRepository.clearSnapshot(source.id, source.version)
        mutable.update { it.copy(notice = "Cached source information cleared. Source settings are kept.") }
    }
    fun preview(url: String, type: CollectorKind, language: String) = work {
        val preview = Graph.collectorPreview.inspect(url, type, language)
        mutable.update { it.copy(preview = preview) }
    }
    fun addUrl(draftId: String, url: String, title: String, type: CollectorKind, language: String, selected: Boolean) = work {
        require(draftId.matches(Regex("custom:[a-f0-9-]{36}")))
        val endpoint = CollectorUrlPolicy.validate(url)
        Graph.collectorRepository.saveDraft(CollectorSource(draftId, title.trim(), endpoint.host, type,
            if (type == CollectorKind.PUBLIC_CALENDAR) SourceCategory.CALENDAR else SourceCategory.CUSTOM, language,
            endpoint.toString(), endpoint.newBuilder().encodedPath("/").query(null).fragment(null).build().toString(), selected = selected))
        saved["sources.addedId"] = draftId
        mutable.update { it.copy(preview = null, addedSourceId = draftId, notice = "Source added. A successful refresh is required before its contents are available.") }
    }
    fun remindArticle(review: ArticleReminderReview, date: LocalDate, time: LocalTime, done: (String) -> Unit) = work {
        val record = Graph.appDatabase.withTransaction {
            val article = review.requireCurrent(Graph.collectorRepository.state(review.sourceId))
            Graph.organizerUseCases.create(OrganizerKind.REMINDER, article.title.take(200),
                time = OrganizerTime(date.atTime(time), zoneId = ZoneId.systemDefault().id), sourceUrl = article.url, sourceLabel = article.publisher.take(200))
        }
        done(record.id)
    }
    fun retrieveWikipedia(sourceId: String, version: Long, contentRevision: String, articleId: String, done: (String) -> Unit) = work {
        val receipt = Graph.researchArticles.retrieve(sourceId, version, contentRevision, articleId)
        mutable.update { it.copy(notice = if (receipt.fetched) "Full article sections are saved for offline reading" else "Article source saved; check its retrieval status") }
        done(receipt.id)
    }
    companion object {
        fun childSource(source: CollectorSource) = (source.external || source.kind == CollectorKind.LOCAL_LIBRARY) && source.selected && source.enabled && !source.muted && source.childApproved
    }
}
