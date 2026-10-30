package com.halo.app.ui.screens.home

import com.halo.app.language.uiText

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.halo.sdk.provider.CognitiveSessionState
import com.halo.app.ui.theme.HaloDeepMint
import com.halo.app.ui.theme.HaloMint
import androidx.compose.ui.graphics.luminance
import com.halo.app.ui.icons.HaloIcon
import com.halo.app.ui.icons.HaloGlyph
import com.halo.app.ui.glass.components.HaloMarkdownText

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun HomeResponseCard(uiState: HomeUiState, responseText: String, isActiveSession: Boolean,
    cloudReply: Boolean = false, onCopy: () -> Unit, onSave: () -> Unit, onEdit: (() -> Unit)? = null) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val displayedText = uiState.nativeReceiptCopy?.let { uiText(it.english) } ?: responseText
    val identityKey = uiState.conversationId to (uiState.presentedIdentity?.turnId ?: uiState.presentedIdentity?.requestId)
    var readRequest by remember(identityKey) { mutableLongStateOf(0) }
    var shareFailed by remember(identityKey) { mutableStateOf(false) }
    val canAct = !isActiveSession && uiState.sessionState !is CognitiveSessionState.Failed && uiState.presentedIdentity?.turnId != null
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 0.dp),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    HaloIcon(
                        glyph = HaloGlyph.HALO,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = uiText("halo"),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
                // The current selection cannot provide provenance for a saved reply.
                // This badge comes only from the actual response/committed turn.
                if (cloudReply) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        HaloIcon(HaloGlyph.CLOUD, contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(6.dp))
                        Text(uiText("Cloud"), style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }

                if (canAct) HomeMessageActions(onCopy = onCopy,
                    onShare = { shareFailed = !shareHomeMessage(context, responseText) },
                    onSave = onSave, onRead = { readRequest++ }, onEdit = onEdit, identityKey = identityKey)
            }
            Spacer(Modifier.height(4.dp))
            val status = when {
                uiState.presentedIdentity?.continuationPending == true -> "Needs your review"
                uiState.presentedIdentity?.source == ResponsePresentationSource.COMMITTED_CONVERSATION && isActiveSession -> "Previous reply"
                uiState.presentedIdentity?.source == ResponsePresentationSource.STREAMING -> "Replying…"
                else -> null
            }
            status?.let {
                Text(uiText(it), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(8.dp))
            }
            if (uiState.presentedIdentity?.source == ResponsePresentationSource.INCOMPLETE) {
                Text(uiText("This reply is incomplete."), color = MaterialTheme.colorScheme.error)
                Spacer(Modifier.height(8.dp))
            }
            /*
             * MINING-P2-001: a committed structured answer renders as a bounded card, with the
             * assistant's own text kept underneath as the fallback. Text-only hosts, speech and
             * the notebook never see the component tree.
             */
            uiState.structuredPresentation?.let { presentation ->
                com.halo.app.interaction.StructuredPresentationRenderer(
                    presentation = presentation,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(12.dp))
            }
            if (uiState.sessionState is CognitiveSessionState.Streaming) {
                Text(responseText, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.fillMaxWidth())
            } else {
                HaloMarkdownText(markdown = displayedText, textColor = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.fillMaxWidth())
            }

        }
    }
    if (shareFailed) Text(androidx.compose.ui.res.stringResource(com.halo.app.R.string.home_share_failed),
        color = MaterialTheme.colorScheme.error)
    if (readRequest > 0) com.halo.app.ui.components.HaloVoiceTextActions(text = "", onText = {},
        allowDictation = false, readText = responseText, readRequest = readRequest, showReadAction = false)
}
