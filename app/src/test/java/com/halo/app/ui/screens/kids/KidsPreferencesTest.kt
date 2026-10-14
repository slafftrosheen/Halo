package com.halo.app.ui.screens.kids

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = android.app.Application::class)
class KidsPreferencesTest {
    @Test fun `mode is opt in persisted and reversible`() = runBlocking {
        val context: Context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences("halo_kids_ui", Context.MODE_PRIVATE).edit().clear().commit()
        val preferences = KidsPreferences(context)
        assertFalse(preferences.getEnabled())
        preferences.setEnabled(true)
        assertTrue(KidsPreferences(context).getEnabled())
        preferences.setEnabled(false)
        assertFalse(KidsPreferences(context).getEnabled())
    }
}
