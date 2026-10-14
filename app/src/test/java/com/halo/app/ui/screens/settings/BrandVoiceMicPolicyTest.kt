package com.halo.app.ui.screens.settings

import org.junit.Assert.assertEquals
import org.junit.Test

class BrandVoiceMicPolicyTest {
    @Test fun microphoneTestIsUserStartedAndCannotBeDuplicated() {
        assertEquals(VoiceMicAction.START, voiceMicAction(VoiceSettingsUiState()))
        assertEquals(VoiceMicAction.FINISH,
            voiceMicAction(VoiceSettingsUiState(isTesting = true, testRecording = true)))
        assertEquals(VoiceMicAction.WAIT,
            voiceMicAction(VoiceSettingsUiState(isTesting = true, testRecording = false)))
        assertEquals(VoiceMicAction.DISABLED,
            voiceMicAction(VoiceSettingsUiState(isDownloading = true)))
    }

    @Test fun modelDownloadHasPriorityOverStaleCaptureState() {
        assertEquals(VoiceMicAction.DISABLED,
            voiceMicAction(VoiceSettingsUiState(
                isDownloading = true, isTesting = true, testRecording = true
            )))
    }
}
