package com.halo.app.ui.online

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.halo.app.R
import com.halo.app.online.*
import com.halo.app.ui.components.HaloTextField
import com.halo.app.ui.components.VoiceFieldKind
import java.math.BigDecimal

@Composable fun onlineProblemText(problem: OnlineProblem): String = stringResource(when (problem) {
    OnlineProblem.FEATURE_DISABLED -> R.string.mode47_feature_disabled
    OnlineProblem.CONSENT_REQUIRED -> R.string.online_consent_required
    OnlineProblem.CONSENT_EXPIRED -> R.string.online_consent_expired
    OnlineProblem.BUDGET_REACHED -> R.string.online_budget_reached
    OnlineProblem.INVALID_AMOUNT -> R.string.online_invalid_amount
    OnlineProblem.LEDGER_UNAVAILABLE -> R.string.online_ledger_unavailable
    OnlineProblem.LOCAL_ONLY -> R.string.online_local_only
    OnlineProblem.CHILD_RESTRICTED -> R.string.online_child_restricted
    OnlineProblem.KEY_REQUIRED -> R.string.online_key_required
    OnlineProblem.AUTHENTICATION -> R.string.online_authentication
    OnlineProblem.CREDIT_LIMIT -> R.string.online_credit_limit
    OnlineProblem.RATE_LIMIT -> R.string.online_rate_limit
    OnlineProblem.NETWORK -> R.string.online_network
    OnlineProblem.INVALID_RESPONSE -> R.string.online_invalid_response
    OnlineProblem.NO_SOURCES -> R.string.online_no_sources
    OnlineProblem.INCOMPLETE -> R.string.online_incomplete
    OnlineProblem.PRIVACY -> R.string.online_privacy
    OnlineProblem.UNAVAILABLE -> R.string.online_unavailable
})

/** Reusable explicit approval. Opening it never authorizes or starts an operation. */
@Composable fun OnlineSpendApprovalDialog(
    identity: String, provider: String, model: String, detail: String,
    initialReservation: String = "", pricingUrl: String? = null,
    onDismiss: () -> Unit, onApprove: (BigDecimal) -> Unit,
) {
    var amount by remember(identity) { mutableStateOf(initialReservation) }
    var acknowledged by remember(identity) { mutableStateOf(false) }
    var submitted by remember(identity) { mutableStateOf(false) }
    var costs by remember { mutableStateOf(false) }
    val value = OnlineOperationLedger.parseMoney(amount)?.takeIf { it > BigDecimal.ZERO }
    val uri = LocalUriHandler.current
    AlertDialog(onDismissRequest = onDismiss, title = { Text(stringResource(R.string.online_paid_title)) },
        text = { Column(Modifier.heightIn(max = 520.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("$provider / $model", style = MaterialTheme.typography.titleSmall)
            Text(detail)
            HaloTextField(amount, { amount = it.take(18) }, label = { Text(stringResource(R.string.online_amount)) },
                kind = VoiceFieldKind.NUMBER, singleLine = true, modifier = Modifier.fillMaxWidth())
            if (amount.isNotBlank() && value == null) Text(stringResource(R.string.online_invalid_amount), color = MaterialTheme.colorScheme.error)
            Row(Modifier.fillMaxWidth()) {
                Checkbox(acknowledged, { acknowledged = it })
                Text(stringResource(R.string.online_unknown_ack), Modifier.weight(1f).padding(top = 8.dp))
            }
            Text(stringResource(R.string.online_cancel_warning), style = MaterialTheme.typography.bodySmall)
            pricingUrl?.let { url -> TextButton(onClick = { runCatching { uri.openUri(url) } }) { Text(stringResource(R.string.online_provider_pricing)) } }
            TextButton(onClick = { costs = true }) { Text(stringResource(R.string.online_costs)) }
        } },
        confirmButton = { TextButton(onClick = {
            if (!submitted && acknowledged && value != null) { submitted = true; onApprove(value) }
        }, enabled = !submitted && acknowledged && value != null) { Text(stringResource(R.string.online_approve)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.online_cancel)) } })
    if (costs) OnlineCostDialog { costs = false }
}

fun imageProviderPricingUrl(provider: String): String? = when (provider) {
    "openrouter" -> "https://openrouter.ai/models?output_modalities=image"
    "openai" -> "https://openai.com/api/pricing/"
    "gemini" -> "https://ai.google.dev/gemini-api/docs/pricing"
    else -> null
}

@Composable internal fun rememberOnlineSearchService(): GroundedWebSearchService {
    val graph = com.halo.app.di.ManualDependencyGraph
    return remember(graph) { graph.groundedWebSearchService }
}
