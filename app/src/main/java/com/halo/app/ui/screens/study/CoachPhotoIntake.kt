package com.halo.app.ui.screens.study

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.halo.app.R
import com.halo.app.ai.PrivacyMode
import com.halo.app.capture.AndroidImageInputPreparer
import com.halo.app.di.ManualDependencyGraph as Graph
import com.halo.app.ui.instruments.HaloInstrumentAction
import com.halo.app.ui.instruments.HaloInstrumentBar
import com.halo.app.ui.screens.kids.HomeworkPhotoReviewContent
import com.halo.app.ui.toolkit.readScanWithVision
import com.halo.app.ui.toolkit.scanFrameText
import com.halo.sdk.driver.CapturedAsset
import com.halo.sdk.driver.StillCaptureRequest
import com.halo.sdk.driver.VisualCaptureId
import com.halo.sdk.runtime.multimodal.ImageInputLimits
import com.halo.sdk.runtime.multimodal.MediaAssetContent
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val PHOTO_BYTE_LIMIT = 20 * 1024 * 1024

/** The capture has no Workspace ID. Only supported, parsed questions cross from intake to coaching. */
@Composable
internal fun CoachPhotoIntake(
    child: Boolean,
    allowed: () -> Boolean,
    reviewedPhotoTicket: String?,
    onReviewedPhotoConsumed: () -> Unit,
    keepHomeworkPhoto: (suspend (Bitmap) -> Unit)?,
    onStudyText: (suspend (String) -> Unit)?,
    acceptsQuestion: (LocalCoachProblem) -> Boolean,
    onQuestions: (List<LocalCoachProblem>) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val privacyMode by Graph.privacyModes.mode.collectAsStateWithLifecycle()
    val currentlyAllowed by rememberUpdatedState(allowed)
    val deliver by rememberUpdatedState(onQuestions)
    val admitsQuestion by rememberUpdatedState(acceptsQuestion)
    val reportTicket by rememberUpdatedState(onReviewedPhotoConsumed)
    var pending by remember { mutableStateOf<File?>(null) }
    var bitmap by remember { mutableStateOf<Bitmap?>(null) }
    var reading by remember { mutableStateOf(false) }
    var acquiring by remember { mutableStateOf(false) }
    var live by remember { mutableStateOf(false) }
    var issue by remember { mutableStateOf<Int?>(null) }
    var written by remember { mutableStateOf("") }
    var photoQuestions by remember { mutableStateOf<List<LocalCoachProblem>?>(null) }
    var kept by remember { mutableStateOf(false) }
    var recognizedSource by remember { mutableStateOf("") }
    var checkPhotoSource by remember { mutableStateOf<(() -> Boolean)?>(null) }
    val approvedGlasses = if (child) Graph.kidsPreferences.homework.approvedGlasses.collectAsStateWithLifecycle().value else null
    val readyGlasses = if (approvedGlasses != null) {
        val revision by Graph.deviceSessionManager.stateRevision.collectAsStateWithLifecycle()
        produceState(false, approvedGlasses, revision) {
            value = try { Graph.visualContextSource.connectedHomeworkGlasses().any { it.identity.deviceId == approvedGlasses } }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { false }
        }.value
    } else false

    DisposableEffect(Unit) {
        onDispose { pending?.delete(); pending = null }
    }
    val currentBitmap = bitmap
    DisposableEffect(currentBitmap) { onDispose { currentBitmap?.recycle() } }
    val unsupported = if (child) R.string.halo11_coach_unsupported_kids else R.string.halo11_coach_unsupported_adult
    fun offer(questions: List<LocalCoachProblem>) {
        val approved = questions.filter(admitsQuestion)
        if (currentlyAllowed() && approved.isNotEmpty()) {
            bitmap = null
            photoQuestions = null
            checkPhotoSource = null
            written = ""
            issue = null
            deliver(approved)
        } else if (currentlyAllowed()) issue = unsupported
    }
    suspend fun consume(image: Bitmap, allowKeep: Boolean = false, sourceAllowed: (() -> Boolean)? = null,
        cloudReview: Boolean = false) {
        var retained = false
        try {
            check(currentlyAllowed() && sourceAllowed?.invoke() != false)
            val transcription = if (child) null else readScanWithVision(image, allowRemote = cloudReview)
            check(currentlyAllowed() && sourceAllowed?.invoke() != false)
            if (cloudReview && transcription == null) {
                issue = R.string.halo11_coach_vision_failed
                return
            }
            val lines = transcription?.lines() ?: scanFrameText(image).map { it.text }
            check(currentlyAllowed() && sourceAllowed?.invoke() != false)
            val questions = localCoachQuestions(lines, child).filter(admitsQuestion)
            if (!child && questions.isEmpty() && currentlyAllowed())
                recognizedSource = lines.joinToString("\n").take(12000)
            if (allowKeep && keepHomeworkPhoto != null && currentlyAllowed() && questions.isNotEmpty()) {
                bitmap = image
                photoQuestions = questions
                checkPhotoSource = sourceAllowed
                kept = false
                retained = true
            } else offer(questions)
        } finally { if (!retained) image.recycle() }
    }
    fun prepare(sourceAllowed: (() -> Boolean)? = null, photoError: Int = R.string.halo11_coach_unavailable,
        read: suspend () -> Bitmap) {
        if (reading || acquiring || !currentlyAllowed() || sourceAllowed?.invoke() == false) return
        reading = true
        issue = null
        recognizedSource = ""
        if (child) { bitmap = null; photoQuestions = null; checkPhotoSource = null }
        scope.launch {
            var decoded: Bitmap? = null
            var adopted = false
            try {
                withContext(Dispatchers.IO) { decoded = read() }
                val image = requireNotNull(decoded)
                if (!currentlyAllowed()) return@launch
                check(sourceAllowed?.invoke() != false)
                if (child) {
                    decoded = null
                    consume(image, allowKeep = true, sourceAllowed = sourceAllowed)
                } else {
                    bitmap = image
                    adopted = true
                }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) {
                if (currentlyAllowed()) issue = photoError
            } finally {
                if (!adopted) decoded?.recycle()
                reading = false
            }
        }
    }
    fun glassesAuthority(deviceId: String): () -> Boolean {
        val family = Graph.kidsPreferences
        val revision = runCatching { family.presentationRevision() }.getOrNull()
        return { revision != null && currentlyAllowed() && !Graph.isHaloPaused() &&
            runCatching { family.requireHomeworkGlasses(revision, deviceId) }.isSuccess }
    }
    LaunchedEffect(reviewedPhotoTicket, child) {
        if (reviewedPhotoTicket != null && currentlyAllowed()) {
            val sourceAllowed = if (child) approvedGlasses?.let(::glassesAuthority) else null
            if (!child || sourceAllowed?.invoke() == true) {
                prepare(sourceAllowed, if (child) R.string.halo11_coach_glasses_unavailable else R.string.halo11_coach_unavailable) {
                    val source = Graph.contextualCapture.takePhotoCoachCopy(reviewedPhotoTicket, child)
                    decodeCoachPhoto(source.bytes, source.mimeType)
                }
            }
            reportTicket()
        }
    }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { captured ->
        val file = pending
        pending = null
        acquiring = false
        if (file != null) {
            if (captured && currentlyAllowed()) prepare {
                try { file.inputStream().use { decodeCoachPhoto(it, "image/jpeg") } }
                finally { file.delete() }
            } else file.delete()
        }
    }
    fun openCamera() {
        if (acquiring || reading || !currentlyAllowed()) return
        acquiring = true
        issue = null
        scope.launch {
            var prepared: File? = null
            var delegated = false
            try {
                withContext(Dispatchers.IO) {
                    val dir = File(context.cacheDir, "coach-captures")
                    check(dir.isDirectory || dir.mkdirs())
                    val expiry = System.currentTimeMillis() - 60 * 60 * 1000L
                    dir.listFiles().orEmpty().filter { it.name.startsWith("coach-") && it.lastModified() in 1..expiry }
                        .forEach { it.delete() }
                    prepared = File.createTempFile("coach-", ".jpg", dir)
                }
                if (!currentlyAllowed()) return@launch
                val file = requireNotNull(prepared)
                pending = file
                camera.launch(FileProvider.getUriForFile(context, "${context.packageName}.captures", file))
                delegated = true
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { if (currentlyAllowed()) issue = R.string.halo11_coach_camera_unavailable }
            finally {
                if (!delegated) {
                    pending = null
                    withContext(NonCancellable + Dispatchers.IO) { prepared?.delete() }
                    acquiring = false
                }
            }
        }
    }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { permitted ->
        if (permitted && currentlyAllowed()) openCamera()
        else if (currentlyAllowed()) issue = R.string.halo11_coach_camera_unavailable
    }
    val livePermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { permitted ->
        if (permitted && currentlyAllowed()) live = true
        else if (currentlyAllowed()) issue = R.string.halo11_coach_camera_unavailable
    }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null && !child && currentlyAllowed()) prepare {
            val resolver = context.contentResolver
            val mime = resolver.getType(uri)?.takeIf { it.startsWith("image/") } ?: "image/jpeg"
            requireNotNull(resolver.openInputStream(uri)).use { decodeCoachPhoto(it, mime) }
        }
    }
    fun requestCamera() {
        if (reading || acquiring || !currentlyAllowed()) return
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) openCamera()
        else permission.launch(Manifest.permission.CAMERA)
    }
    fun requestGlasses(deviceId: String) {
        val sourceAllowed = glassesAuthority(deviceId)
        prepare(sourceAllowed, R.string.halo11_coach_glasses_unavailable) {
            var captured: CapturedAsset? = null
            try {
                Graph.haloPause.whileActive {
                    captured = Graph.visualContextSource.captureHomeworkStill(deviceId,
                        StillCaptureRequest(VisualCaptureId("homework:${UUID.randomUUID()}"))) { check(sourceAllowed()) }
                }
                val still = requireNotNull(captured)
                decodeCoachPhoto(requireNotNull(still.bytes), still.mimeType)
            } finally {
                captured?.bytes?.fill(0)
            }
        }
    }
    if (live && child && currentlyAllowed()) {
        com.halo.app.ui.screens.study.LiveWorksheetCamera(currentlyAllowed, admitsQuestion,
            onChoose = { live = false; offer(listOf(it)) }, onClose = { live = false })
        return
    }
    val photo = bitmap
    val recognized = photoQuestions
    val privacy = stringResource(R.string.halo11_coach_private_photo)
    if (child && photo != null && recognized != null && keepHomeworkPhoto != null) {
        Text(stringResource(R.string.halo11_coach_homework_local_keep), style = MaterialTheme.typography.bodySmall)
        val sourceCheck = checkPhotoSource
        val allowedSource = sourceCheck?.invoke() != false
        LaunchedEffect(approvedGlasses, allowedSource) {
            if (!allowedSource) {
                bitmap = null
                photoQuestions = null
                checkPhotoSource = null
                issue = R.string.halo11_coach_glasses_unavailable
            }
        }
        Button(onClick = { if (sourceCheck?.invoke() != false) offer(recognized) }, enabled = !reading && allowedSource) {
            Text(stringResource(R.string.halo11_coach_pick_question))
        }
        OutlinedButton(enabled = !reading && !kept && allowedSource, onClick = {
            if (!reading && !kept && currentlyAllowed() && sourceCheck?.invoke() != false) {
                reading = true
                issue = null
                scope.launch {
                    try {
                        val copy = requireNotNull(photo.copy(Bitmap.Config.ARGB_8888, false))
                        try { if (sourceCheck?.invoke() != false) { keepHomeworkPhoto(copy); kept = true } } finally { copy.recycle() }
                    } catch (cancelled: CancellationException) { throw cancelled }
                    catch (_: Exception) { issue = R.string.halo11_coach_keep_failed }
                    finally { reading = false }
                }
            }
        }) { Text(stringResource(if (kept) R.string.halo11_coach_kept_photo else R.string.halo11_coach_keep_photo)) }
        TextButton(onClick = { bitmap = null; photoQuestions = null; issue = null; requestCamera() }, enabled = !reading) {
            Text(stringResource(R.string.halo11_coach_retake))
        }
    } else if (!child && photo != null) {
        val preview = stringResource(R.string.halo11_coach_photo_preview)
        fun readReviewedPhoto(cloudReview: Boolean) {
            if (currentlyAllowed() && !reading && !acquiring) {
                reading = true
                issue = null
                recognizedSource = ""
                scope.launch {
                    try { consume(requireNotNull(photo.copy(Bitmap.Config.ARGB_8888, false)), cloudReview = cloudReview) }
                    catch (cancelled: CancellationException) { throw cancelled }
                    catch (_: Exception) { if (currentlyAllowed()) issue = R.string.halo11_coach_unavailable }
                    finally { reading = false }
                }
            }
        }
        HomeworkPhotoReviewContent(photo, failed = false, busy = reading || acquiring,
            onUse = { readReviewedPhoto(cloudReview = false) }, onRetake = ::requestCamera, onCancel = { bitmap = null }, reviewTitle = preview,
            reviewCaption = privacy, useLabel = stringResource(R.string.halo11_coach_read_photo),
            retakeLabel = stringResource(R.string.halo11_coach_retake), photoLabel = preview, embedded = true)
        if (privacyMode == PrivacyMode.CLOUD) {
            Text(stringResource(R.string.halo11_coach_vision_review), style = MaterialTheme.typography.bodySmall)
            OutlinedButton(onClick = { readReviewedPhoto(cloudReview = true) }, enabled = !reading && !acquiring,
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                Text(stringResource(R.string.halo11_coach_vision_read))
            }
        }
    } else {
        Text(privacy, style = MaterialTheme.typography.bodySmall)
        if (child) OutlinedButton(onClick = {
            if (currentlyAllowed()) {
                if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
                    live = true
                else livePermission.launch(Manifest.permission.CAMERA)
            }
        }, enabled = !reading && !acquiring, modifier = Modifier.heightIn(min = 56.dp)) {
            Text(stringResource(R.string.coach_live_camera))
        }
        OutlinedButton(onClick = ::requestCamera, enabled = !reading && !acquiring, modifier = Modifier.testTag("coach.capture")) {
            Text(stringResource(R.string.halo11_coach_take_photo))
        }
        if (child && approvedGlasses != null) OutlinedButton(onClick = { requestGlasses(approvedGlasses) },
            enabled = !reading && !acquiring && readyGlasses, modifier = Modifier.testTag("coach.capture.glasses")) {
            Text(stringResource(R.string.halo11_coach_glasses_take))
        }
        if (child && approvedGlasses != null && !readyGlasses)
            Text(stringResource(R.string.halo11_coach_glasses_connect), style = MaterialTheme.typography.bodySmall)
    }
    if (!child) OutlinedButton(enabled = !reading && !acquiring, onClick = {
        if (currentlyAllowed()) picker.launch(arrayOf("image/*"))
    }) { Text(stringResource(R.string.halo11_coach_choose_photo)) }
    OutlinedTextField(written, onValueChange = { written = it.take(1200) }, enabled = !reading && !acquiring,
        modifier = Modifier.fillMaxWidth(), keyboardOptions = KeyboardOptions(autoCorrectEnabled = !child),
        label = { Text(stringResource(R.string.halo11_coach_typing)) })
    TextButton(onClick = {
        val parsed = localCoachQuestions(written.lines(), child).filter(admitsQuestion)
        if (!child && parsed.isEmpty() && currentlyAllowed()) recognizedSource = written
        offer(parsed)
    },
        enabled = written.isNotBlank() && !reading && !acquiring) { Text(stringResource(R.string.halo11_coach_read_photo)) }
    if (!child && onStudyText != null && recognizedSource.isNotBlank()) {
        Text(stringResource(R.string.halo11_coach_review_for_study), style = MaterialTheme.typography.bodySmall)
        OutlinedTextField(recognizedSource, { recognizedSource = it.take(12000) }, enabled = !reading && !acquiring,
            modifier = Modifier.fillMaxWidth(), minLines = 2, maxLines = 6)
        TextButton(enabled = !reading && !acquiring, onClick = {
            if (currentlyAllowed()) {
                val reviewed = recognizedSource.trim()
                reading = true
                scope.launch {
                    try { onStudyText(reviewed) }
                    catch (cancelled: CancellationException) { throw cancelled }
                    catch (_: Exception) { issue = R.string.halo11_coach_keep_failed }
                    finally { reading = false }
                }
            }
        }) { Text(stringResource(R.string.halo11_coach_keep_for_study)) }
    }
    if (reading || acquiring) LinearProgressIndicator(Modifier.fillMaxWidth())
    val problem = issue
    if (problem != null) Text(stringResource(problem), style = MaterialTheme.typography.bodyMedium)
    if (!child && !reading && !acquiring) HaloInstrumentBar("coach",
        mapOf("coach.capture" to HaloInstrumentAction(::requestCamera)), primary = "coach.capture")
}

private suspend fun decodeCoachPhoto(stream: InputStream, mime: String): Bitmap {
    val bytes = ByteArrayOutputStream().use { output ->
        val chunk = ByteArray(16 * 1024)
        while (true) {
            val got = stream.read(chunk)
            if (got < 0) break
            require(output.size() + got <= PHOTO_BYTE_LIMIT)
            output.write(chunk, 0, got)
        }
        output.toByteArray()
    }
    return decodeCoachPhoto(bytes, mime)
}

private suspend fun decodeCoachPhoto(bytes: ByteArray, mime: String): Bitmap {
    return try {
        require(bytes.size in 1..PHOTO_BYTE_LIMIT)
        val prepared = AndroidImageInputPreparer().prepare(MediaAssetContent(bytes, mime), null,
            ImageInputLimits(maxWidth = 1800, maxHeight = 1800, maxPixels = 3_240_000, maxSourceBytes = PHOTO_BYTE_LIMIT))
        val oriented = prepared.content.bytes
        try { requireNotNull(BitmapFactory.decodeByteArray(oriented, 0, oriented.size)) }
        finally { oriented.fill(0) }
    } finally { bytes.fill(0) }
}
