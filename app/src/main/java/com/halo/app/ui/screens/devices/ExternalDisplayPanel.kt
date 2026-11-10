package com.halo.app.ui.screens.devices

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.halo.app.R
import com.halo.app.ui.components.HaloActionButton
import com.halo.app.ui.components.HaloActionStyle
import com.halo.app.ui.components.HaloTextField
import com.halo.app.ui.components.VoiceFieldKind
import com.halo.app.ui.design.HaloSpacing
import com.halo.host.android.display.AndroidExternalDisplayPresenter
import com.halo.sdk.runtime.display.ExternalDisplayReceipt
import com.halo.sdk.runtime.display.ExternalDisplaySession
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/** A selected Android presentation display is an output route, not verified XREAL identity. */
@Composable
internal fun ExternalDisplayPanel(owner: AndroidExternalDisplayPresenter) {
    val state by owner.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var resumed by remember(lifecycle) { mutableStateOf(lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) }
    var selectedToken by remember { mutableStateOf<String?>(null) }
    var working by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<String?>(null) }
    var resultVersion by remember { mutableStateOf<Long?>(null) }
    var failed by remember { mutableStateOf(false) }
    // Display text is an ephemeral draft, never restored across process death or a new connection.
    var draft by remember { mutableStateOf("") }
    var draftEpoch by remember { mutableStateOf(0) }
    val testCard = stringResource(R.string.external_display_test_content)
    val availableSelection = state.endpoints.any { it.token == selectedToken }
    val canSend = resumed && state.foreground && availableSelection && !working
    val selectedOutput = state.activeToken != null && state.activeToken == selectedToken

    LaunchedEffect(state.endpoints, state.foreground) {
        // A replacement display must be selected again, even if Android reused its numeric id.
        if (!state.foreground || !availableSelection) { selectedToken = null; draft = ""; draftEpoch++ }
        result = null
        failed = false
    }
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) resumed = true
            if (event == Lifecycle.Event.ON_PAUSE || event == Lifecycle.Event.ON_STOP || event == Lifecycle.Event.ON_DESTROY) {
                resumed = false
                selectedToken = null
                draft = ""
                draftEpoch++
                result = null
            }
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }

    fun perform(operation: suspend () -> ExternalDisplayReceipt) {
        if (working) return
        working = true
        failed = false
        result = null
        scope.launch {
            try {
                val receipt = operation()
                result = receipt.message
                resultVersion = receipt.sourceVersion
            }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { failed = true; result = null }
            finally { working = false }
        }
    }

    fun changePage(page: Int) {
        val token = state.activeToken ?: return
        if (!selectedOutput || !resumed || !state.foreground || page !in 1..state.pageCount) return
        val revision = state.revision
        perform { owner.page(token, page, revision) }
    }

    Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerLow) {
        Column(Modifier.fillMaxWidth().padding(HaloSpacing.lg), verticalArrangement = Arrangement.spacedBy(HaloSpacing.md)) {
            Text(stringResource(R.string.external_display_title), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(R.string.external_display_help), style = MaterialTheme.typography.bodyMedium)
            if (!state.foreground) {
                Text(stringResource(R.string.external_display_foreground), style = MaterialTheme.typography.bodySmall)
            }
            if (state.endpoints.isEmpty()) {
                Text(stringResource(R.string.external_display_empty), style = MaterialTheme.typography.bodySmall)
            } else {
                Column(Modifier.fillMaxWidth().selectableGroup()) {
                    state.endpoints.forEach { endpoint ->
                        Row(
                            Modifier.fillMaxWidth().heightIn(min = 48.dp)
                                .selectable(selected = selectedToken == endpoint.token,
                                    enabled = resumed && state.foreground && !working, role = Role.RadioButton,
                                    onClick = { selectedToken = endpoint.token; result = null; failed = false })
                                .padding(vertical = HaloSpacing.sm),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(HaloSpacing.md),
                        ) {
                            RadioButton(selected = selectedToken == endpoint.token, onClick = null,
                                enabled = resumed && state.foreground && !working)
                            Column(Modifier.weight(1f)) {
                                Text(endpoint.name, style = MaterialTheme.typography.titleSmall)
                                Text(stringResource(R.string.external_display_dimensions, endpoint.width, endpoint.height),
                                    style = MaterialTheme.typography.bodySmall)
                                if (state.activeToken == endpoint.token) {
                                    Text(stringResource(R.string.external_display_active), style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                    }
                }
            }
            // The shared voice field has its own ephemeral spoken preview. Remount it
            // with the draft so hidden previews cannot survive a display/session reset.
            key(draftEpoch) { HaloTextField(
                value = draft,
                onValueChange = { text ->
                    if (resumed && canSend) {
                        val limit = ExternalDisplaySession.MAX_TEXT_LENGTH
                        val bounded = text.take(limit)
                        draft = if (bounded.isNotEmpty() && Character.isHighSurrogate(bounded.last())) bounded.dropLast(1) else bounded
                    }
                },
                kind = VoiceFieldKind.TEXT,
                label = { Text(stringResource(R.string.external_display_text_label)) },
                supportingText = {
                    Text(stringResource(R.string.external_display_text_count, draft.length, ExternalDisplaySession.MAX_TEXT_LENGTH))
                },
                enabled = canSend,
                minLines = 3,
                maxLines = 6,
                maxChars = ExternalDisplaySession.MAX_TEXT_LENGTH,
                modifier = Modifier.fillMaxWidth(),
            ) }
            Text(stringResource(R.string.external_display_text_help), style = MaterialTheme.typography.bodySmall)
            HaloActionButton(
                onClick = {
                    val token = selectedToken ?: return@HaloActionButton
                    val revision = state.revision
                    val text = draft
                    perform { owner.show(token, text, revision) }
                },
                enabled = canSend && draft.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
            ) { Text(stringResource(R.string.external_display_show_text)) }
            HaloActionButton(
                onClick = {
                    val token = selectedToken ?: return@HaloActionButton
                    val revision = state.revision
                    perform { owner.show(token, testCard, revision) }
                },
                enabled = canSend,
                style = HaloActionStyle.TONAL,
                modifier = Modifier.fillMaxWidth(),
            ) { Text(stringResource(R.string.external_display_show_test)) }
            if (selectedOutput && state.currentPage > 0 && state.pageCount > 0) {
                Text(stringResource(R.string.external_display_page, state.currentPage, state.pageCount),
                    style = MaterialTheme.typography.bodyMedium)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(HaloSpacing.md)) {
                    HaloActionButton(
                        onClick = { changePage(state.currentPage - 1) },
                        enabled = resumed && state.foreground && !working && state.currentPage > 1,
                        style = HaloActionStyle.SECONDARY,
                        modifier = Modifier.weight(1f),
                    ) { Text(stringResource(R.string.external_display_previous)) }
                    HaloActionButton(
                        onClick = { changePage(state.currentPage + 1) },
                        enabled = resumed && state.foreground && !working && state.currentPage < state.pageCount,
                        style = HaloActionStyle.SECONDARY,
                        modifier = Modifier.weight(1f),
                    ) { Text(stringResource(R.string.external_display_next)) }
                }
            }
            HaloActionButton(
                onClick = {
                    val token = state.activeToken ?: return@HaloActionButton
                    if (token != selectedToken || !resumed || !state.foreground) return@HaloActionButton
                    val revision = state.revision
                    perform { owner.clear(revision, token) }
                },
                enabled = selectedOutput && resumed && state.foreground && !working,
                style = HaloActionStyle.STOP,
                modifier = Modifier.fillMaxWidth(),
            ) { Text(stringResource(R.string.external_display_stop)) }
            if (working) Text(stringResource(R.string.external_display_working), style = MaterialTheme.typography.bodySmall)
            if (failed) {
                Text(stringResource(R.string.external_display_failed), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error)
            } else {
                (state.message ?: result.takeIf { resultVersion == state.revision })?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}
