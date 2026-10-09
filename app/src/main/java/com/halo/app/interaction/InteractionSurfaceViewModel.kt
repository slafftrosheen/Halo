package com.halo.app.interaction

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.halo.runtime.cognition.interaction.InteractionResponse
import com.halo.runtime.cognition.interaction.InteractionSurface
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * RUNTIME-019 Android host ViewModel: composables observe this state instead
 * of reaching into [com.halo.app.di.ManualDependencyGraph] from render code.
 */
class InteractionSurfaceViewModel(
    private val presenter: AndroidInteractionPresenter,
    private val coordinator: com.halo.runtime.cognition.interaction.InteractionSurfaceCoordinator? = null,
) : ViewModel() {

    val state: StateFlow<InteractionPresentationState> = presenter.state

    init {
        presenter.start()
    }

    /** Route a typed response from any renderer back into Runtime. */
    fun respond(response: InteractionResponse) {
        viewModelScope.launch { presenter.respond(response) }
    }

    fun reviewRun(runId: String) {
        viewModelScope.launch {
            // Rebuild from pending journal inputs, including a previously hidden dialog.
            coordinator?.rebuild()
            presenter.showRun(com.halo.runtime.cognition.agent.AgentRunId(runId))
        }
    }

    /** Dismiss (hide) a surface; the durable request stays pending. */
    fun dismiss(surface: InteractionSurface) {
        respond(InteractionResponse.Dismissed(surfaceId = surface.id))
    }
}
