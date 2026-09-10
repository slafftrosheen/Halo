package com.halo.sdk.provider

import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test

class AiRequestCapabilityValidationTest {

    @Test
    fun `rejects image when provider only supports text`() {
        try {
            AiRequest(image = byteArrayOf(1)).validateAgainst(setOf(AiCapability.TEXT))
            fail("Expected UNSUPPORTED_CAPABILITY")
        } catch (e: AiProviderException) {
            assertEquals(AiErrorType.UNSUPPORTED_CAPABILITY, e.type)
        }
    }

    @Test
    fun `multimodal capability accepts bound inputs`() {
        AiRequest(
            text = "describe this",
            image = byteArrayOf(1),
            audio = byteArrayOf(2),
            video = byteArrayOf(3)
        ).validateAgainst(setOf(AiCapability.MULTIMODAL))
    }
}
