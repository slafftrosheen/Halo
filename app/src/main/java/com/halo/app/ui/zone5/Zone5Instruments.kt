package com.halo.app.ui.zone5

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.halo.app.ui.icons.HaloGlyph
import com.halo.app.ui.presence.HaloOrbContextAction
import com.halo.app.ui.presence.LocalShellHaloOrb

/** Presentation only: title/state come from the existing instrument and document owner. */
@Composable
internal fun Zone5InstrumentHeading(tool: String, icon: String, document: String? = null,
    state: String? = null, modifier: Modifier = Modifier) {
    val density = LocalDensity.current
    // The utility band still identifies the document while the keyboard needs the work area.
    if (LocalShellHaloOrb.current && WindowInsets.ime.getBottom(density) > 0) return
    Column(modifier.fillMaxWidth().padding(vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.primary) {
                Zone5Icon(icon, HaloGlyph.TOOL, Modifier.size(if (document == null) 32.dp else 24.dp))
            }
            Text(tool,
                style = if (document == null) MaterialTheme.typography.headlineMedium.copy(fontSize = 26.sp, lineHeight = 34.sp)
                    else MaterialTheme.typography.labelLarge,
                color = if (document == null) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.primary,
                modifier = Modifier.weight(1f).then(if (document == null) Modifier.semantics { heading() } else Modifier))
        }
        document?.let { Text(it, style = MaterialTheme.typography.headlineMedium.copy(fontSize = 26.sp, lineHeight = 34.sp),
            maxLines = 2, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.semantics { heading() }) }
        state?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}

internal data class Zone5LocalTab(val id: String, val label: String, val icon: String)

/** Local views remain named and selected without depending on color or orbital controls. */
@Composable
internal fun Zone5LocalTabs(tabs: List<Zone5LocalTab>, selected: String, onSelect: (String) -> Unit) {
    val large = LocalDensity.current.fontScale >= 1.4f
    val content: @Composable (Modifier) -> Unit = { tabModifier ->
        tabs.forEach { tab -> key(tab.id) {
            val active = selected == tab.id
            val ink = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
            Surface(onClick = { onSelect(tab.id) }, color = androidx.compose.ui.graphics.Color.Transparent,
                contentColor = ink, modifier = tabModifier.semantics { role = Role.Tab; this.selected = active }) {
                Column {
                    if (large) Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Zone5Icon(tab.icon, HaloGlyph.TOOL, Modifier.size(24.dp))
                        Text(tab.label, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                    } else Column(Modifier.fillMaxWidth().heightIn(min = 72.dp).padding(6.dp),
                        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Zone5Icon(tab.icon, HaloGlyph.TOOL, Modifier.size(24.dp))
                        Text(tab.label, style = MaterialTheme.typography.labelLarge,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                    }
                    HorizontalDivider(thickness = if (active) 3.dp else 1.dp,
                        color = if (active) ink else MaterialTheme.colorScheme.outlineVariant)
                }
            }
        } }
    }
    if (large) Column(Modifier.fillMaxWidth()) { content(Modifier.fillMaxWidth()) }
    else Row(Modifier.fillMaxWidth()) { content(Modifier.weight(1f)) }
}

/** A labeled local command. The callback remains the canonical owner's existing operation. */
@Composable
internal fun Zone5ActionButton(label: String, icon: String, enabled: Boolean = true,
    modifier: Modifier = Modifier, onClick: () -> Unit) {
    TextButton(onClick, enabled = enabled, modifier = modifier.heightIn(min = 48.dp).semantics { contentDescription = label }) {
        Zone5Icon(icon, HaloGlyph.ACTION, Modifier.size(24.dp))
        Spacer(Modifier.width(8.dp))
        Text(label, style = MaterialTheme.typography.labelLarge)
    }
}

/** Default satellites only. The original complete list still goes to the orb's All actions. */
internal fun zone5InstrumentOrbitActions(actions: List<HaloOrbContextAction>): List<HaloOrbContextAction> {
    val ids = actions.map { it.id.removePrefix("page:") }.toSet()
    val defaults = when {
        ids.any { it.startsWith("page.note.") } -> setOf("page.note.insert", "page.note.outline", "page.note.find")
        ids.any { it.startsWith("page.office.") } -> setOf("page.office.new", "page.office.open", "page.office.export")
        ids.any { it.startsWith("page.canvas.") } -> setOf("page.canvas.add", "page.canvas.arrange", "page.canvas.fit")
        ids.any { it.startsWith("page.studio.") } -> setOf("page.studio.new", "page.studio.new-canvas", "page.studio.add-layer", "page.studio.layers", "page.studio.export")
        ids.any { it.startsWith("page.memory.") } -> setOf("page.memory.add")
        ids.any { it.startsWith("object.memory.") } -> setOf("object.memory.source", "object.memory.edit")
        ids.any { it.startsWith("page.study.") } -> setOf("page.study.new", "page.study.material", "page.study.options", "page.study.details", "page.study.practice", "page.study.read", "page.study.stop-reading")
        else -> return actions
    }
    return actions.filter { it.id.removePrefix("page:") in defaults }
}
