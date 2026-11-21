package com.halo.app.ui.screens.settings

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.halo.app.di.ManualDependencyGraph as Graph
import com.halo.app.language.uiText
import kotlinx.coroutines.*

@Composable internal fun ChatGptAccountSettings() {
    val owner = remember { Graph.chatGptLogin }
    val account by owner.account.state.collectAsStateWithLifecycle()
    val operation by owner.state.collectAsStateWithLifecycle()
    val mode by Graph.privacyModes.mode.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var failure by remember { mutableStateOf(false) }
    var disconnectRevision by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(owner) {
        try { withContext(Dispatchers.IO) { owner.account.load() } }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { failure = true }
    }
    val providerId = com.halo.providers.openai.ChatGptSubscriptionProvider.ID
    val canFetch = account.connected && account.subscriptionAccess && !operation.busy &&
        mode == com.halo.app.ai.PrivacyMode.CLOUD
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(androidx.compose.ui.res.stringResource(com.halo.app.R.string.z6_account_title), style = MaterialTheme.typography.titleLarge)
        Text(uiText("Eligible subscription models follow your ChatGPT account limits."))
        com.halo.app.ui.components.CloudModeResolution()
        val status = when {
            !account.connected -> androidx.compose.ui.res.stringResource(com.halo.app.R.string.z6_account_none)
            account.displayName.isNullOrBlank() -> uiText("ChatGPT account connected")
            else -> androidx.compose.ui.res.stringResource(com.halo.app.R.string.z6_account_connected, account.displayName.orEmpty())
        }
        Surface(shape = MaterialTheme.shapes.large,
            color = if (account.connected && !account.subscriptionAccess) MaterialTheme.colorScheme.tertiaryContainer
                else MaterialTheme.colorScheme.surfaceContainerHigh) {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(status, style = MaterialTheme.typography.titleMedium)
                if (account.connected && account.subscriptionAccess)
                    Text(androidx.compose.ui.res.stringResource(com.halo.app.R.string.z6_account_access_ok))
                if (account.connected && !account.subscriptionAccess)
                    Text(androidx.compose.ui.res.stringResource(com.halo.app.R.string.z6_account_no_access))
            }
        }
        if (operation.busy) {
            com.halo.app.ui.zone6.Zone6DelayedLoading(true)
            TextButton(onClick = owner::cancel, modifier = Modifier.heightIn(min = 48.dp)) { Text(uiText("Cancel")) }
        } else {
            if (!account.subscriptionAccess) Button(enabled = mode == com.halo.app.ai.PrivacyMode.CLOUD, onClick = {
                failure = runCatching { owner.start() }.isFailure
            }, modifier = Modifier.heightIn(min = 48.dp)) {
                Text(uiText(if (account.connected) "Sign in again" else "Sign in with ChatGPT"))
            }
            if (account.connected) {
                Button(enabled = canFetch, onClick = { Graph.providerControlRepository.refreshModels(providerId) },
                    modifier = Modifier.heightIn(min = 48.dp)) {
                    Text(androidx.compose.ui.res.stringResource(com.halo.app.R.string.z6_account_fetch_models))
                }
                TextButton({ disconnectRevision = account.revision }, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text(uiText("Disconnect ChatGPT…"), color = MaterialTheme.colorScheme.error)
                }
            }
        }
        operation.message?.let { Text(uiText(it)) }
        if (failure) Text(uiText("Account settings changed or are unavailable. Try again."), color = MaterialTheme.colorScheme.error)
    }
    disconnectRevision?.let { revision ->
        AlertDialog(onDismissRequest = { disconnectRevision = null },
            title = { Text(uiText("Disconnect ChatGPT?")) },
            text = { Text(uiText("This removes the account connection from halo.")) },
            confirmButton = {
                TextButton(onClick = {
                    disconnectRevision = null
                    scope.launch {
                        try { withContext(Dispatchers.IO) { owner.disconnect(revision) }; failure = false }
                        catch (cancelled: CancellationException) { throw cancelled }
                        catch (_: Exception) { failure = true }
                    }
                }, enabled = account.revision == revision) {
                    Text(uiText("Disconnect"), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton({ disconnectRevision = null }) { Text(uiText("Cancel")) } },
        )
    }
}
