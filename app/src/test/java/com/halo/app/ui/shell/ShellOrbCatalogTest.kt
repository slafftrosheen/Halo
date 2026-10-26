package com.halo.app.ui.shell

import com.halo.app.ui.screens.home.HomeToolCatalog
import org.junit.Assert.*
import org.junit.Test

class ShellOrbCatalogTest {
    @Test fun activePageActionsRequireTheExactObjectAndPageFamily() {
        val canvas = com.halo.app.ui.presence.ShellPageOwner("canvas", "board-A")
        assertTrue(ShellOrbCatalog.pageOwnerMatches(canvas, "notebook/canvas/{canvasId}?nodeId={nodeId}", "board-A"))
        assertFalse(ShellOrbCatalog.pageOwnerMatches(canvas, "notebook/canvas/{canvasId}?nodeId={nodeId}", "board-B"))
        assertFalse(ShellOrbCatalog.pageOwnerMatches(canvas, "settings", "board-A"))
        assertFalse(ShellOrbCatalog.pageOwnerMatches(com.halo.app.ui.presence.ShellPageOwner("settings"), "advanced_settings", null))
        assertTrue(ShellOrbCatalog.pageOwnerMatches(com.halo.app.ui.presence.ShellPageOwner("advanced_settings"), "advanced_settings", null))
    }
    @Test fun globalMenuContainsEveryUsableExactDestinationWithoutInventingObjectsOrLabs() {
        val routes = ShellOrbCatalog.destinations(false).map { it.route }
        assertEquals(routes.size, routes.distinct().size)
        assertTrue(routes.containsAll(listOf("home", "screen", "notebook", "study", "studio", "activity", "devices", "settings")))
        assertTrue(routes.all { it in HaloScreenCatalog.screens && !it.contains('{') })
        assertFalse(routes.any { com.halo.app.ui.screens.settings.isDeveloperDestination(it) })
        assertFalse(routes.contains("workspace_handoff"))
    }

    @Test fun kidsMenuCannotExposeAdultDestinationsAsUsable() {
        assertEquals(setOf("home", "study"), ShellOrbCatalog.destinations(true).map { it.route }.toSet())
        assertTrue(ShellOrbCatalog.context("notebook/canvas/child-source", true).all { it.id == "study" })
    }

    @Test fun contextualEntriesUseExistingCatalogWithoutSynthesizingSelectionsOrOperations() {
        listOf("home", "screen", "notebook", "notebook/canvas/{canvasId}", "studio", "study", "devices").forEach { route ->
            val actions = ShellOrbCatalog.context(route, false)
            assertTrue(actions.size <= 3)
            actions.forEach { action -> action.catalog?.let { request ->
                assertTrue(HomeToolCatalog.descriptors.any { it.group == request.group && (request.entry == null || it.id == request.entry) })
            } }
        }
        assertEquals("canvas", ShellOrbCatalog.context("notebook/canvas/{canvasId}", false).first().id)
        assertEquals("study", ShellOrbCatalog.context("notebook/study/{sessionId}", false).first().id)
    }
    @Test fun kidsKeepsOnlyAlreadyAllowedChildPageProjections() {
        listOf("note", "canvas", "study").forEach { family ->
            assertTrue(ShellOrbCatalog.allowsPageProjection(com.halo.app.ui.presence.ShellPageOwner(family, "child-object"), true))
        }
        listOf("home", "screen", "notebook", "settings", "advanced_settings", "devices", "activity", "notifications", "memory", "studio", "file_tools").forEach { family ->
            val owner = com.halo.app.ui.presence.ShellPageOwner(family)
            assertFalse(family, ShellOrbCatalog.allowsPageProjection(owner, true))
            assertTrue(family, ShellOrbCatalog.allowsPageProjection(owner, false))
        }
        val note = com.halo.app.ui.presence.ShellPageOwner("note", "child-object")
        assertTrue(ShellOrbCatalog.allowsPageProjection(note, true) && ShellOrbCatalog.pageOwnerMatches(note, "notebook/note/{noteId}", "child-object"))
        assertFalse(ShellOrbCatalog.allowsPageProjection(note, true) && ShellOrbCatalog.pageOwnerMatches(note, "notebook/note/{noteId}", "different-object"))
        assertFalse(ShellOrbCatalog.pageOwnerMatches(note, "settings", "child-object"))
    }

    @Test fun kidsStudyFallbackUsesExactDirectRouteWithoutAdultHomeCatalogue() {
        val study = ShellOrbCatalog.context("home", true).single()
        assertNull(study.catalog)
        assertEquals("study", ShellOrbCatalog.directContextRoute(study, true))
        assertNull(ShellOrbCatalog.directContextRoute(study, false))
        assertNull(ShellOrbCatalog.directContextRoute(study.copy(enabled = false), true))
        assertNull(ShellOrbCatalog.directContextRoute(study.copy(id = "settings"), true))
        val adultStudy = ShellOrbCatalog.context("study", false).first()
        assertNotNull(adultStudy.catalog)
        assertNull(ShellOrbCatalog.directContextRoute(adultStudy, true))
    }

}
