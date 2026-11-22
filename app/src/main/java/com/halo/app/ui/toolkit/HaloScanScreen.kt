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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
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
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
    initialFrom: String? = null,
    initialTo: String? = null,
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
    var from by rememberSaveable(initialFrom) {
        mutableStateOf(initialFrom?.takeIf { code -> scanLanguages.any { it.code == code } } ?: "en")
    }
    var to by rememberSaveable(initialTo) {
        mutableStateOf(initialTo?.takeIf { code -> scanLanguages.any { it.code == code } } ?:
            if (initialMode == HaloScanMode.TRANSLATE) "ru" else "lv")
    }
    var permission by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
    }
    var permissionDeclined by remember { mutableStateOf(false) }
    var importImage by remember { mutableStateOf<android.net.Uri?>(null) }
    var importPdf by remember { mutableStateOf<android.net.Uri?>(null) }
    var chooseImport by rememberSaveable { mutableStateOf(false) }
    var pdfWasLimited by remember { mutableStateOf(false) }
    var showFoundText by rememberSaveable { mutableStateOf(false) }
    var paperPages by remember { mutableStateOf(emptyList<Bitmap>()) }
    var currentPaperPage by remember { mutableIntStateOf(0) }
    var paperSource by remember { mutableStateOf<Bitmap?>(null) }
    var pageTools by rememberSaveable { mutableStateOf(false) }
    var filterTools by rememberSaveable { mutableStateOf(false) }
    var cropTools by rememberSaveable { mutableStateOf(false) }
    var formFields by remember { mutableStateOf<List<ScanFormField>?>(null) }
    var signTools by remember { mutableStateOf(false) }
    var preview by remember { mutableStateOf<PreviewView?>(null) }
    var available by remember { mutableStateOf(false) }
    var cameraFailed by remember { mutableStateOf(false) }
    var frame by remember { mutableStateOf<Bitmap?>(null) }
    var lines by remember { mutableStateOf(emptyList<HaloScanLine>()) }
    var translated by remember { mutableStateOf(emptyList<HaloScanLine>()) }
    val translatedParagraphs = remember(translated) {
        translated.groupBy(HaloScanLine::paragraph).values.joinToString("\n\n") { paragraph ->
            paragraph.joinToString(" ") { it.text }
        }
    }
    val currentTranslatedParagraphs by rememberUpdatedState(translatedParagraphs)
    val reminderCandidates = remember(lines, kidsMode) { if (kidsMode) emptyList() else scanReminderTimes(lines) }
    var handoff by remember { mutableStateOf(false) }
    var label by remember { mutableStateOf<String?>(null) }
    var savedId by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf<String?>(null) }
    var translationError by remember { mutableStateOf(false) }
    val pdfTitle = stringResource(R.string.toolkit_translated_scan_title)
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val translationDraft = remember(frame, translated, from, to, mode, kidsMode, pdfTitle) {
        val snapshot = frame
        if (!kidsMode && mode == HaloScanMode.TRANSLATE && snapshot != null && translated.any { it.text.isNotBlank() }) {
            runCatching { Graph.kidsPreferences.adultActionRevision() }.getOrNull()?.let { confirmation ->
                HaloScanTranslationDraft(context.applicationContext, snapshot, translated.toList(), from, to, pdfTitle, confirmation)
            }
        } else null
    }
    val translationReceipt = translationDraft?.state?.collectAsState()?.value
    val keepingTranslation = translationReceipt?.phase == ScanTranslationPhase.KEEPING
    val scanEnabled = busy == null && !keepingTranslation
    LaunchedEffect(translationDraft, lifecycle) {
        val current = translationDraft ?: return@LaunchedEffect
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            HaloScanTranslationSessions.enter(current)
            try { awaitCancellation() }
            finally { HaloScanTranslationSessions.leave(current) }
        }
    }
    var needsModel by remember { mutableStateOf(false) }
    var confirmCloudFrame by remember { mutableStateOf(false) }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        permission = it
        permissionDeclined = !it
    }
    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (!kidsMode) importImage = uri
    }
    val pdfPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (!kidsMode) importPdf = uri
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
        paperSource = null
        lines = emptyList()
        translated = emptyList()
        label = null
        savedId = null
        error = null
        translationError = false
        needsModel = false
        pdfWasLimited = false
    }

    var usedVision by remember { mutableStateOf(false) }

    suspend fun readAdultFrame(bitmap: Bitmap, identify: Boolean) {
        val result = if (identify) scanWithVision(bitmap,
            "Identify the main object or scene in one short plain-language sentence. Say when uncertain.", 140)
        else readScanWithVision(bitmap)
        if (frame !== bitmap) return
        usedVision = result != null
        if (identify) label = result ?: labelFrame(bitmap)
        else lines = result?.let(::scanModelParagraphs) ?: scanFrameText(bitmap)
    }

    fun translate(download: Boolean) {
        if (busy != null || lines.isEmpty()) return
        val original = lines
        val snapshot = frame
        val (source, target) = from to to
        busy = if (download) "Downloading the language pack over Wi-Fi" else "Translating the scan"
        error = null
        scope.launch {
            try {
                val fromLanguage = scanLanguages.find { it.code == source }?.name ?: source
                val toLanguage = scanLanguages.find { it.code == target }?.name ?: target
                val modelText = if (download || snapshot == null || source == target) null else scanWithVision(snapshot,
                    "Read and translate the visible text from $fromLanguage to $toLanguage. Preserve paragraphs, numbers, names, " +
                        "and meaning. Mark illegible parts. Return the translation only.")
                if (modelText != null) {
                    usedVision = true
                    needsModel = false
                    translated = scanModelParagraphs(modelText)
                    return@launch
                }
                usedVision = false
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
        if (mode == HaloScanMode.DOCUMENT) {
            if (paperPages.size >= 8) { error = "This scan already has eight pages."; return }
            paperPages = paperPages + snapshot
            currentPaperPage = paperPages.lastIndex
            paperSource = snapshot
            return
        }
        busy = if (mode == HaloScanMode.IDENTIFY) "Looking closely" else "Hold still — reading"
        scope.launch {
            try {
                if (mode == HaloScanMode.IDENTIFY) {
                    if (kidsMode) label = safeChildLabel(childSource?.identify(snapshot)) else readAdultFrame(snapshot, identify = true)
                    if (label == null) error = "I’m not sure. Try getting a little closer."
                } else {
                    readAdultFrame(snapshot, identify = false)
                    if (lines.isEmpty()) error = "No text found. Try more light or move a little closer."
                }
            } catch (cancel: CancellationException) { throw cancel }
            catch (_: Exception) { error = "Could not read this frame. Try again." }
            finally { busy = null }
        }
    }

    fun keepPaper() {
        val snapshot = frame ?: return
        val pages = if (mode == HaloScanMode.DOCUMENT && paperPages.isNotEmpty()) paperPages else listOf(snapshot)
        if (busy != null || savedId != null) return
        busy = "Keeping the paper"
        error = null
        scope.launch {
            try { savedId = keepScanPdf(context, pages) }
            catch (cancel: CancellationException) { throw cancel }
            catch (_: Exception) { error = "Could not keep the paper. Try again." }
            finally { busy = null }
        }
    }

    fun updatePhoto(bitmap: Bitmap, keepSource: Boolean = false) {
        if (!keepSource) paperSource = bitmap
        frame = bitmap
        if (mode == HaloScanMode.DOCUMENT && currentPaperPage in paperPages.indices) {
            paperPages = paperPages.toMutableList().apply { set(currentPaperPage, bitmap) }
        }
        lines = emptyList(); translated = emptyList(); savedId = null; label = null
    }

    fun rotatePaper() {
        val bitmap = frame ?: return
        val rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height,
            android.graphics.Matrix().apply { postRotate(90f) }, true)
        updatePhoto(rotated)
    }

    fun switchPaper(index: Int) {
        val bitmap = paperPages.getOrNull(index) ?: return
        currentPaperPage = index
        frame = bitmap
        paperSource = bitmap
        lines = emptyList(); translated = emptyList()
    }

    fun prepareForm() {
        val snapshot = frame ?: return
        if (kidsMode || busy != null) return
        busy = "Finding form fields"
        scope.launch {
            try {
                // OCR supplies positions for the fillable overlay. The vision model can recognize labels that lack punctuation.
                val textLines = scanFrameText(snapshot)
                val fields = scanWithVision(snapshot, "List only the printed labels of blank fillable fields, one per line. Do not invent fields or repeat any entered value.", 400)
                    ?.lineSequence()?.map { it.trim().trimEnd(':').lowercase(java.util.Locale.ROOT) }?.filter(String::isNotBlank)?.toSet().orEmpty()
                val positioned = if (fields.isEmpty()) textLines else textLines.map { line ->
                    val name = line.text.trim().trimEnd(':')
                    if (name.lowercase(java.util.Locale.ROOT) in fields) line.copy(text = "$name:") else line
                }
                if (frame === snapshot) formFields = scanFormFields(positioned, snapshot.width)
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { error = "Could not find form fields on this page." }
            finally { busy = null }
        }
    }
    fun keepTranslation() {
        val draft = translationDraft ?: return
        val receipt = translationReceipt ?: return
        if (busy != null || receipt.phase != ScanTranslationPhase.READY) return
        error = null
        translationError = false
        scope.launch {
            try { checkNotNull(draft.keep(receipt)) }
            catch (cancel: CancellationException) { throw cancel }
            catch (_: Exception) { translationError = true }
        }
    }

    LaunchedEffect(importImage) {
        val uri = importImage ?: return@LaunchedEffect
        if (kidsMode || !scanEnabled) { importImage = null; return@LaunchedEffect }
        reset()
        busy = "Opening photo"
        try {
            val imported = withContext(kotlinx.coroutines.Dispatchers.IO) { requireNotNull(loadAlbumBitmap(context, uri, 2000)) }
            frame = imported
            paperSource = imported
            if (mode == HaloScanMode.DOCUMENT) {
                check(paperPages.size < 8)
                paperPages = paperPages + imported
                currentPaperPage = paperPages.lastIndex
            }
            if (mode != HaloScanMode.DOCUMENT && !magnifier) {
                readAdultFrame(imported, identify = mode == HaloScanMode.IDENTIFY)
            }
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { error = "Could not open this photo." }
        finally { busy = null; importImage = null }
    }

    LaunchedEffect(importPdf) {
        val uri = importPdf ?: return@LaunchedEffect
        if (kidsMode || magnifier || !scanEnabled) { importPdf = null; return@LaunchedEffect }
        val base = paperPages.takeIf { mode == HaloScanMode.DOCUMENT }.orEmpty()
        if (base.size >= 8) { pdfWasLimited = true; importPdf = null; return@LaunchedEffect }
        busy = "Opening PDF"
        error = null
        var loaded: List<Bitmap> = emptyList()
        var adopted = false
        try {
            val authority = requireAdultToolkit(context)
            val (pages, more) = loadScanPdf(context, uri, 8 - base.size)
            loaded = pages
            requireAdultToolkit(context, authority)
            reset()
            mode = HaloScanMode.DOCUMENT
            easy = false
            paperPages = base + pages
            currentPaperPage = base.size
            frame = pages.first()
            paperSource = frame
            pdfWasLimited = more
            adopted = true
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { error = "Could not open this PDF." }
        finally {
            if (!adopted) loaded.forEach(Bitmap::recycle)
            busy = null
            importPdf = null
        }
    }

    val title = uiText(if (magnifier) "Magnifier" else if (kidsMode) "What’s this?" else "HaloScan")
    val sourceLabel = when {
        kidsMode && childSource?.usesCloud == true -> "Grown-up approved online source"
        usedVision && frame != null -> "Configured vision model"
        !kidsMode && Graph.powerModeEnabled() && frame == null -> "Vision may use Power"
        else -> "On this phone"
    }
    val canUseCamera = !kidsMode || childSource != null
    val scanText = lines.groupBy(HaloScanLine::paragraph).values.joinToString("\n\n") { paragraph -> paragraph.joinToString(" ") { it.text } }
    val nativeChildLabel = if (kidsMode && label != null) uiText(label.orEmpty()) else null
    val childSentence = if (nativeChildLabel != null) uiText(kidsObjectClue(label.orEmpty())) else null
    val childTitle = nativeChildLabel?.let { uiText("It looks like %s!", it) }
    val childSpeech = listOfNotNull(childTitle, childSentence).joinToString(" ")
    val primaryLabel = when {
        !permission && frame == null -> "Allow camera"
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
        mode == HaloScanMode.TRANSLATE -> "Translate"
        frame == null && magnifier -> "Freeze"
        scanText.isEmpty() -> "Read"
        ownedReader?.playing == true || ownedReader?.preparing == true -> "Pause"
        ownedReader != null && ownedReader.notice == null -> "Resume"
        else -> "Read aloud"
    }
    val primaryTitle = when (translationReceipt?.phase) {
        ScanTranslationPhase.READY -> stringResource(R.string.toolkit_keep_translation)
        ScanTranslationPhase.KEEPING -> stringResource(R.string.toolkit_keeping_translation)
        ScanTranslationPhase.KEPT -> stringResource(R.string.toolkit_open_translation)
        null -> uiText(primaryLabel)
    }
    val primaryEnabled = scanEnabled && canUseCamera && (!permission || frame != null || available)
    val primary: () -> Unit = {
        when {
            !permission && frame == null -> permissionLauncher.launch(Manifest.permission.CAMERA)
            mode == HaloScanMode.DOCUMENT && savedId != null -> onOpen("notebook/file/${android.net.Uri.encode(savedId.orEmpty())}")
            mode == HaloScanMode.DOCUMENT && frame != null -> keepPaper()
            kidsMode && label != null && ownedReader?.notice == null && ownedReader != null -> reader.toggle()
            kidsMode && label != null -> read(childSpeech)
            mode == HaloScanMode.IDENTIFY && frame != null -> reset()
            translationReceipt?.objectId != null -> onOpen("notebook/file/${android.net.Uri.encode(translationReceipt.objectId)}")
            translationDraft != null -> keepTranslation()
            mode == HaloScanMode.TRANSLATE && lines.isNotEmpty() -> translate(needsModel)
            mode == HaloScanMode.TRANSLATE && frame != null -> reset()
            scanText.isNotEmpty() && ownedReader != null && ownedReader.notice == null -> reader.toggle()
            scanText.isNotEmpty() -> read(scanText)
            magnifier && frame == null -> frame = preview?.scanBitmap()
            else -> capture()
        }
    }
    val pageActions = buildList {
        add(ShellPageAction("page.$route.primary", primaryTitle, HaloGlyph.CAMERA, primaryEnabled && !easy, primary))
        if (!kidsMode) add(ShellPageAction("page.$route.appearance", uiText("Customize"), HaloGlyph.SETTINGS, scanEnabled) { showOptions = true })
    }
    BindShellPageActions(ShellPageOwner(route, statusLabel = uiText(sourceLabel), guidanceBlocked = !scanEnabled,
        sourceIdentity = translationReceipt?.sessionToken, canonicalSourceVersion = translationReceipt?.sourceVersion?.toString(),
        canonicalToolIds = if (!kidsMode && mode == HaloScanMode.TRANSLATE)
            listOf("halo_scan_translation_status", "halo_scan_keep_translation") else emptyList()), pageActions)

    Column(Modifier.fillMaxSize()) {
        ToolZoneHeader(title, onBack, onTitleLongClick = if (kidsMode) null else ({ showOptions = true })) {
            TextButton(onClick = { showGuide = true }, modifier = Modifier.heightIn(min = 48.dp)) { Text(uiText("Show me")) }
        }
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (!kidsMode && !magnifier) {
                com.halo.app.ui.instruments.HaloInstrumentBar("scan", mapOf(
                    "scan.capture" to com.halo.app.ui.instruments.HaloInstrumentAction({
                        if (frame != null) reset() else capture()
                    }, scanEnabled && (!permission || available || frame != null), working = busy != null),
                    "scan.import_photo" to com.halo.app.ui.instruments.HaloInstrumentAction({ chooseImport = true }, scanEnabled),
                    "scan.edges" to com.halo.app.ui.instruments.HaloInstrumentAction({ cropTools = true }, scanEnabled && frame != null),
                    "scan.rotate_reorder" to com.halo.app.ui.instruments.HaloInstrumentAction({ pageTools = true }, scanEnabled && frame != null),
                    "scan.filter" to com.halo.app.ui.instruments.HaloInstrumentAction({ filterTools = true }, scanEnabled && frame != null),
                    "scan.read_it_to_me" to com.halo.app.ui.instruments.HaloInstrumentAction({
                        if (ownedReader?.notice == null && ownedReader != null) reader.toggle() else read(scanText)
                    }, scanText.isNotBlank(), working = ownedReader?.preparing == true),
                    "scan.found_text" to com.halo.app.ui.instruments.HaloInstrumentAction({ showFoundText = true }, scanText.isNotBlank()),
                    "scan.fill_a_form" to com.halo.app.ui.instruments.HaloInstrumentAction(::prepareForm,
                        scanEnabled && frame != null, working = busy == "Finding form fields on this phone"),
                    "scan.sign" to com.halo.app.ui.instruments.HaloInstrumentAction({ signTools = true }, scanEnabled && frame != null),
                    "scan.translate" to com.halo.app.ui.instruments.HaloInstrumentAction({
                        mode = HaloScanMode.TRANSLATE
                        if (lines.isNotEmpty()) translate(false)
                    }, scanEnabled, selected = mode == HaloScanMode.TRANSLATE),
                    "scan.magnifier" to com.halo.app.ui.instruments.HaloInstrumentAction({ onOpen("magnifier") }, scanEnabled),
                    "scan.save_pdf" to com.halo.app.ui.instruments.HaloInstrumentAction(::keepPaper,
                        frame != null && scanEnabled && savedId == null, working = busy == "Keeping the paper"),
                ), primary = "scan.capture")
                if (pdfWasLimited) Text(stringResource(R.string.halo_scan_pdf_limit), style = MaterialTheme.typography.bodySmall)
                if (mode == HaloScanMode.DOCUMENT && frame != null && scanEnabled) TextButton(onClick = {
                    val snapshot = frame ?: return@TextButton
                    busy = "Reading this page"
                    error = null
                    scope.launch {
                        try {
                            val authority = requireAdultToolkit(context)
                            readAdultFrame(snapshot, identify = false)
                            requireAdultToolkit(context, authority)
                            if (frame === snapshot && lines.isNotEmpty()) showFoundText = true
                        } catch (cancelled: CancellationException) { throw cancelled }
                        catch (_: Exception) { error = "Could not read this page." }
                        finally { busy = null }
                    }
                }) { Text(stringResource(R.string.halo_scan_read_page)) }
                if (showFoundText) AlertDialog(onDismissRequest = { showFoundText = false },
                    title = { Text(uiText("Found text")) },
                    text = { androidx.compose.foundation.text.selection.SelectionContainer { Text(scanText, Modifier.heightIn(max = 300.dp).verticalScroll(rememberScrollState())) } },
                    confirmButton = { TextButton(onClick = { showFoundText = false }) { Text(uiText("Done")) } })
            }
            if (!kidsMode && !magnifier) {
                if (easy) {
                    Text(uiText("Just point the phone."), style = MaterialTheme.typography.headlineSmall)
                    listOf(listOf(HaloScanMode.READ, HaloScanMode.TRANSLATE),
                        listOf(HaloScanMode.DOCUMENT, HaloScanMode.IDENTIFY)).forEach { choices ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            choices.forEach { option ->
                                val filled = option == HaloScanMode.READ
                                Surface(onClick = { reset(); mode = option; easy = false }, enabled = scanEnabled,
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
                                label = { Text(uiText(option.title)) }, enabled = scanEnabled)
                        }
                    }
                }
            }
            if (!easy || magnifier || kidsMode) {
                if (mode == HaloScanMode.DOCUMENT && paperPages.isNotEmpty()) {
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        paperPages.indices.forEach { index ->
                            FilterChip(frame != null && index == currentPaperPage, onClick = { switchPaper(index) },
                                label = { Text(uiText("Page %1\$d", index + 1)) })
                        }
                        TextButton(onClick = ::reset, enabled = scanEnabled && paperPages.size < 8) { Text(uiText("Add page")) }
                    }
                }
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
                        ScanLanguageMenu(uiText("From"), from, scanEnabled) { from = it; translated = emptyList(); translationError = false; needsModel = false }
                        ScanLanguageMenu(uiText("To"), to, scanEnabled) { to = it; translated = emptyList(); translationError = false; needsModel = false }
                    }
                    if (needsModel) Text(uiText("A language pack needs Wi-Fi. Your photo and text stay on this phone."))
                    if (translated.isNotEmpty()) Text(uiText("Tap a label to hear it."), style = MaterialTheme.typography.bodyMedium)
                    if (!kidsMode && translatedParagraphs.isNotBlank()) {
                        HaloReviewedDeviceText(translatedParagraphs, to, enabled = scanEnabled) {
                            currentTranslatedParagraphs == translatedParagraphs
                        }
                    }
                }
                if (label != null) Surface(shape = RoundedCornerShape(24.dp), color = scanWarm, contentColor = Color(0xFF392C20)) {
                    Column(Modifier.fillMaxWidth().padding(20.dp).semantics { liveRegion = LiveRegionMode.Polite }) {
                        val headline = if (kidsMode) "${kidsObjectIcon(label.orEmpty())}  $childTitle" else uiText("This looks like %s.", label.orEmpty())
                        Text(headline, fontSize = 26.sp, lineHeight = 32.sp)
                        if (childSentence != null) Text(childSentence, style = MaterialTheme.typography.bodyLarge)
                    }
                }
                if (reminderCandidates.isNotEmpty()) TextButton({ handoff = true }, enabled = scanEnabled,
                    modifier = Modifier.heightIn(min = 48.dp)) {
                    Text(stringResource(R.string.halo_scan_to_planner))
                }
                if (savedId != null) Text(uiText("Paper kept in your Inbox."), style = MaterialTheme.typography.titleMedium)
                if (translationReceipt?.phase == ScanTranslationPhase.KEPT) Text(stringResource(R.string.toolkit_translation_kept),
                    style = MaterialTheme.typography.titleMedium)
                if (canUseCamera) Text(uiText(if (label != null && kidsMode && childSource?.usesCloud == true) "Used online" else sourceLabel),
                    style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                val status = when {
                    translationError -> stringResource(R.string.toolkit_translation_error)
                    keepingTranslation -> stringResource(R.string.toolkit_keeping_translation)
                    else -> (error ?: busy ?: ownedReader?.notice)?.let { uiText(it) }
                }
                if (status != null) Text(status, Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (error != null || translationError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (!easy || magnifier || kidsMode) Surface(tonalElevation = 2.dp) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (frame != null && mode != HaloScanMode.IDENTIFY) TextButton(enabled = scanEnabled, onClick = { reset(); paperPages = emptyList() }) {
                        Text(uiText(if (magnifier) "Unfreeze" else "New scan"))
                    }
                    if (!kidsMode && (magnifier || mode == HaloScanMode.READ)) FilterChip(
                        selected = highContrast, onClick = { highContrast = !highContrast }, label = { Text(uiText("High contrast")) })
                    if (ownedReader != null) TextButton(onClick = { reader.speed(.75) }) { Text(uiText("Slower")) }
                    if (kidsMode && frame != null) TextButton(onClick = ::reset, enabled = busy == null) { Text(uiText("Again")) }
                    if (!kidsMode && !magnifier) TextButton(enabled = scanEnabled, onClick = { easy = true }) { Text(uiText("Easy")) }
                    val retained = savedId
                    if (retained != null && !kidsMode) TextButton(onClick = {
                        onOpen("study?captureSource=${android.net.Uri.encode(retained)}")
                    }) { Text(stringResource(R.string.halo_scan_open_study)) }
                    if (mode == HaloScanMode.TRANSLATE && translated.isNotEmpty()) TextButton(onClick = {
                        if (ownedReader?.notice == null && ownedReader != null && ownedReader.language == to) reader.toggle()
                        else read(translatedParagraphs, language = to)
                    }, enabled = scanEnabled) {
                        Text(uiText(if (ownedReader?.playing == true || ownedReader?.preparing == true) "Pause" else "Hear translation"))
                    }
                }
                Button(
                    onClick = primary,
                    enabled = primaryEnabled,
                    modifier = Modifier.fillMaxWidth().heightIn(min = if (kidsMode) 64.dp else 52.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = if (kidsMode) Color(0xFFFFA776) else scanYellow, contentColor = Color(0xFF272114)),
                ) { Text(primaryTitle) }
            }
        } else TextButton(onClick = { easy = false }, modifier = Modifier.align(Alignment.CenterHorizontally)) { Text(uiText("Standard controls")) }
    }

    if (!kidsMode) {
        if (chooseImport) AlertDialog(onDismissRequest = { chooseImport = false },
            title = { Text(stringResource(R.string.halo_scan_import)) },
            confirmButton = { TextButton({ chooseImport = false; pdfPicker.launch(arrayOf("application/pdf")) }) {
                Text(stringResource(R.string.halo_scan_import_pdf))
            } }, dismissButton = { TextButton({
                chooseImport = false
                imagePicker.launch(androidx.activity.result.PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            }) { Text(stringResource(R.string.halo_scan_import_photo)) } })
        formFields?.let { fields -> HaloScanFormEditor(fields, onClose = { formFields = null }) { entries ->
            frame?.let { updatePhoto(fillScanForm(it, entries)) }
            formFields = null
        } }
        if (signTools) HaloScanSignatureEditor(onClose = { signTools = false }) { ink, scale, left, top ->
            frame?.let { updatePhoto(stampScanSignature(it, ink, scale, left, top)) }
            signTools = false
        }
    }
    if (pageTools && !kidsMode) ScanPageOrderDialog(currentPaperPage,
        if (mode == HaloScanMode.DOCUMENT) paperPages.size else 0,
        onClose = { pageTools = false }, onRotate = ::rotatePaper, onMove = { index ->
            val pages = paperPages.toMutableList()
            pages.add(index, pages.removeAt(currentPaperPage))
            paperPages = pages
            currentPaperPage = index
            savedId = null
        })
    if (filterTools && !kidsMode) ScanFilterDialog(onClose = { filterTools = false }) { filter ->
        val source = paperSource ?: frame
        if (source != null) { paperSource = source; updatePhoto(filter.render(source), keepSource = true) }
        filterTools = false
    }
    if (cropTools && !kidsMode) ScanCropDialog(onClose = { cropTools = false }) { inset ->
        val bitmap = frame
        if (bitmap != null) {
            val x = (bitmap.width * inset).toInt()
            val y = (bitmap.height * inset).toInt()
            updatePhoto(Bitmap.createBitmap(bitmap, x, y, bitmap.width - 2 * x, bitmap.height - 2 * y))
        }
        cropTools = false
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
