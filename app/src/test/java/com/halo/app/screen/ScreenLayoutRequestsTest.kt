package com.halo.app.screen

import org.junit.Assert.*
import org.junit.Test

class ScreenLayoutRequestsTest {
    @Test fun proposalsAreBoundedAndNeverGuessAnUnknownRequest() {
        val names = mapOf("clock" to "Clock & date", "notebook" to "Continue in Notebook")
        assertEquals(LayoutChange.Resize("clock", CardSize.EXPANDED), ScreenLayoutRequests.propose("enlarge Clock & date", names))
        assertNull(ScreenLayoutRequests.propose("delete all my notes", names))
        assertNull(ScreenLayoutRequests.propose("add unknown widget", names))
    }
    @Test fun applyingPreviewPreservesSourcesAndRejectsNoOps() {
        val original = ScreenWidgets.starter("Focus")
        val changed = ScreenLayoutRequests.apply(original, LayoutChange.Remove("notebook"))!!
        assertTrue(original.cards.any { it.widgetType == "notebook" })
        assertFalse(changed.cards.any { it.widgetType == "notebook" })
        assertNull(ScreenLayoutRequests.apply(original, LayoutChange.Add("clock", emptyMap())))
        assertNull(ScreenLayoutRequests.apply(original, LayoutChange.Add("unknown", emptyMap())))
        assertNull(ScreenLayoutRequests.apply(original, LayoutChange.Move("clock", 0)))
    }

    @Test fun compositionAddsOnlyNamedCardsAndRoutesResearchTopicToGroundedSearch() {
        val names = mapOf("weather" to "Weather", "research" to "Research", "calendar" to "Calendar")
        val plan = ScreenLayoutRequests.compose(
            "build my screen with weather, black-hole research articles and calendar", names,
        )!!

        assertEquals(listOf("weather", "research", "calendar"),
            plan.changes.map { (it as LayoutChange.Add).widgetType })
        assertEquals("black hole", plan.researchQuery)
        val layout = ScreenLayoutRequests.apply(ScreenWidgets.starter("Focus"), plan)!!
        assertTrue(layout.cards.any { it.widgetType == "weather" })
        assertTrue(layout.cards.any { it.widgetType == "research" && it.itemId == null })
        assertTrue(layout.cards.any { it.widgetType == "calendar" })
    }

    @Test fun compositionDoesNotGuessUnsupportedRequests() {
        val names = mapOf("weather" to "Weather", "research" to "Research")
        assertNull(ScreenLayoutRequests.compose("build my screen with an unknown widget", names))
    }

    @Test fun researchRefreshCanRunWhenItsCardAlreadyExists() {
        val names = mapOf("research" to "Research")
        val plan = ScreenLayoutRequests.compose("build my screen with research articles about black holes", names)!!
        val layout = ScreenWidgets.starter("Focus").copy(cards = ScreenWidgets.starter("Focus").cards +
            ScreenCard("research", "research:existing", CardSize.STANDARD, false))

        assertEquals("black holes", plan.researchQuery)
        assertEquals(layout, ScreenLayoutRequests.apply(layout, plan))
    }
}
