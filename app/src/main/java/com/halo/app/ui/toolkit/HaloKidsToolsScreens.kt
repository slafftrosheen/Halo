package com.halo.app.ui.toolkit

import android.app.TimePickerDialog
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.halo.app.di.ManualDependencyGraph as Graph
import com.halo.app.language.uiText
import com.halo.app.ui.icons.HaloGlyph
import com.halo.app.ui.icons.HaloIcon
import com.halo.app.ui.screens.kids.KidsBrandTheme
import com.halo.app.ui.screens.notebook.ToolZoneHeader
import com.halo.app.ui.theme.rememberVisualPreferences
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val kidApricot = Color(0xFFFFA776)
private val kidMint = Color(0xFF54DDB9)
private val kidInk = Color(0xFF301B21)

@Composable
internal fun KidsPictureToolTiles(onOpen: (String) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(Triple("kids_scan", "What’s this?", HaloGlyph.CAMERA), Triple("kids_day", "My day", HaloGlyph.CALENDAR))
            .forEach { (route, title, icon) ->
                OutlinedButton(onClick = { onOpen(route) }, modifier = Modifier.weight(1f).heightIn(min = 88.dp)) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        HaloIcon(icon, null, Modifier.size(27.dp))
                        Text(uiText(title), style = MaterialTheme.typography.titleMedium)
                    }
                }
            }
    }
}

@Composable
fun HaloKidsScanScreen(onBack: () -> Unit, onOpen: (String) -> Unit) {
    val preferences = Graph.kidsPreferences
    val tools = preferences.pictureTools
    val choice by tools.photo.collectAsStateWithLifecycle()
    val ready by preferences.loadedState.collectAsStateWithLifecycle()
    val children by preferences.enabled.collectAsStateWithLifecycle()
    val privacyEpoch by Graph.privacyModes.profileChanges.collectAsStateWithLifecycle()
    val source = when {
        !ready || !children -> null
        choice.source == KidsPhotoSource.BUNDLED -> HaloBundledScanChildSource
        choice.source == KidsPhotoSource.CLOUD && kidsCloudPhotoAvailable(privacyEpoch) -> remember(choice) {
            GrownUpApprovedScanCloud(choice, tools)
        }
        else -> null
    }
    KidsBrandTheme { key(choice.lease, source != null) { HaloScanScreen(onBack, onOpen, kidsMode = true, childSource = source) } }
}

@Composable
fun HaloKidsDayScreen(onBack: () -> Unit) {
    val preferences = Graph.kidsPreferences
    val tools = preferences.pictureTools
    val day by tools.day.collectAsStateWithLifecycle()
    val savedTicks by tools.ticks.collectAsStateWithLifecycle()
    val enabled by preferences.enabled.collectAsStateWithLifecycle()
    val appearance by preferences.appearance.collectAsStateWithLifecycle()
    val still = appearance.still || rememberVisualPreferences().lessMotion
    val scope = rememberCoroutineScope()
    var now by remember { mutableStateOf(LocalDateTime.now()) }
    var failure by remember { mutableStateOf(false) }
    var readerToken by remember { mutableStateOf<String?>(null) }
    val reader = Graph.readAloud
    val reading by reader.state.collectAsStateWithLifecycle()
    val ownedReading = reading?.takeIf { it.sessionToken == readerToken }
    val minute = now.hour * 60 + now.minute
    val current = day.lastOrNull { it.minute <= minute }?.id
    val ticks = savedTicks.takeIf { it.date == now.toLocalDate() }?.ids.orEmpty()
    val nowLabel = uiText("Now")
    val doneLabel = uiText("Done")
    val remainingLabel = uiText("To do")
    val timeFormat = remember { DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT) }
    val spoken = day.joinToString("\n\n") {
        "${LocalTime.ofSecondOfDay(it.minute * 60L).format(timeFormat)}. ${it.label}."
    }
    LaunchedEffect(Unit) {
        while (true) { now = LocalDateTime.now(); delay(20_000) }
    }
    DisposableEffect(reader) { onDispose { readerToken?.let(reader::stopSession) } }
    KidsBrandTheme {
        Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
            ToolZoneHeader(uiText("My day"), onBack)
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (day.isEmpty()) Text(uiText("Ask a grown-up to add pictures for your day."), style = MaterialTheme.typography.titleLarge)
                if (enabled) day.forEach { item ->
                    val isCurrent = item.id == current
                    val checked = item.id in ticks
                    val border = if (isCurrent) kidApricot else MaterialTheme.colorScheme.outlineVariant
                    val fill = if (checked) kidMint else Color.Transparent
                    val animatedFill = if (still) fill else animateColorAsState(fill, label = "day item completion").value
                    Surface(shape = RoundedCornerShape(21.dp), border = BorderStroke(if (isCurrent) 2.dp else 1.dp, border),
                        tonalElevation = if (isCurrent) 3.dp else 0.dp,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 76.dp).semantics {
                            stateDescription = if (checked) doneLabel else if (isCurrent) nowLabel else remainingLabel
                        }.clickable(role = Role.Checkbox) {
                            scope.launch {
                                try {
                                    check(preferences.enabled.value)
                                    tools.tick(item.id, now.toLocalDate())
                                    failure = false
                                } catch (cancel: CancellationException) { throw cancel }
                                catch (_: Exception) { failure = true }
                            }
                        }) {
                        Row(Modifier.padding(horizontal = 14.dp, vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically) {
                            Surface(shape = RoundedCornerShape(16.dp), color = kidApricot.copy(alpha = .23f)) {
                                Text(item.picture.emoji, Modifier.padding(10.dp), fontSize = 26.sp)
                            }
                            Column(Modifier.weight(1f)) {
                                Text(item.label, style = MaterialTheme.typography.titleLarge)
                                if (isCurrent) Text(nowLabel, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Surface(shape = CircleShape, color = animatedFill,
                                border = BorderStroke(2.dp, if (checked) kidMint else MaterialTheme.colorScheme.outline),
                                modifier = Modifier.size(38.dp)) {
                                if (checked) Box(contentAlignment = Alignment.Center) {
                                    HaloIcon(HaloGlyph.SUCCESS, null, Modifier.size(23.dp), tint = kidInk)
                                } else Box(Modifier.fillMaxSize())
                            }
                        }
                    }
                }
                if (failure) Text(uiText("Your check could not be kept. Try again."), color = MaterialTheme.colorScheme.error)
            }
            Button(onClick = {
                if (ownedReading?.let { it.notice == null } == true) reader.toggle()
                else readerToken = reader.start("kids-day", spoken, Graph.speechSynthesisRegistry.selector.ttsSelection.value.id,
                    cloudApproved = false, language = null, beforeSentence = { Graph.kidsPreferences.enabled.value })
            }, enabled = enabled && spoken.isNotBlank() && appearance.readAloud,
                modifier = Modifier.fillMaxWidth().padding(16.dp).heightIn(min = 60.dp),
                colors = ButtonDefaults.buttonColors(containerColor = kidApricot, contentColor = kidInk),
            ) {
                Text(uiText(if (ownedReading?.playing == true || ownedReading?.preparing == true) "Pause" else "Hear my day"),
                    style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}

@Composable
internal fun KidsPictureToolSettings(tools: HaloKidsPictureTools) {
    val scope = rememberCoroutineScope()
    val source by tools.photo.collectAsStateWithLifecycle()
    val day by tools.day.collectAsStateWithLifecycle()
    var choosing by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf<String?>(null) }
    var cloudReview by remember { mutableStateOf<KidsCloudVision?>(null) }
    val sourceName = uiText(when (source.source) {
        KidsPhotoSource.OFF -> "Off"
        KidsPhotoSource.BUNDLED -> "On this phone"
        KidsPhotoSource.CLOUD -> "Online"
    })
    fun save(value: KidsPhotoSource, cloud: KidsCloudVision? = null) {
        scope.launch {
            busy = true
            try {
                if (value == KidsPhotoSource.CLOUD) check(kidsCloudPhotoAvailable())
                tools.choosePhoto(value, cloud)
                choosing = false
                cloudReview = null
                status = null
            } catch (cancel: CancellationException) { throw cancel }
            catch (_: Exception) { status = "Could not save the photo source." }
            finally { busy = false }
        }
    }
    OutlinedButton(onClick = { choosing = true; status = null }, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
        Text("${uiText("What’s this? source")} · $sourceName")
    }
    OutlinedButton(onClick = { editing = true }, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
        Text(uiText("Set pictures for My day"))
    }
    if (choosing) AlertDialog(
        onDismissRequest = { if (!busy) choosing = false }, title = { Text(uiText("Choose an identification source")) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(uiText("Choose a source for common objects. The on-phone labels come with Halo."))
                OutlinedButton(onClick = { save(KidsPhotoSource.BUNDLED) }, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
                    Text(uiText("On this phone"))
                }
                OutlinedButton(onClick = {
                    scope.launch {
                        busy = true; status = null
                        try { cloudReview = reviewKidsCloudVision() }
                        catch (cancel: CancellationException) { throw cancel }
                        catch (_: Exception) { status = "Online identification needs a cloud Vision model, Power mode, and image sharing and media approval." }
                        finally { busy = false }
                    }
                }, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text(uiText("Review online source")) }
                TextButton(onClick = { save(KidsPhotoSource.OFF) }, enabled = !busy) { Text(uiText("Turn off identification")) }
                status?.let { Text(uiText(it), color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = { TextButton(onClick = { choosing = false }, enabled = !busy) { Text(uiText("Done")) } },
    )
    cloudReview?.let { target ->
        AlertDialog(
            onDismissRequest = { cloudReview = null }, title = { Text(uiText("Allow online photo identification?")) },
            text = { Text(uiText("When your child confirms a photo, Halo will send it to %s (%s) to identify a common object. You can turn this off here at any time.", target.providerName, target.modelId)) },
            confirmButton = { TextButton(onClick = { save(KidsPhotoSource.CLOUD, target) }, enabled = !busy) { Text(uiText("Allow this source")) } },
            dismissButton = { TextButton(onClick = { cloudReview = null }, enabled = !busy) { Text(uiText("Cancel")) } },
        )
    }
    if (editing) EditKidsPictureDay(day, tools, onDismiss = { editing = false })
}

@Composable
private fun EditKidsPictureDay(initial: List<KidsPictureItem>, tools: HaloKidsPictureTools, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var rows by remember { mutableStateOf(initial) }
    var chosen by remember { mutableStateOf<KidsPictureItem?>(null) }
    var busy by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    val timeFormat = remember { DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT) }
    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() }, title = { Text(uiText("Set pictures for My day")) },
        text = {
            Column(Modifier.heightIn(max = 370.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                rows.sortedBy { it.minute }.forEach { item ->
                    OutlinedButton(onClick = { chosen = item }, modifier = Modifier.fillMaxWidth(), enabled = !busy) {
                        Text("${item.picture.emoji}  ${LocalTime.ofSecondOfDay(item.minute * 60L).format(timeFormat)} · ${item.label}")
                    }
                }
                TextButton(enabled = rows.size < 12 && !busy, onClick = {
                    val minute = ((rows.maxOfOrNull { it.minute } ?: 420) + 60).coerceAtMost(1439)
                    chosen = KidsPictureItem(UUID.randomUUID().toString(), "", minute, KidsDayPicture.BREAKFAST)
                }) { Text(uiText("Add a picture")) }
                if (failed) Text(uiText("Could not save My day."), color = MaterialTheme.colorScheme.error)
            }
        },
        confirmButton = { TextButton(enabled = !busy, onClick = {
            busy = true
            scope.launch {
                try { tools.saveDay(rows); onDismiss() }
                catch (cancel: CancellationException) { throw cancel }
                catch (_: Exception) { failed = true }
                finally { busy = false }
            }
        }) { Text(uiText("Keep")) } },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !busy) { Text(uiText("Cancel")) } },
    )
    chosen?.let { item ->
        var label by remember(item.id) { mutableStateOf(item.label) }
        var picture by remember(item.id) { mutableStateOf(item.picture) }
        var minute by remember(item.id) { mutableIntStateOf(item.minute) }
        var menu by rememberSaveable { mutableStateOf(false) }
        AlertDialog(
            onDismissRequest = { chosen = null }, title = { Text(uiText("Picture and time")) },
            text = {
                Column {
                    OutlinedTextField(label, { label = it.take(60) }, label = { Text(uiText("Name")) }, singleLine = true)
                    Box {
                        OutlinedButton(onClick = { menu = true }) { Text("${picture.emoji}  ${uiText(picture.label)}") }
                        DropdownMenu(menu, { menu = false }) {
                            KidsDayPicture.entries.forEach { option ->
                                val name = uiText(option.label)
                                DropdownMenuItem(text = { Text("${option.emoji}  $name") }, onClick = {
                                    menu = false; picture = option
                                    if (label.isBlank()) label = name
                                })
                            }
                        }
                    }
                    OutlinedButton(onClick = {
                        TimePickerDialog(context, { _, hour, min -> minute = hour * 60 + min },
                            minute / 60, minute % 60, android.text.format.DateFormat.is24HourFormat(context)).show()
                    }) { Text(LocalTime.ofSecondOfDay(minute * 60L).format(timeFormat)) }
                    if (rows.any { it.id == item.id }) TextButton(onClick = { rows = rows.filterNot { it.id == item.id }; chosen = null }) {
                        Text(uiText("Remove"))
                    }
                }
            },
            confirmButton = { TextButton(onClick = {
                rows = rows.filterNot { it.id == item.id } + item.copy(label = label.trim(), minute = minute, picture = picture)
                chosen = null
            }, enabled = label.isNotBlank()) { Text(uiText("Done")) } },
            dismissButton = { TextButton(onClick = { chosen = null }) { Text(uiText("Cancel")) } },
        )
    }
}
