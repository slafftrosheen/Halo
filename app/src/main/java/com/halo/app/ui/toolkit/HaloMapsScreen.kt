package com.halo.app.ui.toolkit

import android.app.KeyguardManager
import android.content.ActivityNotFoundException
import androidx.annotation.StringRes
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import coil.request.CachePolicy
import coil.request.ImageRequest
import com.halo.app.R
import com.halo.app.data.toolkit.HaloMapArrival
import com.halo.app.data.toolkit.HaloMapCoordinate
import com.halo.app.data.toolkit.HaloMapDeparture
import com.halo.app.data.toolkit.HaloMapsStore
import com.halo.app.data.toolkit.HaloSavedPlace
import com.halo.app.di.ManualDependencyGraph as Graph
import com.halo.app.ui.components.HaloTextField
import com.halo.app.ui.components.VoiceFieldKind
import com.halo.app.ui.instruments.HaloInstrumentAction
import com.halo.app.ui.instruments.HaloInstrumentBar
import com.halo.app.everyday.organizer.OrganizerOccurrence
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

private enum class MapsPane(@StringRes val label: Int) {
    SEARCH(R.string.halo11_tool_map_saved), KEEP(R.string.halo11_tool_map_place), ROUTE(R.string.halo11_tool_map_route),
    DEPART(R.string.halo11_tool_map_leave), DISTANCE(R.string.halo11_tool_map_distance), PHOTOS(R.string.halo11_tool_map_photos),
    ARRIVED(R.string.halo11_tool_map_arrived), OFFLINE(R.string.halo11_tool_map_offline_area),
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun HaloMapsScreen(onBack: () -> Unit, placeId: String? = null, eventId: String? = null,
                            occurrenceStart: java.time.Instant? = null,
                            onOffline: (String?, Boolean, String?) -> Unit = { _, _, _ -> }) {
    val context = LocalContext.current
    val locale = LocalConfiguration.current.locales[0]
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val time = remember(locale) {
        DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM).withLocale(locale).withZone(ZoneId.systemDefault())
    }
    val scope = rememberCoroutineScope()
    val family = Graph.kidsPreferences
    val loaded by family.loadedState.collectAsStateWithLifecycle()
    val kids by family.enabled.collectAsStateWithLifecycle()
    val pause by Graph.haloPause.state.collectAsStateWithLifecycle()
    val adult = loaded && !kids
    var mode by rememberSaveable(eventId, occurrenceStart) { mutableStateOf(if (eventId == null) MapsPane.SEARCH else MapsPane.DEPART) }
    var selectedId by rememberSaveable(placeId) { mutableStateOf(placeId) }
    var query by rememberSaveable { mutableStateOf("") }
    var name by rememberSaveable { mutableStateOf("") }
    var coordinatesText by rememberSaveable { mutableStateOf("") }
    var routeStartId by rememberSaveable { mutableStateOf<String?>(null) }
    var routeStartText by rememberSaveable { mutableStateOf("") }
    var travelMode by rememberSaveable { mutableStateOf(OrganicMapsTravelMode.WALK) }
    var choosingStart by remember { mutableStateOf(false) }
    var showPlot by rememberSaveable { mutableStateOf(false) }
    var markedArrivalId by remember { mutableStateOf<String?>(null) }
    var reviewArrivalShare by remember { mutableIntStateOf(0) }
    var layerChoices by remember { mutableStateOf(false) }
    var foreground by remember(lifecycle) { mutableStateOf(lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) }
    var locatePhotos by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var notice by remember { mutableIntStateOf(0) }
    var failed by remember { mutableIntStateOf(0) }
    var reviewHome by remember { mutableStateOf<HaloSavedPlace?>(null) }
    var homeRevision by remember { mutableIntStateOf(0) }
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, _ ->
            foreground = lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
            if (!foreground) locatePhotos = false
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    val privateToolsAllowed = adult && foreground && !pause.paused
    LaunchedEffect(privateToolsAllowed) { if (!privateToolsAllowed) reviewHome = null }
    val organicMapsInstalled = remember(context, foreground) { HaloOrganicMaps.isInstalled(context) }
    val photoToolsAllowed = privateToolsAllowed && locatePhotos && mode in setOf(MapsPane.PHOTOS, MapsPane.ARRIVED)
    val keepPhotosAuthorized by rememberUpdatedState(photoToolsAllowed)
    var photoError by remember { mutableStateOf(false) }
    val photoIndex by produceState<List<HaloMapPhoto>?>(null, photoToolsAllowed) {
        value = null
        photoError = false
        if (!photoToolsAllowed) return@produceState
        try { observeHaloMapPhotos(context.applicationContext) { keepPhotosAuthorized }.collect { value = it } }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { photoError = true }
    }
    val photoRows = photoIndex.takeIf { photoToolsAllowed }
    val visible by produceState(emptyList<HaloSavedPlace>(), adult) {
        if (!adult) { value = emptyList(); return@produceState }
        try { HaloMapsStore.observePlaces().collect { value = it } }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { value = emptyList(); failed = R.string.halo11_tool_map_read_failed }
    }
    val departure by produceState<HaloMapDeparture?>(null, mode, privateToolsAllowed, eventId, occurrenceStart) {
        if (!privateToolsAllowed || mode != MapsPane.DEPART) { value = null; return@produceState }
        while (true) {
            value = try { HaloMapsStore.nextDeparture(eventId = eventId, occurrenceStart = occurrenceStart) }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { failed = R.string.halo11_tool_map_read_failed; null }
            delay(30_000)
        }
    }
    LaunchedEffect(loaded, kids) {
        if (loaded && kids) {
            selectedId = null
            query = ""
            name = ""
            coordinatesText = ""
            routeStartId = null
            routeStartText = ""
            locatePhotos = false
            markedArrivalId = null
            notice = 0
            failed = 0
        }
    }
    LaunchedEffect(pause.version) { if (pause.paused) locatePhotos = false }
    val selected = visible.firstOrNull { it.id == selectedId }
    val homePlace by produceState<HaloSavedPlace?>(null, privateToolsAllowed, visible, homeRevision) {
        value = null
        if (privateToolsAllowed) {
            try { value = HaloMapsStore.homePlace() }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { }
        }
    }
    val coordinates = parsedCoordinate(coordinatesText)
    val routeStart = visible.firstOrNull { it.id == routeStartId }
    val startCoordinates = routeStart?.coordinate ?: parsedCoordinate(routeStartText)
    val routeDestination = selected?.coordinate ?: coordinates
    val currentStart by rememberUpdatedState(startCoordinates)
    val currentDestination by rememberUpdatedState(routeDestination)
    val startReady = (routeStartId == null && routeStartText.isBlank()) || startCoordinates != null
    val startLabel = stringResource(R.string.halo11_tool_map_origin)
    val destinationLabel = stringResource(R.string.halo11_tool_map_destination)
    val canShareArrival = privateToolsAllowed && !busy && (selected != null || coordinates != null) &&
        (selected == null || markedArrivalId != selected.id)
    val arrivalPlace = selected.takeIf { mode == MapsPane.ARRIVED && privateToolsAllowed }
    val arrival by produceState<Pair<HaloSavedPlace, HaloMapArrival>?>(null, arrivalPlace) {
        value = null
        val place = arrivalPlace ?: return@produceState
        while (true) {
            try { value = place to HaloMapsStore.arrived(place) }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { value = null; failed = R.string.halo11_tool_map_read_failed }
            delay(30_000)
        }
    }

    fun show(next: MapsPane) {
        mode = next
        failed = 0
        notice = 0
        if (next != MapsPane.ROUTE) {
            reviewArrivalShare = 0
            choosingStart = false
        }
    }
    fun isUnlockedForeground() = lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED) &&
        context.getSystemService(KeyguardManager::class.java)?.isDeviceLocked == false
    fun markArrival(place: HaloSavedPlace) {
        markedArrivalId = place.id
        show(MapsPane.ARRIVED)
    }
    fun keep() {
        val allowed = coordinates ?: return
        if (!adult || busy || pause.paused || name.isBlank()) return
        busy = true
        scope.launch {
            try {
                selectedId = HaloMapsStore.keep(name, allowed).id
                mode = MapsPane.SEARCH
                notice = R.string.halo11_tool_measure_kept
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { failed = R.string.halo11_tool_map_place_failed }
            finally { busy = false }
        }
    }
    fun dispatchRoute() {
        if (!privateToolsAllowed || busy || !startReady) return
        val place = selected
        val point = routeDestination ?: return
        val origin = routeStart
        val startPoint = startCoordinates
        val transport = travelMode
        val destination = OrganicMapsPoint(point, place?.title ?: destinationLabel)
        val start = startPoint?.let { OrganicMapsPoint(it, origin?.title ?: startLabel) }
        busy = true
        failed = 0
        scope.launch {
            try {
                val approval = family.adultActionRevision()
                val modeVersion = Graph.privacyModes.revision
                val pauseVersion = Graph.haloPause.state.value.version
                if (place != null) require(HaloMapsStore.readPlace(place.id) == place)
                if (origin != null) require(HaloMapsStore.readPlace(origin.id) == origin)
                family.requireAdultAction(approval)
                Graph.haloPause.requireActive()
                check(isUnlockedForeground() && mode == MapsPane.ROUTE && currentDestination == point && currentStart == startPoint &&
                    travelMode == transport && Graph.privacyModes.revision == modeVersion && Graph.haloPause.state.value.version == pauseVersion)
                val uri = if (start == null) HaloOrganicMaps.destination(destination) else HaloOrganicMaps.route(start, destination, transport)
                HaloOrganicMaps.open(context, uri)
                if (markedArrivalId == place?.id) markedArrivalId = null
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: ActivityNotFoundException) { failed = R.string.halo11_tool_map_no_app }
            catch (_: Exception) { failed = R.string.halo11_tool_map_route_failed }
            finally { busy = false }
        }
    }
    fun openProvider(send: () -> Unit) {
        if (!privateToolsAllowed || busy) return
        failed = 0
        try {
            family.adultActionRevision()
            check(isUnlockedForeground())
            Graph.haloPause.requireActive()
            send()
        } catch (_: ActivityNotFoundException) { failed = R.string.halo11_tool_map_no_app }
        catch (_: Exception) { failed = R.string.halo11_tool_map_route_failed }
    }
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Halo11ToolHeader(R.string.halo11_tool_maps_title, onBack)
        Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (adult) {
                TextButton({ onOffline(null, false, null) }, enabled = privateToolsAllowed) {
                    Text(stringResource(R.string.halo11_offline_title))
                }
                homePlace?.let { home ->
                    TextButton({ onOffline(home.id, false, null) }, enabled = privateToolsAllowed && !busy) {
                        Text(stringResource(R.string.halo11_map_go_home))
                    }
                }
                HaloOfflineMapCredits()
                when (mode) {
                    MapsPane.SEARCH -> {
                        HaloTextField(query, { query = it.take(120) }, modifier = Modifier.fillMaxWidth(),
                            kind = VoiceFieldKind.SEARCH, singleLine = true,
                            label = { Text(stringResource(R.string.halo11_tool_map_search)) })
                        if (query.isNotBlank()) {
                            TextButton({ onOffline(null, false, query.trim()) }, enabled = privateToolsAllowed) {
                                Text(stringResource(R.string.halo11_offline_search_places))
                            }
                        }
                        if (query.isNotBlank() && organicMapsInstalled) {
                            TextButton({
                                openProvider { HaloOrganicMaps.open(context, HaloOrganicMaps.search(query.trim(), locale.language)) }
                            }, enabled = privateToolsAllowed) { Text(stringResource(R.string.halo11_tool_map_provider_search)) }
                        }
                        val matches = visible.filter { place ->
                            query.trim().split(Regex("""\s+""")).all { it.isBlank() || place.title.contains(it, ignoreCase = true) }
                        }
                        if (showPlot && matches.isNotEmpty()) PlacePlot(matches, selectedId, { selectedId = it.id })
                        if (matches.isEmpty()) Text(stringResource(R.string.halo11_tool_map_search_empty))
                        matches.take(100).forEach { place ->
                            Card({ selectedId = place.id }, modifier = Modifier.fillMaxWidth()) {
                                ListItem(headlineContent = { Text(place.title) },
                                    supportingContent = { Text(place.coordinate.toString()) },
                                    colors = ListItemDefaults.colors(containerColor = if (selectedId == place.id)
                                        MaterialTheme.colorScheme.secondaryContainer else Color.Transparent))
                            }
                        }
                        if (selected != null) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button({ onOffline(selected.id, false, null) }, enabled = privateToolsAllowed) {
                                Text(stringResource(R.string.halo11_tool_map_route))
                            }
                            OutlinedButton({ markArrival(selected) }, enabled = privateToolsAllowed) {
                                Text(stringResource(R.string.halo11_tool_map_mark_arrived))
                            }
                        }
                        if (selected != null && selected.id != homePlace?.id) TextButton({ reviewHome = selected },
                            enabled = privateToolsAllowed && !busy, modifier = Modifier.heightIn(min = 48.dp)) {
                            Text(stringResource(R.string.halo11_map_set_home))
                        }
                        TextButton({ showPlot = !showPlot }) { Text(stringResource(R.string.halo11_tool_map_local_plot)) }
                    }
                    MapsPane.KEEP -> {
                        OutlinedTextField(name, { name = it.take(120) }, Modifier.fillMaxWidth(), singleLine = true,
                            label = { Text(stringResource(R.string.halo11_tool_map_label)) }, enabled = !busy)
                        CoordinateField(coordinatesText, { coordinatesText = it; selectedId = null }, R.string.halo11_tool_map_coordinates)
                        if (coordinatesText.isNotBlank() && coordinates == null) Text(stringResource(R.string.halo11_tool_map_invalid))
                        Button(::keep, enabled = coordinates != null && name.isNotBlank() && !pause.paused && !busy) {
                            Text(stringResource(R.string.halo11_tool_map_place))
                        }
                    }
                    MapsPane.ROUTE -> {
                        Text(stringResource(R.string.halo11_tool_map_route_help))
                        if (selected != null) {
                            Text(selected.title, style = MaterialTheme.typography.titleLarge)
                            Text(selected.coordinate.toString())
                            TextButton({ selectedId = null }) { Text(stringResource(R.string.halo11_tool_map_select)) }
                        } else {
                            CoordinateField(coordinatesText, { coordinatesText = it }, R.string.halo11_tool_map_coordinates)
                            if (coordinatesText.isNotBlank() && coordinates == null) Text(stringResource(R.string.halo11_tool_map_invalid))
                        }
                        Box {
                            if (visible.any { it.id != selected?.id }) {
                                TextButton({ choosingStart = true }, enabled = !busy) {
                                    Text(stringResource(R.string.halo11_tool_map_pick_origin))
                                }
                                DropdownMenu(choosingStart, onDismissRequest = { choosingStart = false }) {
                                    DropdownMenuItem(text = { Text(stringResource(R.string.halo11_tool_map_start_in_provider)) }, onClick = {
                                        routeStartId = null
                                        routeStartText = ""
                                        choosingStart = false
                                    })
                                    visible.filterNot { it.id == selected?.id }.take(100).forEach { source ->
                                        DropdownMenuItem(text = { Text(source.title) }, onClick = {
                                            routeStartId = source.id
                                            routeStartText = ""
                                            choosingStart = false
                                        })
                                    }
                                }
                            }
                        }
                        if (routeStart != null) {
                            Text(stringResource(R.string.halo11_tool_map_selected_origin, routeStart.title))
                            Text(routeStart.coordinate.toString())
                            TextButton({ routeStartId = null; routeStartText = "" }, enabled = !busy) {
                                Text(stringResource(R.string.halo11_tool_map_start_in_provider))
                            }
                        } else {
                            CoordinateField(routeStartText, { routeStartId = null; routeStartText = it }, R.string.halo11_tool_map_origin_optional)
                            if (!startReady) Text(stringResource(R.string.halo11_tool_map_invalid), color = MaterialTheme.colorScheme.error)
                        }
                        if (startCoordinates != null) {
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OrganicMapsTravelMode.entries.forEach { type ->
                                    FilterChip(type == travelMode, { travelMode = type }, enabled = !busy,
                                        label = { Text(stringResource(type.label)) })
                                }
                            }
                        } else Text(stringResource(R.string.halo11_tool_map_provider_pick_start), style = MaterialTheme.typography.bodySmall)
                        if (organicMapsInstalled) {
                            Button(::dispatchRoute, enabled = routeDestination != null && privateToolsAllowed && startReady && !busy) {
                                Text(stringResource(if (startCoordinates == null) R.string.halo11_tool_map_open else R.string.halo11_tool_map_provider_route))
                            }
                        } else {
                            Text(stringResource(R.string.halo11_tool_map_no_app))
                            TextButton({ show(MapsPane.OFFLINE) }, enabled = privateToolsAllowed) {
                                Text(stringResource(R.string.halo11_tool_map_offline_area))
                            }
                        }
                        if (selected != null) OutlinedButton({ markArrival(selected) }, enabled = privateToolsAllowed) {
                            Text(stringResource(R.string.halo11_tool_map_mark_arrived))
                        }
                        if (selected == null || markedArrivalId != selected.id) {
                            HaloMapShareArrival(canShareArrival, reviewArrivalShare)
                        }
                    }
                    MapsPane.DEPART -> {
                        val next = departure
                        if (next == null) Text(stringResource(R.string.halo11_tool_map_leave_none))
                        else {
                            Text(next.title, style = MaterialTheme.typography.titleLarge)
                            val leaveBy = stringResource(R.string.halo11_tool_map_leave_at, time.format(next.departAt))
                            Text(leaveBy)
                            next.destination?.let { destination ->
                                Text(destination, style = MaterialTheme.typography.bodyMedium)
                                Button(onClick = {
                                    busy = true
                                    scope.launch {
                                        try {
                                            check(isUnlockedForeground())
                                            val current = HaloMapsStore.nextDeparture(eventId = eventId, occurrenceStart = occurrenceStart)
                                            check(current == next && isUnlockedForeground())
                                            val saved = visible.singleOrNull { it.title.equals(destination, ignoreCase = true) }
                                            onOffline(saved?.id, false, destination.takeIf { saved == null })
                                        } catch (cancelled: CancellationException) { throw cancelled }
                                        catch (_: Exception) { failed = R.string.halo11_tool_map_route_failed }
                                        finally { busy = false }
                                    }
                                }, enabled = privateToolsAllowed && !busy) {
                                    Text(stringResource(R.string.halo11_tool_map_route))
                                }
                            }
                            val summary = stringResource(R.string.halo11_tool_map_summary, next.title, leaveBy)
                            key(next) {
                                HaloReviewedDeviceText(summary, locale.toLanguageTag(), privateToolsAllowed) {
                                    HaloMapsStore.nextDeparture(eventId = eventId, occurrenceStart = occurrenceStart) == next
                                }
                            }
                        }
                    }
                    MapsPane.DISTANCE -> MapDistance(visible)
                    MapsPane.OFFLINE -> {
                        Text(stringResource(R.string.halo11_tool_map_offline_area), style = MaterialTheme.typography.titleLarge)
                        Button({ onOffline(null, true, null) }, enabled = privateToolsAllowed) {
                            Text(stringResource(R.string.halo11_offline_search_regions))
                        }
                        Text(stringResource(R.string.halo11_tool_map_download_help))
                        Text(stringResource(R.string.halo11_tool_map_provider_capabilities), style = MaterialTheme.typography.bodySmall)
                        if (organicMapsInstalled) Button({
                            openProvider { HaloOrganicMaps.openOfflineAreas(context) }
                        }, enabled = privateToolsAllowed) { Text(stringResource(R.string.halo11_tool_map_open)) }
                        else Button({
                            openProvider { HaloOrganicMaps.openHomepage(context) }
                        }, enabled = privateToolsAllowed) { Text(stringResource(R.string.halo11_tool_map_get_provider)) }
                    }
                    MapsPane.PHOTOS -> MapPhotosPanel(
                        enabled = photoToolsAllowed,
                        mayEnable = privateToolsAllowed,
                        onToggle = { locatePhotos = it },
                        photos = photoRows,
                        failed = photoError,
                    )
                    MapsPane.ARRIVED -> {
                        val here = arrivalPlace
                        if (here == null) Text(stringResource(R.string.halo11_tool_map_arrived_unavailable))
                        else {
                            Text(here.title, style = MaterialTheme.typography.titleLarge)
                            Text(stringResource(R.string.halo11_tool_map_arrived_manual))
                            val recent = arrival?.takeIf { it.first == here }?.second
                            if (recent != null) {
                                MapArrivalEvents(R.string.halo11_tool_map_arrived_next, recent.upcoming, time)
                                MapArrivalEvents(R.string.halo11_tool_map_arrived_past, recent.previous, time)
                            } else if (failed == 0) LinearProgressIndicator(Modifier.fillMaxWidth())
                            val nearby = remember(here, photoRows) {
                                photoRows?.filter { HaloMapsStore.straightLineKm(here.coordinate, it.coordinate) <= .25 }
                            }
                            MapPhotosPanel(photoToolsAllowed, privateToolsAllowed, { locatePhotos = it }, nearby, photoError)
                        }
                    }
                }
            } else Text(stringResource(R.string.halo11_tool_map_disabled))
            if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            if (notice != 0) Text(stringResource(notice))
            if (failed != 0) Text(stringResource(failed), color = MaterialTheme.colorScheme.error)
        }
        if (adult) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                MapsPane.entries.filterNot { it == MapsPane.ARRIVED }.forEach { pane ->
                    FilterChip(mode == pane, { show(pane) }, enabled = !busy, label = { Text(stringResource(pane.label)) })
                }
            }
            Box {
                DropdownMenu(layerChoices, onDismissRequest = { layerChoices = false }) {
                    DropdownMenuItem(text = { Text(stringResource(R.string.halo11_tool_map_saved)) }, onClick = {
                        show(MapsPane.SEARCH)
                        showPlot = true
                        layerChoices = false
                    })
                    DropdownMenuItem(text = { Text(stringResource(R.string.halo11_tool_map_photos)) }, onClick = {
                        show(MapsPane.PHOTOS)
                        layerChoices = false
                    })
                }
            }
            HaloInstrumentBar("maps", mapOf(
                "maps.search" to HaloInstrumentAction({ show(MapsPane.SEARCH) }, !busy, mode == MapsPane.SEARCH),
                "maps.leave_by" to HaloInstrumentAction({ show(MapsPane.DEPART) }, !busy, mode == MapsPane.DEPART),
                "maps.route" to HaloInstrumentAction({ show(MapsPane.ROUTE) }, !busy, mode == MapsPane.ROUTE),
                "maps.turn" to HaloInstrumentAction({ onOffline(selectedId, false, null) }, privateToolsAllowed && !busy),
                "maps.layers" to HaloInstrumentAction({ layerChoices = true }, !busy, showPlot || mode == MapsPane.PHOTOS),
                "maps.measure" to HaloInstrumentAction({ show(MapsPane.DISTANCE) }, !busy, mode == MapsPane.DISTANCE),
                "maps.save_place" to HaloInstrumentAction({ show(MapsPane.KEEP) }, !busy, mode == MapsPane.KEEP),
                "maps.photos_here" to HaloInstrumentAction({ show(MapsPane.PHOTOS) }, !busy, mode == MapsPane.PHOTOS),
                "maps.offline" to HaloInstrumentAction({ onOffline(null, true, null) }, privateToolsAllowed && !busy, mode == MapsPane.OFFLINE),
                "maps.share_arrival" to HaloInstrumentAction({ show(MapsPane.ROUTE); reviewArrivalShare++ }, canShareArrival),
            ), primary = "maps.search")
        }
    }
    reviewHome?.takeIf { privateToolsAllowed }?.let { home ->
        AlertDialog(onDismissRequest = { reviewHome = null },
            title = { Text(stringResource(R.string.halo11_map_set_home)) },
            text = { Text(stringResource(R.string.halo11_map_set_home_review, home.title)) },
            confirmButton = {
                TextButton(onClick = {
                    reviewHome = null
                    busy = true
                    scope.launch {
                        try {
                            HaloMapsStore.setHomePlace(home) { check(isUnlockedForeground()) }
                            homeRevision++
                            notice = R.string.halo11_map_home_saved
                        } catch (cancelled: CancellationException) { throw cancelled }
                        catch (_: Exception) { failed = R.string.halo11_tool_map_place_failed }
                        finally { busy = false }
                    }
                }, enabled = !busy) { Text(stringResource(R.string.halo11_map_set_home)) }
            },
            dismissButton = { TextButton(onClick = { reviewHome = null }) { Text(stringResource(android.R.string.cancel)) } })
    }
}

@Composable
private fun CoordinateField(value: String, onValue: (String) -> Unit, @StringRes label: Int) {
    OutlinedTextField(value, { onValue(it.take(48)) }, modifier = Modifier.fillMaxWidth(), singleLine = true,
        label = { Text(stringResource(label)) })
}

private fun parsedCoordinate(value: String): HaloMapCoordinate? = runCatching { HaloMapsStore.coordinate(value) }.getOrNull()

@Composable
private fun MapDistance(places: List<HaloSavedPlace>) {
    val locale = LocalConfiguration.current.locales[0]
    var from by rememberSaveable { mutableStateOf("") }
    var to by rememberSaveable { mutableStateOf("") }
    var nextTarget by rememberSaveable { mutableStateOf(false) }
    val fromPoint = parsedCoordinate(from)
    val toPoint = parsedCoordinate(to)
    if (places.isNotEmpty()) PlacePlot(places, null) { point ->
        if (nextTarget) to = point.coordinate.toString() else from = point.coordinate.toString()
        nextTarget = !nextTarget
    }
    CoordinateField(from, { from = it }, R.string.halo11_tool_map_distance_from)
    CoordinateField(to, { to = it }, R.string.halo11_tool_map_distance_to)
    if (fromPoint != null && toPoint != null) {
        val km = String.format(locale, "%.3f", HaloMapsStore.straightLineKm(fromPoint, toPoint))
        Text(stringResource(R.string.halo11_tool_map_distance_result, km), style = MaterialTheme.typography.titleLarge)
    } else if (from.isNotBlank() && to.isNotBlank()) Text(stringResource(R.string.halo11_tool_map_invalid))
}

@Composable
private fun PlacePlot(places: List<HaloSavedPlace>, selectedId: String?, choose: (HaloSavedPlace) -> Unit) {
    val byId = remember(places) { places.associateBy { it.id } }
    val dots = remember(places) { places.map { MapPlotDot(it.id, it.coordinate) } }
    MapCoordinatePlot(dots, selectedId, stringResource(R.string.halo11_tool_map_local_plot)) { id ->
        byId[id]?.let(choose)
    }
}

private data class MapPlotDot(val id: String, val coordinate: HaloMapCoordinate)

@Composable
private fun MapCoordinatePlot(
    points: List<MapPlotDot>, selectedId: String?, label: String, choose: (String) -> Unit,
) {
    val dots = points.take(250)
    if (dots.isEmpty()) return
    val faint = MaterialTheme.colorScheme.outlineVariant
    val tint = MaterialTheme.colorScheme.primary
    val highlighted = MaterialTheme.colorScheme.tertiary
    val current by rememberUpdatedState(choose)
    val lowX = dots.minOf { it.coordinate.longitude }
    val highX = dots.maxOf { it.coordinate.longitude }
    val lowY = dots.minOf { it.coordinate.latitude }
    val highY = dots.maxOf { it.coordinate.latitude }
    fun plot(width: Float, height: Float): List<Offset> {
        val dx = (highX - lowX).coerceAtLeast(.0001)
        val dy = (highY - lowY).coerceAtLeast(.0001)
        return dots.map { dot ->
            val longitude = if (highX == lowX) .5 else (dot.coordinate.longitude - lowX) / dx
            val latitude = if (highY == lowY) .5 else 1 - (dot.coordinate.latitude - lowY) / dy
            Offset((width * (.1 + longitude * .8)).toFloat(), (height * (.1 + latitude * .8)).toFloat())
        }
    }
    Text(label, style = MaterialTheme.typography.labelMedium)
    Canvas(Modifier.fillMaxWidth().height(192.dp).pointerInput(dots) {
        detectTapGestures { tap ->
            val points = plot(size.width.toFloat(), size.height.toFloat())
            val chosen = points.indices.minByOrNull { (points[it] - tap).getDistance() }
            if (chosen != null && (points[chosen] - tap).getDistance() < 28.dp.toPx()) current(dots[chosen].id)
        }
    }) {
        (1..4).forEach { index ->
            val x = size.width * index / 5
            val y = size.height * index / 5
            drawLine(faint, Offset(x, 0f), Offset(x, size.height))
            drawLine(faint, Offset(0f, y), Offset(size.width, y))
        }
        plot(size.width, size.height).forEachIndexed { index, point ->
            val chosen = dots[index].id == selectedId
            drawCircle(if (chosen) highlighted else tint, if (chosen) 8.dp.toPx() else 5.dp.toPx(), point)
        }
    }
}

@Composable
private fun MapArrivalEvents(@StringRes heading: Int, events: List<OrganizerOccurrence>, time: DateTimeFormatter) {
    Text(stringResource(heading), style = MaterialTheme.typography.titleMedium)
    if (events.isEmpty()) Text(stringResource(R.string.halo11_tool_map_arrived_no_events))
    for (event in events) {
        ListItem(headlineContent = { Text(event.record.title) }, supportingContent = {
            Text(time.format(event.start))
        })
    }
}

@Composable
private fun MapPhotosPanel(
    enabled: Boolean, mayEnable: Boolean, onToggle: (Boolean) -> Unit, photos: List<HaloMapPhoto>?, failed: Boolean,
) {
    var selectedId by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(enabled) { if (!enabled) selectedId = null }
    Text(stringResource(R.string.halo11_tool_map_photos), style = MaterialTheme.typography.titleMedium)
    Text(stringResource(R.string.halo11_tool_map_photos_privacy), style = MaterialTheme.typography.bodySmall)
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
        Switch(enabled, onToggle, enabled = mayEnable)
        Text(stringResource(R.string.halo11_tool_map_photos_enable))
    }
    if (!enabled) return
    if (photos.isNullOrEmpty()) {
        when {
            failed -> Text(stringResource(R.string.halo11_tool_map_photos_error))
            photos == null -> LinearProgressIndicator(Modifier.fillMaxWidth())
            else -> Text(stringResource(R.string.halo11_tool_map_photos_empty))
        }
        return
    }
    val dots = remember(photos) { photos.map { MapPlotDot(it.id, it.coordinate) } }
    MapCoordinatePlot(dots, selectedId, stringResource(R.string.halo11_tool_map_photos_plot)) { selectedId = it }
    val photo = photos.firstOrNull { it.id == selectedId }
    if (photo != null) {
        val context = LocalContext.current
        val request = remember(context, photo.uri) {
            ImageRequest.Builder(context).data(photo.uri).diskCachePolicy(CachePolicy.DISABLED)
                .memoryCachePolicy(CachePolicy.DISABLED).build()
        }
        AsyncImage(request, contentDescription = photo.title, modifier = Modifier.fillMaxWidth().height(200.dp))
    }
    for (item in photos.take(24)) {
        TextButton({ selectedId = item.id }) { Text(item.title) }
    }
}
