package com.halo.app.ui.screens.home

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.halo.app.language.uiText
import com.halo.app.ui.icons.HaloGlyph
import com.halo.app.ui.icons.HaloIcon

/** Suggestions prepare an editable draft; only Send submits it. */
@Composable
internal fun HomeWelcome(onDraft: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(uiText("A little help for your day"), style = MaterialTheme.typography.headlineMedium)
            Text(uiText("Think it through, find the words, or learn something new."),
                style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            WelcomeSuggestion(HaloGlyph.ACTIVITY, "Make a plan", "Help me plan my day. Ask me what I need to get done.", onDraft)
            WelcomeSuggestion(HaloGlyph.NOTE, "Find the words", "Help me write a message. Ask me who it is for and what I want to say.", onDraft)
            WelcomeSuggestion(HaloGlyph.QUESTION, "Learn something", "Help me understand a topic. Ask me what I want to learn.", onDraft)
        }
    }
}

@Composable
private fun WelcomeSuggestion(glyph: HaloGlyph, title: String, prompt: String, onDraft: (String) -> Unit) {
    val draft = uiText(prompt)
    Surface(onClick = { onDraft(draft) }, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
        shape = MaterialTheme.shapes.small, color = MaterialTheme.colorScheme.surfaceContainerLow) {
        Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            HaloIcon(glyph, contentDescription = null, modifier = Modifier.size(24.dp), tint = MaterialTheme.colorScheme.primary)
            Text(uiText(title), style = MaterialTheme.typography.titleSmall)
        }
    }
}
