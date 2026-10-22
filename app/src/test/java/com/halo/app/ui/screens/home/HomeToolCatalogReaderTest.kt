package com.halo.app.ui.screens.home

import android.Manifest
import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.halo.app.ai.*
import com.halo.runtime.cognition.tools.ToolRegistry
import com.halo.runtime.cognition.tools.ToolAvailabilityResolver
import com.halo.sdk.provider.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
class HomeToolCatalogReaderTest {
    @Test fun readyConnectionWithMissingOrIneligibleModelRequiresAnswersSetup() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Application>()
        shadowOf(context).grantPermissions(Manifest.permission.RECORD_AUDIO)
        var eligible = false
        var connection = ProviderConnectionState.READY
        var reads = 0
        val registry = ToolRegistry(emptyList())
        val reader = HomeToolCatalogReader(context, registry, ToolAvailabilityResolver(registry),
            selectedProvider = { ProviderControlRow(ProviderConfigurationDescriptor("local", "Local fixture",
                AiExecutionLocation.ON_DEVICE, emptyList(), "FIXTURE", "LOCAL"), true, true, true,
                connection, ProviderCatalogueState.CACHED, 1) },
            answerModelAvailable = { eligible }, privacyMode = { PrivacyMode.PRIVATE },
            familyAllowsOnline = { false }, webAvailability = { reads++; null }, researchEnabled = { true },
            speechReady = { true }, attachmentsAvailable = { true }, imageConfigured = { false })
        suspend fun voice() = reader.read(HomeToolCatalogRequest(false, false)).entries.single { it.descriptor.id == "phone.voice" }
        assertEquals(HomeToolReason.ANSWERS, voice().reason)
        assertEquals(HomeToolRecovery.ANSWERS, voice().recovery)
        eligible = true
        assertEquals(HomeToolStatus.READY, voice().status)
        connection = ProviderConnectionState.CONFIGURED
        assertEquals(HomeToolReason.UNCHECKED, voice().reason)
        connection = ProviderConnectionState.AUTH_FAILED
        assertFalse(voice().canUse)
        assertTrue(reads > 0)
        shadowOf(context).denyPermissions(Manifest.permission.RECORD_AUDIO)
        assertEquals(HomeToolRecovery.PERMISSIONS, voice().recovery)
    }
}
