package com.halo.app.ui.toolkit

import android.content.Context
import android.location.Location
import android.os.SystemClock
import app.organicmaps.sdk.Framework
import app.organicmaps.sdk.OrganicMaps
import app.organicmaps.sdk.Router
import app.organicmaps.sdk.bookmarks.data.MapObject
import app.organicmaps.sdk.downloader.CountryItem
import app.organicmaps.sdk.downloader.MapManager
import app.organicmaps.sdk.location.LocationListener
import app.organicmaps.sdk.routing.RoutingController
import app.organicmaps.sdk.routing.RoutingInfo
import app.organicmaps.sdk.routing.TransitRouteInfo
import app.organicmaps.sdk.search.MapSearchListener
import app.organicmaps.sdk.search.SearchEngine
import app.organicmaps.sdk.search.SearchListener
import app.organicmaps.sdk.search.SearchResult
import com.halo.app.BuildConfig
import com.halo.app.data.toolkit.HaloMapCoordinate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** The provider owns map files and routing. Halo's route session holds a fix only while visible. */
internal object HaloOfflineMapEngine {
    sealed interface Readiness {
        data object Loading : Readiness
        data object Failed : Readiness
        data class Ready(val sdk: OrganicMaps) : Readiness
    }

    private val readiness = MutableStateFlow<Readiness>(Readiness.Loading)
    val state = readiness.asStateFlow()
    private var started = false

    fun initialize(context: Context) {
        if (started) return
        started = true
        try {
            val sdk = OrganicMaps(context.applicationContext, "web", context.packageName, BuildConfig.VERSION_CODE,
                BuildConfig.VERSION_NAME)
            if (!sdk.init { readiness.value = Readiness.Ready(sdk) })
                readiness.value = Readiness.Failed
        } catch (_: Exception) {
            readiness.value = Readiness.Failed
        } catch (_: LinkageError) {
            readiness.value = Readiness.Failed
        } catch (_: AssertionError) {
            readiness.value = Readiness.Failed
        }
    }
}

internal data class HaloOfflineDestination(val title: String, val point: HaloMapCoordinate)
internal enum class HaloOfflinePhase { IDLE, BUILDING, BUILT, NAVIGATING, ARRIVED, MISSING_MAP, FAILED }
internal data class HaloOfflineRegion(val id: String, val title: String, val status: Int, val bytes: Long)

internal class HaloOfflineRouteSession(
    private val sdk: OrganicMaps,
    private val context: Context,
    private val allowed: () -> Boolean,
) : RoutingController.Container, SearchListener, MapSearchListener, MapManager.StorageCallback, LocationListener {
    private val router = RoutingController.get()
    private var storageSubscription = -1
    private var searchStamp = 0L
    private val pending = linkedSetOf<String>()
    private var requestRegion = false
    private var usingLocation = false
    val phase = MutableStateFlow(HaloOfflinePhase.IDLE)
    val places = MutableStateFlow(emptyList<HaloOfflineDestination>())
    val regions = MutableStateFlow(emptyList<HaloOfflineRegion>())
    val progress = MutableStateFlow(0)
    val searching = MutableStateFlow(false)
    val position = MutableStateFlow<Location?>(null)
    val navigation = MutableStateFlow<RoutingInfo?>(null)
    val transit = MutableStateFlow<TransitRouteInfo?>(null)
    val destination = MutableStateFlow<HaloOfflineDestination?>(null)

    fun attach() {
        if (storageSubscription >= 0 || !allowed()) return
        storageSubscription = MapManager.nativeSubscribe(this)
        router.attach(this)
        SearchEngine.INSTANCE.addListener(this)
        SearchEngine.INSTANCE.addMapListener(this)
        sdk.locationHelper.addListener(this)
        refresh(regions.value.map { it.id })
    }

    fun detach() {
        if (storageSubscription < 0) return
        stop()
        val owned = pending.toList()
        pending.clear()
        owned.forEach { MapManager.nativeCancel(it) }
        searchStamp = 0
        searching.value = false
        sdk.locationHelper.removeListener(this)
        SearchEngine.INSTANCE.removeListener(this)
        SearchEngine.INSTANCE.removeMapListener(this)
        SearchEngine.INSTANCE.cancel()
        MapManager.nativeUnsubscribe(storageSubscription)
        storageSubscription = -1
        router.detach()
        position.value = null
    }

    fun select(value: HaloOfflineDestination) {
        if (!allowed()) return
        if (phase.value in setOf(HaloOfflinePhase.BUILDING, HaloOfflinePhase.BUILT, HaloOfflinePhase.NAVIGATING))
            router.cancel()
        navigation.value = null
        transit.value = null
        destination.value = value
        phase.value = HaloOfflinePhase.IDLE
    }

    fun startLocation() {
        if (!allowed() || storageSubscription < 0) return
        usingLocation = true
        if (!sdk.locationHelper.isActive) sdk.locationHelper.start()
    }

    fun search(text: String, forRegion: Boolean, openNow: Boolean) {
        if (!allowed() || storageSubscription < 0) return
        val term = text.trim()
        if (term.isEmpty() || term.length > 120) return
        searchStamp = SystemClock.elapsedRealtimeNanos().coerceAtLeast(searchStamp + 1)
        searching.value = true
        requestRegion = forRegion
        onlyOpen = openNow
        if (forRegion) {
            refresh(emptyList())
            SearchEngine.searchMaps(context, term, searchStamp)
        } else {
            places.value = emptyList()
            val point = position.value.takeIf { usingLocation }
            searching.value = SearchEngine.INSTANCE.search(context, term, false, searchStamp, point != null,
                point?.latitude ?: 0.0, point?.longitude ?: 0.0)
        }
    }

    private var onlyOpen = false

    override fun onMapSearchResults(results: Array<MapSearchListener.Result>, timestamp: Long, isLast: Boolean) {
        if (!allowed() || timestamp != searchStamp || !requestRegion) return
        refresh(results.asSequence().map { it.countryId() }.distinct().take(24).toList())
        if (isLast) searching.value = false
    }

    override fun onResultsUpdate(results: Array<SearchResult>, timestamp: Long) {
        if (!allowed() || timestamp != searchStamp || requestRegion) return
        val location = position.value
        val candidates = results.asSequence().filter { it.type == SearchResult.TYPE_RESULT && it.name.isNotBlank() &&
            (!onlyOpen || it.description?.openNow == SearchResult.OPEN_NOW_YES)
        }.mapNotNull { row ->
            runCatching { HaloOfflineDestination(row.name, HaloMapCoordinate(row.lat, row.lon)) }.getOrNull()
        }.distinct().take(60).toList()
        places.value = if (location == null) candidates.take(24) else candidates.sortedBy {
            val distance = FloatArray(1)
            Location.distanceBetween(location.latitude, location.longitude, it.point.latitude, it.point.longitude, distance)
            distance[0]
        }.take(24)
    }

    override fun onResultsEnd(timestamp: Long) {
        if (timestamp == searchStamp && !requestRegion) searching.value = false
    }

    private fun refresh(ids: List<String>) {
        regions.value = ids.map { id ->
            val country = CountryItem.fill(id)
            HaloOfflineRegion(country.id, country.name, country.status, country.bytesToDownload.takeIf { it > 0 } ?: country.totalSize)
        }
    }

    fun download(reviewed: HaloOfflineRegion): Boolean {
        if (!allowed() || storageSubscription < 0 || reviewed !in regions.value ||
            pending.isNotEmpty() || !MapManager.nativeHasSpaceToDownloadCountry(reviewed.id)) return false
        pending.add(reviewed.id)
        MapManager.startDownload(reviewed.id)
        return true
    }

    fun cancelDownload(id: String) {
        if (pending.remove(id)) MapManager.nativeCancel(id)
    }

    override fun onStatusChanged(data: List<MapManager.StorageCallbackData>) {
        if (!allowed() || storageSubscription < 0) return
        refresh(regions.value.map { it.id })
        pending.removeAll { id -> regions.value.firstOrNull { it.id == id }?.status in
            setOf(CountryItem.STATUS_DONE, CountryItem.STATUS_FAILED) }
    }

    override fun onProgress(countryId: String, localSize: Long, remoteSize: Long) {
        if (allowed() && countryId in pending && remoteSize > 0)
            progress.value = (localSize.toDouble() / remoteSize * 100).toInt().coerceIn(0, 100)
    }

    fun build(profile: Router): Boolean {
        val target = destination.value ?: return false
        val fix = position.value ?: return false
        val age = SystemClock.elapsedRealtimeNanos() - fix.elapsedRealtimeNanos
        val current = sdk.locationHelper.myPosition ?: return false
        if (!allowed() || age !in 0L..15_000_000_000L || !fix.hasAccuracy() || fix.accuracy > 60f) return false
        val end = MapObject.createMapObject(MapObject.API_POINT, target.title, "", target.point.latitude, target.point.longitude)
        if (current.lat == end.lat && current.lon == end.lon) return false
        phase.value = HaloOfflinePhase.BUILDING
        transit.value = null
        router.prepare(current, end, profile)
        return true
    }

    override fun updateBuildProgress(progress: Int, router: Router) {
        if (allowed()) this.progress.value = progress.coerceIn(0, 100)
    }

    override fun onBuiltRoute() {
        if (!allowed()) return
        transit.value = if (router.isTransitType) router.cachedTransitInfo else null
        phase.value = HaloOfflinePhase.BUILT
    }

    override fun onCommonBuildError(lastResultCode: Int, lastMissingMaps: Array<String>) {
        if (!allowed()) return
        refresh(lastMissingMaps.distinct().take(24))
        phase.value = if (lastMissingMaps.isEmpty()) HaloOfflinePhase.FAILED else HaloOfflinePhase.MISSING_MAP
    }

    fun navigate() {
        if (allowed() && phase.value == HaloOfflinePhase.BUILT && position.value != null && router.isBuilt && !router.isTransitType) {
            router.start()
            if (router.isNavigating) phase.value = HaloOfflinePhase.NAVIGATING
        }
    }

    fun stop() {
        router.cancel()
        phase.value = HaloOfflinePhase.IDLE
        navigation.value = null
        transit.value = null
        position.value = null
        if (usingLocation) sdk.locationHelper.stop()
        usingLocation = false
    }

    override fun onLocationUpdated(location: Location) {
        if (!allowed() || !usingLocation) return
        position.value = location
        if (!router.isNavigating) return
        if (Framework.nativeIsRouteFinished()) {
            stop()
            phase.value = HaloOfflinePhase.ARRIVED
        } else navigation.value = Framework.nativeGetRouteFollowingInfo()
    }

    override fun onLocationDisabled() {
        if (!usingLocation) return
        stop()
        phase.value = HaloOfflinePhase.FAILED
    }
}
