package com.halo.app.ui.screens.home

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import com.halo.app.R
import com.halo.app.language.uiText
import com.halo.app.ui.glass.components.HaloMarkdownText

internal fun shareHomeMessage(context: Context, text: String): Boolean = try {
    context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"; putExtra(Intent.EXTRA_TEXT, text)
    }, null))
    true
} catch (_: android.content.ActivityNotFoundException) { false }
catch (_: SecurityException) { false }

@Composable
internal fun HomeTranscriptMessage(identity: String, text: String, user: Boolean,
    onEdit: (() -> Unit)? = null, onSave: (() -> Unit)? = null, canRead: Boolean = false) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    var readRequest by remember(identity) { mutableLongStateOf(0) }
    var shareFailed by remember(identity) { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().padding(start = if (user) 32.dp else 0.dp,
        end = if (user) 0.dp else 16.dp)) {
        Surface(shape = MaterialTheme.shapes.medium,
            color = if (user) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.background) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(uiText(if (user) "You" else "halo"), Modifier.weight(1f),
                        style = MaterialTheme.typography.labelMedium,
                        color = if (user) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant)
                    HomeMessageActions(
                        onCopy = { clipboard.setText(AnnotatedString(text)) },
                        onShare = { shareFailed = !shareHomeMessage(context, text) },
                        onSave = onSave, onRead = if (!user && canRead) ({ readRequest++ }) else null,
                        onEdit = onEdit, identityKey = identity)
                }
                if (user) Text(text, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(bottom = 12.dp))
                else HaloMarkdownText(text, textColor = MaterialTheme.colorScheme.onSurface, modifier = Modifier.padding(bottom = 12.dp))
            }
        }
        if (shareFailed) Text(stringResource(R.string.home_share_failed), color = MaterialTheme.colorScheme.error)
        if (readRequest > 0) com.halo.app.ui.components.HaloVoiceTextActions(text = "", onText = {},
            allowDictation = false, readText = text, readRequest = readRequest, showReadAction = false)
    }
}
