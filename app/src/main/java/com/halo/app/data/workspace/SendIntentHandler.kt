package com.halo.app.data.workspace

import android.content.Intent
import android.net.Uri
import com.halo.app.data.notebook.NotebookNote
import com.halo.app.data.notebook.NotebookRepository
import com.halo.app.data.notebook.WorkspaceObjectSource
import com.halo.app.data.notebook.WorkspaceSource

class SendIntentHandler(
    private val notebookRepository: NotebookRepository,
    private val fileRepository: WorkspaceFileRepository
) {
    suspend fun handleIntent(intent: Intent): Int {
        val action = intent.action ?: return 0
        var importedCount = 0

        if (action == Intent.ACTION_SEND) {
            val type = intent.type ?: ""
            if (type.startsWith("text/")) {
                val sharedText = intent.getStringExtra(Intent.EXTRA_TEXT)
                val sharedSubject = intent.getStringExtra(Intent.EXTRA_SUBJECT)
                if (!sharedText.isNullOrBlank()) {
                    val body = if (!sharedSubject.isNullOrBlank()) "# $sharedSubject\n\n$sharedText" else sharedText
                    notebookRepository.createNote(body = body, source = WorkspaceSource.IMPORT)
                    importedCount++
                }
            } else {
                val streamUri = intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)
                if (streamUri != null) {
                    val imported = fileRepository.importFile(
                        uri = streamUri,
                        mimeType = type.ifBlank { null },
                        source = WorkspaceObjectSource.SHARE_INTENT
                    )
                    if (imported.mimeType.startsWith("image/")) {
                        com.halo.app.di.ManualDependencyGraph.visualAttachments.attach(imported.uri, "Shared file",
                            com.halo.sdk.runtime.multimodal.MediaProvenance.SHARED_FILE, imported.objectId.value)
                    }
                    importedCount++
                }
            }
        } else if (action == Intent.ACTION_SEND_MULTIPLE) {
            val streamUris = intent.getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM)
            if (streamUris != null) {
                for (uri in streamUris) {
                    fileRepository.importFile(
                        uri = uri,
                        source = WorkspaceObjectSource.SHARE_INTENT
                    )
                    importedCount++
                }
            }
        }

        return importedCount
    }
}
