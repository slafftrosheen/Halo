package com.halo.app.ui.screens.kids

import android.media.MediaPlayer
import androidx.annotation.StringRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.halo.app.R
import com.halo.app.di.ManualDependencyGraph as Graph
import com.halo.app.language.uiText
import com.halo.app.ui.components.VoiceFieldCancellation
import com.halo.app.ui.icons.HaloGlyph
import com.halo.app.ui.icons.HaloIcon
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

private enum class ActivityCard(val id: String, val topic: KidsCardTopic, @StringRes val kind: Int,
    @StringRes val title: Int, @StringRes val words: Int, val art: String) {
    PURR("purr", KidsCardTopic.ANIMALS, R.string.kid_card_fact, R.string.kid_card_cat, R.string.kid_card_purr, "🐱"),
    OCEAN("ocean", KidsCardTopic.ANIMALS, R.string.kid_card_try, R.string.kid_card_ocean, R.string.kid_card_ocean, "🐙"),
    APPLES("apples", KidsCardTopic.COUNTING, R.string.kid_card_count, R.string.kid_card_many, R.string.kid_card_many, "🍎"),
    SUNFLOWER("sunflower", KidsCardTopic.NATURE, R.string.kid_card_story, R.string.kid_card_sunflower, R.string.kid_card_grows, "🌻"),
}

private data class PictureChoice(val art: String, @StringRes val label: Int?, val number: String? = null)

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun KidsDynamicCards(owner: KidsCardShelf, onlyBook: Boolean = false) {
    val state by owner.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val local = androidx.compose.ui.platform.LocalResources.current.configuration.locales[0].toLanguageTag()
    val audio by Graph.readAloud.state.collectAsStateWithLifecycle()
    val speaking = audio?.id?.startsWith("kids-card:") == true && (audio?.playing == true || audio?.preparing == true)
    var loadingError by remember { mutableStateOf(false) }
    var notice by remember { mutableStateOf<Int?>(null) }
    var shelfOpen by rememberSaveable(onlyBook) { mutableStateOf(onlyBook) }
    var position by rememberSaveable { mutableIntStateOf(0) }
    var approvedId by rememberSaveable { mutableStateOf<String?>(null) }
    var favoriteId by rememberSaveable { mutableStateOf<String?>(null) }
    LaunchedEffect(owner) {
        try { owner.load() } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { loadingError = true }
    }
    DisposableEffect(Unit) {
        onDispose { if (Graph.readAloud.state.value?.id?.startsWith("kids-card:") == true) Graph.readAloud.stop() }
    }
    val current = state ?: run {
        if (loadingError) Text(stringResource(R.string.kid_card_unavailable))
        return
    }
    val activities = ActivityCard.entries.filter { it.topic in current.topics }
    val adventurePages = current.adventures.filter { it.complete && it.topic in current.topics }
    if (!onlyBook && activities.isEmpty() && current.approvedAnswers.isEmpty() && adventurePages.isEmpty()) return
    val approved = current.approvedAnswers.firstOrNull { it.id == approvedId }
    val card = if (shelfOpen) activities.firstOrNull { it.id == favoriteId }
        else activities.getOrNull(position.coerceAtMost(activities.lastIndex))
    fun stopCard() { if (Graph.readAloud.state.value?.id?.startsWith("kids-card:") == true) Graph.readAloud.stop() }
    fun read(text: String) {
        if (speaking) stopCard()
        else scope.launch {
            notice = null
            if (!readKidsText("kids-card:${card?.id ?: approved?.id}", text, language = local, localOnly = true))
                notice = R.string.kid_card_voice_unavailable
        }
    }
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (!onlyBook) FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            if (activities.isNotEmpty()) FilterChip(selected = !shelfOpen, onClick = { stopCard(); shelfOpen = false },
                label = { Text(stringResource(R.string.kid_card_explore)) }, modifier = Modifier.heightIn(min = 52.dp))
            FilterChip(selected = shelfOpen || activities.isEmpty(), onClick = { stopCard(); shelfOpen = true },
                label = { Text(stringResource(R.string.halo11_coach_my_book)) }, modifier = Modifier.heightIn(min = 52.dp))
        }
        if (shelfOpen || activities.isEmpty()) {
            val kept = activities.filter { it.id in current.favorites }
            if (kept.isEmpty() && current.approvedAnswers.isEmpty() && adventurePages.isEmpty()) Text(stringResource(R.string.kid_card_empty))
            KidsAdventureBookPages(adventurePages)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(7.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                kept.forEach { saved ->
                    OutlinedButton({ stopCard(); favoriteId = saved.id; approvedId = null }, Modifier.heightIn(min = 52.dp)) {
                        Text("${saved.art} ${stringResource(saved.title)}")
                    }
                }
                current.approvedAnswers.forEach { saved ->
                    OutlinedButton({ stopCard(); approvedId = saved.id; favoriteId = null; shelfOpen = true }, Modifier.heightIn(min = 52.dp)) {
                        Text("📖 ${saved.title}")
                    }
                }
            }
        }
        if (approved != null && shelfOpen) {
            KidsCardSurface(R.string.kid_card_grownup, approved.title) {
                KidsCardArt("📖", stringResource(R.string.kid_card_book), Color(0xFFE4E8FF))
                Text(approved.text, style = MaterialTheme.typography.bodyLarge)
                Text(stringResource(R.string.dynamic_card_source, approved.source), style = MaterialTheme.typography.labelMedium)
                KidsCardReadButton(speaking) { read(approved.text) }
            }
        } else if (card != null) key(card.id) {
            val title = stringResource(card.title)
            val words = stringResource(card.words)
            KidsCardSurface(card.kind, title) {
                when (card) {
                    ActivityCard.PURR -> KidsCardArt(card.art, stringResource(R.string.kid_card_cat_picture), Color(0xFFFFE5D7))
                    ActivityCard.OCEAN -> KidsPictureQuestion(card.id, listOf(
                        PictureChoice("🦊", R.string.kid_card_fox), PictureChoice("🐙", R.string.kid_card_octopus),
                        PictureChoice("🦉", R.string.kid_card_owl), PictureChoice("🐿️", R.string.kid_card_squirrel),
                    ), 1, stringResource(R.string.kid_card_ocean_why))
                    ActivityCard.APPLES -> {
                        val apple = stringResource(R.string.kid_card_apple)
                        KidsCardArt("🍎🍎🍎🍎🍎", List(5) { apple }.joinToString(", "), Color(0xFFFFE5D7))
                        KidsPictureQuestion(card.id, listOf(PictureChoice("", null, "4"), PictureChoice("", null, "5"),
                            PictureChoice("", null, "6")), 1, stringResource(R.string.kid_card_apples_why))
                    }
                    ActivityCard.SUNFLOWER -> KidsCardArt("🌱  🌻  🐝", stringResource(R.string.kid_card_story_picture), Color(0xFFFFF0AD))
                }
                if (card.words != card.title) Text(words, style = MaterialTheme.typography.bodyLarge)
                Text(stringResource(R.string.kid_card_local_source), style = MaterialTheme.typography.labelMedium)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    KidsCardReadButton(speaking) { read(words) }
                    if (card == ActivityCard.PURR) KidsCardPurr(onFailure = { notice = R.string.kid_card_voice_unavailable })
                    val kept = card.id in current.favorites
                    TextButton(onClick = { scope.launch {
                        try { if (kept) owner.forget(card.id) else owner.keep(card.id, card.topic); notice = null }
                        catch (cancelled: CancellationException) { throw cancelled }
                        catch (_: Exception) { notice = R.string.kid_card_unavailable }
                    } }, modifier = Modifier.heightIn(min = 52.dp)) {
                        Text(stringResource(if (kept) R.string.kid_card_remove else R.string.dynamic_card_keep))
                    }
                    if (!shelfOpen && activities.size > 1) TextButton(onClick = {
                        stopCard(); position = (position.coerceAtMost(activities.lastIndex) + 1) % activities.size
                    }, modifier = Modifier.heightIn(min = 52.dp)) { Text(uiText("Next card")) }
                }
            }
        }
        notice?.let { Text(stringResource(it), Modifier.semantics { liveRegion = LiveRegionMode.Polite }) }
        val voiceNotice = audio?.takeIf { it.id.startsWith("kids-card:") }?.notice
        voiceNotice?.let { Text(uiText(it), Modifier.semantics { liveRegion = LiveRegionMode.Polite }) }
    }
}

/** A bundled synthesized sound, released on app stop, screen changes, or the shared Stop action. */
@Composable
private fun KidsCardPurr(onFailure: () -> Unit) {
    val context = LocalContext.current
    var player by remember { mutableStateOf<MediaPlayer?>(null) }
    var audioEpoch by remember { mutableLongStateOf(0L) }
    val stopEpoch by VoiceFieldCancellation.epoch.collectAsStateWithLifecycle()
    val lifecycle = androidx.lifecycle.compose.LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_STOP) player = null
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(stopEpoch) { if (player != null && audioEpoch != stopEpoch) player = null }
    val active = player
    DisposableEffect(active) { onDispose { active?.release() } }
    TextButton(onClick = {
        if (player != null) player = null
        else {
            val next = runCatching { MediaPlayer.create(context, R.raw.halo_kids_card_purr) }.getOrNull()
            if (next == null) onFailure()
            else {
                VoiceFieldCancellation.stop()
                audioEpoch = VoiceFieldCancellation.epoch.value
                player = next
                next.setOnCompletionListener { if (player == next) player = null }
                next.setOnErrorListener { _, _, _ -> if (player == next) player = null; onFailure(); true }
                try { next.start() } catch (_: IllegalStateException) { player = null; next.release(); onFailure() }
            }
        }
    }, modifier = Modifier.heightIn(min = 52.dp)) {
        Text(if (active == null) stringResource(R.string.kid_card_purr_sound) else uiText("Stop"), fontSize = 16.sp)
    }
}

@Composable
private fun KidsCardSurface(@StringRes kind: Int, title: String, content: @Composable () -> Unit) {
    val dark = Color(0xFF242039)
    Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(26.dp), color = Color.White, contentColor = dark) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(kind).uppercase(), style = MaterialTheme.typography.labelMedium, color = Color(0xFF514867))
            Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            content()
        }
    }
}

@Composable
private fun KidsCardArt(art: String, description: String, fill: Color) {
    Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), color = fill, contentColor = Color(0xFF242039)) {
        Box(Modifier.fillMaxWidth().heightIn(min = 104.dp).clearAndSetSemantics { contentDescription = description },
            contentAlignment = Alignment.Center) { Text(art, fontSize = 34.sp) }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun KidsPictureQuestion(id: String, options: List<PictureChoice>, correct: Int, explanation: String) {
    var answer by rememberSaveable(id) { mutableIntStateOf(-1) }
    val ink = Color(0xFF242039)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        options.forEachIndexed { index, option ->
            val color = if (answer == index && answer == correct) Color(0xFFC6F5E1) else Color(0xFFF1EDF5)
            val border = if (answer == index && answer != correct) Color(0xFF705BA4) else color
            OutlinedButton(onClick = { answer = index }, modifier = Modifier.heightIn(min = 52.dp),
                colors = ButtonDefaults.outlinedButtonColors(containerColor = color, contentColor = ink), border = BorderStroke(2.dp, border)) {
                val label = option.label?.let { stringResource(it) } ?: option.number.orEmpty()
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    if (option.art.isNotEmpty()) Text(option.art, Modifier.clearAndSetSemantics { }, fontSize = 26.sp)
                    Text(label, fontSize = 16.sp)
                }
            }
        }
    }
    if (answer >= 0) Text(if (answer == correct) "★ $explanation" else stringResource(R.string.dynamic_card_try_again),
        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }, style = MaterialTheme.typography.bodyLarge)
}

@Composable
private fun KidsCardReadButton(speaking: Boolean, onClick: () -> Unit) {
    Button(onClick = onClick, modifier = Modifier.heightIn(min = 52.dp),
        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFFBB9D), contentColor = Color(0xFF242039))) {
        HaloIcon(if (speaking) HaloGlyph.STOP else HaloGlyph.SPEAK, null, Modifier.size(20.dp))
        Spacer(Modifier.width(6.dp))
        Text(uiText(if (speaking) "Stop reading" else "Read aloud"), fontSize = 16.sp)
    }
}

@Composable
internal fun KidsCardSettings(owner: KidsCardShelf) {
    val state by owner.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var failure by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    LaunchedEffect(owner) {
        try { owner.load() } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { failure = true }
    }
    fun change(update: suspend () -> Unit) {
        if (busy) return
        busy = true
        scope.launch {
            try { update(); failure = false } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { failure = true } finally { busy = false }
        }
    }
    Text(stringResource(R.string.kid_card_family_topics), style = MaterialTheme.typography.titleMedium)
    state?.let { current ->
        KidsCardTopic.entries.forEach { topic ->
            val selected = topic in current.topics
            Row(Modifier.fillMaxWidth().heightIn(min = 52.dp).toggleable(selected, enabled = !busy, role = Role.Switch,
                onValueChange = { enabled -> change { owner.setTopic(topic, enabled) } }), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(when (topic) {
                    KidsCardTopic.ANIMALS -> R.string.kid_card_topic_animals
                    KidsCardTopic.COUNTING -> R.string.kid_card_topic_counting
                    KidsCardTopic.NATURE -> R.string.kid_card_topic_nature
                }), Modifier.weight(1f))
                Switch(selected, onCheckedChange = null, enabled = !busy)
            }
        }
        current.approvedAnswers.forEach { approved ->
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(approved.title, Modifier.weight(1f))
                TextButton(onClick = { change { owner.removeApproved(approved.id) } }, enabled = !busy,
                    modifier = Modifier.heightIn(min = 52.dp)) { Text(uiText("Remove")) }
            }
        }
    }
    if (failure) Text(stringResource(R.string.kid_card_unavailable))
}
