package com.halo.app.nativeactions

import android.app.KeyguardManager
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.SecureFlagPolicy
import com.halo.app.R
import com.halo.app.di.ManualDependencyGraph as Graph
import com.halo.app.language.uiText
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

@Composable
internal fun VaultSharePaperPicker(papers: List<VaultPaper>, onChoose: (VaultPaper) -> Unit, onClose: () -> Unit) {
    AlertDialog(onDismissRequest = onClose, properties = DialogProperties(securePolicy = SecureFlagPolicy.SecureOn),
        title = { Text(uiText("Choose a paper to share")) }, text = {
            Column(Modifier.heightIn(max = 350.dp).verticalScroll(rememberScrollState())) {
                papers.sortedBy { it.name }.forEach { paper ->
                    TextButton(onClick = { onChoose(paper) }) { Text(paper.name) }
                }
            }
        }, confirmButton = { TextButton(onClick = onClose) { Text(uiText("Cancel")) } })
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun VaultShareReview(paper: VaultPaper, busy: Boolean, failed: Boolean, onShare: (VaultCopyFormat, Int) -> Unit, onClose: () -> Unit) {
    var days by remember(paper.id, paper.revision) { mutableIntStateOf(1) }
    var format by remember(paper.id, paper.revision) {
        mutableStateOf(if (paper.photo) VaultCopyFormat.PHOTO else VaultCopyFormat.PAPER_DETAILS)
    }
    AlertDialog(onDismissRequest = { if (!busy) onClose() }, properties = DialogProperties(securePolicy = SecureFlagPolicy.SecureOn),
        title = { Text(uiText("Share a copy")) }, text = {
            Column(Modifier.heightIn(max = 470.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(paper.name, style = MaterialTheme.typography.titleMedium)
                if (paper.photo) FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(format == VaultCopyFormat.PHOTO, { format = VaultCopyFormat.PHOTO },
                        enabled = !busy, label = { Text(uiText("Photo copy")) })
                    FilterChip(format == VaultCopyFormat.PAPER_DETAILS, { format = VaultCopyFormat.PAPER_DETAILS },
                        enabled = !busy, label = { Text(uiText("Paper details")) })
                } else Text(uiText("The copy contains the saved paper details."), style = MaterialTheme.typography.bodySmall)
                Text(uiText("Allow opening the copy for this many days"))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    (1..7).forEach { option ->
                        FilterChip(days == option, { days = option }, enabled = !busy, label = { Text(option.toString()) })
                    }
                }
                Text(uiText("The original stays in Vault. After expiry, apps can no longer open this link. A file saved by a recipient remains with them."),
                    style = MaterialTheme.typography.bodySmall)
                if (failed) Text(uiText("Could not prepare the copy. Review the paper and try again."), color = MaterialTheme.colorScheme.error)
                if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            }
        }, confirmButton = { Button(enabled = !busy, onClick = { onShare(format, days) }) { Text(uiText("Share a copy")) } },
        dismissButton = { TextButton(enabled = !busy, onClick = onClose) { Text(uiText("Cancel")) } })
}

internal suspend fun shareVaultCopy(
    context: Context,
    store: HaloVaultStore,
    access: VaultSession,
    paper: VaultPaper,
    format: VaultCopyFormat,
    days: Int,
    chooserTitle: String,
    matchesReview: () -> Boolean,
) {
    val familyRevision = Graph.kidsPreferences.adultActionRevision()
    val modeRevision = Graph.privacyModes.revision
    val pause = Graph.haloPause
    val pauseVersion = pause.state.value.version
    fun authorized() {
        access.requireOpen()
        Graph.kidsPreferences.requireAdultAction(familyRevision)
        check(!pause.isPaused() && pause.state.value.version == pauseVersion && modeRevision == Graph.privacyModes.revision)
        check(context.getSystemService(KeyguardManager::class.java)?.isDeviceLocked == false)
    }
    var staged: Uri? = null
    var presented = false
    try {
        check(matchesReview())
        withContext(Dispatchers.IO) {
            authorized()
            val fresh = store.current(access, paper)
            val bytes = if (format == VaultCopyFormat.PHOTO) store.viewPhoto(access, fresh) else vaultPaperDetails(context, fresh)
            try {
                staged = HaloVaultCopies.stage(context, fresh.name, format, bytes, days, ::authorized)
            } finally { bytes.fill(0) }
        }
        check(matchesReview())
        authorized()
        val content = requireNotNull(staged)
        val transfer = Intent(Intent.ACTION_SEND).apply {
            type = format.mime
            putExtra(Intent.EXTRA_STREAM, content)
            clipData = ClipData.newRawUri(chooserTitle, content)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(transfer, chooserTitle))
        presented = true
    } finally {
        val copy = staged
        if (copy != null && !presented) withContext(NonCancellable + Dispatchers.IO) {
            HaloVaultCopies.revoke(context, HaloVaultCopies.token(context, copy))
        }
    }
}

private fun vaultPaperDetails(context: Context, paper: VaultPaper): ByteArray {
    val locale = context.resources.configuration.locales[0]
    val date = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale)
    return buildString {
        appendLine(paper.name)
        appendLine(context.getString(paper.kind.label))
        paper.bought?.let { appendLine(context.getString(R.string.halo_vault_bought_on, it.format(date))) }
        paper.ends?.let { appendLine(context.getString(R.string.halo_vault_end_on, it.format(date))) }
    }.toByteArray()
}
