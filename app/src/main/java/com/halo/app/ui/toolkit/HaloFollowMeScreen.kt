package com.halo.app.ui.toolkit

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.halo.app.R
import com.halo.app.continuity.FollowMeOptions
import com.halo.app.di.ManualDependencyGraph as Graph
import com.halo.app.ui.icons.HaloGlyph
import com.halo.app.ui.icons.HaloIcon
import com.halo.app.ui.workspace.HaloWorkspaceToggleRow
import com.halo.runtime.cognition.continuity.InteractionEndpoint
import com.halo.runtime.cognition.continuity.InteractionEndpointState
import com.halo.runtime.cognition.continuity.sameEndpoint
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable
internal fun HaloFollowMeScreen(onBack: () -> Unit, onDevices: () -> Unit) {
    val options by Graph.followMePreferences.state.collectAsStateWithLifecycle()
    val endpoints by Graph.continuityEndpoints.endpoints.collectAsStateWithLifecycle()
    val usage by Graph.interactionContinuity.state.collectAsStateWithLifecycle()
    val nearbySettings by Graph.voiceExperienceSettings.state.collectAsStateWithLifecycle()
    val watchForeground by Graph.wearEndpointBridge.foreground.collectAsStateWithLifecycle()
    val kids by Graph.kidsPreferences.enabled.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf(false) }

    fun save(next: FollowMeOptions) {
        if (saving || kids) return
        saving = true
        error = false
        val expected = options
        scope.launch {
            try {
                val revision = Graph.kidsPreferences.adultActionRevision()
                Graph.followMePreferences.update(expected, next) { Graph.kidsPreferences.requireAdultAction(revision) }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { error = true }
            finally { saving = false }
        }
    }

    val title = stringResource(R.string.follow_me_title)
    val help = stringResource(R.string.follow_me_help)
    LifePage("follow_me", title, help, stringResource(R.string.follow_me_devices), true, onDevices, onBack) {
        Text(help, style = MaterialTheme.typography.bodyLarge)
        HaloWorkspaceToggleRow(title, options.enabled, { save(options.copy(enabled = it)) },
            subtitle = stringResource(R.string.follow_me_enabled_detail), enabled = !kids && !saving)
        HaloWorkspaceToggleRow(stringResource(R.string.follow_me_watch_approvals), options.watchApprovals,
            { save(options.copy(watchApprovals = it)) }, subtitle = stringResource(R.string.follow_me_watch_approvals_detail),
            enabled = !kids && !saving)
        if (!nearbySettings.wearNearbyInteraction) Text(stringResource(R.string.follow_me_watch_settings),
            style = MaterialTheme.typography.bodyMedium)
        if (error) Text(stringResource(R.string.page_layout_error), color = MaterialTheme.colorScheme.error)

        Text(stringResource(R.string.follow_me_devices), style = MaterialTheme.typography.titleLarge)
        val phone = endpoints.firstOrNull { it.endpoint == InteractionEndpoint.Phone }
        val managed = endpoints.filter { it.endpoint is InteractionEndpoint.ManagedDevice }
        val wear = endpoints.filter { it.endpoint is InteractionEndpoint.WearOs }
        phone?.let { row ->
            FollowMeEndpoint(stringResource(R.string.follow_me_phone), HaloGlyph.PHONE, row,
                usage.executing && usage.inputEndpoint == InteractionEndpoint.Phone, stringResource(R.string.follow_me_phone_detail))
        }
        wear.forEach { row ->
            val node = row.endpoint as InteractionEndpoint.WearOs
            FollowMeEndpoint(row.label, HaloGlyph.WATCH, row, node.nodeId in watchForeground,
                stringResource(R.string.follow_me_watch_detail))
        }
        managed.forEach { row ->
            FollowMeEndpoint(row.label, HaloGlyph.GLASSES, row,
                usage.executing && (sameEndpoint(usage.inputEndpoint, row.endpoint) || sameEndpoint(usage.audioOutputEndpoint, row.endpoint)),
                stringResource(R.string.follow_me_glasses_detail))
        }
        if (wear.isEmpty() && managed.isEmpty()) Text(stringResource(R.string.follow_me_empty), style = MaterialTheme.typography.bodyMedium)
        TextButton(onDevices, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.follow_me_open_devices)) }
    }
}

@Composable
private fun FollowMeEndpoint(label: String, glyph: HaloGlyph, device: InteractionEndpointState, active: Boolean, help: String) {
    val status = when {
        !device.ready -> R.string.follow_me_needs_a_look
        active -> R.string.follow_me_in_use
        else -> R.string.follow_me_ready
    }
    ListItem(leadingContent = { HaloIcon(glyph, null) }, headlineContent = { Text(label) },
        supportingContent = { Text(help) }, trailingContent = { Text(stringResource(status)) },
        modifier = Modifier.fillMaxWidth())
}
