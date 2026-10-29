package com.halo.app.screen

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlinx.coroutines.runBlocking
import java.time.Instant
import java.time.ZoneId
import java.util.Locale

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [33])
class LocalScreenStoreTest {
    private val context get() = ApplicationProvider.getApplicationContext<Application>()
    @Before fun clear() { context.getSharedPreferences("halo_screen", 0).edit().clear().commit() }
    @Test fun layoutConfigurationAndTopicsSurviveRestart() {
        val store = LocalScreenStore(context)
        val layout = ScreenWidgets.starter("Morning").copy(cards = listOf(ScreenCard("calendar", null,
            CardSize.EXPANDED, true, mapOf("calendar" to "chosen"), archived = true)))
        store.saveLayout(layout); store.setTopics("Space, architecture")
        val restored = LocalScreenStore(context)
        assertEquals(layout.cards, restored.layout.value!!.cards)
        assertTrue(restored.layout.value!!.cards.single().archived)
        assertEquals("Space, architecture", restored.topics.value)
    }
    @Test fun layoutsWithoutArchiveFieldRemainCurrentAfterUpgrade() {
        context.getSharedPreferences("halo_screen", 0).edit()
            .putString("layout", """{"id":"morning","name":"Morning","version":1,"cards":[{"type":"clock","size":"STANDARD","pinned":true}]}""")
            .commit()

        val restored = LocalScreenStore(context)

        assertFalse(restored.layout.value!!.cards.single().archived)
    }
    @Test fun morningLayoutIncludesNativeStudyResumeCard() {
        assertTrue(ScreenWidgets.types.contains("study"))
        assertTrue(ScreenWidgets.starter("Morning").cards.any { it.widgetType == "study" })
    }
    @Test fun researchTimestampIncludesLocalizedDateAndTime() {
        val timestamp = formatScreenTimestamp(Instant.parse("2026-10-22T10:00:00Z"), true, Locale.US, ZoneId.of("UTC"))
        assertTrue(timestamp.contains("2026"))
        assertTrue(timestamp.contains("10:00"))
    }
    @Test fun backgroundBindingDoesNotReplaceUserUndo() {
        val store = LocalScreenStore(context)
        store.saveLayout(ScreenWidgets.starter("Morning"))
        val initial = store.layout.value!!
        store.saveLayout(ScreenWidgets.starter("Focus"))
        store.saveLayout(store.layout.value!!.copy(name = "Background update"), recordUndo = false)
        val restoredInitial = initial.copy(version = store.layout.value!!.version + 1)
        store.undo()
        assertEquals(restoredInitial, store.layout.value)
    }

    @Test fun sourcedResearchAndCitationLinksSurviveRestart() = runBlocking {
        val store = LocalScreenStore(context)
        val at = Instant.parse("2026-10-22T10:00:00Z")
        val research = ScreenItem(
            id = "research:query-hash", sourceType = ScreenSourceType.RESEARCH, sourceId = null,
            title = "Black hole research", summary = "A grounded summary", contentType = ScreenContentType.ARTICLES,
            updatedAt = at, lastSuccessAt = at, expiresAt = null, state = ScreenItemState.READY,
            sensitivity = ScreenSensitivity.PRIVATE, attribution = "Grounded web search",
            availableActions = listOf(ScreenAction.OpenSource, ScreenAction.SaveToNotebook, ScreenAction.HideFromScreen),
            sourceLinks = listOf(ScreenSourceLink("Original paper", "https://example.org/paper")),
        )
        store.publishAndSaveLayout(research, ScreenWidgets.starter("Quiet").copy(cards = listOf(
            ScreenCard("research", research.id, CardSize.STANDARD, false))))

        val restored = LocalScreenStore(context)
        assertEquals(research, restored.items.value.single())
        assertEquals("research", restored.layout.value!!.cards.single().widgetType)
        assertEquals(research.id, restored.layout.value!!.cards.single().itemId)
    }

    @Test fun automationResultUpdatesOneStableScreenCardAcrossRestart() = runBlocking {
        val store = LocalScreenStore(context)
        val firstAt = Instant.parse("2026-10-22T10:00:00Z")
        val first = ScreenItem(
            id = "automation:routine-1", sourceType = ScreenSourceType.AUTOMATION,
            sourceId = "routine-1", title = "Morning summary", summary = "First result",
            contentType = ScreenContentType.SUMMARY, updatedAt = firstAt, lastSuccessAt = firstAt,
            expiresAt = null, state = ScreenItemState.READY, sensitivity = ScreenSensitivity.PRIVATE,
            attribution = null, availableActions = listOf(ScreenAction.OpenSource, ScreenAction.HideFromScreen),
        )
        val layout = ScreenWidgets.starter("Quiet").copy(cards = listOf(
            ScreenCard("automation_result", first.id, CardSize.STANDARD, false)))
        store.publishAndSaveLayout(first, layout)
        val updatedAt = firstAt.plusSeconds(60)
        store.publish(first.copy(summary = "Updated result", updatedAt = updatedAt, lastSuccessAt = updatedAt))

        val restored = LocalScreenStore(context)

        assertEquals(1, restored.items.value.size)
        assertEquals("Updated result", restored.items.value.single().summary)
        assertEquals(first.id, restored.layout.value!!.cards.single().itemId)
    }

    /** Added during the code-only pass; execution deferred by user directive. */
    @Test fun stalePublicationCannotOverwriteNewerCardOrLayout() = runBlocking {
        val store = LocalScreenStore(context)
        val at = Instant.parse("2026-10-25T12:00:00Z")
        val current = ScreenItem("owned-result", ScreenSourceType.AUTOMATION, "owned-routine", "Fixture", "Current",
            ScreenContentType.SUMMARY, at, at, null, ScreenItemState.READY, ScreenSensitivity.PRIVATE,
            null, emptyList())
        val layout = ScreenWidgets.starter("Quiet")
        assertTrue(store.publishAndSaveLayout(current, layout))
        val observed = store.layout.value!!
        assertFalse(store.publishAndSaveLayout(current.copy(summary = "Older", updatedAt = at.minusSeconds(1)),
            observed.copy(name = "Stale proposal"), expectedLayoutVersion = observed.version))
        assertEquals(observed, store.layout.value)
        assertEquals("Current", store.items.value.single().summary)
        store.publish(current.copy(summary = "Undated", updatedAt = null))
        assertEquals("Current", store.items.value.single().summary)
    }

    @Test fun latePinRejectsWhenAnotherLayoutEditHasCommitted() = runBlocking {
        val store = LocalScreenStore(context)
        store.saveLayout(ScreenWidgets.starter("Quiet"))
        val old = store.layout.value!!
        store.saveLayout(old.copy(name = "New layout"))
        val latest = store.layout.value!!
        val item = ScreenItem("owned-result", ScreenSourceType.RESEARCH, "owned-search", "Fixture", "Result",
            ScreenContentType.SUMMARY, Instant.now(), null, null, ScreenItemState.READY, ScreenSensitivity.PRIVATE,
            null, emptyList())
        assertFalse(store.publishAndSaveLayout(item, old, expectedLayoutVersion = old.version))
        assertEquals(latest, store.layout.value)
        assertTrue(store.items.value.isEmpty())
    }

    private fun typedRoutineResult(): ScreenItem = ScreenItem(
        "automation:typed", ScreenSourceType.AUTOMATION, "routine-id", "User's routine title",
        "1 of 2 actions finished", ScreenContentType.SUMMARY, Instant.parse("2026-10-29T09:00:00Z"),
        Instant.parse("2026-10-29T09:00:00Z"), null, ScreenItemState.READY, ScreenSensitivity.PRIVATE,
        null, listOf(ScreenAction.OpenSource), sourceRevision = "producing-revision",
        appSummary = ScreenAppSummary.RoutineActionsFinished(1, 2),
    )

    @Test fun typedSummaryRoundTripPreservesRawSourceAndExactRevision() = runBlocking {
        val original = typedRoutineResult()
        LocalScreenStore(context).publish(original)
        assertEquals(original, LocalScreenStore(context).items.value.single())
    }

    @Test fun absentUnknownOrMalformedSummaryMetadataNeverDropsSourceItem() = runBlocking {
        val original = typedRoutineResult()
        LocalScreenStore(context).publish(original)
        val preferences = context.getSharedPreferences("halo_screen", 0)
        val saved = requireNotNull(preferences.getString("items", null))
        val variants = listOf(
            null,
            org.json.JSONObject().put("version", 2).put("kind", "ROUTINE_ACTIONS_FINISHED").put("completed", 1).put("total", 2),
            org.json.JSONObject().put("version", 1).put("kind", "FUTURE_KIND").put("completed", 1).put("total", 2),
            org.json.JSONObject().put("version", 1).put("kind", "ROUTINE_ACTIONS_FINISHED").put("completed", 3).put("total", 2),
            org.json.JSONObject().put("version", 1).put("kind", "ROUTINE_ACTIONS_FINISHED").put("completed", "1").put("total", 2),
            org.json.JSONObject().put("version", 1).put("kind", "ROUTINE_ACTIONS_FINISHED").put("completed", 1.5).put("total", 2),
            org.json.JSONObject().put("version", 1).put("kind", "ROUTINE_ACTIONS_FINISHED").put("completed", 1),
            "invalid optional object",
        )
        variants.forEach { metadata ->
            val array = org.json.JSONArray(saved)
            array.getJSONObject(0).put("appSummary", metadata)
            preferences.edit().putString("items", array.toString()).commit()
            assertEquals(metadata.toString(), original.copy(appSummary = null), LocalScreenStore(context).items.value.single())
        }
    }

    @Test fun failedRefreshRetainsTypedLastGoodSummaryAcrossRestart() = runBlocking {
        val store = LocalScreenStore(context)
        val original = typedRoutineResult()
        store.publish(original)
        val failedAt = requireNotNull(original.updatedAt).plusSeconds(60)
        store.markFailed(original.id, failedAt)
        assertEquals(original.copy(state = ScreenItemState.STALE, updatedAt = failedAt),
            LocalScreenStore(context).items.value.single())
    }

    @Test fun newerPublicationUpdatesCountsWithoutChangingStableCardIdentity() = runBlocking {
        val store = LocalScreenStore(context)
        val original = typedRoutineResult()
        store.publish(original)
        val changed = original.copy(summary = "2 of 2 actions finished", sourceRevision = "new-revision",
            updatedAt = requireNotNull(original.updatedAt).plusSeconds(60),
            appSummary = ScreenAppSummary.RoutineActionsFinished(2, 2))
        store.publish(changed)
        assertEquals(listOf(changed), LocalScreenStore(context).items.value)
    }
}
