package com.halo.app.interaction

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.halo.app.di.ManualDependencyGraph

/**
 * RUNTIME-019 factory keeping ManualDependencyGraph access out of composables:
 * render code receives [InteractionSurfaceViewModel] via the standard
 * viewModel() helper and never touches the graph directly.
 */
class InteractionSurfaceViewModelFactory : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        require(modelClass.isAssignableFrom(InteractionSurfaceViewModel::class.java)) {
            "Unknown ViewModel class: ${modelClass.name}"
        }
        val graph = ManualDependencyGraph
        val presenter = AndroidInteractionPresenter(
            surfaceStore = graph.interactionSurfaceStore,
            responseHandler = graph.interactionResponseHandler,
            voiceAdapter = com.halo.app.interaction.VoiceInteractionSurfaceAdapter(
                phoneFallbackEnabled = { com.halo.app.di.ManualDependencyGraph.voiceExperienceSettings.state.value.wearablePhoneFallback },
                publishTts = { event -> graph.eventBus.publish(event) },
            ),
            originLookup = { runId -> graph.agentRunStore.get(runId)?.speechOutputTarget },
        )
        return InteractionSurfaceViewModel(presenter, graph.interactionSurfaceCoordinator) as T
    }
}
