package com.halo.app.ui.mini

import org.junit.Assert.*
import org.junit.Test

class MiniHaloHostVisibilityTest {
    @Test fun closingOneHostKeepsTheOrbHiddenWhileAnotherIsVisible() {
        val full = Any(); val mini = Any()
        try {
            HaloHostVisibility.resumed(full); HaloHostVisibility.resumed(mini)
            HaloHostVisibility.hidden(mini); assertTrue(HaloHostVisibility.active.value)
            HaloHostVisibility.hidden(full); assertFalse(HaloHostVisibility.active.value)
        } finally { HaloHostVisibility.hidden(full); HaloHostVisibility.hidden(mini) }
    }
    @Test fun repeatedResumeAndDestroyDoNotLeakVisibility() {
        val owner = Any()
        try {
            HaloHostVisibility.resumed(owner); HaloHostVisibility.resumed(owner)
            HaloHostVisibility.hidden(owner); HaloHostVisibility.hidden(owner)
            assertFalse(HaloHostVisibility.active.value)
        } finally { HaloHostVisibility.hidden(owner) }
    }
}
