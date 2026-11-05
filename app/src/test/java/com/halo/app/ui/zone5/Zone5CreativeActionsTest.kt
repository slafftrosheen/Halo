package com.halo.app.ui.zone5

import android.app.Application
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.lifecycle.*
import androidx.room.withTransaction
import androidx.navigation.compose.*
import androidx.test.core.app.ApplicationProvider
import com.github.takahirom.roborazzi.captureRoboImage
import com.halo.app.ai.*
import com.halo.app.data.studio.*
import com.halo.app.data.workspace.*
import com.halo.app.data.study.StudyRepository
import com.halo.app.di.ManualDependencyGraph
import com.halo.app.di.initializeUnitTestWorkManager
import com.halo.app.study.*
import com.halo.app.ui.presence.*
import com.halo.app.ui.screens.notebook.*
import com.halo.app.ui.screens.study.*
import com.halo.app.ui.theme.*
import com.halo.app.ui.workspace.*
import com.halo.sdk.provider.*
import com.halo.sdk.runtime.study.StudyLanguagePreferences
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.*

/** Native host renders and real Room owners. No provider or external-app invocation. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = Application::class, qualifiers = "en-rUS-w360dp-h800dp")
@OptIn(ExperimentalCoroutinesApi::class)
class Zone5CreativeActionsTest {
    private val scheduler = TestCoroutineScheduler()
    private val main = UnconfinedTestDispatcher(scheduler)
    @get:Rule val compose = createComposeRule(effectContext = main)
    private val owners = ViewModelStore()
    private val jobs = mutableListOf<Job>()
    private lateinit var fixture: WorkspaceFileToolsFixture
    private val actions = mutableStateOf<ShellPageProjection?>(null)
    private var systemBack: (() -> Unit)? = null
    @Before fun setup() {
        Dispatchers.setMain(main); fixture = WorkspaceFileToolsFixture()
        val context = ApplicationProvider.getApplicationContext<Application>()
        initializeUnitTestWorkManager(context); ManualDependencyGraph.init(context)
    }
    private fun own(model: ViewModel) { owners.put(jobs.size.toString(), model); jobs += requireNotNull(model.viewModelScope.coroutineContext[Job]) }
    @After fun close() {
        try {
            compose.runOnIdle { owners.clear() }; scheduler.runCurrent()
            runBlocking { withTimeout(10_000) { jobs.forEach { it.join() }; fixture.scope.coroutineContext[Job]?.cancelAndJoin() } }
        } finally { try { fixture.close() } finally { Dispatchers.resetMain() } }
    }
    private fun await(predicate: () -> Boolean) = compose.waitUntil(10_000) { scheduler.runCurrent(); if (!compose.mainClock.autoAdvance) compose.mainClock.advanceTimeByFrame(); predicate() }
    private fun render(route: String, scale: Float = 1f, content: @Composable () -> Unit) {
        compose.setContent { HostlessHaloTheme(darkTheme = false, fontScale = scale, palette = HaloPalette(HaloAccent.ROSE, HaloTone.WARM)) {
            val utility = remember { mutableStateOf<ShellUtilityProjection?>(null) }
            CompositionLocalProvider(LocalShellHaloOrb provides true, LocalShellPageActions provides actions, LocalShellUtilityBand provides utility) {
                val back = androidx.activity.compose.LocalOnBackPressedDispatcherOwner.current!!.onBackPressedDispatcher
                SideEffect { systemBack = { back.onBackPressed() } }
                Surface(color = MaterialTheme.colorScheme.background, contentColor = MaterialTheme.colorScheme.onSurface) {
                    Column(Modifier.fillMaxSize()) {
                        Box(Modifier.weight(1f).fillMaxWidth()) {
                            val nav = rememberNavController(); NavHost(nav, route) { composable(route) { content() } }
                        }
                        utility.value?.let { RenderShellUtilityBand(it) }
                    }
                }
            }
        } }
        await { actions.value != null }
    }
    @Test fun richNoteInsertConvertsTheEmptyParagraphAndEnterCreatesTheNextParagraph() {
        val note = runBlocking { fixture.notes.createNote(title = "Owned rich note") }
        lateinit var model: NoteEditorViewModel
        compose.runOnUiThread {
            model = NoteEditorViewModel(note.id, com.halo.app.notebook.NotebookUseCases(fixture.notes), document = fixture.documents)
            own(model)
        }
        render("notebook/note/{noteId}", scale = 2f) { NoteEditorScreen(model, {}) }
        await { model.uiState.value.status == SaveStatus.SAVED }
        compose.runOnIdle { assertTrue(actions.value!!.invoke("page.note.insert")) }
        compose.onNodeWithText("Heading 1").performScrollTo().performClick()
        await { model.uiState.value.blocks.singleOrNull()?.type == com.halo.app.data.notebook.NoteBlockType.HEADING }
        compose.onNode(hasSetTextAction()).performTextInput("Field heading")
        compose.onNode(hasSetTextAction()).performTextInput("\n")
        await { model.uiState.value.blocks.size == 2 }
        assertEquals(listOf(com.halo.app.data.notebook.NoteBlockType.HEADING, com.halo.app.data.notebook.NoteBlockType.PARAGRAPH), model.uiState.value.blocks.map { it.type })
        val expected = model.uiState.value.blocks.toList()
        await { model.uiState.value.status == SaveStatus.SAVED }
        assertEquals(expected, model.uiState.value.blocks)
        assertEquals(expected, runBlocking { fixture.documents.load(note.id)!! }.blocks)
    }
    @Test fun canvasArrangeOpensWithoutMutatingTheBoard() {
        val id = runBlocking { fixture.canvases.create("Field board").also { fixture.canvases.addTextNode(it, 24f, 24f, "Synthetic field observation") } }
        lateinit var model: CanvasEditorViewModel
        compose.runOnUiThread { model = CanvasEditorViewModel(id, fixture.canvases); own(model) }
        render("notebook/canvas/{canvasId}") { CanvasEditorScreen(model, onBack = {}) }
        await { !model.uiState.value.loading }
        compose.onRoot().captureRoboImage()
        val original = model.uiState.value.nodes
        compose.runOnIdle {
            val prior = actions.value!!
            assertEquals(listOf("page.canvas.add", "page.canvas.arrange", "page.canvas.fit"), prior.actions.take(3).map { it.id })
            assertTrue(prior.invoke("page.canvas.arrange"))
        }
        compose.onNodeWithText("Arrange").assertExists()
        assertEquals(original, model.uiState.value.nodes)
    }
    @Test fun childCanvasWorkingSubtreePassesItsLiveProfileToNavigationDialog() {
        val id = runBlocking { fixture.canvases.create("Child board") }
        lateinit var model: CanvasEditorViewModel
        compose.runOnUiThread { model = CanvasEditorViewModel(id, fixture.canvases); own(model) }
        render("notebook/canvas/{canvasId}", scale = 2f) {
            // Like AppShell's NavHost, this working subtree has no bottom-Zone navigation local.
            assertNull(LocalHaloZone5Navigation.current)
            CanvasEditorScreen(model, kidsMode = true, onBack = {})
        }
        await { !model.uiState.value.loading }
        compose.runOnIdle { assertTrue(actions.value!!.invoke("page.canvas.view-navigation")) }
        compose.onAllNodesWithText("Left").onFirst().performScrollTo().assertHeightIsAtLeast(androidx.compose.ui.unit.Dp(56f))
        compose.onNodeWithText("Zoom in").performScrollTo().assertHeightIsAtLeast(androidx.compose.ui.unit.Dp(56f))
        compose.onNodeWithText("Small steps").performScrollTo().assertHeightIsAtLeast(androidx.compose.ui.unit.Dp(56f))
        compose.onNodeWithText("Done").assertHeightIsAtLeast(androidx.compose.ui.unit.Dp(56f))
        compose.onNode(isDialog()).captureRoboImage()
    }
    @Test fun childPageIdentityWrapsCompletelyAt200Percent() {
        val title = "A complete saved field observation with a long identifying title and its final distinguishing words"
        render("notebook/file/{objectId}", scale = 2f) {
            BindShellChildPage(title, {}, objectId = "fixture", titleIsSource = true)
        }
        val layouts = mutableListOf<androidx.compose.ui.text.TextLayoutResult>()
        compose.onNodeWithText(title).performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        assertTrue(layouts.single().lineCount > 2)
        assertFalse(layouts.single().hasVisualOverflow)
        compose.onNodeWithText(title).assertIsDisplayed()
        compose.onRoot().captureRoboImage()
    }
    @Test fun emptyCanvasCreationAndImportRemainInItsScopedAddMenu() {
        val id = runBlocking { fixture.canvases.create("Empty field board") }
        lateinit var model: CanvasEditorViewModel
        compose.runOnUiThread { model = CanvasEditorViewModel(id, fixture.canvases); own(model) }
        render("notebook/canvas/{canvasId}") { CanvasEditorScreen(model, onBack = {}) }
        await { !model.uiState.value.loading }
        compose.onNodeWithText("Add text card").assertDoesNotExist()
        compose.runOnIdle { assertTrue(actions.value!!.invoke("page.canvas.add")) }
        assertTrue(model.uiState.value.nodes.isEmpty())
        compose.onNodeWithText("Add a file or PDF").assertIsDisplayed()
        compose.onNodeWithText("Add text card").performClick()
        await { model.uiState.value.nodes.size == 1 }
        assertEquals(1, runBlocking { fixture.canvases.observe(id).first()!! }.nodes.size)
    }
    private val router = object : AiProviderRouter {
        override val selectedProviderId = MutableStateFlow<String?>(null)
        override val selectedModelId = MutableStateFlow<String?>(null)
        override val routingPolicy = MutableStateFlow(AiRoutingPolicy.EXPLICIT)
        override fun selectProvider(id: String) { error("Unexpected provider selection") }
        override fun selectModel(modelId: String?) { error("Unexpected model selection") }
        override fun getAllProviders() = emptyList<AiProvider>()
        override suspend fun getEligibleProvider(request: AiRequest): AiProvider = error("Unexpected provider request")
        override suspend fun execute(request: AiRequest, context: AiContext): AiResponse = error("Unexpected provider request")
        override fun start() = Unit
        override fun stop() = Unit
        override suspend fun resolveExecutionTarget(request: AiRequest, workload: AiWorkloadSpec): AiExecutionTarget = error("Unexpected provider request")
    }
    @Test fun studioNavigationKeepsQueuedFailureRecoveryAfterTheViewModelIsCleared() {
        val service=fixture.studio();val projects=service.projects
        val layer=StudioLayer(id="ink",name="Retain marks",kind=StudioLayerKind.INK)
        val original=runBlocking {projects.create("Navigation recovery",StudioDocument(layers=listOf(layer)))}
        lateinit var model:StudioViewModel
        compose.runOnUiThread {model=StudioViewModel(service,fixture.files,WorkspaceImageService(router,IntentQualificationFixture.MemoryStorage()),NotebookAssetRepository(fixture.context,fixture.database),original.id);own(model)}
        render("studio?projectId={projectId}") {StudioScreen(model,{},{},{},{},{})}
        await {model.state.value.project!=null&&!model.state.value.loading}
        val entered=CompletableDeferred<Unit>();val release=CompletableDeferred<Unit>()
        val held=fixture.scope.launch {fixture.database.withTransaction {
            projects.apply(original.id,original.revision,StudioCommand.Background(42));entered.complete(Unit);release.await()
        }}
        runBlocking {entered.await()}
        val marks=com.halo.sdk.runtime.ink.InkCodec.encode(com.halo.sdk.runtime.ink.InkDocument(listOf(com.halo.sdk.runtime.ink.InkStroke(com.halo.sdk.runtime.ink.InkTool.FINGER,listOf(com.halo.sdk.runtime.ink.InkPoint(100,100))))))
        lateinit var session:StudioInkSession
        compose.runOnIdle {session=requireNotNull(model.startInk(original.revision,layer.id));session.submit(marks);model.closeProject();assertTrue(model.state.value.busy);owners.clear()}
        release.complete(Unit)
        await {service.inkRecovery.value===session}
        assertEquals(marks,session.draft().layers.single().ink)
        assertEquals(42,runBlocking {projects.get(original.id)!!}.session.current.background)
        runBlocking {held.join()}
    }
    @Test fun studioSaveAndUndoDrainAndResumeTheConfirmedPaintRevision() {
        val service=fixture.studio();val projects=service.projects
        val layer=StudioLayer(id="ink",name="Paint",kind=StudioLayerKind.INK)
        val original=runBlocking {projects.create("Paint boundaries",StudioDocument(64,64,layers=listOf(layer),selectedLayerId=layer.id))}
        lateinit var model:StudioViewModel
        compose.runOnUiThread {model=StudioViewModel(service,fixture.files,WorkspaceImageService(router,IntentQualificationFixture.MemoryStorage()),NotebookAssetRepository(fixture.context,fixture.database),original.id);own(model)}
        render("studio?projectId={projectId}") {StudioScreen(model,{},{},{},{},{})}
        await {model.state.value.project!=null&&!model.state.value.loading}
        lateinit var first:StudioInkSession
        fun marks(x:Int)=com.halo.sdk.runtime.ink.InkCodec.encode(com.halo.sdk.runtime.ink.InkDocument(listOf(com.halo.sdk.runtime.ink.InkStroke(com.halo.sdk.runtime.ink.InkTool.FINGER,listOf(com.halo.sdk.runtime.ink.InkPoint(x,x))))))
        compose.runOnIdle {first=requireNotNull(model.startInk(original.revision,layer.id));first.submit(marks(100));model.save()}
        await {!model.state.value.busy&&model.activeInk.value!=null&&model.activeInk.value!==first}
        val saved=model.state.value.project!!
        assertFalse(saved.hasUnsavedChanges)
        assertEquals(saved.revision,model.activeInk.value!!.persistedRevision)
        compose.runOnIdle {model.edit(saved.revision,"undo")}
        await {!model.state.value.busy&&model.state.value.project?.revision!=saved.revision&&model.activeInk.value!=null}
        val resumed=model.activeInk.value!!
        assertTrue(requireNotNull(com.halo.sdk.runtime.ink.InkCodec.decode(resumed.initialLayer.ink)).strokes.isEmpty())
        val finalMarks=marks(300)
        compose.runOnIdle {resumed.submit(finalMarks)}
        await {resumed.fullyPersisted}
        assertNull(model.inkRecovery.value)
        assertEquals(finalMarks,runBlocking {projects.get(original.id)!!}.session.current.layers.single().ink)
    }
    @Test fun studioPaintShowsTheCanonicalStackInsteadOfRawInkAboveAnOpaqueLayer() {
        val service=fixture.studio()
        val ink=StudioLayer(id="ink",name="Under painting",kind=StudioLayerKind.INK,color=0x80ff0000.toInt(),opacity=500)
        val top=StudioLayer(id="top",name="Opaque cover",kind=StudioLayerKind.RECTANGLE,color=android.graphics.Color.BLUE)
        val project=runBlocking {service.projects.create("Canonical paint",StudioDocument(64,64,layers=listOf(ink,top)))}
        val session=StudioInkSession(project,ink.id,service.projects,fixture.scope,{}, {})
        render("studio?projectId={projectId}") {
            BindShellPageActions(ShellPageOwner("studio"),emptyList())
            StudioPaintSurface(service,session,"DRAW",{})
        }
        compose.waitUntil(10_000) {compose.onAllNodesWithTag("studio-paint-artboard").fetchSemanticsNodes().firstOrNull()?.config?.get(androidx.compose.ui.semantics.SemanticsProperties.StateDescription)=="Ready"}
        compose.onNodeWithTag("studio-paint-artboard").performTouchInput {swipe(center,center+androidx.compose.ui.geometry.Offset(24f,0f))}
        compose.waitUntil(10_000) {session.fullyPersisted&&compose.onNodeWithTag("studio-paint-artboard").fetchSemanticsNode().config[androidx.compose.ui.semantics.SemanticsProperties.StateDescription]=="Ready"}
        val pixels=compose.onNodeWithTag("studio-paint-artboard").captureToImage().toPixelMap()
        assertEquals(androidx.compose.ui.graphics.Color.Blue,pixels[pixels.width/2,pixels.height/2])
        runBlocking {session.finishAndAwait()}
    }
    @Test fun studioDoesNotSampleOrPresentAnOldBitmapAfterTheNewRenderFails() {
        val service=fixture.studio()
        val document=StudioDocument(64,64,background=android.graphics.Color.RED)
        val current=mutableStateOf(StudioProject("preview","Preview",1,document,StudioSession(document)))
        var picked:Int?=null
        render("studio?projectId={projectId}") {
            BindShellPageActions(ShellPageOwner("studio"),emptyList())
            StudioCanvasSurface(service,current.value,false,{},{},{},onPick={picked=it})
        }
        compose.waitUntil(10_000) {compose.onAllNodesWithTag("studio-artboard").fetchSemanticsNodes().firstOrNull()?.config?.get(androidx.compose.ui.semantics.SemanticsProperties.StateDescription)=="Ready"}
        compose.runOnIdle {
            val missing=StudioLayer(name="Unavailable image",kind=StudioLayerKind.IMAGE,source=StudioSource("missing-source","pinned"))
            current.value=current.value.copy(revision=2,session=StudioSession(document.apply(StudioCommand.Add(missing))))
        }
        compose.waitUntil(10_000) {compose.onAllNodesWithText("Preview unavailable. Your project is kept.").fetchSemanticsNodes().isNotEmpty()}
        compose.onNodeWithTag("studio-artboard").performTouchInput {click(center)}
        assertNull(picked)
        compose.onNodeWithTag("studio-artboard").assert(androidx.compose.ui.test.SemanticsMatcher.expectValue(androidx.compose.ui.semantics.SemanticsProperties.StateDescription,"Preview unavailable. Your project is kept."))
    }
    @Test fun studioModesShareTheSameRoomDocumentAndAiNeedsExplicitReview() {
        val context=ApplicationProvider.getApplicationContext<Application>()
        val projects=StudioProjectRepository(fixture.database)
        val assets=NotebookAssetRepository(context,fixture.database)
        val service=StudioService(context,projects,fixture.files,fixture.workspace,StudioRenderer(context,fixture.files,fixture.workspace),assets,fixture.canvases,
            fixture.database.workspaceContextDao(),StudioUiRequests(fixture.scope,{}))
        val shape=StudioLayer(id="shape",name="Editable shape",kind=StudioLayerKind.RECTANGLE,bounds=StudioRect(100,100,600,600))
        val project=runBlocking {projects.create("One creative document",StudioDocument(layers=listOf(shape),selectedLayerId=shape.id))}
        lateinit var model:StudioViewModel
        compose.runOnUiThread {model=StudioViewModel(service,fixture.files,WorkspaceImageService(router,IntentQualificationFixture.MemoryStorage()),assets,project.id);own(model)}
        var reviews=0
        render("studio?projectId={projectId}") {StudioScreen(model,{},{},{},{},{reviews++})}
        await {model.state.value.project?.id==project.id&&!model.state.value.loading}
        compose.onNodeWithText("Design").performClick()
        compose.onNodeWithText("Paint").performClick()
        compose.onNodeWithText("Paint").assertIsDisplayed()
        assertEquals(project,runBlocking {projects.get(project.id)})
        compose.runOnIdle {assertTrue(actions.value!!.invoke("page.studio.ai"))}
        compose.waitUntil(10_000) {compose.onAllNodesWithText("Review image edit").fetchSemanticsNodes().isNotEmpty()}
        compose.onNodeWithText("Review image edit").assertIsDisplayed()
        assertEquals(0,reviews)
        assertEquals(project,runBlocking {projects.get(project.id)})
        compose.onNodeWithText("Review image edit").performClick()
        assertEquals(1,reviews)
        assertEquals(project,runBlocking {projects.get(project.id)})
    }
    @Test fun studioArtboardTapAndDragCommitThroughCanonicalRevisionWithUndo() {
        val context=ApplicationProvider.getApplicationContext<Application>()
        val projects=StudioProjectRepository(fixture.database)
        val assets=NotebookAssetRepository(context,fixture.database)
        val service=StudioService(context,projects,fixture.files,fixture.workspace,StudioRenderer(context,fixture.files,fixture.workspace),assets,fixture.canvases,
            fixture.database.workspaceContextDao(),StudioUiRequests(fixture.scope,{}))
        val shape=StudioLayer(id="shape",name="Touch shape",kind=StudioLayerKind.RECTANGLE,bounds=StudioRect(100,100,600,600))
        val project=runBlocking {projects.create("Touch revision",StudioDocument(layers=listOf(shape)))}
        lateinit var model:StudioViewModel
        compose.runOnUiThread {model=StudioViewModel(service,fixture.files,WorkspaceImageService(router,IntentQualificationFixture.MemoryStorage()),assets,project.id);own(model)}
        render("studio?projectId={projectId}") {StudioScreen(model,{},{},{},{},{})}
        await {model.state.value.project!=null&&!model.state.value.loading}
        compose.waitUntil(10_000) {compose.onAllNodesWithTag("studio-artboard").fetchSemanticsNodes().firstOrNull()?.config?.get(androidx.compose.ui.semantics.SemanticsProperties.StateDescription)=="Ready"}
        compose.onNodeWithTag("studio-artboard").performTouchInput {click(center)}
        await {model.state.value.project?.session?.current?.selectedLayerId==shape.id&&!model.state.value.busy}
        compose.onNodeWithTag("studio-artboard").performTouchInput {swipe(center,center+androidx.compose.ui.geometry.Offset(50f,0f))}
        await {model.state.value.project?.session?.current?.layers?.first()?.bounds?.x!=100&&!model.state.value.busy}
        val edited=runBlocking {projects.get(project.id)!!}
        assertTrue(edited.session.current.layers.single().bounds.x>100)
        val restored=runBlocking {projects.undo(edited.id,edited.revision)}
        assertEquals(shape.bounds,restored.session.current.layers.single().bounds)
    }
    @Test fun studioLayersUseCanonicalRevisionAndStaleRestoreIsRejected() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val projects = StudioProjectRepository(fixture.database)
        val assets = NotebookAssetRepository(context, fixture.database)
        val service = StudioService(context, projects, fixture.files, fixture.workspace,
            StudioRenderer(context, fixture.files, fixture.workspace), assets, fixture.canvases,
            fixture.database.workspaceContextDao(), StudioUiRequests(fixture.scope, {}))
        val project = runBlocking { projects.create("Field poster") }
        lateinit var model: StudioViewModel
        compose.runOnUiThread { model = StudioViewModel(service, fixture.files, WorkspaceImageService(router, IntentQualificationFixture.MemoryStorage()), assets, project.id); own(model) }
        render("studio?projectId={projectId}") { StudioScreen(model, {}, {}, {}, {}, {}) }
        await { model.state.value.project != null && !model.state.value.loading }
        compose.runOnIdle {
            assertTrue(actions.value!!.actions.map { it.id }.containsAll(listOf("page.studio.copy", "page.studio.export-jpeg", "page.studio.discard", "page.studio.new")))
            assertEquals(context.getString(com.halo.app.R.string.zone5_saved_state), actions.value!!.owner.statusLabel)
        }
        compose.onRoot().captureRoboImage()
        compose.runOnIdle {
            assertEquals(listOf("page.studio.add-layer", "page.studio.layers", "page.studio.tools"), actions.value!!.actions.take(3).map { it.id })
            assertTrue(actions.value!!.invoke("page.studio.add-layer"))
        }
        compose.onNodeWithText("Choose a layer type").assertIsDisplayed()
        assertTrue(runBlocking { projects.get(project.id)!! }.session.current.layers.isEmpty())
        val external = runBlocking { projects.apply(project.id, project.revision, StudioCommand.Add(StudioLayer(name = "Caption", kind = StudioLayerKind.TEXT))) }
        await { model.state.value.project?.revision == external.revision }
        compose.runOnIdle { assertFalse(model.restore(1, project.revision, project.id)) }
        assertEquals(1, runBlocking { projects.get(project.id)!! }.session.current.layers.size)
        compose.runOnIdle { model.export(com.halo.app.data.workspace.CanvasImageFormat.PNG) }
        await { model.state.value.output != null }
        val receipt = model.state.value.exportedSource
        assertEquals("${external.id}:${external.revision}", receipt)
        compose.runOnIdle { model.dismissOutput() }
        await { model.state.value.output == null }
        assertEquals(receipt, model.state.value.exportedSource)
        compose.runOnIdle { assertTrue(actions.value!!.owner.helpSteps.last().complete) }
    }
    @Test fun studioDiscardRequiresCurrentRevisionConfirmationAndCancelKeepsDraft() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val projects = StudioProjectRepository(fixture.database)
        val assets = NotebookAssetRepository(context, fixture.database)
        val service = StudioService(context, projects, fixture.files, fixture.workspace,
            StudioRenderer(context, fixture.files, fixture.workspace), assets, fixture.canvases,
            fixture.database.workspaceContextDao(), StudioUiRequests(fixture.scope, {}))
        val initial = runBlocking { projects.create("Discard review fixture") }
        val draft = runBlocking { projects.apply(initial.id, initial.revision, StudioCommand.Add(StudioLayer(name = "Keep until confirmed", kind = StudioLayerKind.TEXT))) }
        lateinit var model: StudioViewModel
        compose.runOnUiThread { model = StudioViewModel(service, fixture.files,
            WorkspaceImageService(router, IntentQualificationFixture.MemoryStorage()), assets, draft.id); own(model) }
        render("studio?projectId={projectId}", 2f) { StudioScreen(model, {}, {}, {}, {}, {}) }
        await { model.state.value.project?.revision == draft.revision && !model.state.value.loading }
        compose.runOnIdle { assertTrue(actions.value!!.invoke("page.studio.discard")) }
        assertEquals(draft, runBlocking { projects.get(draft.id) })
        compose.onNodeWithText("Cancel").performClick()
        assertEquals(draft, runBlocking { projects.get(draft.id) })
        compose.runOnIdle { assertTrue(actions.value!!.invoke("page.studio.discard")) }
        val newer = runBlocking { projects.apply(draft.id, draft.revision, StudioCommand.Add(StudioLayer(name = "Newer edit", kind = StudioLayerKind.TEXT))) }
        await { model.state.value.project?.revision == newer.revision }
        compose.onNodeWithText(context.getString(com.halo.app.R.string.studio_discard)).assertIsNotEnabled()
        compose.runOnIdle { model.discardDraft(draft.id, draft.revision) }
        assertEquals(newer, runBlocking { projects.get(newer.id) })
        compose.onNodeWithText("Cancel").performClick()
        compose.runOnIdle { assertTrue(actions.value!!.invoke("page.studio.discard")) }
        compose.onNode(isDialog()).captureRoboImage()
        compose.onNodeWithText(context.getString(com.halo.app.R.string.studio_discard)).performClick()
        await { model.state.value.project?.hasUnsavedChanges == false && !model.state.value.busy }
        assertEquals(initial.committed, runBlocking { projects.get(initial.id)!! }.session.current)
    }
    @Test fun canvasButtonsPanZoomAndMoveOnlyTheRemainingSelection() {
        val id = runBlocking { fixture.canvases.create("Accessible positioning") }
        val a = runBlocking { fixture.canvases.addTextNode(id, 0f, 0f, "Move me") }
        val b = runBlocking { fixture.canvases.addTextNode(id, 200f, 0f, "Keep me") }
        lateinit var model: CanvasEditorViewModel
        compose.runOnUiThread { model = CanvasEditorViewModel(id, fixture.canvases); own(model) }
        render("notebook/canvas/{canvasId}", 2f) { CanvasEditorScreen(model, onBack = {}) }
        await { !model.uiState.value.loading && model.uiState.value.nodes.size == 2 }
        compose.runOnIdle { model.toggleSelectionMode(); model.selectNode(a); model.selectNode(b); model.selectNode(b) }
        compose.runOnIdle {
            assertEquals(setOf(a), model.uiState.value.selectedNodeIds)
            assertTrue(actions.value!!.invoke("page.canvas.view-navigation"))
        }
        compose.onAllNodesWithText("Left").onFirst().performScrollTo().performClick()
        compose.onNodeWithText("Zoom in").performScrollTo().performClick()
        compose.onAllNodesWithText("Right").onLast().performScrollTo().performClick()
        compose.onNode(isDialog()).captureRoboImage()
        compose.onNodeWithText("Done").performClick()
        lateinit var save: Job
        compose.runOnIdle { save = model.persistPending() }
        await { save.isCompleted && model.uiState.value.saveState == CanvasSaveState.SAVED }
        val stored = runBlocking { fixture.canvases.observe(id).first()!! }
        assertEquals(12f, stored.nodes.single { it.id == a }.x, .001f)
        assertEquals(200f, stored.nodes.single { it.id == b }.x, .001f)
        assertEquals(1.1f, stored.viewportScale, .001f)
        // Ordinary selection also survives viewport changes before a later move.
        compose.runOnIdle { model.toggleSelectionMode(); model.selectNode(a); model.transform(5f, 0f, 1f) }
        assertEquals(a, model.uiState.value.selectedNodeId)
    }
    @Test fun studioFailuresNameTheOperationAndVerifyTheRetainedProject() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val projects = StudioProjectRepository(fixture.database)
        val assets = NotebookAssetRepository(context, fixture.database)
        val service = StudioService(context, projects, fixture.files, fixture.workspace,
            StudioRenderer(context, fixture.files, fixture.workspace), assets, fixture.canvases,
            fixture.database.workspaceContextDao(), StudioUiRequests(fixture.scope, {}))
        val project = runBlocking { projects.create("Recovery fixture") }
        lateinit var model: StudioViewModel
        compose.runOnUiThread { model = StudioViewModel(service, fixture.files,
            WorkspaceImageService(router, IntentQualificationFixture.MemoryStorage()), assets, project.id); own(model) }
        render("studio?projectId={projectId}") { StudioScreen(model, {}, {}, {}, {}, {}) }
        await { model.state.value.project != null && !model.state.value.loading }
        compose.runOnIdle { model.restore(999, project.revision, project.id) }
        await { model.state.value.failure != null && !model.state.value.busy }
        assertEquals(com.halo.app.ui.workspace.StudioOperation.RESTORE, model.state.value.failure?.operation)
        assertEquals(com.halo.app.ui.workspace.StudioRetainedWork.SAVED, model.state.value.failure?.retained)
        assertTrue(model.state.value.failure?.canRetry == true)
        assertEquals(project, runBlocking { projects.get(project.id) })
        compose.onNodeWithText("Could not restore that version.", substring = true).assertExists()
        val newer = runBlocking { projects.rename(project.id, project.revision, "Other writer") }
        await { model.state.value.project?.revision == newer.revision }
        compose.runOnIdle { model.retry() }
        await { model.state.value.failure?.reason == com.halo.app.ui.workspace.StudioFailureReason.CHANGED && !model.state.value.busy }
        assertFalse(model.state.value.failure?.canRetry == true)
        assertEquals(newer, runBlocking { projects.get(project.id) })
        compose.runOnIdle { model.dismissError(); model.create("") }
        await { model.state.value.failure != null && !model.state.value.busy }
        assertEquals(com.halo.app.ui.workspace.StudioOperation.CREATE, model.state.value.failure?.operation)
        assertFalse(model.state.value.failure?.canRetry == true)
        assertEquals(newer, runBlocking { projects.get(project.id) })
    }

    @Test fun studioInterruptedExportIsUnknownAndNeverAutomaticallyReplayed() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val projects = StudioProjectRepository(fixture.database)
        val assets = NotebookAssetRepository(context, fixture.database)
        val service = StudioService(context, projects, fixture.files, fixture.workspace,
            StudioRenderer(context, fixture.files, fixture.workspace), assets, fixture.canvases,
            fixture.database.workspaceContextDao(), StudioUiRequests(fixture.scope, {}))
        val project = runBlocking { projects.create("Interrupted fixture") }
        val saved = androidx.lifecycle.SavedStateHandle(mapOf("studioProject" to project.id, "studioPendingOperation" to "EXPORT"))
        lateinit var model: StudioViewModel
        compose.runOnUiThread { model = StudioViewModel(service, fixture.files,
            WorkspaceImageService(router, IntentQualificationFixture.MemoryStorage()), assets, savedState = saved); own(model) }
        render("studio?projectId={projectId}") { StudioScreen(model, {}, {}, {}, {}, {}) }
        await { model.state.value.project != null && !model.state.value.loading }
        assertEquals(com.halo.app.ui.workspace.StudioFailureReason.INTERRUPTED, model.state.value.failure?.reason)
        assertFalse(model.state.value.failure?.canRetry == true)
        assertNull(model.state.value.output)
        assertEquals(project, runBlocking { projects.get(project.id) })
    }

    @Test fun studyPracticeReviewAndBackUseExclusiveViews() {
        val repository = StudyRepository(fixture.database, fixture.database.studyDao())
        val session = runBlocking { repository.createSession(listOf("source"), 7, StudyLanguagePreferences(), "Light and sound") }
        val card = runBlocking { repository.addPracticeDraft(session.id, "What travels faster?", "Light", "source", 7, accepted = true) }
        val passage = "Light travels faster than sound."
        val resolver = StudySourceResolver { id, _ -> ResolvedStudySource.Text(id, "7", "Class material",
            listOf(StudyPassage(passage, StudySourceLocator.TextRange(0, passage.length))), passage.length, SourceCoverage.FULL) }
        lateinit var model: StudyViewModel
        compose.runOnUiThread { model = StudyViewModel(session.id, StudyUseCases(repository, sourceResolver = resolver)); own(model) }
        var leftLesson = 0
        render("study") { StudyScreen(model, onBack = { leftLesson++ }) }
        await { !model.uiState.value.loading && model.uiState.value.dueReviewItems.size == 1 && actions.value?.actions?.firstOrNull()?.enabled == true }
        compose.onRoot().captureRoboImage()
        val original = runBlocking { repository.getSession(session.id) }
        compose.runOnIdle {
            val prior = actions.value!!
            assertEquals(listOf("page.study.details", "page.study.practice", "page.study.read"), prior.actions.take(3).map { it.id })
            assertTrue(prior.invoke("page.study.practice"))
        }
        compose.onNodeWithText("Review (1)").performClick()
        compose.onNodeWithText("Show answer").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Memory progress").assertDoesNotExist()
        compose.runOnIdle { requireNotNull(systemBack).invoke() }
        compose.runOnIdle { assertEquals("page.study.details", actions.value!!.actions.first().id) }
        assertEquals(0, leftLesson)
        assertEquals(original, runBlocking { repository.getSession(session.id) })
        assertTrue(runBlocking { repository.getReviewEvents(card.id) }.isEmpty())
    }

    @Test fun canvasFamilyOpensTheExistingCanonicalBoardWithoutGraphicsProjectActions() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val projects = StudioProjectRepository(fixture.database)
        val assets = NotebookAssetRepository(context, fixture.database)
        val service = StudioService(context, projects, fixture.files, fixture.workspace,
            StudioRenderer(context, fixture.files, fixture.workspace), assets, fixture.canvases,
            fixture.database.workspaceContextDao(), StudioUiRequests(fixture.scope, {}))
        val id = runBlocking { fixture.canvases.create("Connected field idea") }
        lateinit var model: StudioViewModel
        var opened: String? = null
        compose.runOnUiThread { model = StudioViewModel(service, fixture.files,
            WorkspaceImageService(router, IntentQualificationFixture.MemoryStorage()), assets); own(model) }
        render("canvases") { StudioScreen(model, {}, {}, {}, {}, {}, onOpenCanvas = { opened = it }, canvasLibrary = true) }
        await { !model.libraryStatus.value.canvasesLoading && model.canvases.value.isNotEmpty() }
        compose.onNodeWithText("HaloCanvas").assertExists()
        compose.onNodeWithText("Connected field idea").performClick()
        assertEquals(id, opened)
        compose.runOnIdle {
            assertEquals("canvases", actions.value!!.owner.routeFamily)
            assertEquals(listOf("page.studio.new-canvas"), actions.value!!.actions.map { it.id })
            assertFalse(actions.value!!.invoke("page.studio.new"))
        }
        assertTrue(model.projects.value.isEmpty())
    }
    private data class SelectedBoard(val id: String, val selected: Set<String>, val other: String, val model: CanvasEditorViewModel)
    private fun reviewSelectedGroup(): SelectedBoard {
        val id = runBlocking { fixture.canvases.create("Selection board") }
        val a = runBlocking { fixture.canvases.addTextNode(id, 1400f, 1600f, "Source A") }
        val b = runBlocking { fixture.canvases.addTextNode(id, 1800f, 1600f, "Source B") }
        val other = runBlocking { fixture.canvases.addTextNode(id, 0f, 0f, "Unrelated center card") }
        lateinit var model: CanvasEditorViewModel
        compose.runOnUiThread { model = CanvasEditorViewModel(id, fixture.canvases); own(model) }
        render("notebook/canvas/{canvasId}") { CanvasEditorScreen(model, onBack = {}) }
        await { !model.uiState.value.loading && model.uiState.value.nodes.size == 3 }
        compose.runOnIdle { model.toggleSelectionMode(); model.selectNode(a); model.selectNode(b) }
        compose.mainClock.advanceTimeBy(300)
        compose.runOnIdle { assertTrue(actions.value!!.invoke("page.canvas.arrange")) }
        compose.mainClock.advanceTimeBy(300)
        compose.onNodeWithText("Create group").performScrollTo().performClick()
        compose.mainClock.advanceTimeBy(300)
        await { model.uiState.value.organization != null }
        await { compose.onAllNodes(hasSetTextAction() and hasText("Group title")).fetchSemanticsNodes().size == 1 }
        assertEquals(setOf(a, b), model.uiState.value.organization!!.sourceFingerprints.keys)
        return SelectedBoard(id, setOf(a, b), other, model)
    }
    @Test fun groupingOffCenterCardsPreservesTheReviewedSelectionAndUndo() {
        val board = reviewSelectedGroup()
        val before = runBlocking { fixture.canvases.observe(board.id).first()!! }
        compose.onNode(hasSetTextAction() and hasText("Group title")).performSemanticsAction(SemanticsActions.SetText) { assertTrue(it(AnnotatedString("Field observations"))) }
        compose.mainClock.advanceTimeByFrame()
        compose.onNode(isDialog()).captureRoboImage()
        compose.onNodeWithText("Approve organization").performClick()
        await { board.model.uiState.value.organizationCommitRevision > 0 }
        val after = runBlocking { fixture.canvases.observe(board.id).first()!! }
        val group = after.nodes.single { it.type == com.halo.app.data.notebook.CanvasNodeType.GROUP }
        assertEquals("Field observations", group.text)
        assertEquals(board.selected, after.edges.filter { it.fromNodeId == group.id }.map { it.toNodeId }.toSet())
        assertEquals(before.nodes.single { it.id == board.other }, after.nodes.single { it.id == board.other })
        compose.runOnIdle { board.model.undo() }
        await { !board.model.uiState.value.undoAvailable && board.model.uiState.value.nodes.none { it.type == com.halo.app.data.notebook.CanvasNodeType.GROUP } }
        assertEquals(before.nodes.map { Triple(it.id, it.x, it.y) }.toSet(), runBlocking { fixture.canvases.observe(board.id).first()!! }.nodes.map { Triple(it.id, it.x, it.y) }.toSet())
    }
    @Test fun changingASourceWhileNamingASelectedGroupCannotCommitIt() {
        val board = reviewSelectedGroup()
        runBlocking { fixture.canvases.updateText(board.id, board.selected.first(), "Changed outside the preview") }
        await { board.model.uiState.value.nodes.any { it.text == "Changed outside the preview" } }
        compose.onNodeWithText("Approve organization").assertIsNotEnabled()
        compose.onNode(hasText("The source changed. Choose Cancel", substring = true) and hasAnyAncestor(isDialog())).assertIsDisplayed()
        // An alternate caller cannot bypass the same source-version guard.
        compose.runOnIdle { board.model.approveOrganization() }
        await { board.model.uiState.value.organizationRequiresRefresh }
        compose.onNode(isDialog()).captureRoboImage()
        val stored = runBlocking { fixture.canvases.observe(board.id).first()!! }
        assertTrue(stored.nodes.none { it.type == com.halo.app.data.notebook.CanvasNodeType.GROUP })
        assertTrue(stored.edges.isEmpty())
        assertEquals(0L, board.model.uiState.value.organizationCommitRevision)
        compose.onNode(hasText("Cancel") and hasAnyAncestor(isDialog())).performClick()
        await { board.model.uiState.value.organization == null }
        compose.runOnIdle { assertTrue(actions.value!!.invoke("page.canvas.arrange")) }
        compose.mainClock.advanceTimeBy(300)
        compose.onNodeWithText("Create group").performScrollTo().performClick()
        await { board.model.uiState.value.organization != null && !board.model.uiState.value.organizationRequiresRefresh }
        compose.onNodeWithText("Approve organization").assertIsEnabled()
        compose.onNode(hasSetTextAction() and hasText("Group title")).performSemanticsAction(SemanticsActions.SetText) { assertTrue(it(AnnotatedString("Reviewed current cards"))) }
        compose.onNodeWithText("Approve organization").performClick()
        await { board.model.uiState.value.organizationCommitRevision > 0 }
        val recovered = runBlocking { fixture.canvases.observe(board.id).first()!! }
        val group = recovered.nodes.single { it.type == com.halo.app.data.notebook.CanvasNodeType.GROUP }
        assertEquals("Reviewed current cards", group.text)
        assertEquals(board.selected, recovered.edges.filter { it.fromNodeId == group.id }.map { it.toNodeId }.toSet())
        assertTrue(recovered.nodes.any { it.text == "Changed outside the preview" })
    }

    @Test fun staleWholeBoardPreviewRecoversWithoutASelection() {
        val id = runBlocking { fixture.canvases.create("Whole board") }
        val source = runBlocking { fixture.canvases.addTextNode(id, 0f, 0f, "Original whole-board source") }
        lateinit var model: CanvasEditorViewModel
        compose.runOnUiThread { model = CanvasEditorViewModel(id, fixture.canvases); own(model) }
        render("notebook/canvas/{canvasId}") { CanvasEditorScreen(model, onBack = {}) }
        await { !model.uiState.value.loading && model.uiState.value.nodes.size == 1 }
        fun openWholeBoard() {
            compose.runOnIdle { assertTrue(actions.value!!.invoke("page.canvas.arrange")) }
            compose.mainClock.advanceTimeBy(300)
            compose.onNodeWithText("Organize whole board").performScrollTo().performClick()
            await { model.uiState.value.organization != null }
        }
        openWholeBoard()
        assertTrue(model.uiState.value.selectedNodeIds.isEmpty())
        assertNull(model.uiState.value.selectedNodeId)
        runBlocking { fixture.canvases.updateText(id, source, "Current whole-board source") }
        await { model.uiState.value.nodes.any { it.text == "Current whole-board source" } }
        compose.onNodeWithText("Approve organization").assertIsNotEnabled()
        compose.onNode(hasText("Organize whole board for all cards", substring = true) and hasAnyAncestor(isDialog())).assertIsDisplayed()
        compose.onNode(isDialog()).captureRoboImage()
        compose.onNode(hasText("Cancel") and hasAnyAncestor(isDialog())).performClick()
        await { model.uiState.value.organization == null }
        openWholeBoard()
        compose.onNodeWithText("Approve organization").assertIsEnabled().performClick()
        await { model.uiState.value.organizationCommitRevision > 0 }
        val saved = runBlocking { fixture.canvases.observe(id).first()!! }
        val group = saved.nodes.single { it.type == com.halo.app.data.notebook.CanvasNodeType.GROUP }
        assertEquals(listOf(source), saved.edges.filter { it.fromNodeId == group.id }.map { it.toNodeId })
        assertTrue(saved.nodes.any { it.id == source && it.text == "Current whole-board source" })
    }

}
