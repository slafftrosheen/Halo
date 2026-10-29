package com.halo.app.ui.screens.home

import org.junit.Assert.*
import org.junit.Test

class HomeRecoveryActionsTest {
    private val ready = HomeRecoveryOptions(false, false, false, true, true, false, true, true)
    private fun actions(options: HomeRecoveryOptions, calls: MutableList<String> = mutableListOf()) =
        homeRecoveryActions(options, { calls += "activity" }, { calls += "retry" },
            { calls += "answers" }, { calls += "microphone" }, { calls += "voice" })

    @Test fun reviewAndAnswerSetupKeepTheirOriginalIndependentCallbacks() {
        val calls = mutableListOf<String>()
        val actions = actions(ready.copy(needsReview = true, retryable = true,
            needsRecovery = true, answersNeedSetup = true, microphoneGranted = false), calls)
        assertEquals(listOf("review", "setup-answers"), actions.map { it.id })
        actions.forEach { it.onInvoke() }
        assertEquals(listOf("activity", "answers"), calls)
    }
    @Test fun currentConversationRetryRemainsDisabledUntilDraftCanBeSent() {
        val pending = ready.copy(retryable = true, canRetry = false)
        assertFalse(actions(pending).single().enabled)
        assertTrue(actions(pending.copy(canRetry = true)).single().enabled)
        assertTrue(actions(pending.copy(sameConversation = false)).isEmpty())
        val calls = mutableListOf<String>()
        actions(pending.copy(canRetry = true), calls).single().onInvoke()
        assertEquals(listOf("retry"), calls)
    }
    @Test fun unknownSubmissionRecoveryAndVoiceSetupRemainReachable() {
        val calls = mutableListOf<String>()
        actions(ready.copy(needsRecovery = true, speechReady = false), calls).forEach { it.onInvoke() }
        assertEquals(listOf("activity", "voice"), calls)
        calls.clear()
        actions(ready.copy(microphoneGranted = false, speechReady = false), calls).single().onInvoke()
        assertEquals(listOf("microphone"), calls)
        assertTrue(actions(ready).isEmpty())
    }
    @Test fun answerSetupDisablesRetryWithoutReplacingCanonicalSetupCallback() {
        val calls = mutableListOf<String>()
        val actions = actions(ready.copy(retryable = true, answersNeedSetup = true), calls)
        assertFalse(actions.first { it.id == "retry" }.enabled)
        actions.single { it.id == "setup-answers" }.onInvoke()
        assertEquals(listOf("answers"), calls)
    }
}
