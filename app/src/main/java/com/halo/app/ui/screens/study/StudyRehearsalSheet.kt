package com.halo.app.ui.screens.study

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.halo.app.R
import com.halo.app.ai.PrivacyMode
import com.halo.app.di.ManualDependencyGraph as Graph
import com.halo.app.language.uiText
import com.halo.app.study.StudySourceReference
import com.halo.app.ui.components.HaloTextField
import com.halo.app.ui.components.VoiceFieldKind
import com.halo.app.ui.zone6.Zone6ButtonSheet
import java.util.Locale

private val words = Regex("""[\p{L}\p{N}]{4,}""")

private val ignored = setOf("that", "this", "with", "from", "have", "there", "their", "about", "which",
    "were", "what", "when", "will", "your", "more", "such", "also", "through", "them", "they", "would",
    "если", "этот", "этого", "который", "только", "также", "может", "после", "перед", "более")

internal fun rehearsalTerms(text: String): List<String> = words.findAll(text.take(4000).lowercase(Locale.ROOT))
    .map { it.value }.filterNot { it in ignored }.groupingBy { it }.eachCount()
    .entries.sortedByDescending { it.value }.take(5).map { it.key }

@Composable
internal fun StudyRehearsalSheet(sources: List<StudySourceReference>, allowed: () -> Boolean, onDismiss: () -> Unit) {
    val available = sources.filter { !it.stale && !it.excerpt.isNullOrBlank() && it.version != null }
    var index by remember { mutableIntStateOf(0) }
    val source = available.getOrNull(index)
    val mode by Graph.privacyModes.mode.collectAsStateWithLifecycle()
    val question = stringResource(R.string.study_rehearsal_question, source?.title.orEmpty())
    val effectiveAuthority by rememberUpdatedState(allowed)
    val voice = mode == PrivacyMode.PRIVATE && effectiveAuthority()
    var response by remember(source?.objectId, source?.version, mode) { mutableStateOf("") }
    var checked by remember(source?.objectId, source?.version, mode) { mutableStateOf(false) }
    val terms = remember(source?.excerpt) { rehearsalTerms(source?.excerpt.orEmpty()) }
    val covered = remember(response, terms, checked) {
        if (!checked) emptyList() else {
            val said = words.findAll(response.lowercase(Locale.ROOT)).map { it.value }.toSet()
            terms.filter(said::contains)
        }
    }
    DisposableEffect(Unit) {
        onDispose { if (Graph.readAloud.state.value?.id == "study-rehearsal-question") Graph.readAloud.stop() }
    }
    Zone6ButtonSheet(stringResource(R.string.study_oral_rehearsal), onDismiss) {
        Column(Modifier.fillMaxWidth().weight(1f, fill = false).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (source != null && effectiveAuthority()) {
                Text(question, style = MaterialTheme.typography.titleMedium)
                source.locator?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
                TextButton(onClick = {
                    if (effectiveAuthority()) Graph.readAloud.start("study-rehearsal-question", question,
                        "provider.android.tts", cloudApproved = false, language = null,
                        beforeSentence = { effectiveAuthority() })
                }, modifier = Modifier.heightIn(min = 48.dp)) { Text(uiText("Read aloud")) }
                HaloTextField(response, { response = it.take(3000); checked = false }, VoiceFieldKind.TEXT,
                    voiceEnabled = voice, maxChars = 3000, minLines = 2, maxLines = 6,
                    readText = null, label = { Text(uiText("Your explanation")) })
                if (!voice) Text(stringResource(R.string.study_rehearsal_switch_local), style = MaterialTheme.typography.bodySmall)
                Button(onClick = { if (effectiveAuthority()) checked = true },
                    enabled = response.isNotBlank() && terms.isNotEmpty(), modifier = Modifier.heightIn(min = 48.dp)) {
                    Text(stringResource(R.string.coach_step_check))
                }
                if (checked) {
                    Text(stringResource(R.string.study_rehearsal_terms), style = MaterialTheme.typography.bodySmall)
                    Text(stringResource(R.string.study_covered) + " · " + covered.joinToString().ifBlank { "—" })
                    Text(stringResource(R.string.study_missing) + " · " + (terms - covered.toSet()).joinToString().ifBlank { "—" })
                    Text(source.excerpt.orEmpty().take(600), style = MaterialTheme.typography.bodyMedium)
                }
                if (available.size > 1) TextButton(onClick = { index = (index + 1) % available.size },
                    modifier = Modifier.heightIn(min = 48.dp)) { Text(uiText("Next")) }
            } else Text(uiText("Choose a current source for this lesson."))
        }
    }
}
