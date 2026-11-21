package com.halo.app.ui.zone6

import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.halo.app.R
import com.halo.app.language.uiText
import com.halo.app.ui.components.HaloTextField
import com.halo.app.ui.components.VoiceFieldKind
import com.halo.app.ui.presence.HaloOrbDestination
import com.halo.app.ui.shell.ShellOrbCatalog
import com.halo.app.ui.theme.HaloViewMode
import com.halo.app.ui.theme.rememberVisualPreferences
import kotlinx.coroutines.launch

internal const val WORKSPACE_DOCK_KEY = "workspace.dock"
internal const val WORKSPACE_DOCK_DEFAULT = "notebook"
internal val workspaceDockRoutes = setOf(WORKSPACE_DOCK_DEFAULT) +
    com.halo.app.ui.toolkit.HaloToolkit.tools.map { it.route }

private data class PlacesLayout(val order: List<String>, val hidden: Set<String>) {
    fun encode() = order.joinToString(",") + "|" + hidden.sorted().joinToString(",")

    fun shift(route: String, delta: Int): PlacesLayout {
        val from = order.indexOf(route)
        if (from < 0) return this
        val target = (from + delta).coerceIn(order.indices)
        if (target == from) return this
        return copy(order = order.toMutableList().apply { add(target, removeAt(from)) })
    }
}

private fun readPlaces(raw: String?, available: Set<String>): PlacesLayout {
    val ordered = raw.orEmpty().substringBefore('|').split(',').filter { it in available }
    val hidden = raw.orEmpty().substringAfter('|', "").split(',').filter { it in available }.toSet()
    return PlacesLayout((ordered + available).distinct(), hidden)
}

/** Hiding a tile never alters the navigation catalogue or the results of an explicit search. */
@Composable
internal fun Zone6PlacesLayout(destinations: List<HaloOrbDestination>, current: String?, attentionCount: Int,
    kids: Boolean, onPlace: (String) -> Unit) {
    if (kids) {
        Zone6PlacesTiles(destinations, current, attentionCount, true, onPlace = onPlace)
        return
    }
    val easy = rememberVisualPreferences().viewMode == HaloViewMode.EASY
    val ordering = rememberZone6PageSetting("places.order")
    val places = remember(destinations) {
        (destinations + ShellOrbCatalog.workspaceDestinations(false)).distinctBy { it.route }
    }
    val searchPlaces = remember(places) { (places + ShellOrbCatalog.destinations(false)).distinctBy { it.route } }
    val byRoute = places.associateBy { it.route }
    val available = byRoute.keys
    var expected by remember { mutableStateOf<String?>(null) }
    var draft by remember { mutableStateOf<PlacesLayout?>(null) }
    var query by remember { mutableStateOf("") }
    var showAll by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val original = readPlaces(ordering.value, available)
    val configuration = draft ?: original
    val searchTerm = query.trim()
    val localeChoices = searchPlaces.map { it to uiText(it.title) }
    val filtered = if (searchTerm.isBlank()) {
        configuration.order.filterNot { it in configuration.hidden }.mapNotNull(byRoute::get)
    } else localeChoices.filter { (place, label) ->
        label.contains(searchTerm, ignoreCase = true) || place.title.contains(searchTerm, ignoreCase = true)
    }.map { it.first }
    val visible = if (easy && !showAll && searchTerm.isBlank()) filtered.take(4) else filtered
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (draft == null) {
            HaloTextField(query, { query = it.take(100) }, singleLine = true, kind = VoiceFieldKind.SEARCH,
                modifier = Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.page_places_search)) },
                readText = null)
            Zone6PlacesTiles(visible, current, attentionCount, false, singleColumn = easy, onPlace = onPlace)
            if (visible.isEmpty()) Text(stringResource(R.string.page_places_no_matches))
            if (easy && searchTerm.isBlank() && filtered.size > 4) TextButton(onClick = { showAll = !showAll }) {
                Text(stringResource(if (showAll) R.string.page_places_show_less else R.string.page_places_show_all))
            }
            TextButton(onClick = { expected = ordering.value; draft = original; error = false },
                modifier = Modifier.heightIn(min = 48.dp)) {
                Text(stringResource(R.string.page_places_arrange))
            }
        } else {
            val initial = readPlaces(expected, available)
            Text(stringResource(R.string.page_places_arrange), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(R.string.page_places_arrange_help))
            configuration.order.forEachIndexed { index, route ->
                val place = byRoute[route] ?: return@forEachIndexed
                key(route) {
                    val title = uiText(place.title)
                    PlaceArrangementRow(title, route !in configuration.hidden, index > 0,
                        index < configuration.order.lastIndex, !saving,
                        onVisible = { visiblePlace ->
                            val layout = draft ?: return@PlaceArrangementRow
                            draft = layout.copy(hidden = if (visiblePlace) layout.hidden - route else layout.hidden + route)
                        },
                        onShift = { delta -> draft = draft?.shift(route, delta) })
                }
            }
            Zone6PageApplyRow(configuration != initial, saving, error, ordering.previous != null,
                onUndo = { draft = initial; error = false },
                onRestore = { draft = readPlaces(ordering.previous, available); error = false },
                onApply = {
                    saving = true
                    scope.launch {
                        try {
                            error = !ordering.apply(expected, configuration.encode(), readPlaces(null, available).encode())
                            if (!error) draft = null
                        } finally { saving = false }
                    }
                })
            TextButton(onClick = { draft = null }, enabled = !saving) { Text(uiText("Cancel")) }
        }
    }
}

@Composable
private fun PlaceArrangementRow(title: String, visible: Boolean, canMoveUp: Boolean, canMoveDown: Boolean, enabled: Boolean,
    onVisible: (Boolean) -> Unit, onShift: (Int) -> Unit) {
    val distance = with(LocalDensity.current) { 60.dp.toPx() }
    val shift by rememberUpdatedState(onShift)
    val drag = if (enabled) Modifier.pointerInput(distance) {
        var offset = 0f
        detectDragGesturesAfterLongPress(onDragStart = { offset = 0f }) { change, movement ->
            change.consume()
            offset += movement.y
            if (offset >= distance) {
                shift(1)
                offset -= distance
            } else if (offset <= -distance) {
                shift(-1)
                offset += distance
            }
        }
    } else Modifier
    Row(Modifier.fillMaxWidth().heightIn(min = 56.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, Modifier.weight(1f).then(drag))
        val visibilityLabel = stringResource(R.string.page_places_visibility, title)
        Switch(checked = visible, onCheckedChange = onVisible, enabled = enabled,
            modifier = Modifier.padding(horizontal = 4.dp).semantics { contentDescription = visibilityLabel })
        TextButton(onClick = { onShift(-1) }, enabled = enabled && canMoveUp,
            modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.page_places_up)) }
        TextButton(onClick = { onShift(1) }, enabled = enabled && canMoveDown,
            modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.page_places_down)) }
    }
}
