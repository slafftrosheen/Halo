package com.halo.app.ui.screens.home

import android.app.Application
import android.content.Context
import android.content.res.Configuration
import androidx.test.core.app.ApplicationProvider
import com.halo.app.language.resolveUiText
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Locale

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], manifest = Config.NONE, application = Application::class)
class HomeNativeReceiptLocalizationTest {
    @Test fun genericDispatchedHostReceiptKeepsUnconfirmedMeaningInAllFourLanguages() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val expected = mapOf(
            "en" to "Action dispatched; completion is unconfirmed.",
            "ru" to "Действие передано на выполнение; завершение не подтверждено.",
            "lv" to "Darbība nosūtīta izpildei; pabeigšana nav apstiprināta.",
            "pl" to "Działanie przekazano do wykonania; zakończenie nie jest potwierdzone.",
        )
        for ((tag, label) in expected) {
            val resources = context.createConfigurationContext(Configuration(context.resources.configuration).apply {
                setLocale(Locale.forLanguageTag(tag))
            }).resources
            assertEquals(label, resolveUiText(resources, HomeNativeReceiptCopy.DISPATCHED_UNCONFIRMED.english))
        }
    }
}
