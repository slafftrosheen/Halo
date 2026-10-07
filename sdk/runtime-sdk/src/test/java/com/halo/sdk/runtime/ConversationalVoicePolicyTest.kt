package com.halo.sdk.runtime

import org.junit.Assert.*
import org.junit.Test

class ConversationalVoicePolicyTest {
    private fun evidence(ms: Long?, physical: Boolean = false, valid: Boolean = true) =
        BargeInEvidence(physical, true, true, valid, false, 0, ms)
    @Test fun physicalControlsOverrideEverySetting() {
        assertEquals(BargeInDecision.ACCEPT, BargeInPolicy.decide(evidence(0, true), true,
            BargeInPolicyConfig(spokenInterruptionEnabled = false)))
    }
    @Test fun durationAndRouteMustBeProven() {
        for (item in listOf(evidence(200), evidence(null), evidence(900, valid = false)))
            assertEquals(BargeInDecision.REJECT_ECHO, BargeInPolicy.decide(item, true))
        assertEquals(BargeInDecision.ACCEPT, BargeInPolicy.decide(evidence(500), true))
    }
    @Test fun phoneFallbackReflectsOriginAndExplicitOptIn() {
        val glasses = SpeechInputRoute.Device("opaque", null, "Glasses", AudioDeviceKind.WEARABLE)
        assertEquals(SpeechOutputFallback.DISALLOW_PHONE, fallbackFor(glasses, false))
        assertEquals(SpeechOutputFallback.ALLOW_PHONE, fallbackFor(glasses, true))
        assertEquals(SpeechOutputFallback.ALLOW_PHONE, fallbackFor(SpeechInputRoute.Phone, false))
        assertEquals(SpeechOutputFallback.ALLOW_PHONE, fallbackFor(null, false))
    }
    @Test fun externalOnlyDoesNotAcquireGlobalCancellationAuthority() {
        assertEquals(BargeInDecision.STOP_EXTERNAL_ONLY, BargeInPolicy.decide(
            evidence(100).copy(externalProviderInterrupted = true), true,
            BargeInPolicyConfig(spokenInterruptionEnabled = false)))
    }
}
