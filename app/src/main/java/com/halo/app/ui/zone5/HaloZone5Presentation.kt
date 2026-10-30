package com.halo.app.ui.zone5

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.halo.app.language.uiText
import com.halo.app.ui.icons.HaloGlyph
import com.halo.app.ui.icons.HaloIcon
import com.halo.app.ui.presence.HaloOrbContextAction

/** Call-site meaning wins over the legacy enum. This is presentation, never dispatch. */
internal fun zone5ActionIconId(action: HaloOrbContextAction): String = when (action.id.removePrefix("page:")) {
    "new-chat" -> "chat_new"
    "history" -> "chat_history"
    "mode" -> "chat_answer_mode"
    "clear-chat" -> "chat_clear"
    "attach" -> "action_attach"
    "page-context", "page.notebook.context" -> "action_add_context"
    "type-message" -> "office_edit_text"
    "new-note", "page.notebook.new-note", "page.screen.note" -> "office_new_note"
    "page.notebook.import" -> "action_import"
    "page.notebook.capture" -> "action_quick_capture"
    "page.notebook.chats" -> "library_saved_chat"
    "page.notebook.inbox" -> "library_inbox"
    "page.notebook.office", "page.notebook.files", "files" -> "tool_office"
    "page.notebook.studio", "studio" -> "tool_studio"
    "page.notebook.study", "study" -> "tool_study"
    "page.notebook.memory" -> "tool_memory"
    "page.notebook.new-canvas" -> "type_canvas"
    "page.screen.customize" -> "screen_arrange"
    "page.screen.add" -> "screen_add_widget"
    "page.screen.appearance" -> "setting_appearance"
    "page.screen.ambient" -> "screen_display_mode"
    "page.note.rename" -> "action_rename"
    "page.note.outline" -> "office_outline"
    "page.note.find" -> "office_find_replace"
    "page.canvas.fit" -> "canvas_fit"
    "page.canvas.find" -> "action_search"
    "page.memory.add" -> "memory_add"
    "page.memory.suggestions", "page.notebook.memory-review" -> "memory_review"
    "page.memory.history" -> "memory_timeline"
    "page.settings.brain" -> "setting_brain"
    "page.settings.voice" -> "setting_voice"
    "page.settings.vision" -> "setting_vision"
    "page.devices.add" -> "device_add"
    "page.study.material", "page.study.details" -> "study_source"
    else -> HaloZone5Icons.legacyId(action.glyph)
}

@Composable
internal fun Zone5Icon(iconId: String, fallback: HaloGlyph, modifier: Modifier = Modifier) {
    val resource = HaloZone5Icons.resource(iconId)
    if (resource == null) HaloIcon(fallback, null, modifier)
    else Icon(painterResource(resource), contentDescription = null, modifier = modifier)
}

/** One merged, wrapping target; no independent icon click or decorative action. */
@Composable
internal fun LabelledSatellite5(action: HaloOrbContextAction, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val label = uiText(action.title)
    val ink = if (action.enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = .6f)
    CompositionLocalProvider(LocalContentColor provides ink) {
        Column(modifier.clip(RoundedCornerShape(16.dp))
            .clickable(enabled = action.enabled, role = Role.Button, onClickLabel = label, onClick = onClick)
            .semantics(mergeDescendants = true) {
                contentDescription = label
                action.disabledReason?.let { stateDescription = it }
            }.padding(horizontal = 4.dp, vertical = 4.dp).heightIn(min = 48.dp),
            horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.size(48.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceContainerHigh),
                contentAlignment = Alignment.Center) { Zone5Icon(zone5ActionIconId(action), action.glyph, Modifier.size(24.dp)) }
            Spacer(Modifier.height(4.dp))
            Text(label, style = MaterialTheme.typography.labelLarge.copy(fontSize = 14.sp, lineHeight = 20.sp),
                textAlign = TextAlign.Center, softWrap = true)
        }
    }
}
