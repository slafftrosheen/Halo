package com.halo.app.ui.toolkit

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.annotation.StringRes
import com.halo.app.R
import com.halo.app.data.toolkit.HaloMapCoordinate

internal enum class OrganicMapsTravelMode(val parameter: String, @StringRes val label: Int) {
    WALK("pedestrian", R.string.halo11_tool_map_mode_walk),
    BIKE("bicycle", R.string.halo11_tool_map_mode_bike),
    DRIVE("vehicle", R.string.halo11_tool_map_mode_car),
    TRANSIT("transit", R.string.halo11_tool_map_mode_transit),
}

internal data class OrganicMapsPoint(val coordinate: HaloMapCoordinate, val label: String)

/** Organic Maps app-to-app deep links. Route, pin and search data only go to the selected provider's package. */
internal object HaloOrganicMaps {
    private val packages = listOf("app.organicmaps", "app.organicmaps.web", "app.organicmaps.beta")
    private const val homepage = "https://organicmaps.app/"

    fun isInstalled(context: Context): Boolean = launcher(context) != null

    // Upstream Android om://route reads the five keys in this exact order and requires two points.
    fun route(start: OrganicMapsPoint, end: OrganicMapsPoint, mode: OrganicMapsTravelMode): Uri =
        Uri.Builder().scheme("om").authority("route")
            .appendQueryParameter("sll", start.coordinate.providerPair())
            .appendQueryParameter("saddr", start.label)
            .appendQueryParameter("dll", end.coordinate.providerPair())
            .appendQueryParameter("daddr", end.label)
            .appendQueryParameter("type", mode.parameter)
            .build()

    fun destination(end: OrganicMapsPoint): Uri = Uri.Builder().scheme("om").authority("map")
        .appendQueryParameter("v", "1").appendQueryParameter("ll", end.coordinate.providerPair())
        .appendQueryParameter("n", end.label).build()

    fun search(query: String, languageTag: String): Uri {
        require(query.isNotBlank() && query.length <= 120)
        return Uri.Builder().scheme("om").authority("search")
            .appendQueryParameter("locale", languageTag)
            .appendQueryParameter("query", query)
            .build()
    }

    fun open(context: Context, uri: Uri) {
        require(uri.scheme == "om" && uri.authority in setOf("route", "map", "search"))
        val intent = packages.firstNotNullOfOrNull { packageId ->
            Intent(Intent.ACTION_VIEW, uri).setPackage(packageId).takeIf { it.resolveActivity(context.packageManager) != null }
        } ?: throw ActivityNotFoundException()
        context.startActivity(intent)
    }

    fun openOfflineAreas(context: Context) {
        context.startActivity(launcher(context) ?: throw ActivityNotFoundException())
    }

    fun openHomepage(context: Context) {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(homepage)))
    }

    private fun launcher(context: Context): Intent? = packages.firstNotNullOfOrNull(context.packageManager::getLaunchIntentForPackage)
    private fun HaloMapCoordinate.providerPair() = latitude.toString() + "," + longitude.toString()
}
