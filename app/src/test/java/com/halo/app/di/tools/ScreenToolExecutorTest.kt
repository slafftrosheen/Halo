package com.halo.app.di.tools

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.halo.app.screen.*
import com.halo.runtime.cognition.tools.*
import com.halo.sdk.provider.ToolCall
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Instant

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
class ScreenToolExecutorTest {
    private val context get() = ApplicationProvider.getApplicationContext<Application>()
    private lateinit var store: LocalScreenStore
    private lateinit var executor: ScreenToolExecutor
    private var allowed = true
    @Before fun setup() {
        context.getSharedPreferences("halo_screen", 0).edit().clear().commit()
        store = LocalScreenStore(context)
        executor = ScreenToolExecutor(store) { allowed }
    }
    private suspend fun call(name: String, args: Map<String, Any?> = emptyMap(), direct: Boolean = false, interactionId: String? = null) =
        executor.execute(ToolCall("screen-fixture", name, args.mapValues { requireNotNull(it.value) }),
            ToolExecutionContext(interactionId = interactionId, directUserAuthority = direct))
    private fun json(result: ToolExecutionResult) = JSONObject((result as ToolExecutionResult.Success).output)
    private suspend fun revision() = json(call("halo_screen_state")).getString("revision")
    private suspend fun preview(changes: List<Map<String, Any?>>, expectedRevision: String? = null): JSONObject {
        val observed = expectedRevision ?: revision()
        return json(call("halo_screen_preview", mapOf("revision" to observed, "changes" to changes)))
    }
    private suspend fun apply(changes: List<Map<String, Any?>>, expectedRevision: String? = null): JSONObject {
        val observed = expectedRevision ?: revision()
        val proposal = preview(changes, observed)
        return json(call("halo_screen_apply", mapOf("revision" to observed, "changes" to changes,
            "previewToken" to proposal.getString("previewToken"))))
    }

    @Test fun stateIsBoundedConfigurationOnlyAndKeepsPrivacyDefault() = runBlocking {
        store.publish(ScreenItem("secret-item", ScreenSourceType.NOTEBOOK, "secret-source", "secret-title", "secret-body",
            ScreenContentType.NOTE_REF, Instant.EPOCH, Instant.EPOCH, null, ScreenItemState.READY,
            ScreenSensitivity.PRIVATE, "secret-attribution", emptyList()))
        store.saveLayout(ScreenWidgets.starter("Quiet").copy(cards = listOf(ScreenCard("notebook", "secret-item",
            CardSize.STANDARD, false, config = mapOf("label" to "secret-config"), presentationId = "secret-presentation"))))
        val result = json(call("halo_screen_state"))
        assertTrue(result.getJSONObject("settings").getBoolean("hidePrivate"))
        assertTrue(result.getJSONObject("layout").getJSONArray("cards").getJSONObject(0).getBoolean("hasSource"))
        listOf("secret-item", "secret-source", "secret-title", "secret-body", "secret-config", "secret-presentation", "secret-attribution")
            .forEach { assertFalse(it, result.toString().contains(it)) }
        assertEquals(64, result.getString("revision").length)
        assertTrue(result.toString().length < 12000)
    }

    @Test fun previewDoesNotMutateAndExactApplyUsesNativeOwnerPacking() = runBlocking {
        store.saveLayout(ScreenWidgets.starter("Morning"))
        val before = store.layout.value!!
        val changes = listOf(mapOf("operation" to "reorder", "index" to 2, "toIndex" to 0),
            mapOf("operation" to "resize_grid", "index" to 0, "columns" to 4, "dw" to -1, "dh" to 1),
            mapOf("operation" to "flags", "index" to 0, "pinned" to true, "archived" to true),
            mapOf("operation" to "appearance", "font" to "ATKINSON", "background" to "WARM", "translucent" to true),
            mapOf("operation" to "typography", "index" to 0, "titlePercent" to 130, "clockPercent" to 180))
        val expectedCards = ScreenGrid.resize(ScreenGrid.reorder(before.cards, 2, 0), 0, 4, -1, 1)
        val rev = revision(); val proposal = preview(changes, rev)
        assertFalse(proposal.getBoolean("applied")); assertEquals(before, store.layout.value)
        val result = apply(changes, rev)
        assertTrue(result.getBoolean("applied")); assertTrue(result.getBoolean("persisted")); assertTrue(result.getBoolean("changed"))
        val after = store.layout.value!!
        assertEquals(expectedCards[0].placement, after.cards[0].placement)
        assertEquals(before.cards[2].presentationId, after.cards[0].presentationId)
        assertTrue(after.cards[0].archived); assertTrue(after.cards[0].pinned)
        assertEquals(ScreenFont.ATKINSON, after.appearance.font)
        assertEquals(130, after.cards[0].typography.titlePercent)
        assertNotEquals(rev, result.getString("revision"))
        assertEquals(after, LocalScreenStore(context).layout.value)
    }

    @Test fun staleLayoutSettingsPresetAndTamperedPreviewNeverMutate() = runBlocking {
        store.saveLayout(ScreenWidgets.starter("Quiet"))
        val edits = listOf(mapOf("operation" to "add", "widgetType" to "tasks"))
        val rev = revision(); val proposal = preview(edits, rev)
        assertTrue(call("halo_screen_apply", mapOf("revision" to rev, "changes" to edits, "previewToken" to "0".repeat(64))) is ToolExecutionResult.Rejected)
        store.setTopics("New manual choice")
        val before = store.layout.value
        assertTrue(call("halo_screen_apply", mapOf("revision" to rev, "changes" to edits,
            "previewToken" to proposal.getString("previewToken"))) is ToolExecutionResult.Rejected)
        assertTrue(call("halo_screen_settings", mapOf("revision" to rev, "settings" to mapOf("dimAtNight" to false))) is ToolExecutionResult.Rejected)
        assertTrue(call("halo_screen_save_preset", mapOf("revision" to rev, "name" to "Stale")) is ToolExecutionResult.Rejected)
        assertEquals(before, store.layout.value); assertTrue(store.presets.value.isEmpty()); assertTrue(store.dimAtNight.value)
        val fresh = revision(); store.savePreset("Manual", before!!)
        assertTrue(call("halo_screen_undo", mapOf("revision" to fresh)) is ToolExecutionResult.Rejected)
    }

    @Test fun privacyWeakeningNeedsScopedApprovedRuntimeContextAndEveryArgumentValidatesBeforeMutation() = runBlocking {
        val rev = revision()
        val settings = mapOf("hidePrivate" to false, "topics" to "Reading")
        assertTrue(call("halo_screen_settings", mapOf("revision" to rev, "settings" to settings)) is ToolExecutionResult.Rejected)
        assertTrue(call("halo_screen_settings", mapOf("revision" to rev, "settings" to settings), direct = true) is ToolExecutionResult.Rejected)
        assertTrue(call("halo_screen_settings", mapOf("revision" to rev, "settings" to settings), interactionId = " ") is ToolExecutionResult.Rejected)
        assertTrue(store.hidePrivate.value); assertEquals("", store.topics.value)
        assertTrue(call("halo_screen_settings", mapOf("revision" to rev,
            "settings" to mapOf("keepCharging" to true, "topics" to 12)), direct = true) is ToolExecutionResult.Rejected)
        assertFalse(store.keepCharging.value)
        // The canonical ALWAYS/SENSITIVE approval route supplies a scoped
        // interaction without granting the direct-authority fast path.
        val accepted = json(call("halo_screen_settings", mapOf("revision" to rev, "settings" to settings),
            direct = false, interactionId = "approved-fixture-interaction"))
        assertTrue(accepted.getBoolean("applied")); assertFalse(store.hidePrivate.value)
        assertEquals("Reading", store.topics.value)
        assertTrue(accepted.getBoolean("persisted"))
        assertFalse(LocalScreenStore(context).hidePrivate.value)
        assertEquals("Reading", LocalScreenStore(context).topics.value)
        allowed = false
        assertTrue(call("halo_screen_state") is ToolExecutionResult.Rejected)
        assertTrue(call("halo_screen_settings", mapOf("revision" to revisionFromStore(), "settings" to mapOf("hidePrivate" to true)), true) is ToolExecutionResult.Rejected)
        assertFalse(store.hidePrivate.value)
    }

    @Test fun privacyApprovalPreviewNamesTheConsequenceWithoutReturningPrivateSettings() = runBlocking {
        val before = revisionFromStore()
        val call = ToolCall("privacy-preview", "halo_screen_settings", mapOf("revision" to before,
            "settings" to mapOf("hidePrivate" to false, "topics" to "private-fixture-topic")))
        val preflight = executor.preflight(call) as ToolPreflightResult.Validated
        assertEquals(call, preflight.sanitizedCall)
        assertTrue(preflight.preview!!.description.contains("People nearby may see personal and private cards"))
        assertFalse(preflight.preview!!.description.contains("private-fixture-topic"))
        assertNull(preflight.intent)
        assertEquals(before, revisionFromStore())
        assertTrue(store.hidePrivate.value)
    }

    private fun revisionFromStore() = store.withToolState { it.revision }

    @Test fun presetsStartersUndoAndRemovalUseExistingOwnerWithoutChangingSources() = runBlocking {
        apply(listOf(mapOf("operation" to "starter", "name" to "Quiet")))
        json(call("halo_screen_save_preset", mapOf("revision" to revision(), "name" to "Desk")))
        val preset = store.presets.value.single()
        apply(listOf(mapOf("operation" to "starter", "name" to "Focus")))
        val focus = store.layout.value!!
        apply(listOf(mapOf("operation" to "preset", "presetId" to preset.id)))
        assertEquals(preset.cards, store.layout.value!!.cards)
        val restoredFocus = focus.copy(version = store.layout.value!!.version + 1)
        json(call("halo_screen_undo", mapOf("revision" to revision())))
        assertEquals(restoredFocus, store.layout.value)
        json(call("halo_screen_remove_preset", mapOf("revision" to revision(), "presetId" to preset.id)))
        assertTrue(store.presets.value.isEmpty()); assertTrue(store.items.value.isEmpty())
        assertEquals(restoredFocus, store.layout.value)
    }

    @Test fun nativeWhitelistRejectsExecutableMarkupExtraFieldsAndFractionalIndices() = runBlocking {
        store.saveLayout(ScreenWidgets.starter("Quiet"))
        val before = store.layout.value; val rev = revision()
        val invalid = listOf(mapOf("operation" to "add", "widgetType" to "html"),
            mapOf("operation" to "add", "widgetType" to "tasks", "config" to mapOf("js" to "run()")),
            mapOf("operation" to "remove", "index" to 0.5),
            mapOf("operation" to "typography", "index" to 0, "clockPercent" to 201),
            mapOf("operation" to "appearance", "font" to "https://example.org/font"),
            mapOf("operation" to "research", "query" to "invent news"))
        invalid.forEach { assertTrue(it.toString(), call("halo_screen_preview", mapOf("revision" to rev, "changes" to listOf(it))) is ToolExecutionResult.Rejected) }
        assertEquals(before, store.layout.value); assertTrue(store.items.value.isEmpty())
    }

    @Test fun definitionsHaveVersionedOperabilityAndClosedSchemasForRegisteredReview() {
        val descriptors = executor.definitions().map { definition ->
            val contract = requireNotNull(ScreenOperabilityContracts.forDefinition(definition))
            ToolDescriptor(id = ToolId(definition.name), definition = definition, category = ToolCategory.WORKSPACE,
                security = if (definition.name in ScreenOperabilityContracts.readTools) ToolSecurityPolicy.READ_ONLY else
                    ToolSecurityPolicy(risk = ToolRiskClass.REVERSIBLE, confirmation = ConfirmationPolicy.USER_POLICY,
                        replaySafety = ReplaySafety.NEVER_REPLAY), operability = contract)
        }
        val registry = ToolRegistry(listOf(executor), descriptors)
        val review = registry.operabilityReport(listOf(ToolFeatureOperability("screen", descriptors.map { it.name }.toSet())))
        assertTrue(review.toString(), review.isEmpty())
        assertEquals(7, descriptors.size)
        descriptors.forEach { assertEquals(false, it.definition.parameters["additionalProperties"]) }
        assertNotNull(registry.validate(ToolCall("invalid-native-type", "halo_screen_state", mapOf("widgetType" to "html"))))
    }
}
