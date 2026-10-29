package com.halo.app.ui.screens.study

import androidx.compose.runtime.Composable
import com.halo.app.language.uiText

/** Only the typed app-owned review status is formatted. Provider/source feedback stays literal. */
@Composable
internal fun localizedCardReviewMessage(result: CardReviewResult): String {
    val days = result.nextReviewIntervalDays?.takeIf { result.verdict == true && it >= 0 }
    return if (days != null) uiText("Correct. Next review in %1\$d day(s).", days) else uiText(result.message)
}
