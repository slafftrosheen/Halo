package com.halo.app.ui.shell

import android.net.Uri
import com.halo.app.data.workspace.AdaptivePageEntry
import com.halo.app.ui.everyday.EverydayRoutes
import com.halo.app.ui.onboarding.SetupStage

/** External links resolve to installed entry points. They never submit a prompt or grant access. */
internal object HaloDeepLinks {
    const val PREFIX = "halo-entry:"
    private val stableId = Regex("[A-Za-z0-9_.:-]{1,160}")
    private val tools = mapOf(
        "notes" to "file_tools", "canvas" to "canvases", "studio" to "studio", "planner" to EverydayRoutes.ORGANIZER,
        "study" to "study", "coach" to "study?photoCoach=true", "scan" to "scan", "album" to "album", "record" to "record", "sheets" to "sheets",
        "translate" to "translate", "maps" to "maps", "calc" to "calc", "measure" to "measure",
        "focus" to "focus", "vault" to "vault",
    )
    private val roots = setOf("home", "home/today", "notebook", "home/tools", "home/reads", "search", "halo/ask",
        "activity", "notebook/memory", "devices", "discovery", "settings/intelligence/providers", "family",
        "study", "guided_setup", "library/glasses", "maps/offline", "kids/home", "kids/adventure", "kids/homework", EverydayRoutes.SOURCE) +
        tools.values.map { it.substringBefore('?') }

    fun setupStage(route: String): SetupStage? = SetupStage.entries.firstOrNull { route == "guided_setup/${it.name}" }

    fun acceptsRequest(request: String): Boolean {
        if (!request.startsWith(PREFIX)) return false
        val target = request.removePrefix(PREFIX)
        if (AdaptivePageEntry.parse(target) != null) return true
        val route = target.substringBefore('?')
        if (route.startsWith("guided_setup")) return target == "guided_setup" || setupStage(target) != null
        if (Regex("^merchant/checkout/[a-f0-9-]{36}$").matches(route)) return true
        if (Regex("^study/collection/[A-Za-z0-9_.:-]{1,160}/[1-9][0-9]{0,3}$").matches(route)) return true
        return route in roots || listOf("library/item/", "library/collection/", "device/", "settings/intelligence/providers/")
            .any { prefix -> route.startsWith(prefix) && stableId.matches(Uri.decode(route.removePrefix(prefix))) }
    }

    fun resolve(uri: Uri): String? {
        if (uri.scheme != "halo" || !uri.isHierarchical || uri.encodedFragment != null ||
            uri.encodedAuthority != uri.host || uri.toString().length > 4096) return null
        val path = uri.pathSegments
        val query = uri.queryParameterNames
        fun fields(vararg allowed: String) = query.all { it in allowed && uri.getQueryParameters(it).size == 1 }
        fun tail(after: String) = path.takeIf { it.size == 2 && it[0] == after }?.get(1)
        fun checked(value: String?) = value?.takeIf(stableId::matches)
        val route = when (uri.host) {
            "home" -> when {
                path == listOf("today") && fields() -> "home/today"
                path == listOf("library") && fields() -> "notebook"
                path.size == 3 && path.take(2) == listOf("library", "c") && fields() ->
                    checked(path.last())?.let { "library/collection/$it" }
                path.size == 3 && path.take(2) == listOf("library", "item") && fields() ->
                    checked(path.last())?.let { "library/item/$it" }
                path == listOf("tools") && fields() -> "home/tools"
                path == listOf("reads") && fields() -> "home/reads"
                path == listOf("tools", "maps", "nav") && fields() -> "maps/offline"
                path == listOf("tools", "maps") && query.isNotEmpty() && query.size == 1 && fields("q", "to") ->
                    uri.getQueryParameter(query.single())?.trim()
                        ?.takeIf { it.isNotEmpty() && it.length <= 120 && it.none(Char::isISOControl) }
                        ?.let { "maps/offline?q=${Uri.encode(it)}" }
                tail("tools") != null && fields() -> tail("tools")?.let(tools::get)
                tail("reads") != null && fields("article") -> checked(tail("reads"))?.let { id ->
                    val item = uri.getQueryParameter("article")
                    if (item == null || item.isNotEmpty() && item.length <= 512 && item.none(Char::isISOControl))
                        EverydayRoutes.source(id, item) else null
                }
                else -> null
            }
            "search" -> if (path.isEmpty() && fields("q")) {
                val text = uri.getQueryParameter("q").orEmpty()
                if (text.length <= 200 && text.none(Char::isISOControl)) "search?q=${Uri.encode(text)}" else null
            } else null
            "halo" -> when {
                path == listOf("ask") && fields() -> "halo/ask"
                path == listOf("activity") && fields() -> "activity"
                path == listOf("memory") && fields() -> "notebook/memory"
                else -> null
            }
            "devices" -> if (!fields()) null else when {
                path.isEmpty() -> "devices"
                path == listOf("add") -> "discovery"
                path.size == 1 -> checked(path.single())?.let { "device/${Uri.encode(it)}" }
                else -> null
            }
            "settings" -> when {
                path == listOf("connections") && fields() -> "settings/intelligence/providers"
                path == listOf("family") && fields() -> "family"
                tail("connections") != null && fields() -> checked(tail("connections"))?.let { "settings/intelligence/providers/${Uri.encode(it)}" }
                else -> null
            }
            "study" -> when {
                path == listOf("today") && fields() -> "study"
                path == listOf("coach") && fields() -> "study?photoCoach=true"
                path.size == 4 && path[0] == "course" && path[2] == "lesson" && fields() -> {
                    val course = checked(path[1])
                    val lesson = path.last().toIntOrNull()
                    if (course != null && lesson != null && lesson in 1..9999) "study/collection/$course/$lesson" else null
                }
                else -> null
            }
            "album" -> if (path == listOf("glasses") && fields()) "library/glasses" else null
            "onboarding" -> when {
                !fields() -> null
                path.isEmpty() -> "guided_setup"
                path.size == 1 -> SetupStage.entries.firstOrNull { it.name.equals(path.single(), ignoreCase = true) }
                    ?.let { "guided_setup/${it.name}" }
                else -> null
            }
            "kids" -> when {
                path == listOf("home") && fields() -> "kids/home"
                path == listOf("adventure", "today") && fields() -> "kids/adventure"
                path == listOf("homework") && fields() -> "kids/homework"
                else -> null
            }
            else -> null
        }
        return route?.let { PREFIX + it }
    }
}
