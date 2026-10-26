package com.halo.app.ui.screens.notifications

import com.halo.app.ui.presence.BindShellChildPage
import com.halo.app.ui.presence.ShellPageAction
import com.halo.app.ui.components.HaloTextField
import com.halo.app.ui.components.VoiceFieldKind
import com.halo.app.language.uiText

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import com.halo.app.ui.design.*
import com.halo.app.ui.icons.HaloGlyph

@Composable
fun NotificationDetailScreen(model: NotificationDetailViewModel, onBack: () -> Unit, onPrivacy: () -> Unit) {
    RefreshNotifications(model.source)
    val item by model.item.collectAsStateWithLifecycle()
    val state by model.source.state.collectAsStateWithLifecycle()
    val draft by model.draft.collectAsStateWithLifecycle()
    val composing by model.composing.collectAsStateWithLifecycle()
    val confirmation by model.confirmation.collectAsStateWithLifecycle()
    val message by model.message.collectAsStateWithLifecycle()
    val busy by model.busy.collectAsStateWithLifecycle()
    var dismissConfirmation by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(false) }
    val shellHosted = BindShellChildPage("Notification", onBack,
        sourceIdentity = "${item?.key}:${item?.postedAt}:${item?.active}:${item?.dismissible}:$busy:$composing",
        actions = listOf(
            ShellPageAction("notification.privacy", "Manage app notification privacy", HaloGlyph.SECURITY, onInvoke = onPrivacy),
            ShellPageAction("notification.reply", "Reply", HaloGlyph.CONVERSATION,
                item?.reply is ReplyCapability.Available && !busy) { model.composing.value = true },
            ShellPageAction("notification.dismiss", "Dismiss", HaloGlyph.TRASH,
                item?.dismissible == true && !busy) { dismissConfirmation = true },
        ))
    val context = androidx.compose.ui.platform.LocalContext.current
    DisposableEffect(model, context) {
        onDispose { if (context.notificationActivity()?.isChangingConfigurations != true) model.leave() }
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).imePadding().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (!shellHosted) HaloScreenHeader(uiText("Notification"), navigation = onBack, glyph = HaloGlyph.CONVERSATION)
        val row = item
        if (row == null) {
            Text(uiText("Notification unavailable"), style = MaterialTheme.typography.headlineSmall)
            Text(uiText("This item expired or its app privacy policy no longer allows individual viewing."))
            // N02: a stale notification cannot be replied to or dismissed; offer a safe exit.
            TextButton(onClick = onBack, modifier = Modifier.heightIn(min = 48.dp)) {
                Text(uiText("Notifications"))
            }
        } else {
            Text(row.appLabel, style = MaterialTheme.typography.labelLarge)
            Text(row.title ?: uiText("Notification"), style = MaterialTheme.typography.headlineSmall)
            Text(DateTimeFormatter.ofPattern("EEE, d MMM · HH:mm").withZone(ZoneId.systemDefault()).format(row.postedAt), style = MaterialTheme.typography.labelMedium)
            Text(uiText(if (row.active) "Active" else "Ended"))
            row.body?.let { Text(it, style = MaterialTheme.typography.bodyLarge) }
            com.halo.app.ui.components.HaloReadAloud(listOfNotNull(row.title, row.body).joinToString("\n"))
            if (!state.connected) Text(uiText("Notification access is not currently connected"))
            val reply = row.reply as? ReplyCapability.Available
            if (reply != null) TextButton(onClick = { model.composing.value = true }, enabled = !busy) { Text(reply.label) }
            if (composing) {
                HaloTextField(draft, model::edit, label = { Text(uiText("Your reply")) }, enabled = reply != null && !busy,
                    modifier = Modifier.fillMaxWidth(), supportingText = { if (draft.length >= 450) Text("${draft.length}/500") }, kind = VoiceFieldKind.TEXT)
                Text(uiText("Your reply is sent only after you confirm."), style = MaterialTheme.typography.bodyMedium)
                HaloFocusAction(uiText("Review reply"), HaloGlyph.APPROVAL,
                    reply != null && draft.isNotBlank() && !busy, model::confirmReply)
                TextButton(onClick = { model.leave() }, enabled = !busy) { Text(uiText("Cancel reply")) }
                if (reply == null) Text(uiText("This reply action is no longer available"))
            }
            if (row.dismissible) TextButton(onClick = { dismissConfirmation = true }, enabled = !busy) { Text(uiText("Dismiss")) }
        }
        message?.let { Text(uiText(it.template, *it.arguments.toTypedArray())) }
        if (busy) HaloProgressPresentation(uiText("Requesting action…"))
        if (!shellHosted) TextButton(onClick = onPrivacy) { Text(uiText("Manage app notification privacy")) }
    }
    confirmation?.let { confirmed ->
        // N02: the remote app may replace or revoke its reply action while the
        // confirmation is open. The view model revalidates again at dispatch time;
        // the UI must not offer a knowingly stale Send button.
        val canConfirm = !busy && state.connected && item?.active == true &&
            (item?.reply as? ReplyCapability.Available)?.fingerprint == confirmed.fingerprint
        AlertDialog(
            onDismissRequest = { model.confirmation.value = null },
            title = { Text(uiText("Reply via %1\$s?", item?.appLabel ?: uiText("the app"))) },
            text = {
                Column(Modifier.heightIn(max = 320.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    item?.title?.let { Text(it) }
                    Text(confirmed.text)
                    if (!canConfirm) Text(uiText("This reply action is no longer available"),
                        color = MaterialTheme.colorScheme.error)
                    else Text(uiText("Your reply is sent only after you confirm."),
                        style = MaterialTheme.typography.bodySmall)
                }
            },
            dismissButton = {
                TextButton(onClick = { model.confirmation.value = null }) {
                    Text(uiText("Keep editing"))
                }
            },
            confirmButton = {
                TextButton(onClick = model::send, enabled = canConfirm) {
                    Text(uiText("Send"))
                }
            }
        )
    }
    if (dismissConfirmation) AlertDialog(onDismissRequest = { dismissConfirmation = false }, title = { Text(uiText("Dismiss notification?")) },
        text = { Text(uiText("Request dismissal from %1\$s.", item?.appLabel ?: uiText("the app"))) },
        dismissButton = { TextButton(onClick = { dismissConfirmation = false }) { Text(uiText("Cancel")) } },
        confirmButton = { TextButton(onClick = { dismissConfirmation = false; model.dismiss() }, enabled = item?.dismissible == true && !busy) { Text(uiText("Dismiss")) } })
}

private fun android.content.Context.notificationActivity(): android.app.Activity? = when (this) {
    is android.app.Activity -> this
    is android.content.ContextWrapper -> baseContext.takeIf { it !== this }?.notificationActivity()
    else -> null
}
