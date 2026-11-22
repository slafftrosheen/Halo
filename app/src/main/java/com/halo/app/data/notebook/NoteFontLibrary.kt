package com.halo.app.data.notebook

import android.content.Context
import android.graphics.Typeface
import android.net.Uri
import android.provider.OpenableColumns
import com.halo.app.R
import java.io.File
import java.security.MessageDigest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal class NoteFontLibrary(private val context: Context) {
    private val directory = File(context.filesDir, "note-fonts")
    private val names = context.getSharedPreferences("note-font-names.v1", Context.MODE_PRIVATE)

    fun choices(): List<Pair<String, String>> = NOTE_FONT_NAMES.map { it to it } +
        names.all.mapNotNull { (key, name) ->
            if (noteCustomFont.matches(key) && name is String && File(directory, key).isFile) key to name else null
        }.sortedBy { it.second.lowercase() }

    fun typeface(name: String): Typeface = when (name) {
        "Serif" -> Typeface.SERIF
        "Sans serif" -> Typeface.SANS_SERIF
        "Monospace" -> Typeface.MONOSPACE
        "Sora" -> context.resources.getFont(R.font.sora_variable)
        "Onest" -> context.resources.getFont(R.font.onest_cyrillic_variable)
        "Atkinson Hyperlegible" -> context.resources.getFont(R.font.atkinson_hyperlegible_next_variable)
        else -> if (noteCustomFont.matches(name)) {
            runCatching { Typeface.createFromFile(File(directory, name)) }.getOrDefault(Typeface.DEFAULT)
        } else Typeface.DEFAULT
    }

    suspend fun import(uri: Uri): String = withContext(Dispatchers.IO) {
        require(uri.scheme == "content") { "Choose a font from the system file picker." }
        val sourceName = context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null }.orEmpty()
        require(directory.isDirectory || directory.mkdirs())
        val temporary = File.createTempFile("incoming-", ".font", directory)
        try {
            val digest = MessageDigest.getInstance("SHA-256")
            var size = 0
            requireNotNull(context.contentResolver.openInputStream(uri)).use { input ->
                temporary.outputStream().use { output ->
                    val chunk = ByteArray(DEFAULT_BUFFER_SIZE)
                    while (true) {
                        val count = input.read(chunk)
                        if (count < 0) break
                        size += count
                        require(size <= 8 * 1024 * 1024)
                        digest.update(chunk, 0, count)
                        output.write(chunk, 0, count)
                    }
                }
            }
            require(size >= 12)
            val signature = temporary.inputStream().use { input ->
                val data = ByteArray(4)
                check(input.read(data) == data.size)
                data.joinToString("") { "%02x".format(it) }
            }
            require(signature in setOf("00010000", "4f54544f", "74746366", "74727565"))
            Typeface.createFromFile(temporary)
            val id = digest.digest().joinToString("") { "%02x".format(it) }
            val output = File(directory, id)
            check(output.isFile || temporary.renameTo(output))
            val name = sourceName.substringBeforeLast('.').trim().take(64).ifBlank { id.take(12) }
            check(names.edit().putString(id, name).commit())
            id
        } finally {
            temporary.delete()
        }
    }
}
