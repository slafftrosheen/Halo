package com.halo.app.ui.screens.integrations

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.halo.app.mcp.*
import com.halo.app.data.mcp.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

class McpServersViewModel(val service: McpSettingsService) : ViewModel() {
    val servers = service.servers.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val query = MutableStateFlow("")
    val visible = combine(servers, query) { servers, query -> servers.filter { server ->
        server.displayName.contains(query, true) || server.tools.any { it.displayName.contains(query, true) || it.description.contains(query, true) }
    } }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val operation = MutableStateFlow<String?>(null)
    val error = MutableStateFlow<String?>(null)
    val saved = MutableStateFlow<String?>(null)
    fun add(value: McpServerConfiguration, token: String?) {
        if (operation.value != null) return
        operation.value = "Saving"; error.value = null
        viewModelScope.launch(Dispatchers.IO) {
            try { saved.value = service.save(null, value, token) }
            catch (e: CancellationException) { throw e }
            catch (e: IllegalArgumentException) { error.value = e.message }
            catch (_: Exception) { error.value = "Could not save the integration" }
            finally { operation.value = null }
        }
    }
    class Factory(private val service: McpSettingsService) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST") override fun <T : ViewModel> create(modelClass: Class<T>): T = McpServersViewModel(service) as T
    }
}
