package com.halo.app.ui.toolkit

import android.app.KeyguardManager
import android.content.ClipData
import android.content.Intent
import android.net.Uri
import android.widget.VideoView
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import coil.compose.AsyncImage
import com.halo.app.di.ManualDependencyGraph as Graph
import com.halo.app.language.uiText
import com.halo.app.ui.components.HaloVoiceTextActions
import com.halo.app.ui.icons.HaloGlyph
import com.halo.app.ui.presence.BindShellPageActions
import com.halo.app.ui.presence.ShellPageAction
import com.halo.app.ui.presence.ShellPageOwner
import com.halo.app.ui.screens.notebook.ToolZoneHeader
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private data class AlbumShareReview(val video: AlbumStoryVideo, val adultRevision: Long)
private val albumMint = Color(0xFF6EEBC8)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun HaloAlbumScreen(onBack: () -> Unit, onOpen: (String) -> Unit, kidsMode: Boolean = false) {
    if (kidsMode) {
        ToolZoneHeader(uiText("Album"), onBack)
        return
    }
    val context = LocalContext.current
    val index = remember(context) { HaloAlbumIndex(context.applicationContext) }
    val scope = rememberCoroutineScope()
    var library by remember { mutableStateOf(emptyList<HaloAlbumPhoto>()) }
    var people by remember { mutableStateOf(emptyList<AlbumPerson>()) }
    var query by remember { mutableStateOf("") }
    var filter by remember { mutableStateOf("") }
    var spokenRequest by remember { mutableLongStateOf(0L) }
    var showGuide by rememberSaveable { mutableStateOf(false) }
    var customize by rememberSaveable { mutableStateOf(false) }
    var largePhotos by rememberSaveable { mutableStateOf(false) }
    var loading by remember { mutableStateOf(true) }
    var busy by remember { mutableStateOf(false) }
    var notice by remember { mutableStateOf<String?>(null) }
    var selected by remember { mutableStateOf<HaloAlbumPhoto?>(null) }
    var tagDraft by remember { mutableStateOf("") }
    var story by remember { mutableStateOf(emptyList<HaloAlbumPhoto>()) }
    var storyTitle by rememberSaveable { mutableStateOf("") }
    var editStoryTitle by remember { mutableStateOf(false) }
    var music by rememberSaveable { mutableStateOf(true) }
    var playing by remember { mutableStateOf(false) }
    var review by remember { mutableStateOf<AlbumShareReview?>(null) }
    var showPickerChoice by remember { mutableStateOf(false) }
    var personSettings by remember { mutableStateOf(false) }
    var personReference by remember { mutableStateOf<HaloAlbumPhoto?>(null) }
    var personName by remember { mutableStateOf("") }
    var personProgress by remember { mutableStateOf<Float?>(null) }
    var rendered by remember { mutableStateOf<AlbumStoryVideo?>(null) }
    var rendering by remember { mutableStateOf(false) }
    var exportBusy by remember { mutableStateOf(false) }
    var keptVideo by remember { mutableStateOf<String?>(null) }
    var retryRender by remember { mutableIntStateOf(0) }
    val sharedFiles = remember { hashSetOf<String>() }
    var pickerAuthority by remember { mutableStateOf<Long?>(null) }
    val dateFormat = remember { DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM) }
    fun addSelected(values: List<Uri>) {
        val revision = pickerAuthority
        pickerAuthority = null
        if (revision != null && values.isNotEmpty()) scope.launch {
            busy = true
            notice = null
            var kept = 0
            try {
                for (uri in values.take(12)) {
                    try {
                        index.add(uri, revision)
                        kept++
                    } catch (cancel: CancellationException) { throw cancel }
                    catch (_: Exception) { Graph.kidsPreferences.requireAdultAction(revision) }
                }
                library = index.photos()
                var matched = true
                if (kept > 0 && people.isNotEmpty()) {
                    try {
                        index.refreshPeople(revision) { done, total -> personProgress = done.toFloat() / total }
                        library = index.photos()
                    } catch (cancel: CancellationException) { throw cancel }
                    catch (_: Exception) { matched = false }
                    finally { personProgress = null }
                }
                notice = when {
                    kept < values.size -> "Some photos could not be added. Choose readable photos and try again."
                    !matched -> "Photos were kept. People matches could not be refreshed."
                    else -> null
                }
            } catch (cancel: CancellationException) { throw cancel }
            catch (_: Exception) { notice = "Could not add these photos." }
            finally { busy = false }
        }
    }
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(12), ::addSelected)
    val originalFiles = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments(), ::addSelected)
    LaunchedEffect(index) {
        try {
            if (!Graph.kidsPreferences.getEnabled()) {
                val adult = Graph.kidsPreferences.adultActionRevision()
                library = index.photos()
                people = index.people()
                index.refreshPlaces(adult)
                library = index.photos()
            }
        } catch (cancel: CancellationException) { throw cancel }
        catch (_: Exception) { notice = "Could not open the album." }
        finally { loading = false }
    }

    fun addPhotos(original: Boolean) {
        try {
            pickerAuthority = Graph.kidsPreferences.adultActionRevision()
            if (original) originalFiles.launch(arrayOf("image/*"))
            else pick.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        } catch (_: Exception) { pickerAuthority = null; notice = "Could not open photo selection." }
    }

    val results = remember(library, filter) { findAlbumPhotos(library, filter) }
    val grouped = remember(results) { results.groupBy { it.date } }
    val isStory = story.isNotEmpty()
    val dayDefault = uiText("A day to remember")
    val countLabel = uiText("%s photos", if (isStory) story.size else results.size)
    val storyDraft = if (!isStory) null else AlbumStory(story, storyTitle.trim().ifBlank { dayDefault }, countLabel, music)

    LaunchedEffect(storyDraft, editStoryTitle, retryRender) {
        if (editStoryTitle) return@LaunchedEffect
        if (rendered?.draft == storyDraft) return@LaunchedEffect
        playing = false
        keptVideo = null
        val previous = rendered
        rendered = null
        if (previous != null && previous.file.name !in sharedFiles) withContext(Dispatchers.IO) { previous.file.delete() }
        if (storyDraft == null) return@LaunchedEffect
        rendering = true
        notice = null
        try {
            rendered = renderAlbumStory(context, storyDraft, Graph.kidsPreferences.adultActionRevision())
        } catch (cancel: CancellationException) { throw cancel }
        catch (_: Exception) { notice = "Could not create this MP4 on this phone. Try again." }
        finally { rendering = false }
    }
    DisposableEffect(Unit) {
        onDispose { rendered?.file?.takeUnless { it.name in sharedFiles }?.delete() }
    }

    fun makeStory() {
        val beginning = results.firstOrNull() ?: return
        val photos = if (beginning.date != null) results.filter { it.date == beginning.date } else results
        story = photos.distinctBy(HaloAlbumPhoto::id).take(5)
        storyTitle = query.trim().ifBlank { beginning.date?.format(dateFormat) ?: dayDefault }.take(80)
        playing = false
    }

    fun startReview() {
        try {
            val video = rendered?.takeIf { it.draft == storyDraft } ?: return
            review = AlbumShareReview(video, Graph.kidsPreferences.adultActionRevision())
            playing = false
        } catch (_: Exception) { notice = "Ask a grown-up to open Album." }
    }

    fun send(reviewed: AlbumShareReview) {
        scope.launch {
            try {
                val video = reviewed.video
                check(video == rendered && video.file.isFile)
                Graph.kidsPreferences.requireAdultAction(reviewed.adultRevision)
                val uri = video.uri(context)
                val send = Intent(Intent.ACTION_SEND).apply {
                    type = "video/mp4"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    putExtra(Intent.EXTRA_SUBJECT, video.draft.title)
                    clipData = ClipData.newRawUri(video.draft.title, uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                Graph.kidsPreferences.requireAdultAction(reviewed.adultRevision)
                context.startActivity(Intent.createChooser(send, video.draft.title))
                sharedFiles += video.file.name
            } catch (cancel: CancellationException) { throw cancel }
            catch (_: Exception) { notice = "Could not open sharing for this video." }
        }
    }

    fun keepVideo() {
        val video = rendered?.takeIf { it.draft == storyDraft } ?: return
        if (exportBusy) return
        exportBusy = true
        scope.launch {
            try {
                val revision = Graph.kidsPreferences.adultActionRevision()
                val title = video.draft.title.replace(Regex("[\\p{Cntrl}/\\\\]+"), " ").trim().ifBlank { dayDefault }
                Graph.kidsPreferences.requireAdultAction(revision)
                val saved = Graph.workspaceUseCases.importForCanvas(video.uri(context), "$title.mp4", "video/mp4")
                Graph.kidsPreferences.requireAdultAction(revision)
                keptVideo = saved.objectId.value
                notice = "Video kept in Workspace."
            } catch (cancel: CancellationException) { throw cancel }
            catch (_: Exception) { notice = "Could not keep this video." }
            finally { exportBusy = false }
        }
    }

    val photoLibraryEmpty = library.isEmpty()
    val primary = if (isStory) ({ startReview() }) else if (photoLibraryEmpty) ({ showPickerChoice = true }) else ({ makeStory() })
    val primaryLabel = uiText(if (isStory) "Review family share" else if (photoLibraryEmpty) "Choose photos" else "Make a story")
    val primaryEnabled = !loading && !busy && !rendering && !exportBusy &&
        (if (isStory) rendered?.draft == storyDraft else photoLibraryEmpty || results.isNotEmpty())
    BindShellPageActions(ShellPageOwner("album", statusLabel = countLabel),
        listOf(
            ShellPageAction("page.album.primary", primaryLabel, if (isStory) HaloGlyph.ACTION else HaloGlyph.FILE,
                primaryEnabled, primary),
            ShellPageAction("page.album.appearance", uiText("Customize"), HaloGlyph.SETTINGS, !busy) { customize = true },
        ))

    val lifecycle = LocalLifecycleOwner.current
    LaunchedEffect(lifecycle, context) {
        lifecycle.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            HaloAlbumFindRequests.pending.collect { pending ->
                val phrase = pending?.let { request ->
                    HaloAlbumFindRequests.consume(request) { revision ->
                        lifecycle.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED) &&
                            context.getSystemService(KeyguardManager::class.java)?.isDeviceLocked == false &&
                            runCatching { Graph.kidsPreferences.requireAdultAction(revision) }.isSuccess
                    }
                } ?: return@collect
                playing = false
                story = emptyList()
                review = null
                selected = null
                personReference = null
                personSettings = false
                showPickerChoice = false
                editStoryTitle = false
                showGuide = false
                customize = false
                query = phrase
                filter = phrase
            }
        }
    }
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_STOP) playing = false }
        lifecycle.lifecycle.addObserver(observer)
        onDispose { lifecycle.lifecycle.removeObserver(observer) }
    }
    Column(Modifier.fillMaxSize()) {
        ToolZoneHeader(uiText(if (isStory) "Make a story" else "Album"),
            onBack = if (isStory) ({ if (!exportBusy) { story = emptyList(); playing = false } }) else onBack,
            onTitleLongClick = { customize = true }) {
            TextButton(onClick = { showGuide = true }, modifier = Modifier.heightIn(min = 48.dp)) { Text(uiText("Show me")) }
        }
        if (isStory) {
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                val video = rendered?.takeIf { it.draft == storyDraft }
                if (video != null) AlbumStoryPlayback(video, playing, { playing = false }) {
                    playing = false
                    notice = "Could not play this video."
                } else {
                    Box(Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(24.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh), contentAlignment = Alignment.Center) {
                        if (rendering) Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            CircularProgressIndicator()
                            Text(uiText("Creating a video on this phone"))
                        } else TextButton(onClick = { retryRender++ }, enabled = !editStoryTitle) { Text(uiText("Try again")) }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedButton(onClick = { playing = !playing }, enabled = video != null) { Text(uiText(if (playing) "Pause" else "Play story")) }
                    FilterChip(selected = music, onClick = { music = !music }, enabled = !exportBusy, label = { Text(uiText("Soft music")) })
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = { playing = false; editStoryTitle = true }, enabled = !exportBusy) { Text(uiText("Change words")) }
                    TextButton(onClick = { val saved = keptVideo; if (saved == null) keepVideo() else onOpen("notebook/file/${Uri.encode(saved)}") },
                        enabled = video != null && !exportBusy) { Text(uiText(if (keptVideo == null) "Keep video" else "Open video")) }
                }
                notice?.let { Text(uiText(it)) }
            }
        } else LazyColumn(Modifier.weight(1f).fillMaxWidth(), contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item {
                HaloVoiceTextActions(text = query, onText = { query = it; filter = it }, enabled = !busy, dictationRequest = spokenRequest,
                    applySearchImmediately = true, appendDictation = false, showReadAction = false, maxChars = 180,
                    fieldContent = { voice ->
                        OutlinedTextField(query, { query = it.take(180); filter = query }, Modifier.fillMaxWidth(),
                            placeholder = { Text(uiText("Gran’s birthday last summer")) }, trailingIcon = voice,
                            singleLine = true, keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                            keyboardActions = KeyboardActions(onSearch = { filter = query }))
                    })
                personProgress?.let { progress -> LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth()) }
                    ?: if (busy || loading) LinearProgressIndicator(Modifier.fillMaxWidth()) else Unit
            }
            if (results.isEmpty() && !loading) item {
                Text(uiText(if (photoLibraryEmpty) "Choose photos to search dates, words, nearby cities, and people you add."
                    else "No matching photos. Try a name, a date, or a word."), style = MaterialTheme.typography.bodyLarge)
            }
            grouped.forEach { (date, rows) ->
                item {
                    Text("${date?.format(dateFormat) ?: uiText("Date unknown")} · ${uiText("%s photos", rows.size)}",
                        style = MaterialTheme.typography.titleSmall)
                }
                items(rows.chunked(if (largePhotos) 2 else 3)) { photos ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        photos.forEach { photo ->
                            AsyncImage(model = photo.uri, contentDescription = photo.tags.ifBlank { photo.name },
                                modifier = Modifier.weight(1f).aspectRatio(1f).clip(RoundedCornerShape(13.dp))
                                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                                    .clickable { selected = photo; tagDraft = photo.tags },
                                contentScale = ContentScale.Crop)
                        }
                        repeat((if (largePhotos) 2 else 3) - photos.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
            item {
                Text(uiText("Search happens on this phone."), color = MaterialTheme.colorScheme.onSurfaceVariant)
                notice?.let { Text(uiText(it), Modifier.semantics { liveRegion = LiveRegionMode.Polite }) }
            }
        }
        Surface(tonalElevation = 2.dp) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                if (!isStory && !photoLibraryEmpty) FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = { spokenRequest++ }, enabled = !busy) { Text(uiText("Speak to search")) }
                    TextButton(onClick = { showPickerChoice = true }, enabled = !busy) { Text(uiText("Choose photos")) }
                    TextButton(onClick = { personSettings = true }, enabled = !busy) { Text(uiText("People")) }
                }
                Button(primary, enabled = primaryEnabled,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = albumMint, contentColor = Color(0xFF16372E))) {
                    Text(primaryLabel)
                }
            }
        }
    }

    selected?.let { photo ->
        AlertDialog(
            onDismissRequest = { selected = null }, title = { Text(photo.name) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    AsyncImage(photo.uri, photo.tags.ifBlank { photo.name },
                        Modifier.fillMaxWidth().heightIn(min = 140.dp, max = 210.dp), contentScale = ContentScale.Fit)
                    if (photo.nearbyCity.isNotBlank()) Text(uiText("Near %s", photo.nearbyCity), style = MaterialTheme.typography.bodyMedium)
                    if (photo.suggestedPeople.isNotEmpty()) Text(uiText("People matches · %s", photo.suggestedPeople.joinToString(", ")),
                        style = MaterialTheme.typography.bodyMedium)
                    OutlinedTextField(tagDraft, { tagDraft = it.take(500) }, label = { Text(uiText("Names, places and words")) })
                    TextButton(onClick = {
                        selected = null
                        personName = ""
                        personReference = photo
                    }, enabled = !busy) { Text(uiText("Use this photo for People search")) }
                }
            },
            confirmButton = { TextButton(onClick = {
                val replacement = tagDraft
                selected = null
                scope.launch {
                    try {
                        index.putTags(photo.id, replacement, Graph.kidsPreferences.adultActionRevision())
                        library = index.photos()
                    } catch (cancel: CancellationException) { throw cancel }
                    catch (_: Exception) { notice = "Could not save photo names and places." }
                }
            }) { Text(uiText("Keep")) } },
            dismissButton = { TextButton(onClick = { selected = null }) { Text(uiText("Cancel")) } },
        )
    }
    if (showPickerChoice) AlertDialog(
        onDismissRequest = { showPickerChoice = false }, title = { Text(uiText("Choose photos")) },
        text = { Text(uiText("Pick photos to add to Album. Choose original files to find nearby cities from any location information already in those files.")) },
        confirmButton = { TextButton(onClick = { showPickerChoice = false; addPhotos(original = false) }) { Text(uiText("Photo picker")) } },
        dismissButton = { TextButton(onClick = { showPickerChoice = false; addPhotos(original = true) }) { Text(uiText("Original files")) } },
    )
    personReference?.let { photo ->
        AlertDialog(
            onDismissRequest = { if (!busy) personReference = null }, title = { Text(uiText("Add someone to People search")) },
            text = {
                Column(Modifier.heightIn(max = 450.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    AsyncImage(photo.uri, uiText("Selected reference photo"),
                        Modifier.fillMaxWidth().heightIn(min = 140.dp, max = 210.dp), contentScale = ContentScale.Fit)
                    Text(uiText("Choose a clear photo of one person and enter the name you use for them. Halo looks for similar faces in your Album on this phone. You can remove the person at any time."))
                    OutlinedTextField(personName, { personName = it.take(60) }, Modifier.fillMaxWidth(),
                        label = { Text(uiText("Person's name")) }, singleLine = true, enabled = !busy)
                }
            },
            confirmButton = { TextButton(onClick = {
                val name = personName.trim()
                personReference = null
                busy = true
                notice = null
                scope.launch {
                    try {
                        val revision = Graph.kidsPreferences.adultActionRevision()
                        index.enroll(photo, name, revision) { done, total -> personProgress = done.toFloat() / total }
                        people = index.people()
                        library = index.photos()
                        notice = "Person added. Search their name to see matches."
                    } catch (cancel: CancellationException) { throw cancel }
                    catch (_: AlbumReferenceFaceUnavailable) { notice = "Choose a clear reference photo that shows one person." }
                    catch (_: Exception) { notice = "Could not match this person on this phone." }
                    finally { busy = false; personProgress = null }
                }
            }, enabled = !busy && personName.isNotBlank()) { Text(uiText("Match this person")) } },
            dismissButton = { TextButton(onClick = { personReference = null }, enabled = !busy) { Text(uiText("Cancel")) } },
        )
    }
    if (personSettings) AlertDialog(
        onDismissRequest = { if (!busy) personSettings = false }, title = { Text(uiText("People search")) },
        text = {
            Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(uiText("Open a clear photo of one person to add them to People search."))
                people.forEach { person ->
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(person.name, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                        TextButton(onClick = {
                            busy = true
                            scope.launch {
                                try {
                                    index.removePerson(person.id, Graph.kidsPreferences.adultActionRevision())
                                    people = index.people()
                                    library = index.photos()
                                } catch (cancel: CancellationException) { throw cancel }
                                catch (_: Exception) { notice = "Could not remove this person from People search." }
                                finally { busy = false }
                            }
                        }, enabled = !busy) { Text(uiText("Remove")) }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { personSettings = false }, enabled = !busy) { Text(uiText("Done")) } },
    )
    if (editStoryTitle) AlertDialog(
        onDismissRequest = { editStoryTitle = false }, title = { Text(uiText("Change words")) },
        text = { OutlinedTextField(storyTitle, { storyTitle = it.take(80) }, label = { Text(uiText("Title")) }) },
        confirmButton = { TextButton(onClick = { editStoryTitle = false }) { Text(uiText("Done")) } },
    )
    review?.let { reviewed ->
        val video = reviewed.video
        AlertDialog(
            onDismissRequest = { review = null }, title = { Text(uiText("Send to family?")) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(video.draft.title, style = MaterialTheme.typography.titleMedium)
                    Text(uiText("MP4 · %s seconds", (video.durationMs + 500) / 1000), style = MaterialTheme.typography.labelLarge)
                    if (video.draft.music) Text(uiText("Includes soft music"), style = MaterialTheme.typography.bodySmall)
                    Text(uiText("Choose who receives this video on the next screen."))
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        video.draft.photos.forEach { photo ->
                            AsyncImage(photo.uri, photo.tags.ifBlank { photo.name },
                                Modifier.weight(1f).aspectRatio(1f).clip(RoundedCornerShape(7.dp)),
                                contentScale = ContentScale.Crop)
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { review = null; send(reviewed) }) { Text(uiText("Choose app and person")) } },
            dismissButton = { TextButton(onClick = { review = null }) { Text(uiText("Cancel")) } },
        )
    }
    if (showGuide) AlertDialog(
        onDismissRequest = { showGuide = false }, title = { Text(uiText("Show me")) },
        text = { Text(uiText("Choose photos, then say or type what you remember. Open a clear photo of one person to add them to People search. Make a story creates a video you can play, keep, or share after review.")) },
        confirmButton = { TextButton(onClick = { showGuide = false }) { Text(uiText("Try it")) } },
    )
    if (customize) AlertDialog(
        onDismissRequest = { customize = false }, title = { Text(uiText("Customize")) },
        text = {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(uiText("Larger photos"), Modifier.weight(1f))
                    Switch(largePhotos, { largePhotos = it })
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(uiText("Soft music"), Modifier.weight(1f))
                    Switch(music, { music = it })
                }
            }
        },
        confirmButton = { TextButton(onClick = { customize = false }) { Text(uiText("Done")) } },
        dismissButton = { TextButton(onClick = { customize = false; onOpen("notebook") }) { Text(uiText("Open Workspace")) } },
    )
}

@Composable
private fun AlbumStoryPlayback(video: AlbumStoryVideo, playing: Boolean, onComplete: () -> Unit, onFailure: () -> Unit) {
    val description = uiText("Story video preview")
    val completed by rememberUpdatedState(onComplete)
    val failed by rememberUpdatedState(onFailure)
    key(video.file.name) {
        AndroidView(
            factory = { context -> VideoView(context).apply {
                setOnPreparedListener { it.seekTo(1) }
                setOnCompletionListener { completed() }
                setOnErrorListener { _, _, _ -> failed(); true }
                setVideoPath(video.file.absolutePath)
            } },
            modifier = Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(24.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHigh).semantics { contentDescription = description },
            onRelease = { it.stopPlayback() },
            update = { view ->
                if (playing && !view.isPlaying) view.start()
                if (!playing && view.isPlaying) view.pause()
            },
        )
    }
}
