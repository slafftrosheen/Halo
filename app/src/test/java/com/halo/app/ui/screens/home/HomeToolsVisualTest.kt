package com.halo.app.ui.screens.home

import android.app.Application
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.github.takahirom.roborazzi.captureRoboImage
import com.halo.app.ui.theme.HostlessHaloTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Native host rendering only; provider and physical selection qualification remain separate. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [33], application = Application::class, qualifiers = "w360dp-h800dp")
class HomeToolsVisualTest {
    @get:Rule val compose = createComposeRule()
    @Test fun englishTools360() = catalog(false, 1f)
    @Test @Config(qualifiers = "ru-rRU-w360dp-h800dp") fun russianToolsLargeText() = catalog(false, 2f)
    @Test @Config(qualifiers = "lv-rLV-w360dp-h800dp") fun latvianToolsLargeText() = catalog(true, 2f)
    private fun catalog(dark: Boolean, scale: Float) {
        val snapshot = HomeToolCatalog.project(HomeToolCatalogInput())
        compose.setContent { HostlessHaloTheme(darkTheme = dark, fontScale = scale) {
            HomeToolsSheet(snapshot, false, {}, { null }, {}, {}, {})
        } }
        compose.waitForIdle()
        compose.onAllNodes(isRoot()).onLast().captureRoboImage()
    }
    @Test @Config(qualifiers = "ru-rRU-w360dp-h800dp") fun russianPdfReceiptLargeText() {
        compose.setContent { HostlessHaloTheme(fontScale = 2f) {
            HomeImportedSourceDialog(HomeImportedSource("synthetic", "Reading.pdf", HomeImportedSourceKind.PDF), {}, {}, {}, {})
        } }
        compose.waitForIdle()
        compose.onAllNodes(isRoot()).onLast().captureRoboImage()
    }
}
