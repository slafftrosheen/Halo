package com.halo.app.ui.workspace

import com.halo.app.ai.WorkspaceImageService
import com.halo.app.ai.WorkspaceImageChoice
import com.halo.app.ai.ImageSpendPreview
import com.halo.app.online.OnlineOperationRejected
import com.halo.app.online.OnlineProblem
import com.halo.app.online.onlineProviderProblem
import com.halo.sdk.provider.AiProviderException
import com.halo.app.data.workspace.NoteAttachment
import com.halo.app.data.workspace.NotebookAssetRepository
import com.halo.sdk.provider.AiImageResult
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.math.BigDecimal

data class ImageStudioState(
    val choices: List<WorkspaceImageChoice> = emptyList(), val selected: WorkspaceImageChoice? = null,
    val prompt: String = "", val result: AiImageResult? = null, val resultPrompt: String = "",
    val busy: Boolean = false, val saving: Boolean = false, val loading: Boolean = false,
    val error: String? = null, val referenceLabel: String? = null,
    val spendPreview: ImageSpendPreview? = null, val onlineError: OnlineProblem? = null,
    val preparingSpend: Boolean = false,
)
/** Screen-owned draft: dismissing never publishes an image or destroys an original. */
class WorkspaceImageStudio(
    private val scope: CoroutineScope, private val service: WorkspaceImageService,
    private val assets: NotebookAssetRepository,
    private val save: suspend (AiImageResult, String) -> Unit,
) {
    private val mutable = MutableStateFlow(ImageStudioState())
    val state = mutable.asStateFlow()
    private var job: Job? = null
    private var generation = 0L
    private var preparation = 0L
    private var modelsLoading = false
    private var referenceLoading = false
    private var referenceFailed = false
    private fun loadingChanged() { mutable.value = mutable.value.copy(loading = modelsLoading || referenceLoading) }
    private var reference: ByteArray? = null
    private var referenceMime = "image/png"
    private var referenceObjectId: String? = null
    private data class Pending(val choice: WorkspaceImageChoice, val prompt: String, val input: ByteArray?, val mime: String,
        val preview: ImageSpendPreview, val referenceObjectId: String?)
    private var pending: Pending? = null
    private fun invalidateApproval() {
        service.discardSpend(pending?.preview)
        pending = null
        mutable.value = mutable.value.copy(spendPreview = null)
    }
    fun dismissSpend() { invalidateApproval() }
    fun refresh() {
        if (modelsLoading || mutable.value.busy || mutable.value.saving) return
        invalidateApproval()
        modelsLoading = true; loadingChanged()
        scope.launch {
            try {
                val catalogue = service.catalogue(); val last = service.lastChoice()
                val choices = catalogue.models.filter { if (referenceObjectId != null) it.model.supportsEditing else !it.model.requiresReference }
                val selected = choices.firstOrNull { it.providerId == mutable.value.selected?.providerId && it.model.id == mutable.value.selected?.model?.id }
                    ?: choices.firstOrNull { it.providerId == last.first && it.model.id == last.second } ?: choices.firstOrNull()
                mutable.value = mutable.value.copy(choices = choices, selected = selected,
                    error = catalogue.errors.joinToString("\n").takeIf { it.isNotEmpty() })
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { mutable.value = mutable.value.copy(error = "Image models could not be loaded. Refresh to try again.") }
            finally { modelsLoading = false; loadingChanged() }
        }
    }
    fun select(choice: WorkspaceImageChoice) {
        if (!mutable.value.busy && !mutable.value.saving) { invalidateApproval(); mutable.value = mutable.value.copy(selected = choice) }
    }
    fun prompt(text: String) {
        if (!mutable.value.busy && !mutable.value.saving) { invalidateApproval(); mutable.value = mutable.value.copy(prompt = text.take(4000)) }
    }
    fun prepare(attachment: NoteAttachment? = null) {
        if (mutable.value.busy || mutable.value.saving) return
        invalidateApproval()
        val version = ++preparation
        reference = null; referenceFailed = false
        referenceObjectId = attachment?.noteId
        referenceLoading = attachment != null; loadingChanged()
        referenceMime = attachment?.mimeType ?: "image/png"
        mutable.value = mutable.value.copy(result = null, prompt = if (attachment != null) "" else mutable.value.prompt, referenceLabel = attachment?.displayName, error = null, onlineError = null)
        if (attachment == null) { refresh(); return }
        scope.launch {
            try { val bytes = assets.readImage(attachment); if (version == preparation) reference = bytes }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { if (version == preparation) { referenceFailed = true; mutable.value = mutable.value.copy(error = "The reference image could not be read. Close and select the image again.") } }
            finally { if (version == preparation) { referenceLoading = false; loadingChanged(); refresh() } }
        }
    }
    /** First tap prepares a local quote/consent screen; it never sends a generation request. */
    fun generate(editPreview: Boolean = false) {
        val current = mutable.value
        val choice = current.selected ?: return
        if (current.busy || current.saving || current.loading || current.prompt.isBlank() || referenceFailed) return
        invalidateApproval()
        val input = if (editPreview) current.result?.bytes ?: reference else reference
        val mime = if (editPreview) current.result?.mimeType ?: referenceMime else referenceMime
        val version = ++generation
        mutable.value = mutable.value.copy(busy = true, preparingSpend = true, error = null, onlineError = null)
        job = scope.launch {
            try {
                val sourceId = referenceObjectId
                val preview = service.prepareSpend(choice, current.prompt, input, mime, sourceId)
                if (version == generation) {
                    pending = Pending(choice, current.prompt, input, mime, preview, sourceId)
                    mutable.value = mutable.value.copy(spendPreview = preview)
                } else service.discardSpend(preview)
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (error: OnlineOperationRejected) { if (version == generation) mutable.value = mutable.value.copy(onlineError = error.problem) }
            catch (error: com.halo.app.ai.WorkspaceImageUnavailable) { if (version == generation) mutable.value = mutable.value.copy(error = error.message) }
            catch (_: Exception) { if (version == generation) mutable.value = mutable.value.copy(error = "Couldn't generate an image. Check cloud sharing, provider credentials, and model access, then retry.") }
            finally { if (version == generation) mutable.value = mutable.value.copy(busy = false, preparingSpend = false) }
        }
    }
    fun confirmSpend(reservationUsd: BigDecimal) {
        val request = pending ?: return
        if (mutable.value.busy || mutable.value.saving) return
        pending = null // Double taps cannot launch two jobs with the same consent.
        val version = ++generation
        mutable.value = mutable.value.copy(busy = true, spendPreview = null, error = null, onlineError = null)
        job = scope.launch {
            try {
                val image = service.generate(request.choice, request.prompt, request.input, request.mime,
                    request.preview, reservationUsd, acknowledgeUnknownCost = true, referenceObjectId = request.referenceObjectId)
                if (version == generation) mutable.value = mutable.value.copy(result = image, resultPrompt = request.prompt)
            } catch (cancelled: TimeoutCancellationException) { if (version == generation) mutable.value = mutable.value.copy(error = "Image generation timed out. Your previous preview is kept.") }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (error: OnlineOperationRejected) { if (version == generation) mutable.value = mutable.value.copy(onlineError = error.problem) }
            catch (error: AiProviderException) { if (version == generation) mutable.value = mutable.value.copy(onlineError = onlineProviderProblem(error)) }
            catch (error: com.halo.app.ai.WorkspaceImageUnavailable) { if (version == generation) mutable.value = mutable.value.copy(error = error.message) }
            catch (_: Exception) { if (version == generation) mutable.value = mutable.value.copy(error = "Couldn't generate an image. Check cloud sharing, provider credentials, and model access, then retry.") }
            finally { if (version == generation) mutable.value = mutable.value.copy(busy = false, preparingSpend = false) }
        }
    }
    fun cancel() { generation++; job?.cancel(); invalidateApproval(); mutable.value = mutable.value.copy(busy = false, preparingSpend = false) }
    fun saveResult(onSaved: () -> Unit) {
        val current = mutable.value; val image = current.result ?: return
        if (current.busy || current.saving) return
        invalidateApproval()
        mutable.value = mutable.value.copy(saving = true, error = null)
        scope.launch {
            try { save(image, current.resultPrompt); mutable.value = mutable.value.copy(result = null); onSaved() }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { mutable.value = mutable.value.copy(error = "Couldn't save. The preview is kept so you can retry.") }
            finally { mutable.value = mutable.value.copy(saving = false) }
        }
    }
}
