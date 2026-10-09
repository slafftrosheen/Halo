package com.halo.app.study

import android.net.Uri
import com.halo.app.data.notebook.WorkspaceObjectId
import com.halo.app.data.workspace.*
import com.halo.app.notebook.NotebookUseCases
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*

data class ExtractionReviewState(val parts: List<ExtractionPart> = emptyList(), val busy: Boolean = false,
    val progress: String = "", val error: String? = null, val savedNoteId: String? = null)
data class ExtractionDestination(val kind: String, val noteId: String)

/** Review owner only. Sources stay in Workspace; only explicit Save persists extracted text. */
class ExtractionReviewController(private val extractor: StudyTextExtractor,
    private val resolver: StudySourceResolver, private val files: WorkspaceFileRepository,
    private val notes: NotebookUseCases, private val policy: WorkspaceMediaAiPolicy,
    private val scope: CoroutineScope) {
    private val mutable = MutableStateFlow(ExtractionReviewState())
    val state = mutable.asStateFlow()
    val visible = MutableStateFlow(false)
    private var job: Job? = null
    private val events = Channel<ExtractionDestination>(Channel.BUFFERED)
    val navigation = events.receiveAsFlow()
    fun dismiss() { visible.value = false }
    fun cancel() { job?.cancel(); mutable.update { it.copy(progress="Stopping extraction; completed text is kept") } }
    fun edit(index: Int, text: String) {
        if (state.value.busy) return
        mutable.update { current -> current.copy(parts=current.parts.mapIndexed { i, part ->
            if (i == index) part.copy(text=text.take((StudySourceLimits.DECODED_CHARS-current.parts.filterIndexed { j,_ -> j != index }.sumOf { it.text.length }).coerceAtLeast(0))) else part }, savedNoteId=null) }
    }
    fun move(index: Int, delta: Int) {
        if (state.value.busy) return
        mutable.update { current ->
            val target = index + delta
            if (target !in current.parts.indices) current else {
                val parts=current.parts.toMutableList(); val item=parts.removeAt(index); parts.add(target,item)
                current.copy(parts=parts,savedNoteId=null)
            }
        }
    }
    private fun add(part: ExtractionPart) {
        val room = (StudySourceLimits.DECODED_CHARS - state.value.parts.sumOf { it.text.length }).coerceAtLeast(0)
        mutable.update { it.copy(parts=it.parts + part.copy(text=part.text.take(room), warning=
            if (part.text.length > room) listOfNotNull(part.warning,"Extraction character limit reached").joinToString(" ") else part.warning)) }
    }
    fun import(uris: List<Uri>, firstPage: Int = 1, lastPage: Int = firstPage) = start {
        require(uris.size in 1..StudySourceLimits.MAX_SOURCES) { "Choose up to four sources per extraction" }
        uris.forEachIndexed { index, uri ->
            currentCoroutineContext().ensureActive()
            mutable.update { it.copy(progress="Importing source ${index+1}/${uris.size}") }
            var imported: WorkspaceFile? = null
            try {
                imported=files.importFile(uri)
                require(imported.sizeBytes == null || imported.sizeBytes!! <= 32L*1024*1024) { "Source exceeds 32 MB; choose a smaller document" }
                // Existing file owner keeps a local copy so picker permissions cannot strand the source.
                val local=files.keepLocalCopyBounded(imported.objectId,32L*1024*1024)
                policy.setAccess(local.objectId.value,WorkspaceAiAccess.LOCAL_ONLY)
                extract(local.objectId.value,firstPage,lastPage)
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) { add(ExtractionPart(imported?.objectId?.value.orEmpty(),imported?.version,
                imported?.displayName ?: "Source ${index+1}","Import","Unavailable","",error.message ?: "Import failed")) }
        }
    }
    fun workspace(id: String, firstPage: Int = 1, lastPage: Int = firstPage) = start { extract(id,firstPage,lastPage) }
    private fun start(work: suspend () -> Unit) {
        if (job?.isActive == true || state.value.parts.isNotEmpty() && state.value.savedNoteId == null) {
            visible.value=true
            mutable.update { it.copy(error="Finish or discard the current extraction review before starting another") }
            return
        }
        visible.value=true
        mutable.value=ExtractionReviewState(busy=true,progress="Extracting on this phone")
        job=scope.launch {
            try { work(); mutable.update { it.copy(progress="Extraction finished; review the text") } }
            catch (cancelled: CancellationException) { mutable.update { it.copy(progress="Cancelled; completed text is kept") }; throw cancelled }
            catch (error: Exception) { mutable.update { it.copy(error=error.message ?: "Extraction failed; source is kept") } }
            finally { mutable.update { it.copy(busy=false) } }
        }
    }
    private suspend fun extract(id: String, firstPage: Int, lastPage: Int) {
        check(policy.permitsObject(id,true)) { "This source is excluded from AI extraction; review its Workspace policy" }
        val source=resolver.resolve(id,"")
        when(source) {
            is ResolvedStudySource.Visual -> add(ExtractionPart(id,source.revision,source.title,"Image","Local Latin-script OCR",
                extractor.image(source.asset),"Verify OCR against the original. Latin script only; other scripts are unsupported."))
            is ResolvedStudySource.Text -> {
                val file=files.getFile(WorkspaceObjectId(id))
                if (file != null) extractor.document(file.uri,file.mimeType,id,file.version,file.displayName,firstPage,lastPage,::add)
                else add(ExtractionPart(id,source.revision,source.title,"Selected passages","Workspace text",source.passages.joinToString("\n\n") { it.text },
                    if (source.coverage != SourceCoverage.FULL) "Only selected passages are included" else null))
            }
            else -> {
                val file=files.getFile(WorkspaceObjectId(id)) ?: error("No readable document source")
                check(file.mimeType == "application/pdf" || file.mimeType == StudyTextExtractor.DOCX || file.mimeType.startsWith("text/")) { "Choose an image, PDF, DOCX or UTF-8 text file" }
                extractor.document(file.uri,file.mimeType,id,file.version,file.displayName,firstPage,lastPage) { part ->
                    add(part); mutable.update { it.copy(progress="Processed ${part.locator}") }
                }
            }
        }
    }
    fun isExtracting(): Boolean = job?.isActive == true
    fun navigationFailed(message: String) { visible.value=true; mutable.update { it.copy(error=message) } }
    fun discard() { if (!state.value.busy) { mutable.value=ExtractionReviewState(); visible.value=false } }
    fun use(kind: String) {
        if (state.value.busy || state.value.parts.none { it.text.isNotBlank() }) return
        mutable.update { it.copy(busy=true,error=null) }
        scope.launch {
            try {
                val snapshot=state.value
                val id=snapshot.savedNoteId ?: notes.createNote(title="Reviewed extraction", body=snapshot.parts.joinToString("\n\n") {
                    "${it.label} · ${it.locator}\nSource: ${it.sourceId} · version: ${it.sourceVersion ?: "unknown"}\n${it.method} · user-reviewed draft\n${it.warning.orEmpty()}\n\n${it.text}"
                }).id.also { noteId ->
                    val access=if(snapshot.parts.any { policy.access(it.sourceId)==WorkspaceAiAccess.EXCLUDE_FROM_AI }) WorkspaceAiAccess.EXCLUDE_FROM_AI else WorkspaceAiAccess.LOCAL_ONLY
                    policy.setAccess(noteId,access)
                    mutable.update { it.copy(savedNoteId=noteId) }
                }
                val currentAccess=if(snapshot.parts.any { policy.access(it.sourceId)==WorkspaceAiAccess.EXCLUDE_FROM_AI }) WorkspaceAiAccess.EXCLUDE_FROM_AI else WorkspaceAiAccess.LOCAL_ONLY
                policy.setAccess(id,currentAccess)
                check(kind == "save" || currentAccess != WorkspaceAiAccess.EXCLUDE_FROM_AI) { "A source is now excluded from AI; save the reviewed text or update its source policy" }
                events.send(ExtractionDestination(kind,id)); visible.value=false
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) { mutable.update { it.copy(error=error.message ?: "Save failed; reviewed text is kept") } }
            finally { mutable.update { it.copy(busy=false) } }
        }
    }
}
