package com.halo.app.ui.screens.home

import com.halo.sdk.provider.CognitiveSessionState
import com.halo.sdk.runtime.SpeechSessionState

/** Starting a new voice session must not cancel work already owned by another interaction. */
internal fun homeVoiceStartAllowed(session: CognitiveSessionState, speech: SpeechSessionState,
    submissionBusy: Boolean, changingConversation: Boolean, listeningRequested: Boolean): Boolean =
    !submissionBusy && !changingConversation && !listeningRequested &&
        (session == CognitiveSessionState.Idle || session is CognitiveSessionState.Completed || session is CognitiveSessionState.Failed) &&
        (speech == SpeechSessionState.Idle || speech == SpeechSessionState.PassiveListening || speech is SpeechSessionState.Error)
