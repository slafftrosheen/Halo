package com.halo.app.privacy

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.halo.app.R
import com.halo.app.ui.icons.HaloGlyph
import com.halo.app.ui.presence.BindShellPageActions
import com.halo.app.ui.presence.LocalShellHaloOrb
import com.halo.app.ui.presence.ShellPageAction
import com.halo.app.ui.presence.ShellPageOwner
import com.halo.app.ui.toolkit.requireAdultToolkit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@Composable
internal fun HaloPauseScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val owner = remember(context) { HaloPauseOwner.get(context) }
    val current by owner.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var length by remember { mutableStateOf(HaloPauseLength.HALF_HOUR) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf(false) }
    val title = stringResource(R.string.halo_pause_title)
    val status = stringResource(if (current.paused) R.string.halo_pause_status else R.string.halo_pause_ready)
    val actionLabel = stringResource(if (current.paused) R.string.halo_pause_resume else R.string.halo_pause_title)
    val pauseInfo = stringResource(R.string.halo_pause_explain)
    val run: () -> Unit = {
        if (!busy) {
            val expected = current
            busy = true
            scope.launch {
                try {
                    val family = requireAdultToolkit(context)
                    val authorize: () -> Unit = { requireAdultToolkit(context, family) }
                    if (expected.paused) owner.resume(expected.version, authorize)
                    else owner.pause(expected.version, length, authorize)
                    error = false
                } catch (cancelled: CancellationException) { throw cancelled }
                catch (_: Exception) { error = true }
                finally { busy = false }
            }
        }
    }
    BindShellPageActions(ShellPageOwner("pause", sourceIdentity = current.version, canonicalSourceVersion = current.version,
        statusLabel = status, helpDescription = pauseInfo, guidanceBlocked = busy,
        canonicalToolIds = listOf("halo_pause_status", "halo_pause_start")),
        listOf(ShellPageAction("page.pause.commit", actionLabel, HaloGlyph.PAUSE, !busy, run),
            ShellPageAction("page.pause.back", stringResource(R.string.sheets_back), HaloGlyph.PREVIOUS, !busy, onBack)))
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(title, style = MaterialTheme.typography.headlineMedium)
        Text(pauseInfo)
        if (current.paused) {
            val time = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.SHORT)
                .withLocale(java.util.Locale.forLanguageTag(com.halo.app.di.ManualDependencyGraph.appLanguagePreferences.language.value.tag))
                .withZone(ZoneId.systemDefault()).format(Instant.ofEpochMilli(current.untilMillis))
            Text(stringResource(R.string.halo_pause_until, time), style = MaterialTheme.typography.titleLarge)
        } else {
            val labels = listOf(R.string.halo_pause_half_hour, R.string.halo_pause_one_hour, R.string.halo_pause_tomorrow)
            HaloPauseLength.entries.forEachIndexed { index, option ->
                Surface(onClick = { length = option }, enabled = !busy,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
                    Row(Modifier.padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(length == option, onClick = null, enabled = !busy)
                        Spacer(Modifier.width(10.dp))
                        Text(stringResource(labels[index]))
                    }
                }
            }
        }
        if (error) Text(stringResource(R.string.page_layout_error), color = MaterialTheme.colorScheme.error)
        if (!LocalShellHaloOrb.current) Button(run, enabled = !busy) { Text(actionLabel) }
    }
}
