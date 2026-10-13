package com.halo.app.nativeactions

import org.junit.Assert.*
import org.junit.Test

class NativeExportTest {
    @Test fun `export stops before reading any data after Stop`() {
        val output = java.io.ByteArrayOutputStream()
        try { NativeFileActionActivity.copyBounded("private".byteInputStream(), output) { false }; fail("Stop ignored") }
        catch (_: IllegalStateException) { }
        assertEquals(0, output.size())
    }
    @Test fun `export rejects oversized stream without claiming completion`() {
        val input = object : java.io.InputStream() {
            override fun read(): Int = 0
            override fun read(bytes: ByteArray, offset: Int, length: Int): Int { bytes.fill(0); return length }
        }
        val output = object : java.io.OutputStream() { override fun write(value: Int) = Unit; override fun write(bytes: ByteArray, offset: Int, length: Int) = Unit }
        try { NativeFileActionActivity.copyBounded(input, output) { true }; fail("Export limit ignored") }
        catch (_: IllegalArgumentException) { }
    }
}
