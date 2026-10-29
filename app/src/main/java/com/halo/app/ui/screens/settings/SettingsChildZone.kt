package com.halo.app.ui.screens.settings

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.halo.app.language.uiText
import com.halo.app.ui.icons.HaloGlyph
import com.halo.app.ui.presence.BindShellPageActions
import com.halo.app.ui.presence.BindShellUtilityBand
import com.halo.app.ui.presence.LocalShellHaloOrb
import com.halo.app.ui.presence.ShellPageAction
import com.halo.app.ui.presence.ShellPageOwner

/** Root-level binding, never attached to a lazy header that can be disposed by scrolling.
 * The real Navigation entry scopes every callback. This is presentation, not setting-write authority. */
@Composable
internal fun SettingsChildZone(title: String, onBack: () -> Unit, actions: List<ShellPageAction> = emptyList(),
    selectedObjectId: String? = null, selectedSourceVersion: String? = null,
    selectedToolIds: List<String>? = null, selectedTool: String? = null, contextLabel: String? = null) {
    if (!LocalShellHaloOrb.current) return
    val entry = androidx.lifecycle.compose.LocalLifecycleOwner.current as? androidx.navigation.NavBackStackEntry ?: return
    val route = entry.destination.route ?: return
    val providerId = if (route == "settings/intelligence/providers/{providerId}" ||
        route == "settings/intelligence/images/{providerId}") entry.arguments?.getString("providerId")
            ?.takeIf { it.length in 1..240 && it.matches(Regex("[A-Za-z0-9_.:/+-]+")) } else null
    val owner = ShellPageOwner(route, objectId = selectedObjectId ?: providerId,
        sourceIdentity = "$title:${selectedObjectId.orEmpty()}:${selectedSourceVersion.orEmpty()}" +
            contextLabel?.let { ":context:$it" }.orEmpty(), navigationEntryId = entry.id,
        canonicalSourceVersion = selectedSourceVersion, activeToolId = selectedTool,
        canonicalToolIds = selectedToolIds ?: com.halo.app.ui.presence.shellChildCanonicalTools(route))
    BindShellPageActions(owner, listOf(ShellPageAction("settings.child.back", "Back", HaloGlyph.PREVIOUS, onInvoke = onBack)) + actions)
    BindShellUtilityBand(owner) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)) {
            Text(listOfNotNull(uiText(title), contextLabel?.let { uiText(it) }).joinToString(" · "),
                style = MaterialTheme.typography.titleSmall)
        }
    }
}

internal fun settingsChildLink(id: String, title: String, action: () -> Unit) =
    ShellPageAction("settings.child.$id", title, HaloGlyph.SETTINGS, onInvoke = action)
