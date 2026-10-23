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

    private val mutationLocks = java.util.concurrent.ConcurrentHashMap<String, Any>()
    internal fun <T> withFileMutation(directory: File, block: () -> T): T = synchronized(
        mutationLocks.computeIfAbsent(directory.canonicalPath) { Any() }, block)
    private fun requireOwner(owner: String) { require(java.util.UUID.fromString(owner).toString() == owner) }
    private fun receipt(directory: File, file: SherpaSpeechModelCatalog.FileSpec) = File(directory, file.name + ".acquired")
    private fun identity(file: SherpaSpeechModelCatalog.FileSpec, owner: String): String {
        requireOwner(owner)
        require(!file.name.contains('/') && !file.name.contains('\\') && file.sha256?.matches(Regex("[a-f0-9]{64}")) == true && file.expectedBytes > 0)
        return "$owner:${file.sha256}:${file.expectedBytes}"
    }
    internal fun retainFile(directory: File, file: SherpaSpeechModelCatalog.FileSpec) = withFileMutation(directory) {
        val proof = receipt(directory, file)
        check(!proof.exists() || proof.delete()) { "Unable to retain independent model" }
    }
    internal fun hasOwnedFile(directory: File, file: SherpaSpeechModelCatalog.FileSpec, owner: String): Boolean =
        runCatching { receipt(directory, file).readText() == identity(file, owner) }.getOrDefault(false)
    internal fun removeOwnedFiles(directory: File, spec: SherpaSpeechModelCatalog.ModelSpec, owner: String): Boolean = withFileMutation(directory) {
        requireOwner(owner)
        var removed = false
        for (file in spec.files) {
            val target = File(directory, file.name)
            if (hasOwnedFile(directory, file, owner)) {
                if (target.exists()) {
                    check(target.canonicalFile.parentFile == directory.canonicalFile && target.length() == file.expectedBytes) { "Model identity changed; retained" }
                    val digest = java.security.MessageDigest.getInstance("SHA-256")
                    target.inputStream().use { input -> val bytes = ByteArray(65536); while (true) { val n = input.read(bytes); if (n < 0) break; digest.update(bytes, 0, n) } }
                    check(digest.digest().joinToString("") { "%02x".format(it) } == file.sha256) { "Model bytes changed; retained" }
                    check(target.delete()) { "Unable to remove acquired model" }; removed = true
                }
                File(directory, file.name + ".verified").delete()
                check(receipt(directory, file).delete()) { "Unable to remove acquisition receipt" }
            }
            val partial = File(directory, "${file.name}.${file.sha256 ?: spec.revision}.download")
            val proof = File(directory, partial.name + ".acquired")
            if (runCatching { proof.readText() == identity(file, owner) }.getOrDefault(false)) {
                check(partial.canonicalFile.parentFile == directory.canonicalFile && (!partial.exists() || partial.delete())) { "Unable to remove acquired partial" }
                check(proof.delete()) { "Unable to remove partial receipt" }; removed = true
            }
        }
        removed
    }

    fun installFile(input: InputStream, destinationDirectory: File, fileSpec: SherpaSpeechModelCatalog.FileSpec,
        acquisitionOwner: String? = null, requestedOwner: String? = acquisitionOwner): Result = withFileMutation(destinationDirectory) {
        installFileLocked(input, destinationDirectory, fileSpec, acquisitionOwner, requestedOwner)
    }

    private fun installFileLocked(
        input: InputStream,
        destinationDirectory: File,
        fileSpec: SherpaSpeechModelCatalog.FileSpec,
        acquisitionOwner: String?,
        requestedOwner: String?,
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
            acquisitionOwner?.let { identity(fileSpec, it) }
            requestedOwner?.let(::requireOwner)
            check(requestedOwner == null || !target.exists() || hasOwnedFile(destinationDirectory, fileSpec, requestedOwner)) { "An independent model file is retained" }
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
                val ownership = receipt(destinationDirectory, fileSpec)
                if (acquisitionOwner == null) check(!ownership.exists() || ownership.delete()) { "Unable to retain imported model" }
                else {
                    val staged = File(destinationDirectory, ownership.name + ".part")
                    staged.writeText(identity(fileSpec, acquisitionOwner))
                    java.nio.file.Files.move(staged.toPath(), ownership.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING)
                }
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
        } finally { runCatching { input.close() } }
    }

    fun isModelReady(directory: File, spec: SherpaSpeechModelCatalog.ModelSpec): Boolean =
        SherpaSpeechModelCatalog.isInstalled(directory, spec)
}
