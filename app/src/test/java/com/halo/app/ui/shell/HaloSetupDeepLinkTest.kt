package com.halo.app.ui.shell

import android.app.Application
import android.net.Uri
import com.halo.app.ui.onboarding.SetupStage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
class HaloSetupDeepLinkTest {
    @Test fun requestedStageSurvivesResolution() {
        for (stage in SetupStage.entries) {
            val route = "guided_setup/${stage.name}"
            val request = HaloDeepLinks.PREFIX + route
            assertEquals(request, HaloDeepLinks.resolve(Uri.parse("halo://onboarding/${stage.name.lowercase()}")))
            assertTrue(HaloDeepLinks.acceptsRequest(request))
            assertEquals(stage, HaloDeepLinks.setupStage(route))
        }
    }

    @Test fun plainLinkResumesAndInvalidStepsAndParametersAreRejected() {
        assertEquals("halo-entry:guided_setup", HaloDeepLinks.resolve(Uri.parse("halo://onboarding")))
        assertNull(HaloDeepLinks.setupStage("guided_setup"))
        assertNull(HaloDeepLinks.resolve(Uri.parse("halo://onboarding/arbitrary")))
        assertNull(HaloDeepLinks.resolve(Uri.parse("halo://onboarding/ai?grant=true")))
        assertFalse(HaloDeepLinks.acceptsRequest("halo-entry:guided_setup/AI?grant=true"))
    }
}
