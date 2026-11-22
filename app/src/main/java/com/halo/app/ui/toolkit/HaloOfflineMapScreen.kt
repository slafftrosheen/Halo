package com.halo.app.ui.toolkit

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.organicmaps.sdk.Framework
import app.organicmaps.sdk.MapController
import app.organicmaps.sdk.MapRenderingListener
import app.organicmaps.sdk.MapStyle
import app.organicmaps.sdk.MapView
import app.organicmaps.sdk.OrganicMaps
import app.organicmaps.sdk.Router
import app.organicmaps.sdk.downloader.CountryItem
import app.organicmaps.sdk.util.Distance
import com.halo.app.R
import com.halo.app.data.toolkit.HaloMapsStore
import com.halo.app.di.ManualDependencyGraph as Graph
import com.halo.app.ui.screens.notebook.ToolZoneHeader
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first

private val openNowSuffix = Regex("""\s+(?:open now|открыт[аоы]? сейчас)\s*$""", RegexOption.IGNORE_CASE)

internal fun offlineMapSearch(raw: String): Pair<String, Boolean> {
    val query = raw.trim().take(120)
    val term = query.replace(openNowSuffix, "").trimEnd()
    return term to (term != query)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun HaloOfflineMapScreen(onBack: () -> Unit, placeId: String? = null, downloadsFirst: Boolean = false,
    initialQuery: String = "") {
    val context = LocalContext.current
    val paused by Graph.haloPause.state.collectAsStateWithLifecycle()
    val canUse = !paused.paused && Graph.kidsPreferences.loadedState.value && !Graph.kidsPreferences.enabled.value
    val provider by HaloOfflineMapEngine.state.collectAsStateWithLifecycle()
    LaunchedEffect(canUse) { if (canUse) HaloOfflineMapEngine.initialize(context) }
    Column(Modifier.fillMaxSize()) {
        ToolZoneHeader(stringResource(R.string.halo11_offline_title), onBack) {}
        when (val readiness = provider) {
            HaloOfflineMapEngine.Readiness.Loading -> if (canUse) LinearProgressIndicator(Modifier.fillMaxWidth())
            HaloOfflineMapEngine.Readiness.Failed -> Text(stringResource(R.string.halo11_offline_unavailable), Modifier.padding(16.dp))
            is HaloOfflineMapEngine.Readiness.Ready -> if (canUse) HaloOfflineMapBody(readiness.sdk, placeId, downloadsFirst, initialQuery)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun HaloOfflineMapBody(sdk: OrganicMaps, placeId: String?, downloadsFirst: Boolean, initialQuery: String) {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    val lifecycle = owner.lifecycle
    val locale = LocalConfiguration.current.locales[0]
    val dark = isSystemInDarkTheme()
    val session = remember(sdk, owner) {
        HaloOfflineRouteSession(sdk, context.applicationContext) {
            lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED) &&
                !Graph.haloPause.isPaused() && runCatching { requireAdultToolkit(context) }.isSuccess
        }
    }
    val phase by session.phase.collectAsStateWithLifecycle()
    val regions by session.regions.collectAsStateWithLifecycle()
    val places by session.places.collectAsStateWithLifecycle()
    val position by session.position.collectAsStateWithLifecycle()
    val destination by session.destination.collectAsStateWithLifecycle()
    val navigation by session.navigation.collectAsStateWithLifecycle()
    val transit by session.transit.collectAsStateWithLifecycle()
    val fetching by session.searching.collectAsStateWithLifecycle()
    val progress by session.progress.collectAsStateWithLifecycle()
    var regionMode by rememberSaveable(downloadsFirst) { mutableStateOf(downloadsFirst) }
    val initial = remember(initialQuery) { offlineMapSearch(initialQuery) }
    var query by rememberSaveable(initialQuery) { mutableStateOf(initial.first) }
    var openNow by rememberSaveable(initialQuery) { mutableStateOf(initial.second) }
    var profile by rememberSaveable { mutableIntStateOf(0) }
    var readyToRender by remember { mutableStateOf(false) }
    var graphicsFailed by remember { mutableStateOf(false) }
    var error by remember { mutableIntStateOf(0) }
    var permissionRequested by remember { mutableStateOf(false) }
    var startAfterGrant by remember { mutableStateOf(false) }
    var regionReview by remember { mutableStateOf<HaloOfflineRegion?>(null) }
    var controller by remember { mutableStateOf<MapController?>(null) }
    val locationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { allowed ->
        permissionRequested = false
        if (allowed[Manifest.permission.ACCESS_FINE_LOCATION] == true) startAfterGrant = true
        else error = R.string.halo11_offline_location
    }
    DisposableEffect(session, lifecycle) {
        val active = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> session.attach()
                Lifecycle.Event.ON_PAUSE -> { regionReview = null; session.detach() }
                else -> Unit
            }
        }
        lifecycle.addObserver(active)
        if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) session.attach()
        onDispose {
            lifecycle.removeObserver(active)
            session.detach()
            controller?.let {
                lifecycle.removeObserver(it)
                it.onDestroy(owner)
            }
        }
    }
    LaunchedEffect(placeId) {
        if (placeId != null) {
            try {
                lifecycle.currentStateFlow.first { it.isAtLeast(Lifecycle.State.RESUMED) }
                HaloMapsStore.readPlace(placeId)?.let { session.select(HaloOfflineDestination(it.title, it.coordinate)) }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { error = R.string.halo11_offline_unavailable }
        }
    }
    LaunchedEffect(startAfterGrant) {
        if (startAfterGrant) {
            lifecycle.currentStateFlow.first { it.isAtLeast(Lifecycle.State.RESUMED) }
            session.attach()
            session.startLocation()
            startAfterGrant = false
        }
    }
    LaunchedEffect(dark, readyToRender) {
        val style = if (dark) MapStyle.Dark else MapStyle.Clear
        if (readyToRender) MapStyle.set(style) else MapStyle.mark(style)
    }
    LaunchedEffect(readyToRender, destination) {
        if (readyToRender) destination?.let { Framework.nativeZoomToPoint(it.point.latitude, it.point.longitude, 16, false) }
    }

    val routing = phase == HaloOfflinePhase.NAVIGATING
    val liveArrival by produceState<com.halo.app.data.toolkit.HaloMapArrival?>(null, phase, placeId, destination) {
        value = null
        if (phase == HaloOfflinePhase.ARRIVED && placeId != null) {
            val current = destination
            try {
                val saved = HaloMapsStore.readPlace(placeId)
                if (saved != null && saved.coordinate == current?.point) value = HaloMapsStore.arrived(saved)
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { }
        }
    }
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        if (!graphicsFailed) {
            AndroidView(factory = { mapContext ->
                MapView(mapContext).also { view ->
                    MapStyle.mark(if (dark) MapStyle.Dark else MapStyle.Clear)
                    val rendering = object : MapRenderingListener {
                        override fun onRenderingCreated() { readyToRender = true }
                        override fun onRenderingRestored() { readyToRender = true }
                    }
                    controller = MapController(view, sdk.locationHelper, rendering, { graphicsFailed = true }, false).also {
                        lifecycle.addObserver(it)
                    }
                }
            }, modifier = Modifier.fillMaxWidth().fillMaxHeight(.38f).heightIn(max = 280.dp))
        } else {
            Text(stringResource(R.string.halo11_offline_graphics))
        }
        LazyColumn(Modifier.fillMaxWidth().weight(1f),
        verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(bottom = 16.dp)) {
        item("map-credit") { HaloOfflineMapCredits() }
        if (routing) item("turn") {
            val next = navigation
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (next == null) Text(stringResource(R.string.halo11_offline_following), style = MaterialTheme.typography.titleLarge)
                else {
                    val action = when {
                        next.carDirection.name.contains("UTURN", true) -> R.string.halo11_offline_uturn
                        next.carDirection.name.contains("ROUNDABOUT", true) -> R.string.halo11_offline_roundabout
                        next.carDirection.name.contains("LEFT", true) || next.pedestrianDirection.name.contains("LEFT", true) -> R.string.halo11_offline_turn_left
                        next.carDirection.name.contains("RIGHT", true) || next.pedestrianDirection.name.contains("RIGHT", true) -> R.string.halo11_offline_turn_right
                        else -> R.string.halo11_offline_continue
                    }
                    val label = stringResource(action)
                    val distance = next.distToTurn.toString(context)
                    val turn = if (distance.isBlank()) label else stringResource(R.string.halo11_offline_turn, label, distance)
                    val arrow = when (action) {
                        R.string.halo11_offline_uturn -> "↶"
                        R.string.halo11_offline_roundabout -> "⟳"
                        R.string.halo11_offline_turn_left -> "←"
                        R.string.halo11_offline_turn_right -> "→"
                        else -> "↑"
                    }
                    val draw = if (profile == 0) next.pedestrianDirection.turnRes else next.carDirection.getTurnRes(next.exitNum)
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(painterResource(draw), null, Modifier.size(28.dp))
                        Text(turn, style = MaterialTheme.typography.headlineSmall)
                    }
                    if (next.nextStreet.isNotBlank()) Text(next.nextStreet)
                    if (next.distToTurn.isValid) {
                        val meters = next.distToTurn.mDistance * when (next.distToTurn.mUnits) {
                            Distance.Units.Meters -> 1.0
                            Distance.Units.Kilometers -> 1000.0
                            Distance.Units.Feet -> .3048
                            Distance.Units.Miles -> 1609.344
                        }
                        val frame = OfflineTurn(arrow, distance, meters, "$action:${next.nextStreet}:${next.exitNum}", turn)
                        HaloOfflineTurnOutput(frame, locale.toLanguageTag()) {
                            session.phase.value == HaloOfflinePhase.NAVIGATING
                        }
                    }
                    if (next.totalTimeInSeconds > 0) HaloMapShareArrival(enabled = true) {
                        session.navigation.value?.totalTimeInSeconds?.takeIf { it > 0 }?.let {
                            java.time.ZonedDateTime.now().plusSeconds(it.toLong())
                        }
                    }
                }
                Button(onClick = session::stop) { Text(stringResource(R.string.halo11_offline_stop)) }
            }
        } else {
            item("inputs") {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        FilterChip(!regionMode, { regionMode = false; query = "" },
                            label = { Text(stringResource(R.string.halo11_offline_search_places)) })
                        FilterChip(regionMode, { regionMode = true; query = "" },
                            label = { Text(stringResource(R.string.halo11_offline_search_regions)) })
                    }
                    OutlinedTextField(query, onValueChange = { query = it.take(120) }, modifier = Modifier.fillMaxWidth(),
                        singleLine = true, label = { Text(stringResource(if (regionMode) R.string.halo11_offline_region_hint else R.string.halo11_offline_place_hint)) })
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button({
                            error = 0
                            if (regionMode) session.search(query, true, false)
                            else {
                                val (term, requestedOpen) = offlineMapSearch(query)
                                query = term
                                openNow = openNow || requestedOpen
                                session.search(term, false, openNow)
                            }
                        }, enabled = query.isNotBlank()) {
                            Text(stringResource(R.string.halo11_search_title))
                        }
                        if (!regionMode) FilterChip(openNow, { openNow = !openNow },
                            label = { Text(stringResource(R.string.halo11_offline_open_now)) })
                    }
                    if (fetching) LinearProgressIndicator(Modifier.fillMaxWidth())
                }
            }
            if (!regionMode && phase != HaloOfflinePhase.MISSING_MAP) items(places, key = { "${it.title}:${it.point}" }) { place ->
                OutlinedButton({ session.select(place) }, enabled = phase != HaloOfflinePhase.BUILDING) { Text(place.title) }
            }
            if (regionMode || phase == HaloOfflinePhase.MISSING_MAP) items(regions, key = HaloOfflineRegion::id) { region ->
                val busy = region.status in setOf(CountryItem.STATUS_ENQUEUED, CountryItem.STATUS_PROGRESS, CountryItem.STATUS_APPLYING)
                val size = android.text.format.Formatter.formatShortFileSize(context, region.bytes)
                ListItem(headlineContent = { Text(region.title) }, supportingContent = {
                    Text(if (region.status == CountryItem.STATUS_DONE) stringResource(R.string.halo11_offline_region_ready)
                        else if (busy) stringResource(R.string.halo11_offline_progress, progress) else size)
                }, trailingContent = {
                    if (busy) TextButton({ session.cancelDownload(region.id) }) { Text(stringResource(android.R.string.cancel)) }
                    else if (region.status != CountryItem.STATUS_DONE) TextButton({ regionReview = region }) {
                        Text(stringResource(R.string.halo11_offline_download))
                    }
                })
            }
            if (!regionMode && destination != null) item("route") {
                val current = destination ?: return@item
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(current.title, style = MaterialTheme.typography.titleLarge)
                    val profiles = listOf(Router.Pedestrian, Router.Bicycle, Router.Vehicle, Router.Transit)
                    val labels = listOf(R.string.halo11_offline_walk, R.string.halo11_offline_bike,
                        R.string.halo11_offline_car, R.string.halo11_offline_transit)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        profiles.indices.forEach { index ->
                            FilterChip(profile == index, {
                                profile = index; session.stop()
                            }, enabled = phase != HaloOfflinePhase.BUILDING, label = { Text(stringResource(labels[index])) })
                        }
                    }
                    if (position == null) OutlinedButton({
                        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED)
                            session.startLocation()
                        else {
                            permissionRequested = true
                            locationPermission.launch(arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION))
                        }
                    }, enabled = readyToRender && !permissionRequested) { Text(stringResource(R.string.halo11_offline_find_position)) }
                    if (phase == HaloOfflinePhase.BUILT) {
                        if (profiles[profile] == Router.Transit) {
                            Text(stringResource(R.string.halo11_offline_transit_offline), style = MaterialTheme.typography.bodySmall)
                            transit?.let { route ->
                                Text(stringResource(R.string.halo11_offline_transit_duration, (route.totalTime.coerceAtLeast(0) + 59) / 60),
                                    style = MaterialTheme.typography.titleMedium)
                                route.transitSteps.filter { step -> step.type != app.organicmaps.sdk.routing.TransitStepType.INTERMEDIATE_POINT }
                                    .forEach { step ->
                                        val name = if (step.type == app.organicmaps.sdk.routing.TransitStepType.PEDESTRIAN)
                                            stringResource(R.string.halo11_offline_walk)
                                        else step.lineNumber.orEmpty().ifBlank { step.number.orEmpty() }
                                            .ifBlank { stringResource(R.string.halo11_offline_transit) }
                                        val stops = listOf(step.startStopName.orEmpty(), step.endStopName.orEmpty())
                                            .filter(String::isNotBlank).joinToString(" → ")
                                        Text(listOf(name, stops).filter(String::isNotBlank).joinToString(" · "))
                                    }
                            } ?: Text(stringResource(R.string.halo11_offline_route_error))
                        } else Button(session::navigate) { Text(stringResource(R.string.halo11_offline_start)) }
                    }
                    else Button({
                        error = if (session.build(profiles[profile])) 0 else R.string.halo11_offline_fresh_fix
                    }, enabled = readyToRender && position != null && phase != HaloOfflinePhase.BUILDING) { Text(stringResource(R.string.halo11_offline_build)) }
                    if (phase == HaloOfflinePhase.BUILDING) LinearProgressIndicator(progress = { progress / 100f }, modifier = Modifier.fillMaxWidth())
                    if (phase == HaloOfflinePhase.ARRIVED) Text(stringResource(R.string.halo11_offline_arrived))
                    if (phase == HaloOfflinePhase.MISSING_MAP) Text(stringResource(R.string.halo11_offline_missing))
                    if (phase == HaloOfflinePhase.FAILED) Text(stringResource(R.string.halo11_offline_route_error))
                }
            }
            liveArrival?.let { history ->
                item("offline-arrived-context") {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        history.upcoming.forEach { Text(it.record.title, style = MaterialTheme.typography.titleMedium) }
                        history.previous.take(3).forEach { Text(it.record.title, style = MaterialTheme.typography.bodyMedium) }
                    }
                }
            }
        }
        if (error != 0) item("error") { Text(stringResource(error), color = MaterialTheme.colorScheme.error) }
        }
    }
    regionReview?.let { reviewed ->
        AlertDialog(
            onDismissRequest = { regionReview = null },
            title = { Text(stringResource(R.string.halo11_offline_download)) },
            text = { Text(stringResource(R.string.halo11_offline_download_review, reviewed.title,
                android.text.format.Formatter.formatShortFileSize(context, reviewed.bytes))) },
            confirmButton = { TextButton({
                val network = context.getSystemService(ConnectivityManager::class.java)
                val capabilities = network?.getNetworkCapabilities(network.activeNetwork)
                val unmetered = capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED) == true
                error = if (unmetered && session.download(reviewed)) 0 else R.string.halo11_offline_download_error
                regionReview = null
            }) { Text(stringResource(R.string.halo11_offline_download)) } },
            dismissButton = { TextButton({ regionReview = null }) { Text(stringResource(android.R.string.cancel)) } },
        )
    }
}

@Composable
internal fun HaloOfflineMapCredits() {
    val context = LocalContext.current
    fun credit(url: String) {
        runCatching { requireAdultToolkit(context); context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
    }
    Column {
        Text(stringResource(R.string.halo11_offline_attribution), style = MaterialTheme.typography.labelSmall)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton({ credit("https://www.openstreetmap.org/copyright") }) { Text("OpenStreetMap") }
            TextButton({ credit("https://organicmaps.app/") }) { Text("Organic Maps") }
        }
    }
}
