package com.halo.app.ui.presence

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.halo.app.language.uiText
import com.halo.app.ui.icons.HaloGlyph

/** Bind at the screen root, never in a disposable lazy header. No content read or authority is granted. */
@Composable
internal fun BindShellChildPage(title: String, onBack: () -> Unit,
    objectId: String? = null, sourceIdentity: String? = null, titleIsSource: Boolean = false,
    actions: List<ShellPageAction> = emptyList()): Boolean {
    if (!LocalShellHaloOrb.current) return false
    val entry = androidx.lifecycle.compose.LocalLifecycleOwner.current as? androidx.navigation.NavBackStackEntry ?: return false
    val route = entry.destination.route ?: return false
    val owner = ShellPageOwner(route, objectId, sourceIdentity ?: title, entry.id,
        canonicalToolIds = shellChildCanonicalTools(route))
    BindShellPageActions(owner, listOf(ShellPageAction("child.back", "Back", HaloGlyph.PREVIOUS, onInvoke = onBack)) + actions)
    BindShellUtilityBand(owner) {
        Text(if (titleIsSource) title else uiText(title), Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
            style = MaterialTheme.typography.titleSmall, maxLines = 2,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
    }
    return true
}
