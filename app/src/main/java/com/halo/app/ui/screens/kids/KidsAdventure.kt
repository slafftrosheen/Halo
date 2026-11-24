package com.halo.app.ui.screens.kids

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.halo.app.R
import com.halo.app.ai.PrivacyMode
import com.halo.app.di.ManualDependencyGraph as Graph
import com.halo.app.language.uiText
import com.halo.app.ui.components.VoiceFieldCancellation
import com.halo.app.ui.fx.HaloFxAnimation
import com.halo.app.ui.fx.HaloFxMoment
import com.halo.app.ui.presence.HaloSignalFace
import com.halo.app.ui.presence.HaloSignalPresentation
import com.halo.app.ui.presence.HaloSignalState
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private data class AdventureArt(val learn: String, val make: String, val find: String, val tell: String = "💬")
private const val GREETING_ID = "kids-adventure:greeting"

private fun adventureArt(topic: KidsCardTopic): AdventureArt = when (topic) {
    KidsCardTopic.ANIMALS -> AdventureArt("🐱", "✏️", "🪶")
    KidsCardTopic.COUNTING -> AdventureArt("🍎", "🎨", "🪨")
    KidsCardTopic.NATURE -> AdventureArt("🌻", "☀️", "🍃")
    KidsCardTopic.DINOSAURS -> AdventureArt("🦖", "🦕", "🦴")
}

@StringRes internal fun adventureTitle(topic: KidsCardTopic): Int = when (topic) {
    KidsCardTopic.ANIMALS -> R.string.halo11_coach_adventure_animals
    KidsCardTopic.COUNTING -> R.string.halo11_coach_adventure_counts
    KidsCardTopic.NATURE -> R.string.halo11_coach_adventure_nature
    KidsCardTopic.DINOSAURS -> R.string.kids_dinosaurs_title
}

@StringRes private fun stopTitle(stop: KidsAdventureStop): Int = when (stop) {
    KidsAdventureStop.LEARN -> R.string.halo11_coach_adventure_learn
    KidsAdventureStop.MAKE -> R.string.halo11_coach_adventure_make
    KidsAdventureStop.FIND -> R.string.halo11_coach_adventure_find
    KidsAdventureStop.TELL -> R.string.kids_stop_tell
}

@StringRes private fun stopPrompt(topic: KidsCardTopic, stop: KidsAdventureStop): Int = when (topic) {
    KidsCardTopic.ANIMALS -> when (stop) {
        KidsAdventureStop.LEARN -> R.string.halo11_coach_adventure_learn_animals
        KidsAdventureStop.MAKE -> R.string.halo11_coach_adventure_make_animals
        KidsAdventureStop.FIND -> R.string.halo11_coach_adventure_find_animals
        KidsAdventureStop.TELL -> R.string.kids_tell_buddy_story
    }
    KidsCardTopic.COUNTING -> when (stop) {
        KidsAdventureStop.LEARN -> R.string.halo11_coach_adventure_learn_counts
        KidsAdventureStop.MAKE -> R.string.halo11_coach_adventure_make_counts
        KidsAdventureStop.FIND -> R.string.halo11_coach_adventure_find_counts
        KidsAdventureStop.TELL -> R.string.kids_tell_buddy_story
    }
    KidsCardTopic.NATURE -> when (stop) {
        KidsAdventureStop.LEARN -> R.string.halo11_coach_adventure_learn_nature
        KidsAdventureStop.MAKE -> R.string.halo11_coach_adventure_make_nature
        KidsAdventureStop.FIND -> R.string.halo11_coach_adventure_find_nature
        KidsAdventureStop.TELL -> R.string.kids_tell_buddy_story
    }
    KidsCardTopic.DINOSAURS -> when (stop) {
        KidsAdventureStop.LEARN -> R.string.kids_dinosaurs_learn
        KidsAdventureStop.MAKE -> R.string.kids_dinosaurs_make
        KidsAdventureStop.FIND -> R.string.kids_dinosaurs_find
        KidsAdventureStop.TELL -> R.string.kids_tell_buddy_story
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun KidsAdventureHome(owner: KidsCardShelf, readAloud: Boolean, still: Boolean, welcomeAvailable: Boolean,
    requested: Boolean, onRequestHandled: () -> Unit, onBook: () -> Unit) {
    val shelf by owner.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val lifecycleOwner = LocalLifecycleOwner.current
    val lifecycle by lifecycleOwner.lifecycle.currentStateFlow.collectAsStateWithLifecycle()
    val playback by Graph.readAloud.state.collectAsStateWithLifecycle()
    val locale = androidx.compose.ui.platform.LocalResources.current.configuration.locales[0].toLanguageTag()
    val day by produceState(LocalDate.now()) {
        while (true) { value = LocalDate.now(); delay(15_000) }
    }
    val dateKey = day.toString()
    var selectedTopic by rememberSaveable(dateKey) { mutableStateOf<KidsCardTopic?>(null) }
    val current = shelf?.adventureFor(day, selectedTopic)
    val greeting = stringResource(R.string.halo11_coach_adventure_greeting)
    val speakPrompts = readAloud || shelf?.age == KidsAgeBand.EARLY
    var greeted by rememberSaveable { mutableStateOf<String?>(null) }
    var open by rememberSaveable { mutableStateOf(false) }
    var highlighted by rememberSaveable { mutableStateOf<KidsAdventureStop?>(null) }
    var busy by remember { mutableStateOf(false) }
    var cheer by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    LaunchedEffect(owner) {
        try { owner.load() } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { failed = true }
    }
    val requestHandled by rememberUpdatedState(onRequestHandled)
    LaunchedEffect(requested, shelf) {
        if (requested && shelf != null) {
            open = current != null && Graph.kidsPreferences.enabled.value && !Graph.isHaloPaused()
            requestHandled()
        }
    }
    LaunchedEffect(dateKey, open, welcomeAvailable, lifecycle, current?.topic, speakPrompts) {
        if (speakPrompts && !open && current != null && welcomeAvailable && greeted != dateKey &&
            lifecycle.isAtLeast(Lifecycle.State.RESUMED) && Graph.kidsPreferences.enabled.value && !Graph.isHaloPaused() &&
            VoiceFieldCancellation.activeFields.value.isEmpty() && Graph.readAloud.state.value?.let { it.playing || it.preparing } != true) {
            greeted = dateKey
            val cloudApproved = Graph.privacyModes.mode.value == PrivacyMode.CLOUD && Graph.privacyModes.sharingAcknowledged
            readKidsText(GREETING_ID, greeting, language = locale, localOnly = !cloudApproved)
        } else if ((!speakPrompts || !lifecycle.isAtLeast(Lifecycle.State.RESUMED) || !welcomeAvailable) &&
            Graph.readAloud.state.value?.id == GREETING_ID) {
            Graph.readAloud.stop()
        }
    }
    DisposableEffect(Unit) {
        onDispose { if (Graph.readAloud.state.value?.id == GREETING_ID) Graph.readAloud.stop() }
    }
    if (current == null) return
    fun finish(stop: KidsAdventureStop, drawing: String = "") {
        if (busy || !Graph.kidsPreferences.enabled.value || Graph.isHaloPaused() || !lifecycle.isAtLeast(Lifecycle.State.RESUMED)) return
        val revision = runCatching { Graph.kidsPreferences.presentationRevision() }.getOrNull() ?: return
        busy = true
        failed = false
        scope.launch {
            try {
                owner.finishAdventureStop(current.day, current.topic, stop, drawing) {
                    Graph.kidsPreferences.requirePresentation(revision)
                    check(Graph.kidsPreferences.enabled.value && !Graph.isHaloPaused() &&
                        lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED))
                }
                cheer = current.done.size == current.age.stops.size - 1
                highlighted = null
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { failed = true }
            finally { busy = false }
        }
    }
    fun requestDinosaurs() {
        if (busy || !Graph.kidsPreferences.enabled.value || Graph.isHaloPaused()) return
        val revision = runCatching { Graph.kidsPreferences.presentationRevision() }.getOrNull() ?: return
        busy = true
        scope.launch {
            try {
                owner.requestDinosaurs {
                    Graph.kidsPreferences.requirePresentation(revision)
                    check(Graph.kidsPreferences.enabled.value && !Graph.isHaloPaused() &&
                        lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED))
                }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { failed = true }
            finally { busy = false }
        }
    }
    KidsCard {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            val status = when {
                playback?.let { it.id == GREETING_ID && it.playing } == true -> HaloSignalState.SPEAKING
                busy || playback?.let { it.id == GREETING_ID && it.preparing } == true -> HaloSignalState.THINKING
                current.complete -> HaloSignalState.SUCCESS
                else -> HaloSignalState.IDLE
            }
            HaloSignalFace(HaloSignalPresentation(state = status, reducedMotion = still), size = 48.dp)
            Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.secondaryContainer) {
                Text(greeting, Modifier.padding(horizontal = 12.dp, vertical = 8.dp), style = MaterialTheme.typography.bodyLarge)
            }
        }
        KidsPrimaryButton({ open = true }, Modifier.fillMaxWidth()) {
            Column {
                Text(stringResource(R.string.halo11_coach_adventure))
                Text(stringResource(adventureTitle(current.topic)), style = MaterialTheme.typography.labelMedium)
            }
        }
    }
    if (open) ModalBottomSheet(onDismissRequest = { open = false; highlighted = null; cheer = false },
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxWidth().heightIn(max = 620.dp).verticalScroll(rememberScrollState())
            .navigationBarsPadding().padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (current.complete) {
                if (cheer && !still) HaloFxAnimation(HaloFxMoment.BLOOM_KIDS, Modifier.size(78.dp))
                Text(stringResource(R.string.halo11_coach_adventure_complete), style = MaterialTheme.typography.headlineSmall)
                Text(stringResource(R.string.halo11_coach_adventure_kept))
                AdventurePageTiles(current)
                KidsPrimaryButton({ open = false; cheer = false; onBook() }, Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.halo11_coach_adventure_book))
                }
            } else {
                if (!still) com.halo.app.ui.theme.v13.Halo13Animation(
                    com.halo.app.ui.theme.v13.Halo13Motion.KIDS_ADVENTURE, Modifier.size(72.dp),
                )
                Text(stringResource(adventureTitle(current.topic)), style = MaterialTheme.typography.headlineSmall)
                Text(
                    when (current.age) {
                        KidsAgeBand.EARLY -> stringResource(R.string.kids_stops_early)
                        KidsAgeBand.PRIMARY -> stringResource(R.string.halo11_coach_adventure_stops)
                        KidsAgeBand.OLDER -> stringResource(R.string.kids_stops_older)
                    })
                if (current.done.isEmpty()) {
                    Text(stringResource(R.string.kids_where_today), style = MaterialTheme.typography.titleMedium)
                    val islands = shelf?.topics.orEmpty().let { topics ->
                        (listOf(current.topic) + topics.filterNot { it == current.topic }
                            .sortedByDescending { it == KidsCardTopic.DINOSAURS }).take(current.age.choices)
                    }
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        islands.forEach { topic ->
                            FilterChip(selected = current.topic == topic, onClick = { selectedTopic = topic; highlighted = null },
                                label = { Text(adventureArt(topic).learn + " " + stringResource(adventureTitle(topic))) },
                                modifier = Modifier.heightIn(min = current.age.target.dp), enabled = !busy)
                        }
                    }
                    if (KidsCardTopic.DINOSAURS !in shelf?.topics.orEmpty()) {
                        TextButton(onClick = ::requestDinosaurs, enabled = !busy && shelf?.dinosaurRequest != true,
                            modifier = Modifier.heightIn(min = current.age.target.dp)) {
                            Text(stringResource(if (shelf?.dinosaurRequest == true) R.string.kids_topic_asked
                                else R.string.kids_dinosaurs_request))
                        }
                    }
                }
                Text(stringResource(R.string.halo11_coach_adventure_source), style = MaterialTheme.typography.bodySmall)
                AdventurePageTiles(current)
                for (stop in current.age.stops) {
                    val done = stop in current.done
                    KidsSecondaryButton({ highlighted = stop }, Modifier.fillMaxWidth().heightIn(min = current.age.target.dp), enabled = !done && !busy) {
                        Text((if (done) "✓  " else "") + stringResource(stopTitle(stop)))
                    }
                    if (highlighted == stop && !done) key(stop, current.topic) {
                        val prompt = stringResource(stopPrompt(current.topic, stop))
                        LaunchedEffect(speakPrompts, prompt) {
                            if (speakPrompts) readKidsText("kids-adventure:step", prompt, locale, localOnly = true)
                        }
                        DisposableEffect(Unit) {
                            onDispose { if (Graph.readAloud.state.value?.id == "kids-adventure:step") Graph.readAloud.stop() }
                        }
                        if (stop == KidsAdventureStop.MAKE && current.topic == KidsCardTopic.ANIMALS)
                            KidsFoxLesson(current.age.target, busy) { finish(stop, it) }
                        else AdventureStop(current.topic, stop, busy, current.age.target) { finish(stop) }
                    }
                }
            }
            if (failed) Text(stringResource(R.string.halo11_coach_adventure_failure), style = MaterialTheme.typography.bodySmall)
            TextButton({ open = false; highlighted = null; cheer = false }) { Text(uiText("Close")) }
        }
    }
}

@Composable
private fun AdventureStop(topic: KidsCardTopic, stop: KidsAdventureStop, busy: Boolean, target: Int = 56, onDone: () -> Unit) {
    var tryAgain by remember { mutableStateOf(false) }
    Text(stringResource(stopPrompt(topic, stop)), style = MaterialTheme.typography.bodyLarge)
    if (stop != KidsAdventureStop.LEARN) {
        KidsPrimaryButton(onDone, Modifier.heightIn(min = target.dp), enabled = !busy) { Text(stringResource(R.string.halo11_coach_adventure_mark)) }
        return
    }
    val choices = when (topic) {
        KidsCardTopic.ANIMALS -> listOf(stringResource(R.string.halo11_coach_adventure_bird), stringResource(R.string.halo11_coach_adventure_cat))
        KidsCardTopic.COUNTING -> listOf("1", "2")
        KidsCardTopic.NATURE -> listOf(stringResource(R.string.halo11_coach_adventure_rock), stringResource(R.string.halo11_coach_adventure_sun))
        KidsCardTopic.DINOSAURS -> listOf(stringResource(R.string.kids_dinosaurs_two), stringResource(R.string.kids_dinosaurs_three))
    }
    if (topic == KidsCardTopic.COUNTING) Text("🍎 🍎", style = MaterialTheme.typography.headlineSmall)
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        choices.forEachIndexed { index, label ->
            KidsSecondaryButton({ if (index == 1) onDone() else tryAgain = true }, Modifier.weight(1f).heightIn(min = target.dp), enabled = !busy) {
                Text(label)
            }
        }
    }
    if (tryAgain) Text(stringResource(R.string.halo11_coach_adventure_again))
}

@Composable
private fun AdventurePageTiles(page: KidsAdventurePage) {
    val art = adventureArt(page.topic)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        page.age.stops.forEachIndexed { index, stop ->
            val finished = stop in page.done
            Surface(shape = MaterialTheme.shapes.medium, color = if (finished) MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.surfaceVariant) {
                Text(if (finished) listOf(art.learn, art.make, art.find, art.tell)[index] else "○",
                    Modifier.padding(10.dp), style = MaterialTheme.typography.headlineSmall)
            }
        }
    }
    if (page.drawing.isNotEmpty()) {
        val (copies, ink) = kidsLessonInk(page.drawing)
        com.halo.app.ui.screens.notebook.CanvasSketchPreview(ink, Modifier.fillMaxWidth().height(160.dp),
            mirrorCopies = copies, strokeWidthFraction = .06f)
    }
}

@Composable
internal fun KidsAdventureBookPages(pages: List<KidsAdventurePage>) {
    val locale = androidx.compose.ui.platform.LocalResources.current.configuration.locales[0]
    val date = remember(locale) { DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale) }
    pages.takeLast(12).asReversed().forEach { page ->
        Text(stringResource(adventureTitle(page.topic)) + " · " + date.format(page.day), style = MaterialTheme.typography.titleSmall)
        AdventurePageTiles(page)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun KidsBuddyPlay(owner: KidsCardShelf, onDismiss: () -> Unit) {
    val shelf by owner.state.collectAsStateWithLifecycle()
    val topics = KidsCardTopic.entries.filter { it in shelf?.topics.orEmpty() }
    var chosen by remember { mutableStateOf<KidsCardTopic?>(null) }
    var finished by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    LaunchedEffect(owner) {
        try { owner.load() }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { failed = true }
    }
    val active = chosen?.takeIf { it in topics } ?: topics.firstOrNull()
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxWidth().heightIn(max = 550.dp).verticalScroll(rememberScrollState())
            .navigationBarsPadding().padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(stringResource(R.string.halo11_kids_play), style = MaterialTheme.typography.headlineSmall)
            if (failed) Text(stringResource(R.string.halo11_coach_adventure_failure))
            else if (active == null) Text(stringResource(R.string.halo11_kids_play_no_topics))
            else key(active) {
                val done = finished && chosen == active
                if (done) {
                    Text(stringResource(R.string.halo11_kids_play_nice), style = MaterialTheme.typography.titleLarge)
                    if (topics.size > 1) KidsPrimaryButton({
                        chosen = topics[(topics.indexOf(active) + 1) % topics.size]
                        finished = false
                    }) { Text(stringResource(R.string.halo11_kids_play_next)) }
                } else {
                    AdventureStop(active, KidsAdventureStop.LEARN, false) { chosen = active; finished = true }
                }
            }
            TextButton(onClick = onDismiss) { Text(uiText("Close")) }
        }
    }
}
