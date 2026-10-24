package com.halo.app.ui.shell

import com.halo.cognition.language.NativeHaloScreenDestination
import org.junit.Assert.assertTrue
import org.junit.Test

class HaloAssistantScreenParityTest {
    @Test fun everyDeterministicAssistantDestinationHasACanonicalHostDestination() {
        NativeHaloScreenDestination.entries.forEach {
            assertTrue(it.wireId, it.wireId in HaloScreenCatalog.screens)
        }
    }
}
