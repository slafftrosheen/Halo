package com.halo.app.ui.toolkit

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.core.app.NotificationManagerCompat
import com.halo.app.R
import com.halo.app.ui.icons.HaloGlyph
import com.halo.app.ui.presence.BindShellPageActions
import com.halo.app.ui.presence.LocalShellHaloOrb
import com.halo.app.ui.presence.ShellPageAction
import com.halo.app.ui.presence.ShellPageOwner
import com.halo.app.ui.zone6.haloEditablePageTitle
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@Composable
internal fun HaloMorningCheckIn(onBack: () -> Unit) {
    val context = LocalContext.current
    val owner = remember(context) { HaloLifeOwner.get(context) }
    val saved by owner.checkIn.collectAsStateWithLifecycle()
    val defaultMessage = stringResource(R.string.halo_checkin_default_message)
    var phone by remember(saved?.id) { mutableStateOf(saved?.recipient.orEmpty()) }
    var message by remember(saved?.id, defaultMessage) { mutableStateOf(saved?.message ?: defaultMessage) }
    var hour by remember(saved?.id) { mutableStateOf("8") }
    var review by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf(false) }
    var notificationAllowed by remember { mutableStateOf(NotificationManagerCompat.from(context).areNotificationsEnabled()) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        notificationAllowed = NotificationManagerCompat.from(context).areNotificationsEnabled()
    }
    val scope = rememberCoroutineScope()
    fun run(operation: suspend () -> Unit) {
        if (busy) return
        busy = true
        error = false
        scope.launch {
            try { operation() } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { error = true }
            finally { busy = false }
        }
    }
    val title = stringResource(R.string.halo_checkin_title)
    val help = stringResource(R.string.halo_checkin_explain)
    val plan = saved
    val canSave = phone.count(Char::isDigit) >= 5 && message.isNotBlank() && (hour.toIntOrNull() ?: -1) in 4..11
    val action = if (plan == null) stringResource(R.string.halo_checkin_schedule) else stringResource(R.string.halo_checkin_review_message)
    val onPrimary = {
        if (plan != null) review = true
        else run { owner.scheduleCheckIn(null, phone.trim(), hour.toInt(), message.trim()) }
    }
    LifePage("checkin", title, help, action, !busy && (plan != null || canSave && notificationAllowed), onPrimary, onBack,
        guidanceBlocked = review || busy) {
        Text(help)
        if (plan == null) {
            OutlinedTextField(phone, { phone = it.take(32) }, Modifier.fillMaxWidth(), singleLine = true,
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Phone),
                label = { Text(stringResource(R.string.halo_checkin_recipient)) })
            OutlinedTextField(hour, { hour = it.filter(Char::isDigit).take(2) }, Modifier.fillMaxWidth(), singleLine = true,
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Number),
                label = { Text(stringResource(R.string.halo_checkin_hour)) })
            OutlinedTextField(message, { message = it.take(500) }, Modifier.fillMaxWidth(), minLines = 2,
                label = { Text(stringResource(R.string.halo_checkin_message)) })
            if (!notificationAllowed) TextButton({
                if (Build.VERSION.SDK_INT >= 33) permission.launch(Manifest.permission.POST_NOTIFICATIONS)
                else runCatching { context.startActivity(Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                    .putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, context.packageName)) }
            }) { Text(stringResource(R.string.halo_checkin_allow_notification)) }
        } else {
            val time = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM).withZone(ZoneId.systemDefault())
                .format(Instant.ofEpochMilli(plan.at))
            Text(stringResource(R.string.halo_checkin_planned, time), style = MaterialTheme.typography.titleLarge)
            Text(plan.recipient)
            TextButton({ run { owner.clearCheckIn(plan.id) } }, enabled = !busy) {
                Text(stringResource(R.string.halo_checkin_turn_off))
            }
        }
        if (error) Text(stringResource(R.string.page_layout_error), color = MaterialTheme.colorScheme.error)
    }
    if (review && plan != null) AlertDialog(onDismissRequest = { review = false },
        title = { Text(stringResource(R.string.halo_checkin_review_message)) },
        text = { Text(plan.recipient + "\n\n" + plan.message) },
        confirmButton = { Button({
            review = false
            // Explicit handoff only. The chosen messaging app owns the final Send button.
            runCatching {
                requireAdultToolkit(context)
                check(owner.checkIn.value == plan)
                context.startActivity(Intent(Intent.ACTION_SENDTO, Uri.fromParts("smsto", plan.recipient, null))
                    .putExtra("sms_body", plan.message))
            }.onFailure { error = true }
        }) { Text(stringResource(R.string.halo_checkin_open_messages)) } },
        dismissButton = { TextButton({ review = false }) { Text(stringResource(android.R.string.cancel)) } })
}

@Composable
internal fun HaloEmergencyEditor(onBack: () -> Unit) {
    val context = LocalContext.current
    val owner = remember(context) { HaloLifeOwner.get(context) }
    val saved by owner.emergency.collectAsStateWithLifecycle()
    var draft by remember(saved) { mutableStateOf(saved) }
    var review by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    val title = stringResource(R.string.halo_emergency_title)
    val help = stringResource(R.string.halo_emergency_scope)
    fun save() {
        busy = true
        scope.launch {
            try { owner.saveEmergency(saved, draft); error = false }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { error = true }
            finally { busy = false }
        }
    }
    LifePage("emergency", title, help, stringResource(R.string.halo_life_save), !busy && (!draft.enabled || draft.hasContent),
        { if (draft.enabled && !saved.enabled) review = true else save() }, onBack, guidanceBlocked = review || busy) {
        Text(help)
        EmergencyEditField(R.string.halo_emergency_allergies, draft.allergies) { draft = draft.copy(allergies = it) }
        EmergencyEditField(R.string.halo_emergency_medicine, draft.medicine) { draft = draft.copy(medicine = it) }
        EmergencyEditField(R.string.halo_emergency_contact, draft.contact) { draft = draft.copy(contact = it) }
        EmergencyEditField(R.string.halo_emergency_language, draft.language) { draft = draft.copy(language = it) }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.halo_emergency_publish), Modifier.weight(1f))
            Switch(draft.enabled, { draft = draft.copy(enabled = it) })
        }
        if (Build.VERSION.SDK_INT >= 33 && !NotificationManagerCompat.from(context).areNotificationsEnabled())
            TextButton({ permission.launch(Manifest.permission.POST_NOTIFICATIONS) }) {
                Text(stringResource(R.string.halo_checkin_allow_notification))
            }
        if (saved.enabled && !NotificationManagerCompat.from(context).areNotificationsEnabled()) {
            Text(stringResource(R.string.halo_notice_off))
        }
        if (saved.enabled) TextButton({ context.startActivity(Intent(context, HaloEmergencyActivity::class.java)) }) {
            Text(stringResource(R.string.halo_emergency_open))
        }
        if (error) Text(stringResource(R.string.page_layout_error), color = MaterialTheme.colorScheme.error)
    }
    if (review) AlertDialog(onDismissRequest = { review = false }, title = { Text(title) },
        text = { Text(stringResource(R.string.halo_emergency_consent)) },
        confirmButton = { Button({ review = false; save() }) { Text(stringResource(R.string.halo_emergency_publish)) } },
        dismissButton = { TextButton({ review = false }) { Text(stringResource(android.R.string.cancel)) } })
}

@Composable
private fun EmergencyEditField(label: Int, value: String, onChange: (String) -> Unit) {
    OutlinedTextField(value, { onChange(it.take(240)) }, Modifier.fillMaxWidth(),
        minLines = 1, maxLines = 3, label = { Text(stringResource(label)) })
}

class HaloEmergencyActivity : androidx.activity.ComponentActivity() {
    override fun onCreate(savedInstanceState: android.os.Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= 27) setShowWhenLocked(true)
        setContent {
            val owner = remember { HaloLifeOwner.get(applicationContext) }
            val card by owner.emergency.collectAsStateWithLifecycle()
            LaunchedEffect(card.enabled) { if (!card.enabled) finish() }
            MaterialTheme {
                Surface(Modifier.fillMaxSize()) {
                    Column(Modifier.fillMaxSize().padding(24.dp).verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        Text(stringResource(R.string.halo_emergency_title), style = MaterialTheme.typography.headlineMedium,
                            color = if (androidx.compose.foundation.isSystemInDarkTheme())
                                androidx.compose.ui.graphics.Color(0xFFFFBF92) else androidx.compose.ui.graphics.Color(0xFF794322))
                        if (card.enabled) listOf(
                            R.string.halo_emergency_allergies to card.allergies,
                            R.string.halo_emergency_medicine to card.medicine,
                            R.string.halo_emergency_contact to card.contact,
                            R.string.halo_emergency_language to card.language,
                        ).filter { it.second.isNotBlank() }.forEach { (label, value) ->
                            Text(stringResource(label), style = MaterialTheme.typography.titleMedium)
                            Text(value, style = MaterialTheme.typography.bodyLarge)
                        }
                        TextButton({ finish() }) { Text(stringResource(android.R.string.ok)) }
                    }
                }
            }
        }
    }
}

@Composable
internal fun LifePage(
    route: String, title: String, help: String, action: String, enabled: Boolean,
    onPrimary: () -> Unit, onBack: () -> Unit, guidanceBlocked: Boolean = false,
    extra: List<ShellPageAction> = emptyList(), content: @Composable ColumnScope.() -> Unit,
) {
    val back = stringResource(R.string.sheets_back)
    BindShellPageActions(ShellPageOwner(route, helpDescription = help, guidanceBlocked = guidanceBlocked),
        listOf(ShellPageAction("page.$route.start", action, HaloGlyph.ACTION, enabled, onPrimary),
            ShellPageAction("page.$route.back", back, HaloGlyph.PREVIOUS, true, onBack)) + extra)
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(title, Modifier.haloEditablePageTitle(), style = MaterialTheme.typography.headlineMedium)
        content()
        if (!LocalShellHaloOrb.current) Button(onPrimary, enabled = enabled, modifier = Modifier.heightIn(min = 52.dp)) { Text(action) }
    }
}
