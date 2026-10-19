package com.halo.app.audio

import org.junit.Assert.*
import org.junit.Test

class SherpaGenerationCallbackTest {
    @Test fun `native callback has the exact JNI method descriptor and honors cancellation`() {
        val method = SherpaGenerationCallback::class.java.getDeclaredMethod("invoke", FloatArray::class.java)
        assertEquals(java.lang.Integer::class.java, method.returnType)
        var active = true
        val callback = SherpaGenerationCallback { active }
        assertEquals(1, method.invoke(callback, floatArrayOf(0.1f)))
        active = false
        assertEquals(0, method.invoke(callback, floatArrayOf(0.1f)))
    }
}
