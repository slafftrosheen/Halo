package com.halo.app.ui.workspace

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import com.halo.app.capture.LocalCaptureStore
import com.halo.app.data.studio.StudioProject
import com.halo.app.data.workspace.NotebookAssetRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.io.File

internal data class StudioCameraActions(val take: (StudioProject) -> Unit, val retry: (StudioProject) -> Unit,
    val recoveryAvailable: Boolean, val busy: Boolean)

/** Uses the existing capture/Inbox owner; pending intent identity survives Activity recreation. */
@Composable
internal fun rememberStudioCamera(viewModel: StudioViewModel, captures: LocalCaptureStore,
    assets: NotebookAssetRepository): StudioCameraActions {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var path by rememberSaveable { mutableStateOf<String?>(null) }
    var projectId by rememberSaveable { mutableStateOf<String?>(null) }
    var revision by rememberSaveable { mutableLongStateOf(0) }
    var recovery by rememberSaveable { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    fun receive() {
        val savedPath = path ?: return
        val target = projectId ?: return
        val expected = revision
        if (busy) return
        busy = true
        scope.launch {
            try {
                val noteId = captures.receive(File(savedPath), sourceKind = "PHONE_CAMERA", publishForInference = false)
                val image = requireNotNull(assets.observe(noteId).first().firstOrNull { it.type == "IMAGE" })
                viewModel.service.importAttachment(target, expected, noteId, image.id)
                // The capture owner retains the original in Inbox; only pending intent metadata is cleared.
                path = null; recovery = false
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { recovery = true; viewModel.reportError() }
            finally { busy = false }
        }
    }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        busy = false
        if (success) receive()
        else {
            val canceled = path
            path = null; recovery = false
            canceled?.let { scope.launch { captures.discardPendingCapture(File(it)) } }
        }
    }
    fun launch() {
        busy = true
        scope.launch {
            try {
                val pending = captures.preparePhoneCapture()
                path = pending.file.absolutePath; recovery = false
                camera.launch(pending.uri)
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) {
                val pending = path
                path = null; pending?.let { captures.discardPendingCapture(File(it)) }
                busy = false; viewModel.reportError()
            }
        }
    }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) launch() else { busy = false; viewModel.reportError() }
    }
    return StudioCameraActions(take = { project ->
        if (!busy && path == null) {
            busy = true
            projectId = project.id; revision = project.revision
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) launch()
            else permission.launch(Manifest.permission.CAMERA)
        }
    }, retry = { project ->
        if (!busy && path != null) {
            // This is a new explicit user retry, bound to the project now shown, never automatic mutation replay.
            projectId = project.id; revision = project.revision
            val pending = requireNotNull(path)
            if (File(pending).length() > 0) receive()
            else {
                busy = true
                scope.launch { captures.discardPendingCapture(File(pending)); path = null; launch() }
            }
        }
    }, recoveryAvailable = path != null && (recovery || !busy), busy = busy)
}
