package com.halo.app.ui.screens.home

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.halo.app.language.uiText
import com.halo.app.ui.design.HaloShapes
import com.halo.app.ui.icons.HaloGlyph
import com.halo.app.ui.icons.HaloIcon

/** Suggestions prepare an editable draft; only Send submits it. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun HomeWelcome(onDraft: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            WelcomeSuggestion(HaloGlyph.ACTIVITY, "Make a plan", "Help me plan my day. Ask me what I need to get done.", onDraft)
            WelcomeSuggestion(HaloGlyph.NOTE, "Find the words", "Help me write a message. Ask me who it is for and what I want to say.", onDraft)
            WelcomeSuggestion(HaloGlyph.QUESTION, "Learn something", "Help me understand a topic. Ask me what I want to learn.", onDraft)
        }
        Text(uiText("Pick a starting point. You can edit it before sending."),
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun WelcomeSuggestion(glyph: HaloGlyph, title: String, prompt: String, onDraft: (String) -> Unit) {
    val draft = uiText(prompt)
    AssistChip(onClick = { onDraft(draft) }, label = { Text(uiText(title)) },
        leadingIcon = { HaloIcon(glyph, null, Modifier.size(20.dp)) },
        shape = HaloShapes.pill, modifier = Modifier.heightIn(min = 48.dp))
}
