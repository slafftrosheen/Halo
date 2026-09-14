package com.halo.providers.llamacpp

import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class GgufMetadataReaderTest {
    @Test
    fun readsArchitectureAndArchitectureSpecificContextLength() {
        val file = writeGguf(
            metadata = listOf(
                "general.architecture" to StringValue("llama"),
                "llama.context_length" to UInt32Value(8192L),
            )
        )

        try {
            val metadata = GgufMetadataReader.read(file)
            assertEquals("llama", metadata?.architecture)
            assertEquals(8192, metadata?.contextLength)
        } finally {
            file.delete()
        }
    }

    @Test
    fun ignoresUnknownMetadataTypesWithoutGuessing() {
        val file = writeGguf(
            metadata = listOf(
                "general.architecture" to StringValue("qwen2"),
                "qwen2.context_length" to UInt32Value(32768L),
            )
        )

        try {
            val metadata = GgufMetadataReader.read(file)
            assertEquals("qwen2", metadata?.architecture)
            assertEquals(32768, metadata?.contextLength)
        } finally {
            file.delete()
        }
    }

    @Test
    fun rejectsInvalidMagicAndTruncatedFiles() {
        val file = Files.createTempFile("halo-invalid-gguf", ".gguf").toFile()
        file.writeBytes(byteArrayOf(0x47, 0x47, 0x55, 0x46))
        try {
            assertNull(GgufMetadataReader.read(file))
        } finally {
            file.delete()
        }
    }

    private fun writeGguf(metadata: List<Pair<String, Value>>): File {
        val file = Files.createTempFile("halo-test-", ".gguf").toFile()
        file.outputStream().buffered().use { out ->
            out.writeLe32(0x46554747L)
            out.writeLe32(3L)
            out.writeLe64(0L)
            out.writeLe64(metadata.size.toLong())
            metadata.forEach { (key, value) ->
                out.writeString(key)
                value.write(out)
            }
        }
        return file
    }

    private sealed interface Value {
        fun write(out: java.io.OutputStream)
    }

    private class StringValue(private val value: String) : Value {
        override fun write(out: java.io.OutputStream) {
            out.writeLe32(8L)
            out.writeString(value)
        }
    }

    private class UInt32Value(private val value: Long) : Value {
        override fun write(out: java.io.OutputStream) {
            out.writeLe32(4L)
            out.writeLe32(value)
        }
    }

    private fun java.io.OutputStream.writeString(value: String) {
        val bytes = value.toByteArray(Charsets.UTF_8)
        writeLe64(bytes.size.toLong())
        write(bytes)
    }

    private fun java.io.OutputStream.writeLe32(value: Long) {
        write((value and 0xFF).toInt())
        write(((value ushr 8) and 0xFF).toInt())
        write(((value ushr 16) and 0xFF).toInt())
        write(((value ushr 24) and 0xFF).toInt())
    }

    private fun java.io.OutputStream.writeLe64(value: Long) {
        for (index in 0 until 8) write(((value ushr (index * 8)) and 0xFF).toInt())
    }
}
