package com.halo.app.ui.mini

import org.junit.Assert.assertEquals
import org.junit.Test

class BubblePositionTest {
    @Test fun rotationAndOversizedWindowsStayReachable() {
        assertEquals(0 to 0, BubblePosition.snap(-100,-100,320,640,56))
        assertEquals(584 to 264, BubblePosition.snap(1000,1000,640,320,56))
        assertEquals(0 to 0, BubblePosition.snap(100,100,32,32,56))
    }
}
