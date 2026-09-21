package com.halo.app.storage

import android.content.Context
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.security.MessageDigest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Crash-resumable migration for downloaded assets that must never enter Auto Backup. */
class LocalAssetStorageMigration(
    private val context: Context,
    private val layout: HaloStorageLayout,
) {
    suspend fun migrate() = withContext(Dispatchers.IO) {
        layout.ensureDirectories()
        migrateDirectory(File(context.filesDir, "speech"), layout.speechModels)

        val legacyModelRoots = listOf(
            File(context.filesDir, "models"),
            File(context.filesDir, "Download"),
            context.getExternalFilesDir(android.os.Environment.DIRECTORY_DOWNLOADS),
            context.getExternalFilesDir(null)?.let { File(it, "Download") },
            context.getExternalFilesDir(null),
        ).filterNotNull()
        legacyModelRoots.filter(File::exists).forEach { root ->
            root.walkTopDown()
                .filter { it.isFile && it.extension.equals("gguf", ignoreCase = true) }
                .forEach { source ->
                    migrateFile(source, File(layout.models, source.name))
                }
            deleteEmptyDirectories(root)
        }
    }

    private fun migrateDirectory(from: File, to: File) {
        if (!from.exists() || from.canonicalFile == to.canonicalFile) return
        from.walkTopDown().filter(File::isFile).forEach { source ->
            val relative = source.relativeTo(from)
            migrateFile(source, File(to, relative.path))
        }
        deleteEmptyDirectories(from)
    }

    private fun migrateFile(source: File, destination: File) {
        destination.parentFile?.mkdirs()
        if (destination.exists() && filesMatch(source, destination)) {
            source.delete()
            return
        }
        if (destination.exists()) return // Different asset with the same name; preserve both originals.

        val partial = File(destination.parentFile, ".${destination.name}.migrating")
        if (partial.exists() && !partial.delete()) return
        FileInputStream(source).use { input ->
            FileOutputStream(partial).use { output ->
                input.copyTo(output)
                output.fd.sync()
            }
        }
        if (!filesMatch(source, partial)) {
            partial.delete()
            return
        }
        if (!partial.renameTo(destination)) {
            FileInputStream(partial).use { input ->
                FileOutputStream(destination).use { output ->
                    input.copyTo(output)
                    output.fd.sync()
                }
            }
            if (!filesMatch(source, destination)) {
                destination.delete()
                return
            }
            partial.delete()
        }
        if (filesMatch(source, destination)) source.delete()
    }

    private fun filesMatch(first: File, second: File): Boolean =
        first.length() == second.length() && sha256(first).contentEquals(sha256(second))

    private fun sha256(file: File): ByteArray {
        val digest = MessageDigest.getInstance("SHA-256")
        FileInputStream(file).use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest()
    }

    private fun deleteEmptyDirectories(root: File) {
        if (!root.exists()) return
        root.walkBottomUp().filter(File::isDirectory).forEach { directory ->
            if (directory.listFiles().isNullOrEmpty()) directory.delete()
        }
    }
}
