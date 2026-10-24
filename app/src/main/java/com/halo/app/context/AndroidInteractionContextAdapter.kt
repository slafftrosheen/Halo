package com.halo.app.context

import com.halo.app.data.AppDatabase
import com.halo.app.data.RoomConversationStore
import com.halo.app.data.WorkspaceContextStore
import com.halo.app.data.notebook.NotebookRepository
import com.halo.app.data.workspace.WorkspaceMediaAiPolicy
import com.halo.app.study.StudySourceResolver
import com.halo.app.study.ResolvedStudySource
import com.halo.runtime.cognition.context.*
import com.halo.runtime.cognition.turn.CognitiveTurnRequest
import com.halo.runtime.cognition.vision.InteractionMediaBinder
import com.halo.sdk.runtime.VisualFreshness
import com.halo.sdk.runtime.multimodal.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.UUID

data class SelectedContextChip(val focus: InteractionContextFocus, val title: String, val sharing: String,
    val mediaRef: MediaAssetRef? = null, val saved: Boolean = false)

/** Identity/lease adapter only. Existing Workspace and media owners keep all source bytes. */
class AndroidInteractionContextAdapter(
    val owner: InteractionContextContinuity, private val conversations: RoomConversationStore,
    private val database: AppDatabase, private val workspace: WorkspaceContextStore,
    private val sources: StudySourceResolver, private val media: MediaAssetStore,
    private val binder: InteractionMediaBinder, private val policy: WorkspaceMediaAiPolicy,
    private val scope: CoroutineScope, private val nowMs: () -> Long = System::currentTimeMillis,
    private val eventBus: com.halo.sdk.runtime.EventBus? = null,
    private val effectivePolicy: (String) -> com.halo.sdk.provider.AiContextPolicy = { com.halo.sdk.provider.AiContextPolicy.CONSERVATIVE },
) {
    private data class VisualWorkspaceBinding(val objectId: String, val version: String)
    private val lock = Mutex()
    private val leases = linkedMapOf<String, MediaAssetRef>()
    private val titles = mutableMapOf<ContextReference, String>()
    /** Content-free source provenance travels with this explicit image into one turn. */
    private val visualContextText = mutableMapOf<ContextReference, String>()
    private val visualWorkspaceBindings = mutableMapOf<ContextReference, VisualWorkspaceBinding>()
    private val visualReferences = mutableSetOf<ContextReference>()
    private val mutableChips = MutableStateFlow<List<SelectedContextChip>>(emptyList())
    val chips = mutableChips.asStateFlow()
    val message = MutableStateFlow<String?>(null)
    private val events = kotlinx.coroutines.channels.Channel<ContextualNextActionTarget>(kotlinx.coroutines.channels.Channel.BUFFERED)
    val navigation = events.receiveAsFlow()
    suspend fun navigate(target: ContextualNextActionTarget) { events.send(target) }
    suspend fun selectStudy(sessionId: String) {
        val graph = com.halo.app.di.ManualDependencyGraph
        val session = graph.studyUseCases.getSession(sessionId)?.takeIf { it.lifecycle == com.halo.sdk.runtime.study.StudySessionLifecycle.ACTIVE } ?: error("Study session unavailable")
        val conversation = conversation()
        val reference = ContextReference.StudySession(sessionId)
        titles[reference] = session.goal ?: "Selected Study lesson"
        owner.select(InteractionContextFocus(conversation,reference,ContextCategory.STUDY,ContextFocusReason.STUDY_SESSION,nowMs(),null))
        navigate(ContextualNextActionTarget.ContinueStudy(sessionId))
    }
    init {
        scope.launch { conversations.activeConversation.collect { refresh() } }
        scope.launch { owner.state.collect { refresh() } }
        scope.launch { eventBus?.observe(com.halo.sdk.runtime.InteractionTurnChanged::class.java)?.collect { event ->
            val state = event.state
            if(state is com.halo.sdk.runtime.InteractionTurnState.Cancelled || state is com.halo.sdk.runtime.InteractionTurnState.Interrupted || state is com.halo.sdk.runtime.InteractionTurnState.Failed) lock.withLock {
                owner.state.value.filter { it.sourceInteractionId == state.interactionId }.forEach {
                    // Ending a turn releases its own media lease, not the user's selected source.
                    // Clear/Remove and expiry remain the explicit context cleanup owners.
                    if(it.state != ContextReferenceState.EXPIRED && it.state != ContextReferenceState.SOURCE_UNAVAILABLE)
                        owner.select(it.copy(state = ContextReferenceState.SELECTED, sourceInteractionId = null))
                }
            } else if(state is com.halo.sdk.runtime.InteractionTurnState.Completed) {
                owner.state.value.filter { it.sourceInteractionId == state.interactionId }.forEach {
                    if (it.reference in visualReferences && it.state !in setOf(ContextReferenceState.EXPIRED, ContextReferenceState.SOURCE_UNAVAILABLE))
                        owner.select(it.copy(state = ContextReferenceState.SELECTED, sourceInteractionId = null))
                    else owner.transition(it.conversationId,it.reference,ContextReferenceState.CONSUMED)
                }
            }
        } }
    }
    private suspend fun conversation(): String { conversations.awaitReady(); return requireNotNull(conversations.activeConversationId()) }
    suspend fun selectImage(ref: MediaAssetRef, title: String, contextText: String? = null,
        commitSource: suspend (() -> Unit) -> Unit = { commit -> commit() }) = lock.withLock {
        val conversation = conversation()
        require(media.metadata(ref) != null) { "That image is no longer available. Please attach it again." }
        check(owner.current(conversation).none { it.category == ContextCategory.VISUAL &&
            (it.reference as? ContextReference.MediaAsset)?.assetId != ref.id }) { "Remove the attached image before selecting another" }
        check(owner.current(conversation).none { it.category == ContextCategory.WORKSPACE && it.reference in visualReferences }) {
            "Remove the selected image source before attaching another"
        }
        val boundWorkspaceId = contextText?.takeIf { it.isNotBlank() }?.let { ref.workspaceObjectId }
        val boundWorkspaceVersion = boundWorkspaceId?.let { objectId ->
            requireNotNull(workspace.versionFor(objectId)) { "Selected region source is no longer available." }
        }
        releaseVisual(conversation)
        val contextOwner = "context:${UUID.randomUUID()}"
        val reference: ContextReference.MediaAsset
        val retained: MediaAssetRef
        val expires = if (ref.lifetime == MediaLifetime.WORKSPACE_PERSISTED) null else minOf(nowMs() + 120_000, ref.createdAtMs + 15 * 60_000)
        try {
            retained = media.transferOwnership(ref, contextOwner)
            reference = ContextReference.MediaAsset(retained.id, retained.interactionId)
            while (leases.size >= 8) {
                val oldest = leases.keys.first()
                releaseVisual(oldest)
                owner.remove(oldest, ContextCategory.VISUAL)
            }
            // All suspending media preparation precedes the source owner's guarded,
            // synchronous publication. A newer bookmark cannot authorize this crop.
            commitSource {
                leases[conversation] = retained
                titles[reference] = title.take(160)
                contextText?.takeIf { it.isNotBlank() }?.take(2000)?.let { text ->
                    visualContextText[reference] = text
                    if (boundWorkspaceId != null && boundWorkspaceVersion != null)
                        visualWorkspaceBindings[reference] = VisualWorkspaceBinding(boundWorkspaceId, boundWorkspaceVersion)
                }
                visualReferences.add(reference)
                owner.select(InteractionContextFocus(conversation,reference,ContextCategory.VISUAL,ContextFocusReason.EXPLICIT_ATTACHMENT,nowMs(),expires))
            }
        } catch (failure: Throwable) {
            withContext(NonCancellable) {
                // A failed handoff owns only this newly transferred context lease.
                val failed = leases[conversation]?.takeIf { it.interactionId == contextOwner }
                if (failed != null) {
                    leases.remove(conversation)
                    val failedRef = ContextReference.MediaAsset(failed.id, failed.interactionId)
                    titles.remove(failedRef); visualContextText.remove(failedRef)
                    visualWorkspaceBindings.remove(failedRef); visualReferences.remove(failedRef)
                    if (owner.current(conversation).any { it.category == ContextCategory.VISUAL && it.reference == failedRef })
                        owner.remove(conversation, ContextCategory.VISUAL)
                }
                media.releaseInteraction(contextOwner)
            }
            throw failure
        }
        if (expires != null) scope.launch { delay((expires - nowMs()).coerceAtLeast(0)); lock.withLock {
            if(leases[conversation] == retained) { releaseVisual(conversation); owner.transition(conversation,reference,ContextReferenceState.EXPIRED) }
        } }
        message.value = null
    }
    suspend fun selectWorkspace(objectId: String, expectedVersion: String? = null) = lock.withLock {
        val conversation = conversation()
        val item = database.notebookDao().getWorkspaceObject(objectId)?.takeIf { it.lifecycle == "ACTIVE" } ?: error("Source unavailable")
        check(policy.access(objectId) != com.halo.app.data.workspace.WorkspaceAiAccess.EXCLUDE_FROM_AI) { "Source excluded from AI" }
        val version = workspace.versionFor(objectId) ?: error("Source unavailable")
        check(expectedVersion == null || version == expectedVersion) { "Source changed. Select it again." }
        // Resolve readability now, without a model call or permission change.
        val canvas = canvasReferenceText(objectId)
        check(canvas == null || version.startsWith("canvas:")) { "Remove the ink-region reference before selecting the whole Canvas." }
        val source = if (canvas == null) sources.resolveExplicitReference(objectId, "") else null
        if(source is ResolvedStudySource.Unsupported) error(source.reason)
        check(source !is ResolvedStudySource.Pdf) { "Choose a readable note, text file, or exact image. PDF pages stay in Study." }
        check(workspace.versionFor(objectId) == version) { "Source changed. Select it again." }
        currentCoroutineContext().ensureActive()
        val reference = ContextReference.WorkspaceObject(objectId,version)
        titles[reference] = item.title.take(160)
        if(source is ResolvedStudySource.Visual) visualReferences.add(reference)
        releaseVisual(conversation)
        currentCoroutineContext().ensureActive()
        owner.remove(conversation,ContextCategory.VISUAL)
        owner.select(InteractionContextFocus(conversation,reference,ContextCategory.WORKSPACE,ContextFocusReason.USER_SELECTED_WORKSPACE,nowMs(),null))
        message.value = null
    }
    /** Canvas text comes from the existing Workspace owner; linked source bytes remain excluded. */
    private suspend fun canvasReferenceText(objectId: String): String? {
        val item = database.notebookDao().getWorkspaceObject(objectId)?.takeIf {
            it.lifecycle == "ACTIVE" && it.contentRef.startsWith("canvas:") && it.type in setOf("CANVAS", "ARTIFACT")
        } ?: return null
        val text = workspace.contentFor(item.id) ?: error("Canvas unavailable")
        return "Canvas reference objectId=${item.id}. Read halo_canvas_get for exact card IDs and board version before editing.\n$text"
    }
    suspend fun remove(category: ContextCategory) = lock.withLock {
        val conversation = conversation()
        if(category == ContextCategory.VISUAL) releaseVisual(conversation)
        owner.remove(conversation,category); message.value = null
    }
    suspend fun clearConversation(conversationId: String) = lock.withLock {
        releaseVisual(conversationId)
        ContextCategory.entries.forEach { owner.remove(conversationId, it) }
        refreshLocked()
    }
    suspend fun refresh() {
        lock.withLock { refreshLocked() }
    }
    private suspend fun refreshLocked() {
        val conversation = conversations.activeConversationId() ?: return
        val global = effectivePolicy(conversation)
        mutableChips.value = owner.current(conversation).filter { it.category != ContextCategory.RESULT }.map {
            val objectId = (it.reference as? ContextReference.WorkspaceObject)?.objectId ?: leases[conversation]?.workspaceObjectId
            val visual = it.reference in visualReferences
            val enabled = if(visual) global.includeImages else global.includeWorkspace
            val cloud = if(visual) global.allowCloudImages else global.allowCloudWorkspace
            val objectCloud = objectId?.let { id -> policy.permitsObject(id,false) }
                ?: leases[conversation]?.let { ref -> policy.permits(ref,false) } ?: false
            val sharing = if(!enabled) "Context disabled" else if(objectId?.let { id -> policy.access(id) == com.halo.app.data.workspace.WorkspaceAiAccess.EXCLUDE_FROM_AI } == true)
                "Excluded from AI" else if(cloud && objectCloud) "Configured cloud permitted" else "Local only"
            SelectedContextChip(it,titles[it.reference] ?: "Selected source",sharing,
                mediaRef = leases[conversation]?.takeIf { ref -> (it.reference as? ContextReference.MediaAsset)?.assetId == ref.id },
                saved = objectId != null)
        }
        val live = owner.state.value.map { it.reference }.toSet()
        titles.keys.retainAll(live)
        visualContextText.keys.retainAll(live)
        visualWorkspaceBindings.keys.retainAll(live)
        visualReferences.retainAll(live)
    }
    private suspend fun releaseVisual(conversation: String) { leases.remove(conversation)?.interactionId?.let { media.releaseInteraction(it) } }

    suspend fun resolve(turn: CognitiveTurnRequest): CognitiveTurnRequest = try { lock.withLock {
        val conversation = turn.conversationId ?: return@withLock turn
        val selected = owner.current(conversation).filter { it.reference in turn.contextReferences && it.category in setOf(ContextCategory.VISUAL,ContextCategory.WORKSPACE) }
        check(turn.contextReferences.filterNot { it is ContextReference.StudySession }.all { ref -> selected.any { it.reference == ref } }) { "Selected source changed. Please reselect it before asking." }
        if(selected.isEmpty()) return@withLock turn
        val refs = mutableListOf<ContextReference>()
        var image: MediaAssetRef? = null
        var workspaceId: String? = null
        val workspaceTexts = mutableListOf<String>()
        for(focus in selected) {
            check(focus.state != ContextReferenceState.EXPIRED && focus.state != ContextReferenceState.SOURCE_UNAVAILABLE) {
                "That source is no longer available. Please attach it again or remove the reference."
            }
            when(val reference = focus.reference) {
                is ContextReference.MediaAsset -> {
                    image = leases[conversation]?.takeIf { it.id == reference.assetId && it.interactionId == reference.ownerInteractionId }
                    if(image == null || media.metadata(image) == null) {
                        owner.transition(conversation,reference,ContextReferenceState.SOURCE_UNAVAILABLE)
                        error("That image is no longer available. Please attach it again.")
                    }
                    visualWorkspaceBindings[reference]?.let { binding ->
                        if (workspace.versionFor(binding.objectId) != binding.version) {
                            owner.transition(conversation,reference,ContextReferenceState.SOURCE_UNAVAILABLE)
                            message.value = "The selected ink region changed. Return to Canvas and select it again."
                            error(message.value!!)
                        }
                        workspaceId = binding.objectId
                    }
                    visualContextText[reference]?.let(workspaceTexts::add)
                }
                is ContextReference.WorkspaceObject -> {
                    check(policy.access(reference.objectId) != com.halo.app.data.workspace.WorkspaceAiAccess.EXCLUDE_FROM_AI) {
                        "Source excluded from AI"
                    }
                    if(workspace.versionFor(reference.objectId) != reference.version) {
                        owner.transition(conversation,reference,ContextReferenceState.SOURCE_UNAVAILABLE)
                        message.value = "Original version changed or source unavailable. Use updated source or remove the reference."
                        error(message.value!!)
                    }
                    val canvas = canvasReferenceText(reference.objectId)
                    if (canvas != null) {
                        check(reference.version?.startsWith("canvas:") == true) { "Canvas selection changed. Please select the page again." }
                        workspaceId = reference.objectId
                        workspaceTexts += canvas
                    } else when(val source = sources.resolveExplicitReference(reference.objectId,turn.request.text.orEmpty())) {
                        is ResolvedStudySource.Visual -> image = source.asset
                        is ResolvedStudySource.Text -> {
                            workspaceId = reference.objectId
                            workspaceTexts += source.passages.joinToString("\n\n") { it.text }.take(8000)
                        }
                        else -> { owner.transition(conversation,reference,ContextReferenceState.SOURCE_UNAVAILABLE); error("Source unavailable. Please reselect it.") }
                    }
                    check(workspace.versionFor(reference.objectId) == reference.version) { "Original version changed. Please select it again." }
                }
                else -> Unit
            }
            refs += focus.reference
        }
        val bound = image?.let { binder.bindImage(it,turn.interactionId,VisualFreshness.REFERENCE).ref }
        refs.forEach { owner.transition(conversation,it,ContextReferenceState.BOUND_TO_TURN,turn.interactionId.value) }
        val explicitWorkspaceText = workspaceTexts.distinct().joinToString("\n\n").take(8000).takeIf { it.isNotBlank() }
        val visualBinding = refs.filterIsInstance<ContextReference.MediaAsset>()
            .mapNotNull { visualWorkspaceBindings[it] }.firstOrNull()
        turn.copy(contextReferences=refs,explicitWorkspaceText=explicitWorkspaceText,explicitImage=bound?.let { ImageContext(mediaRef=it,mimeType=it.mimeType,timestampMs=it.createdAtMs,
            assetId=it.id,interactionId=turn.interactionId.value,freshness=VisualFreshness.REFERENCE) },
            request=if(bound != null) turn.request.copy(toolUseMode=com.halo.sdk.provider.ToolUseMode.NONE) else turn.request,
            metadata=turn.metadata + (if(bound != null) mapOf("hasAssistantImage" to "true","explicitImageReference" to "true") else emptyMap()) +
                listOfNotNull((workspaceId ?: visualBinding?.objectId)?.let { "workspaceObjectId" to it },
                    refs.filterIsInstance<ContextReference.WorkspaceObject>().firstOrNull()?.version?.let { "contextWorkspaceVersion" to it },
                    visualBinding?.let { "contextWorkspaceVersion" to it.version },
                    (workspaceId ?: image?.workspaceObjectId)?.let { "contextWorkspaceObjectId" to it }).toMap())
    } } catch(cancelled: CancellationException) { throw cancelled }
    catch(error: Exception) { message.value = error.message ?: "Selected source unavailable. Please attach it again."; throw error }
    suspend fun permitsWorkspace(objectId: String, isLocal: Boolean): Boolean = policy.permitsObject(objectId,isLocal)
}
