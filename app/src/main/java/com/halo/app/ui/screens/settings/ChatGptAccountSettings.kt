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
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(uiText("Sign in with ChatGPT to use eligible subscription models. OpenAI controls your account limits. This connection does not use API credits."))
        com.halo.app.ui.components.CloudModeResolution()
        Text(uiText(if (account.connected) "ChatGPT account connected" else "No ChatGPT account connected"))
        if (account.connected && !account.subscriptionAccess) Text(uiText("Signed in, but ChatGPT did not grant subscription access to this account."))
        if (operation.busy) {
            LinearProgressIndicator(Modifier.fillMaxWidth())
            TextButton(onClick = owner::cancel) { Text(uiText("Cancel")) }
        } else {
            Button(enabled = mode == com.halo.app.ai.PrivacyMode.CLOUD, onClick = {
                failure = runCatching { owner.start() }.isFailure
            }) { Text(uiText("Sign in with ChatGPT")) }
            if (account.connected) TextButton(onClick = {
                val version = account.revision
                scope.launch {
                    try { withContext(Dispatchers.IO) { owner.disconnect(version) }; failure = false }
                    catch (cancelled: CancellationException) { throw cancelled }
                    catch (_: Exception) { failure = true }
                }
            }) { Text(uiText("Disconnect ChatGPT")) }
        }
        operation.message?.let { Text(uiText(it)) }
        if (failure) Text(uiText("Account settings changed or are unavailable. Try again."), color = MaterialTheme.colorScheme.error)
    }
}
