package com.halo.app.assistant

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.Build
import android.os.Bundle
import android.service.voice.VoiceInteractionSession
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import androidx.compose.ui.platform.ComposeView
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.lifecycle.ViewModelStore
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.halo.host.android.assistant.ScreenContextExtractor
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ViewModelStoreOwner
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistry
import com.halo.app.assistant.ui.AssistantSurface
import com.halo.app.ui.theme.HaloTheme
import com.halo.app.di.ManualDependencyGraph
import com.halo.sdk.runtime.InteractionTurnState
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import com.halo.sdk.runtime.HostInvocation
import com.halo.sdk.runtime.InteractionTurnChanged
import com.halo.sdk.runtime.InvocationSource
import java.util.UUID
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class HaloVoiceInteractionSession(
    context: Context,
    private val runtimeBridge: AssistantRuntimeBridge,
    private val contextExtractor: ScreenContextExtractor = ScreenContextExtractor(),
    private val applicationScope: CoroutineScope,
) : VoiceInteractionSession(context), LifecycleOwner, ViewModelStoreOwner, SavedStateRegistryOwner {
    
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var active: ActiveInvocation? = null
    private var interactionObserverJob: Job? = null

    private var interactionState by mutableStateOf<InteractionTurnState?>(null)
    private var screenContextAttached by mutableStateOf(false)
    private var requestedScreenContext by mutableStateOf(false)

    private val lifecycleRegistry = LifecycleRegistry(this)
    private val store = ViewModelStore()
    private val savedStateRegistryController = SavedStateRegistryController.create(this)
    
    init {
        savedStateRegistryController.performRestore(null)
        lifecycleRegistry.currentState = Lifecycle.State.CREATED
    }

    private data class ActiveInvocation(
        val invocation: HostInvocation,
        val ready: CompletableDeferred<Unit> = CompletableDeferred(),
        var screenshotAttached: Boolean = false,
    )

    override fun onCreateContentView(): View = ComposeView(context).apply {
        setViewTreeLifecycleOwner(this@HaloVoiceInteractionSession)
        setViewTreeViewModelStoreOwner(this@HaloVoiceInteractionSession)
        setViewTreeSavedStateRegistryOwner(this@HaloVoiceInteractionSession)

        setContent {
            HaloTheme {
                /*
                 * RUNTIME-019: the overlay observes the shared interaction
                 * projection and can render the active input surface inline;
                 * the same surface stays pending if the overlay is hidden.
                 */
                val interactionViewModel: com.halo.app.interaction.InteractionSurfaceViewModel =
                    androidx.lifecycle.viewmodel.compose.viewModel(
                        factory = com.halo.app.interaction.InteractionSurfaceViewModelFactory(),
                    )
                val interactionState2 by interactionViewModel.state.collectAsState()
                AssistantSurface(
                    interactionState = interactionState,
                    screenContextAttached = screenContextAttached,
                    interactionSurface = interactionState2.primary
                        ?.takeIf { it.presentation.requiresForegroundAttention.not() },
                    onInteractionRespond = interactionViewModel::respond,
                    onStopClicked = {
                        val stopped = active
                        active = null
                        if (stopped != null) {
                            scope.launch {
                                runtimeBridge.cancelAssistantInteraction(stopped.invocation.interactionId, "user_stop")
                            }
                        }
                        hide()
                    },
                    onOpenHaloClicked = {
                        context.packageManager.getLaunchIntentForPackage(context.packageName)?.let { launchIntent ->
                            context.startActivity(launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                        }
                        hide()
                    }
                )
            }
        }
    }

    override fun onShow(args: Bundle?, showFlags: Int) {
        super.onShow(args, showFlags)
        lifecycleRegistry.currentState = Lifecycle.State.RESUMED
        active?.let { previous ->
            scope.launch {
                runtimeBridge.cancelAssistantInteraction(previous.invocation.interactionId, "superseded")
            }
        }
        val inputDeviceId = args?.getInt(Intent.EXTRA_ASSIST_INPUT_DEVICE_ID, -1)?.takeIf { it >= 0 }
        val invocation = HostInvocation(
            interactionId = UUID.randomUUID().toString(),
            source = InvocationSource.ANDROID_ASSISTANT,
            invokedAtMs = System.currentTimeMillis(),
            inputDeviceId = inputDeviceId,
            requestedAssistContext = showFlags and SHOW_WITH_ASSIST != 0,
            requestedScreenshot = showFlags and SHOW_WITH_SCREENSHOT != 0,
        )
        val current = ActiveInvocation(invocation)
        active = current
        
        interactionState = InteractionTurnState.Listening(invocation.interactionId, null)
        requestedScreenContext = invocation.requestedAssistContext
        screenContextAttached = false
        
        // Listen to state changes for this interaction
        interactionObserverJob?.cancel()
        interactionObserverJob = ManualDependencyGraph.eventBus.observe(InteractionTurnChanged::class.java)
            .onEach { event ->
                if (event.state.interactionId == current.invocation.interactionId) {
                    interactionState = event.state
                }
            }
            .launchIn(scope)

        scope.launch {
            try {
                runtimeBridge.begin(invocation)
                current.ready.complete(Unit)
                if (active === current) {
                    val listening = runtimeBridge.startListening(invocation.interactionId, inputDeviceId)
                    if (!listening) {
                        interactionState = InteractionTurnState.Failed(invocation.interactionId, "Microphone unavailable")
                    }
                }
            } catch (error: Throwable) {
                current.ready.completeExceptionally(error)
                if (active === current) {
                    interactionState = InteractionTurnState.Failed(invocation.interactionId, "Halo could not start")
                }
            }
        }
    }

    override fun onHandleAssist(state: AssistState) {
        super.onHandleAssist(state)
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return
        val current = active ?: return
        val snapshot = contextExtractor.extract(state.assistStructure, state.assistContent)
        scope.launch {
            runCatching { current.ready.await(); runtimeBridge.attachScreen(current.invocation.interactionId, snapshot) }
                .onSuccess {
                    if (active === current) {
                        screenContextAttached = true
                    }
                }
                .onFailure {
                    // Do nothing on failure to keep the UI state clean
                }
        }
    }

    override fun onHandleScreenshot(screenshot: Bitmap?) {
        super.onHandleScreenshot(screenshot)
        val bitmap = screenshot?.copy(screenshot.config ?: Bitmap.Config.ARGB_8888, false) ?: return
        val current = active ?: return
        if (current.screenshotAttached) return
        scope.launch {
            runCatching { current.ready.await(); runtimeBridge.attachScreenshot(current.invocation.interactionId, bitmap) }
                .onSuccess { current.screenshotAttached = true }
            bitmap.recycle()
        }
    }

    override fun onHide() {
        interactionObserverJob?.cancel()
        interactionObserverJob = null
        val ending = active
        active = null
        if (ending != null) scope.launch { runtimeBridge.hideAssistantSurface(ending.invocation.interactionId) }
        interactionState = InteractionTurnState.Idle
        lifecycleRegistry.currentState = Lifecycle.State.CREATED
        super.onHide()
    }

    override fun onDestroy() {
        interactionObserverJob?.cancel()
        interactionObserverJob = null
        active?.let { ending ->
            applicationScope.launch {
                runtimeBridge.hideAssistantSurface(ending.invocation.interactionId)
            }
        }
        active = null
        lifecycleRegistry.currentState = Lifecycle.State.DESTROYED
        store.clear()
        scope.cancel()
        super.onDestroy()
    }

    override val lifecycle: Lifecycle get() = lifecycleRegistry
    override val viewModelStore: ViewModelStore get() = store
    override val savedStateRegistry: SavedStateRegistry get() = savedStateRegistryController.savedStateRegistry
}
