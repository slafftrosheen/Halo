package com.halo.host.android.appfunctions

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.halo.runtime.cognition.apps.*
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = android.app.Application::class)
class AppFunctionOldAndroidTest {
    @Test fun unsupportedPlatformKeepsDirectActionFallbackWithoutClassLoadingCrash() = runTest {
        val index = AppFunctionCapabilityIndex()
        val consumer = AndroidAppFunctionConsumer(ApplicationProvider.getApplicationContext<Context>(), index, backgroundScope)
        consumer.refresh()
        assertEquals(AppFunctionAvailability.UNSUPPORTED, index.state.value.availability)
    }
}
