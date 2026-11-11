package com.halo.app.wearables

import android.content.ContentValues
import android.graphics.BitmapFactory
import android.os.Build
import android.provider.MediaStore
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.halo.app.di.ManualDependencyGraph
import com.halo.app.language.uiText
import com.halo.app.ui.design.*
import com.halo.app.ui.icons.HaloGlyph
import com.halo.app.ui.presence.BindShellChildPage
import com.halo.app.ui.presence.ShellPageAction
import com.halo.sdk.runtime.embodiment.DeviceRecord
import com.halo.sdk.runtime.wearables.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import java.util.UUID

@Composable
fun NativeWearableControlScreen(deviceId: String, onBack: () -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)) {
        NativeWearableControls(deviceId, onBack)
    }
}

/** UI and runtime tools share the exact same session/driver operation owner. */
@Composable
fun NativeWearableControls(deviceId: String, onBack: (() -> Unit)? = null) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var record by remember(deviceId) { mutableStateOf<DeviceRecord?>(null) }
    var session by remember(deviceId) { mutableStateOf<NativeWearableSession?>(null) }
    var task by remember(deviceId) { mutableStateOf<Job?>(null) }
    var message by remember(deviceId) { mutableStateOf<String?>(null) }
    var image by remember(deviceId) { mutableStateOf<ByteArray?>(null) }
    var busy by remember(deviceId) { mutableStateOf(false) }
    LaunchedEffect(deviceId) {
        try {
            record = ManualDependencyGraph.deviceRegistry.getDevice(deviceId)
            session = record?.let { ManualDependencyGraph.deviceAutoConnectCoordinator.nativeWearables.ensure(it) }
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { message = "This native adapter is not available" }
    }
    DisposableEffect(deviceId) { onDispose { task?.cancel(); image = null } }
    val snapshot by (session?.driver?.state ?: remember { MutableStateFlow(NativeSnapshot()) }).collectAsStateWithLifecycle()
    val ready = snapshot.stage == NativeStage.READY
    val supported = session?.driver?.supportedActions.orEmpty()
    fun runOperation(block: suspend (NativeWearableSession) -> String) {
        val owner = session ?: return
        if (busy) return
        busy = true
        task = scope.launch {
            try { message = block(owner) }
            catch (cancelled: CancellationException) { message = "Operation cancelled; check device state before retrying"; throw cancelled }
            catch (_: Exception) { message = "The device did not complete this operation. Check its connection and retry explicitly." }
            finally { busy = false }
        }
    }
    fun execute(action: NativeAction) = runOperation { owner ->
        if (action in setOf(NativeAction.BATTERY, NativeAction.DEVICE_INFO)) {
            checkNotNull(ManualDependencyGraph.nativeDeviceStatusActions.refresh(deviceId, action, snapshot.sourceVersion))
            return@runOperation "Device response received"
        }
        val receipt = owner.driver.execute(action)
        when (receipt.confirmation) {
            NativeConfirmation.RECEIVED -> "Device response received"
            NativeConfirmation.ACKNOWLEDGED -> "Device acknowledged the command"
            NativeConfirmation.DISPATCHED -> "Command sent; physical completion is not yet confirmed"
        }
    }
    val hosted = if (onBack != null) BindShellChildPage(record?.identity?.displayName ?: "My glasses", onBack,
        objectId = deviceId, titleIsSource = true,
        sourceIdentity = "$deviceId:${snapshot.stage}:$busy",
        actions = listOf(
            ShellPageAction("glasses.refresh", "Check status", HaloGlyph.GLASSES, ready && !busy && NativeAction.BATTERY in supported) { execute(NativeAction.BATTERY) },
            ShellPageAction("glasses.photo", "Take photo", HaloGlyph.CAMERA, ready && !busy && NativeAction.PHOTO in supported && snapshot.cameraAvailable != false) { execute(NativeAction.PHOTO) },
        )) else false
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (!hosted) Text(record?.identity?.displayName ?: uiText("Native glasses"), style = MaterialTheme.typography.titleLarge)
        Text(uiText("Native protocol · physical qualification pending"), style = MaterialTheme.typography.labelMedium)
        HaloStatusPresentation(if (ready) HaloStatus.READY else if (snapshot.stage == NativeStage.ERROR) HaloStatus.FAILED
            else if (snapshot.stage in setOf(NativeStage.CONNECTING, NativeStage.HANDSHAKE)) HaloStatus.PREPARING else HaloStatus.UNAVAILABLE)
        if (session?.driver?.protocolId == "BRILLIANT_FRAME") Text(
            androidx.compose.ui.res.stringResource(com.halo.app.R.string.frame_status_adapter_scope), style = MaterialTheme.typography.bodySmall)
        snapshot.error?.let { Text(uiText(it), style = MaterialTheme.typography.bodySmall) }
        snapshot.batteryPercent?.let { Text(uiText("Battery") + ": $it%") }
        snapshot.model?.let { Text(uiText("Model") + ": $it") }
        snapshot.firmware?.let { Text(uiText("Firmware") + ": $it") }
        snapshot.mediaCounts?.let { Text(uiText("Photos / videos / recordings") + ": ${it.joinToString(" / ")}") }
        snapshot.aggregateMediaCount?.let { Text(uiText("Media count") + ": $it") }
        snapshot.videoRecording?.let { Text(uiText(if (it) "Video recording is active" else "Video recording is stopped")) }
        snapshot.audioRecording?.let { Text(uiText(if (it) "Audio recording is active" else "Audio recording is stopped")) }
        message?.let { Text(uiText(it), style = MaterialTheme.typography.bodyMedium) }
        if (busy) {
            LinearProgressIndicator(Modifier.fillMaxWidth())
            OutlinedButton(onClick = { task?.cancel() }, modifier = Modifier.heightIn(min = 48.dp)) { Text(uiText("Cancel operation")) }
        }
        if (!ready) Button(onClick = { runOperation { it.authorizeExplicitConnection(); it.connect(); "Connected and ready" } },
            enabled = session != null && !busy, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text(uiText("Connect")) }
        else {
            OutlinedButton(onClick = { runOperation { it.disconnect(); "Disconnected" } }, enabled = !busy,
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text(uiText("Disconnect")) }
            record?.let { saved ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(uiText("Auto-connect"))
                    Switch(checked = saved.autoConnect, enabled = !busy, onCheckedChange = { enabled ->
                        scope.launch {
                            try {
                                val fresh = ManualDependencyGraph.deviceRegistry.getDevice(deviceId) ?: return@launch
                                val updated = fresh.copy(autoConnect = enabled)
                                ManualDependencyGraph.deviceRegistry.saveDevice(updated)
                                ManualDependencyGraph.deviceSessionManager.refreshSavedDevices()
                                record = updated
                            } catch (cancelled: CancellationException) { throw cancelled }
                            catch (_: Exception) { message = "Could not save the auto-connect preference" }
                        }
                    })
                }
            }
            listOf("Battery status" to NativeAction.BATTERY, "Device information" to NativeAction.DEVICE_INFO,
                "Check for photos" to NativeAction.MEDIA_COUNTS, "Storage status" to NativeAction.STORAGE).filter { it.second in supported }.forEach { (label, action) ->
                OutlinedButton(onClick = { execute(action) }, enabled = !busy, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text(uiText(label)) }
            }
            if (snapshot.cameraAvailable != false && NativeAction.PHOTO in supported) {
                Text(uiText("Camera and recording"), style = MaterialTheme.typography.titleMedium)
                Text(uiText("Capture is explicit. Images stay on this phone; no cloud request is made."), style = MaterialTheme.typography.bodySmall)
                listOf("Take photo on glasses" to NativeAction.PHOTO, "Start video" to NativeAction.START_VIDEO,
                    "Stop video" to NativeAction.STOP_VIDEO, "Start audio recording" to NativeAction.START_AUDIO,
                    "Stop audio recording" to NativeAction.STOP_AUDIO).filter { it.second in supported }.forEach { (label, action) ->
                    OutlinedButton(onClick = { execute(action) }, enabled = !busy, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text(uiText(label)) }
                }
                Button(onClick = { runOperation { image = it.driver.captureAiPhoto(); "Fresh glasses image received" } }, enabled = !busy && snapshot.aiPhotoAvailable,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text(uiText("Capture image for review")) }
            }
            if (NativeAction.SYNC_TIME in supported) OutlinedButton(onClick = { execute(NativeAction.SYNC_TIME) }, enabled = !busy,
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text(uiText("Sync device clock")) }
        }
        image?.let { bytes ->
            val bitmap = remember(bytes) {
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
                if (bounds.outWidth <= 0 || bounds.outHeight <= 0 || bounds.outWidth.toLong() * bounds.outHeight > 48_000_000) null
                else BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply {
                    inSampleSize = Integer.highestOneBit(maxOf(1, maxOf(bounds.outWidth, bounds.outHeight) / 1080))
                })
            }
            if (bitmap != null) Image(bitmap.asImageBitmap(), uiText("Captured glasses image"), Modifier.fillMaxWidth().heightIn(max = 360.dp))
            if (Build.VERSION.SDK_INT >= 29) OutlinedButton(onClick = { runOperation {
                withContext(Dispatchers.IO) {
                    val resolver = context.contentResolver
                    val values = ContentValues().apply {
                        put(MediaStore.Images.Media.DISPLAY_NAME, "Halo_${UUID.randomUUID()}.jpg")
                        put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                        put(MediaStore.Images.Media.RELATIVE_PATH, "DCIM/Halo")
                        put(MediaStore.Images.Media.IS_PENDING, 1)
                    }
                    val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: error("Cannot create gallery item")
                    try {
                        resolver.openOutputStream(uri)?.use { output -> output.write(bytes) } ?: error("Cannot save image")
                        resolver.update(uri, ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) }, null, null)
                    } catch (error: Exception) { resolver.delete(uri, null, null); throw error }
                }
                "Saved to DCIM/Halo"
            } }, enabled = !busy, modifier = Modifier.heightIn(min = 48.dp)) { Text(uiText("Save image to Gallery")) }
            TextButton(onClick = { image = null }, modifier = Modifier.heightIn(min = 48.dp)) { Text(uiText("Close image")) }
        }
    }
}
