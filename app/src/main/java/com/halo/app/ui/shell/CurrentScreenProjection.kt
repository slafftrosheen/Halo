package com.halo.app.ui.shell

import androidx.compose.runtime.*
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.halo.app.context.CurrentScreenContext
import com.halo.app.context.CurrentScreenMetadata
import com.halo.app.context.CurrentScreenSuite
import com.halo.app.ui.presence.ShellPageProjection

/** Same page lease as the carousel; contains identifiers only, never reads a document. */
@Composable
internal fun BindCurrentScreenContext(context: CurrentScreenContext, entryId: String?, route: String?,
    projection: ShellPageProjection?, lifecycle: Lifecycle, allowed: Boolean) {
    val owner = projection?.owner
    val family = owner?.routeFamily ?: route?.substringBefore('?') ?: "unknown"
    val suite = when {
        family == "home" || family == "screen" -> CurrentScreenSuite.HOME
        family == "note" || family == "file_tools" -> CurrentScreenSuite.OFFICE
        family == "canvas" || family == "studio" -> CurrentScreenSuite.STUDIO
        family == "study" -> CurrentScreenSuite.STUDY
        family == "memory" -> CurrentScreenSuite.MEMORY
        family == "activity" || family == "notifications" -> CurrentScreenSuite.ACTIVITY
        primaryNavigationRoute(route) == "notebook" -> CurrentScreenSuite.WORKSPACE
        primaryNavigationRoute(route) == "devices" -> CurrentScreenSuite.DEVICES
        primaryNavigationRoute(route) == "settings" -> CurrentScreenSuite.SETTINGS
        else -> CurrentScreenSuite.OTHER
    }
    val fullSelection = owner?.selectionIds.orEmpty().toList()
    val metadata = CurrentScreenMetadata(route ?: "unknown", suite, (owner?.activeToolId ?: family).replace(Regex("[^A-Za-z0-9_./:+-]"), "_").take(100),
        owner?.objectId, owner?.canonicalSourceVersion, fullSelection.take(100),
        projection?.actions?.filter { it.enabled }?.map { it.id }.orEmpty(), owner?.canonicalToolIds.orEmpty(), selectionCount = fullSelection.size, selectionTruncated = fullSelection.size > 100)
    val latest by rememberUpdatedState(metadata)
    val latestSelection by rememberUpdatedState(fullSelection)
    var lease by remember(context) { mutableStateOf<CurrentScreenContext.Lease?>(null) }
    DisposableEffect(context, entryId, route, owner?.sourceIdentity, owner?.objectId, lifecycle, allowed) {
        fun release() { lease?.let(context::detach); lease = null }
        fun resume() {
            release()
            if (allowed && entryId != null) {
                lease = context.attach(entryId)
                lease?.let { current ->
                    if (runCatching { context.publish(current, latest, latestSelection) }.getOrDefault(false).not()) release()
                }
            }
        }
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) resume()
            else if (event == Lifecycle.Event.ON_PAUSE || event == Lifecycle.Event.ON_STOP) release()
        }
        lifecycle.addObserver(observer)
        if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) resume()
        onDispose { lifecycle.removeObserver(observer); release() }
    }
    SideEffect { lease?.let { current ->
        // An imported external identifier must not crash the visible page or retain a stale snapshot.
        if (runCatching { context.publish(current, metadata, fullSelection) }.getOrDefault(false).not()) {
            context.detach(current); lease = null
        }
    } }
}
