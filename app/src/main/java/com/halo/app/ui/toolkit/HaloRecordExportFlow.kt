package com.halo.app.ui.toolkit

import android.app.KeyguardManager
import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import com.halo.app.R
import com.halo.app.di.ManualDependencyGraph as Graph
import com.halo.app.privacy.HaloPauseOwner
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal data class HaloRecordAuthority(val familyRevision: Long, val pauseRevision: String, val modeRevision: Long) {
    companion object {
        fun current(pauseOwner: HaloPauseOwner): HaloRecordAuthority = HaloRecordAuthority(
            Graph.kidsPreferences.adultActionRevision(), pauseOwner.state.value.version, Graph.privacyModes.revision)
    }

    fun verify(context: Context, pauseOwner: HaloPauseOwner) {
        Graph.kidsPreferences.requireAdultAction(familyRevision)
        check(!pauseOwner.isPaused() && context.getSystemService(KeyguardManager::class.java)?.isDeviceLocked == false)
        check(pauseOwner.state.value.version == pauseRevision && Graph.privacyModes.revision == modeRevision)
    }
}

private data class HaloRecordExportTicket(val review: HaloRecordExportReview, val authority: HaloRecordAuthority)

@Composable
internal fun HaloRecordExportFlow(
    show: Boolean,
    audio: HaloRecordAudio?,
    selection: HaloRecordWindow?,
    captions: List<HaloRecordCaption>,
    speakerPrefix: String,
    allowReview: Boolean,
    pauseOwner: HaloPauseOwner,
    onClose: () -> Unit,
    onBusy: (String?) -> Unit,
    onNotice: (Int?) -> Unit,
) {
    val context: Context = LocalContext.current
    val scope = rememberCoroutineScope()
    val latestAudio: HaloRecordAudio? by rememberUpdatedState(audio)
    val latestSelection: HaloRecordWindow? by rememberUpdatedState(selection)
    var review: HaloRecordExportReview? by remember { mutableStateOf(null) }
    var pending: HaloRecordExportTicket? by remember { mutableStateOf(null) }
    LaunchedEffect(audio, selection, allowReview) {
        if (!allowReview || review?.audio != audio || review?.window != selection) review = null
    }
    fun complete(destination: Uri?) {
        val ticket: HaloRecordExportTicket = pending ?: return
        pending = null
        if (destination == null) { onBusy(null); return }
        scope.launch {
            try {
                check(ticket.review.audio == latestAudio && ticket.review.window == latestSelection)
                saveRecordExport(context, destination, ticket, pauseOwner)
                onNotice(R.string.halo_record_export_saved)
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { onNotice(R.string.halo_record_export_failed) }
            finally { onBusy(null) }
        }
    }
    val wavePicker = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(HaloRecordExportFormat.AUDIO.mimeType), ::complete)
    val textPicker = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(HaloRecordExportFormat.TRANSCRIPT.mimeType), ::complete)
    val subtitlePicker = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(HaloRecordExportFormat.SUBTITLES.mimeType), ::complete)
    if (show && audio != null && selection != null) HaloRecordExportMenu(captions.isNotEmpty(), onChoose = { format ->
        onClose()
        if (allowReview) {
            val payload: String? = if (format == HaloRecordExportFormat.AUDIO) null
                else haloRecordTextExport(captions, selection, format, speakerPrefix)
            when {
                payload?.isBlank() == true -> onNotice(R.string.halo_record_export_no_words)
                payload != null && payload.length > 524_288 -> onNotice(R.string.halo_record_export_too_long)
                else -> review = HaloRecordExportReview(audio, selection, format, payload)
            }
        }
    }, onClose = onClose)
    val currentReview: HaloRecordExportReview? = review
    if (currentReview != null) HaloRecordExportDialog(currentReview, onSave = {
        review = null
        if (allowReview && audio == currentReview.audio && selection == currentReview.window) {
            try {
                pending = HaloRecordExportTicket(currentReview, HaloRecordAuthority.current(pauseOwner))
                onBusy("Saving a copy")
                onNotice(null)
                val name: String = currentReview.format.nameWithExtension
                when (currentReview.format) {
                    HaloRecordExportFormat.AUDIO -> wavePicker.launch(name)
                    HaloRecordExportFormat.TRANSCRIPT -> textPicker.launch(name)
                    HaloRecordExportFormat.SUBTITLES -> subtitlePicker.launch(name)
                }
            } catch (_: Exception) {
                pending = null
                onBusy(null)
                onNotice(R.string.halo_record_export_failed)
            }
        }
    }, onClose = { review = null })
}

private suspend fun saveRecordExport(context: Context, destination: Uri, ticket: HaloRecordExportTicket,
    pauseOwner: HaloPauseOwner) = withContext(Dispatchers.IO) {
    fun allowed(): Unit = ticket.authority.verify(context, pauseOwner)
    val review: HaloRecordExportReview = ticket.review
    allowed()
    check(review.audio.isCurrent())
    requireNotNull(context.contentResolver.openOutputStream(destination, "wt")).use { stream ->
        val text: String? = review.payload
        if (text == null) writeHaloRecordWave(review.audio, review.window, stream, ::allowed)
        else stream.write(text.encodeToByteArray())
    }
}
