package com.halo.app.ui.zone5

import android.app.Application
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
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
    @get:Rule val compose = createComposeRule()
    private val scheduler = TestCoroutineScheduler()
    private val owners = ViewModelStore()
    private val jobs = mutableListOf<Job>()
    private lateinit var fixture: WorkspaceFileToolsFixture
    private val actions = mutableStateOf<ShellPageProjection?>(null)
    @Before fun setup() {
        Dispatchers.setMain(UnconfinedTestDispatcher(scheduler)); fixture = WorkspaceFileToolsFixture()
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
    private fun await(predicate: () -> Boolean) = compose.waitUntil(10_000) { scheduler.runCurrent(); predicate() }
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
    @Test fun canvasArrangeIsAPreviewAndOldActionsExpireWhenSelectionChanges() {
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
    @Test fun studyPracticeChangesOnlyTheLocalViewAndBackRestoresLessonActions() {
        val repository = StudyRepository(fixture.database, fixture.database.studyDao())
        val session = runBlocking { repository.createSession(listOf("source"), 7, StudyLanguagePreferences(), "Light and sound") }
        val passage = "Light travels faster than sound."
        val resolver = StudySourceResolver { id, _ -> ResolvedStudySource.Text(id, "7", "Class material",
            listOf(StudyPassage(passage, StudySourceLocator.TextRange(0, passage.length))), passage.length, SourceCoverage.FULL) }
        lateinit var model: StudyViewModel
        compose.runOnUiThread { model = StudyViewModel(session.id, StudyUseCases(repository, sourceResolver = resolver)); own(model) }
        render("study") { StudyScreen(model, onBack = {}) }
        await { !model.uiState.value.loading && actions.value?.actions?.firstOrNull()?.enabled == true }
        compose.onRoot().captureRoboImage()
        val original = runBlocking { repository.getSession(session.id) }
        compose.runOnIdle {
            val prior = actions.value!!
            assertEquals(listOf("page.study.details", "page.study.practice", "page.study.read"), prior.actions.take(3).map { it.id })
            assertTrue(prior.invoke("page.study.practice"))
        }
        compose.onNodeWithText("Back to tutor").performClick()
        compose.runOnIdle { assertEquals("page.study.details", actions.value!!.actions.first().id) }
        assertEquals(original, runBlocking { repository.getSession(session.id) })
    }
}
