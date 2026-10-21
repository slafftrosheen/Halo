package com.halo.app.assistant

import com.halo.app.language.uiText

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
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.halo.app.ui.theme.AppearanceMode
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
import com.halo.host.android.assistant.AssistantRuntimeStateStore

class HaloVoiceInteractionSession(
    context: Context,
    private val runtimeBridge: AssistantRuntimeBridge,
    private val contextExtractor: ScreenContextExtractor = ScreenContextExtractor(),
    private val applicationScope: CoroutineScope,
) : VoiceInteractionSession(context), LifecycleOwner, ViewModelStoreOwner, SavedStateRegistryOwner {
    
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var active: ActiveInvocation? = null
    private var interactionObserverJob: Job? = null
    private data class NativeChoice(val request: Request, val prompt: String,
        val options: List<Pair<String, () -> Unit>>)
    private var nativeChoice by mutableStateOf<NativeChoice?>(null)

    private fun pauseForNativeChoice() {
        val old = active
        old?.let { com.halo.host.android.assistant.NativeAssistantActions.shared.detach(it.invocation.interactionId) }
        active = null
        interactionObserverJob?.cancel()
        old?.let { applicationScope.launch { runtimeBridge.cancelAssistantInteraction(it.invocation.interactionId, "native_choice") } }
    }

    private fun clearNativeChoice() {
        nativeChoice?.request?.takeIf { it.isActive }?.cancel()
        nativeChoice = null
    }

    override fun onGetSupportedCommands(commands: Array<out String>): BooleanArray =
        commands.map { it == COMMAND_CAPABILITIES || it == COMMAND_STOP }.toBooleanArray()

    override fun onRequestCommand(request: CommandRequest) {
        when (request.command) {
            COMMAND_CAPABILITIES -> request.sendResult(Bundle().apply {
                putStringArray("commands", arrayOf(COMMAND_CAPABILITIES, COMMAND_STOP))
                putBoolean("confirmation", true); putBoolean("pickOption", true)
                putString("phoneExecution", "Halo discovery and durable authorization")
            })
            COMMAND_STOP -> {
                val current = active
                scope.launch {
                    try {
                        current?.let { runtimeBridge.cancelAssistantInteraction(it.invocation.interactionId, "native_stop") }
                        if (request.isActive) request.sendResult(Bundle().apply { putBoolean("stopped", true) })
                    } catch (_: Exception) { if (request.isActive) request.cancel() }
                    hide()
                }
            }
            else -> request.cancel()
        }
    }

    override fun onRequestConfirmation(request: ConfirmationRequest) {
        clearNativeChoice()
        pauseForNativeChoice()
        nativeChoice = NativeChoice(request, nativePrompt(request.callingPackage, request.voicePrompt), listOf(
            "Confirm" to { if (request.isActive) request.sendConfirmationResult(true, Bundle()) },
            "Decline" to { if (request.isActive) request.sendConfirmationResult(false, Bundle()) },
        ))
        show(null, 0)
    }

    override fun onRequestPickOption(request: PickOptionRequest) {
        clearNativeChoice()
        pauseForNativeChoice()
        val options = request.options
        if (options.isEmpty() || options.size > 12) { request.cancel(); return }
        nativeChoice = NativeChoice(request, nativePrompt(request.callingPackage, request.voicePrompt), options.map { option ->
            option.label.toString().take(120) to { if (request.isActive) request.sendPickOptionResult(arrayOf(option), Bundle()) }
        })
        show(null, 0)
    }

    private fun nativePrompt(caller: String, prompt: android.app.VoiceInteractor.Prompt?): String =
        "${caller.take(150)}: ${prompt?.visualPrompt?.toString()?.take(500) ?: "Choose an option"}"

    override fun onCancelRequest(request: Request) {
        if (nativeChoice?.request === request) nativeChoice = null
        if (request.isActive) request.cancel()
    }

    override fun onRequestCompleteVoice(request: CompleteVoiceRequest) {
        request.sendCompleteResult(Bundle())
        hide()
    }

    override fun onRequestAbortVoice(request: AbortVoiceRequest) {
        val current = active
        current?.let { applicationScope.launch { runtimeBridge.cancelAssistantInteraction(it.invocation.interactionId, "native_abort") } }
        request.sendAbortResult(Bundle())
        hide()
    }

    private companion object {
        const val COMMAND_CAPABILITIES = "com.halo.assistant.CAPABILITIES"
        const val COMMAND_STOP = "com.halo.assistant.STOP"
    }

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

        setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
        setContent {
            com.halo.app.language.AppLanguageProvider(ManualDependencyGraph.appLanguagePreferences) {
                val appearance by ManualDependencyGraph.appearancePreferences.mode.collectAsStateWithLifecycle()
                val darkTheme = when (appearance) {
                    AppearanceMode.SYSTEM -> isSystemInDarkTheme()
                    AppearanceMode.LIGHT -> false
                    AppearanceMode.DARK -> true
                }
                val preferences by ManualDependencyGraph.assistantPreferences.state.collectAsState()
                HaloTheme(darkTheme = darkTheme, fontScale = preferences.fontScale) {
                    /*
                     * RUNTIME-019: the overlay observes the shared interaction
                     * projection and can render the active input surface inline;
                     * the same surface stays pending if the overlay is hidden.
                     */
                    val interactionViewModel: com.halo.app.interaction.InteractionSurfaceViewModel =
                        androidx.lifecycle.viewmodel.compose.viewModel(
                            factory = com.halo.app.interaction.InteractionSurfaceViewModelFactory(),
                        )
                    val interactionState2 by interactionViewModel.state.collectAsStateWithLifecycle()
                    nativeChoice?.let { choice ->
                        androidx.compose.material3.AlertDialog(
                            onDismissRequest = { clearNativeChoice() },
                            title = { androidx.compose.material3.Text(uiText("Voice request")) },
                            text = { androidx.compose.material3.Text(choice.prompt) },
                            confirmButton = {
                                androidx.compose.foundation.layout.Column {
                                    choice.options.forEach { (label, respond) ->
                                        androidx.compose.material3.TextButton(onClick = {
                                            nativeChoice = null
                                            respond()
                                        }) { androidx.compose.material3.Text(label) }
                                    }
                                }
                            },
                            dismissButton = { androidx.compose.material3.TextButton(onClick = { clearNativeChoice() }) {
                                androidx.compose.material3.Text(uiText("Cancel"))
                            } },
                        )
                    }
                    val appContext by ManualDependencyGraph.appFunctionCapabilityIndex.state.collectAsState()
                    var currentAppLabel by androidx.compose.runtime.remember(appContext.currentPackage) { mutableStateOf<String?>(null) }
                    androidx.compose.runtime.LaunchedEffect(appContext.currentPackage) {
                        currentAppLabel = appContext.currentPackage?.let { pkg -> kotlinx.coroutines.withContext(Dispatchers.IO) {
                            runCatching { context.packageManager.getApplicationLabel(context.packageManager.getApplicationInfo(pkg, 0)).toString() }.getOrDefault(pkg)
                        } }
                    }
                    AssistantSurface(
                        currentApp = currentAppLabel,
                        interactionState = interactionState,
                        screenContextAttached = screenContextAttached,
                        interactionSurface = interactionState2.primary
                            ?.takeIf { it.interactionId?.value == active?.invocation?.interactionId },
                        onInteractionRespond = interactionViewModel::respond,
                        onStopClicked = {
                            val stopped = active
                            com.halo.app.ui.fx.HaloMotionEvents.stop(stopped?.invocation?.interactionId)
                            stopped?.let { com.halo.host.android.assistant.NativeAssistantActions.shared.detach(it.invocation.interactionId) }
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
    }

    override fun onShow(args: Bundle?, showFlags: Int) {
        super.onShow(args, showFlags)
        android.util.Log.i("HaloAssistant", "HALO_ASSISTANT_SHOW interactionId=${active?.invocation?.interactionId ?: "none"}")
        AssistantRuntimeStateStore.sessionVisible(true)
        lifecycleRegistry.currentState = Lifecycle.State.RESUMED
        if (nativeChoice != null) return
        val previous = active
        previous?.let { com.halo.host.android.assistant.NativeAssistantActions.shared.detach(it.invocation.interactionId) }
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
                previous?.let { runtimeBridge.cancelAssistantInteraction(it.invocation.interactionId, "replaced") }
                if (active !== current) return@launch
                runtimeBridge.begin(invocation)
                current.ready.complete(Unit)
                if (active !== current) {
                    runtimeBridge.hideAssistantSurface(invocation.interactionId)
                    return@launch
                }
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
        android.util.Log.i("HaloAssistant", "HALO_ASSISTANT_ASSIST_CONTEXT interactionId=${active?.invocation?.interactionId ?: "none"}")
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return
        val current = active ?: return
        val currentPackage = state.assistStructure?.activityComponent?.packageName
        attachNativeActions(current.invocation.interactionId, state.activityId, currentPackage)
        if (currentPackage != null) scope.launch {
            val identity = UUID.randomUUID().toString()
            val direct = try { com.halo.host.android.assistant.NativeAssistantActions.shared.list().map { action ->
                com.halo.runtime.cognition.apps.SemanticAppCapability(action.token, currentPackage,
                    com.halo.runtime.cognition.apps.SemanticActionMapping.fromOfferedIdentifier(action.name),
                    action.name, null, emptyList(), com.halo.runtime.cognition.apps.AppOperationRoute.DIRECT_ACTION,
                    com.halo.runtime.cognition.apps.CapabilityScope.CURRENT_ACTIVITY, activityIdentity = identity)
            } } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
                catch (_: Exception) { emptyList() }
            if (active === current) ManualDependencyGraph.appFunctionConsumer.attach(currentPackage, identity,
                if (Build.VERSION.SDK_INT >= 37) getAppFunctionActivityId(state.activityId) else null, direct)
        }
        val snapshot = contextExtractor.extract(state.assistStructure, state.assistContent)
        scope.launch {
            runCatching { current.ready.await(); if (active === current) runtimeBridge.attachScreen(current.invocation.interactionId, snapshot) }
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

    @androidx.annotation.RequiresApi(29)
    private fun attachNativeActions(owner: String, activityId: ActivityId, packageName: String?) {
        com.halo.host.android.assistant.NativeAssistantActions.shared.attach(owner,
            object : com.halo.host.android.assistant.NativeAppActionSource {
                private var actions = emptyList<android.app.DirectAction>()
                private var accessRevision = -1L
                override suspend fun list(): List<String> = kotlinx.coroutines.withTimeout(5_000) {
                    kotlinx.coroutines.suspendCancellableCoroutine { continuation ->
                        val cancellation = android.os.CancellationSignal()
                        continuation.invokeOnCancellation { cancellation.cancel() }
                        requestDirectActions(activityId, cancellation, context.mainExecutor) { result ->
                            if (continuation.isActive) {
                                actions = result.take(12)
                                accessRevision = ManualDependencyGraph.appActionAccess.revision
                                continuation.resumeWith(Result.success(actions.map { it.id }))
                            }
                        }
                    }
                }
                override fun dispatch(index: Int) {
                    val action = actions.getOrNull(index) ?: error("Native app action is no longer available")
                    val access = ManualDependencyGraph.appActionAccess
                    check(packageName != null && accessRevision == access.revision && access.allows(packageName,
                        com.halo.runtime.cognition.apps.SemanticActionMapping.fromOfferedIdentifier(action.id).value)) {
                        "App action access changed; review access and discover again"
                    }
                    performDirectAction(action, Bundle(), null, context.mainExecutor) { /* App result is untrusted, not proof of the effect. */ }
                    actions = emptyList()
                }
            })
    }

    override fun onDirectActionsInvalidated(activityId: ActivityId) {
        super.onDirectActionsInvalidated(activityId)
        com.halo.host.android.assistant.NativeAssistantActions.shared.invalidate()
        ManualDependencyGraph.appFunctionConsumer.detach()
    }

    @Suppress("DEPRECATION")
    override fun onHandleAssist(data: Bundle?, structure: android.app.assist.AssistStructure?, content: android.app.assist.AssistContent?) {
        if (Build.VERSION.SDK_INT >= 29) return
        val current = active ?: return
        val snapshot = contextExtractor.extract(structure, content) ?: return
        scope.launch {
            current.ready.await()
            if (active === current) {
                runtimeBridge.attachScreen(current.invocation.interactionId, snapshot)
                screenContextAttached = true
            }
        }
    }

    override fun onHandleScreenshot(screenshot: Bitmap?) {
        super.onHandleScreenshot(screenshot)
        android.util.Log.i("HaloAssistant", "HALO_ASSISTANT_SCREENSHOT interactionId=${active?.invocation?.interactionId ?: "none"}")
        val current = active ?: return
        if (current.screenshotAttached || screenshot == null) return
        current.screenshotAttached = true
        val bitmap = screenshot.copy(screenshot.config ?: Bitmap.Config.ARGB_8888, false) ?: return
        scope.launch {
            try {
                current.ready.await()
                if (active === current) runtimeBridge.attachScreenshot(current.invocation.interactionId, bitmap)
            } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
              catch (_: Exception) { current.screenshotAttached = false }
            finally { bitmap.recycle() }
        }.invokeOnCompletion { if (!bitmap.isRecycled) bitmap.recycle() }
    }

    override fun onHide() {
        clearNativeChoice()
        AssistantRuntimeStateStore.sessionVisible(false)
        ManualDependencyGraph.appFunctionConsumer.detach()
        interactionObserverJob?.cancel()
        interactionObserverJob = null
        val ending = active
        ending?.let { com.halo.host.android.assistant.NativeAssistantActions.shared.detach(it.invocation.interactionId) }
        active = null
        if (ending != null) scope.launch { runtimeBridge.hideAssistantSurface(ending.invocation.interactionId) }
        interactionState = InteractionTurnState.Idle
        lifecycleRegistry.currentState = Lifecycle.State.CREATED
        super.onHide()
        android.util.Log.i("HaloAssistant", "HALO_ASSISTANT_HIDE interactionId=${ending?.invocation?.interactionId ?: "none"}")
    }

    override fun onDestroy() {
        val endingInteractionId = active?.invocation?.interactionId
        clearNativeChoice()
        AssistantRuntimeStateStore.sessionDestroyed()
        interactionObserverJob?.cancel()
        interactionObserverJob = null
        active?.let { ending ->
            com.halo.host.android.assistant.NativeAssistantActions.shared.detach(ending.invocation.interactionId)
            applicationScope.launch {
                runtimeBridge.hideAssistantSurface(ending.invocation.interactionId)
            }
        }
        active = null
        lifecycleRegistry.currentState = Lifecycle.State.DESTROYED
        store.clear()
        scope.cancel()
        super.onDestroy()
        android.util.Log.i("HaloAssistant", "HALO_ASSISTANT_SESSION_DESTROYED interactionId=${endingInteractionId ?: "none"}")
    }

    override val lifecycle: Lifecycle get() = lifecycleRegistry
    override val viewModelStore: ViewModelStore get() = store
    override val savedStateRegistry: SavedStateRegistry get() = savedStateRegistryController.savedStateRegistry
}
