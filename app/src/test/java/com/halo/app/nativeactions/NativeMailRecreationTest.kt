package com.halo.app.nativeactions

import android.app.Application
import android.content.Intent
import android.os.Bundle
import com.halo.sdk.provider.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [34], application = Application::class)
class NativeMailRecreationTest {
    @Test fun recreatedConsentActivityCancelsPendingAuthorityBeforeAnyRestoredCallbackCanRead() {
        val (id, _) = NativeActivityRequests.create(RemoteDispatchProvenance(setOf(RemoteDataCategory.WORKSPACE), 47))
        val activity = Robolectric.buildActivity(NativeGoogleMailActivity::class.java,
            Intent().putExtra("requestId", id)).create(Bundle())
        try {
            assertTrue(activity.get().isFinishing)
            assertFalse(NativeActivityRequests.active(id))
            assertNull(NativeActivityRequests.remoteProvenance(id))
        } finally { activity.destroy(); NativeActivityRequests.cancel(id) }
    }
}
