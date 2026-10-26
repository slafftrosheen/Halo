package com.halo.app.ui.screens.home

import com.halo.app.ui.icons.HaloGlyph
import com.halo.app.ui.presence.ShellPageAction

/** Presentation-only migration of Home's original recovery and setup callbacks. */
internal data class HomeRecoveryOptions(
    val needsReview: Boolean,
    val retryable: Boolean,
    val needsRecovery: Boolean,
    val sameConversation: Boolean,
    val canRetry: Boolean,
    val answersNeedSetup: Boolean,
    val microphoneGranted: Boolean,
    val speechReady: Boolean,
)

internal fun homeRecoveryActions(
    options: HomeRecoveryOptions,
    onActivity: () -> Unit,
    onRetry: () -> Unit,
    onAnswers: () -> Unit,
    onMicrophone: () -> Unit,
    onVoice: () -> Unit,
): List<ShellPageAction> = buildList {
    when {
        options.needsReview -> add(ShellPageAction("review", "Review", HaloGlyph.APPROVAL, onInvoke = onActivity))
        options.retryable && options.sameConversation -> add(ShellPageAction("retry", "Retry", HaloGlyph.ACTIVITY,
            enabled = options.canRetry, onInvoke = onRetry))
        options.needsRecovery && options.sameConversation -> add(ShellPageAction("recover", "Open Activity", HaloGlyph.ACTIVITY, onInvoke = onActivity))
    }
    when {
        options.answersNeedSetup -> add(ShellPageAction("setup-answers", "Set up answers", HaloGlyph.MODEL, onInvoke = onAnswers))
        !options.microphoneGranted -> add(ShellPageAction("enable-voice", "Enable voice", HaloGlyph.LISTEN, onInvoke = onMicrophone))
        !options.speechReady -> add(ShellPageAction("setup-voice", "Set up voice", HaloGlyph.LISTEN, onInvoke = onVoice))
    }
}
