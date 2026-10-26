package com.halo.app.ui.screens.settings

import com.halo.app.ui.theme.*
import org.junit.Assert.*
import org.junit.Test

class AppearanceDraftTest {
    @Test fun modeSwitchesKeepBothPaletteDraftsUntilCancel() {
        val saved = HaloPalettes()
        var draft = AppearanceDraft(AppearanceMode.SYSTEM, saved)
        draft = draft.editPalette(false, HaloPalette(HaloAccent.BLUE)).copy(mode = AppearanceMode.DARK)
        draft = draft.editPalette(true, HaloPalette(HaloAccent.ROSE, HaloTone.SLATE)).copy(mode = AppearanceMode.LIGHT)
        assertEquals(HaloAccent.BLUE, draft.palette(false).accent)
        assertEquals(HaloAccent.ROSE, draft.palette(true).accent)
        assertTrue(draft.changed)
        // Cancel reconstructs from current owners, without writing either owner.
        draft = AppearanceDraft(AppearanceMode.SYSTEM, saved)
        assertEquals(AppearanceMode.SYSTEM, draft.mode)
        assertEquals(saved, draft.palettes)
        assertFalse(draft.changed)
    }

    @Test fun liveOwnerAndSystemChangesDoNotEraseEditedChannel() {
        val draft = AppearanceDraft(AppearanceMode.SYSTEM, HaloPalettes())
            .editPalette(false, HaloPalette(HaloAccent.BLUE)).copy(mode = AppearanceMode.DARK)
        val external = HaloPalettes(light = HaloPalette(HaloAccent.CORAL), dark = HaloPalette(HaloAccent.LILAC))
        val refreshed = draft.refresh(AppearanceMode.LIGHT, external)
        assertEquals(AppearanceMode.DARK, refreshed.mode)
        assertEquals(HaloAccent.BLUE, refreshed.palettes.light.accent)
        assertEquals(HaloAccent.LILAC, refreshed.palettes.dark.accent)
        assertTrue(refreshed.changed)
        val applied = AppearanceDraft(refreshed.mode, refreshed.palettes)
        assertFalse(applied.changed)
        assertEquals(refreshed.palettes, applied.palettes)
    }
}
