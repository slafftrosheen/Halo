package com.halo.app.data.workspace

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import java.io.File
import java.io.InputStream
import kotlin.math.max

/**
 * Actual workspace bytes, not a synthetic WorkspaceFile. Note assets stay under
 * their owning note and are validated again against the attachment DAO on Save.
 */
sealed interface CanvasStitchSource {
    val ownerObjectId: String
    val title: String
    val sourceKey: String

    data class WorkspaceImage(val file: WorkspaceFile) : CanvasStitchSource {
        override val ownerObjectId: String get() = file.objectId.value
        override val title: String get() = file.displayName
        override val sourceKey: String get() = "file:" + file.objectId.value
    }

    data class NoteImage(val attachment: NoteAttachment) : CanvasStitchSource {
        override val ownerObjectId: String get() = attachment.noteId
        override val title: String get() = attachment.displayName ?: "Notebook image"
        override val sourceKey: String get() = "asset:" + attachment.assetId
    }

    fun open(context: Context): InputStream {
        return when (this) {
            is WorkspaceImage -> {
                require(file.mimeType.startsWith("image/") &&
                    file.availability in setOf(WorkspaceFileAvailability.AVAILABLE,
                        WorkspaceFileAvailability.OWNED_LOCAL)) { "Image source is unavailable." }
                requireNotNull(context.contentResolver.openInputStream(file.uri))
            }
            is NoteImage -> {
                val image = attachment
                require(image.type == "IMAGE" && image.mimeType.startsWith("image/")) {
                    "Notebook attachment is no longer an image."
                }
                // Note assets may be app-owned, managed copies, or durable document URIs.
                when (image.storageType) {
                    "APP_OWNED", "MANAGED_FILE" -> File(image.pathOrUri).inputStream()
                    "EXTERNAL_URI" -> {
                        val uri = Uri.parse(image.pathOrUri)
                        require(uri.scheme == "content" && image.permissionPersisted) {
                            "Notebook image permission expired. Reattach the image."
                        }
                        requireNotNull(context.contentResolver.openInputStream(uri))
                    }
                    else -> error("Unsupported image attachment storage.")
                }
            }
        }
    }

    /** Bounded two-pass bitmap decode shared by imported images and note attachments. */
    fun bounds(context: Context): CanvasImageSize {
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        open(context).use { BitmapFactory.decodeStream(it, null, options) }
        return CanvasImageSize(options.outWidth, options.outHeight)
    }

    fun decode(context: Context, preview: Boolean): Bitmap {
        val size = bounds(context)
        val maxEdge = if (preview) 512 else 4096
        var sample = 1
        while (max(size.width / sample, size.height / sample) > maxEdge ||
            size.width.toLong() * size.height / sample / sample > 12_000_000L) sample *= 2
        return requireNotNull(open(context).use { input ->
            BitmapFactory.decodeStream(input, null, BitmapFactory.Options().apply {
                inSampleSize = sample
                inPreferredConfig = Bitmap.Config.ARGB_8888
            })
        }) { "Image could not be decoded." }
    }
}
