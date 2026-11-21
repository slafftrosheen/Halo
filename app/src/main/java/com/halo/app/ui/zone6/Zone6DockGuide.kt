package com.halo.app.ui.zone6

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import com.halo.app.R
import com.halo.app.language.uiText
import com.halo.app.ui.presence.HaloOrbContextAction
import com.halo.app.ui.theme.HaloAccent
import com.halo.app.ui.theme.brandAccent
import com.halo.app.ui.zone5.Zone5GuideSession

internal object Zone6GuideReplay {
    var request by mutableIntStateOf(0)
        private set
    var finished by mutableIntStateOf(0)
        private set
    var tour by mutableStateOf<Zone6Tour?>(null)
        private set
    fun replay(tour: Zone6Tour? = null) {
        this.tour = tour
        request++
    }
    fun finish() {
        finished = request
        tour = null
    }
}

internal enum class Zone6GuideTarget { PLACE, ACTIONS, NAVIGATION, RING, COMPOSER }
internal val LocalZone6GuideTarget = compositionLocalOf<Zone6GuideTarget?> { null }

@Composable
internal fun Modifier.zone6GuideOutline(target: Zone6GuideTarget): Modifier =
    if (LocalZone6GuideTarget.current == target)
        border(2.dp, if (Zone6GuideReplay.tour == null) MaterialTheme.colorScheme.primary
            else brandAccent(MaterialTheme.colorScheme.surface.luminance() < .5f, HaloAccent.LILAC).ink,
            RoundedCornerShape(16.dp))
    else this

private data class TourCard(val title: String, val text: String, val target: Zone6GuideTarget)

@Composable
private fun tourCards(tour: Zone6Tour, actions: List<HaloOrbContextAction>, compact: Boolean): List<TourCard> {
    val name = stringResource(tour.title)
    val location = TourCard(name, stringResource(R.string.help_tour_place), Zone6GuideTarget.PLACE)
    val navigation = TourCard(stringResource(R.string.help_tour_places_title),
        stringResource(R.string.help_tour_places), Zone6GuideTarget.NAVIGATION)
    val ring = TourCard(stringResource(R.string.help_tour_ring_title),
        stringResource(R.string.help_tour_ring), Zone6GuideTarget.RING)
    val composer = TourCard(stringResource(R.string.help_tour_input_title),
        stringResource(R.string.help_tour_input), Zone6GuideTarget.COMPOSER)
    val buttons = actions.filter { it.enabled && it.id.removePrefix("page:") in tour.actionIds }
        .map { uiText(it.title) }.distinct()
    val tool = if (buttons.isEmpty()) null else TourCard(
        stringResource(R.string.help_tour_actions_title),
        stringResource(R.string.help_tour_actions, buttons.joinToString(" · ")), Zone6GuideTarget.ACTIONS)
    return when (tour) {
        Zone6Tour.HOME -> listOf(location, composer, navigation, ring)
        Zone6Tour.TALK -> listOf(ring, composer)
        else -> listOfNotNull(location, tool, navigation)
    }.filterNot { compact && it.target in setOf(Zone6GuideTarget.PLACE, Zone6GuideTarget.ACTIONS) }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun Zone6DockGuide(
    family: String?, session: Zone5GuideSession?, eligible: Boolean, kids: Boolean,
    route: String?, actions: List<HaloOrbContextAction>, compact: Boolean,
): Zone6GuideTarget? {
    val tour = Zone6GuideReplay.tour
    var arrived by remember(Zone6GuideReplay.request) { mutableStateOf(false) }
    var tourStep by remember(Zone6GuideReplay.request) { mutableIntStateOf(0) }
    val atDestination = tour?.atRoute(route) == true
    LaunchedEffect(tour, atDestination) {
        if (tour != null && atDestination) arrived = true
        if (tour != null && !atDestination && arrived) Zone6GuideReplay.finish()
    }
    if (tour != null) {
        if (!eligible || !atDestination || kids) return null
        val cards = tourCards(tour, actions, compact)
        if (cards.isEmpty()) return null
        val step = tourStep.coerceIn(cards.indices)
        val card = cards[step]
        fun close(completed: Boolean) {
            if (completed) session?.dismiss(tour.seenKey)
            family?.let { session?.dismiss("dock.v6.$it") }
            Zone6GuideReplay.finish()
        }
        Zone6ButtonSheet(card.title, { close(false) }) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
                .semantics { liveRegion = LiveRegionMode.Polite }, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.help_tour_step, step + 1, cards.size), style = MaterialTheme.typography.labelLarge)
                Text(card.text)
                FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = { close(false) }, modifier = Modifier.heightIn(min = 48.dp)) {
                        Text(stringResource(R.string.help_tour_exit))
                    }
                    TextButton(onClick = { tourStep = step - 1 }, enabled = step > 0, modifier = Modifier.heightIn(min = 48.dp)) {
                        Text(uiText("Back"))
                    }
                    Button(onClick = { if (step == cards.lastIndex) close(true) else tourStep = step + 1 },
                        modifier = Modifier.heightIn(min = 48.dp)) {
                        Text(uiText(if (step == cards.lastIndex) "Done" else "Next"))
                    }
                }
            }
        }
        return card.target
    }
    val replay = Zone6GuideReplay.request != Zone6GuideReplay.finished
    val tools = setOf("studio", "note", "canvas", "file_tools", "memory", "study", "kids")
    val guideKey = if (replay) "dock.v6.replay" else "dock.v6." + family
    val seen = !replay && (family !in tools || session == null || session.suppressed(guideKey))
    var step by remember(guideKey, Zone6GuideReplay.request) { mutableIntStateOf(0) }
    if (!eligible || seen) return null
    fun finish() {
        if (replay) Zone6GuideReplay.finish() else session?.dismiss(guideKey)
    }
    val cards = if (kids) listOf(
        "These are your buttons" to "Choose Type or Help here.",
        "Talk to halo" to "Tap the ring when you want to talk.",
    ) else listOf(
        "This tells you where you are" to "Look here for the current place and what halo is doing.",
        "Your buttons for this page" to "The filled button is the most useful action here. More has the rest.",
        "Go anywhere" to "Open Home, Workspace or Activity here. Places shows every destination.",
        "Tap the ring and just talk" to "Hold the ring to open Places.",
    )
    val index = step.coerceIn(cards.indices)
    val (title, message) = cards[index]
    val target = if (kids) listOf(Zone6GuideTarget.ACTIONS, Zone6GuideTarget.RING)[index]
        else Zone6GuideTarget.entries[index]
    val minimum = if (kids) 56.dp else 48.dp
    Zone6ButtonSheet(uiText(title), ::finish) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
            .semantics { liveRegion = LiveRegionMode.Polite }, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(uiText(message))
            if (kids) com.halo.app.ui.components.HaloReadAloud(uiText(message), compact = true, category = com.halo.sdk.provider.RemoteDataCategory.TEXT)
            LinearProgressIndicator(progress = { (step + 1f) / cards.size }, modifier = Modifier.fillMaxWidth())
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(::finish, Modifier.heightIn(min = minimum)) { Text(uiText("Skip")) }
                Button(onClick = { if (step == cards.lastIndex) finish() else step++ },
                    modifier = Modifier.heightIn(min = minimum)) { Text(uiText(if (step == cards.lastIndex) "Done" else "Next")) }
            }
        }
    }
    return target
}
