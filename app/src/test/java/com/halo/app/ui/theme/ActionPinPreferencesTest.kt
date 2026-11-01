package com.halo.app.ui.theme

import android.app.Application
import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
class ActionPinPreferencesTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    @Before fun clear() { context.getSharedPreferences("halo_appearance", 0).edit().clear().commit() }
    @Test fun orderedFamilyPinsAndDeliberatelyEmptySelectionSurviveFreshOwner() = runBlocking {
        val owner = AppearancePreferences(context); owner.awaitLoaded()
        owner.setMode(AppearanceMode.DARK); owner.awaitPersistence()
        owner.setActionPinsPersisted("studio", emptyList(), listOf("page:page.studio.save", "page:page.studio.layers"))
        owner.setActionPinsPersisted("note", emptyList(), listOf("page:page.note.insert"))
        val reopened = AppearancePreferences(context); reopened.awaitLoaded()
        assertEquals(owner.actionPins.value, reopened.actionPins.value)
        reopened.setActionPinsPersisted("studio", reopened.actionPins.value.families.getValue("studio"), emptyList())
        val empty = AppearancePreferences(context); empty.awaitLoaded()
        assertEquals(emptyList<String>(), empty.actionPins.value.families["studio"].orEmpty())
        assertEquals(listOf("page:page.note.insert"), empty.actionPins.value.families["note"])
        assertEquals(AppearanceMode.DARK, empty.mode.value)
    }
    @Test fun staleSelectionAndOversizedListCannotOverwriteAcknowledgedPins() = runBlocking {
        val owner = AppearancePreferences(context); owner.awaitLoaded()
        val pins = listOf("page:page.canvas.add")
        owner.setActionPinsPersisted("canvas", emptyList(), pins)
        assertTrue(runCatching { owner.setActionPinsPersisted("canvas", emptyList(), listOf("wrong")) }.isFailure)
        assertTrue(runCatching { owner.setActionPinsPersisted("canvas", pins, (1..5).map { "action$it" }) }.isFailure)
        assertEquals(pins, owner.actionPins.value.families["canvas"])
    }
    @Test fun failedCommitDoesNotClaimNewSelectionAndMalformedDataIsExplicit() = runBlocking {
        val backing = context.getSharedPreferences("halo_appearance", 0)
        val wrapper = object : ContextWrapper(context) {
            override fun getApplicationContext(): Context = this
            override fun getSharedPreferences(name: String, mode: Int): SharedPreferences = object : SharedPreferences by backing {
                override fun edit(): SharedPreferences.Editor {
                    val delegate = backing.edit()
                    return object : SharedPreferences.Editor by delegate {
                        override fun putString(key: String?, value: String?): SharedPreferences.Editor {
                            delegate.putString(key, value)
                            return this // Keep the failing commit on the chained production call.
                        }
                        override fun commit(): Boolean = false
                    }
                }
            }
        }
        val owner = AppearancePreferences(wrapper); owner.awaitLoaded()
        assertTrue(runCatching { owner.setActionPinsPersisted("note", emptyList(), listOf("page:page.note.insert")) }.isFailure)
        assertTrue(owner.actionPins.value.families.isEmpty())
        backing.edit().putString("zone5_action_pins", "{broken").commit()
        val broken = AppearancePreferences(context); broken.awaitLoaded()
        assertTrue(broken.actionPins.value.unreadable)
        broken.setActionPinsPersisted("note", emptyList(), emptyList())
        val cleared = AppearancePreferences(context); cleared.awaitLoaded()
        assertFalse(cleared.actionPins.value.unreadable)
        assertTrue(cleared.actionPins.value.families.isEmpty())
    }
}
