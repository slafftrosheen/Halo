package com.halo.app.ui.toolkit

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.halo.app.R
import com.halo.app.di.ManualDependencyGraph as Graph
import com.halo.app.language.uiText
import com.halo.app.ui.icons.HaloGlyph
import com.halo.app.ui.icons.HaloIcon
import com.halo.app.ui.presence.BindShellPageActions
import com.halo.app.ui.presence.ShellPageAction
import com.halo.app.ui.presence.ShellPageOwner
import com.halo.app.ui.screens.notebook.ToolZoneHeader
import com.halo.app.ui.zone6.rememberZone6PageSetting
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlin.math.min

enum class HaloScanMode(val title: String, val easyTitle: String, val icon: HaloGlyph) {
    READ("Read", "Read it to me", HaloGlyph.SPEAK), DOCUMENT("Document", "Keep a paper", HaloGlyph.FILE),
    TRANSLATE("Translate", "Translate", HaloGlyph.LANGUAGE), IDENTIFY("What’s this?", "What’s this?", HaloGlyph.QUESTION),
}

private val scanYellow = Color(0xFFF5D65D)
private val scanWarm = Color(0xFFFFE9C9)
private val scanLilac = Color(0xFFD8BEFF)
private val warningWords = Regex("\\b(?:warning|danger|caution|allergens?|peanuts?|tree nuts?|wheat|soy|milk|eggs?|sesame|shellfish)\\b", RegexOption.IGNORE_CASE)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun HaloScanScreen(
    onBack: () -> Unit,
    onOpen: (String) -> Unit,
    magnifier: Boolean = false,
    kidsMode: Boolean = false,
    childSource: HaloScanChildSource? = null,
    initialMode: HaloScanMode = HaloScanMode.READ,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val route = if (kidsMode) "kids_scan" else if (magnifier) "magnifier" else if (initialMode == HaloScanMode.TRANSLATE) "translate" else "scan"
    val appearance = rememberZone6PageSetting("$route.appearance")
    val reader = Graph.readAloud
    val transport by reader.state.collectAsState()
    var readerToken by remember { mutableStateOf<String?>(null) }
    var readerStart by remember { mutableIntStateOf(0) }
    var readDocument by remember { mutableStateOf("") }
    val ownedReader = transport?.takeIf { it.sessionToken == readerToken }
    var mode by rememberSaveable(initialMode, magnifier, kidsMode) {
        mutableStateOf(if (kidsMode) HaloScanMode.IDENTIFY else if (magnifier) HaloScanMode.READ else initialMode)
    }
    var easy by rememberSaveable { mutableStateOf(appearance.value?.contains("easy") == true) }
    var showGuide by rememberSaveable { mutableStateOf(false) }
    var showOptions by rememberSaveable { mutableStateOf(false) }
    var highContrast by rememberSaveable { mutableStateOf(appearance.value?.contains("contrast") == true) }
    var zoom by rememberSaveable { mutableFloatStateOf(if (magnifier) 2f else 1f) }
    var from by rememberSaveable { mutableStateOf("en") }
    var to by rememberSaveable { mutableStateOf("lv") }
    var permission by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
    }
    var permissionDeclined by remember { mutableStateOf(false) }
    var preview by remember { mutableStateOf<PreviewView?>(null) }
    var available by remember { mutableStateOf(false) }
    var cameraFailed by remember { mutableStateOf(false) }
    var frame by remember { mutableStateOf<Bitmap?>(null) }
    var lines by remember { mutableStateOf(emptyList<HaloScanLine>()) }
    var translated by remember { mutableStateOf(emptyList<HaloScanLine>()) }
    val reminderCandidates = remember(lines, kidsMode) { if (kidsMode) emptyList() else scanReminderTimes(lines) }
    var handoff by remember { mutableStateOf(false) }
    var label by remember { mutableStateOf<String?>(null) }
    var savedId by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf<String?>(null) }
    var needsModel by remember { mutableStateOf(false) }
    var confirmCloudFrame by remember { mutableStateOf(false) }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        permission = it
        permissionDeclined = !it
    }
    DisposableEffect(reader) {
        onDispose { readerToken?.let(reader::stopSession) }
    }

    fun read(text: String, start: Int = 0, language: String? = null) {
        val position = start.coerceIn(0, text.length)
        if (text.substring(position).isBlank()) return
        readerStart = position
        readDocument = text
        readerToken = reader.start("scan", text.substring(position), Graph.speechSynthesisRegistry.selector.ttsSelection.value.id,
            cloudApproved = false, language = language)
    }

    fun reset() {
        readerToken?.let(reader::stopSession)
        readerToken = null
        readDocument = ""
        frame = null
        lines = emptyList()
        translated = emptyList()
        label = null
        savedId = null
        error = null
        needsModel = false
    }

    fun translate(download: Boolean) {
        if (busy != null || lines.isEmpty()) return
        val original = lines
        val (source, target) = from to to
        busy = if (download) "Downloading the language pack over Wi-Fi" else "Translating on this phone"
        error = null
        scope.launch {
            try {
                needsModel = !download && !scanTranslationReady(source, target) && source != target
                if (!needsModel) translated = translateScanLines(original, source, target, download)
            } catch (cancel: CancellationException) { throw cancel }
            catch (_: Exception) {
                error = if (download) "Language pack unavailable. Connect to Wi-Fi and try again." else "Could not translate the text. Try again."
            } finally { busy = null }
        }
    }

    fun capture(fromCloudConfirmation: Boolean = false) {
        if (busy != null) return
        if (!permission) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
            return
        }
        if (kidsMode && childSource == null) {
            error = "Ask a grown-up to choose an identification source."
            return
        }
        val snapshot = frame ?: preview?.scanBitmap()
        if (snapshot == null) {
            error = "The camera is getting ready. Try again in a moment."
            return
        }
        frame = snapshot
        error = null
        if (kidsMode && childSource?.usesCloud == true && !fromCloudConfirmation) {
            confirmCloudFrame = true
            return
        }
        if (mode == HaloScanMode.DOCUMENT) return
        busy = if (mode == HaloScanMode.IDENTIFY) "Looking closely" else "Hold still — reading"
        scope.launch {
            try {
                if (mode == HaloScanMode.IDENTIFY) {
                    label = if (kidsMode) safeChildLabel(childSource?.identify(snapshot)) else labelFrame(snapshot)
                    if (label == null) error = "I’m not sure. Try getting a little closer."
                } else {
                    lines = scanFrameText(snapshot)
                    if (lines.isEmpty()) error = "No text found. Try more light or move a little closer."
                }
            } catch (cancel: CancellationException) { throw cancel }
            catch (_: Exception) { error = "Could not read this frame. Try again." }
            finally { busy = null }
        }
    }

    fun keepPaper() {
        val snapshot = frame ?: return
        if (busy != null || savedId != null) return
        busy = "Keeping the paper"
        error = null
        scope.launch {
            try { savedId = keepScanPdf(context, snapshot) }
            catch (cancel: CancellationException) { throw cancel }
            catch (_: Exception) { error = "Could not keep the paper. Try again." }
            finally { busy = null }
        }
    }

    val title = uiText(if (magnifier) "Magnifier" else if (kidsMode) "What’s this?" else "HaloScan")
    val sourceLabel = if (kidsMode && childSource?.usesCloud == true) "Grown-up approved online source" else "On this phone"
    val canUseCamera = !kidsMode || childSource != null
    val scanText = lines.groupBy(HaloScanLine::paragraph).values.joinToString("\n\n") { paragraph -> paragraph.joinToString(" ") { it.text } }
    val nativeChildLabel = if (kidsMode && label != null) uiText(label.orEmpty()) else null
    val childSentence = if (nativeChildLabel != null) uiText(kidsObjectClue(label.orEmpty())) else null
    val childTitle = nativeChildLabel?.let { uiText("It looks like %s!", it) }
    val childSpeech = listOfNotNull(childTitle, childSentence).joinToString(" ")
    val primaryLabel = when {
        !permission -> "Allow camera"
        busy != null -> busy.orEmpty()
        mode == HaloScanMode.DOCUMENT && frame == null -> "Capture paper"
        mode == HaloScanMode.DOCUMENT && savedId == null -> "Keep paper"
        mode == HaloScanMode.DOCUMENT -> "Open paper"
        kidsMode && label != null && (ownedReader?.playing == true || ownedReader?.preparing == true) -> "Pause"
        kidsMode && label != null -> "Hear it"
        mode == HaloScanMode.IDENTIFY && frame != null -> "Look again"
        mode == HaloScanMode.IDENTIFY -> "What’s this?"
        mode == HaloScanMode.TRANSLATE && frame == null -> "Capture text"
        mode == HaloScanMode.TRANSLATE && lines.isEmpty() -> "Try again"
        mode == HaloScanMode.TRANSLATE && needsModel -> "Download language pack"
        mode == HaloScanMode.TRANSLATE && translated.isNotEmpty() -> "Hear translation"
        mode == HaloScanMode.TRANSLATE -> "Translate"
        frame == null && magnifier -> "Freeze"
        scanText.isEmpty() -> "Read"
        ownedReader?.playing == true || ownedReader?.preparing == true -> "Pause"
        ownedReader != null && ownedReader.notice == null -> "Resume"
        else -> "Read aloud"
    }
    val primaryEnabled = busy == null && canUseCamera && (!permission || frame != null || available)
    val primary: () -> Unit = {
        when {
            !permission -> permissionLauncher.launch(Manifest.permission.CAMERA)
            mode == HaloScanMode.DOCUMENT && savedId != null -> onOpen("notebook/file/${android.net.Uri.encode(savedId.orEmpty())}")
            mode == HaloScanMode.DOCUMENT && frame != null -> keepPaper()
            kidsMode && label != null && ownedReader?.notice == null && ownedReader != null -> reader.toggle()
            kidsMode && label != null -> read(childSpeech)
            mode == HaloScanMode.IDENTIFY && frame != null -> reset()
            mode == HaloScanMode.TRANSLATE && translated.isNotEmpty() -> read(translated.joinToString("\n\n") { it.text }, language = to)
            mode == HaloScanMode.TRANSLATE && lines.isNotEmpty() -> translate(needsModel)
            mode == HaloScanMode.TRANSLATE && frame != null -> reset()
            scanText.isNotEmpty() && ownedReader != null && ownedReader.notice == null -> reader.toggle()
            scanText.isNotEmpty() -> read(scanText)
            magnifier && frame == null -> frame = preview?.scanBitmap()
            else -> capture()
        }
    }
    val pageActions = buildList {
        add(ShellPageAction("page.$route.primary", uiText(primaryLabel), HaloGlyph.CAMERA, primaryEnabled && !easy, primary))
        if (!kidsMode) add(ShellPageAction("page.$route.appearance", uiText("Customize"), HaloGlyph.SETTINGS, busy == null) { showOptions = true })
    }
    BindShellPageActions(ShellPageOwner(route, statusLabel = uiText(sourceLabel), guidanceBlocked = busy != null), pageActions)

    Column(Modifier.fillMaxSize()) {
        ToolZoneHeader(title, onBack, onTitleLongClick = if (kidsMode) null else ({ showOptions = true })) {
            TextButton(onClick = { showGuide = true }, modifier = Modifier.heightIn(min = 48.dp)) { Text(uiText("Show me")) }
        }
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (!kidsMode && !magnifier) {
                if (easy) {
                    Text(uiText("Just point the phone."), style = MaterialTheme.typography.headlineSmall)
                    listOf(listOf(HaloScanMode.READ, HaloScanMode.TRANSLATE),
                        listOf(HaloScanMode.DOCUMENT, HaloScanMode.IDENTIFY)).forEach { choices ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            choices.forEach { option ->
                                val filled = option == HaloScanMode.READ
                                Surface(onClick = { reset(); mode = option; easy = false }, enabled = busy == null,
                                    shape = RoundedCornerShape(20.dp), color = if (filled) scanYellow else MaterialTheme.colorScheme.surfaceContainerHigh,
                                    contentColor = if (filled) Color(0xFF272114) else MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.weight(1f).heightIn(min = 132.dp)) {
                                    Column(Modifier.fillMaxWidth().padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterVertically)) {
                                        HaloIcon(option.icon, null, Modifier.size(30.dp))
                                        Text(uiText(option.easyTitle), style = MaterialTheme.typography.titleMedium)
                                    }
                                }
                            }
                        }
                    }
                } else {
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                        HaloScanMode.entries.forEach { option ->
                            FilterChip(selected = mode == option, onClick = { if (mode != option) { reset(); mode = option } },
                                label = { Text(uiText(option.title)) }, enabled = busy == null)
                        }
                    }
                }
            }
            if (!easy || magnifier || kidsMode) {
                if (canUseCamera) ScanViewfinder(
                    frame, zoom, highContrast, translated, permission, cameraFailed, kidsMode,
                    onView = { preview = it }, onAvailable = { available = it }, onFailure = { cameraFailed = true },
                    onReadTranslation = { read(it, language = to) },
                )
                if (!canUseCamera) Text(uiText("Ask a grown-up to choose an identification source."), style = MaterialTheme.typography.titleLarge)
                else if (permissionDeclined && !permission) Text(uiText("Allow camera access to point HaloScan at a page or object."))
                if (canUseCamera && (magnifier || mode == HaloScanMode.READ)) {
                    if (magnifier && frame == null) {
                        Text(uiText("Zoom %s×", zoom.toInt()), style = MaterialTheme.typography.labelLarge)
                        Slider(value = zoom, onValueChange = { zoom = it }, valueRange = 1f..6f, steps = 4,
                            modifier = Modifier.fillMaxWidth())
                    }
                    if (scanText.isNotEmpty()) {
                        val spoken = if (readDocument == scanText && ownedReader != null) ownedReader.range?.let { range ->
                            sectionOffset(scanText, readerStart, ownedReader.sections, ownedReader.index)?.let { offset ->
                                (offset + range.first)..(offset + range.last)
                            }
                        } else null
                        ScanReadableText(scanText, spoken, highContrast) { read(scanText, it) }
                        if (magnifier) Text(uiText("Highlighted words can help you check a label."),
                            style = MaterialTheme.typography.bodySmall)
                    }
                }
                if (mode == HaloScanMode.TRANSLATE) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ScanLanguageMenu(uiText("From"), from, busy == null) { from = it; translated = emptyList(); needsModel = false }
                        ScanLanguageMenu(uiText("To"), to, busy == null) { to = it; translated = emptyList(); needsModel = false }
                    }
                    if (needsModel) Text(uiText("A language pack needs Wi-Fi. Your photo and text stay on this phone."))
                    if (translated.isNotEmpty()) Text(uiText("Tap a label to hear it."), style = MaterialTheme.typography.bodyMedium)
                }
                if (label != null) Surface(shape = RoundedCornerShape(24.dp), color = scanWarm, contentColor = Color(0xFF392C20)) {
                    Column(Modifier.fillMaxWidth().padding(20.dp).semantics { liveRegion = LiveRegionMode.Polite }) {
                        val headline = if (kidsMode) "${kidsObjectIcon(label.orEmpty())}  $childTitle" else uiText("This looks like %s.", label.orEmpty())
                        Text(headline, fontSize = 26.sp, lineHeight = 32.sp)
                        if (childSentence != null) Text(childSentence, style = MaterialTheme.typography.bodyLarge)
                    }
                }
                if (reminderCandidates.isNotEmpty()) TextButton({ handoff = true }, enabled = busy == null,
                    modifier = Modifier.heightIn(min = 48.dp)) {
                    Text(stringResource(R.string.halo_scan_to_planner))
                }
                if (savedId != null) Text(uiText("Paper kept in your Inbox."), style = MaterialTheme.typography.titleMedium)
                if (canUseCamera) Text(uiText(if (label != null && kidsMode && childSource?.usesCloud == true) "Used online" else sourceLabel),
                    style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                val status = error ?: busy ?: ownedReader?.notice
                if (status != null) Text(uiText(status), Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (error != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (!easy || magnifier || kidsMode) Surface(tonalElevation = 2.dp) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (frame != null && mode != HaloScanMode.IDENTIFY) TextButton(enabled = busy == null, onClick = ::reset) {
                        Text(uiText(if (magnifier) "Unfreeze" else "New scan"))
                    }
                    if (!kidsMode && (magnifier || mode == HaloScanMode.READ)) FilterChip(
                        selected = highContrast, onClick = { highContrast = !highContrast }, label = { Text(uiText("High contrast")) })
                    if (ownedReader != null) TextButton(onClick = { reader.speed(.75) }) { Text(uiText("Slower")) }
                    if (kidsMode && frame != null) TextButton(onClick = ::reset, enabled = busy == null) { Text(uiText("Again")) }
                    if (!kidsMode && !magnifier) TextButton(enabled = busy == null, onClick = { easy = true }) { Text(uiText("Easy")) }
                    if (savedId != null) TextButton(onClick = { onOpen("notebook") }) { Text(uiText("Send it to Notebook")) }
                }
                Button(
                    onClick = primary,
                    enabled = primaryEnabled,
                    modifier = Modifier.fillMaxWidth().heightIn(min = if (kidsMode) 64.dp else 52.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = if (kidsMode) Color(0xFFFFA776) else scanYellow, contentColor = Color(0xFF272114)),
                ) { Text(uiText(primaryLabel)) }
            }
        } else TextButton(onClick = { easy = false }, modifier = Modifier.align(Alignment.CenterHorizontally)) { Text(uiText("Standard controls")) }
    }

    if (handoff && reminderCandidates.isNotEmpty() && !kidsMode) {
        val source = stringResource(R.string.halo_scan_source)
        AlertDialog(onDismissRequest = { handoff = false },
            title = { Text(stringResource(R.string.halo_scan_to_planner)) },
            text = {
                Column(Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState())) {
                    Text(stringResource(R.string.halo_scan_to_planner_hint))
                    reminderCandidates.forEach { (line, time) ->
                        TextButton({
                            if (HaloPlannerHandoff.propose(line, time, source)) {
                                handoff = false
                                onOpen("planner")
                            }
                        }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                            Text(time.toString() + " · " + line)
                        }
                    }
                }
            },
            confirmButton = { TextButton({ handoff = false }) { Text(stringResource(android.R.string.cancel)) } })
    }

    if (showGuide) AlertDialog(
        onDismissRequest = { showGuide = false },
        title = { Text(uiText("Show me")) },
        text = {
            Text(uiText(when {
                kidsMode -> "Point the camera at a thing you know. Tap What’s this? to see what Halo can find."
                magnifier -> "Point at small print. Slide to zoom, then tap Freeze. Tap Read to turn the image into words."
                else -> "Point the camera at the page. Choose Read, Document, Translate, or What’s this? and tap the yellow button."
            }))
        },
        confirmButton = { TextButton(onClick = { showGuide = false }) { Text(uiText("Try it")) } },
    )
    if (showOptions) AlertDialog(
        onDismissRequest = { showOptions = false },
        title = { Text(uiText("Customize")) },
        text = {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(uiText("High contrast"), Modifier.weight(1f))
                    Switch(checked = highContrast, onCheckedChange = { highContrast = it })
                }
                if (!magnifier) Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(uiText("Easy"), Modifier.weight(1f))
                    Switch(checked = easy, onCheckedChange = { easy = it })
                }
            }
        },
        confirmButton = { TextButton(onClick = {
            val chosen = listOfNotNull("easy".takeIf { easy }, "contrast".takeIf { highContrast }).joinToString(",")
            scope.launch { if (appearance.apply(appearance.value, chosen, "")) showOptions = false }
        }) { Text(uiText("Done")) } },
    )
    if (confirmCloudFrame) AlertDialog(
        onDismissRequest = { confirmCloudFrame = false; reset() },
        title = { Text(uiText("Use the approved online source?")) },
        text = { Text(uiText("This camera picture will be sent to %s, the online source your grown-up chose.",
            childSource?.cloudName ?: uiText("the online source"))) },
        confirmButton = { TextButton(onClick = { confirmCloudFrame = false; capture(true) }) { Text(uiText("Look online")) } },
        dismissButton = { TextButton(onClick = { confirmCloudFrame = false; reset() }) { Text(uiText("Cancel")) } },
    )
}

@Composable
private fun ScanViewfinder(
    frame: Bitmap?,
    zoom: Float,
    highContrast: Boolean,
    translated: List<HaloScanLine>,
    permission: Boolean,
    failed: Boolean,
    kidsMode: Boolean,
    onView: (PreviewView?) -> Unit,
    onAvailable: (Boolean) -> Unit,
    onFailure: () -> Unit,
    onReadTranslation: (String) -> Unit,
) {
    BoxWithConstraints(
        Modifier.fillMaxWidth().heightIn(min = 220.dp).aspectRatio(1.35f)
            .clip(RoundedCornerShape(22.dp)).background(if (kidsMode) scanWarm else Color(0xFF211E19)),
        contentAlignment = Alignment.Center,
    ) {
        if (frame != null) {
            val contrast = if (highContrast) ColorFilter.colorMatrix(ColorMatrix(scanContrastMatrix)) else null
            Image(frame.asImageBitmap(), uiText("Captured camera frame"), Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit, colorFilter = contrast)
            if (translated.isNotEmpty()) {
                val frameWidth = maxWidth.value
                val ratio = min(frameWidth / frame.width, maxHeight.value / frame.height)
                val left = (maxWidth.value - frame.width * ratio) / 2
                val top = (maxHeight.value - frame.height * ratio) / 2
                Box(Modifier.fillMaxSize()) {
                    translated.forEach { row ->
                        val anchor = row.position ?: return@forEach
                        val width = (frameWidth - left - anchor.left * ratio).coerceIn(min(40f, frameWidth), frameWidth)
                        Text(row.text, Modifier.offset((left + anchor.left * ratio).dp, (top + anchor.top * ratio).dp)
                            .widthIn(max = width.dp).background(scanYellow, RoundedCornerShape(6.dp))
                            .clickable { onReadTranslation(row.text) }.padding(horizontal = 6.dp, vertical = 3.dp),
                            color = Color(0xFF272114), maxLines = 1, overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        } else if (permission && !failed) {
            HaloScanCamera(zoom, highContrast, onView, onAvailable, onFailure, Modifier.fillMaxSize())
            Canvas(Modifier.fillMaxSize().padding(18.dp)) {
                val edge = 25.dp.toPx()
                val stroke = 3.dp.toPx()
                listOf(Offset.Zero to Size(1f, 1f), Offset(size.width, 0f) to Size(-1f, 1f),
                    Offset(0f, size.height) to Size(1f, -1f), Offset(size.width, size.height) to Size(-1f, -1f))
                    .forEach { (corner, direction) ->
                        drawLine(scanYellow, corner, corner + Offset(direction.width * edge, 0f), stroke)
                        drawLine(scanYellow, corner, corner + Offset(0f, direction.height * edge), stroke)
                    }
            }
        } else Text(uiText(if (failed) "Camera unavailable" else "Camera preview"), color = Color.White)
    }
}

private fun sectionOffset(text: String, start: Int, parts: List<String>, index: Int): Int? {
    var next = start
    for (at in 0..index) {
        val part = parts.getOrNull(at) ?: return null
        val offset = text.indexOf(part, next).takeIf { it >= 0 } ?: return null
        if (at == index) return offset
        next = offset + part.length
    }
    return null
}

private fun kidsObjectIcon(label: String) = when (label) {
    "leaf" -> "🍃"
    "tree" -> "🌳"
    "bird" -> "🐦"
    "flower" -> "🌼"
    "cat" -> "🐈"
    "dog" -> "🐕"
    "book" -> "📗"
    "ball" -> "⚽"
    "apple" -> "🍎"
    "rock" -> "🪨"
    "fish" -> "🐟"
    "rainbow" -> "🌈"
    else -> "🔎"
}

private fun kidsObjectClue(label: String) = when (label) {
    "leaf" -> "A leaf helps a plant use sunlight to make food."
    "tree" -> "Trees grow new leaves and branches as they get bigger."
    "bird" -> "Birds have feathers."
    "flower" -> "Some flowers make seeds after they bloom."
    "book" -> "A book can use pictures and words to tell a story."
    "rainbow" -> "A rainbow can form when sunlight passes through drops of water."
    else -> "Look closely at its shape and colors."
}

@Composable
private fun ScanReadableText(text: String, spoken: IntRange?, highContrast: Boolean, onRead: (Int) -> Unit) {
    var layout by remember(text) { mutableStateOf<TextLayoutResult?>(null) }
    val readAt by rememberUpdatedState(onRead)
    val highlighted = buildAnnotatedString {
        append(text)
        warningWords.findAll(text).forEach { match ->
            addStyle(SpanStyle(color = Color(0xFF272114), background = scanYellow), match.range.first, match.range.last + 1)
        }
        spoken?.let {
            val from = it.first.coerceIn(0, text.length)
            val to = (it.last + 1).coerceIn(from, text.length)
            if (from < to) addStyle(SpanStyle(color = Color(0xFF231738), background = scanLilac), from, to)
        }
    }
    Surface(color = if (highContrast) Color.Black else MaterialTheme.colorScheme.surfaceContainer,
        contentColor = if (highContrast) Color.White else MaterialTheme.colorScheme.onSurface, shape = RoundedCornerShape(18.dp)) {
        Text(highlighted,
            Modifier.fillMaxWidth().padding(16.dp).pointerInput(text) {
                detectTapGestures { spot ->
                    val position = layout?.getOffsetForPosition(spot) ?: return@detectTapGestures
                    val word = layout?.getWordBoundary(position.coerceIn(0, text.lastIndex))
                    readAt(word?.start ?: position)
                }
            },
            fontSize = if (highContrast) 23.sp else 19.sp, lineHeight = 30.sp, onTextLayout = { layout = it })
    }
}

@Composable
private fun ScanLanguageMenu(title: String, selected: String, enabled: Boolean, onSelect: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(onClick = { expanded = true }, enabled = enabled) {
            val current = scanLanguages.first { it.code == selected }
            Text("$title · ${uiText(current.name)}")
        }
        DropdownMenu(expanded, { expanded = false }) {
            scanLanguages.forEach { language ->
                DropdownMenuItem(text = { Text(uiText(language.name)) }, onClick = { expanded = false; onSelect(language.code) })
            }
        }
    }
}
