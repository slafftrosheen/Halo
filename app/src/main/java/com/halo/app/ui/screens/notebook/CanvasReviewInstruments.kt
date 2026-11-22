package com.halo.app.ui.screens.notebook

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.halo.app.R
import com.halo.app.data.notebook.CanvasNode
import com.halo.app.data.notebook.CanvasNodeType
import com.halo.app.data.notebook.CanvasReviewPin
import com.halo.app.data.notebook.NoteCommentReply
import com.halo.app.language.uiText
import com.halo.app.ui.zone6.Zone6ButtonSheet

@Composable
internal fun CanvasGroupInstruments(
    selection: List<CanvasNode>, locked: Set<String>, onAction: (String) -> Unit, onDismiss: () -> Unit,
) {
    val canChangeGeometry = selection.none { it.id in locked }
    Zone6ButtonSheet(uiText("Group and lock"), onDismiss) {
        TextButton({ onAction("Group selection") }, enabled = canChangeGeometry &&
            selection.size >= 2 && selection.none { it.type == CanvasNodeType.COMMENT }) { Text(uiText("Group selected objects")) }
        TextButton({ onAction("Ungroup") }, enabled = canChangeGeometry &&
            selection.any { it.type == CanvasNodeType.GROUP }) { Text(uiText("Ungroup")) }
        TextButton({ onAction("Lock") }, enabled = selection.any { it.id !in locked }) { Text(uiText("Lock position")) }
        TextButton({ onAction("Unlock") }, enabled = selection.any(CanvasNode::locked)) { Text(uiText("Unlock position")) }
    }
}

@Composable
internal fun CanvasPickPin(
    onPosition: (Offset) -> Unit, onCancel: () -> Unit, modifier: Modifier = Modifier,
) {
    val current by rememberUpdatedState(onPosition)
    Box(modifier.pointerInput(Unit) { detectTapGestures { current(it) } }) {
        Surface(Modifier.align(Alignment.TopCenter).padding(12.dp), shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.secondaryContainer) {
            Row(Modifier.padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(uiText("Tap the board to place a comment"))
                TextButton(onCancel) { Text(uiText("Cancel")) }
            }
        }
    }
}

@Composable
internal fun CanvasReviewPinIcon(pin: CanvasReviewPin?, modifier: Modifier = Modifier) {
    Box(modifier, contentAlignment = Alignment.Center) {
        Icon(painterResource(R.drawable.halo_ic_annotate),
            contentDescription = uiText(if (pin?.resolved == true) "Resolved comment" else "Open comment"),
            modifier = Modifier.fillMaxSize(.62f),
            tint = if (pin?.resolved == true) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.primary)
    }
}

@Composable
internal fun CanvasReviewIndex(
    nodes: List<CanvasNode>, enabled: Boolean, onPlace: () -> Unit, onOpen: (CanvasNode) -> Unit, onDismiss: () -> Unit,
) {
    val notes = remember(nodes) {
        nodes.filter { it.type == CanvasNodeType.COMMENT }.mapNotNull { node ->
            CanvasReviewPin.decode(node.text)?.let { pin -> node to pin }
        }.sortedBy { it.second.resolved }
    }
    Zone6ButtonSheet(uiText("Canvas comments"), onDismiss) {
        Button(onPlace, enabled = enabled) { Text(uiText("Place a comment pin")) }
        if (notes.isEmpty()) Text(uiText("There are no comments yet."))
        Column(Modifier.heightIn(max = 400.dp).verticalScroll(rememberScrollState())) {
            notes.forEach { (node, pin) ->
                TextButton(onClick = { onOpen(node) }) {
                    Text(uiText(if (pin.resolved) "Resolved comment" else "Open comment") + " · " + pin.text.take(80))
                }
            }
        }
    }
}

@Composable
internal fun CanvasReviewPinDialog(
    pin: CanvasReviewPin?, enabled: Boolean, onChange: (CanvasReviewPin) -> Unit, onDismiss: () -> Unit,
) {
    var author by remember { mutableStateOf("") }
    var message by remember { mutableStateOf("") }
    val defaultAuthor = uiText("You")
    AlertDialog(onDismissRequest = onDismiss,
        title = { Text(uiText(if (pin == null) "New comment" else "Comment pin")) },
        text = {
            Column(Modifier.heightIn(max = 430.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (pin != null) NoteReviewThread(pin.author, pin.text, pin.replies, pin.resolved)
                if (enabled && (pin == null || pin.replies.size < 25)) {
                    OutlinedTextField(author, { author = it.take(24) }, label = { Text(uiText("Initials")) }, singleLine = true)
                    OutlinedTextField(message, { message = it.take(1200) },
                        label = { Text(uiText(if (pin == null) "Comment" else "Reply")) }, minLines = 2, maxLines = 5)
                }
                if (pin != null) TextButton(onClick = { onChange(pin.copy(resolved = !pin.resolved)) }, enabled = enabled) {
                    Text(uiText(if (pin.resolved) "Reopen" else "Resolve"))
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val signature = author.trim().ifBlank { defaultAuthor }
                val value = message.trim()
                if (pin == null) onChange(CanvasReviewPin(signature, value))
                else onChange(pin.copy(replies = pin.replies + NoteCommentReply(signature, value)))
            }, enabled = enabled && message.isNotBlank() && (pin == null || pin.replies.size < 25)) {
                Text(uiText(if (pin == null) "Add comment" else "Send reply"))
            }
        },
        dismissButton = { TextButton(onDismiss) { Text(uiText("Close")) } },
    )
}

@Composable
internal fun NoteReviewThread(author: String, text: String, replies: List<NoteCommentReply>, resolved: Boolean) {
    Text(uiText(if (resolved) "Resolved comment" else "Open comment"), style = MaterialTheme.typography.labelMedium)
    Text(author.ifBlank { uiText("You") }, style = MaterialTheme.typography.labelSmall)
    Text(text)
    replies.forEach { reply ->
        HorizontalDivider()
        Text(reply.author.ifBlank { uiText("You") }, style = MaterialTheme.typography.labelSmall)
        Text(reply.text)
    }
}
