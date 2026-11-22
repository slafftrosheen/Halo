package com.halo.app.nativeactions

import android.os.SystemClock
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.SecureFlagPolicy
import com.halo.app.R
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/** A transient in-app request. It carries no device approval and is never constructed from a link. */
internal object PrivateLibraryHandoff {
    data class Request(val target: PrivateLibraryTarget, val expiresAt: Long)
    private var request: Request? = null

    @androidx.annotation.MainThread fun offer(target: PrivateLibraryTarget) {
        request = Request(target, SystemClock.elapsedRealtime() + 3 * 60_000L)
    }
    @androidx.annotation.MainThread fun pending() = request?.takeIf { it.expiresAt > SystemClock.elapsedRealtime() }
    @androidx.annotation.MainThread fun claim(expected: Request?): PrivateLibraryTarget? {
        if (expected == null || pending() !== expected) return null
        request = null
        return expected.target
    }
    @androidx.annotation.MainThread fun discard(expected: Request?) { if (request === expected) request = null }
}

internal class PrivateLibraryVaultState {
    var listing by mutableStateOf(PrivateLibraryListing(emptyList(), 0))
    var review by mutableStateOf<PrivateLibraryPlan?>(null)
    var opened by mutableStateOf<Pair<PrivateLibrarySummary, PrivateLibraryView>?>(null)
    var confirmRestore by mutableStateOf<PrivateLibrarySummary?>(null)
    var busy by mutableStateOf(false)
    var error by mutableStateOf<Int?>(null)
}

@Composable
internal fun rememberPrivateLibraryVaultState(store: PrivateLibraryStore, access: VaultSession?,
    request: PrivateLibraryHandoff.Request?): PrivateLibraryVaultState {
    val state = remember(access) { PrivateLibraryVaultState() }
    LaunchedEffect(access) {
        val session = access ?: return@LaunchedEffect
        state.busy = true
        state.error = null
        try {
            state.listing = store.list(session)
            PrivateLibraryHandoff.claim(request)?.let { state.review = store.prepare(session, it) }
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: com.halo.app.data.ConversationPrivateBusy) { state.error = R.string.halo11_private_chat_finishing }
        catch (_: Exception) { state.error = R.string.halo11_private_cannot_lock }
        finally { state.busy = false }
    }
    return state
}

internal fun LazyListScope.privateLibraryVaultItems(store: PrivateLibraryStore, access: VaultSession, state: PrivateLibraryVaultState) {
    if (state.listing.entries.isNotEmpty() || state.listing.unavailable > 0 || state.error != null) item("private-library-header") {
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (state.listing.entries.isNotEmpty() || state.listing.unavailable > 0) {
                Text(stringResource(R.string.halo11_private_library_items), style = MaterialTheme.typography.titleMedium)
            }
            if (state.listing.unavailable > 0) Text(stringResource(R.string.halo11_private_unavailable),
                style = MaterialTheme.typography.bodySmall)
            state.error?.let { Text(stringResource(it), color = MaterialTheme.colorScheme.error) }
        }
    }
    items(state.listing.entries, key = { "private-library:${it.id}" }) { entry -> PrivateLibraryVaultRow(store, access, state, entry) }
}

@Composable
private fun PrivateLibraryVaultRow(store: PrivateLibraryStore, access: VaultSession, state: PrivateLibraryVaultState,
    entry: PrivateLibrarySummary) {
    val scope = rememberCoroutineScope()
    Surface(onClick = {
        scope.launch {
            state.busy = true
            state.error = null
            try { state.opened = entry to store.view(access, entry.id) }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { state.error = R.string.halo11_private_unavailable }
            finally { state.busy = false }
        }
    }, enabled = !state.busy, shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(entry.title, style = MaterialTheme.typography.titleSmall)
            if (entry.collection) Text(stringResource(R.string.halo11_private_collection_count, entry.itemCount),
                style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
internal fun PrivateLibraryVaultDialogs(store: PrivateLibraryStore, access: VaultSession, state: PrivateLibraryVaultState) {
    val scope = rememberCoroutineScope()
    fun work(errorText: Int, action: suspend () -> Unit) {
        if (state.busy) return
        state.busy = true
        state.error = null
        scope.launch {
            try { action(); state.listing = store.list(access) }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: com.halo.app.data.ConversationPrivateBusy) { state.error = R.string.halo11_private_chat_finishing }
            catch (_: Exception) { state.error = errorText }
            finally { state.busy = false }
        }
    }
    val secure = DialogProperties(securePolicy = SecureFlagPolicy.SecureOn)
    state.review?.let { plan ->
        AlertDialog(onDismissRequest = { if (!state.busy) state.review = null }, properties = secure,
            title = { Text(stringResource(R.string.halo11_private_lock)) },
            text = {
                Column(Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(plan.title, style = MaterialTheme.typography.titleMedium)
                    Text(stringResource(R.string.halo11_private_lock_review))
                    val linked = if (plan.target is PrivateLibraryTarget.Collection) plan.titles else plan.titles.drop(1)
                    if (linked.isNotEmpty()) {
                        Text(stringResource(R.string.halo11_private_linked_items), style = MaterialTheme.typography.titleSmall)
                        linked.forEach { Text(it) }
                    }
                    if (plan.conversationIds.isNotEmpty()) Text(stringResource(R.string.halo11_private_chat_review))
                    if (plan.automationItems.isNotEmpty()) Text(stringResource(R.string.halo11_private_automation_review))
                    if (plan.includesToolResults) Text(stringResource(R.string.halo11_private_tool_results_review))
                    if (state.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                    state.error?.let { Text(stringResource(it), color = MaterialTheme.colorScheme.error) }
                }
            }, confirmButton = { Button(onClick = {
                work(R.string.halo11_private_cannot_lock) {
                    try {
                        store.lock(access, plan)
                        state.review = null
                    } catch (_: PrivateLibraryReviewChanged) {
                        state.review = store.prepare(access, plan.target)
                        state.error = R.string.halo11_private_review_changed
                    }
                }
            }, enabled = !state.busy) { Text(stringResource(R.string.halo11_private_lock)) } },
            dismissButton = { TextButton(onClick = { state.review = null }, enabled = !state.busy) {
                Text(stringResource(android.R.string.cancel))
            } })
    }
    state.opened?.takeIf { state.confirmRestore == null }?.let { (entry, view) ->
        AlertDialog(onDismissRequest = { state.opened = null }, properties = secure,
            title = { Text(view.title) }, text = {
                Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    view.items.forEach { item ->
                        if (view.items.size > 1 || entry.collection) Text(item.title, style = MaterialTheme.typography.titleSmall)
                        item.canvasNodeCount?.let { count -> Text(stringResource(R.string.halo11_private_collection_count, count),
                            style = MaterialTheme.typography.bodySmall) }
                        if (!item.note.isNullOrBlank()) Text(item.note)
                        for (media in item.media) key(item.itemId, media.id) {
                            if (media.title.isNotBlank()) Text(media.title, style = MaterialTheme.typography.bodySmall)
                            ProtectedLibraryFilePreview(store, access, entry.id, item.itemId, media)
                        }
                    }
                }
            }, confirmButton = {
                TextButton(onClick = { state.confirmRestore = entry }) { Text(stringResource(R.string.halo11_private_restore)) }
            }, dismissButton = { TextButton(onClick = { state.opened = null }) { Text(stringResource(android.R.string.cancel)) } })
    }
    state.confirmRestore?.let { entry ->
        AlertDialog(onDismissRequest = { if (!state.busy) state.confirmRestore = null }, properties = secure,
            title = { Text(stringResource(R.string.halo11_private_restore)) },
            text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.halo11_private_restore_review))
                if (state.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                state.error?.let { Text(stringResource(it), color = MaterialTheme.colorScheme.error) }
            } }, confirmButton = { Button(onClick = {
                work(R.string.halo11_private_cannot_restore) {
                    store.restore(access, entry.id)
                    state.confirmRestore = null
                    state.opened = null
                }
            }, enabled = !state.busy) { Text(stringResource(R.string.halo11_private_restore)) } },
            dismissButton = { TextButton(onClick = { state.confirmRestore = null }, enabled = !state.busy) {
                Text(stringResource(android.R.string.cancel))
            } })
    }
}
