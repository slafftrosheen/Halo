package com.halo.app.ui.toolkit

import android.app.KeyguardManager
import android.app.Notification
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.halo.app.R
import com.halo.app.di.ManualDependencyGraph
import com.halo.app.data.notification.NotificationAccessPolicy
import com.halo.app.notification.NotificationListenerState
import com.halo.app.notification.NotificationReplyOutcome
import com.halo.app.notification.NotificationReplyRegistry
import com.halo.app.ui.components.HaloVoiceTextActions
import com.halo.app.ui.components.LocalPrivateSpeech
import com.halo.app.ui.icons.HaloGlyph
import com.halo.app.ui.presence.BindShellPageActions
import com.halo.app.ui.presence.ShellPageAction
import com.halo.app.ui.presence.ShellPageOwner
import com.halo.app.ui.zone6.haloEditablePageTitle
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

private data class MoveReplyReview(val key: String, val packageName: String, val actionFingerprint: String?, val label: String)

@Composable
internal fun HaloOnMoveScreen(onBack: () -> Unit, onOpen: (String) -> Unit) {
    val context = LocalContext.current
    val recentSource = remember { ManualDependencyGraph.notificationRepository.observeRecent() }
    val notices by recentSource.collectAsStateWithLifecycle(emptyList())
    val access by ManualDependencyGraph.notificationBridge.state.collectAsStateWithLifecycle()
    val current = notices.filter { it.active && it.category == Notification.CATEGORY_MESSAGE }
        .maxByOrNull { it.postedAt }
    val direction = notices.filter { it.active && it.category == "navigation" }
        .maxByOrNull { it.postedAt }
    var review by remember { mutableStateOf<MoveReplyReview?>(null) }
    var sent by remember { mutableStateOf(false) }
    var options by remember { mutableStateOf(false) }
    var destination by remember { mutableStateOf("") }
    var error by remember { mutableStateOf(false) }
    val kids by ManualDependencyGraph.kidsPreferences.enabled.collectAsStateWithLifecycle()
    var automatic by remember { mutableStateOf<Boolean?>(null) }
    var savingAutomatic by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(context, kids) { automatic = if (kids) null else HaloCarMode.autoOpen(context) }
    val headline = direction?.content?.body?.takeIf(String::isNotBlank)
        ?: direction?.content?.title?.takeIf(String::isNotBlank)
        ?: stringResource(R.string.halo_move_no_direction)
    val message = current?.content?.body ?: current?.content?.title
    val title = stringResource(R.string.halo_move_title)
    val help = stringResource(R.string.halo_move_help)
    val edit = stringResource(R.string.coach_edit_page)
    val exit = stringResource(R.string.halo_move_exit)
    BindShellPageActions(ShellPageOwner("on_move", sourceIdentity = current?.key, helpDescription = help,
        guidanceBlocked = review != null || options),
        listOf(ShellPageAction("page.on_move.appearance", edit, HaloGlyph.SETTINGS) { options = true },
            ShellPageAction("page.on_move.back", exit, HaloGlyph.PREVIOUS, true, onBack)))
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(18.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(title, Modifier.haloEditablePageTitle(), style = MaterialTheme.typography.headlineMedium)
        Text(headline, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.headlineMedium)
        Text(message ?: stringResource(R.string.halo_move_no_message),
            maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleLarge)
        if (message != null) CompositionLocalProvider(LocalPrivateSpeech provides true) {
            HaloVoiceTextActions(message, {}, allowDictation = false, readActionLabel = stringResource(R.string.halo_move_read))
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button({
                current?.let { item ->
                    val live = NotificationReplyRegistry.shared.replyActionFor(item.key)
                    review = MoveReplyReview(item.key, item.packageName, live?.actionFingerprint, item.appLabel.orEmpty())
                    sent = false
                    error = false
                }
            }, enabled = current != null, modifier = Modifier.weight(1f).heightIn(min = 120.dp)) {
                Text(stringResource(R.string.halo_move_reply))
            }
            OutlinedButton(onBack, modifier = Modifier.weight(1f).heightIn(min = 120.dp)) { Text(exit) }
        }
        TextButton({ options = true }, modifier = Modifier.heightIn(min = 48.dp)) {
            Text(stringResource(R.string.halo_move_navigation))
        }
        automatic?.takeUnless { kids }?.let { selected ->
            com.halo.app.ui.workspace.HaloWorkspaceToggleRow(
                title = stringResource(R.string.halo_move_automatic), checked = selected,
                subtitle = stringResource(R.string.halo_move_automatic_help), enabled = !savingAutomatic,
                onCheckedChange = { enabled ->
                    if (!savingAutomatic) {
                        savingAutomatic = true
                        error = false
                        scope.launch {
                            try { HaloCarMode.setAutoOpen(context, selected, enabled); automatic = enabled }
                            catch (cancelled: CancellationException) { throw cancelled }
                            catch (_: Exception) { error = true }
                            finally { savingAutomatic = false }
                        }
                    }
                },
            )
        }
        if (access != NotificationListenerState.Connected) TextButton({ onOpen("notification_settings") }) {
            Text(stringResource(R.string.halo_focus_message_access))
        }
        if (sent) Text(stringResource(R.string.halo_move_reply_handoff))
        if (error) Text(stringResource(R.string.page_layout_error), color = MaterialTheme.colorScheme.error)
    }
    review?.let { proposal ->
        val response = stringResource(R.string.halo_move_suggested_reply)
        val canDispatch = !proposal.actionFingerprint.isNullOrBlank()
        AlertDialog(onDismissRequest = { review = null },
            title = { Text(stringResource(R.string.halo_move_reply)) },
            text = {
                if (canDispatch) Column {
                    Text(proposal.label, style = MaterialTheme.typography.titleSmall)
                    Text(response)
                } else Text(stringResource(R.string.halo_move_reply_help))
            },
            confirmButton = { Button({
                review = null
                val keyguard = context.getSystemService(KeyguardManager::class.java)
                val graph = ManualDependencyGraph
                if (current?.key != proposal.key || graph.kidsPreferences.enabled.value ||
                    !graph.kidsPreferences.loadedState.value || keyguard?.isKeyguardLocked != false ||
                    !graph.notificationBridge.isAccessGranted() ||
                    graph.notificationPolicies.policyForApp(proposal.packageName) != NotificationAccessPolicy.ALLOW) error = true
                else if (!canDispatch) onOpen("notifications/${Uri.encode(proposal.key)}")
                else {
                    sent = graph.notificationReplyDispatcher.dispatch(context, proposal.key, response,
                        proposal.actionFingerprint) == NotificationReplyOutcome.DISPATCHED
                    error = !sent
                }
            }) {
                Text(stringResource(if (canDispatch) R.string.halo_move_send else R.string.halo_move_open_reply))
            } },
            dismissButton = { TextButton({ review = null }) { Text(stringResource(android.R.string.cancel)) } })
    }
    if (options) AlertDialog(onDismissRequest = { options = false },
        title = { Text(stringResource(R.string.halo_move_navigation)) }, text = {
            OutlinedTextField(destination, { destination = it.take(240) }, label = {
                Text(stringResource(R.string.halo_move_destination))
            })
        }, confirmButton = {
            Button({
                val route = Uri.parse("geo:0,0?q=${Uri.encode(destination.trim())}")
                runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, route)) }
                    .onSuccess { options = false }.onFailure { error = true; options = false }
            }, enabled = destination.isNotBlank()) { Text(stringResource(R.string.halo_move_maps)) }
        }, dismissButton = { TextButton({ options = false }) { Text(stringResource(android.R.string.cancel)) } })
}
