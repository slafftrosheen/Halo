package com.halo.runtime.cognition.session

import com.halo.runtime.cognition.context.ImageContext
import com.halo.sdk.provider.AiRequest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class CognitiveRequestBindingTest {

    @Test
    fun `fresh assembled image is copied into request`() {

        val request =
            AiRequest(
                text = "What is this?"
            )

        val imageBytes =
            byteArrayOf(
                10,
                20,
                30
            )

        val image =
            ImageContext(
                imageBytes =
                    imageBytes,
                mimeType =
                    "image/jpeg",
                timestampMs =
                    123L
            )

        val binding =
            request.copy(
                image =
                    image.imageBytes,
                mimeTypes =
                    listOf(
                        image.mimeType
                    )
            )

        assertArrayEquals(
            imageBytes,
            binding.image
        )

        assertEquals(
            listOf("image/jpeg"),
            binding.mimeTypes
        )
    }

    @Test
    fun `explicit request image remains authoritative`() {

        val explicitImage =
            byteArrayOf(
                90,
                91,
                92
            )

        val request =
            AiRequest(
                text = "Describe this",
                image =
                    explicitImage,
                mimeTypes =
                    listOf(
                        "image/png"
                    )
            )

        val assembled =
            ImageContext(
                imageBytes =
                    byteArrayOf(
                        1,
                        2,
                        3
                    ),
                mimeType =
                    "image/jpeg",
                timestampMs =
                    123L
            )

        val bound =
            if (request.image != null) {
                request
            } else {
                request.copy(
                    image =
                        assembled.imageBytes,
                    mimeTypes =
                        listOf(
                            assembled.mimeType
                        )
                )
            }

        assertArrayEquals(
            explicitImage,
            bound.image
        )

        assertEquals(
            listOf("image/png"),
            bound.mimeTypes
        )
    }
}
