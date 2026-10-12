package com.halo.app.ui.screens.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.halo.app.ai.GenerationProof
import com.halo.app.ai.ProviderConnectionState
import com.halo.app.language.uiText
import com.halo.app.ui.design.HaloFocusAction
import com.halo.app.ui.design.HaloProgressPresentation
import com.halo.app.ui.design.HaloStatus
import com.halo.app.ui.design.HaloStatusPresentation
import com.halo.app.ui.icons.HaloGlyph

internal enum class IntelligenceStep { CONFIGURE, CHECK_CONNECTION, CHOOSE_MODEL, TEST_REPLY, VERIFIED, WAIT }

internal fun intelligenceNextStep(configured: Boolean, connection: ProviderConnectionState?,
    modelSelected: Boolean, generation: GenerationProof?): IntelligenceStep = when {
    !configured -> IntelligenceStep.CONFIGURE
    connection == ProviderConnectionState.CHECKING || generation == GenerationProof.CHECKING -> IntelligenceStep.WAIT
    connection != ProviderConnectionState.READY -> IntelligenceStep.CHECK_CONNECTION
    !modelSelected -> IntelligenceStep.CHOOSE_MODEL
    generation != GenerationProof.PROVEN -> IntelligenceStep.TEST_REPLY
    else -> IntelligenceStep.VERIFIED
}

@Composable
internal fun IntelligenceGuidance(step: IntelligenceStep, onConfigure: () -> Unit,
    onModels: () -> Unit, onConnection: () -> Unit, onReply: () -> Unit) {
    val instruction = when (step) {
        IntelligenceStep.CONFIGURE -> "Choose a provider or an on-device model. Save its configuration before testing."
        IntelligenceStep.CHECK_CONNECTION -> "Configuration is saved. Check the connection before relying on it."
        IntelligenceStep.CHOOSE_MODEL -> "The connection reports ready. Choose a model for your conversation."
        IntelligenceStep.TEST_REPLY -> "A model is selected. Test a reply to check that this setup can generate an answer."
        IntelligenceStep.VERIFIED -> "A reply test succeeded with this provider. This does not verify every model or future request; Home shows actual outcomes."
        IntelligenceStep.WAIT -> "A real check is running. Wait for its result before starting another check."
    }
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(uiText("Next step"), style = MaterialTheme.typography.titleMedium)
        Text(uiText(instruction), style = MaterialTheme.typography.bodyMedium)
        when (step) {
            IntelligenceStep.CONFIGURE -> HaloFocusAction(uiText("Set up answers"), HaloGlyph.MODEL, true, onConfigure)
            IntelligenceStep.CHECK_CONNECTION -> HaloFocusAction(uiText("Test connection"), HaloGlyph.MODEL, true, onConnection)
            IntelligenceStep.CHOOSE_MODEL -> HaloFocusAction(uiText("Choose a model"), HaloGlyph.MODEL, true, onModels)
            IntelligenceStep.TEST_REPLY -> HaloFocusAction(uiText("Test a reply"), HaloGlyph.MODEL, true, onReply)
            IntelligenceStep.VERIFIED -> HaloStatusPresentation(HaloStatus.COMPLETED, label = uiText("Provider reply test succeeded"))
            IntelligenceStep.WAIT -> HaloProgressPresentation(uiText("Checking this setup…"))
        }
    }
}
