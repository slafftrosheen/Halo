package com.halo.app.ui.screens.settings

import com.halo.sdk.provider.AiExecutionLocation
import org.junit.Assert.assertEquals
import org.junit.Test

class IntelligenceOverviewPresentationTest {
    @Test fun locationLabelDependsOnlyOnActualSelectedProvider() {
        assertEquals("No location selected", intelligenceLocationLabel(null))
        assertEquals("On this phone", intelligenceLocationLabel(AiExecutionLocation.ON_DEVICE))
        assertEquals("On your network", intelligenceLocationLabel(AiExecutionLocation.LOCAL_NETWORK))
        assertEquals("In the cloud", intelligenceLocationLabel(AiExecutionLocation.REMOTE))
    }
    @Test fun providerIdentifierDoesNotLeakIntoOverview() {
        assertEquals("Model selected", intelligenceModelSummary("private/model/revision"))
        assertEquals("No model selected", intelligenceModelSummary(null))
    }
}
