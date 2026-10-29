package com.halo.app.ui.screens.devices

import android.content.Context
import android.content.res.Configuration
import androidx.test.core.app.ApplicationProvider
import com.halo.sdk.runtime.embodiment.DeviceKind
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Locale

@RunWith(RobolectricTestRunner::class)
@Config(application = android.app.Application::class, sdk = [33])
class DeviceSeenCopyTest {
    private fun device(age: Long? = null, text: String = "Last seen 4 d ago", connected: Boolean = false) =
        GenericDeviceUiState("qa", "QA device", DeviceKind.HEYCYAN_GLASSES, connected,
            "Saved", false, "Unknown", firmware = "Unknown", lastSeen = text, lastSeenElapsedMs = age)

    @Test fun relativeAgeChangesOnlyAtMinuteHourAndDayBoundariesAndClampsFutureClock() {
        assertEquals(DeviceAgeCopy("Just now"), deviceAgeCopy(-1))
        assertEquals(DeviceAgeCopy("Just now"), deviceAgeCopy(59_999))
        assertEquals(DeviceAgeCopy("%1\$d min ago", 1), deviceAgeCopy(60_000))
        assertEquals(DeviceAgeCopy("%1\$d hr ago", 1), deviceAgeCopy(3_600_000))
        assertEquals(DeviceAgeCopy("Yesterday"), deviceAgeCopy(86_400_000))
        assertEquals(DeviceAgeCopy("%1\$d days ago", 2), deviceAgeCopy(172_800_000))
    }

    @Test fun realTimestampCopyUsesCurrentResourceLocaleAndKeepsLegacySourceTextLiteral() {
        val base = ApplicationProvider.getApplicationContext<Context>()
        val expected = mapOf("en" to "4 days ago", "ru" to "4 дн. назад", "lv" to "Pirms 4 d.", "pl" to "4 dni temu")
        expected.forEach { (language, text) ->
            val resources = base.createConfigurationContext(Configuration(base.resources.configuration).apply {
                setLocale(Locale.forLanguageTag(language))
            }).resources
            assertEquals(language, text, resolveDeviceSeenValue(resources, device(345_600_000)))
            assertEquals("literal source", resolveDeviceSeenValue(resources, device(text = "literal source")))
            assertEquals(listOf("Seen" to text), deviceListSecondaryFacts(device(345_600_000), text))
            assertTrue(deviceListSecondaryFacts(device(0, "Now", connected = true)).isEmpty())
            assertTrue(deviceListSecondaryFacts(device(text = "Never")).isEmpty())
        }
    }
}
