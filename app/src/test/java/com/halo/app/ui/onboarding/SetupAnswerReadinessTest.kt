package com.halo.app.ui.onboarding

import com.halo.app.ai.GenerationProof
import com.halo.app.ai.ProviderCatalogueState
import com.halo.app.ai.ProviderConnectionState
import com.halo.app.ai.ProviderControlRow
import com.halo.sdk.provider.AiExecutionLocation
import com.halo.sdk.provider.ProviderConfigurationDescriptor
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SetupAnswerReadinessTest {
    private val verified = ProviderControlRow(
        descriptor = ProviderConfigurationDescriptor("service", "Service", AiExecutionLocation.REMOTE,
            emptyList(), "TEST", "ACCOUNT_DEPENDENT"),
        credentialConfigured = true, configured = true, selected = true,
        connection = ProviderConnectionState.READY, catalogue = ProviderCatalogueState.LIVE,
        modelCount = 1, generation = GenerationProof.PROVEN,
        lastSelectedModelId = "answer-model", generationModelId = "answer-model",
    )

    @Test fun acceptsTheSharedSettingsAnswerProofWithoutAnOnboardingOnlyReceipt() {
        assertTrue(setupAnswerVerified(verified))
        // A successful answer is evidence even if the separate authentication test was not run.
        assertTrue(setupAnswerVerified(verified.copy(connection = ProviderConnectionState.CONFIGURED)))
    }

    @Test fun aProviderResolvedModelDoesNotInvalidateTheSelectedAliasProof() {
        assertTrue(setupAnswerVerified(verified.copy(
            lastSelectedModelId = "service/auto", generationModelId = "resolved/model")))
    }

    @Test fun changingConfigurationOrSelectionRequiresAFreshSharedProof() {
        assertFalse(setupAnswerVerified(null))
        assertFalse(setupAnswerVerified(verified.copy(configured = false)))
        assertFalse(setupAnswerVerified(verified.copy(selected = false)))
        assertFalse(setupAnswerVerified(verified.copy(lastSelectedModelId = null)))
        // The canonical repository clears generation proof when the selected model or key changes.
        assertFalse(setupAnswerVerified(verified.copy(lastSelectedModelId = "new-model",
            generation = GenerationProof.NOT_TESTED)))
        assertFalse(setupAnswerVerified(verified.copy(generation = GenerationProof.CHECKING)))
        assertFalse(setupAnswerVerified(verified.copy(generation = GenerationProof.FAILED)))
    }

    @Test fun aFailedOrUnavailableConnectionCannotAppearReadyFromAnEarlierAnswer() {
        ProviderConnectionState.entries.filter {
            it != ProviderConnectionState.READY && it != ProviderConnectionState.CONFIGURED
        }.forEach { state -> assertFalse(state.name, setupAnswerVerified(verified.copy(connection = state))) }
    }
}
