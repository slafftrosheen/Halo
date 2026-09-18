package com.halo.app.ui.theme

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = android.app.Application::class)
class AppearancePreferencesTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences("halo_appearance", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
    }

    @Test
    fun defaultsToSystemAndPersistsExplicitMode() {
        val preferences = AppearancePreferences(context)

        assertEquals(AppearanceMode.SYSTEM, preferences.mode.value)

        preferences.setMode(AppearanceMode.DARK)

        assertEquals(AppearanceMode.DARK, preferences.mode.value)
        assertEquals(
            AppearanceMode.DARK,
            AppearancePreferences(context).mode.value
        )
    }
}
