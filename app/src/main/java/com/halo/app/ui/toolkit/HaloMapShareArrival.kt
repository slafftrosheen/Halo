package com.halo.app.ui.toolkit

import android.app.DatePickerDialog
import android.app.Dialog
import android.app.KeyguardManager
import android.app.TimePickerDialog
import android.content.Intent
import android.text.format.DateFormat
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.halo.app.R
import com.halo.app.di.ManualDependencyGraph as Graph
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/** Copies a single reviewed time into the Android chooser. */
@Composable
internal fun HaloMapShareArrival(enabled: Boolean, reviewRequest: Int = 0, estimate: (() -> ZonedDateTime?)? = null) {
    val context = LocalContext.current
    val locale = LocalConfiguration.current.locales[0]
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var reviewing by remember { mutableStateOf(false) }
    var day by remember { mutableStateOf(LocalDate.now()) }
    var hour by remember { mutableStateOf<LocalTime?>(null) }
    var error by remember { mutableIntStateOf(0) }
    var handledRequest by remember { mutableIntStateOf(0) }
    var picker by remember { mutableStateOf<Dialog?>(null) }
    val zone = ZoneId.systemDefault()
    val current = hour?.let { chosen ->
        val local = day.atTime(chosen)
        zone.rules.getValidOffsets(local).firstOrNull()?.let { ZonedDateTime.ofStrict(local, it, zone) }
    }
    val preview = current?.let {
        val formatted = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM).withLocale(locale).format(it)
        stringResource(R.string.halo11_tool_map_share_body, formatted, it.offset.id.replace("Z", "+00:00"))
    }
    val shareTitle = stringResource(R.string.halo11_tool_map_share)
    DisposableEffect(Unit) { onDispose { picker?.dismiss() } }
    LaunchedEffect(enabled) { if (!enabled) { picker?.dismiss(); reviewing = false; hour = null; error = 0 } }
    fun openReview() {
        val currentEstimate = estimate?.invoke()?.withZoneSameInstant(zone)?.plusSeconds(59)
        day = currentEstimate?.toLocalDate() ?: LocalDate.now(zone)
        hour = currentEstimate?.toLocalTime()?.withSecond(0)?.withNano(0)
        error = 0
        reviewing = true
    }
    LaunchedEffect(reviewRequest) {
        if (enabled && reviewRequest > 0 && handledRequest != reviewRequest) {
            handledRequest = reviewRequest
            openReview()
        }
    }
    fun chooseTime() {
        val initial = hour ?: LocalTime.now().plusMinutes(30)
        picker = TimePickerDialog(context, { _, hours, minutes -> hour = LocalTime.of(hours, minutes); error = 0 },
            initial.hour, initial.minute, DateFormat.is24HourFormat(context)).also(Dialog::show)
    }
    fun chooseDate() {
        picker = DatePickerDialog(context, { _, year, month, days -> day = LocalDate.of(year, month + 1, days); error = 0 },
            day.year, day.monthValue - 1, day.dayOfMonth).also(Dialog::show)
    }
    TextButton(::openReview, enabled = enabled) { Text(shareTitle) }
    if (error != 0 && !reviewing) Text(stringResource(error), color = MaterialTheme.colorScheme.error)
    if (!reviewing || !enabled) return
    AlertDialog(
        onDismissRequest = { reviewing = false },
        title = { Text(shareTitle) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.halo11_tool_map_share_help))
                TextButton(::chooseDate) {
                    val formatted = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale).format(day)
                    Text(stringResource(R.string.halo11_tool_map_share_day, formatted))
                }
                TextButton(::chooseTime) {
                    val selected = hour
                    val formatted = selected?.let { DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(locale).format(it) }
                    Text(if (formatted == null) stringResource(R.string.halo11_tool_map_share_select_time)
                        else stringResource(R.string.halo11_tool_map_share_time, formatted))
                }
                if (preview != null) Text(preview, style = MaterialTheme.typography.titleMedium)
                if (error != 0) Text(stringResource(error), color = MaterialTheme.colorScheme.error)
            }
        },
        confirmButton = {
            TextButton({
                val chosen = current
                if (chosen == null || !chosen.isAfter(ZonedDateTime.now(zone))) {
                    error = R.string.halo11_tool_map_share_future
                } else if (preview != null) {
                    try {
                        check(enabled && lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED) &&
                            context.getSystemService(KeyguardManager::class.java)?.isDeviceLocked == false)
                        Graph.kidsPreferences.adultActionRevision()
                        check(!Graph.haloPause.isPaused())
                        val intent = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, preview)
                        context.startActivity(Intent.createChooser(intent, shareTitle))
                        reviewing = false
                    } catch (_: Exception) { error = R.string.halo11_tool_map_share_error }
                }
            }, enabled = hour != null) { Text(stringResource(R.string.halo11_tool_map_share_review)) }
        },
        dismissButton = { TextButton({ reviewing = false }) { Text(stringResource(R.string.halo11_tool_close)) } },
    )
}
