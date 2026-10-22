package com.halo.app.ui.shell

import android.app.Activity
import android.app.role.RoleManager
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.halo.app.R
import com.halo.app.di.AndroidSkillPermissionChecker
import com.halo.app.ui.onboarding.SetupProgressStore
import com.halo.app.ui.workspace.HaloWorkspaceButton
import com.halo.app.ui.workspace.HaloWorkspaceCard
import com.halo.sdk.runtime.skills.HostPermission

internal class AndroidCapabilityGuide(private val context: Context) {
    private val checker = AndroidSkillPermissionChecker(context)
    val inventory = guidedCapabilityInventory(Build.VERSION.SDK_INT)
    private fun hostPermission(id: String): HostPermission? = when (id) {
        "notification_access" -> HostPermission.NOTIFICATION_ACCESS
        "accessibility" -> HostPermission.ACCESSIBILITY
        "write_settings" -> HostPermission.WRITE_SETTINGS
        "notification_policy" -> HostPermission.NOTIFICATION_POLICY
        else -> null
    }

    fun settingsIntent(id: String): Intent = when (id) {
        "exact_alarm" -> com.halo.host.android.reminder.ExactAlarmAccessController(context).settingsIntent()
            ?: Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))
        "overlay" -> Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${context.packageName}"))
        "assistant" -> Intent(Settings.ACTION_VOICE_INPUT_SETTINGS)
        else -> hostPermission(id)?.let(checker::specialAccessIntent)
            ?: Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))
    }

    fun granted(request: GuidedCapabilityRequest): Boolean = when (request.id) {
        "exact_alarm" -> com.halo.host.android.reminder.ExactAlarmAccessController(context).status() !=
            com.halo.host.android.reminder.ExactAlarmAccess.NeedsUserGrant
        "location" -> request.permissions.any { context.checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED }
        "overlay" -> Settings.canDrawOverlays(context)
        "assistant" -> Build.VERSION.SDK_INT >= 29 &&
            context.getSystemService(RoleManager::class.java)?.isRoleHeld(RoleManager.ROLE_ASSISTANT) == true
        else -> hostPermission(request.id)?.let(checker::granted)
            ?: request.permissions.all { context.checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED }
    }

    fun available(request: GuidedCapabilityRequest): Boolean {
        val feature = when (request.id) {
            "microphone" -> PackageManager.FEATURE_MICROPHONE
            "camera" -> PackageManager.FEATURE_CAMERA_ANY
            "bluetooth" -> PackageManager.FEATURE_BLUETOOTH
            "nearby_wifi" -> PackageManager.FEATURE_WIFI
            else -> null
        }
        if (feature != null && !context.packageManager.hasSystemFeature(feature)) return false
        if (request.id == "assistant" && (Build.VERSION.SDK_INT < 29 || context.getSystemService(RoleManager::class.java)
                ?.isRoleAvailable(RoleManager.ROLE_ASSISTANT) != true)) return false
        return !request.special || settingsIntent(request.id).resolveActivity(context.packageManager) != null
    }

    fun status(request: GuidedCapabilityRequest, attempted: Boolean): CapabilityAccessState {
        if (request.id == "location" && granted(request) &&
            context.checkSelfPermission(android.Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED)
            return CapabilityAccessState.LIMITED
        val activity = context.activity()
        return capabilityAccessState(available(request), granted(request), request.special, attempted,
            request.permissions.any { activity != null && ActivityCompat.shouldShowRequestPermissionRationale(activity, it) })
    }
}

private tailrec fun Context.activity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.activity()
    else -> null
}

/** Shared Settings guide. No launch effect survives navigation or process recreation. */
@Composable
internal fun CapabilitySetupGuide(store: SetupProgressStore, onAccessChanged: () -> Unit = {}) {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    val platform = remember(context) { AndroidCapabilityGuide(context) }
    val progress by store.capabilityGuideState.collectAsStateWithLifecycle()
    var revision by remember { mutableIntStateOf(0) }
    var running by remember { mutableStateOf(false) }
    var unavailableId by remember { mutableStateOf<String?>(null) }
    val inventory = platform.inventory

    fun next(after: String?): String? = nextGuidedCapability(inventory, after,
        inventory.filter(platform::granted).map { it.id }.toSet(), store.capabilityGuide.attemptedIds +
            inventory.filter { platform.status(it, false) == CapabilityAccessState.DECLINED }.map { it.id },
        store.capabilityGuide.skippedIds, inventory.filterNot(platform::available).map { it.id }.toSet())
    fun returned() {
        running = false
        revision++
        val pending = store.capabilityGuide.pendingId
        if (pending != null) store.advanceCapabilityGuide(pending, next(pending), attempted = true)
        onAccessChanged()
    }
    val runtimeLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { returned() }
    val settingsLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        running = false
        returned()
    }
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) { revision++; onAccessChanged() }
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }

    fun launch(request: GuidedCapabilityRequest) {
        store.markCapabilityRequested(request.id)
        try {
            if (request.special) settingsLauncher.launch(platform.settingsIntent(request.id))
            else runtimeLauncher.launch(request.permissions.toTypedArray())
        } catch (_: android.content.ActivityNotFoundException) {
            unavailableId = request.id; running = false; returned()
        } catch (_: SecurityException) {
            unavailableId = request.id; running = false; returned()
        }
    }
    LaunchedEffect(progress.currentId, running) {
        if (!running || !progress.active || progress.pendingId != null) return@LaunchedEffect
        val current = inventory.firstOrNull { it.id == progress.currentId } ?: return@LaunchedEffect
        // Special access always needs its own visible, explained Settings entry.
        if (current.special) { running = false; return@LaunchedEffect }
        if (platform.granted(current) || current.id in progress.attemptedIds || !platform.available(current)) {
            store.advanceCapabilityGuide(current.id, next(current.id), attempted = current.id in progress.attemptedIds)
        } else launch(current)
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(stringResource(R.string.capability_guide_title), style = MaterialTheme.typography.titleLarge)
        Text(stringResource(R.string.capability_guide_explanation))
        Text(stringResource(R.string.capability_guide_privacy), style = MaterialTheme.typography.bodySmall)
        val current = inventory.firstOrNull { progress.active && it.id == progress.currentId }
        if (current == null) {
            val firstRemaining = next(null)
            HaloWorkspaceButton(stringResource(R.string.capability_guide_start), onClick = {
                store.startCapabilityGuide(firstRemaining); running = false
            }, enabled = firstRemaining != null, modifier = Modifier.fillMaxWidth())
            if (firstRemaining == null || progress.attemptedIds.isNotEmpty() || progress.skippedIds.isNotEmpty())
                Text(stringResource(R.string.capability_guide_finished))
        } else {
            HaloWorkspaceCard {
                Text(stringResource(capabilityCopy(current.id).first), style = MaterialTheme.typography.titleMedium)
                Text(stringResource(capabilityCopy(current.id).second))
                if (!running) HaloWorkspaceButton(stringResource(if (current.special)
                    R.string.capability_guide_open_settings else R.string.capability_guide_continue), onClick = {
                    // A persisted pending request is a receipt, never an instruction to replay it.
                    if (progress.pendingId != null || current.id in progress.attemptedIds || platform.granted(current)) {
                        store.advanceCapabilityGuide(current.id, next(current.id), attempted = current.id in progress.attemptedIds)
                        running = false
                    } else if (current.special) launch(current)
                    else running = true
                }, modifier = Modifier.fillMaxWidth())
                TextButton(onClick = {
                    running = false
                    store.advanceCapabilityGuide(current.id, next(current.id), attempted = current.id in progress.attemptedIds)
                }, enabled = progress.pendingId == null, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text(stringResource(R.string.capability_guide_skip))
                }
                if (running) TextButton(onClick = { running = false }) { Text(stringResource(R.string.capability_guide_pause)) }
            }
        }
        // Reads Android again on every lifecycle return, including revocations made outside Halo.
        key(revision) {
            inventory.forEach { request ->
                val status = if (unavailableId == request.id) CapabilityAccessState.UNAVAILABLE
                    else platform.status(request, request.id in progress.attemptedIds)
                HaloWorkspaceCard {
                    Text(stringResource(capabilityCopy(request.id).first), style = MaterialTheme.typography.titleSmall)
                    Text(stringResource(capabilityCopy(request.id).second), style = MaterialTheme.typography.bodySmall)
                    Text(stringResource(when (status) {
                        CapabilityAccessState.ALLOWED -> R.string.capability_state_allowed
                        CapabilityAccessState.LIMITED -> R.string.capability_state_approximate
                        CapabilityAccessState.NOT_ASKED -> R.string.capability_state_not_asked
                        CapabilityAccessState.DECLINED -> R.string.capability_state_declined
                        CapabilityAccessState.SETTINGS_REQUIRED -> R.string.capability_state_settings
                        CapabilityAccessState.UNAVAILABLE -> R.string.capability_state_unavailable
                    }))
                    if (status == CapabilityAccessState.NOT_ASKED && request.id in progress.skippedIds)
                        TextButton(onClick = {
                            running = false
                            store.startCapabilityGuide(request.id)
                            launch(request)
                        }, enabled = progress.pendingId == null, modifier = Modifier.heightIn(min = 48.dp)) {
                            Text(stringResource(R.string.capability_guide_continue))
                        }
                    if (status in setOf(CapabilityAccessState.DECLINED, CapabilityAccessState.SETTINGS_REQUIRED, CapabilityAccessState.LIMITED))
                        TextButton(onClick = {
                            // Repair is separate from setup sequencing. Opening Settings is never a grant.
                            running = false
                            try { settingsLauncher.launch(platform.settingsIntent(request.id)) }
                            catch (_: android.content.ActivityNotFoundException) { unavailableId = request.id }
                            catch (_: SecurityException) { unavailableId = request.id }
                        }, enabled = progress.pendingId == null, modifier = Modifier.heightIn(min = 48.dp)) {
                            Text(stringResource(R.string.capability_guide_open_settings))
                        }
                }
            }
        }
    }
}

private fun capabilityCopy(id: String): Pair<Int, Int> = when (id) {
    "microphone" -> R.string.capability_mic to R.string.capability_mic_why
    "notifications" -> R.string.capability_notifications to R.string.capability_notifications_why
    "contacts" -> R.string.capability_contacts to R.string.capability_contacts_why
    "calendar_read" -> R.string.capability_calendar_read to R.string.capability_calendar_read_why
    "calendar_write" -> R.string.capability_calendar_write to R.string.capability_calendar_write_why
    "camera" -> R.string.capability_camera to R.string.capability_camera_why
    "location" -> R.string.capability_location to R.string.capability_location_why
    "bluetooth" -> R.string.capability_bluetooth to R.string.capability_bluetooth_why
    "nearby_wifi" -> R.string.capability_wifi to R.string.capability_wifi_why
    "local_network" -> R.string.capability_network to R.string.capability_network_why
    "notification_access" -> R.string.capability_listener to R.string.capability_listener_why
    "accessibility" -> R.string.capability_accessibility to R.string.capability_accessibility_why
    "write_settings" -> R.string.capability_brightness to R.string.capability_brightness_why
    "notification_policy" -> R.string.capability_dnd to R.string.capability_dnd_why
    "overlay" -> R.string.capability_overlay to R.string.capability_overlay_why
    "exact_alarm" -> R.string.capability_exact_alarm to R.string.capability_exact_alarm_why
    else -> R.string.capability_assistant to R.string.capability_assistant_why
}
