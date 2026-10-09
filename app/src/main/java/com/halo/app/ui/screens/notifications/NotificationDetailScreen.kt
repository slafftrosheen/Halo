package com.halo.app.ui.screens.notifications

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
    var dismissConfirmation by remember { mutableStateOf(false) }
    val context = androidx.compose.ui.platform.LocalContext.current
    DisposableEffect(model, context) {
        onDispose { if (context.notificationActivity()?.isChangingConfigurations != true) model.leave() }
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).imePadding().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        TextButton(onClick = onBack) { Text(uiText("Back")) }
        val row = item
        if (row == null) {
            Text(uiText("Notification unavailable"), style = MaterialTheme.typography.headlineSmall)
            Text(uiText("This item expired or its app privacy policy no longer allows individual viewing."))
        } else {
            Text(row.appLabel, style = MaterialTheme.typography.labelLarge)
            Text(row.title ?: "Notification", style = MaterialTheme.typography.headlineSmall)
            Text(DateTimeFormatter.ofPattern("EEE, d MMM · HH:mm").withZone(ZoneId.systemDefault()).format(row.postedAt), style = MaterialTheme.typography.labelMedium)
            Text(if (row.active) "Active" else "Ended")
            row.body?.let { Text(it, style = MaterialTheme.typography.bodyLarge) }
            if (!state.connected) Text(uiText("Notification access is not currently connected"))
            val reply = row.reply as? ReplyCapability.Available
            if (reply != null) TextButton(onClick = { model.composing.value = true }, enabled = !busy) { Text(reply.label) }
            if (composing) {
                OutlinedTextField(draft, model::edit, label = { Text(uiText("Your reply")) }, enabled = reply != null && !busy,
                    modifier = Modifier.fillMaxWidth(), supportingText = { if (draft.length >= 450) Text("${draft.length}/500") })
                Row {
                    TextButton(onClick = { model.leave() }, enabled = !busy) { Text(uiText("Cancel reply")) }
                    Button(onClick = model::confirmReply, enabled = reply != null && draft.isNotBlank() && !busy) { Text(uiText("Review reply")) }
                }
                if (reply == null) Text(uiText("This reply action is no longer available"))
            }
            if (row.dismissible) TextButton(onClick = { dismissConfirmation = true }, enabled = !busy) { Text(uiText("Dismiss")) }
        }
        message?.let { Text(it) }
        TextButton(onClick = onPrivacy) { Text(uiText("Manage app notification privacy")) }
    }
    confirmation?.let { confirmed -> AlertDialog(onDismissRequest = { model.confirmation.value = null },
        title = { Text("Reply via ${item?.appLabel ?: "the app"}?") },
        text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { item?.title?.let { Text(it) }; Text(confirmed.text) } },
        dismissButton = { TextButton(onClick = { model.confirmation.value = null }) { Text(uiText("Cancel")) } },
        confirmButton = { TextButton(onClick = model::send, enabled = item?.reply is ReplyCapability.Available && !busy) { Text(uiText("Send")) } }) }
    if (dismissConfirmation) AlertDialog(onDismissRequest = { dismissConfirmation = false }, title = { Text(uiText("Dismiss notification?")) },
        text = { Text("Request dismissal from ${item?.appLabel ?: "the app"}.") },
        dismissButton = { TextButton(onClick = { dismissConfirmation = false }) { Text(uiText("Cancel")) } },
        confirmButton = { TextButton(onClick = { dismissConfirmation = false; model.dismiss() }, enabled = item?.dismissible == true && !busy) { Text(uiText("Dismiss")) } })
}

private fun android.content.Context.notificationActivity(): android.app.Activity? = when (this) {
    is android.app.Activity -> this
    is android.content.ContextWrapper -> baseContext.takeIf { it !== this }?.notificationActivity()
    else -> null
}
