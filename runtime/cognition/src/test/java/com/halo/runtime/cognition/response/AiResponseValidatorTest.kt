package com.halo.runtime.cognition.response

import com.halo.sdk.provider.AiCapability
import com.halo.sdk.provider.AiContext
import com.halo.sdk.provider.AiErrorType
import com.halo.sdk.provider.AiModel
import com.halo.sdk.provider.AiProvider
import com.halo.sdk.provider.AiProviderException
import com.halo.sdk.provider.AiRequest
import com.halo.sdk.provider.AiResponse
import com.halo.sdk.provider.AiResponseValidator
import com.halo.sdk.provider.AiStreamEvent
import com.halo.sdk.provider.ProviderStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test

class AiResponseValidatorTest {

    private class FakeProvider(
        override val id: String = "fake"
    ) : AiProvider {

        override val displayName =
            "Fake Provider"

        override val isLocal =
            true

        override val status =
            MutableStateFlow(
                ProviderStatus.READY
            )

        override suspend fun getStatus(): ProviderStatus =
            status.value

        override suspend fun getCapabilities():
            Set<AiCapability> =
            setOf(
                AiCapability.TEXT
            )

        override suspend fun generate(
            request: AiRequest,
            context: AiContext
        ): AiResponse =
            AiResponse(
                text = "ok",
                providerId = id
            )

        override fun stream(
            request: AiRequest,
            context: AiContext
        ): Flow<AiStreamEvent> =
            emptyFlow()

        override suspend fun listModels():
            List<AiModel> =
            emptyList()

        override suspend fun testConnection():
            Boolean =
            true
    }

    @Test
    fun `valid response passes`() =
        runTest {

            val provider =
                FakeProvider()

            val response =
                AiResponse(
                    text = "hello",
                    providerId =
                        provider.id
                )

            val result =
                AiResponseValidator.validate(
                    provider,
                    response
                )

            assertEquals(
                "hello",
                result.text
            )
        }

    @Test
    fun `identity mismatch fails`() =
        runTest {

            val provider =
                FakeProvider(
                    "provider-a"
                )

            val response =
                AiResponse(
                    text = "hello",
                    providerId =
                        "provider-b"
                )

            try {

                AiResponseValidator.validate(
                    provider,
                    response
                )

                fail(
                    "Expected INVALID_RESPONSE"
                )

            } catch (
                e: AiProviderException
            ) {

                assertEquals(
                    AiErrorType.INVALID_RESPONSE,
                    e.type
                )
            }
        }

    @Test
    fun `empty response fails`() =
        runTest {

            val provider =
                FakeProvider()

            val response =
                AiResponse(
                    text = "",
                    providerId =
                        provider.id
                )

            try {

                AiResponseValidator.validate(
                    provider,
                    response
                )

                fail(
                    "Expected EMPTY_RESPONSE"
                )

            } catch (
                e: AiProviderException
            ) {

                assertEquals(
                    AiErrorType.EMPTY_RESPONSE,
                    e.type
                )
            }
        }
}
