package com.halo.app.ui.toolkit

import android.app.NotificationManager
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.halo.app.R
import com.halo.app.di.ManualDependencyGraph
import com.halo.app.everyday.briefing.DayReportSettings
import com.halo.app.ui.instruments.HaloInstrumentAction
import com.halo.app.ui.instruments.HaloInstrumentBar
import com.halo.app.ui.icons.HaloGlyph
import com.halo.app.ui.presence.BindShellPageActions
import com.halo.app.ui.presence.LocalShellHaloOrb
import com.halo.app.ui.presence.ShellPageAction
import com.halo.app.ui.presence.ShellPageOwner
import com.halo.app.ui.zone6.Zone6ButtonSheet
import com.halo.app.ui.zone6.haloEditablePageTitle
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun HaloFocusScreen(onBack: () -> Unit, onOpen: (String) -> Unit) {
    val context = LocalContext.current
    val owner = remember(context) { HaloFocusOwner.get(context) }
    val journal = remember(context) { DayReportSettings.get(context) }
    val note by journal.focusNote.collectAsStateWithLifecycle()
    val session by owner.state.collectAsStateWithLifecycle()
    val messageSource = remember { ManualDependencyGraph.notificationRepository.observeRecent() }
    val messages by messageSource.collectAsStateWithLifecycle(emptyList())
    val listener by ManualDependencyGraph.notificationBridge.state.collectAsStateWithLifecycle()
    val lifecycle by LocalLifecycleOwner.current.lifecycle.currentStateFlow.collectAsState()
    val systemAccess = remember(lifecycle, context) {
        Build.VERSION.SDK_INT >= 29 && context.getSystemService(NotificationManager::class.java)
            ?.isNotificationPolicyAccessGranted == true
    }
    var minutes by rememberSaveable { mutableIntStateOf(25) }
    var quiet by rememberSaveable { mutableStateOf(false) }
    var restMinutes by rememberSaveable { mutableIntStateOf(5) }
    var edit by remember { mutableStateOf(false) }
    var editBlocks by remember { mutableStateOf(false) }
    var finish by remember { mutableStateOf(false) }
    var reflection by remember { mutableStateOf("") }
    var error by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var left by remember { mutableLongStateOf(0) }
    val scope = rememberCoroutineScope()
    fun safely(operation: suspend () -> Unit) {
        if (busy) return
        busy = true
        error = false
        scope.launch {
            try { operation() }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { error = true }
            finally { busy = false }
        }
    }
    LaunchedEffect(session?.version) {
        val active = owner.current() ?: return@LaunchedEffect
        while (true) {
            left = active.left()
            if (left == 0L) {
                runCatching { owner.end(active.version, onlyExpired = true) }
                break
            }
            delay(250)
        }
    }
    val active = session
    val primary = stringResource(when {
        active == null -> R.string.halo_focus_start
        active.rest -> R.string.halo11_focus_end_rest
        else -> R.string.halo_focus_end
    })
    val primaryAction = {
        safely {
            if (active == null) owner.start(minutes, quiet && systemAccess)
            else owner.end(active.version)
        }
    }
    val edition = stringResource(R.string.coach_edit_page)
    val help = stringResource(R.string.halo_focus_help)
    BindShellPageActions(ShellPageOwner("focus", sourceIdentity = active?.version,
        canonicalSourceVersion = active?.version, helpDescription = help,
        statusLabel = if (active?.rest == true) stringResource(R.string.halo11_focus_rest)
            else if (active != null) stringResource(R.string.halo_focus_title) else null,
        guidanceBlocked = edit || editBlocks || finish || busy),
        listOf(ShellPageAction("page.focus.run", primary, HaloGlyph.PLAY, !busy, primaryAction),
            ShellPageAction("page.focus.appearance", edition, HaloGlyph.SETTINGS, active == null) { edit = true },
            ShellPageAction("page.focus.back", stringResource(R.string.sheets_back), HaloGlyph.PREVIOUS, !busy, onBack)))
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 20.dp), horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(vertical = 16.dp)) {
        item {
            Text(stringResource(R.string.halo_focus_title), Modifier.haloEditablePageTitle(), style = MaterialTheme.typography.headlineMedium)
            Text(help, style = MaterialTheme.typography.bodyMedium)
        }
        item {
            HaloInstrumentBar("focus", mapOf(
                "focus.timer" to HaloInstrumentAction(primaryAction, !busy, selected = active != null),
                "focus.blocks" to HaloInstrumentAction({ editBlocks = true }, !busy && active == null),
                "focus.quiet" to HaloInstrumentAction({ edit = true }, !busy && active == null, selected = quiet),
                "focus.done" to HaloInstrumentAction({ reflection = ""; finish = true }, !busy && active?.rest == false),
            ), primary = "focus.timer")
        }
        item {
            Box(Modifier.fillMaxWidth().heightIn(min = 174.dp), contentAlignment = Alignment.Center) {
                val progress = if (active == null) 1f else (left / (active.durationMinutes * 60_000f)).coerceIn(0f, 1f)
                CircularProgressIndicator(progress = { progress }, modifier = Modifier.size(156.dp), strokeWidth = 7.dp)
                val seconds = ((if (active == null) minutes * 60_000L else left) + 999) / 1000
                Text(java.lang.String.format(java.util.Locale.getDefault(), "%02d:%02d", seconds / 60, seconds % 60),
                    style = MaterialTheme.typography.headlineLarge.copy(fontFeatureSettings = "tnum"))
            }
        }
        if (active == null) item {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(5, 15, 25, 45, 60).forEach { duration ->
                    FilterChip(minutes == duration, { minutes = duration }, label = { Text(duration.toString()) })
                }
            }
            TextButton({ edit = true }) { Text(stringResource(R.string.halo_focus_options)) }
            OutlinedButton({
                safely { owner.start(restMinutes, quiet = false, rest = true) }
            }, enabled = !busy) { Text(stringResource(R.string.halo11_focus_start_rest, restMinutes)) }
        } else if (!active.rest) {
            item {
                Text(stringResource(R.string.halo_focus_messages), style = MaterialTheme.typography.titleMedium)
                Text(stringResource(if (active.quietApplied) R.string.halo_focus_quiet_active
                    else if (active.quietRequested) R.string.halo_focus_quiet_requested
                    else R.string.halo_focus_quiet_inactive))
                if (listener != com.halo.app.notification.NotificationListenerState.Connected) {
                    TextButton({ onOpen("notification_settings") }) { Text(stringResource(R.string.halo_focus_message_access)) }
                }
            }
            items(messages.filter { it.postedAt.toEpochMilli() >= active.startedAt && it.category == "msg" }.take(30),
                key = { it.key }) { message ->
                ListItem(headlineContent = { Text(message.content.title ?: message.appLabel.orEmpty()) },
                    supportingContent = { Text(message.appLabel.orEmpty()) })
            }
        }
        note?.let { kept ->
            item(key = "focus-note") {
                ListItem(headlineContent = { Text(kept.text) }, supportingContent = { Text(stringResource(R.string.halo11_focus_kept)) },
                    trailingContent = { TextButton({
                        safely { kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { journal.removeFocusNote(kept.sessionId) } }
                    }, enabled = !busy) { Text(stringResource(R.string.halo11_focus_remove)) } })
            }
        }
        if (error) item { Text(stringResource(R.string.page_layout_error), color = MaterialTheme.colorScheme.error) }
        item {
            if (!LocalShellHaloOrb.current) Button(primaryAction, enabled = !busy,
                modifier = Modifier.heightIn(min = 52.dp)) { Text(primary) }
        }
    }
    if (edit) Zone6ButtonSheet(edition, { edit = false }) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.halo_focus_quiet_help))
            if (systemAccess) Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.halo_focus_quiet), Modifier.weight(1f))
                Switch(quiet, { quiet = it })
            } else if (Build.VERSION.SDK_INT >= 29) OutlinedButton({
                runCatching { context.startActivity(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)) }
                    .onFailure { error = true }
            }) { Text(stringResource(R.string.halo_focus_access)) }
        }
    }
    if (editBlocks) Zone6ButtonSheet(stringResource(R.string.halo11_focus_blocks), { editBlocks = false }) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.halo11_focus_work_time), style = MaterialTheme.typography.titleSmall)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(5, 15, 25, 45, 60, 90).forEach { value ->
                    FilterChip(minutes == value, { minutes = value }, label = { Text(value.toString()) })
                }
            }
            Text(stringResource(R.string.halo11_focus_rest_time), style = MaterialTheme.typography.titleSmall)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(2, 5, 10, 15, 20).forEach { value ->
                    FilterChip(restMinutes == value, { restMinutes = value }, label = { Text(value.toString()) })
                }
            }
            TextButton({ editBlocks = false }) { Text(stringResource(android.R.string.ok)) }
        }
    }
    if (finish && active?.rest == false) AlertDialog(
        onDismissRequest = { finish = false },
        title = { Text(stringResource(R.string.halo11_focus_done)) },
        text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.halo11_focus_keep_hint))
            OutlinedTextField(reflection, {
                reflection = it.filterNot(Char::isISOControl).take(240)
            }, label = { Text(stringResource(R.string.halo11_focus_done)) }, singleLine = true)
        } },
        confirmButton = { TextButton({
            finish = false
            safely { check(owner.end(active.version, note = reflection.ifBlank { null })) }
        }, enabled = !busy) { Text(stringResource(R.string.halo11_focus_finish)) } },
        dismissButton = { TextButton({ finish = false }) { Text(stringResource(android.R.string.cancel)) } },
    )
}
