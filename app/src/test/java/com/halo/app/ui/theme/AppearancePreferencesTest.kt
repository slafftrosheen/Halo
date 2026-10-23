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

    @Test fun orbShortcutsPersistEmptySelectionWithoutChangingTheme() = runBlocking {
        val preferences = AppearancePreferences(context)
        preferences.awaitLoaded()
        assertEquals(com.halo.app.ui.presence.HaloOrbShortcut.defaults, preferences.orbShortcuts.value)
        preferences.setMode(AppearanceMode.DARK)
        preferences.setOrbShortcuts(emptySet())
        preferences.awaitPersistence()
        val restored = AppearancePreferences(context)
        restored.awaitLoaded()
        assertEquals(emptySet<com.halo.app.ui.presence.HaloOrbShortcut>(), restored.orbShortcuts.value)
        assertEquals(AppearanceMode.DARK, restored.mode.value)
    }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    @Test fun latestShortcutChoiceBeforeInitialLoadWinsAndDoesNotCancelModeRestore() = runTest {
        context.getSharedPreferences("halo_appearance", Context.MODE_PRIVATE).edit()
            .putString("mode", AppearanceMode.DARK.name).putStringSet("orb_shortcuts", setOf("CREATE", "UNKNOWN")).commit()
        val preferences = AppearancePreferences(context, StandardTestDispatcher(testScheduler))
        preferences.setOrbShortcuts(setOf(com.halo.app.ui.presence.HaloOrbShortcut.FIND))
        preferences.setOrbShortcuts(setOf(com.halo.app.ui.presence.HaloOrbShortcut.PHONE))
        advanceUntilIdle(); preferences.awaitPersistence()
        assertEquals(setOf(com.halo.app.ui.presence.HaloOrbShortcut.PHONE), preferences.orbShortcuts.value)
        assertEquals(AppearanceMode.DARK, preferences.mode.value)
        assertEquals(setOf("PHONE"), context.getSharedPreferences("halo_appearance", Context.MODE_PRIVATE).getStringSet("orb_shortcuts", null))
    }

    @Test fun unknownShortcutNamesNeverBecomeRoutesAndReloadUsesExistingOwner() = runBlocking {
        val preferences = AppearancePreferences(context)
        preferences.awaitLoaded()
        context.getSharedPreferences("halo_appearance", Context.MODE_PRIVATE).edit()
            .putStringSet("orb_shortcuts", setOf("NOTEBOOK", "arbitrary://execute")).commit()
        preferences.reloadFromDisk()
        assertEquals(setOf(com.halo.app.ui.presence.HaloOrbShortcut.NOTEBOOK), preferences.orbShortcuts.value)
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
