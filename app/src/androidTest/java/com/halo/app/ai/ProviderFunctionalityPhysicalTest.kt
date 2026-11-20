package com.halo.app.ai

import android.app.KeyguardManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.os.Bundle
import android.os.ParcelFileDescriptor
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.halo.app.MainActivity
import com.halo.app.data.AppDatabase
import com.halo.app.data.agent.RoomAgentRunRepository
import com.halo.app.everyday.collectors.*
import com.halo.runtime.cognition.agent.DefaultAgentRunCoordinator
import com.halo.runtime.cognition.agent.AgentStep
import com.halo.app.data.WorkspaceContextStore
import com.halo.app.data.notebook.*
import com.halo.app.data.workspace.RoomWorkspaceRepository
import com.halo.app.di.ManualDependencyGraph as Graph
import com.halo.app.di.tools.*
import com.halo.app.ui.mini.HaloHostVisibility
import com.halo.app.ui.shell.HaloScreenCatalog
import com.halo.runtime.cognition.blackboard.DefaultBlackboard
import com.halo.runtime.cognition.context.DefaultContextAssembler
import com.halo.runtime.cognition.conversation.InMemoryConversationStore
import com.halo.runtime.cognition.session.CognitiveSession
import com.halo.runtime.cognition.tools.*
import com.halo.sdk.provider.*
import com.halo.sdk.runtime.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayOutputStream
import java.util.UUID

/** Explicit live opt-in. Synthetic media and an isolated Room database; no personal context or profile edits. */
@RunWith(AndroidJUnit4::class)
class ProviderFunctionalityPhysicalTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private fun receipt(message: String) = instrumentation.sendStatus(0, Bundle().apply { putString("stream", "$message\n") })
    private suspend fun target(): AiExecutionTarget {
        val args = InstrumentationRegistry.getArguments()
        assumeTrue(args.getString("providerParityLive") == "true")
        val context = instrumentation.targetContext
        assertFalse(context.getSystemService(KeyguardManager::class.java).isDeviceLocked)
        // The runner closes activities between tests. Launch with the test shell's foreground
        // authority; an application-context start can be denied as a background launch.
        val launch = withContext(Dispatchers.IO) {
            ParcelFileDescriptor.AutoCloseInputStream(instrumentation.uiAutomation.executeShellCommand(
                "am start -W -f 0x34000000 -n ${context.packageName}/${MainActivity::class.java.name} --es ${HaloScreenCatalog.EXTRA_SCREEN} home"
            )).use { it.readBytes().toString(Charsets.UTF_8) }
        }
        assertTrue("Halo test foreground launch failed", launch.contains("Status: ok"))
        withTimeout(60_000) { Graph.kidsPreferences.loadedState.first { it }; while (!HaloHostVisibility.active.value) delay(100) }
        assertFalse(Graph.kidsPreferences.enabled.value)
        assertEquals(PrivacyMode.CLOUD, Graph.privacyModes.mode.value)
        val features = listOf(
            ToolFeatureOperability("rich-sources-day", setOf("halo_source_get", "halo_source_image", "halo_day_context")),
            ToolFeatureOperability("workflow", setOf(WorkflowPlanTool.ID)),
            ToolFeatureOperability("grounded-web", setOf("halo_web_search_prepare", "halo_web_search")))
        val review = Graph.toolRegistry.operabilityReport(features)
        assertTrue("Registered feature contracts: $review", review.isEmpty())
        val providerId = requireNotNull(args.getString("providerParityId"))
        val modelId = requireNotNull(args.getString("providerParityModel"))
        val provider = Graph.aiProviderRouter.getAllProviders().filterIsInstance<AiInferenceProvider>().single { it.id == providerId }
        val models = provider.listModels()
        val model = models.single { it.id == modelId }
        receipt("PARITY_CATALOG provider=$providerId model=$modelId image=${AiCapability.IMAGE in model.inputCapabilities} tools=${model.supportsTools}")
        return AiExecutionTarget(provider, model, provider.getCapabilities())
    }

    @Test fun imageUnderstandingUsesGeneratedPixels() = runBlocking {
        val target = target()
        assertTrue(AiCapability.IMAGE in target.effectiveCapabilities)
        val bitmap = Bitmap.createBitmap(128, 128, Bitmap.Config.ARGB_8888)
        val bytes = ByteArrayOutputStream().use { out ->
            Canvas(bitmap).apply { drawColor(Color.RED); drawCircle(64f, 64f, 35f, Paint().apply { color = Color.BLUE }) }
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out); out.toByteArray()
        }
        bitmap.recycle()
        val directory = java.io.File(instrumentation.targetContext.cacheDir, "source-parity-${UUID.randomUUID()}")
        try {
            val saved = ArticleContentStore(directory)
            val image = ArticleImage("https://example.org/qa-generated.png", assetKey = saved.saveImage(bytes), mimeType = "image/png")
            val fullText = "QA complete source paragraph. ".repeat(1000) + "FINAL SOURCE DETAIL"
            val body = ArticleContent(fullText, listOf(image), "PAGE_CAPTURED", "https://example.org/qa", java.time.Instant.now().toString())
            val article = ArticleFact("qa-article", "QA source", "Heading-only preview", body.pageUrl, "QA", null, "en",
                images = body.images, contentKey = saved.save(body), contentStatus = body.status)
            val reopened = ArticleContentStore(directory)
            var offset = 0
            val restored = StringBuilder()
            do {
                val page = SavedSourcePages.read(CollectorPayload.Articles(listOf(article)), mapOf("articleId" to article.id, "textOffset" to offset), reopened::read)
                restored.append(page.getJSONArray("items").getJSONObject(0).getString("text"))
                offset = if (page.isNull("nextTextOffset")) -1 else page.getInt("nextTextOffset")
            } while (offset >= 0)
            assertEquals(fullText, restored.toString())
            val router = object : AiProviderRouter by Graph.aiProviderRouter {
                override suspend fun execute(request: AiRequest, workload: AiWorkloadSpec, context: AiContext): AiResponse {
                    assertTrue(workload.allowRemote)
                    assertEquals(ToolUseMode.NONE, request.toolUseMode)
                    assertArrayEquals(bytes, request.image)
                    return target.provider.generate(request.copy(modelId = target.modelId, maxOutputTokens = 120), context)
                }
            }
            val result = withTimeout(90_000) { SourceImageReader(reopened, router) { true }.read(image,
                "Describe the background color and the circle color in English. One sentence.") }
            val analysis = result.getString("analysis").lowercase()
            assertTrue(analysis.contains("red")); assertTrue(analysis.contains("blue"))
            assertEquals(image.assetKey, result.getString("assetKey"))
            receipt("PARITY_VISION provider=${target.providerId} generated_image=true saved_source_reopen=true full_text_pages=true red_and_blue_verified=true")
        } finally { directory.deleteRecursively() }
    }

    @Test fun cognitiveSessionCreatesAndReadsAnIsolatedWorkspaceNote() = runBlocking {
        val target = target()
        assertTrue(AiCapability.TOOLS in target.effectiveCapabilities)
        val context = instrumentation.targetContext
        val dbName = "provider-parity-${UUID.randomUUID()}.db"
        var db = Room.databaseBuilder(context, AppDatabase::class.java, dbName).build()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try {
            val documents = NotebookDocumentRepository(db.notebookDocumentDao(), db)
            val notes = RoomNotebookRepository(db.notebookDao(), documents = documents)
            val state = object : Storage {
                val values = mutableMapOf<String, Any>()
                override fun getString(key: String, defaultValue: String?) = values[key] as? String ?: defaultValue
                override fun putString(key: String, value: String?) { if (value != null) values[key] = value else values.remove(key) }
                override fun getBoolean(key: String, defaultValue: Boolean) = values[key] as? Boolean ?: defaultValue
                override fun putBoolean(key: String, value: Boolean) { values[key] = value }
                override fun getInt(key: String, defaultValue: Int) = values[key] as? Int ?: defaultValue
                override fun putInt(key: String, value: Int) { values[key] = value }
                override fun remove(key: String) { values.remove(key) }
                override fun clear() { values.clear() }
            }
            val owner = WorkspaceToolExecutor(notes, RoomWorkspaceRepository(db.notebookDao()), WorkspaceContextStore(db, notes, state))
            val calls = java.util.Collections.synchronizedList(mutableListOf<String>())
            val allowed = setOf("halo_create_note", "halo_search_notebook", "halo_get_workspace_object")
            val limited = object : ToolExecutor {
                override fun definitions() = owner.definitions().filter { it.name in allowed }
                override suspend fun execute(toolCall: ToolCall): ToolExecutionResult {
                    check(toolCall.functionName in allowed)
                    val result = owner.execute(toolCall)
                    if (result is ToolExecutionResult.Success) calls += toolCall.functionName
                    return result
                }
            }
            val runs = RoomAgentRunRepository(db)
            val coordinator = DefaultAgentRunCoordinator(runs, scope)
            val plan = WorkflowPlanTool(runs, coordinator)
            val registry = ToolRegistry(listOf(limited, plan), descriptors = limited.definitions().map { requireNotNull(HaloToolDescriptors.workspace(it)) } + WorkflowPlanTool.descriptor())
            assertTrue(registry.operabilityReport(listOf(ToolFeatureOperability("workflow", setOf(WorkflowPlanTool.ID)))).isEmpty())
            val router = object : AiProviderRouter by Graph.aiProviderRouter {
                override val selectedProviderId = MutableStateFlow<String?>(target.providerId)
                override val selectedModelId = MutableStateFlow<String?>(target.modelId)
                override suspend fun resolveExecutionTarget(request: AiRequest, workload: AiWorkloadSpec) = target
                override suspend fun resolveFallbackExecutionTarget(request: AiRequest, workload: AiWorkloadSpec, failedProviderId: String): AiExecutionTarget? = null
            }
            val bus = object : EventBus {
                override suspend fun publish(event: HaloEvent) = Unit
                override fun <T : HaloEvent> observe(type: Class<T>): Flow<T> = emptyFlow()
                override fun events(): Flow<HaloEvent> = emptyFlow()
            }
            val store = InMemoryConversationStore()
            val board = DefaultBlackboard()
            val session = CognitiveSession(bus, router, store, DefaultContextAssembler(store, board, registry), registry,
                InMemoryToolAuthorizer(registry), scope = scope, blackboard = board, agentRunStore = runs, agentRunCoordinator = coordinator)
            session.submit(AiRequest(text = "In this isolated QA workspace, first record a visible two-step workflow using halo_workflow_plan. Then create exactly one note titled Halo provider QA with body exactly: The blue kite is ready. Then read the saved note using halo_get_workspace_object and report its exact body. Execute the plan through tools; do not claim a save without its receipt.",
                modelId = target.modelId, maxOutputTokens = 320, toolUseMode = ToolUseMode.REQUIRED),
                policy = AiContextPolicy(includeDeviceState = false, includeImages = false, allowCloudWorkspace = true))
            val done = withTimeout(120_000) { session.state.first { it is CognitiveSessionState.Completed || it is CognitiveSessionState.Failed } }
            val run = runs.observeRuns().first().single { it.parentRunId == null }
            if (calls.count { it == "halo_create_note" } != 1 || done !is CognitiveSessionState.Completed) {
                // This session contains only the synthetic QA prompt and isolated owners.
                receipt("PARITY_DIAGNOSTIC state=${run.state} calls=${calls.joinToString()}")
                run.steps.forEach { step -> when (step) {
                    is AgentStep.ToolRejected -> receipt("PARITY_REJECTED tool=${step.toolId} code=${step.reasonCode} reason=${step.reason.take(800)}")
                    is AgentStep.ToolOutcomeRecorded -> receipt("PARITY_OUTCOME tool=${step.toolId} success=${step.success} truth=${step.actionTruth} output=${if (step.success) "omitted" else step.output.take(800)}")
                    else -> Unit
                } }
                receipt("PARITY_REPLY ${(done as? CognitiveSessionState.Completed)?.response?.text?.take(1200)}")
            }
            assertTrue("Provider tool loop did not finish", done is CognitiveSessionState.Completed)
            assertEquals(1, calls.count { it == "halo_create_note" })
            assertTrue(calls.contains("halo_get_workspace_object"))
            val note = notes.observeNotes().first().single()
            assertEquals("Halo provider QA", note.title); assertEquals("The blue kite is ready.", note.body)
            assertTrue(run.steps.filterIsInstance<AgentStep.PlanCreated>().any { it.steps.size >= 2 })
            assertTrue(run.steps.indexOfFirst { it is AgentStep.PlanCreated } < run.steps.indexOfFirst {
                it is AgentStep.ToolOutcomeRecorded && it.toolId == "halo_create_note" && it.success })
            scope.coroutineContext.job.cancelAndJoin()
            db.close(); db = Room.databaseBuilder(context, AppDatabase::class.java, dbName).build()
            assertEquals("The blue kite is ready.", RoomNotebookRepository(db.notebookDao()).getNote(note.id)!!.body)
            assertTrue(requireNotNull(RoomAgentRunRepository(db).get(run.id)).steps.filterIsInstance<AgentStep.PlanCreated>().any { it.steps.size >= 2 })
            receipt("PARITY_WORKSPACE provider=${target.providerId} cognitive_session=true plan_recorded_before_action=true create_count=1 exact_read=true room_reopen=true private_user_data=false")
        } finally {
            withContext(NonCancellable) { scope.coroutineContext.job.cancelAndJoin() }
            db.close(); context.deleteDatabase(dbName)
        }
    }
}
