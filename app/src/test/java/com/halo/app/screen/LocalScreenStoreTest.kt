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

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [33])
class LocalScreenStoreTest {
    private val context get() = ApplicationProvider.getApplicationContext<Application>()
    @Before fun clear() { context.getSharedPreferences("halo_screen", 0).edit().clear().commit() }
    @Test fun layoutConfigurationAndTopicsSurviveRestart() {
        val store = LocalScreenStore(context)
        val layout = ScreenWidgets.starter("Morning").copy(cards = listOf(ScreenCard("calendar", null,
            CardSize.EXPANDED, true, mapOf("calendar" to "chosen"))))
        store.saveLayout(layout); store.setTopics("Space, architecture")
        val restored = LocalScreenStore(context)
        assertEquals(layout.cards, restored.layout.value!!.cards)
        assertEquals("Space, architecture", restored.topics.value)
    }
    @Test fun morningLayoutIncludesNativeStudyResumeCard() {
        assertTrue(ScreenWidgets.types.contains("study"))
        assertTrue(ScreenWidgets.starter("Morning").cards.any { it.widgetType == "study" })
    }
    @Test fun backgroundBindingDoesNotReplaceUserUndo() {
        val store = LocalScreenStore(context)
        store.saveLayout(ScreenWidgets.starter("Morning"))
        val initial = store.layout.value!!
        store.saveLayout(ScreenWidgets.starter("Focus"))
        store.saveLayout(store.layout.value!!.copy(name = "Background update"), recordUndo = false)
        store.undo()
        assertEquals(initial, store.layout.value)
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
}
