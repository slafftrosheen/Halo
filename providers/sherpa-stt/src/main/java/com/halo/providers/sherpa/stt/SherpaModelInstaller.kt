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
        val temporary = File(destinationDirectory, ".${fileSpec.name}.partial")

        return try {
            temporary.delete()
            input.use { source ->
                temporary.outputStream().use { output ->
                    source.copyTo(output)
                    output.fd.sync()
                }
            }

            if (!temporary.isFile || temporary.length() < fileSpec.minimumBytes) {
                temporary.delete()
                Result.Rejected(
                    "Model file is incomplete: ${fileSpec.name} (${temporary.length()} bytes)"
                )
            } else {
                if (target.exists() && !target.delete()) {
                    temporary.delete()
                    return Result.Rejected("Unable to replace existing model file")
                }
                if (!temporary.renameTo(target)) {
                    temporary.delete()
                    return Result.Rejected("Unable to finalize model file")
                }
                Result.Installed(target)
            }
        } catch (exception: Exception) {
            temporary.delete()
            Result.Rejected(exception.message ?: "Model installation failed")
        }
    }

    fun isModelReady(directory: File, spec: SherpaSpeechModelCatalog.ModelSpec): Boolean =
        SherpaSpeechModelCatalog.isInstalled(directory, spec)
}
