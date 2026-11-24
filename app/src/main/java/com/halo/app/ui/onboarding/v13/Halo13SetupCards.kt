package com.halo.app.ui.onboarding.v13

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.halo.app.R
import com.halo.app.language.uiText
import com.halo.app.ui.onboarding.SetupProgressStore
import java.time.LocalDate
import org.json.JSONObject

private data class Prompt(val id: String, val title: String, val body: String, val priority: Int, val audience: Set<String>)

@Composable
internal fun Halo13SetupPrompts(store: SetupProgressStore, date: LocalDate, voiceReady: Boolean, calendarReady: Boolean,
    onCalendar: () -> Unit, onOpen: (String) -> Unit) {
    val assets = LocalContext.current.assets
    val russian = LocalConfiguration.current.locales[0].language == "ru"
    val prompts = remember(assets, russian) {
        runCatching {
            val text = assets.open("onboarding/setup-cards.json").bufferedReader().use { it.readText() }
            val rows = JSONObject(text).getJSONArray("cards")
            (0 until rows.length()).map { index ->
                val entry = rows.getJSONObject(index)
                val audience = entry.getJSONArray("forWho")
                Prompt(entry.getString("id"), entry.getString(if (russian) "titleRu" else "title"),
                    entry.getString(if (russian) "bodyRu" else "body"), entry.getInt("priority"),
                    (0 until audience.length()).map(audience::getString).toSet())
            }.sortedBy { it.priority }
        }.getOrDefault(emptyList())
    }
    val hiddenDate = remember(date) { mutableStateOf(false) }
    if (hiddenDate.value) return
    val audience = store.welcomeAudience()
    val eligible = prompts.filter { prompt ->
        (prompt.audience.isEmpty() || prompt.audience.any { it in audience } ||
            prompt.id == "kids" && "MY_CHILD" in audience) &&
            (prompt.id != "hear_you" || !voiceReady) && (prompt.id != "calendar" || !calendarReady)
    }
    val id = store.setupCard(date, eligible.map { it.id }) ?: return
    val prompt = eligible.firstOrNull { it.id == id } ?: return
    LaunchedEffect(date, id) { store.noteSetupCard(date, id) }
    fun respond(opened: Boolean) {
        store.noteSetupCard(date, id)
        store.respondToSetupCard(date, id, opened)
        hiddenDate.value = true
        if (opened) when (id) {
            "hear_you" -> onOpen("settings/voice/input")
            "brain_download" -> onOpen("settings/intelligence/on-device")
            "calendar" -> onCalendar()
            "look", "kids" -> onOpen("settings")
            "glasses" -> onOpen("devices")
            "study_sources" -> onOpen("study")
        }
    }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(prompt.title, style = MaterialTheme.typography.titleSmall)
            Text(prompt.body, style = MaterialTheme.typography.bodySmall)
            Row {
                TextButton(onClick = { respond(true) }, modifier = Modifier.heightIn(min = 48.dp)) { Text(uiText("Try it")) }
                TextButton(onClick = { respond(false) }, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text(stringResource(R.string.ob13_card_later))
                }
            }
        }
    }
}
