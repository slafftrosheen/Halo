package com.halo.app.ai
import org.junit.Test
import org.junit.Assert.assertEquals
class LocalModelDeviceFitTest {
    private val gb=1024L*1024*1024
    @Test fun `storage takes precedence over memory and declared ram is honored`() {
        assertEquals(LocalModelDeviceFit.Verdict.STORAGE_SHORTAGE,LocalModelDeviceFit.assess(gb,gb,8*gb,8*gb,null))
        assertEquals(LocalModelDeviceFit.Verdict.MEMORY_PRESSURE,LocalModelDeviceFit.assess(gb,8*gb,gb,8*gb,null))
        assertEquals(LocalModelDeviceFit.Verdict.MEMORY_PRESSURE,LocalModelDeviceFit.assess(gb,8*gb,8*gb,4*gb,6))
        assertEquals(LocalModelDeviceFit.Verdict.ESTIMATED_FIT,LocalModelDeviceFit.assess(gb,8*gb,8*gb,8*gb,6))
    }
}
