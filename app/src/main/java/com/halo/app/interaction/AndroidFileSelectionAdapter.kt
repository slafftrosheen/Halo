package com.halo.app.interaction

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import com.halo.runtime.cognition.interaction.FileKind
import com.halo.runtime.cognition.interaction.InteractionFileReference
import java.util.UUID

/**
 * RUNTIME-019 host-native file selection (spec #58): the Android renderer
 * opens the real system picker; Runtime never sees platform Uris.
 *
 * [AndroidFileSelectionRegistry] maps the opaque portable
 * [InteractionFileReference.referenceId] to the host [Uri] through the host's
 * own content-access layer. References are transient unless later imported
 * into the Workspace (spec #94).
 */
object AndroidFileSelectionRegistry {
    private val references = mutableMapOf<String, Uri>()

    fun register(uri: Uri): InteractionFileReference {
        val id = "android-file:" + UUID.randomUUID().toString()
        synchronized(references) { references[id] = uri }
        return InteractionFileReference(
            referenceId = id,
            displayName = null,
            mimeType = null,
        )
    }

    fun resolve(referenceId: String): Uri? = synchronized(references) { references[referenceId] }
}

/** Semantic kinds → MIME filter groups for the system picker. */
internal fun mimeTypesFor(kinds: Set<FileKind>): Array<String> {
    val types = linkedSetOf<String>()
    kinds.forEach { kind ->
        when (kind) {
            FileKind.IMAGE -> types += "image/*"
            FileKind.PDF -> types += "application/pdf"
            FileKind.DOCUMENT -> {
                types += "text/*"
                types += "application/msword"
                types += "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
            }
            FileKind.AUDIO -> types += "audio/*"
            FileKind.VIDEO -> types += "video/*"
            FileKind.ANY -> types += "*/*"
        }
    }
    return types.toTypedArray().ifEmpty { arrayOf("*/*") }
}

/**
 * Compose helper returning a launcher that opens the system picker (SAF) and
 * translates the result into portable file references.
 */
@Composable
fun rememberAndroidFileSelectionLauncher(
    acceptedKinds: Set<FileKind>,
    allowMultiple: Boolean,
    onSelected: (List<InteractionFileReference>) -> Unit,
): () -> Unit {
    val context = LocalContext.current
    val mimeTypes = mimeTypesFor(acceptedKinds)

    val singleLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let { onSelected(listOf(registerResolved(context, it))) }
    }
    val multipleLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments()
    ) { uris: List<Uri> ->
        if (uris.isNotEmpty()) onSelected(uris.map { registerResolved(context, it) })
    }

    return {
        if (allowMultiple) {
            multipleLauncher.launch(mimeTypes)
        } else {
            singleLauncher.launch(mimeTypes)
        }
    }
}

private fun registerResolved(context: Context, uri: Uri): InteractionFileReference {
    runCatching {
        context.contentResolver.takePersistableUriPermission(
            uri,
            android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION,
        )
    }
    return AndroidFileSelectionRegistry.register(uri)
}
