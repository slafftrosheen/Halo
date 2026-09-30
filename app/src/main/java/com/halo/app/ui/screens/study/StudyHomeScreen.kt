package com.halo.app.ui.screens.study

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.halo.app.ui.design.HaloScreenHeader

@Composable
fun StudyHomeScreen(model: StudyHomeViewModel, onResume: (String) -> Unit, onReview: (String) -> Unit, onNotebook: () -> Unit) {
    val sessions by model.sessions.collectAsStateWithLifecycle()
    val due by model.due.collectAsStateWithLifecycle()
    val busy by model.busy.collectAsStateWithLifecycle()
    val notice by model.notice.collectAsStateWithLifecycle()
    var topic by rememberSaveable { mutableStateOf("") }
    var language by rememberSaveable { mutableStateOf("") }
    var preferences by rememberSaveable { mutableStateOf(false) }
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
    LazyColumn(Modifier.widthIn(max = 720.dp).fillMaxSize().imePadding(), contentPadding = PaddingValues(24.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) {
        item { HaloScreenHeader("Study", "A little curiosity goes a long way.") }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text("What would you like to learn?", style = MaterialTheme.typography.headlineSmall)
                OutlinedTextField(topic, { topic = it.take(400) }, modifier = Modifier.fillMaxWidth(), minLines = 2,
                    placeholder = { Text("A topic, a question, or an exercise...") }, enabled = !busy)
                if (preferences) OutlinedTextField(language, { language = it.take(80) }, label = { Text("Explain in (optional)") }, modifier = Modifier.fillMaxWidth())
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { model.start(topic, language) }, enabled = topic.isNotBlank() && !busy) { Text(if (busy) "Starting..." else "Let's learn") }
                    TextButton(onClick = { preferences = !preferences }) { Text("Language") }
                }
                TextButton(onClick = onNotebook) { Text("Or choose a note from Workspace") }
                notice?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        }
        if (due.isNotEmpty()) item {
            Text("Ready to remember", style = MaterialTheme.typography.titleMedium)
            Text("${due.size} cards are ready for another look.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            TextButton(onClick = { onReview(due.first().sessionId) }) { Text("Review cards") }
        }
        if (sessions.isNotEmpty()) item { Text("Pick up where you left off", style = MaterialTheme.typography.titleMedium) }
        items(sessions, key = { it.id }) { session ->
            Surface(onClick = { onResume(session.id) }, modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceContainerLow) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(session.goal ?: "Study from a note", style = MaterialTheme.typography.titleMedium, maxLines = 2)
                    Text("Continue learning", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
    }
}
