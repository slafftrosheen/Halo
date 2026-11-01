package com.halo.app.ui.zone5

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.Saver
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import com.halo.app.language.uiText
import com.halo.app.ui.presence.ShellNextStep
import com.halo.sdk.runtime.Storage

/** UI preferences use the existing storage owner; no document, task or permission state lives here. */
@Stable
internal class Zone5GuideSession(private val storage: Storage? = null, shown: Boolean = false) {
    var presentationActive by mutableStateOf(false)
    var shownThisSession by mutableStateOf(shown)
        private set
    private var dismissed by mutableStateOf(emptySet<String>())
    var guidedFamily by mutableStateOf<String?>(null)
        private set
    var guidedObject by mutableStateOf<String?>(null)
        private set
    var paused by mutableStateOf(false)
        private set
    var preferenceFailure by mutableStateOf(false)
        private set
    fun suppressed(id: String) = id in dismissed || storage?.getBoolean("zone5.guide.$id", false) == true
    fun shown() { shownThisSession = true }
    fun dismiss(id: String) {
        dismissed = dismissed + id
        try {
            storage?.putBoolean("zone5.guide.$id", true)
            check(storage == null || storage.getBoolean("zone5.guide.$id", false))
        } catch (_: Exception) { preferenceFailure = true }
    }
    fun confirmed(ids: Set<String>) { ids.filterNot(::suppressed).forEach(::dismiss) }
    fun start(owner: com.halo.app.ui.presence.ShellPageOwner) {
        guidedFamily = owner.routeFamily; guidedObject = owner.objectId; paused = false
    }
    fun pause() { paused = true }
    fun exit() { guidedFamily = null; guidedObject = null; paused = false }
    fun applies(owner: com.halo.app.ui.presence.ShellPageOwner) = !paused && guidedFamily == owner.routeFamily &&
        (guidedObject == null || guidedObject == owner.objectId)
    fun observe(owner: com.halo.app.ui.presence.ShellPageOwner) {
        if (guidedFamily != null && (guidedFamily != owner.routeFamily || guidedObject != null && guidedObject != owner.objectId)) pause()
        else if (applies(owner) && guidedObject == null && owner.objectId != null) guidedObject = owner.objectId
    }
    companion object {
        fun saver(storage: Storage) = Saver<Zone5GuideSession, List<String>>(
            save = { listOf(it.shownThisSession.toString(), it.guidedFamily.orEmpty(), it.guidedObject.orEmpty(), it.paused.toString()) },
            restore = { values -> Zone5GuideSession(storage, values.getOrNull(0) == "true").apply {
                guidedFamily = values.getOrNull(1)?.takeIf(String::isNotEmpty)
                guidedObject = values.getOrNull(2)?.takeIf(String::isNotEmpty)
                paused = values.getOrNull(3) == "true"
            } })
    }
}

internal val LocalZone5GuideSession = staticCompositionLocalOf<Zone5GuideSession?> { null }

internal fun zone5SafeGuideAction(id: String) = id in setOf("page.office.open", "page.canvas.add", "page.note.insert",
    "page.study.new", "page.study.material", "page.memory.add", "page.studio.new", "page.studio.new-canvas",
    "page.studio.add-layer", "page.notebook.new-note", "page.kids.type")
internal fun zone5RequestedGuideAction(id: String) = zone5SafeGuideAction(id) || id in setOf(
    "page.studio.save", "page.studio.export", "page.note.view-read", "page.note.export", "page.office.export", "page.study.practice", "page.study.next")

internal fun zone5GuideAllowed(idle: Boolean, foreground: Boolean, focused: Boolean, compact: Boolean,
    keyboard: Boolean, disclosure: Boolean, guideLight: Boolean, eligible: Boolean) =
    idle && foreground && focused && !compact && !keyboard && !disclosure && guideLight && eligible

@Composable
internal fun Zone5GuideInline(guide: ShellNextStep, dismiss: () -> Unit) {
    val dismissLabel = uiText("Dismiss")
    val target = if (com.halo.app.ui.presence.LocalHaloZone5Navigation.current?.kids == true) 56.dp else 48.dp
    Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = MaterialTheme.shapes.small) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            Text(guide.message, Modifier.weight(1f).semantics { liveRegion = LiveRegionMode.Polite },
                color = MaterialTheme.colorScheme.onPrimaryContainer, style = MaterialTheme.typography.bodyMedium)
            TextButton(onClick = dismiss, modifier = Modifier.heightIn(min = target)) { Text(dismissLabel) }
        }
    }
}
