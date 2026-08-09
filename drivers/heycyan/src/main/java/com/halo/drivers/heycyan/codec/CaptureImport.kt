package com.halo.drivers.heycyan.codec

import java.io.File

data class ImportedCapture(
    val source: File,
    val destination: File,
    val capture: BleCapture
)

/**
 * Imports one or more externally recorded captures into the managed store.
 *
 * The source JSON is validated through [JsonCaptureSessionStore.load] and then
 * copied byte-for-byte. Import never reserializes a capture, so unknown JSON
 * fields and the original raw evidence are not rewritten during analysis setup.
 */
class CaptureImporter(
    private val store: JsonCaptureSessionStore
) {
    fun importFiles(files: Iterable<File>): List<ImportedCapture> = files.map { source ->
        require(source.isFile) { "Capture file does not exist: ${source.absolutePath}" }
        val capture = store.load(source)
        val destination = store.importRaw(source, capture)
        ImportedCapture(source = source, destination = destination, capture = capture)
    }
}