package com.halo.app.data.workspace

import android.content.Intent
import android.net.Uri
import com.halo.app.data.notebook.NotebookRepository
import com.halo.app.data.notebook.WorkspaceObjectSource
import com.halo.app.data.notebook.WorkspaceSource
import kotlinx.coroutines.CancellationException

data class SharedInboxReceipt(val objectIds: List<String>, val failed: Int)

class SendIntentHandler(private val notebookRepository: NotebookRepository,
    private val importFile: suspend (Uri) -> WorkspaceFile) {
    constructor(notes: NotebookRepository, workspace: com.halo.app.notebook.WorkspaceUseCases) :
        this(notes, { uri -> workspace.importForCanvas(uri, source = WorkspaceObjectSource.SHARE_INTENT) })
    /** Save each object durably before optional board linking. No AI attachment. */
    suspend fun importToInbox(intent: Intent): SharedInboxReceipt {
        if (intent.action !in setOf(Intent.ACTION_SEND, Intent.ACTION_SEND_MULTIPLE)) return SharedInboxReceipt(emptyList(), 0)
        val ids = mutableListOf<String>()
        var failures = 0
        val text = intent.getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString()?.take(12_000)
        if (!text.isNullOrBlank()) {
            try { ids += notebookRepository.createNote(text, WorkspaceSource.IMPORT,
                intent.getStringExtra(Intent.EXTRA_SUBJECT)?.take(160)).id }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { failures++ }
        }
        val streams = sharedUris(intent)
        failures += (streams.size - 16).coerceAtLeast(0)
        for (uri in streams.take(16)) {
            try { ids += importFile(uri).objectId.value }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { failures++ }
        }
        return SharedInboxReceipt(ids, failures)
    }
    suspend fun handleIntent(intent: Intent) = importToInbox(intent).objectIds.size

    companion object {
        @Suppress("DEPRECATION")
        fun sharedUris(intent: Intent): List<Uri> = buildList {
            if (intent.action == Intent.ACTION_SEND) intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)?.let(::add)
            if (intent.action == Intent.ACTION_SEND_MULTIPLE) intent.getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM)?.let(::addAll)
            intent.clipData?.let { clip -> repeat(clip.itemCount.coerceAtMost(64)) { clip.getItemAt(it).uri?.let(::add) } }
        }.distinct().filter { it.scheme == "content" || it.scheme == "file" }
    }
}
