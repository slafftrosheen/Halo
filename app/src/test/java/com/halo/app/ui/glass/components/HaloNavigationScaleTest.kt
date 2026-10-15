package com.halo.app.ui.glass.components

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HaloNavigationScaleTest {
    @Test fun defaultScaleKeepsLabels() {
        assertTrue(navigationLabelsVisible(1.0f))
        assertTrue(navigationLabelsVisible(1.2f))
    }

    @Test fun largeScaleAvoidsEllipsizedLabels() {
        assertFalse(navigationLabelsVisible(1.3f))
        assertFalse(navigationLabelsVisible(1.5f))
        assertFalse(navigationLabelsVisible(2.0f))
    }
}
