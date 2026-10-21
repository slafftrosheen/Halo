package com.halo.app.screen

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [33])
class LocalScreenStoreTest {
    private val context get() = ApplicationProvider.getApplicationContext<Application>()
    @Before fun clear() { context.getSharedPreferences("halo_screen", 0).edit().clear().commit() }
    @Test fun layoutConfigurationAndTopicsSurviveRestart() {
        val store = LocalScreenStore(context)
        val layout = ScreenWidgets.starter("Morning").copy(cards = listOf(ScreenCard("calendar", null,
            CardSize.EXPANDED, true, mapOf("calendar" to "chosen"))))
        store.saveLayout(layout); store.setTopics("Space, architecture")
        val restored = LocalScreenStore(context)
        assertEquals(layout.cards, restored.layout.value!!.cards)
        assertEquals("Space, architecture", restored.topics.value)
    }
    @Test fun backgroundBindingDoesNotReplaceUserUndo() {
        val store = LocalScreenStore(context)
        store.saveLayout(ScreenWidgets.starter("Morning"))
        val initial = store.layout.value!!
        store.saveLayout(ScreenWidgets.starter("Focus"))
        store.saveLayout(store.layout.value!!.copy(name = "Background update"), recordUndo = false)
        store.undo()
        assertEquals(initial, store.layout.value)
    }
}
