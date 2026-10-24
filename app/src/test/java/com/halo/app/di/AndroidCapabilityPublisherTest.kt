package com.halo.app.di

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.halo.runtime.cognition.tools.CapabilityCatalogue
import com.halo.runtime.cognition.tools.CapabilityAvailability
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class AndroidCapabilityPublisherTest {
    @Test fun localConfigurationSupportDoesNotDependOnPhysicalDeviceOrAssistantRoleReadiness() {
        val publisher = AndroidCapabilityPublisher(ApplicationProvider.getApplicationContext<Context>())
        val catalogue = CapabilityCatalogue()
        publisher.publishInto(catalogue)
        val expected = setOf("host.assistant.preferences", "host.assistant.profiles", "halo.devices.saved", "halo.notifications.settings",
            "host.voice.configuration", "host.speech.inventory", "host.assistant.learning", "host.assistant.providers", "halo.routines.manage", "halo.study.active")
        val values = catalogue.snapshot.value.capabilities.filter { it.id.value in expected }
        assertEquals(expected, values.map { it.id.value }.toSet())
        assertTrue(values.all { it.availability == CapabilityAvailability.AVAILABLE && it.ownerId == "host:primary" })
        assertTrue(values.single { it.id.value == "halo.devices.saved" }.description.contains("does not mean"))
        assertTrue(values.single { it.id.value == "halo.notifications.settings" }.description.contains("does not grant"))
    }
    @Test fun unchangedChecksDoNotTriggerAnInfiniteCatalogueRefreshLoop() {
        val publisher = AndroidCapabilityPublisher(ApplicationProvider.getApplicationContext<Context>())
        val catalogue = CapabilityCatalogue()
        publisher.publishInto(catalogue)
        val revision = catalogue.snapshot.value.revision
        repeat(3) { publisher.refreshInto(catalogue) }
        assertEquals(revision, catalogue.snapshot.value.revision)
    }
}
