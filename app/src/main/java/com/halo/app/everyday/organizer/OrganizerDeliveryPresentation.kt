package com.halo.app.everyday.organizer

import java.time.Instant

enum class OrganizerAlertViewState { LOADING, UNAVAILABLE, RINGING, SCHEDULED, FINISHED }
fun organizerAlertViewState(loaded: Boolean, loadFailed: Boolean, receipt: DeliveryReceipt?): OrganizerAlertViewState = when {
    !loaded -> OrganizerAlertViewState.LOADING
    loadFailed && receipt == null -> OrganizerAlertViewState.UNAVAILABLE
    receipt?.state in setOf(DeliveryState.FIRED, DeliveryState.DELIVERING) -> OrganizerAlertViewState.RINGING
    receipt?.state in setOf(DeliveryState.PENDING, DeliveryState.EXACT, DeliveryState.APPROXIMATE, DeliveryState.NOTIFICATIONS_BLOCKED) -> OrganizerAlertViewState.SCHEDULED
    else -> OrganizerAlertViewState.FINISHED
}

/** Exact occurrence identity selects its reviewed title/content; the series is never mutated for presentation. */
fun OrganizerRecord.forDelivery(key: String, exceptions: List<OccurrenceOverride>): OrganizerRecord {
    val original = key.removePrefix("$id@").toLongOrNull()?.let(Instant::ofEpochMilli) ?: return this
    val exception = exceptions.find { it.recordId == id && it.originalStart == original && it.disposition == OccurrenceDisposition.REPLACED }
        ?: return this
    return copy(title = exception.replacementTitle ?: title, details = exception.replacementDetails ?: details,
        location = exception.replacementLocation ?: location,
        sourceUrl = exception.sourceUrlOverride?.takeIf { it.isNotEmpty() } ?: sourceUrl.takeIf { exception.sourceUrlOverride == null },
        time = exception.replacementTime ?: time)
}
