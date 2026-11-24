package com.halo.app.ui.screens.study

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.pluralStringResource
import com.halo.app.R
import com.halo.app.language.uiText

/** Only the typed app-owned review status is formatted. Provider/source feedback stays literal. */
@Composable
internal fun localizedCardReviewMessage(result: CardReviewResult): String {
    val days = result.nextReviewIntervalDays ?: return uiText(result.message)
    val timing = when {
        days <= 1 -> stringResource(R.string.ln13_review_tomorrow)
        days == 7 -> stringResource(R.string.ln13_review_next_week)
        days >= 30 -> stringResource(R.string.ln13_review_next_month)
        else -> pluralStringResource(R.plurals.ln13_review_in_days, days, days)
    }
    return stringResource(R.string.ln13_comes_back, timing)
}
