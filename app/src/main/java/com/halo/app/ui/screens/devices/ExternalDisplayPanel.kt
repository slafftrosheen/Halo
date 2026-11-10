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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
import com.halo.app.R
import com.halo.app.ui.components.HaloActionButton
import com.halo.app.ui.components.HaloActionStyle
import com.halo.app.ui.design.HaloSpacing
import com.halo.host.android.display.AndroidExternalDisplayPresenter
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/** A selected Android presentation display is an output route, not verified XREAL identity. */
@Composable
internal fun ExternalDisplayPanel(owner: AndroidExternalDisplayPresenter) {
    val state by owner.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var selectedToken by remember { mutableStateOf<String?>(null) }
    var working by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<String?>(null) }
    var resultVersion by remember { mutableStateOf<Long?>(null) }
    var failed by remember { mutableStateOf(false) }
    val testCard = stringResource(R.string.external_display_test_content)
    val availableSelection = state.endpoints.any { it.token == selectedToken }

    LaunchedEffect(state.endpoints) {
        // A replacement display must be selected again, even if Android reused its numeric id.
        if (!availableSelection) selectedToken = null
        result = null
        failed = false
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
                                    enabled = state.foreground && !working, role = Role.RadioButton,
                                    onClick = { selectedToken = endpoint.token; result = null; failed = false })
                                .padding(vertical = HaloSpacing.sm),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(HaloSpacing.md),
                        ) {
                            RadioButton(selected = selectedToken == endpoint.token, onClick = null,
                                enabled = state.foreground && !working)
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
            HaloActionButton(
                onClick = {
                    val token = selectedToken ?: return@HaloActionButton
                    working = true
                    failed = false
                    scope.launch {
                        try {
                            val receipt = owner.show(token, testCard)
                            result = receipt.message
                            resultVersion = receipt.sourceVersion
                        }
                        catch (cancelled: CancellationException) { throw cancelled }
                        catch (_: Exception) { failed = true; result = null }
                        finally { working = false }
                    }
                },
                enabled = state.foreground && availableSelection && !working,
                modifier = Modifier.fillMaxWidth(),
            ) { Text(stringResource(R.string.external_display_show_test)) }
            HaloActionButton(
                onClick = {
                    working = true
                    failed = false
                    scope.launch {
                        try {
                            val receipt = owner.clear()
                            result = receipt.message
                            resultVersion = receipt.sourceVersion
                        }
                        catch (cancelled: CancellationException) { throw cancelled }
                        catch (_: Exception) { failed = true; result = null }
                        finally { working = false }
                    }
                },
                enabled = state.activeToken != null && !working,
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
