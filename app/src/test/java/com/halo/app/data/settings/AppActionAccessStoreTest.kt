package com.halo.app.data.settings

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [33])
class AppActionAccessStoreTest {
    private val context get() = ApplicationProvider.getApplicationContext<Application>()
    @Before fun clear() { context.getSharedPreferences("app_action_access", 0).edit().clear().commit() }
    @Test fun perActionAndAppDenialsSurviveRecreationWithoutEnablingSiblings() = runTest {
        val store = AppActionAccessStore(context)
        store.setAllowed("example.mail", "archive_current", false)
        assertFalse(AppActionAccessStore(context).allows("example.mail", "archive_current"))
        assertTrue(store.allows("example.mail", "search"))
        store.setAllowed("example.mail", "*", false)
        store.setAllowed("example.mail", "search", true)
        assertFalse(store.allows("example.mail", "search"))
        store.setAllowed("example.mail", "*", true)
        val restored = AppActionAccessStore(context)
        assertTrue(restored.allows("example.mail", "search"))
        assertFalse(restored.allows("example.mail", "archive_current"))
    }
    @Test fun catalogueFailureCannotEraseCommittedDenial() = runTest {
        val store = AppActionAccessStore(context) { error("Catalogue unavailable") }
        store.setAllowed("example.mail", "search", false)
        assertFalse(store.allows("example.mail", "search"))
        assertEquals(1L, store.revision)
        assertFalse(AppActionAccessStore(context).allows("example.mail", "search"))
    }
}
