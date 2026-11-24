package com.halo.app.ui.everyday

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.halo.app.everyday.organizer.OrganizerRecord
import com.halo.app.language.uiText
import java.security.MessageDigest
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

/** One reviewed event occurrence and an independently scheduled leave reminder. */
data class PlannerLeaveByReview(
    val eventId: String,
    val eventVersion: Long,
    val originalStart: Instant,
    val start: Instant,
    val title: String,
    val location: String,
    val savedMinutes: Int?,
    val existing: OrganizerRecord? = null,
) {
    internal val reminderId: String get() {
        val key = "$eventId\u0000$originalStart"
        val digest = MessageDigest.getInstance("SHA-256").digest(key.toByteArray(Charsets.UTF_8))
        return "leave-by:" + digest.joinToString("") { "%02x".format(it) }
    }

    internal fun leaveAt(minutes: Int): Instant {
        require(minutes in 0..10_080)
        return start.minusSeconds(minutes * 60L)
    }
}

@Composable
internal fun PlannerLeaveBySheet(review: PlannerLeaveByReview, now: Instant, zone: ZoneId, locale: Locale,
    busy: Boolean, error: String?, onDismiss: () -> Unit, onKeep: (Int) -> Unit, onOpenExisting: (OrganizerRecord) -> Unit) {
    var minutes by rememberSaveable(review.eventId, review.eventVersion, review.originalStart.toString()) {
        mutableStateOf(review.savedMinutes?.toString().orEmpty())
    }
    val entered = minutes.toIntOrNull()?.takeIf { it in 0..10_080 }
    val leaveAt = entered?.let(review::leaveAt)
    val inFuture = leaveAt != null && leaveAt > now
    val timestamp = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM).withLocale(locale).withZone(zone)
    val existing = review.existing
    EverydaySheet(onDismiss) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(uiText("Leave-by time"), style = MaterialTheme.typography.headlineSmall)
            com.halo.app.ui.theme.v13.Halo13Animation(com.halo.app.ui.theme.v13.Halo13Motion.LEAVE_BY,
                Modifier.fillMaxWidth().height(68.dp))
            Text(review.title, style = MaterialTheme.typography.titleMedium)
            Text(uiText("Event starts %s", timestamp.format(review.start)), style = MaterialTheme.typography.bodyMedium)
            if (review.location.isNotBlank()) Text(review.location, style = MaterialTheme.typography.bodyMedium)
            if (existing != null) {
                Text(uiText(if (existing.deletedAt == null) "This event already has a leave reminder."
                    else "The leave reminder for this event is in Trash."))
                if (existing.deletedAt == null) existing.time?.let { time ->
                    Text(uiText("Saved reminder %s", timestamp.format(time.instant(zone))), style = MaterialTheme.typography.bodyMedium)
                }
                Button({ onOpenExisting(existing) }, Modifier.fillMaxWidth().heightIn(min = 52.dp), enabled = !busy) {
                    Text(uiText(if (existing.deletedAt == null) "Open reminder" else "Open Trash"))
                }
            } else {
                OutlinedTextField(minutes, { minutes = it.filter { digit -> digit in '0'..'9' }.take(5) },
                    enabled = !busy, label = { Text(uiText("Travel minutes")) },
                    supportingText = {
                        Text(uiText(if (minutes.isNotBlank() && entered == null) "Enter 0 to 10080 minutes."
                            else "Enter the travel time you want to allow."))
                    }, isError = minutes.isNotBlank() && entered == null, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth())
                if (leaveAt != null) {
                    Text(uiText("Leave by %s", timestamp.format(leaveAt)), style = MaterialTheme.typography.titleLarge)
                    Text(uiText("Save a reminder for this time on this phone."), style = MaterialTheme.typography.bodyMedium)
                    if (!inFuture) Text(uiText("That leave-by time has passed. Enter fewer travel minutes."), style = MaterialTheme.typography.bodyMedium)
                }
                error?.let { EverydayMessage(it, error = true) }
                Button({ entered?.let(onKeep) }, Modifier.fillMaxWidth().heightIn(min = 52.dp),
                    enabled = !busy && inFuture) { Text(uiText("Save reminder")) }
            }
            TextButton(onDismiss, enabled = !busy, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text(uiText("Close")) }
        }
    }
}
