package com.halo.app.ui.screens.kids

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.AtomicFile
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.verticalScroll
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.SecureFlagPolicy
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.halo.app.R
import com.halo.app.di.ManualDependencyGraph
import com.halo.app.language.uiText
import com.halo.app.ui.instruments.HaloInstrumentAction
import com.halo.app.ui.instruments.HaloInstrumentBar
import com.halo.app.ui.screens.study.CoachResultSummary
import com.halo.app.ui.screens.study.CoachHintArea
import com.halo.app.ui.screens.study.CoachOperation
import com.halo.app.ui.workspace.InspectableImage
import java.io.File
import java.time.LocalTime
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

internal val kidsHomeworkOperations = CoachOperation.entries.filter { it != CoachOperation.ACCELERATION }.toSet()

internal data class KidsHomeworkWindow(
    val enabled: Boolean = false,
    val start: LocalTime = LocalTime.of(16, 0),
    val end: LocalTime = LocalTime.of(18, 0),
    val subjects: Set<CoachOperation> = kidsHomeworkOperations,
) {
    init { require(start != end && subjects.isNotEmpty() && kidsHomeworkOperations.containsAll(subjects)) }
    fun includes(time: LocalTime): Boolean = enabled && if (start < end) time >= start && time < end
        else time >= start || time < end
}

internal class KidsHomeworkOwner(context: Context) {
    private val disk = context.applicationContext.getSharedPreferences("halo_kids_homework_v1", Context.MODE_PRIVATE)
    private val sheet = AtomicFile(File(context.applicationContext.noBackupFilesDir, "kids-homework-photo.jpg"))
    private val writes = Mutex()
    private val mutable = MutableStateFlow<KidsHomeworkWindow?>(null)
    val settings = mutable.asStateFlow()
    private val mutableGlasses = MutableStateFlow<String?>(null)
    val approvedGlasses = mutableGlasses.asStateFlow()
    private val latestSummary = MutableStateFlow<CoachResultSummary?>(null)
    val summary = latestSummary.asStateFlow()
    private val summaryWrites = AtomicLong()
    private var latestSummaryWrite = 0L
    private val photoKept = MutableStateFlow(false)
    val hasKeptPhoto = photoKept.asStateFlow()

    suspend fun load() = withContext(Dispatchers.IO) {
        writes.withLock {
            if (mutable.value != null) return@withLock
            photoKept.value = sheet.baseFile.isFile
            mutableGlasses.value = disk.getString("approved_glasses", null)?.takeIf { it.isNotBlank() && it.length <= 200 }
            val savedAreas = disk.getStringSet("hint_areas", null).orEmpty()
            latestSummary.value = CoachResultSummary(disk.getInt("done", 0), disk.getInt("total", 0),
                disk.getInt("hinted", 0), CoachHintArea.entries.filterTo(linkedSetOf()) { it.name in savedAreas })
                .takeIf(::validSummary)
            val start = disk.getString("from", null)?.let { runCatching { LocalTime.parse(it) }.getOrNull() }
            val end = disk.getString("until", null)?.let { runCatching { LocalTime.parse(it) }.getOrNull() }
            val saved = disk.getStringSet("subjects", null)
            val subjects = saved?.let { names -> kidsHomeworkOperations.filterTo(linkedSetOf()) { it.name in names } }
                ?.takeIf(Set<CoachOperation>::isNotEmpty) ?: kidsHomeworkOperations
            mutable.value = if (start == null || end == null || start == end) KidsHomeworkWindow()
                else KidsHomeworkWindow(disk.getBoolean("enabled", false), start, end, subjects)
        }
    }

    suspend fun chooseGlasses(expected: String?, deviceId: String?, checkAuthority: () -> Unit) = withContext(Dispatchers.IO) {
        writes.withLock {
            check(mutable.value != null && mutableGlasses.value == expected)
            require(deviceId == null || deviceId.isNotBlank() && deviceId.length <= 200)
            if (deviceId != null) check(ManualDependencyGraph.visualContextSource.connectedHomeworkGlasses().any { it.identity.deviceId == deviceId })
            checkAuthority()
            check(disk.edit().putString("approved_glasses", deviceId).commit())
            mutableGlasses.value = deviceId
        }
    }

    suspend fun change(expected: KidsHomeworkWindow, approved: KidsHomeworkWindow, checkAuthority: () -> Unit) =
        withContext(Dispatchers.IO) {
            writes.withLock {
                check(mutable.value == expected)
                checkAuthority()
                check(disk.edit().putBoolean("enabled", approved.enabled).putString("from", approved.start.toString())
                    .putString("until", approved.end.toString())
                    .putStringSet("subjects", approved.subjects.map { it.name }.toSet()).commit())
                mutable.value = approved
            }
        }

    suspend fun updateSummary(result: CoachResultSummary, checkAuthority: () -> Unit) {
        val revision = summaryWrites.incrementAndGet()
        withContext(Dispatchers.IO) {
            writes.withLock {
                if (revision <= latestSummaryWrite) return@withLock
                require(validSummary(result))
                checkAuthority()
                check(mutable.value?.includes(LocalTime.now()) == true)
                check(disk.edit().putInt("done", result.done).putInt("total", result.total)
                    .putInt("hinted", result.usedHints).putStringSet("hint_areas", result.areas.map { it.name }.toSet()).commit())
                latestSummaryWrite = revision
                latestSummary.value = result
            }
        }
    }

    suspend fun keepPhoto(image: Bitmap, checkAuthority: () -> Unit) = withContext(Dispatchers.IO) {
        writes.withLock {
            checkAuthority()
            check(mutable.value?.includes(LocalTime.now()) == true)
            val output = sheet.startWrite()
            try {
                check(image.compress(Bitmap.CompressFormat.JPEG, 88, output))
                checkAuthority()
                sheet.finishWrite(output)
                photoKept.value = true
            } catch (error: Exception) {
                sheet.failWrite(output)
                throw error
            }
        }
    }

    suspend fun readPhoto(checkAuthority: () -> Unit): Bitmap = withContext(Dispatchers.IO) {
        writes.withLock {
            checkAuthority()
            val image = requireNotNull(sheet.openRead().use { BitmapFactory.decodeStream(it) })
            try { checkAuthority(); image }
            catch (error: Exception) { image.recycle(); throw error }
        }
    }

    suspend fun removePhoto(checkAuthority: () -> Unit) = withContext(Dispatchers.IO) {
        writes.withLock {
            checkAuthority()
            sheet.delete()
            check(!sheet.baseFile.exists())
            photoKept.value = false
        }
    }

    private fun validSummary(summary: CoachResultSummary): Boolean =
        summary.total in 1..12 && summary.done in 0..summary.total && summary.usedHints in 0..summary.total
}

@Composable
internal fun rememberKidsHomeworkTime(owner: KidsHomeworkOwner): Boolean {
    val schedule by owner.settings.collectAsStateWithLifecycle()
    val now by produceState(LocalTime.now(), schedule) {
        while (true) { value = LocalTime.now(); delay(2_000) }
    }
    LaunchedEffect(owner) {
        try { owner.load() } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { /* A missing or unreadable schedule leaves the helper closed. */ }
    }
    return schedule?.includes(now) == true
}

@Composable
internal fun KidsHomeworkSettings(
    owner: KidsHomeworkOwner,
    requireAuthority: () -> Unit,
    save: suspend (KidsHomeworkWindow, KidsHomeworkWindow) -> Unit,
) {
    val schedule by owner.settings.collectAsStateWithLifecycle()
    val recent by owner.summary.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    var summaryOpen by remember { mutableStateOf(false) }
    LaunchedEffect(owner) {
        try { owner.load() } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { failed = true }
    }
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.halo11_coach_homework_schedule), style = MaterialTheme.typography.titleMedium)
        Text(stringResource(R.string.halo11_coach_homework_schedule_local), style = MaterialTheme.typography.bodySmall)
        val current = schedule
        if (current != null) HomeworkWindowFields(current, busy) { desired ->
            if (!busy) {
                busy = true
                failed = false
                scope.launch {
                    try { save(current, desired) } catch (cancelled: CancellationException) { throw cancelled }
                    catch (_: Exception) { failed = true }
                    finally { busy = false }
                }
            }
        }
        if (current != null) {
            KidsHomeworkGlassesSetting(owner, requireAuthority)
            KidsHomeworkScheduleTransfer(current, requireAuthority, save)
        }
        val result = recent
        if (result != null) {
            HaloInstrumentBar("coach", mapOf("coach.summary" to HaloInstrumentAction({
                if (runCatching(requireAuthority).isSuccess) summaryOpen = !summaryOpen
            }, selected = summaryOpen)), primary = "coach.summary")
        }
        if (result != null && summaryOpen) {
            Text(stringResource(R.string.halo11_coach_homework_summary, result.done, result.total, result.usedHints),
                style = MaterialTheme.typography.bodyMedium)
            if (result.areas.isNotEmpty()) {
                val areas = result.areas.sortedBy { it.ordinal }.map { area ->
                    stringResource(when (area) {
                        CoachHintArea.IDEA -> R.string.halo11_coach_area_idea
                        CoachHintArea.CARRYING -> R.string.halo11_coach_area_carrying
                        CoachHintArea.BORROWING -> R.string.halo11_coach_area_borrowing
                        CoachHintArea.CALCULATING -> R.string.halo11_coach_area_calculating
                    })
                }.joinToString(", ")
                Text(stringResource(R.string.halo11_coach_homework_summary_areas, areas), style = MaterialTheme.typography.bodySmall)
            }
        }
        KidsKeptHomeworkPhoto(owner, requireAuthority)
        if (failed) Text(uiText("Could not save your change. Try again."), style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun KidsHomeworkGlassesSetting(owner: KidsHomeworkOwner, requireAuthority: () -> Unit) {
    val approved by owner.approvedGlasses.collectAsStateWithLifecycle()
    val devicesRevision by ManualDependencyGraph.deviceSessionManager.stateRevision.collectAsStateWithLifecycle()
    val available by produceState(emptyList<com.halo.sdk.runtime.embodiment.DeviceRecord>(), devicesRevision) {
        value = try { ManualDependencyGraph.visualContextSource.connectedHomeworkGlasses() }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { emptyList() }
    }
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    fun choose(id: String?) {
        if (busy) return
        val reviewed = approved
        busy = true
        failed = false
        scope.launch {
            try { owner.chooseGlasses(reviewed, id, requireAuthority) }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { failed = true }
            finally { busy = false }
        }
    }
    Text(stringResource(R.string.halo11_coach_glasses_settings), style = MaterialTheme.typography.titleSmall)
    Text(stringResource(R.string.halo11_coach_glasses_local), style = MaterialTheme.typography.bodySmall)
    if (available.isEmpty()) Text(stringResource(R.string.halo11_coach_glasses_connect), style = MaterialTheme.typography.bodySmall)
    available.forEach { device ->
        val id = device.identity.deviceId
        FilterChip(id == approved, { choose(id) }, enabled = !busy && id != approved,
            label = { Text(device.identity.displayName.ifBlank { device.descriptor.kind.label }) })
    }
    if (approved != null) TextButton({ choose(null) }, enabled = !busy) {
        Text(stringResource(R.string.halo11_coach_glasses_disable))
    }
    if (failed) Text(stringResource(R.string.halo11_coach_glasses_unavailable), style = MaterialTheme.typography.bodySmall)
}

@Composable
private fun KidsKeptHomeworkPhoto(owner: KidsHomeworkOwner, requireAuthority: () -> Unit) {
    val available by owner.hasKeptPhoto.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var image by remember { mutableStateOf<Bitmap?>(null) }
    var failed by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    val shown = image
    DisposableEffect(shown) { onDispose { shown?.recycle() } }
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_STOP) image = null }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    if (available) OutlinedButton(enabled = !busy, onClick = {
        busy = true
        failed = false
        scope.launch {
            try {
                val photo = owner.readPhoto(requireAuthority)
                if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) image = photo else photo.recycle()
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { failed = true }
            finally { busy = false }
        }
    }) { Text(stringResource(R.string.halo11_coach_open_kept_photo)) }
    if (shown != null) AlertDialog(onDismissRequest = { image = null },
        properties = DialogProperties(securePolicy = SecureFlagPolicy.SecureOn),
        title = { Text(stringResource(R.string.halo11_coach_photo_preview)) },
        text = { InspectableImage(shown, stringResource(R.string.halo11_coach_photo_preview),
            Modifier.fillMaxWidth().height(280.dp)) },
        dismissButton = { TextButton(onClick = { image = null }) { Text(uiText("Close")) } },
        confirmButton = {
            TextButton(enabled = !busy, onClick = {
                busy = true
                scope.launch {
                    try { owner.removePhoto(requireAuthority); image = null }
                    catch (cancelled: CancellationException) { throw cancelled }
                    catch (_: Exception) { failed = true; image = null }
                    finally { busy = false }
                }
            }) { Text(stringResource(R.string.halo11_coach_delete_kept_photo)) }
        })
    if (failed) Text(stringResource(R.string.halo11_coach_unavailable), style = MaterialTheme.typography.bodySmall)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun HomeworkWindowFields(current: KidsHomeworkWindow, busy: Boolean, onSave: (KidsHomeworkWindow) -> Unit) {
    var enabled by remember(current) { mutableStateOf(current.enabled) }
    var from by remember(current) { mutableStateOf(current.start.toString()) }
    var until by remember(current) { mutableStateOf(current.end.toString()) }
    var subjects by remember(current) { mutableStateOf(current.subjects) }
    val start = runCatching { LocalTime.parse(from) }.getOrNull()
    val end = runCatching { LocalTime.parse(until) }.getOrNull()
    val next = if (start == null || end == null || start == end || subjects.isEmpty()) null
        else KidsHomeworkWindow(enabled, start, end, subjects)
    Row(Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.halo11_coach_homework_enabled), Modifier.weight(1f))
        Switch(enabled, { enabled = it }, enabled = !busy)
    }
    if (enabled) {
        Text(stringResource(R.string.halo11_coach_homework_math))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            for (subject in kidsHomeworkOperations) FilterChip(subject in subjects, {
                subjects = if (subject in subjects) subjects - subject else subjects + subject
            }, enabled = !busy, label = { Text(stringResource(subject.homeworkLabel)) })
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(from, { from = it.take(5) }, Modifier.weight(1f), enabled = !busy, singleLine = true,
                label = { Text(stringResource(R.string.halo11_coach_homework_start)) })
            OutlinedTextField(until, { until = it.take(5) }, Modifier.weight(1f), enabled = !busy, singleLine = true,
                label = { Text(stringResource(R.string.halo11_coach_homework_end)) })
        }
    }
    Button(enabled = !busy && (next != null || !enabled) && (next ?: current.copy(enabled = false)) != current,
        onClick = { onSave(next ?: current.copy(enabled = false)) }) { Text(uiText("Save")) }
}

private val CoachOperation.homeworkLabel get() = when (this) {
    CoachOperation.ADD -> R.string.halo11_coach_add
    CoachOperation.SUBTRACT -> R.string.halo11_coach_subtract
    CoachOperation.MULTIPLY -> R.string.halo11_coach_multiply
    CoachOperation.DIVIDE -> R.string.halo11_coach_divide
    CoachOperation.ACCELERATION -> R.string.halo11_coach_acceleration_idea
}

private object HomeworkScheduleFile {
    const val MAX_BYTES = 2048
    fun encode(window: KidsHomeworkWindow): ByteArray = JSONObject().put("schema", "halo-homework-schedule/1")
        .put("enabled", window.enabled).put("start", window.start.toString()).put("end", window.end.toString())
        .put("subjects", JSONArray(window.subjects.sortedBy { it.ordinal }.map { it.name })).toString().toByteArray(Charsets.UTF_8)

    fun decode(data: ByteArray): KidsHomeworkWindow {
        require(data.size in 1..MAX_BYTES)
        val item = JSONObject(data.toString(Charsets.UTF_8))
        require(item.keys().asSequence().toSet() == setOf("schema", "enabled", "start", "end", "subjects"))
        require(item.getString("schema") == "halo-homework-schedule/1")
        val list = item.getJSONArray("subjects")
        require(list.length() in 1..kidsHomeworkOperations.size)
        val subjects = (0 until list.length()).map { CoachOperation.valueOf(list.getString(it)) }.toSet()
        require(subjects.size == list.length())
        return KidsHomeworkWindow(item.getBoolean("enabled"), LocalTime.parse(item.getString("start")),
            LocalTime.parse(item.getString("end")), subjects)
    }
}

@Composable
private fun KidsHomeworkScheduleTransfer(
    current: KidsHomeworkWindow,
    requireAuthority: () -> Unit,
    save: suspend (KidsHomeworkWindow, KidsHomeworkWindow) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val latest by rememberUpdatedState(current)
    val authority by rememberUpdatedState(requireAuthority)
    var incoming by remember { mutableStateOf<Pair<KidsHomeworkWindow, KidsHomeworkWindow>?>(null) }
    var outgoing by remember { mutableStateOf<KidsHomeworkWindow?>(null) }
    var busy by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    fun transfer(action: suspend () -> Unit) {
        if (busy) return
        busy = true
        failed = false
        scope.launch {
            try { authority(); action() }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { failed = true }
            finally { busy = false }
        }
    }
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) transfer {
            val expected = latest
            val candidate = withContext(Dispatchers.IO) {
                authority()
                requireNotNull(context.contentResolver.openInputStream(uri)).use { stream ->
                    val data = java.io.ByteArrayOutputStream()
                    val buffer = ByteArray(256)
                    while (true) {
                        val count = stream.read(buffer)
                        if (count < 0) break
                        require(data.size() + count <= HomeworkScheduleFile.MAX_BYTES)
                        data.write(buffer, 0, count)
                    }
                    HomeworkScheduleFile.decode(data.toByteArray())
                }
            }
            authority()
            incoming = expected to candidate
        }
    }
    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        val approved = outgoing
        outgoing = null
        if (uri != null && approved != null) transfer {
            check(latest == approved)
            withContext(Dispatchers.IO) {
                authority()
                requireNotNull(context.contentResolver.openOutputStream(uri, "wt")).use { it.write(HomeworkScheduleFile.encode(approved)) }
            }
        }
    }
    TextButton({ pick.launch(arrayOf("application/json", "text/plain")) }, enabled = !busy) {
        Text(stringResource(R.string.halo11_homework_import))
    }
    TextButton({
        if (runCatching(authority).isSuccess) {
            outgoing = latest
            export.launch("halo-homework-schedule.json")
        }
    }, enabled = !busy) { Text(stringResource(R.string.halo11_homework_export)) }
    incoming?.let { (expected, candidate) ->
        val subjects = candidate.subjects.sortedBy { it.ordinal }.map { stringResource(it.homeworkLabel) }.joinToString(", ")
        AlertDialog(onDismissRequest = { incoming = null }, properties = DialogProperties(securePolicy = SecureFlagPolicy.SecureOn),
            title = { Text(stringResource(R.string.halo11_homework_import)) },
            text = { Text(stringResource(R.string.halo11_homework_transfer_review, candidate.start.toString(), candidate.end.toString(),
                subjects, stringResource(if (candidate.enabled) R.string.halo11_homework_active else R.string.halo11_homework_inactive))) },
            confirmButton = { TextButton({ incoming = null; transfer { save(expected, candidate) } }) {
                Text(stringResource(android.R.string.ok))
            } },
            dismissButton = { TextButton({ incoming = null }) { Text(stringResource(android.R.string.cancel)) } })
    }
    if (failed) Text(stringResource(R.string.halo11_homework_transfer_error), color = MaterialTheme.colorScheme.error)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun GrownUpHomeworkSettings(preferences: KidsPreferences) {
    val context = LocalContext.current
    var lease by remember { mutableStateOf<Long?>(null) }
    var unavailable by remember { mutableStateOf(false) }
    val unlock = rememberKidsGrownUpGate(onVerified = {
        lease = runCatching { preferences.presentationRevision() }.getOrNull()
        unavailable = lease == null
    }, onUnavailable = { unavailable = true })
    TextButton(unlock) { Text(stringResource(R.string.halo11_coach_homework_schedule)) }
    if (unavailable) Text(stringResource(R.string.halo11_family_safe_unlock))
    val revision = lease ?: return
    ModalBottomSheet(onDismissRequest = { lease = null },
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        properties = ModalBottomSheetProperties(securePolicy = SecureFlagPolicy.SecureOn)) {
        Column(Modifier.fillMaxWidth().heightIn(max = 620.dp).verticalScroll(androidx.compose.foundation.rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 12.dp)) {
            KidsHomeworkSettings(preferences.homework, requireAuthority = {
                check(lease == revision)
                check(context.getSystemService(android.app.KeyguardManager::class.java)?.isDeviceLocked == false)
                preferences.requirePresentation(revision)
            }) { before, after -> preferences.configureHomework(revision, before, after) }
            TextButton({ lease = null }) { Text(stringResource(android.R.string.ok)) }
        }
    }
}
