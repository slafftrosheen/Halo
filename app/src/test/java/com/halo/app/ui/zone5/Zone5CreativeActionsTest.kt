package com.halo.app.ui.zone5

import android.app.Application
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.lifecycle.*
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
        compose.onRoot().captureRoboImage()
        compose.runOnIdle {
            assertEquals(listOf("page.studio.add-layer", "page.studio.layers", "page.studio.export"), actions.value!!.actions.take(3).map { it.id })
            assertTrue(actions.value!!.invoke("page.studio.add-layer"))
        }
        compose.onNodeWithText("Choose a layer type").assertIsDisplayed()
        assertTrue(runBlocking { projects.get(project.id)!! }.session.current.layers.isEmpty())
        val external = runBlocking { projects.apply(project.id, project.revision, StudioCommand.Add(StudioLayer(name = "Caption", kind = StudioLayerKind.TEXT))) }
        await { model.state.value.project?.revision == external.revision }
        compose.runOnIdle { assertFalse(model.restore(1, project.revision, project.id)) }
        assertEquals(1, runBlocking { projects.get(project.id)!! }.session.current.layers.size)
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
        compose.onNodeWithText("Back").performClick()
        compose.runOnIdle { assertEquals("page.study.details", actions.value!!.actions.first().id) }
        assertEquals(0, leftLesson)
        assertEquals(original, runBlocking { repository.getSession(session.id) })
        assertTrue(runBlocking { repository.getReviewEvents(card.id) }.isEmpty())
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
        compose.onNodeWithText("Approve organization").performClick()
        await { board.model.uiState.value.notice == "Source changed. Review it again." }
        compose.onNode(hasText("Source changed. Review it again.") and hasAnyAncestor(isDialog())).assertIsDisplayed()
        compose.onNode(isDialog()).captureRoboImage()
        val stored = runBlocking { fixture.canvases.observe(board.id).first()!! }
        assertTrue(stored.nodes.none { it.type == com.halo.app.data.notebook.CanvasNodeType.GROUP })
        assertTrue(stored.edges.isEmpty())
        assertEquals(0L, board.model.uiState.value.organizationCommitRevision)
    }

}
