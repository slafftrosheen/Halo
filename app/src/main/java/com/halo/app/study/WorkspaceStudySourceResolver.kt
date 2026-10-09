package com.halo.app.study

import android.content.Context
import com.halo.app.data.notebook.*
import com.halo.app.data.workspace.*
import com.halo.sdk.runtime.multimodal.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.first
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction

/** Reads only explicitly selected Workspace objects, on demand. No extracted bodies are persisted. */
class WorkspaceStudySourceResolver(
    private val context: Context,
    private val documents: NotebookDocumentRepository,
    private val workspace: WorkspaceRepository,
    private val files: WorkspaceFileRepository,
    val pdf: StudyPdfPages,
    private val assets: NotebookAssetRepository? = null,
) : StudySourceResolver {
    override suspend fun resolve(objectId: String, query: String): ResolvedStudySource = resolveForTurn(objectId, query, StudySourceLimits.TOTAL_CHARS, false)
    override suspend fun resolveExplicitReference(objectId: String, query: String): ResolvedStudySource =
        resolveSource(objectId,query,StudySourceLimits.TOTAL_CHARS,false,true)
    override suspend fun resolveForTurn(objectId: String, query: String, budget: Int, includeBeginning: Boolean): ResolvedStudySource =
        resolveSource(objectId,query,budget,includeBeginning,false)
    private suspend fun resolveSource(objectId: String, query: String, budget: Int, includeBeginning: Boolean, verifyTextRevision: Boolean): ResolvedStudySource = withContext(Dispatchers.IO) {
        val item = workspace.get(WorkspaceObjectId(objectId))
            ?: return@withContext ResolvedStudySource.Unsupported(objectId, "Material", "Source not found")
        if (item.lifecycle != WorkspaceObjectLifecycle.ACTIVE)
            return@withContext ResolvedStudySource.Unsupported(objectId, item.title, "Source is not active")
        if (item.type == WorkspaceObjectType.NOTE) {
            val images = assets?.observe(objectId)?.first()?.filter { it.type == "IMAGE" }.orEmpty()
            if (images.size == 1) {
                val image = images.single()
                return@withContext ResolvedStudySource.Visual(objectId, image.assetId, item.title,
                    MediaAssetRef("attachment:$objectId:${image.assetId}", MediaKind.IMAGE, image.mimeType, image.bytes, assets?.captureHash(objectId),
                        item.createdAt, MediaProvenance.WORKSPACE, MediaLifetime.WORKSPACE_PERSISTED, workspaceObjectId = objectId), StudySourceLocator.Image())
            }
            if (images.size > 1) return@withContext ResolvedStudySource.Unsupported(objectId, item.title, "Choose a note with one exact image source")
            val loaded = documents.load(objectId)
            val doc = (if (loaded?.blocks?.isEmpty() == true) {
                documents.loadLegacyDocument(objectId)
            } else loaded)
                ?: return@withContext ResolvedStudySource.Unsupported(objectId, item.title, "Note unavailable")
            val all = StudyPassageSelector.notePassages(doc.blocks.map { it.text })
            val selected = StudyPassageSelector.select(all, query, budget, includeBeginning)
            val total = all.sumOf { it.text.length }
            return@withContext ResolvedStudySource.Text(objectId, doc.revision.toString(), doc.title, selected, total,
                if (selected.sumOf { it.text.length } == total) SourceCoverage.FULL else SourceCoverage.PARTIAL)
        }
        if (item.type !in setOf(WorkspaceObjectType.FILE, WorkspaceObjectType.MEDIA, WorkspaceObjectType.ARTIFACT))
            return@withContext ResolvedStudySource.Unsupported(objectId, item.title, "This object is not study material")
        val file = files.getFile(item.id)
            ?: return@withContext ResolvedStudySource.Unsupported(objectId, item.title, "File unavailable")
        if (file.availability !in setOf(WorkspaceFileAvailability.AVAILABLE, WorkspaceFileAvailability.OWNED_LOCAL))
            return@withContext ResolvedStudySource.Unsupported(objectId, item.title, "Needs relink")
        try {
            when {
                file.mimeType == "application/pdf" -> {
                    val count = pdf.pageCount(file)
                    ResolvedStudySource.Pdf(objectId, file.version, item.title, count, 1)
                }
                file.mimeType in setOf("image/png", "image/jpeg", "image/webp") -> ResolvedStudySource.Visual(
                    objectId, file.version, item.title,
                    MediaAssetRef("workspace:$objectId", MediaKind.IMAGE, file.mimeType, file.sizeBytes, file.sha256,
                        item.createdAt, MediaProvenance.WORKSPACE, MediaLifetime.WORKSPACE_PERSISTED, workspaceObjectId = objectId),
                    StudySourceLocator.Image())
                file.mimeType.startsWith("text/") || file.mimeType == "application/json" -> {
                    val bytes = context.contentResolver.openInputStream(file.uri)?.use { input ->
                        val output = java.io.ByteArrayOutputStream()
                        val buffer = ByteArray(8192)
                        while (output.size() <= StudySourceLimits.READ_BYTES) {
                            val count = input.read(buffer, 0, minOf(buffer.size, StudySourceLimits.READ_BYTES + 1 - output.size()))
                            if (count < 0) break
                            output.write(buffer, 0, count)
                        }
                        output.toByteArray() }
                        ?: return@withContext ResolvedStudySource.Unsupported(objectId, item.title, "Needs relink")
                    val cut = bytes.size > StudySourceLimits.READ_BYTES
                    if(verifyTextRevision && !StudyTextRevisionVerifier.matches(bytes,file.sha256,!cut))
                        return@withContext ResolvedStudySource.Unsupported(objectId,item.title,"File changed or its exact revision cannot be verified. Re-import a bounded text file before attaching it.")
                    val decoder = Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT)
                    val buffer = ByteBuffer.wrap(bytes, 0, minOf(bytes.size, StudySourceLimits.READ_BYTES))
                    val chars = java.nio.CharBuffer.allocate(StudySourceLimits.DECODED_CHARS + 1)
                    val status = decoder.decode(buffer, chars, !cut)
                    if (status.isError) status.throwException()
                    chars.flip()
                    val decoded = chars.toString().removePrefix("\ufeff")
                    val truncated = cut || status.isOverflow || decoded.length > StudySourceLimits.DECODED_CHARS
                    val text = decoded.take(StudySourceLimits.DECODED_CHARS)
                    val all = StudyPassageSelector.chunks(text) { start, end -> StudySourceLocator.TextRange(start, end) }
                    val selected = StudyPassageSelector.select(all, query, budget, includeBeginning)
                    ResolvedStudySource.Text(objectId, file.version, item.title, selected, text.length,
                        if (truncated || selected.sumOf { it.text.length } < text.length) SourceCoverage.PARTIAL else SourceCoverage.FULL)
                }
                else -> ResolvedStudySource.Unsupported(objectId, item.title, "Unsupported file type")
            }
        } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
        catch (_: Exception) { ResolvedStudySource.Unsupported(objectId, item.title, "Source could not be read; check access and format") }
    }
}
