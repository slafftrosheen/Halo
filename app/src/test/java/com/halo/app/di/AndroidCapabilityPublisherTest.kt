package com.halo.app.di

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.halo.runtime.cognition.tools.CapabilityCatalogue
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class AndroidCapabilityPublisherTest {
    @Test fun unchangedChecksDoNotTriggerAnInfiniteCatalogueRefreshLoop() {
        val publisher = AndroidCapabilityPublisher(ApplicationProvider.getApplicationContext<Context>())
        val catalogue = CapabilityCatalogue()
        publisher.publishInto(catalogue)
        val revision = catalogue.snapshot.value.revision
        repeat(3) { publisher.refreshInto(catalogue) }
        assertEquals(revision, catalogue.snapshot.value.revision)
    }
}
