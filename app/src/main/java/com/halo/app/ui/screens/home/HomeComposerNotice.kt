package com.halo.app.ui.screens.home

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.fillMaxWidth
import com.halo.app.language.uiText

/** Presentation only: no admission, retry or runtime authority. Never display raw diagnostics. */
internal fun homeComposerNotice(answersNeedSetup: Boolean, receipt: TextSubmissionState,
    conversationId: String, active: Boolean = false): String? = when {
    active || receipt.busy || receipt.phase == TextSubmissionPhase.WAITING_FOR_INPUT -> null
    answersNeedSetup -> "Answers need setup. Choose Set up answers below."
    receipt.conversationId != conversationId || !receipt.needsRecovery -> null
    !receipt.retryable -> "Review the recorded outcome in Activity before trying again."
    receipt.phase == TextSubmissionPhase.TIMED_OUT -> "Your message wasn’t accepted. You can try again."
    receipt.phase == TextSubmissionPhase.CANCELLED -> "Stopped. Your draft is here when you need it."
    else -> "Couldn’t finish that. Try again."
}

@Composable
internal fun HomeComposerNotice(message: String?) {
    if (message != null) Text(uiText(message), Modifier.fillMaxWidth(),
        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}
