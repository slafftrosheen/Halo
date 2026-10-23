package com.halo.app.ui.screens.settings

import androidx.compose.runtime.Composable
import com.halo.app.privacy.SharedAnswerHistory

/** The existing Settings destination now projects the shared dispatch receipt owner. */
@Composable fun SharedAnswerHistoryScreen(history: SharedAnswerHistory, onBack: () -> Unit) {
    ModeProfilesScreen(onBack, {}, {}, {}, activityOnly = true, clearLegacyHistory = { history.clear() })
}
