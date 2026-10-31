package com.halo.app.ui.zone5

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import com.halo.app.ui.fx.haloTrace

/** Call-site meaning wins over the legacy enum. This is presentation, never dispatch. */
internal fun zone5ActionIconId(action: HaloOrbContextAction): String = when (action.id.removePrefix("page:")) {
    "new-chat" -> "chat_new"
    "history" -> "chat_history"
    "mode" -> "chat_answer_mode"
    "clear-chat" -> "chat_clear"
    "attach" -> "action_attach"
    "page-context", "page.notebook.context" -> "action_add_context"
    "page.kids.type" -> "office_edit_text"
    "page.kids.help" -> "action_help"
    "page.kids.stop" -> "media_stop"
    "page.kids.grown-up" -> "action_lock"
    "type-message" -> "office_edit_text"
    "new-note", "page.notebook.new-note", "page.screen.note" -> "office_new_note"
    "page.notebook.import" -> "action_import"
    "page.notebook.organize", "page.notebook.selection-actions" -> "action_organize"
    "page.notebook.select-visible", "page.notebook.selection-done" -> "action_check"
    "tools" -> "action_tools"
    "page.notebook.capture" -> "action_quick_capture"
    "page.notebook.chats" -> "library_saved_chat"
    "page.notebook.inbox" -> "library_inbox"
    "page.notebook.office", "page.notebook.files", "files" -> "tool_office"
    "page.notebook.studio", "studio" -> "tool_studio"
    "page.notebook.study", "study" -> "tool_study"
    "page.notebook.memory" -> "tool_memory"
    "page.notebook.new-canvas" -> "type_canvas"
    "page.screen.customize" -> "screen_arrange"
    "page.screen.add" -> "screen_widgets"
    "page.screen.appearance" -> "setting_appearance"
    "page.screen.ambient" -> "screen_display_mode"
    "page.note.rename", "page.studio.rename", "page.canvas.rename" -> "action_rename"
    "page.note.insert" -> "action_add"
    "page.note.export", "page.office.export", "page.studio.export", "page.canvas.export" -> "action_export"
    "page.office.new" -> "office_document"
    "page.office.open" -> "library_open"
    "page.office.graphics" -> "tool_studio"
    "page.studio.add-layer" -> "studio_layer_add"
    "page.studio.layers" -> "studio_layers"
    "page.studio.new" -> "studio_shapes"
    "page.studio.new-canvas" -> "type_canvas"
    "page.studio.save" -> "action_save"
    "page.studio.draw" -> "studio_draw"
    "page.studio.text" -> "studio_text"
    "page.studio.shape" -> "studio_shapes"
    "page.studio.transform" -> "studio_transform"
    "page.studio.background" -> "setting_appearance"
    "page.studio.image" -> "action_import"
    "page.note.outline" -> "office_outline"
    "page.note.find" -> "office_find_replace"
    "page.canvas.fit" -> "canvas_fit"
    "page.canvas.add" -> "action_add"
    "page.canvas.arrange" -> "screen_arrange"
    "page.canvas.find" -> "action_search"
    "page.memory.add" -> "memory_add"
    "page.memory.suggestions", "page.notebook.memory-review" -> "memory_review"
    "page.memory.history" -> "memory_timeline"
    "object.memory.source" -> "study_source"
    "object.memory.edit" -> "office_edit_text"
    "object.memory.forget" -> "memory_forget"
    "page.study.new" -> "study_lesson"
    "page.study.practice" -> "study_practice"
    "page.study.read" -> "action_read_aloud"
    "page.study.stop-reading" -> "media_stop"
    "page.settings.brain" -> "setting_brain"
    "page.settings.voice" -> "setting_voice"
    "page.settings.vision" -> "setting_vision"
    "page.devices.add" -> "device_add"
    "page.study.material", "page.study.details" -> "study_source"
    else -> HaloZone5Icons.legacyId(action.glyph)
}

@Composable
internal fun Zone5Icon(iconId: String, fallback: HaloGlyph, modifier: Modifier = Modifier) {
    val resource = HaloZone5Icons.resource(when (iconId) {
        "tool_canvas" -> "screen_arrange"
        "tool_notes" -> "office_edit_text"
        else -> iconId
    })
    val colors = MaterialTheme.colorScheme
    val ink = when (zone5IconRole(iconId)) {
        Zone5IconRole.FILE -> if (com.halo.app.ui.theme.rememberVisualPreferences().higherContrast) colors.onSurface else zone5FileInk(colors)
        Zone5IconRole.TOOL -> colors.primary
        Zone5IconRole.DEFAULT -> LocalContentColor.current
    }
    val renderedInk = ink.copy(alpha = ink.alpha * LocalContentColor.current.alpha)
    if (resource == null) HaloIcon(fallback, null, modifier, tint = renderedInk)
    else Icon(painterResource(resource), contentDescription = null, modifier = modifier, tint = renderedInk)
}

/** One merged, wrapping target; no independent icon click or decorative action. */
@Composable
internal fun LabelledSatellite5(action: HaloOrbContextAction, onClick: () -> Unit, modifier: Modifier = Modifier, guided: Boolean = false,
    dense: Boolean = false, minimumTarget: androidx.compose.ui.unit.Dp = 48.dp) {
    val label = uiText(action.title)
    val reason = if (action.enabled) null else action.disabledReason?.let { uiText(it) }
    val ink = if (action.enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = .6f)
    val border = if (com.halo.app.ui.theme.rememberVisualPreferences().higherContrast) MaterialTheme.colorScheme.outline
        else MaterialTheme.colorScheme.primary.copy(alpha = if (action.enabled) .24f else .12f)
    CompositionLocalProvider(LocalContentColor provides ink) {
        Column(modifier.haloTrace(guided && action.enabled, MaterialTheme.colorScheme.primary, 16.dp, lapOnAppear = true)
            .clip(RoundedCornerShape(16.dp))
            .clickable(enabled = action.enabled, role = Role.Button, onClickLabel = label, onClick = onClick)
            .semantics(mergeDescendants = true) {
                contentDescription = label
                reason?.let { stateDescription = it }
            }.padding(horizontal = 4.dp, vertical = 4.dp).heightIn(min = minimumTarget),
            horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.size(if (dense) 32.dp else 48.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceContainerHigh)
                .border(1.dp, border, CircleShape),
                contentAlignment = Alignment.Center) { Zone5Icon(zone5ActionIconId(action), action.glyph, Modifier.size(24.dp)) }
            Spacer(Modifier.height(4.dp))
            Text(label, style = MaterialTheme.typography.labelLarge.copy(fontSize = if (dense) 12.sp else 14.sp, lineHeight = if (dense) 16.sp else 20.sp),
                textAlign = TextAlign.Center, softWrap = true)
        }
    }
}
