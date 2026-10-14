package com.halo.app.ui.theme

import org.junit.Assert.assertEquals
import org.junit.Test

/** Prevents secondary screens from silently reintroducing a third font family. */
class BrandTypographyTest {
    @Test fun headingsUseTheSharedBrandDisplaySlot() {
        listOf(
            Typography.displayLarge, Typography.headlineLarge, Typography.headlineMedium,
            Typography.headlineSmall, Typography.titleLarge, Typography.titleMedium,
            Typography.titleSmall
        ).forEach { assertEquals(HaloBrandFonts.display, it.fontFamily) }
    }

    @Test fun ReadingAndUiLabelsUseTheSharedAccessibleBodySlot() {
        listOf(
            Typography.bodyLarge, Typography.bodyMedium, Typography.bodySmall,
            Typography.labelLarge, Typography.labelMedium, Typography.labelSmall
        ).forEach { assertEquals(HaloBrandFonts.reading, it.fontFamily) }
    }
}
