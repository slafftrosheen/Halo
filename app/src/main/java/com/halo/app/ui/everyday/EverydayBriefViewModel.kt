package com.halo.app.ui.everyday

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.halo.app.HaloApplication
import com.halo.app.di.ManualDependencyGraph as Graph
import com.halo.app.everyday.briefing.*
import com.halo.app.everyday.organizer.OrganizerRecord
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

data class BriefPageState(val loading: Boolean = true, val busy: Boolean = false, val document: EverydayBrief? = null,
    val history: List<EverydayBrief> = emptyList(), val settings: BriefSettings? = null,
    val records: List<OrganizerRecord> = emptyList(), val error: String? = null, val notice: String? = null,
    val missing: Boolean = false)

/** One page controller over the shared briefing owner. Opening it never refreshes external sources. */
class EverydayBriefViewModel(private val application: HaloApplication, initialId: String?, private val saved: androidx.lifecycle.SavedStateHandle) : ViewModel() {
    private val mutable = MutableStateFlow(BriefPageState())
    val state = mutable.asStateFlow()
    private var observing: Job? = null
    private var working: Job? = null
    private var requestedId: String? = saved["brief.requestedId"] ?: initialId
    init { load() }
    private fun load() {
        if (state.value.busy || working?.isActive == true) return
        mutable.value = state.value.copy(loading = true, busy = true, error = null)
        working = viewModelScope.launch {
        try {
            application.awaitOrganizerBootstrap()
            Graph.kidsPreferences.getEnabled()
            Graph.collectorBootstrap.ensureInitialized()
            val id = requestedId ?: Graph.briefRepository.recent(60).firstOrNull {
                it.date == java.time.LocalDate.now() && it.zoneId == java.time.ZoneId.systemDefault().id &&
                    (!Graph.kidsPreferences.enabled.value || it.audience == BriefAudience.KIDS)
            }?.id ?: Graph.briefService.build(BriefPeriod.ON_DEMAND).id
            watch(id)
            refreshAuxiliary()
        } catch (cancelled: CancellationException) { throw cancelled }
          catch (_: Exception) { mutable.value = state.value.copy(loading = false, error = "The brief could not be opened. Try again.") }
          finally { mutable.value = state.value.copy(busy = false) }
        }
    }
    private fun watch(id: String) {
        requestedId = id; saved["brief.requestedId"] = id
        observing?.cancel()
        mutable.value = state.value.copy(loading = true, document = null, missing = false, error = null)
        observing = viewModelScope.launch {
            try { Graph.briefService.observeVisible(id).collect { document ->
                mutable.value = state.value.copy(loading = false, document = document, missing = document == null)
            } } catch (cancelled: CancellationException) { throw cancelled }
              catch (_: Exception) { mutable.value = state.value.copy(loading = false, document = null, error = "The brief could not be opened. Try again.") }
        }
    }
    fun open(id: String) { if (!state.value.busy) watch(id) }
    private fun work(block: suspend () -> Unit) {
        if (state.value.busy || working?.isActive == true) return
        mutable.value = state.value.copy(busy = true, notice = null, error = null)
        working = viewModelScope.launch {
            try {
                block()
                try { refreshAuxiliary() } catch (cancelled: CancellationException) { throw cancelled }
                catch (_: Exception) { mutable.value = state.value.copy(error = "The action finished, but the updated view could not be loaded. Retry to view the saved result.") }
            }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { mutable.value = state.value.copy(error = "This change could not be saved. Your saved data is kept; reopen and retry.") }
            finally { mutable.value = state.value.copy(busy = false, loading = false) }
        }
    }
    private suspend fun refreshAuxiliary() {
        val family = Graph.kidsPreferences.getEnabled()
        val history = Graph.briefRepository.recent(60).filter { !family || it.audience == BriefAudience.KIDS }
        val settings = Graph.briefRepository.settings()
        val records = Graph.organizerRepository.records().filter { !family || it.id in settings.childRecordIds }
        mutable.value = state.value.copy(history = history, settings = settings, records = records)
    }
    fun build(period: BriefPeriod, study: Boolean = false) = work {
        val document = Graph.briefService.build(period, if (study) BriefAudience.STUDY else BriefAudience.EVERYDAY)
        watch(document.id)
    }
    fun retry() = load()
    fun read() = work { state.value.document?.let { Graph.briefReadAloud.read(it.id) } }
    fun stopReading() = Graph.briefReadAloud.stop()
    fun openFact(fact: BriefFact, navigate: (String) -> Unit) {
        val id = state.value.document?.id ?: return
        viewModelScope.launch {
            try { navigate(EverydayRoutes.briefFact(id, fact.id)) }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { mutable.value = state.value.copy(error = "This linked item changed or is no longer available. Update the brief to see current information.") }
        }
    }
    fun saveSettings(value: BriefSettings) = work {
        check(!Graph.kidsPreferences.getEnabled()) { "Adult settings required" }
        val revision = Graph.kidsPreferences.adultActionRevision()
        com.halo.app.everyday.EverydayWriteAuthority.run({ Graph.kidsPreferences.requireAdultAction(revision) }) {
            Graph.briefRepository.saveSettings(value, value.version)
        }
        mutable.value = state.value.copy(notice = "Brief settings saved.")
    }
    fun dismissNotice() { mutable.value = state.value.copy(notice = null) }
    fun reloadSettings() = work { refreshAuxiliary() }
    override fun onCleared() { observing?.cancel(); super.onCleared() }
}
