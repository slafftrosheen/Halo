package com.halo.app.ui.theme

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.*
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = android.app.Application::class)
class PalettePreferencesTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    @Before fun reset() { context.getSharedPreferences("halo_palettes", Context.MODE_PRIVATE).edit().clear().commit() }
    @Test fun `light and dark persist independently`() = runBlocking {
        val preferences = PalettePreferences(context)
        preferences.awaitReady()
        preferences.select(false, HaloPalette(HaloAccent.BLUE, HaloTone.WARM))
        preferences.select(true, HaloPalette(HaloAccent.LILAC, HaloTone.COOL))
        preferences.awaitReady()
        val restored = PalettePreferences(context)
        restored.awaitReady()
        assertEquals(HaloPalette(HaloAccent.BLUE, HaloTone.WARM), restored.state.value.light)
        assertEquals(HaloPalette(HaloAccent.LILAC, HaloTone.COOL), restored.state.value.dark)
    }
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    @Test fun `editing light before initial load preserves stored dark colours`() = runTest {
        context.getSharedPreferences("halo_palettes", Context.MODE_PRIVATE).edit()
            .putString("dark.accent", "CORAL").putString("dark.tone", "COOL").commit()
        val preferences = PalettePreferences(context, StandardTestDispatcher(testScheduler))
        preferences.select(false, HaloPalette(HaloAccent.BLUE, HaloTone.WARM))
        advanceUntilIdle()
        preferences.awaitReady()
        assertEquals(HaloPalette(HaloAccent.CORAL, HaloTone.COOL), preferences.state.value.dark)
        assertEquals(HaloPalette(HaloAccent.BLUE, HaloTone.WARM), preferences.state.value.light)
    }
    @Test fun `unknown stored choices fall back to readable defaults`() = runBlocking {
        context.getSharedPreferences("halo_palettes", Context.MODE_PRIVATE).edit().putString("dark.accent", "broken").putString("light.tone", "broken").commit()
        val preferences = PalettePreferences(context)
        preferences.awaitReady()
        assertEquals(HaloPalettes(), preferences.state.value)
    }
    @Test fun `extended backgrounds persist independently without replacing old profile fields`() = runBlocking {
        val preferences = PalettePreferences(context)
        preferences.selectPersisted(false, HaloPalette(HaloAccent.SAGE, HaloTone.PAPER, HaloBackgroundStyle.MIST))
        preferences.selectPersisted(true, HaloPalette(HaloAccent.ROSE, HaloTone.SLATE, HaloBackgroundStyle.STILL))
        val restored = PalettePreferences(context)
        restored.awaitReady()
        assertEquals(HaloPalette(HaloAccent.SAGE, HaloTone.PAPER, HaloBackgroundStyle.MIST), restored.state.value.light)
        assertEquals(HaloPalette(HaloAccent.ROSE, HaloTone.SLATE, HaloBackgroundStyle.STILL), restored.state.value.dark)
    }
}
