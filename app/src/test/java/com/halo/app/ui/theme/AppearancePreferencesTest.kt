package com.halo.app.ui.theme

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
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
    fun defaultsToSystemAndPersistsExplicitMode() = runBlocking {
        val preferences = AppearancePreferences(context)
        preferences.awaitLoaded()

        assertEquals(AppearanceMode.SYSTEM, preferences.mode.value)

        preferences.setMode(AppearanceMode.DARK)
        preferences.awaitPersistence()

        assertEquals(AppearanceMode.DARK, preferences.mode.value)

        val restored = AppearancePreferences(context)
        restored.awaitLoaded()
        assertEquals(AppearanceMode.DARK, restored.mode.value)
    }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    @Test
    fun explicitSystemChoiceBeforeInitialLoadWinsOverStoredDarkMode() = runTest {
        context.getSharedPreferences("halo_appearance", Context.MODE_PRIVATE)
            .edit().putString("mode", AppearanceMode.DARK.name).commit()
        val preferences = AppearancePreferences(context, StandardTestDispatcher(testScheduler))
        preferences.setMode(AppearanceMode.SYSTEM)
        advanceUntilIdle()
        preferences.awaitLoaded()
        preferences.awaitPersistence()
        assertEquals(AppearanceMode.SYSTEM, preferences.mode.value)
        assertEquals(AppearanceMode.SYSTEM.name,
            context.getSharedPreferences("halo_appearance", Context.MODE_PRIVATE).getString("mode", null))
    }
}
