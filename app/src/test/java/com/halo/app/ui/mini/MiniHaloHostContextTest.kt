package com.halo.app.ui.mini

import android.app.Activity
import android.app.Application
import android.content.ContextWrapper
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
class MiniHaloHostContextTest {
    @Test fun localizedAndThemedContextsResolveTheSameNativeWindowOwner() {
        val controller = Robolectric.buildActivity(Activity::class.java).setup()
        try {
            val owner = controller.get()
            val wrapped = android.view.ContextThemeWrapper(ContextWrapper(owner), android.R.style.Theme_Material_Light)
            assertSame(owner, miniHaloActivityOwner(wrapped))
            assertSame(owner, miniHaloActivityOwner(owner))
        } finally { controller.pause().stop().destroy() }
    }
    @Test fun applicationContextDoesNotPretendToOwnAWindow() {
        assertNull(miniHaloActivityOwner(ApplicationProvider.getApplicationContext<Application>()))
    }
}
