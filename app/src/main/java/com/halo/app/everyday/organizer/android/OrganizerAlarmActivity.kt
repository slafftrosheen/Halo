package com.halo.app.everyday.organizer.android

import android.app.KeyguardManager
import android.content.Intent
import android.os.Bundle
import android.os.UserManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import com.halo.app.HaloApplication
import com.halo.app.MainActivity
import com.halo.app.R
import com.halo.app.di.ManualDependencyGraph
import com.halo.app.everyday.organizer.*
import com.halo.app.ui.presence.HaloSignalFace
import com.halo.app.ui.presence.HaloSignalPresentation
import com.halo.app.ui.presence.HaloSignalState
import com.halo.app.ui.theme.HaloTheme
import java.time.Duration
import kotlinx.coroutines.*

/** Dedicated alarm surface; only timing and generic text are available before first unlock. */
@OptIn(ExperimentalLayoutApi::class)
class OrganizerAlarmActivity : ComponentActivity() {
    private var record by mutableStateOf<OrganizerRecord?>(null)
    private var receipt by mutableStateOf<DeliveryReceipt?>(null)
    private var loaded by mutableStateOf(false)
    private var busy by mutableStateOf(false)
    private var error by mutableStateOf<String?>(null)
    private var loadJob: Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (android.os.Build.VERSION.SDK_INT >= 27) { setShowWhenLocked(true); setTurnScreenOn(true) }
        else @Suppress("DEPRECATION") window.addFlags(android.view.WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
            android.view.WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON)
        setContent {
            HaloTheme {
                Surface(Modifier.fillMaxSize()) {
                    BoxWithConstraints(Modifier.fillMaxSize().safeDrawingPadding()) {
                        val zoneMinimum = (maxHeight * .25f).coerceAtMost(220.dp)
                        val viewState = organizerAlertViewState(loaded, error != null, receipt)
                        val active = viewState == OrganizerAlertViewState.RINGING
                        Column(Modifier.fillMaxSize()) {
                            Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(24.dp),
                                verticalArrangement = Arrangement.spacedBy(16.dp)) {
                                Text(stringResource(R.string.organizer_alarm_private_title), style = MaterialTheme.typography.labelLarge)
                                if (!loaded) CircularProgressIndicator()
                                else {
                                    Text(if (getSystemService(KeyguardManager::class.java).isDeviceLocked || viewState == OrganizerAlertViewState.UNAVAILABLE)
                                        stringResource(R.string.organizer_alarm_private_title) else record?.title ?: stringResource(R.string.organizer_delivery_finished), style = MaterialTheme.typography.headlineLarge)
                                    Text(stringResource(when (viewState) {
                                        OrganizerAlertViewState.RINGING -> R.string.organizer_alarm_active
                                        OrganizerAlertViewState.SCHEDULED -> R.string.organizer_delivery_scheduled
                                        OrganizerAlertViewState.UNAVAILABLE -> R.string.organizer_delivery_unknown
                                        else -> R.string.organizer_delivery_finished
                                    }),
                                        style = MaterialTheme.typography.bodyLarge)
                                    error?.let {
                                        Text(it, color = MaterialTheme.colorScheme.error)
                                        TextButton(onClick = { error = null; reload() }, enabled = !busy) { Text(stringResource(R.string.organizer_retry)) }
                                    }
                                    TextButton(onClick = ::openHalo, enabled = !busy) { Text(stringResource(R.string.organizer_open_halo)) }
                                }
                            }
                            Column(Modifier.fillMaxWidth().heightIn(min = zoneMinimum).padding(16.dp),
                                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                HaloSignalFace(HaloSignalPresentation(state = when (viewState) {
                                    OrganizerAlertViewState.RINGING, OrganizerAlertViewState.UNAVAILABLE -> HaloSignalState.WARNING
                                    OrganizerAlertViewState.LOADING, OrganizerAlertViewState.SCHEDULED -> HaloSignalState.WAITING
                                    OrganizerAlertViewState.FINISHED -> HaloSignalState.STOPPED },
                                    label = when (viewState) { OrganizerAlertViewState.RINGING -> "Alarm"; OrganizerAlertViewState.UNAVAILABLE -> "Needs attention"
                                        OrganizerAlertViewState.LOADING, OrganizerAlertViewState.SCHEDULED -> "Waiting"; else -> "Stopped" }, reducedMotion = true), size = 72.dp, zone5 = true,
                                    contentDescription = stringResource(R.string.organizer_alarm_private_title))
                                // A wrapping action row keeps both labeled targets reachable at large text sizes.
                                FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    OutlinedButton(onClick = { act(snooze = true) }, enabled = active && !busy) { Text(stringResource(R.string.organizer_delivery_snooze)) }
                                    Button(onClick = { act(snooze = false) }, enabled = active && !busy) { Text(stringResource(R.string.organizer_delivery_stop)) }
                                }
                            }
                        }
                    }
                }
            }
        }
        reload()
    }
    override fun onNewIntent(intent: Intent) { super.onNewIntent(intent); setIntent(intent); reload() }
    override fun onResume() { super.onResume(); reload() }
    private fun reload() {
        loaded = false
        loadJob?.cancel()
        loadJob = lifecycleScope.launch {
          try {
            val key = intent.getStringExtra(OrganizerIntents.KEY)
            val version = intent.getLongExtra(OrganizerIntents.VERSION, -1)
            val pair = withContext(Dispatchers.IO) {
                if (key == null || version <= 0) null else if (!getSystemService(UserManager::class.java).isUserUnlocked) {
                    OrganizerDirectBootStore(this@OrganizerAlarmActivity).get(key.substringBefore('@'))?.let { value ->
                        value.record.copy(title = getString(R.string.organizer_alarm_private_title)) to value.receipts.find {
                            it.occurrenceKey == key && it.recordVersion == version }
                    }
                } else {
                    (application as HaloApplication).awaitOrganizerBootstrap()
                    val owner = ManualDependencyGraph.organizerRepository
                    owner.get(key.substringBefore('@'))?.takeIf { it.version == version && it.deletedAt == null }?.let {
                        it.forDelivery(key, owner.overrides(it.id)) to owner.receipts(it.id).find { receipt -> receipt.occurrenceKey == key && receipt.recordVersion == version }
                    }
                }
            }
            record = pair?.first; receipt = pair?.second; loaded = true
          } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { record = null; receipt = null; loaded = true; error = getString(R.string.organizer_delivery_retry) }
        }
    }
    private fun act(snooze: Boolean) {
        val current = receipt ?: return
        if (busy) return
        busy = true
        lifecycleScope.launch {
            try {
                val accepted = withContext(Dispatchers.IO) {
                    if (!getSystemService(UserManager::class.java).isUserUnlocked)
                        OrganizerLockedDelivery(this@OrganizerAlarmActivity).action(current.occurrenceKey, current.recordVersion, current.triggerAt, snooze)
                    else {
                        (application as HaloApplication).awaitOrganizerBootstrap()
                        if (snooze) { ManualDependencyGraph.organizerDelivery.snooze(current.occurrenceKey, current.recordVersion, Duration.ofMinutes(5), current.triggerAt); true }
                        else ManualDependencyGraph.organizerDelivery.dismiss(current.occurrenceKey, current.recordVersion, current.triggerAt)
                    }
                }
                if (accepted) finish() else { error = getString(R.string.organizer_delivery_finished); reload() }
            } catch (cancelled: CancellationException) { throw cancelled }
              catch (_: Exception) { error = getString(R.string.organizer_delivery_retry); reload() }
              finally { busy = false }
        }
    }
    private fun openHalo() {
        val keyguard = getSystemService(KeyguardManager::class.java)
        fun launch() { startActivity(Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)); finish() }
        if (!keyguard.isKeyguardLocked) launch()
        else keyguard.requestDismissKeyguard(this, object : KeyguardManager.KeyguardDismissCallback() {
            override fun onDismissSucceeded() = launch()
        })
    }
}
