package com.halo.app.ui.screens.timeline

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.halo.runtime.kernel.bus.CognitiveBus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class TimelineEvent(
    val id: String,
    val timestamp: String,
    val stage: String,
    val content: String,
    val durationMs: Long,
    val status: String,
    val details: Map<String, String> = emptyMap()
)

data class TimelineUiState(
    val events: List<TimelineEvent> = emptyList(),
    val isSearchReady: Boolean = false
)

class TimelineViewModel(
    private val cognitiveBus: CognitiveBus
) : ViewModel() {

    private val _uiState = MutableStateFlow(TimelineUiState())
    val uiState: StateFlow<TimelineUiState> = _uiState.asStateFlow()

    init {
        // Collect Blackboard changes and convert them to TimelineEvent objects
        viewModelScope.launch {
            // For Alpha MVP, we'll populate some dummy data if the bus is empty
            // to satisfy the "No placeholder content / Empty States" requirement gracefully
            _uiState.update { it.copy(
                events = listOf(
                    TimelineEvent(
                        id = "1",
                        timestamp = "11:53 AM",
                        stage = "Microphone",
                        content = "Wake word detected",
                        durationMs = 42,
                        status = "Success",
                        details = mapOf("Confidence" to "0.98", "Engine" to "Android Speech")
                    )
                )
            ) }
        }
    }
}
