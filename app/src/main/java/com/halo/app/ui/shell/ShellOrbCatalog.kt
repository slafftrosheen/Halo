package com.halo.app.ui.shell

import com.halo.app.ui.icons.HaloGlyph
import com.halo.app.ui.presence.*
import com.halo.app.ui.screens.home.HomeToolGroup
import com.halo.app.ui.screens.settings.isDeveloperDestination

/** Exact navigation whitelist shared with assistant discovery; never synthesizes object IDs. */
internal object ShellOrbCatalog {
    /** Adult shell roots only. Child mode retains its existing allowlist. */
    fun globalDestinations(kids: Boolean): List<HaloOrbDestination> = if (kids) destinations(true).filterNot {
        it.route in setOf("screen/brief", "screen/clocks")
    } else listOf(
        HaloOrbDestination("screen", "HaloScreen", HaloGlyph.HOME),
        HaloOrbDestination("home", "Home", HaloGlyph.HOME),
        HaloOrbDestination("notebook", "Workspace", HaloGlyph.NOTE),
        HaloOrbDestination("devices", "Devices", HaloGlyph.DEVICE),
        HaloOrbDestination("activity", "Activities & notifications", HaloGlyph.ACTIVITY),
        HaloOrbDestination("settings", "Settings", HaloGlyph.SETTINGS),
    )
    /** Direct instrument destinations are a presentation subset of the canonical catalogue. */
    fun workspaceDestinations(kids: Boolean): List<HaloOrbDestination> = if (kids) emptyList() else
        listOfNotNull(destinations(false).firstOrNull { it.route == "screen/organizer" },
            destinations(false).firstOrNull { it.route == "studio" },
            HaloOrbDestination("notebook?filter=notes", "HaloNotes", HaloGlyph.NOTE)) +
            listOf("study", "notebook/memory", "canvases").mapNotNull { route -> destinations(false).firstOrNull { it.route == route } }
    fun organizerDestinations(kids: Boolean): List<HaloOrbDestination> =
        listOf("screen/brief", "screen/clocks", "screen/sources").mapNotNull { route ->
            destinations(kids).firstOrNull { it.route == route }
        }
    /** Only restores controls on already allowed child pages; entry/object checks remain mandatory. */
    fun allowsPageProjection(owner: ShellPageOwner, kids: Boolean): Boolean =
        !kids || owner.routeFamily in setOf("kids", "note", "canvas", "study", "everyday_brief", "everyday_clocks", "everyday_organizer", "everyday_source", "reference_libraries")

    /** Kids Home does not host the adult catalogue; use its already allowed exact Study route. */
    fun directContextRoute(action: HaloOrbContextAction, kids: Boolean): String? =
        if (kids && action.enabled && action.id == "study" && action.catalog == null) "study" else null

    fun pageOwnerMatches(owner: ShellPageOwner, route: String?, objectId: String?): Boolean = when (owner.routeFamily) {
        "home", "kids" -> route == "home"
        "screen" -> route == "screen"
        "everyday_organizer" -> route?.substringBefore("?") == "screen/organizer" && owner.objectId==objectId
        "everyday_clocks" -> route?.substringBefore("?") == "screen/clocks"
        "everyday_brief" -> route?.substringBefore("?") == "screen/brief"
        "reference_libraries" -> route?.substringBefore("?") == "screen/organizer/libraries"
        "everyday_sources" -> route == "screen/sources"
        "everyday_source" -> route?.substringBefore("?") == "screen/source" && owner.objectId == objectId
        "notebook" -> route?.substringBefore("?") == "notebook"
        "canvas" -> route?.startsWith("notebook/canvas/") == true && owner.objectId == objectId
        "note" -> route?.startsWith("notebook/note/") == true && owner.objectId == objectId
        "studio" -> route?.substringBefore("?") == "studio"
        "file_tools" -> route?.substringBefore("?") == "file_tools"
        "memory" -> route?.substringBefore("?") == "notebook/memory"
        "study" -> (route?.startsWith("study") == true || route?.startsWith("notebook/study/") == true) &&
            (objectId == null || owner.objectId == objectId)
        "settings" -> route == "settings"
        "advanced_settings" -> route == "advanced_settings"
        "devices" -> route == "devices"
        "activity" -> route?.startsWith("activity") == true
        "notifications" -> route == "notifications"
        else -> owner.routeFamily == route
    }

    fun destinations(kids: Boolean): List<HaloOrbDestination> = HaloScreenCatalog.screens
        .filterKeys { !isDeveloperDestination(it) && it !in setOf("mini_halo", "workspace_handoff") &&
            (!kids || it in setOf("home", "study", "screen/brief", "screen/clocks", "screen/organizer")) }
        .map { (route, title) -> HaloOrbDestination(route, title, when (primaryNavigationRoute(route)) {
            "home", "screen" -> HaloGlyph.HOME
            "notebook", "studio", "file_tools" -> HaloGlyph.NOTE
            "devices" -> HaloGlyph.DEVICE
            "activity" -> HaloGlyph.ACTIVITY
            else -> if (route == "study") HaloGlyph.QUESTION else HaloGlyph.SETTINGS
        }) }

    fun context(route: String?, kids: Boolean): List<HaloOrbContextAction> {
        if (kids) return listOf(HaloOrbContextAction("study", "Study", HaloGlyph.QUESTION))
        fun tools(id: String, title: String, group: HomeToolGroup, entry: String? = null) =
            HaloOrbContextAction(id, title, HaloGlyph.TOOL, HaloToolCatalogRequest(group, entry))
        return when {
            route?.startsWith("notebook/canvas/") == true -> listOf(
                tools("canvas", "Canvas tools", HomeToolGroup.WORK, "work.canvas"),
                tools("images", "Create", HomeToolGroup.CREATE),
                tools("files", "File tools", HomeToolGroup.WORK, "work.files"))
            route?.startsWith("studio") == true -> listOf(tools("studio", "Studio", HomeToolGroup.CREATE, "create.studio"),
                tools("files", "File tools", HomeToolGroup.WORK, "work.files"))
            route?.startsWith("study") == true || route?.startsWith("notebook/study/") == true -> listOf(
                tools("study", "Study", HomeToolGroup.WORK, "work.study"),
                tools("sources", "Workspace", HomeToolGroup.WORK, "work.notebook"))
            route == "devices" || route?.startsWith("device/") == true -> listOf(tools("phone", "On this phone", HomeToolGroup.PHONE))
            route == "home" -> listOf(HaloOrbContextAction("new-note", "New note", HaloGlyph.NOTE),
                tools("find", "Find", HomeToolGroup.FIND), tools("work", "Workspace", HomeToolGroup.WORK))
            else -> emptyList()
        }
    }
}
