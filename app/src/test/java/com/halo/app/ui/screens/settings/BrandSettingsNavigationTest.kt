package com.halo.app.ui.screens.settings

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.halo.app.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** BrandBook 1.0: navigation icons are actual resources, not generic placeholders. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
class BrandSettingsNavigationTest {
    @Test fun brandNavigationIconsInflate() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val icons = listOf(
            R.drawable.halo_ic_integrations,
            R.drawable.halo_ic_skills,
            R.drawable.halo_ic_diagnostics,
            R.drawable.halo_ic_runtime_status,
            R.drawable.halo_ic_add_device,
            R.drawable.halo_ic_open_app,
            R.drawable.halo_ic_read_mode,
            R.drawable.halo_ic_calendar,
            R.drawable.halo_ic_notifications,
        )
        icons.forEach { assertNotNull("Could not load icon $it", context.getDrawable(it)) }
    }

    @Test fun privacyDestinationOpensExistingContextControls() {
        assertEquals("settings/intelligence/context", SettingsDestination.PRIVACY.route)
    }
}
