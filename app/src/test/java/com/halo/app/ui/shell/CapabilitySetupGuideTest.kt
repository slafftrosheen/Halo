package com.halo.app.ui.shell

import android.Manifest
import android.app.Application
import android.content.pm.PackageManager
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.result.ActivityResultRegistry
import androidx.activity.result.ActivityResultRegistryOwner
import androidx.activity.result.contract.ActivityResultContract
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.core.app.ActivityOptionsCompat
import androidx.test.core.app.ApplicationProvider
import com.halo.app.ui.onboarding.SetupProgressStore
import com.halo.app.ui.theme.HaloTheme
import com.halo.sdk.runtime.Storage
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
class CapabilitySetupGuideTest {
    @get:Rule val compose = createComposeRule()
    private val registry = RecordingRegistry()

    private fun show(store: SetupProgressStore) {
        val app = ApplicationProvider.getApplicationContext<Application>()
        Shadows.shadowOf(app.packageManager).setSystemFeature(PackageManager.FEATURE_MICROPHONE, true)
        Shadows.shadowOf(app).denyPermissions(Manifest.permission.RECORD_AUDIO)
        val owner = object : ActivityResultRegistryOwner { override val activityResultRegistry = registry }
        compose.setContent {
            CompositionLocalProvider(LocalActivityResultRegistryOwner provides owner) {
                HaloTheme(darkTheme = false) {
                    Column(Modifier.verticalScroll(rememberScrollState())) { CapabilitySetupGuide(store) }
                }
            }
        }
    }

    @Test fun openingGuideNeverLaunchesAndExplicitStartRequestsOnlyCurrentCapability() {
        val store = SetupProgressStore(MemoryStorage())
        show(store)
        compose.runOnIdle { assertTrue(registry.inputs.isEmpty()) }
        compose.onNodeWithText("Start capability setup").performScrollTo().performClick()
        compose.waitForIdle()
        assertTrue(registry.inputs.isEmpty()) // Explanation and Skip are available before every OS prompt.
        compose.onNodeWithText("Continue setup").performScrollTo().performClick()
        compose.waitForIdle()
        assertEquals(1, registry.inputs.size)
        assertEquals(listOf(Manifest.permission.RECORD_AUDIO), (registry.inputs.single() as Array<*>).toList())
        assertEquals("microphone", store.capabilityGuide.pendingId)
        assertEquals(setOf("microphone"), store.capabilityGuide.attemptedIds)
    }

    @Test fun restoredPendingRequestDoesNotReplayWithoutUserAction() {
        val memory = MemoryStorage()
        SetupProgressStore(memory).apply { startCapabilityGuide("microphone"); markCapabilityRequested("microphone") }
        val restored = SetupProgressStore(memory)
        show(restored)
        compose.runOnIdle { assertTrue(registry.inputs.isEmpty()) }
        compose.onNodeWithText("Continue setup").performScrollTo().assertExists()
        assertEquals("microphone", restored.capabilityGuide.pendingId)
    }

    @Test fun denialAdvancesWithoutFalseGrantOrAnotherAutomaticPrompt() {
        val store = SetupProgressStore(MemoryStorage()).apply { startCapabilityGuide("microphone") }
        show(store)
        compose.onNodeWithText("Continue setup").performScrollTo().performClick()
        compose.waitForIdle()
        compose.runOnIdle { registry.complete(mapOf(Manifest.permission.RECORD_AUDIO to false)) }
        compose.waitForIdle()
        assertEquals(1, registry.inputs.size)
        assertNull(store.capabilityGuide.pendingId)
        assertNotEquals("microphone", store.capabilityGuide.currentId)
        assertEquals(setOf("microphone"), store.capabilityGuide.attemptedIds)
    }

    @Test fun exhaustedSequenceShowsCompletionAndDisablesStart() {
        val store = SetupProgressStore(MemoryStorage())
        guidedCapabilityInventory(33).forEach {
            store.startCapabilityGuide(it.id)
            store.markCapabilityRequested(it.id)
            store.advanceCapabilityGuide(it.id, null, attempted = true)
        }
        show(store)
        compose.onNodeWithText("Start capability setup").performScrollTo().assertIsNotEnabled()
        compose.runOnIdle { assertTrue(registry.inputs.isEmpty()) }
    }

    @Test fun skipNeverLaunchesAndDoesNotRecordAnAttempt() {
        val store = SetupProgressStore(MemoryStorage()).apply { startCapabilityGuide("microphone") }
        show(store)
        compose.onNodeWithText("Skip this capability").performScrollTo().performClick()
        compose.runOnIdle {
            assertTrue(registry.inputs.isEmpty())
            assertEquals(setOf("microphone"), store.capabilityGuide.skippedIds)
            assertTrue(store.capabilityGuide.attemptedIds.isEmpty())
        }
    }

    @Test fun androidGrantAndRevocationOverrideSavedSetupReceipts() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        Shadows.shadowOf(app.packageManager).setSystemFeature(PackageManager.FEATURE_MICROPHONE, true)
        val platform = AndroidCapabilityGuide(app)
        val mic = platform.inventory.first { it.id == "microphone" }
        Shadows.shadowOf(app).grantPermissions(Manifest.permission.RECORD_AUDIO)
        assertEquals(CapabilityAccessState.ALLOWED, platform.status(mic, true))
        Shadows.shadowOf(app).denyPermissions(Manifest.permission.RECORD_AUDIO)
        assertEquals(CapabilityAccessState.SETTINGS_REQUIRED, platform.status(mic, true))
    }

    @Test fun approximateLocationIsUsableAndNeverShownAsPrecise() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        Shadows.shadowOf(app).grantPermissions(Manifest.permission.ACCESS_COARSE_LOCATION)
        Shadows.shadowOf(app).denyPermissions(Manifest.permission.ACCESS_FINE_LOCATION)
        val platform = AndroidCapabilityGuide(app)
        val location = platform.inventory.first { it.id == "location" }
        assertTrue(platform.granted(location))
        assertEquals(CapabilityAccessState.LIMITED, platform.status(location, true))
    }

    @Test @Config(sdk = [26]) fun assistantRoleIsUnavailableOnAndroidBeforeRoleApi() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val platform = AndroidCapabilityGuide(app)
        val assistant = platform.inventory.first { it.id == "assistant" }
        assertEquals(CapabilityAccessState.UNAVAILABLE, platform.status(assistant, false))
    }

    private class RecordingRegistry : ActivityResultRegistry() {
        val inputs = mutableListOf<Any?>()
        private var lastCode = 0
        override fun <I, O> onLaunch(requestCode: Int, contract: ActivityResultContract<I, O>, input: I,
            options: ActivityOptionsCompat?) { lastCode = requestCode; inputs.add(input) }
        fun complete(result: Map<String, Boolean>) { dispatchResult(lastCode, result) }
    }

    private class MemoryStorage : Storage {
        private val values = mutableMapOf<String, Any>()
        override fun getString(key: String, defaultValue: String?) = values[key] as? String ?: defaultValue
        override fun putString(key: String, value: String?) { if (value == null) values.remove(key) else values[key] = value }
        override fun getInt(key: String, defaultValue: Int) = values[key] as? Int ?: defaultValue
        override fun putInt(key: String, value: Int) { values[key] = value }
        override fun getBoolean(key: String, defaultValue: Boolean) = values[key] as? Boolean ?: defaultValue
        override fun putBoolean(key: String, value: Boolean) { values[key] = value }
        override fun remove(key: String) { values.remove(key) }
        override fun clear() { values.clear() }
    }
}
