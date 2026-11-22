package com.halo.app.ui.toolkit

import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.SecureFlagPolicy
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.halo.app.R
import com.halo.app.data.toolkit.HaloMapsStore
import com.halo.app.data.toolkit.HaloSavedPlace
import com.halo.app.di.ManualDependencyGraph as Graph
import com.halo.app.ui.screens.kids.rememberKidsGrownUpGate
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Family labels reference the saved places on this phone. They do not collect movement. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun HaloFamilySafePlaces() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val paused by Graph.haloPause.state.collectAsStateWithLifecycle()
    val kids by Graph.kidsPreferences.enabled.collectAsStateWithLifecycle()
    var access by remember { mutableStateOf<Long?>(null) }
    var unavailable by remember { mutableStateOf(false) }
    val unlock = rememberKidsGrownUpGate(onVerified = {
        access = runCatching { requireAdultToolkit(context) }.getOrNull()
        unavailable = access == null
    }, onUnavailable = { unavailable = true })
    val validAccess = access?.takeUnless { paused.paused || kids }
    DisposableEffect(lifecycle) {
        val revoke = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_STOP) access = null }
        lifecycle.addObserver(revoke)
        onDispose { lifecycle.removeObserver(revoke) }
    }
    LaunchedEffect(paused.paused, kids) { if (paused.paused || kids) access = null }
    OutlinedButton(unlock, enabled = validAccess == null && !paused.paused && !kids) {
        Text(stringResource(R.string.halo11_family_safe_places))
    }
    if (unavailable) Text(stringResource(R.string.halo11_family_safe_unlock))
    if (validAccess == null) return
    val preferences = remember(context) { context.getSharedPreferences("halo_family_safe_places_v11", Context.MODE_PRIVATE) }
    var selected by remember(preferences, validAccess) {
        mutableStateOf(preferences.getStringSet("savedPlaceIds", emptySet()).orEmpty().toSet())
    }
    var failed by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    val source = remember(validAccess) { HaloMapsStore.observePlaces().catch { error ->
        if (error is CancellationException) throw error
        failed = true
        emit(emptyList<HaloSavedPlace>())
    } }
    val places by source.collectAsStateWithLifecycle(emptyList())
    var pending by remember { mutableStateOf<Pair<String, Boolean>?>(null) }
    ModalBottomSheet(onDismissRequest = { access = null }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        properties = ModalBottomSheetProperties(securePolicy = SecureFlagPolicy.SecureOn)) {
        Column(Modifier.fillMaxWidth().heightIn(max = 500.dp).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(stringResource(R.string.halo11_family_safe_places), style = MaterialTheme.typography.titleLarge)
            Text(stringResource(R.string.halo11_family_safe_privacy))
            if (places.isEmpty()) Text(stringResource(R.string.halo11_family_safe_empty))
            for (place in places.take(100)) {
                val checked = place.id in selected
                ListItem(headlineContent = { Text(place.title) }, trailingContent = {
                    Checkbox(checked, onCheckedChange = { pending = place.id to it },
                        enabled = !saving && (checked || places.count { it.id in selected } < 50))
                })
            }
            if (failed) Text(stringResource(R.string.halo11_family_safe_failed), color = MaterialTheme.colorScheme.error)
            TextButton({ access = null }) { Text(stringResource(android.R.string.ok)) }
        }
    }
    pending?.let { (id, add) ->
        val candidate = places.find { it.id == id }
        if (candidate != null) AlertDialog(onDismissRequest = { pending = null },
            properties = DialogProperties(securePolicy = SecureFlagPolicy.SecureOn),
            title = { Text(stringResource(if (add) R.string.halo11_family_safe_add else R.string.halo11_family_safe_remove, candidate.title)) },
            confirmButton = { TextButton({
                pending = null
                val prior = selected
                saving = true
                scope.launch {
                    try {
                        selected = withContext(Dispatchers.IO) {
                            val current = HaloMapsStore.currentPlaces()
                            check(candidate in current && access == validAccess)
                            requireAdultToolkit(context, validAccess)
                            val retained = prior.intersect(current.mapTo(hashSetOf()) { it.id })
                            val next = if (add) retained + id else retained - id
                            check(next.size <= 50 && preferences.edit().putStringSet("savedPlaceIds", next).commit())
                            next
                        }
                        failed = false
                    } catch (cancelled: CancellationException) { throw cancelled }
                    catch (_: Exception) { failed = true }
                    finally { saving = false }
                }
            }) { Text(stringResource(android.R.string.ok)) } },
            dismissButton = { TextButton({ pending = null }) { Text(stringResource(android.R.string.cancel)) } })
    }
}
