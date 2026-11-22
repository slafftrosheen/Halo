package com.halo.app.ui.toolkit

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.halo.app.R
import com.halo.app.data.toolkit.HaloMeasurement
import com.halo.app.data.toolkit.MeasurementPoint
import com.halo.app.data.toolkit.RoomWall
import com.halo.app.di.ManualDependencyGraph as Graph
import com.halo.app.ui.instruments.HaloInstrumentAction
import com.halo.app.ui.instruments.HaloInstrumentBar
import java.io.File
import java.util.Locale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlin.math.sqrt

private enum class MeasurePane(@StringRes val label: Int) {
    RULER(R.string.halo11_tool_ruler),
    LEVEL(R.string.halo11_tool_level),
    ROOM(R.string.halo11_tool_room),
}

private data class PhoneTilt(val side: Double, val forward: Double)
private data class ImageFit(val left: Float, val top: Float, val width: Float, val height: Float)

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun HaloMeasureScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val family = Graph.kidsPreferences
    val loaded by family.loadedState.collectAsStateWithLifecycle()
    val kids by family.enabled.collectAsStateWithLifecycle()
    val adult = loaded && !kids
    val locale = LocalConfiguration.current.locales[0]
    var mode by rememberSaveable { mutableStateOf(MeasurePane.RULER) }
    var permission by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
    }
    var camera by remember { mutableStateOf(false) }
    var streaming by remember { mutableStateOf(false) }
    var preview by remember { mutableStateOf<PreviewView?>(null) }
    var photo by remember { mutableStateOf<Bitmap?>(null) }
    var points by remember { mutableStateOf(emptyList<MeasurementPoint>()) }
    var reference by rememberSaveable { mutableStateOf("") }
    var walls by remember { mutableStateOf(emptyList<RoomWall>()) }
    var tilt by remember { mutableStateOf<PhoneTilt?>(null) }
    var busy by remember { mutableStateOf(false) }
    var notice by remember { mutableIntStateOf(0) }
    var error by remember { mutableIntStateOf(0) }
    val image = photo
    val length = reference.decimal()
    val estimate = remember(image, points, length) {
        if (image == null || length == null || points.size != 4) null
        else runCatching { HaloMeasurement.imageEstimate(image.width, image.height, points, length) }.getOrNull()
    }
    val outline = remember(walls) { HaloMeasurement.roomPoints(walls) }
    val estimateLabel = if (estimate == null) null else stringResource(R.string.halo11_tool_measure_estimate, measurementNumber(estimate, locale))
    val tiltReading = tilt
    val tiltLabel = tiltReading?.let { stringResource(R.string.halo11_tool_measure_level_axes,
        measurementNumber(it.side, locale), measurementNumber(it.forward, locale)) }
    val closure = outline.last().let { hypot(it.x, it.y) }
    val roomLabel = if (walls.isEmpty()) null else if (closure < .005)
        stringResource(R.string.halo11_tool_measure_room_closed)
    else stringResource(R.string.halo11_tool_measure_room_open, measurementNumber(closure, locale))
    val summary = when (mode) { MeasurePane.RULER -> estimateLabel; MeasurePane.ROOM -> roomLabel; MeasurePane.LEVEL -> tiltLabel }
    val title = stringResource(R.string.halo11_tool_measure_title)
    val detailLabel = stringResource(mode.label)
    val pause = Graph.haloPause
    val paused by pause.state.collectAsStateWithLifecycle()
    val wallLabel = walls.takeIf { it.isNotEmpty() }?.let {
        stringResource(R.string.halo11_tool_measure_wall_list, it.joinToString(" · ") { wall -> measurementNumber(wall.metres, locale) })
    }

    LaunchedEffect(adult, paused.paused) {
        if (paused.paused) camera = false
        if (!adult) { camera = false; photo = null; points = emptyList(); walls = emptyList(); tilt = null }
    }

    fun newPhoto(bitmap: Bitmap) {
        photo = bitmap
        points = emptyList()
        camera = false
        error = 0
        notice = 0
    }

    val cameraPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { allowed ->
        permission = allowed
        camera = allowed && adult && !pause.isPaused()
        if (!allowed) error = R.string.halo11_tool_measure_camera_error
    }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null && adult && !busy && !pause.isPaused()) {
            busy = true
            scope.launch {
                try {
                    val approved = family.adultActionRevision()
                    val privacyRevision = Graph.privacyModes.revision
                    val pauseVersion = pause.state.value.version
                    val imported = withContext(Dispatchers.IO) { requireNotNull(loadAlbumBitmap(context, uri, 2000)) }
                    family.requireAdultAction(approved)
                    check(privacyRevision == Graph.privacyModes.revision && pauseVersion == pause.state.value.version && !pause.isPaused())
                    newPhoto(imported)
                } catch (cancelled: CancellationException) { throw cancelled }
                catch (_: Exception) { error = R.string.halo11_tool_measure_photo_error }
                finally { busy = false }
            }
        }
    }

    fun change(next: MeasurePane) {
        camera = false
        mode = next
        notice = 0
        error = 0
        if (next != MeasurePane.LEVEL) tilt = null
    }

    fun keep() {
        val text = summary ?: return
        val result = if (mode == MeasurePane.ROOM && wallLabel != null) text + " · " + wallLabel else text
        if (busy || !adult || paused.paused) return
        val measuredImage = image.takeIf { mode == MeasurePane.RULER }
        val capturedPoints = points.takeIf { measuredImage != null }.orEmpty()
        val capturedOutline = outline.takeIf { mode == MeasurePane.ROOM }.orEmpty()
        busy = true
        scope.launch {
            notice = 0
            error = 0
            try {
                val approval = family.adultActionRevision()
                val modeVersion = Graph.privacyModes.revision
                val pauseVersion = pause.state.value.version
                fun authorize() {
                    family.requireAdultAction(approval)
                    check(modeVersion == Graph.privacyModes.revision && !pause.isPaused() && pauseVersion == pause.state.value.version)
                }
                keepMeasurement(context, title, detailLabel, result, measuredImage, capturedPoints, capturedOutline, ::authorize)
                notice = R.string.halo11_tool_measure_kept
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { error = R.string.halo11_tool_measure_keep_failed }
            finally { busy = false }
        }
    }

    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Halo11ToolHeader(R.string.halo11_tool_measure_title, onBack)
        Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (adult) when (mode) {
                MeasurePane.RULER -> {
                    Text(stringResource(R.string.halo11_tool_measure_instructions))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton({
                            error = 0
                            permission = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
                            if (permission) camera = true else cameraPermission.launch(Manifest.permission.CAMERA)
                        }, enabled = !busy && !paused.paused) { Text(stringResource(R.string.halo11_tool_measure_camera)) }
                        TextButton({ picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                            enabled = !busy && !paused.paused) { Text(stringResource(R.string.halo11_tool_measure_photo)) }
                    }
                    if (camera && permission && !paused.paused) {
                        HaloScanCamera(1f, false, onView = { preview = it }, onAvailable = { streaming = it },
                            onFailure = { camera = false; error = R.string.halo11_tool_measure_camera_error },
                            modifier = Modifier.fillMaxWidth().height(252.dp))
                        Button({
                            val frame = preview?.scanBitmap()
                            if (frame != null) newPhoto(frame) else error = R.string.halo11_tool_measure_camera_error
                        }, enabled = streaming && !busy) { Text(stringResource(R.string.halo11_tool_measure_snap)) }
                    } else if (image != null) {
                        Text(stringResource(R.string.halo11_tool_measure_points))
                        MarkMeasurementImage(image, points, !busy) { tap ->
                            points = if (points.size == 4) listOf(tap) else points + tap
                            notice = 0
                        }
                        TextButton({ points = emptyList(); notice = 0 }, enabled = points.isNotEmpty() && !busy) {
                            Text(stringResource(R.string.halo11_tool_measure_reset))
                        }
                        OutlinedTextField(reference, { reference = it.take(16); notice = 0 }, Modifier.fillMaxWidth(),
                            label = { Text(stringResource(R.string.halo11_tool_measure_reference)) }, singleLine = true, enabled = !busy,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                        if (points.size == 4 && estimate == null && reference.isNotBlank())
                            Text(stringResource(R.string.halo11_tool_measure_invalid), color = MaterialTheme.colorScheme.error)
                        estimateLabel?.let { Text(it, style = MaterialTheme.typography.headlineSmall) }
                    } else Text(stringResource(R.string.halo11_tool_measure_none))
                }
                MeasurePane.LEVEL -> if (!paused.paused) MeasureLevel(tilt, tiltLabel, onReading = { tilt = it })
                MeasurePane.ROOM -> {
                    MeasureRoom(walls, outline, roomLabel, enabled = !busy, onWalls = { walls = it; notice = 0 })
                    wallLabel?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                }
            } else Text(stringResource(R.string.halo11_tool_measure_unavailable))
            if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            if (notice != 0) Text(stringResource(notice))
            if (error != 0) Text(stringResource(error), color = MaterialTheme.colorScheme.error)
        }
        if (adult) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                MeasurePane.entries.forEach { pane ->
                    FilterChip(mode == pane, { change(pane) }, enabled = !busy, label = { Text(stringResource(pane.label)) })
                }
                TextButton(::keep, enabled = summary != null && !busy && !paused.paused) {
                    Text(stringResource(R.string.halo11_tool_keep))
                }
            }
            HaloInstrumentBar("measure", mapOf(
                "measure.ruler" to HaloInstrumentAction({ change(MeasurePane.RULER) }, !busy, mode == MeasurePane.RULER),
                "measure.level" to HaloInstrumentAction({ change(MeasurePane.LEVEL) }, !busy, mode == MeasurePane.LEVEL),
                "measure.room" to HaloInstrumentAction({ change(MeasurePane.ROOM) }, !busy, mode == MeasurePane.ROOM),
                "measure.keep" to HaloInstrumentAction(::keep, !busy && summary != null && !paused.paused),
            ), primary = "measure.ruler")
        }
    }
}

private fun fitImage(width: Float, height: Float, image: Bitmap): ImageFit {
    val ratio = minOf(width / image.width, height / image.height)
    val dw = image.width * ratio
    val dh = image.height * ratio
    return ImageFit((width - dw) / 2f, (height - dh) / 2f, dw, dh)
}

@Composable
private fun MarkMeasurementImage(photo: Bitmap, points: List<MeasurementPoint>, enabled: Boolean, onPoint: (MeasurementPoint) -> Unit) {
    val source = remember(photo) { photo.asImageBitmap() }
    val latestPoint by rememberUpdatedState(onPoint)
    Canvas(Modifier.fillMaxWidth().heightIn(min = 160.dp, max = 350.dp).aspectRatio(photo.width.toFloat() / photo.height)
        .pointerInput(photo, enabled) {
            if (!enabled) return@pointerInput
            detectTapGestures { tap ->
                val fit = fitImage(size.width.toFloat(), size.height.toFloat(), photo)
                val x = (tap.x - fit.left) / fit.width
                val y = (tap.y - fit.top) / fit.height
                if (x in 0f..1f && y in 0f..1f) latestPoint(MeasurementPoint(x.toDouble(), y.toDouble()))
            }
        }) {
        val fit = fitImage(size.width, size.height, photo)
        drawImage(source, dstOffset = IntOffset(fit.left.roundToInt(), fit.top.roundToInt()),
            dstSize = IntSize(fit.width.roundToInt().coerceAtLeast(1), fit.height.roundToInt().coerceAtLeast(1)))
        val pins = points.map { Offset(fit.left + it.x.toFloat() * fit.width, fit.top + it.y.toFloat() * fit.height) }
        pins.chunked(2).forEachIndexed { index, line ->
            val ink = if (index == 0) Color(0xFFD7BCF8) else Color.White
            if (line.size == 2) {
                drawLine(Color.Black, line[0], line[1], strokeWidth = 5.dp.toPx())
                drawLine(ink, line[0], line[1], strokeWidth = 2.dp.toPx())
            }
            for (pin in line) {
                drawCircle(Color.Black, 7.dp.toPx(), pin)
                drawCircle(ink, 5.dp.toPx(), pin)
            }
        }
    }
}

@Composable
private fun MeasureLevel(tilt: PhoneTilt?, label: String?, onReading: (PhoneTilt?) -> Unit) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val manager = remember(context) { context.getSystemService(SensorManager::class.java) }
    val sensor = remember(manager) { manager?.getDefaultSensor(Sensor.TYPE_GRAVITY) ?: manager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) }
    val update by rememberUpdatedState(onReading)
    var available by remember { mutableStateOf(sensor != null) }
    DisposableEffect(manager, sensor, lifecycle) {
        val activeSensor = sensor
        val service = manager
        if (activeSensor == null || service == null) return@DisposableEffect onDispose {}
        val filtered = FloatArray(3)
        var initialized = false
        val listener = object : SensorEventListener {
            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
            override fun onSensorChanged(event: SensorEvent) {
                val active = lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
                if (!active) return
                val values = event.values
                val totalSquared = values.take(3).sumOf { (it * it).toDouble() }
                if (totalSquared !in 55.0..150.0) return
                for (i in filtered.indices) filtered[i] = if (initialized) filtered[i] * .85f + values[i] * .15f else values[i]
                initialized = true
                val x = filtered[0].toDouble()
                val y = filtered[1].toDouble()
                val z = filtered[2].toDouble()
                update(PhoneTilt(Math.toDegrees(atan2(x, sqrt(y * y + z * z))), Math.toDegrees(atan2(y, sqrt(x * x + z * z)))))
            }
        }
        fun register() { available = service.registerListener(listener, activeSensor, SensorManager.SENSOR_DELAY_UI) }
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> register()
                Lifecycle.Event.ON_PAUSE -> { service.unregisterListener(listener); initialized = false; update(null) }
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer); service.unregisterListener(listener) }
    }
    Text(stringResource(R.string.halo11_tool_measure_level_hint))
    if (!available) Text(stringResource(R.string.halo11_tool_measure_level_unavailable))
    else {
        val outline = MaterialTheme.colorScheme.outline
        val bubble = if (tilt != null && hypot(tilt.side, tilt.forward) < 1.0) Color(0xFF75C8AC)
            else MaterialTheme.colorScheme.primary
        Canvas(Modifier.fillMaxWidth().height(190.dp)) {
            val radius = minOf(size.width, size.height) * .44f
            drawCircle(outline, radius, style = Stroke(2.dp.toPx()))
            drawLine(outline, center - Offset(radius, 0f), center + Offset(radius, 0f))
            drawLine(outline, center - Offset(0f, radius), center + Offset(0f, radius))
            if (tilt != null) {
                val x = (tilt.side / 25.0).coerceIn(-1.0, 1.0).toFloat()
                val y = (tilt.forward / 25.0).coerceIn(-1.0, 1.0).toFloat()
                val length = hypot(x.toDouble(), y.toDouble()).coerceAtLeast(1.0).toFloat()
                drawCircle(bubble, 12.dp.toPx(), center + Offset(x, -y) * (radius * .76f / length))
            }
        }
        Text(label ?: stringResource(R.string.halo11_tool_measure_level_wait), style = MaterialTheme.typography.titleMedium)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MeasureRoom(walls: List<RoomWall>, outline: List<MeasurementPoint>, label: String?, enabled: Boolean, onWalls: (List<RoomWall>) -> Unit) {
    var wall by rememberSaveable { mutableStateOf("") }
    var width by rememberSaveable { mutableStateOf("") }
    var depth by rememberSaveable { mutableStateOf("") }
    val wallLength = wall.decimal()?.takeIf(HaloMeasurement::validWall)
    val rectWidth = width.decimal()?.takeIf(HaloMeasurement::validWall)
    val rectDepth = depth.decimal()?.takeIf(HaloMeasurement::validWall)
    Text(stringResource(R.string.halo11_tool_measure_room_hint))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(width, { width = it.take(12) }, Modifier.weight(1f), singleLine = true, enabled = enabled,
            label = { Text(stringResource(R.string.halo11_tool_measure_dimensions)) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
        OutlinedTextField(depth, { depth = it.take(12) }, Modifier.weight(1f), singleLine = true, enabled = enabled,
            label = { Text(stringResource(R.string.halo11_tool_measure_depth)) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
    }
    TextButton({
        if (rectWidth != null && rectDepth != null) onWalls(listOf(rectWidth, rectDepth, rectWidth, rectDepth).map { RoomWall(it, false) })
    }, enabled = enabled && rectWidth != null && rectDepth != null) { Text(stringResource(R.string.halo11_tool_measure_rect)) }
    if (walls.isNotEmpty()) {
        val ink = MaterialTheme.colorScheme.primary
        Canvas(Modifier.fillMaxWidth().height(195.dp)) {
            val plot = measurePlot(outline, size.width, size.height)
            plot.zipWithNext().forEach { (start, end) -> drawLine(ink, start, end, strokeWidth = 3.dp.toPx()) }
            plot.firstOrNull()?.let { drawCircle(ink, 5.dp.toPx(), it) }
            plot.lastOrNull()?.let { drawCircle(ink, 4.dp.toPx(), it) }
        }
        label?.let { Text(it) }
    }
    OutlinedTextField(wall, { wall = it.take(12) }, Modifier.fillMaxWidth(), singleLine = true, enabled = enabled,
        label = { Text(stringResource(R.string.halo11_tool_measure_wall)) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        TextButton({ wallLength?.let { onWalls(walls + RoomWall(it, true)); wall = "" } },
            enabled = enabled && wallLength != null && walls.size < 120) { Text(stringResource(R.string.halo11_tool_measure_left)) }
        TextButton({ wallLength?.let { onWalls(walls + RoomWall(it, false)); wall = "" } },
            enabled = enabled && wallLength != null && walls.size < 120) { Text(stringResource(R.string.halo11_tool_measure_right)) }
        TextButton({ onWalls(walls.dropLast(1)) }, enabled = enabled && walls.isNotEmpty()) { Text(stringResource(R.string.halo11_tool_measure_undo)) }
    }
}

private fun measurePlot(points: List<MeasurementPoint>, width: Float, height: Float): List<Offset> {
    if (points.isEmpty()) return emptyList()
    val left = points.minOf { it.x }
    val top = points.minOf { it.y }
    val w = (points.maxOf { it.x } - left).coerceAtLeast(.01)
    val h = (points.maxOf { it.y } - top).coerceAtLeast(.01)
    val scale = minOf(width / w, height / h) * .8
    val dx = (width - w * scale) / 2
    val dy = (height - h * scale) / 2
    return points.map { Offset((dx + (it.x - left) * scale).toFloat(), (dy + (it.y - top) * scale).toFloat()) }
}

private suspend fun keepMeasurement(context: Context, title: String, instrument: String, summary: String, image: Bitmap?,
                                    points: List<MeasurementPoint>, outline: List<MeasurementPoint>, authorize: () -> Unit) {
    var staged: File? = null
    try {
        val file = withContext(Dispatchers.IO) {
            authorize()
            val folder = File(context.filesDir, "workspace/owned/measure-staging")
            check(folder.isDirectory || folder.mkdirs())
            val destination = File.createTempFile("measurement-", ".jpg", folder)
            staged = destination
            val result = measurementReceipt(instrument, summary, image, points, outline)
            try { destination.outputStream().use { check(result.compress(Bitmap.CompressFormat.JPEG, 91, it)) } }
            finally { result.recycle() }
            destination
        }
        authorize()
        Graph.workspaceUseCases.importForCanvas(captureFileUri(context, file), title + " · " + instrument + ".jpg", "image/jpeg")
    } finally { withContext(NonCancellable + Dispatchers.IO) { staged?.delete() } }
}

private fun measurementReceipt(instrument: String, summary: String, image: Bitmap?, points: List<MeasurementPoint>,
                               outline: List<MeasurementPoint>): Bitmap {
    val wide = image?.width?.coerceAtLeast(700) ?: 950
    val pictureHeight = image?.height ?: if (outline.isEmpty()) 0 else 500
    val footer = Bitmap.createBitmap(wide, pictureHeight + 132, Bitmap.Config.ARGB_8888)
    val canvas = android.graphics.Canvas(footer)
    canvas.drawColor(android.graphics.Color.rgb(247, 244, 251))
    val pen = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply { color = android.graphics.Color.rgb(33, 28, 47) }
    if (image != null) {
        val inset = (wide - image.width) / 2f
        canvas.drawBitmap(image, inset, 0f, pen)
        points.chunked(2).forEachIndexed { index, pair ->
            pen.color = if (index == 0) android.graphics.Color.rgb(199, 149, 255) else android.graphics.Color.WHITE
            pen.strokeWidth = 6f
            val pins = pair.map { Offset(inset + (it.x * image.width).toFloat(), (it.y * image.height).toFloat()) }
            if (pins.size == 2) canvas.drawLine(pins[0].x, pins[0].y, pins[1].x, pins[1].y, pen)
            for (p in pins) canvas.drawCircle(p.x, p.y, 9f, pen)
        }
    } else if (outline.isNotEmpty()) {
        pen.strokeWidth = 5f
        measurePlot(outline, wide.toFloat(), pictureHeight.toFloat()).zipWithNext().forEach { (first, last) ->
            canvas.drawLine(first.x, first.y, last.x, last.y, pen)
        }
    }
    pen.color = android.graphics.Color.rgb(33, 28, 47)
    pen.textSize = 29f
    canvas.drawText(instrument, 26f, pictureHeight + 46f, pen)
    pen.textSize = 25f
    val inches = pen.measureText(summary)
    if (inches > wide - 52) pen.textSize *= (wide - 52) / inches
    canvas.drawText(summary, 26f, pictureHeight + 92f, pen)
    return footer
}

private fun String.decimal(): Double? = trim().replace(',', '.').toDoubleOrNull()?.takeIf { it.isFinite() }
private fun measurementNumber(number: Double, locale: Locale): String = String.format(locale, "%.2f", number)
