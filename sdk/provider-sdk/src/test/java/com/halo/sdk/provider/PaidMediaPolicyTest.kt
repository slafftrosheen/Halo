package com.halo.sdk.provider

import org.junit.Assert.*
import org.junit.Test

/** Source regression cases. Not executed in this code-only batch. */
class PaidMediaPolicyTest {
    @Test fun remoteImageIsDeniedWithoutSeparateOptIn() {
        val error = assertThrows(AiProviderException::class.java) {
            PaidMediaPolicy.requireAllowed(false, AiRequest(image = byteArrayOf(1)), false)
        }
        assertEquals(AiErrorType.CAPABILITY_DISABLED, error.type)
        assertEquals("PAID_MEDIA_CONSENT_REQUIRED", error.details.providerCode)
    }
    @Test fun remoteAudioAndVideoAreDenied() {
        listOf(AiRequest(audio = byteArrayOf(1)), AiRequest(video = byteArrayOf(2))).forEach { request ->
            assertThrows(AiProviderException::class.java) { PaidMediaPolicy.requireAllowed(false, request, false) }
        }
    }
    @Test fun imageWorkloadCannotBypassGateBeforePayloadPreparation() {
        assertThrows(AiProviderException::class.java) {
            PaidMediaPolicy.requireAllowed(false, AiRequest(text = "explain"), false,
                AiWorkloadSpec(kind = AiWorkloadKind.VISUAL_UNDERSTANDING))
        }
    }
    @Test fun explicitRequiredMediaCapabilityIsProtected() {
        assertThrows(AiProviderException::class.java) {
            PaidMediaPolicy.requireAllowed(false, AiRequest(text = "transcribe"), false,
                AiWorkloadSpec(requiredCapabilities = setOf(AiCapability.AUDIO)))
        }
    }
    @Test fun localMediaNeedsNoCloudSpendingOptIn() {
        PaidMediaPolicy.requireAllowed(true, AiRequest(image = byteArrayOf(1)), false)
    }
    @Test fun ordinaryTextIsUnchanged() {
        PaidMediaPolicy.requireAllowed(false, AiRequest(text = "Hello"), false)
    }
    @Test fun optInCanBeRevokedBeforeNextDispatch() {
        val request = AiRequest(image = byteArrayOf(1))
        PaidMediaPolicy.requireAllowed(false, request, true)
        assertThrows(AiProviderException::class.java) { PaidMediaPolicy.requireAllowed(false, request, false) }
    }
}
