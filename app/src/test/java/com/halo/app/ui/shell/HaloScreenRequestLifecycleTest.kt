package com.halo.app.ui.shell

import android.app.Application
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
class HaloScreenRequestLifecycleTest {
    private class Owner : LifecycleOwner {
        override val lifecycle = LifecycleRegistry.createUnsafe(this)
    }

    private fun lifecycleTest(block: suspend TestScope.(Owner) -> Unit) = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val owner = Owner().apply { lifecycle.currentState = Lifecycle.State.CREATED }
        try { block(owner) } finally {
            owner.lifecycle.currentState = Lifecycle.State.DESTROYED
            runCurrent()
            Dispatchers.resetMain()
        }
    }

    @Test fun queuedRequestsAreRetainedWhileStoppedAndConsumedOnlyOnResume() = lifecycleTest { owner ->
        val pending = MutableStateFlow<String?>("notebook")
        val navigated = mutableListOf<String>()
        backgroundScope.launch { HaloScreenRequests.collectResumed(owner.lifecycle, pending, navigated::add) }
        runCurrent()
        owner.lifecycle.currentState = Lifecycle.State.STARTED
        runCurrent()
        assertEquals(emptyList<String>(), navigated)
        assertEquals("notebook", pending.value)
        owner.lifecycle.currentState = Lifecycle.State.RESUMED
        runCurrent()
        assertEquals(listOf("notebook"), navigated)
        assertNull(pending.value)
        owner.lifecycle.currentState = Lifecycle.State.CREATED
        runCurrent()
        pending.value = "voice_settings"
        runCurrent()
        assertEquals(listOf("notebook"), navigated)
        assertEquals("voice_settings", pending.value)
        owner.lifecycle.currentState = Lifecycle.State.RESUMED
        runCurrent()
        assertEquals(listOf("notebook", "voice_settings"), navigated)
        assertNull(pending.value)
    }

    @Test fun repeatedWarmRequestsDoNotNeedAComposeFrameOrDifferentRouteKey() = lifecycleTest { owner ->
        val pending = MutableStateFlow<String?>(null)
        val navigated = mutableListOf<String>()
        owner.lifecycle.currentState = Lifecycle.State.RESUMED
        backgroundScope.launch { HaloScreenRequests.collectResumed(owner.lifecycle, pending, navigated::add) }
        runCurrent()
        val routes = listOf("notebook", "voice_settings", "notebook", "voice_settings", "voice_settings")
        routes.forEach { route ->
            pending.value = route
            runCurrent()
            assertNull("The live collector must consume each accepted request", pending.value)
        }
        assertEquals(routes, navigated)
    }

    @Test fun stoppedHostCannotStealItsResumedSuccessorsRequestOrReplayOnReturn() = lifecycleTest { owner ->
        val successor = Owner().apply { lifecycle.currentState = Lifecycle.State.CREATED }
        val pending = MutableStateFlow<String?>(null)
        val first = mutableListOf<String>()
        val second = mutableListOf<String>()
        backgroundScope.launch { HaloScreenRequests.collectResumed(owner.lifecycle, pending, first::add) }
        backgroundScope.launch { HaloScreenRequests.collectResumed(successor.lifecycle, pending, second::add) }
        runCurrent()
        pending.value = "mini_halo"
        runCurrent()
        assertEquals("mini_halo", pending.value)
        successor.lifecycle.currentState = Lifecycle.State.RESUMED
        runCurrent()
        assertEquals(listOf("mini_halo"), second)
        successor.lifecycle.currentState = Lifecycle.State.DESTROYED
        owner.lifecycle.currentState = Lifecycle.State.RESUMED
        runCurrent()
        assertEquals(emptyList<String>(), first)
        assertNull(pending.value)
    }
}
