package com.halo.app.ui.screens.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.Surface
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.padding
import com.halo.app.ui.design.HaloShapes
import com.halo.app.ui.fx.haloTrace
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.halo.app.ai.GenerationProof
import com.halo.app.ai.ProviderConnectionState
import com.halo.app.language.uiText
import com.halo.app.ui.components.HaloActionButton
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
    onModels: () -> Unit, onConnection: () -> Unit, onReply: () -> Unit, guided: Boolean = false) {
    val heading = when (step) {
        IntelligenceStep.CONFIGURE -> "Choose how halo answers"
        IntelligenceStep.CHECK_CONNECTION -> "Check the connection"
        IntelligenceStep.CHOOSE_MODEL -> "Choose a conversation model"
        IntelligenceStep.TEST_REPLY -> "One reply to get ready"
        IntelligenceStep.VERIFIED -> "Reply check completed"
        IntelligenceStep.WAIT -> "Checking this setup"
    }
    val instruction = when (step) {
        IntelligenceStep.CONFIGURE -> "Choose where halo answers, then finish setup."
        IntelligenceStep.CHECK_CONNECTION -> "Your setup is saved. Check that halo can reach it."
        IntelligenceStep.CHOOSE_MODEL -> "Choose the model for your everyday conversations."
        IntelligenceStep.TEST_REPLY -> "Try one reply to see how this setup answers."
        IntelligenceStep.VERIFIED -> "Your last reply check succeeded."
        IntelligenceStep.WAIT -> "Wait for the result before starting another check."
    }
    Surface(Modifier.fillMaxWidth().haloTrace(enabled = guided,
        accent = MaterialTheme.colorScheme.primary, corner = 20.dp),
        shape = HaloShapes.card, color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
    Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(uiText(if (step == IntelligenceStep.VERIFIED) "Checked with a reply" else "Next step"),
            style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
        Text(uiText(heading), style = MaterialTheme.typography.titleMedium)
        Text(uiText(instruction), style = MaterialTheme.typography.bodyMedium)
        when (step) {
            IntelligenceStep.CONFIGURE -> GuidanceAction("Set up answers", onConfigure)
            IntelligenceStep.CHECK_CONNECTION -> GuidanceAction("Test connection", onConnection)
            IntelligenceStep.CHOOSE_MODEL -> GuidanceAction("Choose a model", onModels)
            IntelligenceStep.TEST_REPLY -> GuidanceAction("Test a reply", onReply)
            IntelligenceStep.VERIFIED -> HaloStatusPresentation(HaloStatus.COMPLETED, label = uiText("Provider reply test succeeded"))
            IntelligenceStep.WAIT -> HaloProgressPresentation(uiText("Checking this setup…"))
        }
    }
}
}


@Composable
private fun GuidanceAction(label: String, onClick: () -> Unit) {
    HaloActionButton(onClick = onClick, modifier = Modifier.fillMaxWidth()) { Text(uiText(label)) }
}
