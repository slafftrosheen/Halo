package com.halo.app.nativeactions

import android.app.AlarmManager
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import android.provider.Settings
import android.service.dreams.DreamService
import android.view.GestureDetector
import android.view.Gravity
import android.view.MotionEvent
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextClock
import android.widget.TextView
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.halo.app.HaloApplication
import com.halo.app.R
import com.halo.app.everyday.collectors.CollectorKind
import com.halo.app.everyday.collectors.CollectorPayload
import com.halo.app.everyday.organizer.OrganizerKind
import com.halo.app.everyday.presentation.readEverydaySnapshot
import com.halo.app.ui.mini.MiniHaloEntry
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale
import kotlinx.coroutines.*

private val bedsideInk = Color(0xFFCE9368)
private const val bedPrefs = "halo_bedside_v1"
private const val quietKey = "quiet"

private fun Context.bedsideCharging() = registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
    ?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0)?.let { it != 0 } == true

private suspend fun Context.bedsideTomorrow(): String {
    (applicationContext as HaloApplication).awaitOrganizerBootstrap()
    val snapshot = readEverydaySnapshot(this)
    val zone = snapshot.zone
    val date = snapshot.now.atZone(zone).toLocalDate().plusDays(1)
    val native = getSystemService(AlarmManager::class.java)?.nextAlarmClock?.triggerTime?.let(Instant::ofEpochMilli)
    val alarm = (snapshot.occurrences.asSequence().filter { it.record.kind == OrganizerKind.ALARM && !it.completed }
        .map { it.start } + listOfNotNull(native)).filter { it.atZone(zone).toLocalDate() == date }.minOrNull()
    val alarmLine = if (alarm == null) getString(R.string.halo_bedside_no_alarm) else getString(
        R.string.halo_bedside_alarm, DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)
            .withLocale(resources.configuration.locales[0]).withZone(zone).format(alarm))
    val row = snapshot.sources.firstOrNull { it.source.kind == CollectorKind.WEATHER && it.source.selected &&
        it.source.enabled && !it.source.muted && (!snapshot.kids || it.source.childApproved) }
    val cached = row?.snapshot?.takeIf { it.sourceVersion == row.source.version }
    val fact = (cached?.payload as? CollectorPayload.Weather)?.fact
    val weather = fact?.days?.firstOrNull { day ->
        day.date == snapshot.now.atZone(ZoneId.of(fact.place.zoneId)).toLocalDate().plusDays(1)
    }
    val rainLine = when {
        weather?.precipitationProbability == null -> getString(R.string.halo_bedside_no_weather)
        else -> {
            val chance = java.text.NumberFormat.getPercentInstance(resources.configuration.locales[0])
                .format(weather.precipitationProbability / 100.0)
            getString(if (cached?.stale(snapshot.now, true, snapshot.power) == true) R.string.halo_bedside_weather_saved
                else R.string.halo_bedside_weather, chance)
        }
    }
    return getString(R.string.halo_bedside_summary, alarmLine, rainLine)
}

@Composable
internal fun HaloBedsideScreen(onBack: () -> Unit, onWhisper: () -> Unit) {
    val context = LocalContext.current
    val prefs = remember(context) { context.getSharedPreferences(bedPrefs, Context.MODE_PRIVATE) }
    var quiet by remember { mutableStateOf(prefs.getBoolean(quietKey, false)) }
    var systemAccess by remember { mutableStateOf(false) }
    val whisper by rememberUpdatedState(onWhisper)
    val summary by produceState<String?>(null, context) {
        try { value = withContext(Dispatchers.IO) { context.bedsideTomorrow() } }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { value = context.getString(R.string.halo_bedside_unavailable) }
    }
    androidx.lifecycle.compose.LifecycleResumeEffect(context) {
        systemAccess = context.getSystemService(NotificationManager::class.java)?.isNotificationPolicyAccessGranted == true
        onPauseOrDispose { }
    }
    Column(Modifier.fillMaxSize().background(Color.Black).systemBarsPadding().padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack, modifier = Modifier.heightIn(min = 48.dp)) {
                Text(stringResource(R.string.halo_bedside_close), color = bedsideInk)
            }
            TextButton(onClick = {
                runCatching { context.startActivity(Intent(Settings.ACTION_DREAM_SETTINGS)) }
                    .recoverCatching { context.startActivity(Intent(Settings.ACTION_DISPLAY_SETTINGS)) }
            }, modifier = Modifier.heightIn(min = 48.dp)) {
                Text(stringResource(R.string.halo_bedside_settings), color = bedsideInk)
            }
        }
        Column(Modifier.weight(1f).fillMaxWidth().pointerInput(Unit) { detectTapGestures(onDoubleTap = { whisper() }) },
            verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
            AndroidView(factory = { bedsideClock(it) })
            Text(summary ?: stringResource(R.string.halo_bedside_loading), textAlign = TextAlign.Center,
                color = bedsideInk, style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(16.dp))
            TextButton(onClick = onWhisper, modifier = Modifier.heightIn(min = 48.dp)) {
                Text(stringResource(R.string.halo_bedside_whisper_action), color = bedsideInk)
            }
            Text(stringResource(R.string.halo_bedside_whisper), color = bedsideInk, style = MaterialTheme.typography.bodySmall)
        }
        if (Build.VERSION.SDK_INT >= 29) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.halo_bedside_quiet), color = bedsideInk, modifier = Modifier.weight(1f))
                Switch(quiet && systemAccess, onCheckedChange = { enabled ->
                    if (prefs.edit().putBoolean(quietKey, enabled).commit()) quiet = enabled
                }, enabled = systemAccess)
            }
            if (!systemAccess) TextButton(onClick = {
                runCatching { context.startActivity(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)) }
            }) { Text(stringResource(R.string.halo_bedside_quiet_access), color = bedsideInk) }
        }
    }
}

/** Android starts this screen saver only when the user chooses it. It never requests lock-screen or microphone access. */
class HaloBedsideDreamService : DreamService() {
    private var session: CoroutineScope? = null
    @Volatile private var quietSession: String? = null
    private lateinit var info: TextView

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        isFullscreen = true
        isInteractive = true
        isScreenBright = false
        val colors = resources.getColor(R.color.halo_bedside_ink, theme)
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundColor(android.graphics.Color.BLACK)
            setPadding(24, 24, 24, 24)
        }
        layout.addView(bedsideClock(this))
        info = TextView(this).apply {
            gravity = Gravity.CENTER
            setTextColor(colors)
            setText(R.string.halo_bedside_loading)
            textSize = 15f
        }
        layout.addView(info)
        val action = TextView(this).apply {
            gravity = Gravity.CENTER
            setTextColor(colors)
            setText(R.string.halo_bedside_whisper)
            minHeight = (48 * resources.displayMetrics.density).toInt()
            setOnClickListener { whisper() }
        }
        layout.addView(action)
        val close = android.widget.Button(this).apply {
            setText(R.string.halo_bedside_close)
            setOnClickListener { finish() }
        }
        layout.addView(close)
        val tap = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onDown(event: MotionEvent) = true
            override fun onDoubleTap(event: MotionEvent): Boolean { whisper(); return true }
        })
        layout.setOnTouchListener { _, event -> tap.onTouchEvent(event) }
        setContentView(layout, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
    }

    override fun onDreamingStarted() {
        super.onDreamingStarted()
        val hour = java.time.LocalTime.now().hour
        if (!bedsideCharging() || hour in 7 until 21) {
            finish()
            return
        }
        val active = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        session = active
        active.launch {
            while (true) {
                val data = try { withContext(Dispatchers.IO) { bedsideTomorrow() } }
                    catch (cancelled: CancellationException) { throw cancelled }
                    catch (_: Exception) { getString(R.string.halo_bedside_unavailable) }
                info.text = data
                delay(30 * 60_000L)
                val now = java.time.LocalTime.now().hour
                if (!bedsideCharging() || now in 7 until 21) { finish(); break }
            }
        }
        val prefs = getSharedPreferences(bedPrefs, Context.MODE_PRIVATE)
        if (prefs.getBoolean(quietKey, false) && Build.VERSION.SDK_INT >= 29 &&
            getSystemService(NotificationManager::class.java)?.isNotificationPolicyAccessGranted == true) {
            val id = java.util.UUID.randomUUID().toString()
            quietSession = id
                        active.launch(Dispatchers.IO) {
                runCatching { BedsideQuiet.start(this@HaloBedsideDreamService, id) { quietSession == id } }
            }
        }
    }

    override fun onDreamingStopped() {
        session?.cancel()
        session = null
        val key = quietSession
        quietSession = null
        if (key != null) runCatching { BedsideQuiet.scheduleRelease(this, key, 0L) }
        super.onDreamingStopped()
    }

    private fun whisper() {
        runCatching { startActivity(MiniHaloEntry.intent(this)) }
        finish()
    }
}

private fun bedsideClock(context: Context) = TextClock(context).apply {
    gravity = Gravity.CENTER
    format12Hour = "h:mm a"
    format24Hour = "HH:mm"
    typeface = context.resources.getFont(R.font.sora_variable)
    setTextColor(ContextCompat.getColor(context, R.color.halo_bedside_ink))
    textSize = 58f
    setSingleLine()
}
