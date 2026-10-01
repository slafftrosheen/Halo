package com.halo.providers.sherpa.stt

import java.io.File
import java.io.InputStream

/**
 * Installs one already-downloaded model artifact into Halo's private model area.
 *
 * This class deliberately knows nothing about Android Uri/SAF or networking.
 * The caller supplies an InputStream from an explicit user action. Installation
 * is atomic and the resulting file is validated before the temporary file is
 * promoted.
 */
object SherpaModelInstaller {
    sealed interface Result {
        data class Installed(val file: File) : Result
        data class Rejected(val reason: String) : Result
    }

    fun installFile(
        input: InputStream,
        destinationDirectory: File,
        fileSpec: SherpaSpeechModelCatalog.FileSpec
    ): Result {
        if (fileSpec.name.contains('/') || fileSpec.name.contains('\\')) {
            return Result.Rejected("Invalid model filename")
        }
        if (!destinationDirectory.exists() && !destinationDirectory.mkdirs()) {
            return Result.Rejected("Unable to create model directory")
        }
        if (!destinationDirectory.isDirectory) {
            return Result.Rejected("Model destination is not a directory")
        }

        val target = File(destinationDirectory, fileSpec.name)
        val temporary = File(destinationDirectory, "${fileSpec.name}.partial")

        return try {
            temporary.delete()
            input.use { source ->
                temporary.outputStream().use { output ->
                    source.copyTo(output)
                    output.fd.sync()
                }
            }

            val digest = fileSpec.sha256?.let {
                java.security.MessageDigest.getInstance("SHA-256").apply {
                    temporary.inputStream().use { stream ->
                        val bytes = ByteArray(64 * 1024)
                        while (true) { val count = stream.read(bytes); if (count < 0) break; update(bytes, 0, count) }
                    }
                }.digest().joinToString("") { "%02x".format(it) }
            }
            if (!temporary.isFile || temporary.length() < fileSpec.minimumBytes ||
                (fileSpec.expectedBytes > 0 && temporary.length() != fileSpec.expectedBytes) ||
                (fileSpec.sha256 != null && digest != fileSpec.sha256)) {
                temporary.delete()
                Result.Rejected(
                    "Model file is incomplete: ${fileSpec.name} (${temporary.length()} bytes)"
                )
            } else {
                java.nio.file.Files.move(temporary.toPath(), target.toPath(),
                    java.nio.file.StandardCopyOption.ATOMIC_MOVE,
                    java.nio.file.StandardCopyOption.REPLACE_EXISTING)
                fileSpec.sha256?.let { hash ->
                    File(destinationDirectory, "${fileSpec.name}.verified").writeText("$hash:${target.length()}:${target.lastModified()}")
                }
                Result.Installed(target)
            }
        } catch (exception: kotlinx.coroutines.CancellationException) {
            temporary.delete(); throw exception
        } catch (exception: Exception) {
            temporary.delete()
            Result.Rejected(exception.message ?: "Model installation failed")
        }
    }

    fun isModelReady(directory: File, spec: SherpaSpeechModelCatalog.ModelSpec): Boolean =
        SherpaSpeechModelCatalog.isInstalled(directory, spec)
}
