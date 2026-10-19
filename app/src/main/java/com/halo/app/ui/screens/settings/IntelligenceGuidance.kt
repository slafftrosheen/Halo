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
        IntelligenceStep.CONFIGURE -> "Choose this phone, a computer at home or a cloud service."
        IntelligenceStep.CHECK_CONNECTION -> "Your choice is saved. Check that halo can reach it."
        IntelligenceStep.CHOOSE_MODEL -> "Connected. Choose a model before asking your first question."
        IntelligenceStep.TEST_REPLY -> "A model is selected. Check that it can answer."
        IntelligenceStep.VERIFIED -> "halo answered with this choice. Future requests show their own result."
        IntelligenceStep.WAIT -> "halo is checking your choice. This may take a moment."
    }
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(uiText("Next step"), style = MaterialTheme.typography.titleMedium)
        Text(uiText(instruction), style = MaterialTheme.typography.bodyMedium)
        when (step) {
            IntelligenceStep.CONFIGURE -> HaloFocusAction(uiText("Set up answers"), HaloGlyph.MODEL, true, onConfigure)
            IntelligenceStep.CHECK_CONNECTION -> HaloFocusAction(uiText("Check connection"), HaloGlyph.MODEL, true, onConnection)
            IntelligenceStep.CHOOSE_MODEL -> HaloFocusAction(uiText("Choose a model"), HaloGlyph.MODEL, true, onModels)
            IntelligenceStep.TEST_REPLY -> HaloFocusAction(uiText("Check answers"), HaloGlyph.MODEL, true, onReply)
            IntelligenceStep.VERIFIED -> HaloStatusPresentation(HaloStatus.COMPLETED, label = uiText("halo answered"))
            IntelligenceStep.WAIT -> HaloProgressPresentation(uiText("Checking this setup…"))
        }
    }
}
