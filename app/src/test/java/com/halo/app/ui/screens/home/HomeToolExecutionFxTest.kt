package com.halo.app.ui.screens.home

import com.halo.app.ui.fx.HaloFxTool
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HomeToolExecutionFxTest {
    @Test fun supportedToolIdsMapToSuppliedMotionAssets() {
        assertEquals(HaloFxTool.TIMER, homeFxTool("halo_timer"))
        assertEquals(HaloFxTool.SEARCH, homeFxTool("halo_search"))
        assertEquals(HaloFxTool.OPEN_APP, homeFxTool("halo_open_app"))
        assertEquals(HaloFxTool.CALENDAR, homeFxTool("halo_calendar_create"))
        assertEquals(HaloFxTool.SEND, homeFxTool("halo_compose_sms"))
    }

    @Test fun noMotionIsInventedForUnknownOrUnverifiedToolIds() {
        assertNull(homeFxTool("halo_device_info"))
        assertNull(homeFxTool("halo_capture_photo"))
        assertNull(homeFxTool(""))
    }

    @Test fun edgePulseOnlyForActualPhoneAction() {
        org.junit.Assert.assertTrue(homeIsPhoneAction("halo_ui_click"))
        org.junit.Assert.assertTrue(homeIsPhoneAction("halo_open_app"))
        org.junit.Assert.assertTrue(homeIsPhoneAction("halo_perform_app_action"))
        org.junit.Assert.assertFalse(homeIsPhoneAction("halo_ui_wait"))
        org.junit.Assert.assertFalse(homeIsPhoneAction("halo_search"))
        org.junit.Assert.assertFalse(homeIsPhoneAction("halo_timer"))
        org.junit.Assert.assertFalse(homeIsPhoneAction(""))
    }

    @Test fun labelsDoNotExposeRawInternalToolIdentifiers() {
        assertEquals("Timer", homeFxToolLabel(HaloFxTool.TIMER))
        assertEquals("Open app", homeFxToolLabel(HaloFxTool.OPEN_APP))
    }
}
