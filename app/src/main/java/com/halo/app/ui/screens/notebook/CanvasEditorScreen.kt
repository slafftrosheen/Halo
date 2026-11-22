package com.halo.app.ui.screens.notebook

import com.halo.app.ui.presence.BindShellPageActions
import com.halo.app.ui.presence.ShellPageAction
import com.halo.app.ui.presence.ShellPageOwner
import com.halo.app.ui.zone5.Zone5InstrumentHeading
import com.halo.app.ui.zone5.Zone5ActionButton
import com.halo.app.ui.instruments.HaloInstrumentAction
import com.halo.app.ui.instruments.HaloInstrumentBar
import com.halo.app.ui.zone6.BindHaloGestureAlternative
import com.halo.app.ui.zone6.HaloGesture
import com.halo.app.ui.zone6.LocalHaloPageGuidance
import com.halo.app.ui.zone6.haloGestureAnchor
import com.halo.app.ui.workspace.ToolLookDialog
import com.halo.app.ui.workspace.rememberToolAppearance
import com.halo.app.R

import com.halo.app.ui.components.HaloTextField
import com.halo.app.ui.components.VoiceFieldKind
import com.halo.app.language.uiText
import com.halo.app.ui.components.HaloVoiceTextActions
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.painterResource
import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.compose.runtime.DisposableEffect
import kotlinx.coroutines.launch
import com.halo.app.data.notebook.CanvasFileMarkupCodec
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.halo.app.data.notebook.CanvasNodeType
import com.halo.app.data.notebook.CanvasVisualShape
import com.halo.app.data.notebook.CanvasReviewPin
import com.halo.app.data.notebook.canvasLockedIds
import com.halo.app.data.notebook.isCanvasContainer
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

import androidx.activity.compose.BackHandler
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import com.halo.app.ui.fx.rememberHaloReducedMotion
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.halo.app.ui.design.HaloScreenHeader
import com.halo.app.ui.design.HaloFocusAction
import com.halo.app.ui.design.HaloEmptyState
import com.halo.app.ui.design.HaloProgressPresentation
import com.halo.app.ui.icons.HaloGlyph
import com.halo.app.ui.fx.haloDraftBorder
import com.halo.app.ui.fx.HaloFxAnimation
import com.halo.app.ui.fx.HaloFxMoment
import kotlinx.coroutines.delay
import com.halo.app.data.notebook.CanvasNode
import kotlin.math.roundToInt

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class,
    androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun CanvasEditorScreen(viewModel: CanvasEditorViewModel, focusNodeId: String? = null,
    onOpenStudio: ((String, String?) -> Unit)? = null, kidsMode: Boolean = false, onBack: () -> Unit) {
    var toolsExpanded by rememberSaveable(viewModel.canvasId) { mutableStateOf(false) }
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var requestedList by rememberSaveable(viewModel.canvasId) { mutableStateOf<Boolean?>(null) }
    val touchExploration = canvasTouchExplorationEnabled()
    val easyView = com.halo.app.ui.theme.rememberVisualPreferences().viewMode == com.halo.app.ui.theme.HaloViewMode.EASY
    val context = LocalContext.current
    val guidance = LocalHaloPageGuidance.current
    val currentGuidance by rememberUpdatedState(guidance)
    val appearanceOwner = remember(viewModel.canvasId, context.applicationContext) { canvasAppearanceOwner(context, viewModel.canvasId) }
    var savedAppearance by rememberToolAppearance(appearanceOwner)
    var appearanceDraft by remember(viewModel.canvasId) { mutableStateOf<CanvasAppearance?>(null) }
    val appearance = appearanceDraft ?: savedAppearance?.current ?: CanvasAppearance()
    val canCustomize = !kidsMode && !state.loading && !state.missing && savedAppearance != null
    fun customize() {
        if (canCustomize) appearanceDraft = savedAppearance?.current
    }
    val showAsList = requestedList ?: (appearance.openAsList || easyView || touchExploration)
    val remoteInk = com.halo.app.di.ManualDependencyGraph.deviceSessionManager.ink
    val remoteSessions by remoteInk.sessions.collectAsStateWithLifecycle()
    val remotePreviews by remoteInk.previews.collectAsStateWithLifecycle()
    LaunchedEffect(remoteSessions.isNotEmpty()) {
        if (remoteSessions.isNotEmpty()) while (true) { delay(1000); remoteInk.refresh() }
    }
    var requestedPreviewId by remember(viewModel.canvasId) { mutableStateOf<String?>(null) }
    LaunchedEffect(state.selectedNodeId) { requestedPreviewId = state.selectedNodeId }
    var observedOrganizationCommit by remember(viewModel.canvasId) {
        mutableLongStateOf(state.organizationCommitRevision)
    }
    var showArrangeMoment by remember(viewModel.canvasId) { mutableStateOf(false) }
    LaunchedEffect(state.organizationCommitRevision) {
        val revision = state.organizationCommitRevision
        val newlyCommitted = canvasArrangeShouldPlay(observedOrganizationCommit, revision)
        observedOrganizationCommit = revision
        if (newlyCommitted) {
            showArrangeMoment = true
            delay(1000L)
            showArrangeMoment = false
        }
    }
    val reducedCanvasMotion = rememberHaloReducedMotion()
    // Shared per-card motion values let graph edges follow their cards.
    // Values are logical board coordinates, not screen pixels: panning/zooming
    // and manual dragging retain their direct, zero-latency behavior.
    val cardMotion = remember(viewModel.canvasId) {
        mutableStateMapOf<String, CanvasCardMotion>()
    }
    LaunchedEffect(state.nodes, state.organizingMotionNodeIds, reducedCanvasMotion) {
        val validIds = state.nodes.mapTo(mutableSetOf()) { it.id }
        cardMotion.keys.toList().filterNot { it in validIds }.forEach { cardMotion.remove(it) }
        for (node in state.nodes) {
            val motion = cardMotion.getOrPut(node.id) { CanvasCardMotion(node.x, node.y) }
            val glide = canvasCardShouldGlide(
                node.id, state.organizingMotionNodeIds, reducedCanvasMotion,
            )
            launch {
                if (glide) motion.x.animateTo(node.x, tween(durationMillis = 560))
                else motion.x.snapTo(node.x)
            }
            launch {
                if (glide) motion.y.animateTo(node.y, tween(durationMillis = 560))
                else motion.y.snapTo(node.y)
            }
        }
    }
    val boardEditingEnabled = state.canEditBoard()
    var groupSelection by remember(viewModel.canvasId) { mutableStateOf<Set<String>?>(null) }
    var reviewIndex by remember(viewModel.canvasId) { mutableStateOf(false) }
    var pinPlacing by remember(viewModel.canvasId) { mutableStateOf(false) }
    var pinPosition by remember(viewModel.canvasId) { mutableStateOf<Offset?>(null) }
    var reviewPinId by remember(viewModel.canvasId) { mutableStateOf<String?>(null) }
    val lockedIds = remember(state.nodes, state.edges) { canvasLockedIds(state.nodes, state.edges) }
    // Dismiss stale action menus if a background operation takes ownership of the board.
    // Saveable drafts remain intact and can be resumed when the board is ready.
    val memoReadiness by viewModel.memoReadiness.collectAsStateWithLifecycle()
    BackHandler { viewModel.leave(onBack) }
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    val edgeColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
    val guideTone = com.halo.app.ui.theme.brandAccent(
        androidx.compose.foundation.isSystemInDarkTheme(), com.halo.app.ui.theme.HaloAccent.LILAC).ink
    var showImageStudio by remember { mutableStateOf(false) }
    var showAi by rememberSaveable { mutableStateOf(false) }
    var draftReviewOpen by rememberSaveable(viewModel.canvasId) { mutableStateOf(true) }
    LaunchedEffect(state.aiDraft != null) { if (state.aiDraft != null) draftReviewOpen = true }
    var previousGeneratedDraft by remember(viewModel.canvasId) {
        mutableLongStateOf(state.aiDraftRevision)
    }
    var showDraftDrawOn by remember(viewModel.canvasId) { mutableStateOf(false) }
    LaunchedEffect(state.aiDraftRevision) {
        val revision = state.aiDraftRevision
        val arrived = canvasDraftDrawOnShouldPlay(
            previousGeneratedDraft, revision, state.aiDraft != null,
        )
        previousGeneratedDraft = revision
        if (arrived) {
            showDraftDrawOn = true
            delay(900L) // supplied Draw-on: once, never a working-loop
            showDraftDrawOn = false
        }
    }
    var aiAction by rememberSaveable { mutableStateOf("Review") }
    var aiInstructions by rememberSaveable { mutableStateOf("") }
    var menu by remember { mutableStateOf(false) }
    var arrangeTarget by remember(viewModel.canvasId) { mutableStateOf<Pair<Set<String>, Int>?>(null) }
    // NB04: one contextual suggestion, never an automatic graph mutation.
    // The user must have explicitly selected four cards. Dismissing this
    // suggestion leaves the cards and selection untouched.
    var dismissedGroupSuggestion by rememberSaveable(viewModel.canvasId) {
        mutableStateOf("")
    }
    var rename by rememberSaveable { mutableStateOf(false) }
    var title by rememberSaveable { mutableStateOf("") }
    var picker by remember { mutableStateOf(false) }
    var insertMenu by remember { mutableStateOf(false) }
    var cardMenu by remember { mutableStateOf(false) }
    var listEditCard by remember(viewModel.canvasId) { mutableStateOf<CanvasNode?>(null) }
    var listEditText by remember(viewModel.canvasId) { mutableStateOf("") }
    var listEditSaving by remember(viewModel.canvasId) { mutableStateOf(false) }
    var listEditSaveError by remember(viewModel.canvasId) { mutableStateOf(false) }
    LaunchedEffect(boardEditingEnabled) { if (!boardEditingEnabled) cardMenu = false }
    var inboxCapture by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    var connecting by remember(viewModel) { mutableStateOf(false) }
    var connectionFrom by remember(viewModel) { mutableStateOf<String?>(null) }
    var connectionTo by remember(viewModel) { mutableStateOf<String?>(null) }
    var connectionPointer by remember(viewModel) { mutableStateOf<Offset?>(null) }
    var chooseConnectionLabel by remember(viewModel) { mutableStateOf(false) }
    fun endConnect() {
        connecting = false
        connectionFrom = null
        connectionTo = null
        connectionPointer = null
        chooseConnectionLabel = false
    }
    fun startConnect(from: String? = state.selectedNodeId) {
        if (!boardEditingEnabled || state.nodes.size < 2) return
        connecting = true
        connectionFrom = from
        connectionTo = null
        connectionPointer = null
    }
    fun chooseConnectionNode(id: String) {
        if (connectionFrom == null) connectionFrom = id
        else if (id != connectionFrom) connectionTo = id
    }
    if (!showAsList && !kidsMode && boardEditingEnabled && state.nodes.size > 1) {
        BindHaloGestureAlternative(HaloGesture.CANVAS_LINK) { startConnect() }
    }
    var pairReview by remember { mutableStateOf(false) }
    var pairLabel by rememberSaveable { mutableStateOf("") }
    LaunchedEffect(state.selectedNodeIds) {
        if (state.selectedNodeIds.size != 2) pairReview = false
    }
    var connectionLabel by rememberSaveable { mutableStateOf("") }
    LaunchedEffect(boardEditingEnabled, state.nodes.map { it.id }) {
        if (connecting && (!boardEditingEnabled || listOfNotNull(connectionFrom, connectionTo).any { id ->
                state.nodes.none { it.id == id }
            })) endConnect()
    }
    BackHandler(connecting && !chooseConnectionLabel) { endConnect() }
    var confirmDelete by remember { mutableStateOf(false) }
    var boardWidth by remember { mutableStateOf(0) }
    var boardHeight by remember { mutableStateOf(0) }
    var focused by rememberSaveable(focusNodeId) { mutableStateOf(false) }
    LaunchedEffect(focusNodeId, state.loading, boardWidth, boardHeight, showAsList) {
        if (!focused && !state.loading && (showAsList || boardWidth > 0 && boardHeight > 0) && focusNodeId != null) {
            if (showAsList) viewModel.selectNode(focusNodeId) else viewModel.focusNode(focusNodeId)
            focused = true
        }
    }
    val selected = state.nodes.firstOrNull { it.id == state.selectedNodeId }
    val rewriteAvailable = canvasRewriteIsAvailable(selected?.type,
        selected?.refObjectId != null, selected?.text)
    LaunchedEffect(showAi, rewriteAvailable) {
        if (showAi && !rewriteAvailable && aiAction == "Rewrite") aiAction = "Review"
    }
    val lifecycleOwner = LocalLifecycleOwner.current
    var sketchOpen by rememberSaveable { mutableStateOf(false) }
    var sketchTarget by rememberSaveable { mutableStateOf<String?>(null) }
    var sketchOriginal by rememberSaveable { mutableStateOf("") }
    var annotateNodeId by rememberSaveable { mutableStateOf<String?>(null) }
    var imageToolsNodeId by rememberSaveable { mutableStateOf<String?>(null) }
    var pdfPageNodeId by rememberSaveable { mutableStateOf<String?>(null) }
    var stitchSources by remember { mutableStateOf<List<Pair<String, com.halo.app.data.workspace.CanvasStitchSource>>>(emptyList()) }
    var stitchPickerIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var annotationVersion by rememberSaveable { mutableStateOf<String?>(null) }
    var annotationOriginal by rememberSaveable { mutableStateOf("") }
    var searchOpen by remember { mutableStateOf(false) }
    var navigationControls by rememberSaveable(viewModel.canvasId) { mutableStateOf(false) }
    var handMode by rememberSaveable(viewModel.canvasId) { mutableStateOf(false) }
    var minimapOpen by remember(viewModel.canvasId) { mutableStateOf(false) }
    var minimapJump by remember(viewModel.canvasId) { mutableStateOf<String?>(null) }
    var minimapFit by remember(viewModel.canvasId) { mutableStateOf(false) }
    var shapeOpen by remember(viewModel.canvasId) { mutableStateOf(false) }
    var shapeTarget by remember(viewModel.canvasId) { mutableStateOf<CanvasNode?>(null) }
    var frameOpen by remember(viewModel.canvasId) { mutableStateOf(false) }
    var presenting by remember(viewModel.canvasId) { mutableStateOf(false) }
    var alignOpen by remember(viewModel.canvasId) { mutableStateOf<Pair<Set<String>, Int>?>(null) }
    var autoFocusNewText by remember(viewModel.canvasId) { mutableStateOf<String?>(null) }
    var selectionStart by remember(viewModel.canvasId) { mutableStateOf<Offset?>(null) }
    var selectionEnd by remember(viewModel.canvasId) { mutableStateOf<Offset?>(null) }
    val shellOwnsOrb = com.halo.app.ui.presence.LocalShellHaloOrb.current
    val pageOwner = ShellPageOwner("canvas", viewModel.canvasId,
        "${state.nodes.hashCode()}:${state.edges.hashCode()}:${state.selectedNodeIds.sorted()}:${state.selectedNodeId}",
        selectionIds = state.selectedNodeIds.ifEmpty { setOfNotNull(state.selectedNodeId) }.sorted(),
        statusLabel = when {
            connecting -> stringResource(com.halo.app.R.string.z6_connect_mode)
            state.loading -> uiText("Loading…")
            state.missing -> uiText("Canvas unavailable")
            state.saveState == CanvasSaveState.FAILED -> stringResource(com.halo.app.R.string.canvas_save_failed)
            state.saveState == CanvasSaveState.SAVING -> stringResource(com.halo.app.R.string.canvas_save_saving)
            state.saveState == CanvasSaveState.EDITING -> stringResource(com.halo.app.R.string.canvas_save_editing)
            !boardEditingEnabled -> uiText("Updating...")
            state.aiDraft != null || state.textDraft != null || state.drawingDraft != null -> stringResource(com.halo.app.R.string.canvas_review_draft)
            showAsList -> stringResource(com.halo.app.R.string.canvas_presentation_list)
            else -> stringResource(com.halo.app.R.string.zone5_saved_state)
        },
        helpDescription = stringResource(com.halo.app.R.string.zone5_help_canvas),
        guidanceBlocked = connecting || appearanceDraft != null || navigationControls || minimapOpen || shapeOpen ||
            frameOpen || presenting || alignOpen != null || groupSelection != null || reviewIndex || pinPlacing ||
            pinPosition != null || reviewPinId != null || !boardEditingEnabled || insertMenu || picker || showAi ||
            state.aiDraft != null || menu || toolsExpanded || searchOpen || sketchOpen || showImageStudio,
        completedGuideIds = if (!state.loading && !state.missing && state.nodes.isNotEmpty()) setOf("canvas-add.v1", "canvas-new.v1") else emptySet(),
        helpSteps = listOf(com.halo.app.ui.presence.ShellGuideStep("canvas-add.v1", "page.canvas.add", stringResource(com.halo.app.R.string.zone5_guide_canvas), !state.loading && !state.missing && state.nodes.isNotEmpty())),
        nextStep = if (boardEditingEnabled && state.nodes.isEmpty() && !insertMenu && !picker && !showAi && state.aiDraft == null &&
            !menu && !toolsExpanded && !searchOpen && !sketchOpen && !showImageStudio)
            com.halo.app.ui.presence.ShellNextStep("canvas-add.v1", "page.canvas.add", stringResource(com.halo.app.R.string.zone5_guide_canvas)) else null,
        canonicalToolIds = listOf("halo_canvas_current", "halo_canvas_get", "halo_canvas_add_text", "halo_canvas_arrange",
            "halo_canvas_edit_text", "halo_canvas_export_note"),
        defaultDisabledActionReason = stringResource(when {
            state.loading -> com.halo.app.R.string.action_loading
            state.missing -> com.halo.app.R.string.action_missing
            else -> com.halo.app.R.string.action_wait
        }),
        disabledActionReasons = buildMap {
            if (!state.loading && !state.missing) {
                if (boardEditingEnabled && state.nodes.isEmpty()) {
                    put("page.canvas.arrange", uiText("Nothing in this view"))
                    put("page.canvas.export", uiText("Nothing in this view"))
                }
                if (state.nodes.isEmpty()) put("page.canvas.fit", uiText("Nothing in this view"))
                if (boardEditingEnabled && !state.undoAvailable) put("page.canvas.undo", stringResource(com.halo.app.R.string.action_no_undo))
            }
        })
    BindShellPageActions(pageOwner, if (connecting) listOf(
        ShellPageAction("page.canvas.done-connect", uiText("Done"), HaloGlyph.SUCCESS, connectionFrom != null && connectionTo != null) {
            connectionLabel = ""
            chooseConnectionLabel = true
        },
        ShellPageAction("page.canvas.cancel-connect", uiText("Cancel"), HaloGlyph.STOP) { endConnect() },
    ) else (if (state.saveState == CanvasSaveState.FAILED) listOf(
        ShellPageAction("page.canvas.retry-save", stringResource(com.halo.app.R.string.canvas_save_retry), HaloGlyph.NOTE) {
            viewModel.persistPending()
        }) else emptyList()) + listOf(
        ShellPageAction("page.canvas.add", "Add card", HaloGlyph.NOTE, boardEditingEnabled) { insertMenu = true },
        ShellPageAction("page.canvas.connect", "Connect", HaloGlyph.TOOL, boardEditingEnabled && state.nodes.size > 1) { startConnect() },
        ShellPageAction("page.canvas.arrange", stringResource(com.halo.app.R.string.zone5_arrange), HaloGlyph.TOOL,
            boardEditingEnabled && state.nodes.isNotEmpty()) {
            val current = viewModel.uiState.value
            arrangeTarget = current.selectedNodeIds.ifEmpty { setOfNotNull(current.selectedNodeId) } to current.nodes.hashCode()
        },
        ShellPageAction("page.canvas.fit", "Fit all cards", HaloGlyph.NOTE,
            !showAsList && state.nodes.isNotEmpty() && !state.loading && !state.missing) { viewModel.fitToContent() },
        ShellPageAction("page.canvas.view-layout", stringResource(
            if (showAsList) com.halo.app.R.string.canvas_presentation_show_board else com.halo.app.R.string.canvas_presentation_show_list
        ), HaloGlyph.NOTE, !state.loading && !state.missing) { requestedList = !showAsList },
        ShellPageAction("page.canvas.view-navigation", stringResource(com.halo.app.R.string.canvas_view_controls), HaloGlyph.TOOL,
            !state.loading && !state.missing) { requestedList = false; navigationControls = true },
        ShellPageAction("page.canvas.options", "Tools", HaloGlyph.ACTION, !state.loading && !state.missing) { menu = true },
        ShellPageAction("page.canvas.appearance", stringResource(R.string.alive_tool_edit_page), HaloGlyph.APPEARANCE,
            canCustomize, ::customize),
        ShellPageAction("page.canvas.find", "Find on canvas", HaloGlyph.SEARCH, !state.loading && !state.missing) { searchOpen = true; toolsExpanded = true },
        ShellPageAction("page.canvas.tools", "More tools", HaloGlyph.TOOL) { toolsExpanded = !toolsExpanded },
        ShellPageAction("page.canvas.undo", "Undo", HaloGlyph.PREVIOUS, boardEditingEnabled && state.undoAvailable) { viewModel.undo() },
        ShellPageAction("page.canvas.export", "Export to note", HaloGlyph.FILE, boardEditingEnabled && state.nodes.isNotEmpty()) { viewModel.exportToNote() },
        ShellPageAction("page.canvas.rename", "Rename", HaloGlyph.NOTE, boardEditingEnabled) { title = state.title; rename = true },
        ShellPageAction("page.canvas.view-select", if (state.selectionMode) "Done selecting" else "Select cards", HaloGlyph.TOOL,
            boardEditingEnabled, viewModel::toggleSelectionMode),
    ))
    if (shellOwnsOrb) com.halo.app.ui.presence.BindShellUtilityBand(pageOwner, includesStatus = true) {
        ToolZoneHeader(state.title, onBack = { viewModel.leave(onBack) }, subtitle = pageOwner.statusLabel,
            onTitleLongClick = (::customize).takeIf { canCustomize }) {
            ToolZoneHeaderAction("page.canvas.options") {
                IconButton(onClick = { menu = true }, modifier = Modifier.size(48.dp)) {
                    com.halo.app.ui.icons.HaloIcon(HaloGlyph.TOOL, uiText("Tools"))
                }
            }
        }
    }
    val canvasLook = savedAppearance
    if (canvasLook != null && appearanceDraft != null) {
        ToolLookDialog(R.string.alive_canvas_look, canvasLook, appearance, { appearanceDraft = it },
            onApply = {
                savedAppearance = appearanceOwner.apply(canvasLook, appearance)
                if (appearance.openAsList != canvasLook.current.openAsList) requestedList = null
                appearanceDraft = null
            }, onDismiss = { appearanceDraft = null }, preview = { CanvasAppearancePreview(appearance) }) {
            CanvasAppearanceControls(appearance, state.selectedNodeId) { appearanceDraft = it }
        }
    }
    if (navigationControls) CanvasNavigationDialog(state, onPan = { x, y ->
        viewModel.transform(x * density.density, y * density.density, 1f)
    }, onZoom = { factor -> viewModel.transform(0f, 0f, factor, boardWidth / 2f, boardHeight / 2f) },
        onMove = { x, y -> viewModel.uiState.value.let { current ->
            (if (current.selectionMode) current.selectedNodeIds.firstOrNull() else current.selectedNodeId)?.let {
                viewModel.moveNode(it, x * current.viewportScale, y * current.viewportScale)
            }
        } }, onFit = viewModel::fitToContent, onReset = viewModel::resetViewport,
        onDismiss = { navigationControls = false }, kidsMode = kidsMode)
    var boardSearch by rememberSaveable { mutableStateOf("") }
    var searchType by rememberSaveable { mutableStateOf("All") }
    LaunchedEffect(boardSearch, searchType, state.nodes.map { it.text }, state.edges, state.references) {
        val types = when (searchType) {
            "Notes" -> setOf(com.halo.app.data.notebook.WorkspaceObjectType.NOTE)
            "Files" -> setOf(com.halo.app.data.notebook.WorkspaceObjectType.FILE, com.halo.app.data.notebook.WorkspaceObjectType.MEDIA)
            else -> emptySet()
        }
        viewModel.searchCanvas(boardSearch, types)
    }
    var recognizing by remember { mutableStateOf(false) }
    var groupDialog by remember { mutableStateOf(false) }
    groupSelection?.let { ids ->
        val selection = state.nodes.filter { it.id in ids }
        CanvasGroupInstruments(selection, lockedIds, onAction = { action ->
            viewModel.manualSelectionActionOn(action, ids)
            groupSelection = null
        }, onDismiss = { groupSelection = null })
    }
    if (reviewIndex) {
        CanvasReviewIndex(state.nodes, boardEditingEnabled, onPlace = {
            reviewIndex = false
            requestedList = false
            handMode = false
            pinPlacing = true
        }, onOpen = { node ->
            reviewIndex = false
            reviewPinId = node.id
        }, onDismiss = { reviewIndex = false })
    }
    val commentPosition = pinPosition
    if (commentPosition != null) {
        CanvasReviewPinDialog(null, boardEditingEnabled, onChange = { pin ->
            viewModel.addReviewPin(commentPosition.x, commentPosition.y, pin) { id ->
                pinPosition = null
                reviewPinId = id
            }
        }, onDismiss = { pinPosition = null })
    } else {
        val reviewNode = state.nodes.firstOrNull { it.id == reviewPinId && it.type == CanvasNodeType.COMMENT }
        val reviewPin = reviewNode?.let { CanvasReviewPin.decode(it.text) }
        if (reviewNode != null && reviewPin != null) {
            key(reviewNode.id, reviewNode.revision) {
                CanvasReviewPinDialog(reviewPin, boardEditingEnabled, onChange = { pin ->
                    viewModel.updateReviewPin(reviewNode, pin) { }
                }, onDismiss = { reviewPinId = null })
            }
        }
    }
    BackHandler(enabled = pinPlacing) { pinPlacing = false }
    var groupTitle by rememberSaveable { mutableStateOf("") }
    var tagDialog by remember { mutableStateOf(false) }
    var tagTitle by rememberSaveable { mutableStateOf("") }

    listEditCard?.let { original ->
        val latest = state.nodes.firstOrNull { it.id == original.id }
        val matches = latest != null && latest.revision == original.revision && latest.text == original.text && latest.canEditListText()
        val maxChars = when (original.type) {
            CanvasNodeType.GROUP, CanvasNodeType.FRAME -> 80
            CanvasNodeType.TAG -> 50
            else -> Int.MAX_VALUE
        }
        AlertDialog(onDismissRequest = { if (!listEditSaving) listEditCard = null }, title = { Text(uiText("Edit card")) },
            text = {
                if (!matches) Text(stringResource(com.halo.app.R.string.canvas_presentation_card_changed))
                else Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    HaloTextField(listEditText, { listEditText = it.take(maxChars) }, enabled = boardEditingEnabled && !listEditSaving,
                        kind = VoiceFieldKind.TEXT, maxChars = maxChars, readText = null,
                        label = { Text(uiText("Text")) }, minLines = 2, maxLines = 6)
                    if (listEditSaveError) Text(uiText("Could not save that change. Try again."),
                        color = MaterialTheme.colorScheme.error)
                }
            },
            confirmButton = {
                TextButton(enabled = matches && boardEditingEnabled && !listEditSaving && listEditText != original.text, onClick = {
                    listEditSaving = true
                    listEditSaveError = false
                    scope.launch {
                        viewModel.saveListCardText(original, listEditText) { listEditCard = null }.join()
                        listEditSaving = false
                        listEditSaveError = listEditCard != null
                    }
                }) { Text(uiText("Save")) }
            },
            dismissButton = { TextButton(onClick = { listEditCard = null }, enabled = !listEditSaving) { Text(uiText("Cancel")) } })
    }

    arrangeTarget?.let { target ->
        val currentIds = state.selectedNodeIds.ifEmpty { setOfNotNull(state.selectedNodeId) }
        val valid = boardEditingEnabled && state.nodes.hashCode() == target.second && currentIds == target.first
        fun applyCurrent(action: () -> Unit) {
            val live = viewModel.uiState.value
            if (valid && live.nodes.hashCode() == target.second && live.selectedNodeIds.ifEmpty { setOfNotNull(live.selectedNodeId) } == target.first) {
                arrangeTarget = null; action()
            }
        }
        ModalBottomSheet(onDismissRequest = { arrangeTarget = null }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
            Column(Modifier.fillMaxWidth().weight(1f, fill = false).verticalScroll(rememberScrollState()).padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(com.halo.app.R.string.zone5_arrange), style = MaterialTheme.typography.titleLarge)
                if (!valid) Text(stringResource(com.halo.app.R.string.zone5_document_changed))
                Zone5ActionButton(uiText("Organize whole board"), "action_organize", valid, Modifier.fillMaxWidth()) {
                    applyCurrent { viewModel.organize(wholeBoard = true, ai = false) }
                }
                Zone5ActionButton(uiText("Organize selected"), "studio_align", valid && target.first.isNotEmpty(), Modifier.fillMaxWidth()) {
                    applyCurrent { viewModel.organize(wholeBoard = false, ai = false) }
                }
                Zone5ActionButton(uiText("Create group"), "studio_group", valid && target.first.size >= 2, Modifier.fillMaxWidth()) {
                    applyCurrent { viewModel.organize(wholeBoard = false, ai = false) }
                }
                Zone5ActionButton(uiText("Bring forward"), "action_up", valid && target.first.isNotEmpty(), Modifier.fillMaxWidth()) {
                    applyCurrent { viewModel.manualSelectionAction("Bring forward") }
                }
                Zone5ActionButton(uiText("Ungroup"), "studio_ungroup", valid && selected?.type == CanvasNodeType.GROUP, Modifier.fillMaxWidth()) {
                    applyCurrent { viewModel.manualSelectionAction("Ungroup") }
                }
            }
        }
    }

    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) viewModel.importSource(uri)
    }
    if (minimapOpen) CanvasMinimapSheet(state.nodes, { id ->
        requestedList = false; minimapJump = id; minimapOpen = false
    }, onFit = { requestedList = false; minimapFit = true; minimapOpen = false }, onDismiss = { minimapOpen = false })
    LaunchedEffect(showAsList, boardWidth, boardHeight, minimapJump, minimapFit) {
        if (!showAsList && boardWidth > 0 && boardHeight > 0) {
            minimapJump?.let(viewModel::focusNode)
            if (minimapFit) viewModel.fitToContent()
            minimapJump = null; minimapFit = false
        }
    }
    if (shapeOpen) CanvasShapeDialog(shapeTarget?.let { CanvasVisualShape.decode(it.text) } ?: CanvasVisualShape(),
        editing = shapeTarget != null, ready = boardEditingEnabled && shapeTarget?.let { state.nodes.firstOrNull { node -> node.id == it.id } == it } != false,
        onApply = { shape ->
            val existing = shapeTarget
            if (existing == null) viewModel.addShape(shape) else viewModel.updateShape(existing, shape)
            shapeTarget = null; shapeOpen = false
        }, onDismiss = { shapeTarget = null; shapeOpen = false })
    if (frameOpen) CanvasFrameDialog(boardEditingEnabled, onInsert = { title, preset ->
        requestedList = false
        handMode = false
        viewModel.addFrame(title, preset)
        frameOpen = false
    }, onDismiss = { frameOpen = false })
    if (presenting) CanvasPresentDialog(state) { presenting = false }
    alignOpen?.let { (ids, fingerprint) ->
        val valid = boardEditingEnabled && state.nodes.hashCode() == fingerprint && state.selectedNodeIds == ids
        com.halo.app.ui.zone6.Zone6ButtonSheet(uiText("Align and distribute"), { alignOpen = null }) {
            val commands = listOf("Align left", "Align center", "Align right", "Align top", "Align middle", "Align bottom",
                "Distribute horizontally", "Distribute vertically")
            commands.forEachIndexed { index, action ->
                TextButton(onClick = { viewModel.manualSelectionAction(action); alignOpen = null },
                    enabled = valid && ids.size >= if (index < 6) 2 else 3) { Text(uiText(action)) }
            }
        }
    }
    DisposableEffect(viewModel, lifecycleOwner) {
        com.halo.app.di.ManualDependencyGraph.activeCanvasForCapture.value = viewModel.canvasId
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_STOP) viewModel.persistPending() }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            viewModel.persistPending()
            if (com.halo.app.di.ManualDependencyGraph.activeCanvasForCapture.value == viewModel.canvasId)
                com.halo.app.di.ManualDependencyGraph.activeCanvasForCapture.value = null
        }
    }
    if (inboxCapture) InboxCaptureDialog(viewModel.canvasId) { inboxCapture = false }
    if (insertMenu) AlertDialog(
        onDismissRequest = { insertMenu = false },
        title = { Text(uiText("Add to Canvas")) },
        text = {
            // NB04: one Add entry point invokes the existing durable card,
            // recorder, picker and image owners; none is a mocked attachment.
            Column(Modifier.fillMaxWidth().heightIn(max = 420.dp)
                .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = { insertMenu = false; viewModel.addTextNode() },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)
                ) { Text(uiText("Add text card")) }
                OutlinedButton(
                    onClick = {
                        insertMenu = false
                        sketchTarget = null
                        sketchOriginal = ""
                        sketchOpen = true
                    },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)
                ) { Text(uiText("Sketch")) }
                OutlinedButton(
                    onClick = { insertMenu = false; inboxCapture = true },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)
                ) { Text(uiText("Photo or voice memo")) }
                OutlinedButton(
                    onClick = { insertMenu = false; picker = true },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)
                ) { Text(uiText("Link a workspace item")) }
                OutlinedButton(
                    onClick = { insertMenu = false; filePicker.launch(arrayOf("*/*")) },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)
                ) { Text(uiText("Add a file or PDF")) }
                OutlinedButton(onClick = { insertMenu = false; groupDialog = true },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Text(uiText("Group")) }
                if (viewModel.imageStudio != null) OutlinedButton(
                    onClick = {
                        insertMenu = false
                        viewModel.imageStudio?.prepare()
                        showImageStudio = true
                    },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)
                ) { Text(uiText("Create an image")) }
            }
        },
        confirmButton = {
            TextButton(onClick = { insertMenu = false }) { Text(uiText("Cancel")) }
        }
    )
    CanvasOrganizationDialog(viewModel, state)
    if (sketchOpen && !state.loading) {
        val target = state.nodes.firstOrNull { it.id == sketchTarget && it.type == CanvasNodeType.SKETCH }
        val recovery = state.drawingDraft?.takeIf { it.matchesSource(sketchTarget, "sketch", sketchOriginal) }
        var observedDraft by remember(sketchTarget, sketchOriginal) { mutableStateOf(recovery) }
        CanvasSketchPadDialog(
            initial = sketchOriginal,
            editingExisting = target != null,
            recovery = recovery?.sketch,
            selectionOwnerId = target?.let { viewModel.canvasId },
            selectionPageId = target?.id,
            onSelectionChanged = viewModel::setInkSelection,
            onSaveSelection = viewModel::saveInkSelection,
            onDraft = { encoded ->
                val draft = com.halo.app.data.notebook.CanvasDrawingDraft(sketchTarget, "sketch", sketchOriginal, sketch = encoded)
                observedDraft = draft
                viewModel.keepDrawingDraft(draft)
            },
            onSave = { encoded ->
                viewModel.addSketch(encoded, sketchTarget, sketchOriginal.takeIf { sketchTarget != null }, expectedDraft = observedDraft)
                sketchOpen = false
                sketchTarget = null
            },
            onDismiss = { viewModel.discardDrawingDraft(observedDraft); viewModel.setInkSelection(null); sketchOpen = false; sketchTarget = null },
        )
    }

    if (groupDialog) AlertDialog(
        onDismissRequest = { groupDialog = false },
        title = { Text(uiText("Create group")) },
        text = { HaloTextField(groupTitle, { groupTitle = it.take(80) },
            label = { Text(uiText("Group title")) }, singleLine = true, kind = VoiceFieldKind.SHORT, maxChars = 80) },
        confirmButton = { com.halo.app.ui.components.HaloActionButton(onClick = { viewModel.addGroup(groupTitle); groupDialog = false; groupTitle = "" },
            enabled = groupTitle.isNotBlank()) { Text(uiText("Create group")) } },
        dismissButton = { TextButton(onClick = { groupDialog = false }) { Text(uiText("Cancel")) } },
    )
    if (tagDialog) AlertDialog(
        onDismissRequest = { tagDialog = false },
        title = { Text(uiText("Tag selected")) },
        text = { HaloTextField(tagTitle, { tagTitle = it.take(50) },
            label = { Text(uiText("Tag name")) }, singleLine = true, kind = VoiceFieldKind.SHORT, maxChars = 50) },
        confirmButton = { com.halo.app.ui.components.HaloActionButton(onClick = { viewModel.tagSelected(tagTitle); tagDialog = false; tagTitle = "" },
            enabled = tagTitle.trim().trimStart('#').isNotBlank()) { Text(uiText("Add tag")) } },
        dismissButton = { TextButton(onClick = { tagDialog = false }) { Text(uiText("Cancel")) } },
    )
    annotateNodeId?.let { nodeId ->
        val target = state.nodes.firstOrNull { it.id == nodeId }
        val file = target?.refObjectId?.let { state.files[it] }
        if (file != null && target != null) {
            val recovery = state.drawingDraft?.takeIf { it.matchesSource(nodeId, "annotation", annotationOriginal, annotationVersion) }
            var observedDraft by remember(nodeId, annotationOriginal, annotationVersion) { mutableStateOf(recovery) }
            CanvasSourceInkDialog(file, annotationOriginal,
                recovery = recovery,
                onDraft = { draft ->
                    val owned = draft.copy(nodeId = nodeId, fileVersion = annotationVersion)
                    observedDraft = owned
                    viewModel.keepDrawingDraft(owned)
                },
                onSave = { updates -> viewModel.saveFileMarkup(nodeId, updates, annotationOriginal, annotationVersion, expectedDraft = observedDraft); annotateNodeId = null },
                onSaveSelection = { selection, updates ->
                    viewModel.saveFileMarkup(nodeId, updates, annotationOriginal, annotationVersion, selection, expectedDraft = observedDraft) {
                        viewModel.setInkSelection(null)
                        annotateNodeId = null
                    }
                },
                onDismiss = { viewModel.discardDrawingDraft(observedDraft); viewModel.setInkSelection(null); annotateNodeId = null },
                onSelectionChanged = viewModel::setInkSelection)
        }
    }
    imageToolsNodeId?.let { nodeId ->
        val target = state.nodes.firstOrNull { it.id == nodeId }
        val file = target?.refObjectId?.let(state.files::get)
        if (file != null && file.mimeType.startsWith("image/")) {
            CanvasLocalImageToolsDialog(
                file = file,
                busy = state.imageToolBusy,
                onSave = { recipe ->
                    viewModel.saveEditedImage(nodeId, file.version, recipe) { imageToolsNodeId = null }
                },
                onDismiss = { if (!state.importing) imageToolsNodeId = null },
            )
        }
    }
    pdfPageNodeId?.let { nodeId ->
        val target = state.nodes.firstOrNull { it.id == nodeId }
        val pdf = target?.refObjectId?.let(state.files::get)
        if (pdf != null && pdf.mimeType == "application/pdf") {
            CanvasPdfPageDialog(pdf, state.imageToolBusy,
                onSave = { pages ->
                    viewModel.saveExtractedPdfPages(nodeId, pdf, pages) { pdfPageNodeId = null }
                },
                onDismiss = { if (!state.imageToolBusy) pdfPageNodeId = null })
        }
    }
    if (stitchPickerIds.isNotEmpty()) {
        CanvasStitchSourcePicker(
            viewModel = viewModel,
            nodeIds = stitchPickerIds,
            onChoose = { chosen -> stitchSources = chosen; stitchPickerIds = emptySet() },
            onDismiss = { stitchPickerIds = emptySet() },
        )
    }
    if (stitchSources.isNotEmpty()) {
        CanvasImageStitchDialog(
            sources = stitchSources,
            busy = state.imageToolBusy,
            onSave = { recipe, ordered ->
                viewModel.saveCombinedImages(ordered, recipe) { stitchSources = emptyList() }
            },
            onDismiss = { if (!state.imageToolBusy) stitchSources = emptyList() },
        )
    }
    state.textDraft?.let { draft ->
        AlertDialog(
            onDismissRequest = viewModel::discardRecognizedDraft,
            title = { Text(uiText(if (draft.sourceKind == "Transcript") "Review transcript" else "Review recognized text")) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(uiText("Edit before adding this searchable text card."),
                        style = MaterialTheme.typography.bodySmall)
                    HaloTextField(draft.text, viewModel::editRecognizedDraft,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 120.dp, max = 340.dp), kind = VoiceFieldKind.TEXT)

                }
            },
            confirmButton = {
                com.halo.app.ui.components.HaloActionButton(onClick = viewModel::saveRecognizedDraft, enabled = draft.text.isNotBlank()) {
                    Text(uiText("Add text card"))
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::discardRecognizedDraft) { Text(uiText("Discard")) }
            },
        )
    }
    if (showImageStudio) viewModel.imageStudio?.let { studio ->
        com.halo.app.ui.workspace.WorkspaceImageDialog(studio) { showImageStudio = false }
    }
    if (showAi) AlertDialog(onDismissRequest = { showAi = false }, title = { Text(uiText("Think with halo")) },
        text = { Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            CanvasAiActionPicker(aiAction, onAction = { aiAction = it },
                rewriteAvailable = rewriteAvailable)
            Text(
                uiText(when {
                    aiAction == "Rewrite" -> "Rewrite the selected text card."
                    !rewriteAvailable -> "Select a text card to rewrite it. Linked sources can be edited in their own editor."
                    else -> "Use the text and link labels on this board."
                }), style = MaterialTheme.typography.bodySmall)
            HaloTextField(aiInstructions, { aiInstructions = it.take(1000) }, label = { Text(uiText("Your direction (optional)")) }, minLines = 2, kind = VoiceFieldKind.TEXT, maxChars = 1000)

        } }, confirmButton = { com.halo.app.ui.components.HaloActionButton(onClick = { viewModel.requestAi(aiAction, aiInstructions); showAi = false },
            enabled = boardEditingEnabled && (aiAction != "Rewrite" || rewriteAvailable)) {
            Text(uiText("Create draft"))
        } },
        dismissButton = { TextButton(onClick = { showAi = false }) { Text(uiText("Cancel")) } })
    state.aiDraft?.let { draft ->
        if (draftReviewOpen) {
            ModalBottomSheet(
                onDismissRequest = { draftReviewOpen = false },
                containerColor = MaterialTheme.colorScheme.surface,
            ) {
                CanvasAiDraftReview(
                    draft = draft,
                    busy = !boardEditingEnabled,
                    showDrawOnMoment = showDraftDrawOn,
                    onEdit = viewModel::editAi,
                    onKeep = viewModel::applyAi,
                    onDiscard = viewModel::discardAiDraft,
                    onRegenerate = viewModel::regenerateAi,
                    modifier = Modifier.fillMaxWidth().imePadding().padding(horizontal = 20.dp),
                )
            }
        }
    }

    if (pairReview && state.selectedNodeIds.size == 2) {
        val pair = state.nodes.filter { it.id in state.selectedNodeIds }.take(2)
        if (pair.size == 2) AlertDialog(
            onDismissRequest = { pairReview = false },
            title = { Text(uiText("Connect selected cards?")) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(pair.joinToString(" → ") { it.readableText().take(45) })
                    HaloTextField(pairLabel, { pairLabel = it.take(120) },
                        label = { Text(uiText("Connection label (optional)")) },
                        kind = VoiceFieldKind.SHORT, maxChars = 120)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.connectPair(pair[0].id, pair[1].id, pairLabel)
                    pairReview = false
                    pairLabel = ""
                }, enabled = boardEditingEnabled) { Text(uiText("Connect")) }
            },
            dismissButton = {
                TextButton(onClick = { pairReview = false }) { Text(uiText("Cancel")) }
            }
        )
    }
    if (rename) AlertDialog(onDismissRequest = { rename = false }, title = { Text(uiText("Rename canvas")) },
        text = { HaloTextField(title, { title = it.take(160) }, singleLine = true, label = { Text(uiText("Title")) }, kind = VoiceFieldKind.SHORT, maxChars = 160) },
        confirmButton = { TextButton(enabled = title.isNotBlank(), onClick = { viewModel.rename(title); rename = false }) { Text(uiText("Save")) } },
        dismissButton = { TextButton(onClick = { rename = false }) { Text(uiText("Cancel")) } })
    if (confirmDelete) AlertDialog(onDismissRequest = { confirmDelete = false }, title = { Text(uiText("Remove selected cards?")) },
        text = { Text(uiText("Its connections will also be removed. Linked source items stay in Workspace.")) },
        confirmButton = { TextButton(onClick = { viewModel.manualSelectionAction("Clear"); confirmDelete = false }) { Text(uiText("Delete")) } },
        dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(uiText("Cancel")) } })
    if (picker) AlertDialog(onDismissRequest = { picker = false }, title = { Text(uiText("Link a workspace item")) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                HaloTextField(query, { query = it }, label = { Text(uiText("Search items")) }, singleLine = true, kind = VoiceFieldKind.SEARCH)
                val matches = remember(state.references, query) { state.references.filter { it.title.contains(query, true) } }
                LazyColumn(Modifier.heightIn(max = 320.dp)) {
                    if (matches.isEmpty()) item { Text(uiText("No matching items. Create a note or import a file in Workspace first.")) }
                    items(matches, key = { it.id.value }) { item ->
                        TextButton(onClick = { viewModel.addReference(item.id.value); picker = false; query = "" }) {
                            Column(Modifier.fillMaxWidth()) { Text(item.title.ifBlank { uiText("Untitled") }); Text(uiText(when (item.type) { com.halo.app.data.notebook.WorkspaceObjectType.CONVERSATION -> "Saved chat"; else -> item.type.name.lowercase().replaceFirstChar { it.uppercase() } }), style = MaterialTheme.typography.bodySmall) }
                        }
                    }
                }
            }
        }, confirmButton = { TextButton(onClick = { picker = false }) { Text(uiText("Done")) } })
    if (chooseConnectionLabel && connectionFrom != null && connectionTo != null) {
        fun commitConnection(label: String) {
            val from = connectionFrom
            val to = connectionTo
            if (from != null && to != null) viewModel.connectPair(from, to, label)
            endConnect()
        }
        com.halo.app.ui.zone6.Zone6ButtonSheet(stringResource(com.halo.app.R.string.z6_connect_label_title),
            { commitConnection("") }) {
            Column(Modifier.fillMaxWidth().weight(1f, fill = false).verticalScroll(rememberScrollState()).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton({ commitConnection("") }, modifier = Modifier.heightIn(min = 48.dp)) {
                        Text(stringResource(com.halo.app.R.string.z6_connect_label_none))
                    }
                    listOf("part of", "leads to", "inspired by", "needs").forEach { label ->
                        val translated = uiText(label)
                        TextButton({ commitConnection(translated) }, modifier = Modifier.heightIn(min = 48.dp)) { Text(translated) }
                    }
                }
                HaloTextField(connectionLabel, { connectionLabel = it.take(100) },
                    label = { Text(stringResource(com.halo.app.R.string.z6_connect_label_own)) },
                    singleLine = true, kind = VoiceFieldKind.SHORT, maxChars = 100)
                Button({ commitConnection(connectionLabel) }, enabled = connectionLabel.isNotBlank(),
                    modifier = Modifier.heightIn(min = 48.dp)) { Text(uiText("Done")) }
            }
        }
    }

    if (menu) {
        com.halo.app.ui.zone6.Zone6ButtonSheet(stringResource(com.halo.app.R.string.z6_sheet_more), { menu = false }) {
            Column(Modifier.fillMaxWidth().widthIn(max = 640.dp)
                    .verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(uiText("More tools"), style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(bottom = 8.dp))
                Text(stringResource(com.halo.app.R.string.zone5_group_edit), style = MaterialTheme.typography.titleSmall)
                CanvasSheetAction("Organize whole board", com.halo.app.R.drawable.ic_halo_action_organize,
                    enabled = boardEditingEnabled && state.nodes.isNotEmpty()) {
                    menu = false; viewModel.organize(wholeBoard = true)
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant,
                    modifier = Modifier.padding(vertical = 4.dp))
                Text(stringResource(com.halo.app.R.string.zone5_group_view), style = MaterialTheme.typography.titleSmall)
                if (canCustomize) TextButton(onClick = { menu = false; customize() },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                    Text(stringResource(R.string.alive_canvas_look))
                }
                CanvasSheetAction("Find on canvas", com.halo.app.R.drawable.ic_halo_action_search) {
                    menu = false; searchOpen = true; toolsExpanded = true
                }
                CanvasSheetAction("Fit all cards", com.halo.app.R.drawable.ic_halo_canvas_fit,
                    enabled = state.nodes.isNotEmpty() && !state.loading && !state.missing) {
                    menu = false; viewModel.fitToContent()
                }
                HorizontalDivider()
                Text(stringResource(com.halo.app.R.string.zone5_group_file), style = MaterialTheme.typography.titleSmall)
                CanvasSheetAction("Export to note", com.halo.app.R.drawable.ic_halo_action_export,
                    enabled = boardEditingEnabled && state.nodes.isNotEmpty()) {
                    menu = false; viewModel.exportToNote()
                }
                CanvasSheetAction("Rename", com.halo.app.R.drawable.ic_halo_action_rename,
                    enabled = boardEditingEnabled) {
                    menu = false; title = state.title; rename = true
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant,
                    modifier = Modifier.padding(vertical = 4.dp))
                Text(stringResource(com.halo.app.R.string.zone5_group_more), style = MaterialTheme.typography.titleSmall)
                CanvasSheetAction("Study this canvas", com.halo.app.R.drawable.ic_halo_tool_study,
                    enabled = boardEditingEnabled && state.nodes.isNotEmpty()) {
                    menu = false; viewModel.exportToNote(startStudy = true)
                }
                CanvasSheetAction("Think with halo", com.halo.app.R.drawable.ic_halo_cognition_think,
                    enabled = boardEditingEnabled) { menu = false; showAi = true }
                CanvasSheetAction("Ask halo to organize", com.halo.app.R.drawable.halo_ic_ai_organize,
                    enabled = boardEditingEnabled && state.nodes.isNotEmpty()) {
                    menu = false; viewModel.organize(wholeBoard = state.selectedNodeIds.isEmpty(), ai = true)
                }
                CanvasSheetAction("Create an image", com.halo.app.R.drawable.halo_ic_create_image,
                    enabled = boardEditingEnabled && viewModel.imageStudio != null) {
                    menu = false; viewModel.imageStudio?.prepare(); showImageStudio = true
                }
                state.images[selected?.refObjectId]?.let { image ->
                    CanvasSheetAction("Edit image with AI", com.halo.app.R.drawable.halo_ic_image,
                        enabled = boardEditingEnabled && viewModel.imageStudio != null) {
                        menu = false; viewModel.imageStudio?.prepare(image); showImageStudio = true
                    }
                }
                CanvasSheetAction("Reset view", com.halo.app.R.drawable.halo_ic_fit_all) {
                    menu = false; viewModel.resetViewport()
                }
                CanvasSheetAction("More tools", com.halo.app.R.drawable.halo_ic_more) {
                    menu = false; toolsExpanded = !toolsExpanded
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }

    if (toolsExpanded) ModalBottomSheet(onDismissRequest = { toolsExpanded = false },
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(vertical = 12.dp)) {

        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Text(uiText("More tools"), modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.labelMedium)
            TextButton(onClick = { toolsExpanded = false }) { Text(uiText("Done")) }
        }
        FlowRow(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box {
                HaloFocusAction(uiText("Card actions"), HaloGlyph.ACTION, enabled = selected != null && boardEditingEnabled,
                    onClick = { cardMenu = true }, active = selected != null,
                    style = if (selected != null) com.halo.app.ui.components.HaloActionStyle.PRIMARY else com.halo.app.ui.components.HaloActionStyle.SECONDARY)
                DropdownMenu(cardMenu, { cardMenu = false }) {
            if (selected != null) {
                if (selected.type == CanvasNodeType.SKETCH) {
                    TextButton(onClick = { sketchTarget = selected.id; sketchOriginal = selected.text; sketchOpen = true }) { Text(uiText("Edit sketch")) }
                    TextButton(onClick = {
                        if (!recognizing) {
                            recognizing = true
                            val snapshot = selected.text
                            val id = selected.id
                            scope.launch {
                                try {
                                    val found = CanvasSketchOcr.recognize(snapshot)
                                    viewModel.recognizedDraft(id, "Handwriting OCR", found, snapshot)
                                } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
                                catch (_: Exception) {
                                    viewModel.recognizedDraft(id, "Handwriting OCR", "")
                                } finally { recognizing = false }
                            }
                        }
                    }, enabled = !recognizing) { Text(uiText("Recognize handwriting")) }
                }
                val selectedFile = selected.refObjectId?.let { state.files[it] }
                selected.refObjectId?.let { id -> com.halo.app.ui.workspace.WorkspaceContinueButton(id, selectedFile?.version,
                    enabled = !state.importing && !state.imageToolBusy) }
                if (selectedFile?.mimeType?.startsWith("image/") == true) {
                    if (onOpenStudio == null) TextButton(onClick = {
                        cardMenu = false
                        imageToolsNodeId = selected.id
                    }) { Text(uiText("Image tools")) }
                    TextButton(onClick = {
                        cardMenu = false
                        viewModel.saveImagePalette(selected.id, selectedFile.version)
                    }, enabled = !state.importing && !state.imageToolBusy) { Text(uiText("Extract color palette")) }
                }
                if (selectedFile?.mimeType?.startsWith("image/") != true && onOpenStudio != null) {
                    selected.refObjectId?.let { state.images[it] }?.takeIf { it.type == "IMAGE" }?.let { attachment ->
                        TextButton(onClick = { cardMenu = false; onOpenStudio(attachment.noteId, attachment.id) }) {
                            Text(stringResource(com.halo.app.R.string.studio_open_in))
                        }
                    }
                }
                if (selectedFile?.mimeType == "application/pdf") {
                    TextButton(onClick = { cardMenu = false; pdfPageNodeId = selected.id },
                        enabled = !state.imageToolBusy) { Text(uiText("Extract PDF pages")) }
                }
                if (selectedFile?.mimeType == "application/pdf" || selectedFile?.mimeType?.startsWith("image/") == true) {
                    TextButton(onClick = { annotateNodeId = selected.id; annotationOriginal = selected.text; annotationVersion = selectedFile.version }) { Text(uiText("Annotate")) }
                    TextButton(onClick = {
                        val id = selected.id
                        val original = selected.text
                        recognizing = true
                        scope.launch {
                            try {
                                val text = CanvasSketchOcr.recognizeSource(context, selectedFile)
                                viewModel.recognizedDraft(id, "OCR page 1", text, original, selectedFile.version)
                            } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
                            catch (_: Exception) { viewModel.recognizedDraft(id, "OCR page 1", "") }
                            finally { recognizing = false }
                        }
                    }, enabled = !recognizing) { Text(uiText("Recognize source text (page 1)")) }
                    Text(uiText("Local OCR reads Latin script, including Latvian. Cyrillic and handwriting may be unreadable. Review against the source."), Modifier.padding(12.dp), style = MaterialTheme.typography.bodyMedium)
                }
                if (selectedFile?.mimeType?.startsWith("audio/") == true) {
                    Text(uiText("Local speech: %1\$s · %2\$s · %3\$s", uiText(memoReadiness.modelName), if (memoReadiness.language == "auto") uiText("Automatic") else memoReadiness.language, uiText(memoReadiness.phase)), Modifier.padding(12.dp))
                    Text(uiText("Use an M4A or WAV recording up to two minutes and 12 MB. If recognition is unavailable, write a transcript."), Modifier.padding(12.dp))
                    TextButton(onClick = { viewModel.transcribeFile(selected.id) },
                        enabled = !state.transcribing && memoReadiness.phase in setOf("Ready", "Installed")) { Text(uiText("Transcribe locally")) }
                    TextButton(onClick = { viewModel.writeTranscript(selected.id) }) { Text(uiText("Write transcript")) }
                }
                TextButton(onClick = { tagDialog = true }) { Text(uiText("Tag selected")) }
                TextButton(onClick = { viewModel.duplicateSelected() }) { Text(uiText("Duplicate")) }
                TextButton(onClick = { toolsExpanded = false; cardMenu = false; startConnect(selected.id) }, enabled = state.nodes.size > 1) { Text(uiText("Connect")) }
                TextButton(onClick = { viewModel.manualSelectionAction("Arrange") }) { Text(uiText("Arrange selected")) }
                TextButton(onClick = { viewModel.manualSelectionAction("Bring forward") }) { Text(uiText("Bring forward")) }
                TextButton(onClick = { viewModel.manualSelectionAction("Ungroup") }, enabled = selected.type == CanvasNodeType.GROUP) { Text(uiText("Ungroup")) }
                TextButton(onClick = { confirmDelete = true }) { Text(uiText("Remove selected cards")) }
            }
                }
            }
        }
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = viewModel::toggleSelectionMode, enabled = boardEditingEnabled) { Text(uiText(if (state.selectionMode) "Done selecting" else "Select cards")) }
            if (state.selectionMode) TextButton(onClick = viewModel::selectAll, enabled = boardEditingEnabled) { Text(uiText("Select all")) }
            if (state.selectedNodeIds.size in 1..6) {
                val selection = state.nodes.filter { it.id in state.selectedNodeIds }
                val mayContainImages = selection.any { node ->
                    node.refObjectId?.let { ref ->
                        state.files[ref]?.mimeType?.startsWith("image/") == true ||
                            state.images[ref]?.type == "IMAGE" ||
                            state.references.any { it.id.value == ref &&
                                it.type == com.halo.app.data.notebook.WorkspaceObjectType.NOTE }
                    } == true
                }
                if (mayContainImages) {
                    TextButton(onClick = { stitchPickerIds = state.selectedNodeIds.toSet() },
                        enabled = !state.imageToolBusy && !state.importing && !state.loading && !state.missing) {
                        Text(uiText("Combine images"))
                    }
                }
            }
            if (state.selectedNodeIds.isNotEmpty()) TextButton(onClick = { viewModel.organize() }, enabled = boardEditingEnabled) { Text(uiText("Organize selected")) }
            TextButton(onClick = { viewModel.undo() }, enabled = boardEditingEnabled && state.undoAvailable) { Text(uiText("Undo")) }
            state.drawingDraft?.let { draft -> TextButton(onClick = {
                if (draft.kind == "sketch") { sketchTarget = draft.nodeId; sketchOriginal = draft.original; sketchOpen = true }
                else { annotateNodeId = draft.nodeId; annotationOriginal = draft.original; annotationVersion = draft.fileVersion }
            }) { Text(uiText("Resume drawing")) } }
        }
        Text(uiText(if (state.selectedNodeIds.isEmpty()) "Tap a card to select. Hold and drag to move. Pinch to zoom; drag the background to pan." else "Selected cards: %1\$d", state.selectedNodeIds.size),
            Modifier.padding(horizontal = 16.dp), style = MaterialTheme.typography.bodySmall)
        com.halo.app.ui.components.HaloReadAloud(
            state.nodes.sortedWith(compareBy({ it.y }, { it.x })).map { it.readableText() }.filter { it.isNotBlank() }
                .plus(state.edges.mapNotNull { it.label?.takeIf(String::isNotBlank) }).joinToString("\n\n"),
            label = "Read this canvas")
        // The selected card is the explicit voice-edit target. Never dictate into
        // an arbitrary card when multiple cards are selected or one is linked.
        val voiceCard = state.nodes.firstOrNull { node ->
            node.id == state.selectedNodeId && node.refObjectId == null &&
                node.type != CanvasNodeType.SKETCH && node.type != CanvasNodeType.INK_SELECTION
        }
        if (voiceCard != null && state.selectedNodeIds.size <= 1 && !state.selectionMode) {
            Column(Modifier.fillMaxWidth().padding(horizontal=16.dp)) {
                androidx.compose.runtime.key(voiceCard.id) {
                    HaloVoiceTextActions(
                        text=voiceCard.text,
                        onText={ viewModel.updateNodeText(voiceCard.id,
                            it.take(if(voiceCard.type==CanvasNodeType.GROUP) 80 else if(voiceCard.type==CanvasNodeType.TAG) 50 else 12000)) },
                        enabled=boardEditingEnabled,
                        readText=voiceCard.text,
                        maxChars=if(voiceCard.type==CanvasNodeType.GROUP) 80 else if(voiceCard.type==CanvasNodeType.TAG) 50 else 12000,
                        appendDictation=voiceCard.type!=CanvasNodeType.GROUP && voiceCard.type!=CanvasNodeType.TAG,
                    )
                }
            }
        }
        if (searchOpen) {
            val hits = state.searchHits
            val matches = state.nodes.filter { node -> hits.any { it.nodeId == node.id } }
            Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    HaloTextField(boardSearch, { boardSearch = it.take(160) },
                        label = { Text(uiText("Search this canvas")) },
                        singleLine = true, modifier = Modifier.weight(1f), kind = VoiceFieldKind.SEARCH, maxChars = 160)
                    TextButton(onClick = { searchOpen = false; boardSearch = "" }) { Text(uiText("Close")) }
                }

                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("All", "Notes", "Files").forEach { type -> FilterChip(searchType == type, { searchType = type }, label = { Text(uiText(type)) }) }
                }
                if (boardSearch.isNotBlank()) {
                    Text(uiText("%1\$d matching cards", matches.size),
                        style = MaterialTheme.typography.bodySmall)
                    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
                        matches.forEach { node ->
                            val label = node.refObjectId?.let { id -> state.references.firstOrNull { it.id.value == id }?.title }
                                ?: when (node.type) {
                                    CanvasNodeType.SKETCH -> uiText("Sketch")
                                    CanvasNodeType.INK_SELECTION -> uiText("Ink selection")
                                    else -> CanvasFileMarkupCodec.displayTitle(node.text)
                                }
                            TextButton(onClick = {
                                if (showAsList) viewModel.selectNode(node.id) else viewModel.focusNode(node.id)
                            }) {
                                Text(label.take(28).ifBlank { uiText("Untitled card") })
                            }
                        }
                    }
                }
            }
        }
                }
    }

    Column(Modifier.fillMaxSize().background(com.halo.app.ui.theme.haloWorkspaceBackground()).then(if (shellOwnsOrb) Modifier else Modifier.imePadding())) {
        if (shellOwnsOrb) Zone5InstrumentHeading(uiText("Canvas"), "tool_canvas", state.title,
            modifier = Modifier.padding(horizontal = 20.dp))
        if (!shellOwnsOrb) EditorWorkspaceHeader(state.title, menu,
            onToggle = { menu = true }, onBack = { viewModel.leave(onBack) },
            onHalo = if (com.halo.app.ui.presence.LocalShellHaloOrb.current) null else ({ showAi = true }),
            haloEnabled = boardEditingEnabled)
        if (!state.loading && !state.missing) CanvasBoardModeSwitch(showAsList) { requestedList = it }
        if (!state.loading && !state.missing) {
            val canAct = boardEditingEnabled && !connecting
            fun addText(sticky: Boolean) {
                requestedList = false
                handMode = false
                if (state.selectionMode) viewModel.toggleSelectionMode()
                viewModel.addTextNode(sticky) { id -> autoFocusNewText = id }
            }
            HaloInstrumentBar("canvas", mapOf(
                "canvas.select" to HaloInstrumentAction({ handMode = false; viewModel.toggleSelectionMode() }, canAct, selected = state.selectionMode && !handMode),
                "canvas.hand_pan" to HaloInstrumentAction({
                    requestedList = false
                    if (state.selectionMode) viewModel.toggleSelectionMode()
                    handMode = !handMode
                }, !connecting, selected = handMode && !showAsList),
                "canvas.sticky_note" to HaloInstrumentAction({ addText(true) }, canAct, working = state.addingNode),
                "canvas.shape" to HaloInstrumentAction({ requestedList = false; handMode = false; shapeTarget = null; shapeOpen = true }, canAct),
                "canvas.text" to HaloInstrumentAction({ addText(false) }, canAct, working = state.addingNode),
                "canvas.connector" to HaloInstrumentAction({ handMode = false; startConnect() }, canAct && state.nodes.size > 1, selected = connecting),
                "canvas.pen_highlighter" to HaloInstrumentAction({ sketchTarget = null; sketchOriginal = ""; sketchOpen = true }, canAct),
                "canvas.picture_file" to HaloInstrumentAction({ filePicker.launch(arrayOf("*/*")) }, canAct, working = state.importing),
                "canvas.frame" to HaloInstrumentAction({ frameOpen = true }, canAct),
                "canvas.group_lock" to HaloInstrumentAction({
                    groupSelection = state.selectedNodeIds.ifEmpty { setOfNotNull(state.selectedNodeId) }
                }, canAct && state.selectedNodeId != null, selected = state.selectedNodeId?.let { it in lockedIds } == true),
                "canvas.comments" to HaloInstrumentAction({ reviewIndex = true }, !connecting,
                    selected = reviewIndex || pinPlacing || reviewPinId != null),
                "canvas.align_distribute" to HaloInstrumentAction({ alignOpen = state.selectedNodeIds to state.nodes.hashCode() }, canAct && state.selectedNodeIds.size >= 2),
                "canvas.properties_panel" to HaloInstrumentAction({
                    selected?.let { card ->
                        if (card.type == CanvasNodeType.SHAPE) { shapeTarget = card; shapeOpen = true }
                        else { listEditText = card.text; listEditSaveError = false; listEditCard = card }
                    }
                }, canAct && state.selectedNodeIds.size <= 1 && selected?.let { it.type == CanvasNodeType.SHAPE || it.canEditListText() } == true),
                "canvas.minimap" to HaloInstrumentAction({ minimapOpen = true }, state.nodes.isNotEmpty() && !connecting),
                "canvas.present" to HaloInstrumentAction({ presenting = true }, !connecting && state.nodes.any { it.type == CanvasNodeType.FRAME }, selected = presenting),
            ), primary = "canvas.sticky_note")
        }
        if (!shellOwnsOrb && !showAsList) Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(com.halo.app.R.string.canvas_count_zoom_summary,
                    pluralStringResource(com.halo.app.R.plurals.canvas_card_count, state.nodes.size, state.nodes.size),
                    pluralStringResource(com.halo.app.R.plurals.canvas_connection_count, state.edges.size, state.edges.size),
                    (state.viewportScale * 100).roundToInt()),
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (state.nodes.isNotEmpty()) {
                TextButton(
                    onClick = viewModel::fitToContent,
                    enabled = !state.loading && !state.missing,
                    modifier = Modifier.heightIn(min = 48.dp),
                ) { Text(uiText("Fit all cards")) }
            }
        }
        if (state.transcribing) Row(Modifier.padding(horizontal = 16.dp)) {
            CircularProgressIndicator(Modifier.size(20.dp))
            Text(uiText("Transcribing locally…"), Modifier.padding(start = 12.dp),
                style = MaterialTheme.typography.bodySmall)
            TextButton(onClick = viewModel::cancelTranscription) { Text(uiText("Cancel")) }
        }
        if (recognizing) Row(Modifier.padding(horizontal = 16.dp)) {
            CircularProgressIndicator(Modifier.size(20.dp))
            Text(uiText("Recognizing handwriting…"), Modifier.padding(start = 12.dp),
                style = MaterialTheme.typography.bodySmall)
        }
        if (state.imageToolBusy) Row(Modifier.padding(horizontal = 16.dp)) {
            CircularProgressIndicator(Modifier.size(20.dp))
            Text(uiText("Processing image locally…"), Modifier.padding(start = 12.dp),
                style = MaterialTheme.typography.bodySmall)
        }
        if (state.importing) Row(Modifier.padding(horizontal = 16.dp)) {
            CircularProgressIndicator(Modifier.size(20.dp))
            Text(uiText("Importing into Workspace…"), Modifier.padding(start = 12.dp), style = MaterialTheme.typography.bodySmall)
        }
        if (!shellOwnsOrb && state.selectionMode && state.selectedNodeIds.size == 2 && boardEditingEnabled)
            TextButton(onClick = { pairReview = true },
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                Text(uiText("Connect selected cards"))
            }
        if (state.aiBusy) Row(Modifier.padding(horizontal = 16.dp)) { Text(uiText("halo is thinking..."), Modifier.weight(1f)); TextButton(onClick = viewModel::discardAi) { Text(uiText("Cancel")) } }
        val selectedSignature = state.selectedNodeIds.sorted().joinToString("|")
        if (state.selectionMode && state.selectedNodeIds.size == 4 &&
            state.organization == null && boardEditingEnabled &&
            selectedSignature != dismissedGroupSuggestion) {
            Surface(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                shape = com.halo.app.ui.design.HaloShapes.card,
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
                FlowRow(Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(uiText("Group these four cards?"),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.align(Alignment.CenterVertically))
                    TextButton(onClick = { viewModel.organize(wholeBoard = false, ai = false) },
                        modifier = Modifier.heightIn(min = 48.dp)) {
                        Text(uiText("Review group"))
                    }
                    TextButton(onClick = { dismissedGroupSuggestion = selectedSignature },
                        modifier = Modifier.heightIn(min = 48.dp)) {
                        Text(uiText("Not now"))
                    }
                }
            }
        }
        state.notice?.let { notice ->
            Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(uiText(notice), Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                TextButton(onClick = viewModel::clearNotice) { Text(uiText("Dismiss")) }
            }
        }
        if (state.exporting) HaloProgressPresentation(uiText("Saving to Notebook…"), Modifier.padding(horizontal = 16.dp))
        if (connecting) {
            val source = state.nodes.firstOrNull { it.id == connectionFrom }
            val hint = if (source == null) uiText("Choose the first card")
                else stringResource(com.halo.app.R.string.z6_connect_hint, source.readableText().take(60).ifBlank { uiText("Untitled card") })
            Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = MaterialTheme.shapes.medium,
                modifier = Modifier.fillMaxWidth().padding(8.dp).semantics {
                    liveRegion = androidx.compose.ui.semantics.LiveRegionMode.Polite
                }) {
                Text(hint, Modifier.padding(12.dp), color = MaterialTheme.colorScheme.onPrimaryContainer)
            }
            if (showAsList) Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.End) {
                TextButton(onClick = ::endConnect, modifier = Modifier.heightIn(min = 48.dp)) { Text(uiText("Cancel")) }
                Button(onClick = {
                    connectionLabel = ""
                    chooseConnectionLabel = true
                }, enabled = boardEditingEnabled && connectionFrom != null && connectionTo != null,
                    modifier = Modifier.heightIn(min = 48.dp)) { Text(uiText("Done")) }
            }
        }
        Box(Modifier.weight(1f).fillMaxWidth().clipToBounds()) {
        if (showAsList && !state.loading && !state.missing) {
            if (state.nodes.isEmpty()) CanvasEmptyStarter(
                modifier = Modifier.align(Alignment.Center).padding(24.dp),
                enabled = boardEditingEnabled,
                onText = { viewModel.addTextNode() },
                onImport = { insertMenu = true },
                onHalo = { showAi = true }
            ) else CanvasBoardList(state, if (connecting) setOfNotNull(connectionFrom, connectionTo) else null,
                boardEditingEnabled,
                onSelect = { id ->
                    if (connecting) { if (boardEditingEnabled) chooseConnectionNode(id) }
                    else if (state.nodes.any { it.id == id && it.type == CanvasNodeType.COMMENT }) reviewPinId = id
                    else viewModel.selectNode(id)
                },
                onEdit = { node ->
                    listEditText = node.text
                    listEditSaveError = false
                    listEditCard = node
                },
                onCardActions = { id ->
                    if (viewModel.uiState.value.canEditBoard()) {
                        viewModel.selectNode(id)
                        toolsExpanded = true
                        cardMenu = true
                    }
                },
                onOpenSource = { viewModel.openReference(it) },
                appearance = appearance,
                modifier = Modifier.fillMaxSize()
            )
        } else Box(Modifier.matchParentSize().haloGestureAnchor(HaloGesture.CANVAS_LINK).onSizeChanged {
            boardWidth = it.width; boardHeight = it.height
            viewModel.measureViewport(it.width.toFloat(), it.height.toFloat(), density.density)
        }.pointerInput(viewModel, connecting, density, state.selectionMode, handMode) {
            if (connecting) {
                var origin: String? = null
                fun cardAt(position: Offset): String? {
                    val board = viewModel.uiState.value
                    if (!board.canEditBoard()) return null
                    val scale = board.viewportScale
                    val point = with(density) { Offset(((position.x - board.viewportX) / scale).toDp().value,
                        ((position.y - board.viewportY) / scale).toDp().value) }
                    return board.nodes.filter { card ->
                        val x = cardMotion[card.id]?.x?.value ?: card.x
                        val y = cardMotion[card.id]?.y?.value ?: card.y
                        point.x in x..(x + card.width) && point.y in y..(y + card.height)
                    }.maxByOrNull { it.zIndex }?.id
                }
                detectDragGestures(
                    onDragStart = { position ->
                        origin = cardAt(position)?.takeIf { connectionFrom == null || connectionFrom == it }
                        origin?.let { source ->
                            connectionFrom = source
                            connectionTo = null
                            connectionPointer = position
                            currentGuidance?.gestureUsed(HaloGesture.CANVAS_LINK)
                        }
                    },
                    onDragEnd = {
                        val source = origin
                        val target = connectionPointer?.let(::cardAt)
                        if (source != null && target != source && target != null) chooseConnectionNode(target)
                        connectionPointer = null
                        origin = null
                    },
                    onDragCancel = {
                        connectionPointer = null
                        origin = null
                    },
                ) { change, _ ->
                    if (origin != null) {
                        change.consume()
                        connectionPointer = change.position
                    }
                }
            } else if (state.selectionMode && !handMode) detectDragGestures(
                onDragStart = { point -> selectionStart = point; selectionEnd = point },
                onDragEnd = {
                    val from = selectionStart
                    val to = selectionEnd
                    if (from != null && to != null) {
                        val view = viewModel.uiState.value
                        val scale = view.viewportScale * density.density
                        viewModel.selectInBounds((minOf(from.x, to.x) - view.viewportX) / scale,
                            (minOf(from.y, to.y) - view.viewportY) / scale, (maxOf(from.x, to.x) - view.viewportX) / scale,
                            (maxOf(from.y, to.y) - view.viewportY) / scale)
                    }
                    selectionStart = null; selectionEnd = null
                }, onDragCancel = { selectionStart = null; selectionEnd = null },
            ) { change, _ -> change.consume(); selectionEnd = change.position }
            else detectTransformGestures { center, pan, zoom, _ ->
                viewModel.transform(pan.x, pan.y, zoom, center.x, center.y)
            }
        }) {
            Canvas(Modifier.fillMaxSize()) {
                // Round 2: dots anchored in board coordinates, not graph-paper lines.
                val spacing = (32.dp.toPx() * state.viewportScale).coerceAtLeast(8.dp.toPx())
                val dotRadius = 1.dp.toPx()
                var x = ((state.viewportX % spacing) + spacing) % spacing
                while (x < size.width) {
                    var y = ((state.viewportY % spacing) + spacing) % spacing
                    while (y < size.height) {
                        drawCircle(gridColor.copy(alpha = 0.42f), dotRadius, Offset(x, y))
                        y += spacing
                    }
                    x += spacing
                }
                val nodes = state.nodes.associateBy { it.id }
                fun anchor(node: CanvasNode): Offset {
                    val position = cardMotion[node.id]
                    return Offset(
                        ((position?.x?.value ?: node.x) + node.width / 2).dp.toPx() * state.viewportScale + state.viewportX,
                        ((position?.y?.value ?: node.y) + node.height / 2).dp.toPx() * state.viewportScale + state.viewportY,
                    )
                }
                if (connecting) {
                    val start = connectionFrom?.let(nodes::get)?.let(::anchor)
                    val end = connectionTo?.let(nodes::get)?.let(::anchor) ?: connectionPointer
                    if (start != null && end != null) drawLine(guideTone, start, end, 2.dp.toPx(),
                        pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(8.dp.toPx(), 6.dp.toPx())))
                }
                for (edge in state.edges) {
                    val from = nodes[edge.fromNodeId] ?: continue
                    val to = nodes[edge.toNodeId] ?: continue
                    val start = anchor(from); val end = anchor(to)
                    drawCanvasLink(start, end, edgeColor, appearance.links)
                    drawCircle(edgeColor, 3.5.dp.toPx(), start); drawCircle(edgeColor, 3.5.dp.toPx(), end)
                }
            }
            when {
                state.loading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                state.missing -> HaloEmptyState(uiText("Canvas unavailable"), uiText("Return to Notebook to choose another canvas. Your other items are unchanged."), HaloGlyph.NOTE,
                    modifier = Modifier.align(Alignment.Center))
                state.nodes.isEmpty() -> CanvasEmptyStarter(
                    modifier = Modifier.align(Alignment.Center).padding(24.dp),
                    enabled = boardEditingEnabled,
                    onText = { viewModel.addTextNode() },
                    onImport = { insertMenu = true },
                    onHalo = { showAi = true }
                )
            }
            val previewCandidates = state.nodes.mapNotNull { node ->
                when (node.type) {
                    CanvasNodeType.GROUP, CanvasNodeType.FRAME, CanvasNodeType.SHAPE, CanvasNodeType.TAG, CanvasNodeType.SKETCH, CanvasNodeType.INK_SELECTION -> return@mapNotNull null
                    else -> Unit
                }
                val ref = node.refObjectId ?: return@mapNotNull null
                val source = canvasMediaSource(state.images[ref], state.files[ref])
                if (source.attachment == null && source.file?.isCanvasPreviewAvailable() != true)
                    return@mapNotNull null
                val motion = cardMotion[node.id]
                CanvasPreviewCandidate(node.id,
                    with(density) { ((motion?.x?.value ?: node.x).dp * state.viewportScale).toPx() } + state.viewportX,
                    with(density) { ((motion?.y?.value ?: node.y).dp * state.viewportScale).toPx() } + state.viewportY,
                    with(density) { (node.width * state.viewportScale).dp.toPx() },
                    with(density) { (node.height * state.viewportScale).dp.toPx() }, node.zIndex)
            }
            val admittedPreviews = admittedCanvasPreviews(previewCandidates, boardWidth.toFloat(),
                boardHeight.toFloat(), requestedPreviewId ?: state.selectedNodeId)
            state.nodes.sortedWith(compareBy<CanvasNode> { !it.type.isCanvasContainer() }
                .thenBy { it.zIndex }).forEach { node ->
                key(node.id) {
                    val position = cardMotion[node.id]
                    val x = with(density) { ((position?.x?.value ?: node.x).dp * state.viewportScale).toPx() } + state.viewportX
                    val y = with(density) { ((position?.y?.value ?: node.y).dp * state.viewportScale).toPx() } + state.viewportY
                    val width = node.width * state.viewportScale
                    val height = node.height * state.viewportScale
                    val visible = x + with(density) { width.dp.toPx() } >= 0 && y + with(density) { height.dp.toPx() } >= 0 && x <= boardWidth && y <= boardHeight
                    val cardSelected = if (connecting) node.id == connectionFrom || node.id == connectionTo
                        else if (state.selectionMode) node.id in state.selectedNodeIds else node.id == state.selectedNodeId
                    val selectCard: () -> Unit = {
                        if (node.type == CanvasNodeType.COMMENT) reviewPinId = node.id
                        else if (connecting) chooseConnectionNode(node.id)
                        else viewModel.selectNode(node.id)
                    }
                    val selectLabel = stringResource(if (state.selectionMode && cardSelected) com.halo.app.R.string.canvas_deselect_card else com.halo.app.R.string.canvas_select_card)
                    if (visible && !state.loading && !state.missing) CanvasCardFrame(
                        width = node.width, height = node.height, scale = state.viewportScale,
                        modifier = Modifier.offset { IntOffset(x.roundToInt(), y.roundToInt()) }.pointerInput(node.id, state.viewportScale, boardEditingEnabled, connecting, appearance.snap, handMode, node.id in lockedIds) {
                                if (boardEditingEnabled && !connecting && !handMode && node.id !in lockedIds) detectDragGesturesAfterLongPress(
                                    onDragStart = { viewModel.beginDrag(node.id) },
                                    onDragEnd = { if (appearance.snap) viewModel.snapMovedCard(node.id) },
                                ) { change, drag ->
                                    change.consume()
                                    viewModel.moveNode(node.id, with(density) { drag.x.toDp().value }, with(density) { drag.y.toDp().value })
                                }
                            },
                    ) {
                    Surface(
                        modifier = Modifier.fillMaxSize().semantics {
                            this.selected = cardSelected
                            if (!handMode) onClick(label = selectLabel) { selectCard(); true }
                        },
                        onClick = { if (!handMode) selectCard() },
                        shape = com.halo.app.ui.design.HaloShapes.card,
                        color = if (node.type == CanvasNodeType.SHAPE) androidx.compose.ui.graphics.Color.Transparent else canvasCardColor(appearance, node,
                            node.refObjectId?.let { it in state.images || state.files[it]?.mimeType?.startsWith("image/") == true } == true),
                        border = if (node.type == CanvasNodeType.SHAPE && !cardSelected) null else BorderStroke(
                            if (cardSelected) 2.dp else 1.dp,
                            if (cardSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant),
                    ) {
                        if (node.type == CanvasNodeType.COMMENT) {
                            CanvasReviewPinIcon(CanvasReviewPin.decode(node.text), Modifier.fillMaxSize())
                        } else if (node.type == CanvasNodeType.SHAPE) {
                            CanvasVisualShapeContent(CanvasVisualShape.decode(node.text) ?: CanvasVisualShape(), Modifier.fillMaxSize())
                        } else if (node.type.isCanvasContainer() || node.type == CanvasNodeType.TAG) Column(
                            Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(uiText(when (node.type) {
                                CanvasNodeType.GROUP -> "Group title"
                                CanvasNodeType.FRAME -> "Frame"
                                else -> "Tag card"
                            }),
                                style = MaterialTheme.typography.labelSmall)
                            if (node.id in lockedIds) Text(uiText("Position locked"), style = MaterialTheme.typography.labelSmall)
                            if (state.selectionMode || handMode) Text(node.text, style = MaterialTheme.typography.titleMedium)
                            else BasicTextField(node.text, { viewModel.updateNodeText(node.id, it.take(if (node.type.isCanvasContainer()) 80 else 50)) },
                                readOnly = !boardEditingEnabled,
                                textStyle = MaterialTheme.typography.titleMedium.copy(color = MaterialTheme.colorScheme.onSurface),
                                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary))
                            if (node.type == CanvasNodeType.GROUP) Text(
                                uiText("Drag this zone to move enclosed cards."),
                                style = MaterialTheme.typography.bodySmall)
                        }
                        else if (node.type == CanvasNodeType.INK_SELECTION) Column(
                            Modifier.fillMaxSize().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            val sourceNode = state.edges.firstOrNull { it.toNodeId == node.id && it.label == "selection" }
                                ?.fromNodeId?.let { sourceId -> state.nodes.firstOrNull { it.id == sourceId } }
                            val sourceVersion = sourceNode?.refObjectId?.let { id -> state.files[id]?.version ?: state.files[id]?.sha256 }
                            val selectionCurrent = node.inkSelectionIsCurrent(sourceNode, viewModel.canvasId, sourceVersion)
                            val selectionCanRender = selectionCurrent && sourceNode?.type in
                                setOf(CanvasNodeType.SKETCH, CanvasNodeType.FILE, CanvasNodeType.IMAGE)
                            val selectionContext = node.inkSelectionContext().takeIf { selectionCurrent }
                            val selectedInkMarkup = if (sourceNode != null && selectionContext != null) when (sourceNode.type) {
                                CanvasNodeType.SKETCH -> sourceNode.text
                                CanvasNodeType.FILE, CanvasNodeType.IMAGE -> selectionContext.pageId
                                    ?.removePrefix("page-")?.toIntOrNull()?.let { page ->
                                        CanvasFileMarkupCodec.decode(sourceNode.text).pages[page]
                                    }
                                else -> null
                            } else null
                            Text(uiText("Ink selection"), style = MaterialTheme.typography.labelSmall)
                            Text(uiText("%1\$d strokes", node.inkSelectionStrokeCount() ?: 0),
                                style = MaterialTheme.typography.bodyMedium)
                            if (selectionContext != null && selectedInkMarkup != null) {
                                Text(uiText(if (sourceNode?.type == CanvasNodeType.SKETCH) "Selected marks preview" else "Selected page marks preview (source page not shown)"),
                                    style = MaterialTheme.typography.labelSmall)
                                CanvasSketchPreview(selectedInkMarkup, Modifier.fillMaxWidth().height(104.dp), selectionContext)
                            }
                            Text(uiText(if (selectionCurrent) "Selection source is unchanged." else "Source changed; select the region again."),
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            if (selectionCurrent) Text(uiText("Working set uses this region reference instead of the whole Canvas; marks stay local and unrecognized."),
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            TextButton(onClick = {
                                sourceNode?.let { source ->
                                    if (source.type == CanvasNodeType.SKETCH) {
                                        viewModel.selectNode(source.id); sketchTarget = source.id
                                        sketchOriginal = source.text; sketchOpen = true
                                    } else viewModel.openReference(source.id)
                                }
                            }, enabled = sourceNode != null) { Text(uiText("Open linked source")) }
                            TextButton(onClick = { viewModel.addInkSelectionToWorkingSet(node.id) },
                                enabled = selectionCurrent) { Text(uiText("Add selection to working set")) }
                            TextButton(onClick = { viewModel.askAboutInkSelection(node.id) },
                                enabled = selectionCurrent) { Text(uiText("Ask about selection")) }
                            val studyMaterialTitle = uiText("Selected ink region")
                            val studyGoal = uiText("Study the selected region")
                            TextButton(onClick = { viewModel.studyInkSelection(
                                node.id,
                                studyMaterialTitle,
                                studyGoal) },
                                enabled = selectionCanRender) { Text(uiText("Study this selection")) }
                        }
                        else if (node.type == CanvasNodeType.SKETCH) Column(Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(uiText("Sketch"), style = MaterialTheme.typography.labelSmall)
                            val remoteSession = remoteSessions.firstOrNull { it.boardId == viewModel.canvasId && it.documentId == node.id }
                            CanvasSketchPreview(node.text, Modifier.fillMaxWidth().weight(1f),
                                ephemeralStroke = remoteSession?.let { remotePreviews[it.sessionId] })
                            TextButton(onClick = { viewModel.selectNode(node.id); sketchTarget = node.id; sketchOriginal = node.text; sketchOpen = true }) {
                                Text(uiText("Edit sketch"))
                            }
                        }
                        else if (node.refObjectId == null && (state.selectionMode || handMode)) {
                            CanvasTextCardDecoration(node.text, Modifier.fillMaxSize().padding(14.dp)) {
                                Text(node.text, style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                        else if (node.refObjectId == null) {
                            val textFocus = remember(node.id) { FocusRequester() }
                            LaunchedEffect(autoFocusNewText, state.selectedNodeId, state.addingNode) {
                                if (autoFocusNewText == node.id && state.selectedNodeId == node.id && !state.addingNode) {
                                    textFocus.requestFocus()
                                    autoFocusNewText = null
                                }
                            }
                            BasicTextField(
                            value = node.text,
                            readOnly = !boardEditingEnabled,
                            onValueChange = { viewModel.updateNodeText(node.id, it) },
                            modifier = Modifier.fillMaxSize().padding(14.dp).focusRequester(textFocus)
                                .onFocusChanged { if (it.isFocused && !state.selectionMode) viewModel.selectNode(node.id) },
                            textStyle = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurface),
                            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                            decorationBox = { inner -> CanvasTextCardDecoration(node.text, content = inner) },
                            )
                        }
                        else if (state.images[node.refObjectId] != null || state.files[node.refObjectId]?.isCanvasVisualSource() == true) {
                            val reference = state.references.firstOrNull { it.id.value == node.refObjectId }
                            val attachment = state.images[node.refObjectId]
                            val file = state.files[node.refObjectId]
                            CanvasMediaCard(reference?.title ?: attachment?.displayName ?: file?.displayName ?: uiText("Image"),
                                attachment, file, node.text, previewEnabled = node.id in admittedPreviews,
                                canOpenSource = reference != null,
                                onSelect = { requestedPreviewId = node.id; viewModel.selectNode(node.id) },
                                onOpenSource = { viewModel.openReference(node.id) })
                        }
                        else Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            val reference = state.references.firstOrNull { it.id.value == node.refObjectId }
                            val localFile = state.files[node.refObjectId]
                            Text(uiText(when {
                                state.images[node.refObjectId] != null || localFile?.mimeType?.startsWith("image/") == true -> "Image"
                                localFile?.mimeType?.startsWith("audio/") == true -> "Audio file"
                                localFile?.mimeType == "application/pdf" -> "PDF document"
                                else -> reference?.let(::workspaceTypeLabel) ?: "Source unavailable"
                            }), style = MaterialTheme.typography.labelSmall)
                            Text(reference?.title ?: CanvasFileMarkupCodec.displayTitle(node.text), style = MaterialTheme.typography.titleSmall, maxLines = 1)
                            if (localFile?.mimeType?.startsWith("audio/") == true) {
                                CanvasLocalAudioPlayer(localFile)
                            }
                            TextButton(onClick = { viewModel.openReference(node.id) }, enabled = reference != null) { Text(uiText("Open source")) }
                        }
                    }
                    }
                }
            }
        }
        if (pinPlacing && boardEditingEnabled) CanvasPickPin(
            onPosition = { position ->
                val board = viewModel.uiState.value
                val scale = (board.viewportScale * density.density).coerceAtLeast(.1f)
                pinPosition = Offset((position.x - board.viewportX) / scale, (position.y - board.viewportY) / scale)
                pinPlacing = false
            }, onCancel = { pinPlacing = false }, modifier = Modifier.matchParentSize(),
        )
        val selectionInk = MaterialTheme.colorScheme.primary
        if (selectionStart != null && selectionEnd != null && state.selectionMode) Canvas(Modifier.matchParentSize()) {
            val from = selectionStart ?: return@Canvas
            val to = selectionEnd ?: return@Canvas
            val origin = Offset(minOf(from.x, to.x), minOf(from.y, to.y))
            val extent = androidx.compose.ui.geometry.Size(kotlin.math.abs(from.x - to.x), kotlin.math.abs(from.y - to.y))
            drawRect(selectionInk.copy(alpha = .10f), origin, extent)
            drawRect(selectionInk, origin, extent, style = androidx.compose.ui.graphics.drawscope.Stroke(2.dp.toPx()))
        }
        // An unapproved draft is a board overlay, never a persisted CanvasNode.
        // It stays visible when the editor sheet is dismissed and disappears
        // immediately after an explicit Keep or Discard.
        if (showArrangeMoment) {
            HaloFxAnimation(
                moment = HaloFxMoment.CANVAS_ARRANGE,
                modifier = Modifier.align(Alignment.TopCenter)
                    .padding(top = 16.dp).size(112.dp),
            )
        }
        if (state.aiDraft != null && !draftReviewOpen) {
            CanvasInlineDraftCard(
                draft = state.aiDraft!!,
                busy = !boardEditingEnabled,
                modifier = Modifier.align(Alignment.Center).padding(12.dp),
                onReview = { draftReviewOpen = true },
                onKeep = viewModel::applyAi,
                onDiscard = viewModel::discardAiDraft,
            )
        }
        }
        if (selected != null && state.edges.any { it.fromNodeId == selected.id || it.toNodeId == selected.id }) {
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
                state.edges.filter { it.fromNodeId == selected.id || it.toNodeId == selected.id }.forEach { edge ->
                    val other = state.nodes.firstOrNull { it.id == if (edge.fromNodeId == selected.id) edge.toNodeId else edge.fromNodeId }
                    TextButton(onClick = { viewModel.removeConnection(edge.id) }, enabled = boardEditingEnabled) {
                        Text(uiText("Remove") + " " + (edge.label ?: other?.readableText()?.take(24) ?: uiText("connection")))
                    }
                }
            }
        }
        // Editing commands are not actionable while a file, model draft or export
        // is mutating the board. Selection/viewport are preserved on suspension.
        if (!shellOwnsOrb) CanvasBrandDock(enabled = boardEditingEnabled, haloThinking = state.aiBusy,
            selecting = state.selectionMode, onSelect = viewModel::toggleSelectionMode,
            onText = { viewModel.addTextNode() },
            onSketch = { sketchTarget = null; sketchOriginal = ""; sketchOpen = true },
            onLink = { picker = true }, onGroup = { groupDialog = true },
            onImport = { filePicker.launch(arrayOf("*/*")) }, onHalo = { showAi = true })
    }
}

/** Selection uses ordinary Text so the card Surface receives body taps without opening an editor. */
@Composable
private fun CanvasTextCardDecoration(text: String, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Icon(painterResource(com.halo.app.R.drawable.halo_ic_card), contentDescription = null,
                modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
            Text(uiText("Text"), style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Box {
            if (text.isBlank()) Text(uiText("Write an idea..."), color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium)
            content()
        }
    }
}

/** A card glides only when an approved organization really changed its position.
 * Never interpolate drag, zoom, pan, imported content or reduced-motion screens.
 */
internal fun canvasCardShouldGlide(
    id: String,
    organizingNodeIds: Set<String>,
    reducedMotion: Boolean,
): Boolean = !reducedMotion && id in organizingNodeIds

private class CanvasCardMotion(x: Float, y: Float) {
    val x = Animatable(x)
    val y = Animatable(y)
}

/** A preview, replay or restored editor must never trigger a saved-arrangement FX. */
internal fun canvasArrangeShouldPlay(previousRevision: Long, revision: Long): Boolean =
    revision > previousRevision && previousRevision >= 0L

/** Only a newly generated provisional proposal receives the supplied Draw-on.
 * Review reopening, editing and restoring an existing draft are not new events.
 */
internal fun canvasDraftDrawOnShouldPlay(
    previousRevision: Long, nextRevision: Long, draftAvailable: Boolean,
): Boolean = draftAvailable && nextRevision > previousRevision

/**
 * DevKit v1.2 Round 2: an empty board should invite a real first action, not
 * leave users to discover the toolbar before they can start.
 */
@Composable
internal fun CanvasEmptyStarter(
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onText: () -> Unit,
    onImport: () -> Unit,
    onHalo: () -> Unit,
) {
    Column(
        modifier = modifier.widthIn(max = 360.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (!com.halo.app.ui.presence.LocalShellHaloOrb.current) com.halo.app.ui.presence.HaloSignalFace(
            presentation = com.halo.app.ui.presence.HaloSignalPresentation(
                label = "Ready", reducedMotion = true),
            size = 80.dp, contentDescription = uiText("Think with halo"),
        )
        Text(uiText("Make room for your ideas"), style = MaterialTheme.typography.titleMedium)
        Text(
            uiText("Add cards, link your notes and connect what belongs together."),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (!com.halo.app.ui.presence.LocalShellHaloOrb.current) com.halo.app.ui.components.HaloActionButton(onClick = onText, enabled = enabled,
            modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
            Text(uiText("Add text card"))
        }
        if (!com.halo.app.ui.presence.LocalShellHaloOrb.current) OutlinedButton(onClick = onImport, enabled = enabled,
            modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
            Text(stringResource(com.halo.app.R.string.brand_canvas_import_choice))
        }
        if (!com.halo.app.ui.presence.LocalShellHaloOrb.current) OutlinedButton(onClick = onHalo, enabled = enabled,
            modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
            Text(uiText("Think with halo"))
        }
    }
}

/** The tool dock is icon-only before translated labels become unreadably truncated. */
internal fun canvasDockLabelsVisible(fontScale: Float): Boolean = fontScale < 1.3f

@Composable
internal fun CanvasBrandDock(enabled: Boolean, selecting: Boolean, onSelect: () -> Unit,
    onText: () -> Unit, onSketch: () -> Unit, onLink: () -> Unit, onGroup: () -> Unit,
    onImport: () -> Unit, onHalo: () -> Unit, haloThinking: Boolean = false) {
    val showLabels = com.halo.app.ui.presence.LocalShellHaloOrb.current || canvasDockLabelsVisible(LocalDensity.current.fontScale)
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth(),
        shape = com.halo.app.ui.design.HaloShapes.hero,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        shadowElevation = 2.dp,
    ) {
        Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Row(Modifier.weight(1f).horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                val actions = listOf(
                    Triple(if (selecting) "Done selecting" else "Select", com.halo.app.R.drawable.ic_halo_action_select, onSelect),
                    Triple("Text", com.halo.app.R.drawable.ic_halo_office_edit_text, onText),
                    Triple("Sketch", com.halo.app.R.drawable.ic_halo_studio_draw, onSketch),
                    Triple("Link", com.halo.app.R.drawable.ic_halo_action_link, onLink),
                    Triple("Group", com.halo.app.R.drawable.ic_halo_studio_group, onGroup),
                    Triple("Import", com.halo.app.R.drawable.ic_halo_action_import, onImport))
                actions.forEachIndexed { index, (label, icon, action) ->
                    val active = selecting && index == 0
                    val spokenLabel = uiText(label)
                    val visualLabel = if (active) "Done" else label
                    Surface(onClick = action, enabled = enabled,
                        modifier = Modifier.heightIn(min = 56.dp)
                            .semantics { contentDescription = spokenLabel },
                        shape = com.halo.app.ui.design.HaloShapes.pill,
                        color = if (active) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f)
                            else MaterialTheme.colorScheme.surfaceContainerLow,
                        contentColor = if (active) MaterialTheme.colorScheme.onPrimaryContainer
                            else MaterialTheme.colorScheme.onSurface,
                        border = BorderStroke(1.dp, if (active) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.outlineVariant)) {
                        Column(Modifier.widthIn(min = if (showLabels) 60.dp else 48.dp).padding(8.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Icon(painterResource(icon), contentDescription = null, modifier = Modifier.size(24.dp))
                            if (showLabels) {
                                Text(uiText(visualLabel), style = MaterialTheme.typography.labelSmall,
                                    maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
            }
            if (!com.halo.app.ui.presence.LocalShellHaloOrb.current) com.halo.app.ui.presence.HaloSignalFace(com.halo.app.ui.presence.HaloSignalPresentation(
                    state = if (haloThinking) com.halo.app.ui.presence.HaloSignalState.THINKING else com.halo.app.ui.presence.HaloSignalState.IDLE,
                    label = if (haloThinking) "Thinking" else "Ready"),
                size = 58.dp, contentDescription = uiText("Think with halo"), onClick = if (enabled) onHalo else null)
        }
    }
}

/** Round 2 page 39: all canvas-level actions live in one scannable bottom sheet. */
@Composable
internal fun CanvasSheetAction(
    label: String,
    icon: Int,
    enabled: Boolean = true,
    accent: Boolean = false,
    onClick: () -> Unit,
) {
    Surface(onClick = onClick, enabled = enabled,
        modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
        shape = com.halo.app.ui.design.HaloShapes.card,
        color = if (accent) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.20f)
            else MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, if (accent) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.outlineVariant)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Icon(painterResource(icon), contentDescription = null,
                modifier = Modifier.size(22.dp), tint = MaterialTheme.colorScheme.primary)
            Text(uiText(label), modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface)
        }
    }
}

/** Round 2 Canvas: the AI task is one mutually exclusive choice.
 * Selecting a different task never runs inference or commits a draft.
 */
internal val canvasAiActions = listOf("Review", "Expand", "Rewrite")

/** Linked source cards are owned by their original Notebook editors; only
 * an editable, nonempty Canvas TEXT card can receive a proposed rewrite.
 */
internal fun canvasRewriteIsAvailable(
    type: CanvasNodeType?, linkedSource: Boolean, text: String?,
): Boolean = type == CanvasNodeType.TEXT && !linkedSource && !text.isNullOrBlank()

@Composable
internal fun CanvasAiActionPicker(
    selected: String,
    onAction: (String) -> Unit,
    rewriteAvailable: Boolean = true,
) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = com.halo.app.ui.design.HaloShapes.pill,
        color = scheme.surfaceContainer,
        border = BorderStroke(1.dp, scheme.outlineVariant),
    ) {
        Row(Modifier.fillMaxWidth().padding(4.dp).selectableGroup(),
            horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            canvasAiActions.forEach { action ->
                val isSelected = action == selected
                Surface(
                    modifier = Modifier.weight(1f).heightIn(min = 48.dp)
                        .selectable(
                            selected = isSelected,
                            enabled = action != "Rewrite" || rewriteAvailable,
                            role = Role.RadioButton,
                            onClick = { onAction(action) },
                        ),
                    shape = com.halo.app.ui.design.HaloShapes.pill,
                    color = if (isSelected) scheme.primaryContainer
                        else androidx.compose.ui.graphics.Color.Transparent,
                ) {
                    Box(Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 8.dp),
                        contentAlignment = Alignment.Center) {
                        Text(uiText(action), style = MaterialTheme.typography.labelMedium,
                            color = if (action == "Rewrite" && !rewriteAvailable)
                                scheme.onSurfaceVariant.copy(alpha = .55f)
                            else if (isSelected) scheme.onPrimaryContainer else scheme.onSurface,
                            maxLines = 2,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                    }
                }
            }
        }
    }
}

// Round 2: Keep is the ONLY persistence action. A drag-dismiss merely collapses
// the sheet to a reopenable board banner; Discard is never inferred from dismissal.
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
internal fun CanvasAiDraftReview(
    draft: CanvasAiDraft,
    busy: Boolean,
    onEdit: (String) -> Unit,
    onKeep: () -> Unit,
    onDiscard: () -> Unit,
    onRegenerate: () -> Unit,
    modifier: Modifier = Modifier,
    showDrawOnMoment: Boolean = false,
) {
    Column(
        modifier.navigationBarsPadding().padding(bottom = 12.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        if (showDrawOnMoment) {
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                HaloFxAnimation(HaloFxMoment.DRAFT_DRAWON, modifier = Modifier.size(92.dp))
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(painterResource(com.halo.app.R.drawable.halo_ic_ai_draft),
                contentDescription = null, modifier = Modifier.size(28.dp),
                tint = MaterialTheme.colorScheme.primary)
            Column(Modifier.weight(1f)) {
                Text(uiText("Review AI draft"), style = MaterialTheme.typography.titleLarge)
                Text(uiText(if (draft.nodeId == null) "Add as card" else "Apply to card"),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(uiText("Proposed by halo · not saved"),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary)
            }
        }
        Surface(
            modifier = Modifier.fillMaxWidth().haloDraftBorder(
                visible = !showDrawOnMoment, color = MaterialTheme.colorScheme.primary),
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            shape = com.halo.app.ui.design.HaloShapes.card,
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(uiText("Review or edit before applying."),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                HaloTextField(
                    value = draft.text,
                    onValueChange = onEdit,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 152.dp, max = 300.dp),
                    enabled = !busy,
                    minLines = 4, kind = VoiceFieldKind.TEXT)
            }
        }
        OutlinedButton(
            onClick = onRegenerate, enabled = !busy,
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
        ) { Text(uiText("Regenerate")) }
        // NB05: Preserve explicit Keep/Discard semantics; stack touch targets
        // at high system font scales rather than squeezing their labels.
        FlowRow(Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            maxItemsInEachRow = if (LocalDensity.current.fontScale >= 1.5f) 1 else 2) {
            TextButton(onClick = onDiscard, enabled = !busy,
                modifier = Modifier.weight(1f).heightIn(min = 52.dp)) {
                Text(uiText("Discard"))
            }
            com.halo.app.ui.components.HaloActionButton(onClick = onKeep, enabled = !busy && draft.text.isNotBlank(),
                modifier = Modifier.weight(1f).heightIn(min = 52.dp)) {
                Text(uiText("Keep"))
            }
        }
    }
}

// The preview belongs to the view only; calling Keep is the sole path to a
// persisted node. A pending rewrite also remains separate from its source card.
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
internal fun CanvasInlineDraftCard(
    draft: CanvasAiDraft,
    busy: Boolean,
    onReview: () -> Unit,
    onKeep: () -> Unit,
    onDiscard: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(.86f).widthIn(max = 300.dp)
            .haloDraftBorder(visible = true, color = MaterialTheme.colorScheme.primary),
        shape = com.halo.app.ui.design.HaloShapes.card,
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Column(Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(painterResource(com.halo.app.R.drawable.halo_ic_ai_draft),
                    contentDescription = null, modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.primary)
                Column(Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(uiText("Review AI draft"), style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface)
                    Text(uiText("Proposed by halo · not saved"),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary)
                }
            }
            Text(draft.text, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 4, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
            TextButton(onClick = onReview, enabled = !busy,
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                Text(uiText("Review"))
            }
            FlowRow(Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                maxItemsInEachRow = if (LocalDensity.current.fontScale >= 1.5f) 1 else 2) {
                TextButton(onClick = onDiscard, enabled = !busy,
                    modifier = Modifier.weight(1f).heightIn(min = 48.dp)) { Text(uiText("Discard")) }
                com.halo.app.ui.components.HaloActionButton(onClick = onKeep, enabled = !busy && draft.text.isNotBlank(),
                    modifier = Modifier.weight(1f).heightIn(min = 48.dp)) { Text(uiText("Keep")) }
            }
        }
    }
}
