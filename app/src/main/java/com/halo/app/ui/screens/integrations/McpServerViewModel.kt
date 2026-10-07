package com.halo.app.ui.screens.integrations

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.halo.app.mcp.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

class McpServerViewModel(val id: String, val service: McpSettingsService) : ViewModel() {
    val server = service.servers.map { it.firstOrNull { server -> server.id == id } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
    val configuration = MutableStateFlow<McpServerConfiguration?>(null)
    val operation = MutableStateFlow<String?>(null)
    val message = MutableStateFlow<String?>(null)
    val removed = MutableStateFlow(false)
    private var job: Job? = null
    private fun run(label: String, action: suspend () -> Unit) {
        if (operation.value != null) return
        operation.value = label; message.value = null
        job = viewModelScope.launch(Dispatchers.IO) {
            try { action() }
            catch (e: CancellationException) { throw e }
            catch (e: IllegalArgumentException) { message.value = e.message }
            catch (_: Exception) { message.value = "Could not complete this configuration action" }
            finally { operation.value = null }
        }
    }
    fun edit() = run("Loading configuration") { configuration.value = service.configuration(id) }
    fun save(value: McpServerConfiguration, token: String?, removeCredential: Boolean) = run("Saving") {
        service.save(id, value, token, removeCredential); configuration.value = null; message.value = "Saved · reconnect explicitly"
    }
    fun enable(enabled: Boolean) = run("Saving") { service.setEnabled(id, enabled) }
    fun connect() = run("Connecting") { service.connect(id) }
    fun refresh() = run("Refreshing tools") { service.refreshTools(id) }
    fun tool(id: String, enabled: Boolean) = run("Saving tool") { service.setToolEnabled(id, enabled) }
    fun removeCredential() = run("Removing credential") {
        val value = service.configuration(id) ?: return@run
        service.save(id, value, removeCredential = true)
    }
    fun remove() = run("Removing") { service.remove(id); removed.value = true }
    fun cancelDiscovery() { if (operation.value in setOf("Connecting", "Refreshing tools")) job?.cancel() }
    class Factory(private val id: String, private val service: McpSettingsService) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST") override fun <T : ViewModel> create(modelClass: Class<T>): T = McpServerViewModel(id, service) as T
    }
}
