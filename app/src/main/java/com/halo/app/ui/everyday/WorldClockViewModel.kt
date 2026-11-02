package com.halo.app.ui.everyday

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.halo.app.di.ManualDependencyGraph as Graph
import com.halo.app.everyday.collectors.OfflineCity
import com.halo.app.everyday.organizer.WorldClockRecord
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

data class WorldClockPageState(
    val clocks: List<WorldClockRecord> = emptyList(), val loading: Boolean = true,
    val busy: Boolean = false, val error: String? = null, val readFailed: Boolean = false,
    val undoId: String? = null, val undoVersion: Long? = null,
)

/** Page-scoped jobs survive rotation; saved undo authority is a record ID and exact deletion version. */
class WorldClockViewModel(private val saved: SavedStateHandle) : ViewModel() {
    private val mutable = MutableStateFlow(WorldClockPageState(undoId = saved["clock.undoId"], undoVersion = saved["clock.undoVersion"]))
    val state = mutable.asStateFlow()
    private var observing: Job? = null
    init { retry() }
    fun retry() {
        observing?.cancel()
        mutable.update { it.copy(loading = true, readFailed = false, error = null) }
        observing = viewModelScope.launch {
            try { Graph.worldClocks.observe().collect { rows ->
                mutable.update { it.copy(clocks = rows, loading = false, readFailed = false) }
            } } catch (cancelled: CancellationException) { throw cancelled }
              catch (_: Exception) { mutable.update { it.copy(clocks = emptyList(), loading = false, readFailed = true,
                  error = "Saved clocks could not be opened. Your data is kept; try again.") } }
        }
    }
    private fun change(action: suspend () -> Unit) {
        if (state.value.busy) return
        mutable.update { it.copy(busy = true, error = null) }
        viewModelScope.launch {
            try { check(!Graph.kidsPreferences.getEnabled())
                val revision = Graph.kidsPreferences.adultActionRevision()
                com.halo.app.everyday.EverydayWriteAuthority.run({ Graph.kidsPreferences.requireAdultAction(revision) }, action)
            }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { mutable.update { it.copy(error = "The clock changed or could not be saved. Reopen it and try again.") } }
            finally { mutable.update { it.copy(busy = false) } }
        }
    }
    fun add(city: OfflineCity, done: () -> Unit) = change {
        Graph.worldClocks.save(WorldClockRecord(java.util.UUID.randomUUID().toString(),
            "${city.name} · ${city.countryCode}", city.zoneId, city.id), null)
        done()
    }
    fun rename(id: String, version: Long, label: String, done: () -> Unit) = change {
        val row = Graph.worldClocks.get(id) ?: error("Missing clock")
        Graph.worldClocks.save(row.copy(label = label.trim()), version)
        done()
    }
    fun remove(row: WorldClockRecord) = change {
        val removed = Graph.worldClocks.remove(row.id, row.version)
        saved["clock.undoId"] = removed.id; saved["clock.undoVersion"] = removed.version
        mutable.update { it.copy(undoId = removed.id, undoVersion = removed.version) }
    }
    fun undo() = change {
        val id = state.value.undoId ?: return@change
        val version = state.value.undoVersion ?: return@change
        Graph.worldClocks.restore(id, version)
        clearUndo()
    }
    fun clearUndo() {
        saved.remove<String>("clock.undoId"); saved.remove<Long>("clock.undoVersion")
        mutable.update { it.copy(undoId = null, undoVersion = null) }
    }
    fun move(id: String, delta: Int) {
        val rows = state.value.clocks
        val index = rows.indexOfFirst { it.id == id }
        if (index < 0 || index + delta !in rows.indices) return
        change {
            val ids = rows.map { it.id }.toMutableList()
            java.util.Collections.swap(ids, index, index + delta)
            Graph.worldClocks.reorder(ids, rows.associate { it.id to it.version })
        }
    }
}
