package com.halo.app.ui.sources.v13

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.halo.app.R
import com.halo.app.everyday.collectors.CollectorState
import com.halo.app.ui.theme.v13.Halo13Animation
import com.halo.app.ui.theme.v13.Halo13Motion

internal val SourceGroup.label: Int get() = when (this) {
    SourceGroup.YOUR_OWN -> R.string.sp13_own_sources
    SourceGroup.NEWS_AND_READING -> R.string.sp13_news_sources
    SourceGroup.WEATHER_AND_ALERTS -> R.string.sp13_weather_sources
    SourceGroup.CALENDARS -> R.string.sp13_calendar_sources
    SourceGroup.STUDY -> R.string.sp13_study_sources
    SourceGroup.KIDS_SAFE -> R.string.sp13_kids_sources
}

private val PlainState.label: Int get() = when (this) {
    PlainState.UP_TO_DATE -> R.string.sp13_current
    PlainState.UPDATING -> R.string.sp13_updating
    PlainState.ON_THIS_PHONE -> R.string.sp13_on_phone
    PlainState.WAITING_FOR_INTERNET -> R.string.sp13_waiting
    PlainState.TAKING_A_BREAK -> R.string.sp13_backoff
    PlainState.NEEDS_YOU -> R.string.sp13_needs_you
    PlainState.CANT_REACH -> R.string.sp13_unreachable
    PlainState.PAUSED -> R.string.sp13_paused
    PlainState.NEW -> R.string.sp13_new
}

@Composable
internal fun SourceStatus(row: CollectorState, stale: Boolean) {
    val status = SourceManager.state(row.source, row.attempt.state, row.attempt.consecutiveFailures)
    val tint = when (status.tone) {
        Tone.DONE -> MaterialTheme.colorScheme.primary
        Tone.NEEDS_YOU -> MaterialTheme.colorScheme.tertiary
        Tone.ERROR -> MaterialTheme.colorScheme.error
        Tone.NEUTRAL -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        if (status == PlainState.UPDATING) {
            Halo13Animation(Halo13Motion.SOURCE_REFRESH, Modifier.size(24.dp), loop = true)
        } else {
            Icon(painterResource(R.drawable.halo_ic_source), null, Modifier.size(20.dp), tint = tint)
        }
        Text(stringResource(if (stale && status == PlainState.UP_TO_DATE) R.string.sp13_saved_older else status.label),
            color = tint, style = MaterialTheme.typography.bodySmall)
    }
}
