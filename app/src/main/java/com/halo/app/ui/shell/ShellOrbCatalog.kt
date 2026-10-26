package com.halo.app.ui.shell

import com.halo.app.ui.icons.HaloGlyph
import com.halo.app.ui.presence.*
import com.halo.app.ui.screens.home.HomeToolGroup
import com.halo.app.ui.screens.settings.isDeveloperDestination

/** Exact navigation whitelist shared with assistant discovery; never synthesizes object IDs. */
internal object ShellOrbCatalog {
    /** Only restores controls on already allowed child pages; entry/object checks remain mandatory. */
    fun allowsPageProjection(owner: ShellPageOwner, kids: Boolean): Boolean =
        !kids || owner.routeFamily in setOf("note", "canvas", "study")

    /** Kids Home does not host the adult catalogue; use its already allowed exact Study route. */
    fun directContextRoute(action: HaloOrbContextAction, kids: Boolean): String? =
        if (kids && action.enabled && action.id == "study" && action.catalog == null) "study" else null

    fun pageOwnerMatches(owner: ShellPageOwner, route: String?, objectId: String?): Boolean = when (owner.routeFamily) {
        "home" -> route == "home"
        "screen" -> route == "screen"
        "notebook" -> route == "notebook"
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
            (!kids || it in setOf("home", "study")) }
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
