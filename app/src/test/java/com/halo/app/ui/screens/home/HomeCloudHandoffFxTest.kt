package com.halo.app.ui.screens.home

import com.halo.sdk.provider.AiExecutionLocation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HomeCloudHandoffFxTest {
    @Test fun handoffIsOnlyForTheActualRemoteExecutingProvider() {
        val locations = mapOf(
            "litert" to AiExecutionLocation.ON_DEVICE,
            "lan" to AiExecutionLocation.LOCAL_NETWORK,
            "cloud" to AiExecutionLocation.REMOTE,
        )
        assertEquals("cloud", cloudHandoffProviderId("cloud", locations))
        assertNull(cloudHandoffProviderId("litert", locations))
        assertNull(cloudHandoffProviderId("lan", locations))
        assertNull(cloudHandoffProviderId("unknown", locations))
        assertNull(cloudHandoffProviderId(null, locations))
    }
}
