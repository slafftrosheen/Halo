package com.halo.app.data.workspace

import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction

/** Exact editable text lives in canonical Notebook CODE blocks, not another document store. */
enum class WorkspaceTextFormat(val mime: String, val extension: String, val blockLanguage: String) {
    TXT("text/plain", "txt", "halo-file-txt-v1"),
    MARKDOWN("text/markdown", "md", "halo-file-md-v1"),
    CSV("text/csv", "csv", "halo-file-csv-v1");
    companion object {
        fun fromLanguage(value: String?) = entries.firstOrNull { it.blockLanguage == value }
        fun fromFile(name: String, mime: String): WorkspaceTextFormat? {
            val extension = name.substringAfterLast('.', "").lowercase(java.util.Locale.ROOT)
            return when {
                mime == "text/csv" || mime == "application/csv" || extension == "csv" && mime in setOf("text/plain", "application/octet-stream") -> CSV
                mime == "text/markdown" || mime == "text/x-markdown" || extension in setOf("md", "markdown") && mime in setOf("text/plain", "application/octet-stream") -> MARKDOWN
                mime == "text/plain" || extension == "txt" && mime == "application/octet-stream" -> TXT
                else -> null
            }
        }
    }
}

object WorkspaceTextEncoding {
    const val MAX_BYTES = 1_000_000
    const val MAX_CHARS = 200_000
    fun decode(bytes: ByteArray): String {
        require(bytes.size <= MAX_BYTES) { "Text file exceeds the supported size" }
        val buffer = ByteBuffer.wrap(bytes)
        val decoded = Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT).decode(buffer).toString().removePrefix("\uFEFF")
        validate(decoded)
        return decoded
    }
    fun validate(text: String) {
        require(text.length <= MAX_CHARS && '\u0000' !in text) { "Unsupported text content or size" }
        // Unpaired surrogates must not silently become replacement bytes on export.
        Charsets.UTF_8.newEncoder().onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT).encode(java.nio.CharBuffer.wrap(text))
    }
    fun encode(text: String): ByteArray {
        validate(text)
        return text.toByteArray(Charsets.UTF_8).also { require(it.size <= MAX_BYTES) }
    }
}

/** Bounded RFC-style comma-separated text. Values remain literal; no formulas or scripts execute. */
data class WorkspaceCsv(val rows: List<List<String>>) {
    fun requireValid() {
        require(rows.size in 1..MAX_ROWS)
        require(rows.all { it.size in 1..MAX_COLUMNS && it.all { cell -> cell.length <= MAX_CELL_CHARS && '\u0000' !in cell } })
        require(rows.sumOf { it.size } <= MAX_CELLS)
    }
    fun set(row: Int, column: Int, value: String): WorkspaceCsv {
        require(row in rows.indices && column in 0 until MAX_COLUMNS)
        val updated = rows.mapIndexed { index, cells -> if (index != row) cells else cells.toMutableList().apply {
            while (size <= column) add("")
            this[column] = value
        } }
        return WorkspaceCsv(updated).also(WorkspaceCsv::requireValid)
    }
    fun insertRow(index: Int): WorkspaceCsv {
        require(index in 0..rows.size)
        return WorkspaceCsv(rows.toMutableList().apply { add(index, List(rows.maxOf { it.size }) { "" }) }).also(WorkspaceCsv::requireValid)
    }
    fun deleteRow(index: Int): WorkspaceCsv {
        require(index in rows.indices && rows.size > 1)
        return WorkspaceCsv(rows.filterIndexed { row, _ -> row != index }).also(WorkspaceCsv::requireValid)
    }
    fun insertColumn(index: Int): WorkspaceCsv {
        require(index in 0..rows.maxOf { it.size })
        return WorkspaceCsv(rows.map { cells -> cells.toMutableList().apply {
            while (size < index) add("")
            add(index, "")
        } }).also(WorkspaceCsv::requireValid)
    }
    fun deleteColumn(index: Int): WorkspaceCsv {
        require(index in 0 until rows.maxOf { it.size } && rows.maxOf { it.size } > 1)
        return WorkspaceCsv(rows.map { cells -> cells.filterIndexed { column, _ -> column != index }.ifEmpty { listOf("") } })
            .also(WorkspaceCsv::requireValid)
    }
    fun encode(): String {
        requireValid()
        return rows.joinToString("\r\n") { cells -> cells.joinToString(",") { cell ->
            if (cell.any { it == ',' || it == '"' || it == '\r' || it == '\n' }) "\"" + cell.replace("\"", "\"\"") + "\"" else cell
        } }.also(WorkspaceTextEncoding::validate)
    }
    companion object {
        const val MAX_ROWS = 1000
        const val MAX_COLUMNS = 64
        const val MAX_CELLS = 10_000
        const val MAX_CELL_CHARS = 16_000
        fun parse(text: String): WorkspaceCsv {
            WorkspaceTextEncoding.validate(text)
            val rows = mutableListOf<List<String>>()
            var row = mutableListOf<String>()
            val cell = StringBuilder()
            var quoted = false
            var closed = false
            var at = 0
            var cells = 0
            fun endCell() {
                require(cell.length <= MAX_CELL_CHARS && ++cells <= MAX_CELLS)
                row += cell.toString(); cell.setLength(0); closed = false
                require(row.size <= MAX_COLUMNS)
            }
            fun endRow() {
                endCell(); rows += row; row = mutableListOf()
                require(rows.size <= MAX_ROWS)
            }
            while (at < text.length) {
                val char = text[at++]
                if (quoted) {
                    if (char == '"') {
                        if (at < text.length && text[at] == '"') { cell.append('"'); at++ }
                        else { quoted = false; closed = true }
                    } else cell.append(char)
                } else when (char) {
                    '"' -> { require(cell.isEmpty() && !closed); quoted = true }
                    ',' -> endCell()
                    '\r', '\n' -> { if (char == '\r' && at < text.length && text[at] == '\n') at++; endRow() }
                    else -> { require(!closed); cell.append(char) }
                }
                require(cell.length <= MAX_CELL_CHARS)
            }
            require(!quoted) { "Unclosed quoted CSV value" }
            // A terminal record separator is not an extra empty record; trailing commas are cells.
            if (row.isNotEmpty() || cell.isNotEmpty() || closed || rows.isEmpty() || text.endsWith(',')) endRow()
            return WorkspaceCsv(rows).also(WorkspaceCsv::requireValid)
        }
    }
}
