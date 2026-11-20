package com.halo.sdk.provider

import org.junit.Assert.*
import org.junit.Test

class InlineMediaTest {
    @Test fun missingMimeEmptyAndOversizedMediaAreRejectedBeforeDispatch() {
        for (request in listOf(AiRequest(audio = byteArrayOf(1)), AiRequest(image = byteArrayOf()),
            AiRequest(video = ByteArray(12 * 1024 * 1024 + 1), mimeTypes = listOf("video/mp4")))) {
            assertTrue(runCatching { request.inlineMedia() }.exceptionOrNull() is AiProviderException)
        }
    }
    @Test fun continuationDoesNotResendMedia() {
        assertTrue(AiRequest(image = byteArrayOf(1), appendTextAsUserMessage = false).inlineMedia().isEmpty())
    }
}
