package com.halo.app.ui.screens.settings

import com.halo.app.ui.components.HaloTextField
import com.halo.app.ui.components.VoiceFieldKind
import com.halo.app.language.uiText

import androidx.lifecycle.compose.collectAsStateWithLifecycle

import android.app.NotificationManager
import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.halo.app.data.notification.NotificationAccessPolicy
import com.halo.app.di.ManualDependencyGraph
import com.halo.app.notification.NotificationListenerState
import com.halo.host.android.reminder.ExactAlarmAccessController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun NotificationSettingsScreen(viewModel: RoutinesViewModel, onBack: () -> Unit, onOpenInbox: () -> Unit = {}) {
    SettingsChildZone("Notifications", onBack)
    val context = LocalContext.current


    val graph = ManualDependencyGraph
    val bridge = graph.notificationBridge
    val state by bridge.state.collectAsStateWithLifecycle()
    val digest by viewModel.result.collectAsStateWithLifecycle()
    val routines by viewModel.uiState.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var revision by remember { mutableIntStateOf(0) }
    var posting by remember { mutableStateOf(false) }
    var exact by remember { mutableStateOf("Checking") }
    var reminders by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var apps by remember { mutableStateOf<List<Pair<String, String>>>(emptyList()) }
    val deliverySettings by graph.digestDeliverySettings.state.collectAsStateWithLifecycle()
    val deliveryChannel = deliverySettings.channel
    val wearableId = deliverySettings.wearableId
    var wearableDraft by rememberSaveable(wearableId) { mutableStateOf(wearableId) }
    val recent by graph.notificationRepository.observeRecent().collectAsStateWithLifecycle(initialValue = emptyList())
    var policyError by remember { mutableStateOf<String?>(null) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    fun refresh() {
        scope.launch(Dispatchers.IO) {
            bridge.refresh()
            val delivery = context.getSystemService(NotificationManager::class.java).areNotificationsEnabled()
            val access = ExactAlarmAccessController(context).status().javaClass.simpleName
            val receipts = com.halo.host.android.reminder.ReminderRecordStore(context).statuses()
            withContext(Dispatchers.Main) { posting = delivery; exact = access; reminders = receipts; revision++ }
        }
    }
    val postPermission = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.RequestPermission()) { refresh() }
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) refresh() }
        lifecycle.addObserver(observer)
        refresh()
        onDispose { lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(Unit) {
        apps = withContext(Dispatchers.IO) {
            context.packageManager.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0)
                .map { it.activityInfo.packageName to it.loadLabel(context.packageManager).toString() }
                .filter { it.first != context.packageName }.distinctBy { it.first }.sortedBy { it.second.lowercase() }
        }
    }
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = com.halo.app.ui.design.HaloSpacing.screen), contentPadding = PaddingValues(bottom = 100.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            if (!com.halo.app.ui.presence.LocalShellHaloOrb.current) com.halo.app.ui.design.HaloScreenHeader(uiText("Notifications & Digests"), navigation = onBack)
            Text(uiText("Reading notifications and showing Halo notifications are separate permissions. You can decline either."))
            AttentionSettingsSection()
            Text(uiText("Notification access: ") + when (state) {
                NotificationListenerState.PermissionMissing -> "Not granted"
                NotificationListenerState.Connecting -> "Connecting"
                NotificationListenerState.Connected -> "Connected"
                NotificationListenerState.Disconnected -> "Disconnected"
            })
            TextButton(onClick = onOpenInbox) { Text(uiText("Open notification inbox")) }
            TextButton(onClick = bridge::openNotificationAccessSettings) { Text(uiText("Open Android notification access")) }
            if(android.os.Build.VERSION.SDK_INT >= 33) TextButton(onClick = { postPermission.launch(android.Manifest.permission.POST_NOTIFICATIONS) }) { Text(uiText("Allow Halo notifications")) }
            Text("Halo notification delivery permission: ${if (posting) "Allowed" else "Not granted / disabled"}")
            TextButton(onClick = { context.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)) }) { Text(uiText("Manage Halo notification delivery")) }
            Text("Exact reminder access: $exact")
            TextButton(onClick = { ExactAlarmAccessController(context).openSettings() }) { Text(uiText("Manage exact alarm access")) }
            reminders.forEach { (id, status) -> Text("Reminder ${id.substringBefore('|')}: $status") }
            Text(uiText("Background digest delivery"), style = MaterialTheme.typography.titleMedium)
            listOf("activity" to "Android intent", "android" to "Android notification").forEach { (key, title) ->
                TextButton(onClick = {
                    scope.launch { graph.digestDeliverySettings.setChannel(key) }
                }) { Text(if (deliveryChannel == key) "Selected: $title" else title) }
            }
            HaloTextField(
                value = wearableDraft,
                onValueChange = { wearableDraft = it },
                label = { Text(uiText("Wearable device ID for explicit speech")) }, kind = VoiceFieldKind.SHORT)
            TextButton(enabled = wearableDraft.isNotBlank(), onClick = {
                scope.launch {
                    val normalized = wearableDraft.trim()
                    graph.digestDeliverySettings.setWearableId(normalized)
                    graph.digestDeliverySettings.setChannel("wearable")
                }
            }) { Text(if (deliveryChannel == "wearable") "Wearable speech enabled" else "Enable wearable speech for digests") }
            Text(uiText("Wearable speech never falls back to the phone. Quiet mode and Do Not Disturb use Activity."))
            TextButton(onClick = viewModel::summarizeNow) { Text(uiText("Summarize now")) }
            digest?.let { Text(it) }
            (policyError ?: routines.error)?.let { Text(uiText("Could not update notification settings. Try again."), color = MaterialTheme.colorScheme.error) }
            Text(uiText("Apps"), style = MaterialTheme.typography.titleLarge)
            Text(uiText("Apps start blocked. Private and secret content is redacted. Retention: 24 hours. Summaries stay offline."))
        }
        if (recent.isNotEmpty()) item {
            Text(uiText("Allowed notification history"), style = MaterialTheme.typography.titleMedium)
            recent.take(20).forEach { n ->
                Text("${n.appLabel ?: n.packageName}: ${n.content.title.orEmpty()}\n${n.content.body.orEmpty()}", Modifier.padding(vertical = 4.dp))
            }
        }
        items(apps, key = { it.first }) { (pkg, label) ->
            var expanded by remember { mutableStateOf(false) }
            val policy = remember(pkg, revision) { graph.notificationPolicies.policyForApp(pkg) }
            Column {
                Text(label, style = MaterialTheme.typography.titleMedium)
                Text(pkg, style = MaterialTheme.typography.bodySmall)
                Box {
                    TextButton(onClick = { expanded = true }) { Text(uiText(notificationPolicyLabel(policy))) }
                    DropdownMenu(expanded, { expanded = false }) {
                        NotificationAccessPolicy.entries.forEach { option -> DropdownMenuItem(text = { Text(uiText(notificationPolicyLabel(option))) }, onClick = {
                            expanded = false
                            scope.launch {
                                try {
                                    withContext(Dispatchers.IO) { graph.notificationRepository.setPolicy(pkg, option) }
                                    revision++
                                } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
                                catch (_: Exception) { policyError = "Could not save notification access. Try again." }
                            }
                        }) }
                    }
                }
            }
        }
    }
}

internal fun notificationPolicyLabel(policy: NotificationAccessPolicy): String = when(policy) {
    NotificationAccessPolicy.ALLOW -> "Read and use notifications"
    NotificationAccessPolicy.SUMMARIZE_ONLY -> "Summaries only"
    NotificationAccessPolicy.HIDE_CONTENT -> "Hide notification content"
    NotificationAccessPolicy.BLOCK -> "Blocked"
}
