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
    var shownThisSession by mutableStateOf(shown)
        private set
    private var dismissed by mutableStateOf(emptySet<String>())
    fun suppressed(id: String) = id in dismissed || storage?.getBoolean("zone5.guide.$id", false) == true
    fun shown() { shownThisSession = true }
    fun dismiss(id: String) {
        dismissed = dismissed + id
        storage?.putBoolean("zone5.guide.$id", true)
    }
    companion object {
        fun saver(storage: Storage) = Saver<Zone5GuideSession, Boolean>(save = { it.shownThisSession },
            restore = { Zone5GuideSession(storage, it) })
    }
}

internal val LocalZone5GuideSession = staticCompositionLocalOf<Zone5GuideSession?> { null }

internal fun zone5SafeGuideAction(id: String) = id in setOf("page.office.open", "page.canvas.add", "page.note.insert",
    "page.study.new", "page.study.material", "page.memory.add", "page.studio.new", "page.studio.new-canvas",
    "page.studio.add-layer", "page.notebook.new-note")

internal fun zone5GuideAllowed(idle: Boolean, foreground: Boolean, focused: Boolean, compact: Boolean,
    keyboard: Boolean, disclosure: Boolean, guideLight: Boolean, eligible: Boolean) =
    idle && foreground && focused && !compact && !keyboard && !disclosure && guideLight && eligible

@Composable
internal fun Zone5GuideCallout(guide: ShellNextStep, dismiss: () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = MaterialTheme.shapes.medium) {
        Row(Modifier.fillMaxWidth().padding(start = 12.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            Text(guide.message, Modifier.weight(1f).semantics { liveRegion = LiveRegionMode.Polite },
                color = MaterialTheme.colorScheme.onPrimaryContainer, style = MaterialTheme.typography.bodySmall)
            TextButton(dismiss, Modifier.heightIn(min = 48.dp)) { Text(uiText("Dismiss")) }
        }
    }
}
