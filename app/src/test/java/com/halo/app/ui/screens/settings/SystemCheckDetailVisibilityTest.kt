package com.halo.app.ui.screens.settings

import com.halo.app.ui.icons.HaloGlyph
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SystemCheckDetailVisibilityTest {
    @Test fun expandedInventoryPreservesAllSourceSections() {
        val sections = listOf(
            SystemCheckSection("runtime", "Runtime", listOf(
                SystemCheckItem("core", "Core", HaloGlyph.DIAGNOSTICS, CheckStatus.READY, "Observed")
            )),
            SystemCheckSection("voice", "Voice", listOf(
                SystemCheckItem("mic_permission", "Permission", HaloGlyph.DIAGNOSTICS,
                    CheckStatus.PASSED, "Granted")
            )),
        )
        assertEquals(2, sections.sumOf { it.items.size })
        assertEquals(2, systemCheckEssentialItems(sections).size)
        assertTrue(systemCheckExplicitActionLabel(sections[0].items[0]) == null)
    }
}
