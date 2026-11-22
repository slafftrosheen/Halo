package com.halo.app.nativeactions

import android.appwidget.AppWidgetManager
import android.content.Intent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.halo.app.R
import com.halo.app.di.ManualDependencyGraph as Graph
import com.halo.app.everyday.presentation.EverydayCardConfig
import com.halo.app.everyday.presentation.EverydayToolkitWidgets
import com.halo.app.everyday.presentation.EverydayWidgetShows
import com.halo.app.everyday.presentation.EverydayWidgetStyle
import com.halo.app.everyday.presentation.EverydayCardKind
import com.halo.app.everyday.presentation.EverydayCards
import com.halo.app.everyday.presentation.EverydaySnapshot
import com.halo.app.everyday.presentation.EverydayWidgetSize
import com.halo.app.language.resolveUiText
import com.halo.app.language.uiText
import com.halo.app.ui.design.HaloShapes
import com.halo.app.ui.theme.rememberVisualPreferences
import com.halo.app.ui.workspace.HaloWorkspaceToggleRow
import com.halo.app.wallpaper.HaloWallpaper
import com.halo.app.wallpaper.HaloWallpaperCanvas
import com.halo.app.wallpaper.WallpaperPreferences
import com.halo.app.wallpaper.haloWallpaperTime
import com.halo.app.wallpaper.wallpaperAt
import com.halo.app.wallpaper.wallpaperPeriod
import kotlinx.coroutines.CancellationException

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun EverydayWidgetPicker(initialKind: EverydayCardKind = EverydayCardKind.DASHBOARD, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val manager = remember(context) { AppWidgetManager.getInstance(context) }
    val supportsPin = remember(manager) { manager.isRequestPinAppWidgetSupported }
    val installed = remember { EverydayWidgetProviders.ids(context).mapNotNull { id ->
        EverydayWidgetProviders.kind(context, id)?.let { id to it }
    } }
    var kind by rememberSaveable(initialKind) { mutableStateOf(initialKind.name) }
    var size by rememberSaveable { mutableStateOf<String?>(null) }
    var style by rememberSaveable { mutableStateOf(EverydayWidgetStyle.GLASS.name) }
    var shows by rememberSaveable { mutableStateOf(EverydayWidgetShows.DEFAULT.name) }
    var motion by rememberSaveable { mutableStateOf(true) }
    var autoPage by rememberSaveable { mutableStateOf(false) }
    var matchesLook by rememberSaveable { mutableStateOf(true) }
    var notice by rememberSaveable { mutableStateOf<Int?>(null) }
    val config = EverydayCardConfig(EverydayCardKind.valueOf(kind), widgetSize = size?.let(EverydayWidgetSize::valueOf),
        widgetMotion = motion, widgetMatchLook = matchesLook, widgetStyle = EverydayWidgetStyle.valueOf(style),
        widgetShows = EverydayWidgetShows.valueOf(shows), widgetAutoPage = autoPage && EverydayWidgetViews.supportsPaging(EverydayCardKind.valueOf(kind)))
    var snapshotError by remember { mutableStateOf(false) }
    var retry by remember { mutableIntStateOf(0) }
    val snapshot by produceState<EverydaySnapshot?>(null, retry) {
        snapshotError = false
        try { value = readEverydayWidgetSnapshot(context) }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { snapshotError = true }
    }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text(stringResource(R.string.alive_widget_title), style = MaterialTheme.typography.headlineSmall)
            val easy = rememberVisualPreferences().viewMode == com.halo.app.ui.theme.HaloViewMode.EASY
            var moreKinds by rememberSaveable { mutableStateOf(false) }
            val groups = if (easy && !moreKinds) listOf(
                R.string.widget_toolkit_original to listOf(EverydayCardKind.TODAY, EverydayCardKind.DASHBOARD, EverydayCardKind.TASKS,
                    EverydayCardKind.CLOCK_WEATHER, EverydayCardKind.BRIEF, config.kind).distinct()
            ) else listOf(
                R.string.widget_toolkit_capture to EverydayToolkitWidgets.capture,
                R.string.widget_toolkit_planning to EverydayToolkitWidgets.planning,
                R.string.widget_toolkit_family to EverydayToolkitWidgets.family,
                R.string.widget_toolkit_smarter to listOf(EverydayCardKind.SMART_STACK,
                    EverydayCardKind.LOCK_NEXT_EVENT, EverydayCardKind.LOCK_RAIN),
                R.string.widget_toolkit_original to EverydayCards.launcherKinds.filterNot { it in EverydayToolkitWidgets.launcherKinds },
            )
            groups.forEach { (heading, options) ->
                Text(stringResource(heading), style = MaterialTheme.typography.titleSmall)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    options.forEach { option ->
                        FilterChip(config.kind == option, {
                            kind = option.name
                            size = null
                            shows = EverydayWidgetShows.DEFAULT.name
                            notice = null
                        }, label = { Text(uiText(EverydayCards.title(option))) },
                            modifier = Modifier.heightIn(min = if (easy) 64.dp else 48.dp))
                    }
                }
            }
            if (easy && !moreKinds) TextButton({ moreKinds = true }) { Text(uiText("More")) }
            if (config.kind in EverydayToolkitWidgets.lockScreen) {
                Text(stringResource(R.string.widget_toolkit_lock_hint), style = MaterialTheme.typography.bodySmall)
            }
            if (snapshot != null) EverydayWidgetPreview(config, snapshot!!)
            else if (!snapshotError) LinearProgressIndicator(Modifier.fillMaxWidth())
            if (snapshotError) {
                Text(stringResource(R.string.alive_widget_preview_error), color = MaterialTheme.colorScheme.error)
                TextButton({ retry++ }) { Text(uiText("Retry")) }
            }
            WidgetDisplayChoices(config, enabled = notice != R.string.alive_widget_pin_requested) { next ->
                size = next.widgetSize?.name
                motion = next.widgetMotion
                autoPage = next.widgetAutoPage
                matchesLook = next.widgetMatchLook
                style = next.widgetStyle.name
                shows = next.widgetShows.name
                notice = null
            }
            Button(onClick = {
                val reviewed = snapshot ?: return@Button
                notice = try {
                    if (EverydayWidgetPin.request(context, config, reviewed)) R.string.alive_widget_pin_requested
                    else R.string.alive_widget_pin_unavailable
                } catch (_: Exception) { R.string.alive_widget_pin_unavailable }
            }, enabled = snapshot != null && supportsPin && notice != R.string.alive_widget_pin_requested,
                modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
                Text(stringResource(R.string.alive_widget_title))
            }
            if (!supportsPin) Text(stringResource(R.string.alive_widget_pin_unavailable))
            notice?.let { Text(stringResource(it), style = MaterialTheme.typography.bodyMedium) }
            if (installed.isNotEmpty()) {
                Text(stringResource(R.string.alive_widget_installed), style = MaterialTheme.typography.titleMedium)
                installed.forEach { (id, widgetKind) ->
                    TextButton(onClick = {
                        runCatching {
                            Graph.kidsPreferences.adultActionRevision()
                            context.startActivity(Intent(context, EverydayWidgetConfigurationActivity::class.java)
                                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id))
                        }.onFailure { notice = R.string.alive_widget_preview_error }
                    }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                        Text(uiText(EverydayCards.title(widgetKind)) + " · " + id, Modifier.weight(1f))
                    }
                }
            }
            TextButton(onDismiss, modifier = Modifier.heightIn(min = 48.dp)) { Text(uiText("Close")) }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun WidgetDisplayChoices(config: EverydayCardConfig, enabled: Boolean = true, allowAuto: Boolean = true,
    onChange: (EverydayCardConfig) -> Unit) {
    Text(stringResource(R.string.alive_widget_size), style = MaterialTheme.typography.titleMedium)
    val scheme = MaterialTheme.colorScheme
    val previewSize = config.widgetSize ?: EverydayToolkitWidgets.defaultSize(config.kind)
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        EverydayWidgetSize.entries.forEach { size ->
            val chosen = previewSize == size
            val (sampleWidth, sampleHeight, label) = when (size) {
                EverydayWidgetSize.SMALL -> Triple(15.dp, 15.dp, R.string.alive_widget_small)
                EverydayWidgetSize.WIDE -> Triple(32.dp, 15.dp, R.string.alive_widget_wide)
                EverydayWidgetSize.TALL -> Triple(28.dp, 22.dp, R.string.widget_toolkit_big)
            }
            Surface(onClick = { onChange(config.copy(widgetSize = size)) }, enabled = enabled,
                color = if (chosen) scheme.primaryContainer else scheme.surfaceContainerHigh,
                contentColor = if (chosen) scheme.onPrimaryContainer else scheme.onSurfaceVariant,
                border = if (chosen) BorderStroke(1.dp, scheme.primary) else null,
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.weight(1f).heightIn(min = 62.dp).semantics { selected = chosen }) {
                Column(Modifier.padding(horizontal = 2.dp, vertical = 7.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(Modifier.height(24.dp), contentAlignment = Alignment.Center) {
                        Box(Modifier.size(sampleWidth, sampleHeight).background(
                            if (chosen) scheme.primary.copy(alpha = .40f) else scheme.outlineVariant,
                            MaterialTheme.shapes.extraSmall))
                    }
                    Text(stringResource(label), style = MaterialTheme.typography.labelMedium, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                }
            }
        }
    }
    if (allowAuto) TextButton({ onChange(config.copy(widgetSize = null)) }, enabled = enabled,
        modifier = Modifier.heightIn(min = 48.dp).semantics { selected = config.widgetSize == null }) {
        Text(stringResource(R.string.alive_widget_auto))
    }
    Text(stringResource(R.string.alive_widget_size_explanation), style = MaterialTheme.typography.bodySmall)
    Text(stringResource(R.string.widget_toolkit_style), style = MaterialTheme.typography.titleMedium)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(EverydayWidgetStyle.GLASS to R.string.widget_toolkit_glass, EverydayWidgetStyle.SOLID to R.string.widget_toolkit_solid,
            EverydayWidgetStyle.CLEAR to R.string.widget_toolkit_clear).forEach { (style, label) ->
            FilterChip(config.widgetStyle == style, { onChange(config.copy(widgetStyle = style)) }, enabled = enabled,
                label = { Text(stringResource(label)) }, modifier = Modifier.heightIn(min = 48.dp))
        }
    }
    if (EverydayToolkitWidgets.supportsShows(config.kind)) {
        Text(stringResource(R.string.widget_toolkit_shows), style = MaterialTheme.typography.titleMedium)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            val base = if (config.kind in EverydayToolkitWidgets.launcherKinds) EverydayWidgetShows.THIS_WEEK else EverydayWidgetShows.DEFAULT
            listOf(EverydayWidgetShows.THIS_WEEK to R.string.widget_toolkit_this_week,
                EverydayWidgetShows.TODAY to R.string.widget_toolkit_today,
                EverydayWidgetShows.NEXT_ONLY to R.string.widget_toolkit_next_only).forEach { (shows, label) ->
                val selection = if (config.widgetShows == EverydayWidgetShows.DEFAULT) base else config.widgetShows
                FilterChip(selection == shows, { onChange(config.copy(widgetShows = shows)) }, enabled = enabled,
                    label = { Text(stringResource(label)) }, modifier = Modifier.heightIn(min = 48.dp))
            }
        }
    }
    HaloWorkspaceToggleRow(title = stringResource(R.string.alive_widget_motion), checked = config.widgetMotion,
        onCheckedChange = { onChange(config.copy(widgetMotion = it)) }, enabled = enabled)
    if (EverydayWidgetViews.supportsPaging(config.kind)) HaloWorkspaceToggleRow(title = uiText("Rotate widget pages"),
        checked = config.widgetAutoPage, onCheckedChange = { onChange(config.copy(widgetAutoPage = it)) },
        enabled = enabled && config.widgetMotion)
    HaloWorkspaceToggleRow(title = stringResource(R.string.alive_widget_match), checked = config.widgetMatchLook,
        onCheckedChange = { onChange(config.copy(widgetMatchLook = it)) }, enabled = enabled)
}

@Composable
internal fun EverydayWidgetPreview(config: EverydayCardConfig, snapshot: EverydaySnapshot) {
    val context = LocalContext.current
    val locale = LocalConfiguration.current.locales[0]
    val size = config.widgetSize ?: EverydayToolkitWidgets.defaultSize(config.kind)
    val height = EverydayToolkitWidgets.previewHeight(config)
    val wallpaper by remember(context) { WallpaperPreferences.get(context) }.state.collectAsStateWithLifecycle()
    val previewTime = haloWallpaperTime()
    val previewScene = wallpaper.current.wallpaperAt(wallpaperPeriod(previewTime))
    val nativeContext = remember(context) { context }
    val previewDark = when (previewScene) {
        HaloWallpaper.PAPER, HaloWallpaper.PAPER_CUT, HaloWallpaper.INK_WASH, HaloWallpaper.BALTIC_DUNES -> false
        HaloWallpaper.YOUR_PHOTO, HaloWallpaper.DAY_ARC, HaloWallpaper.SEASONS, HaloWallpaper.MEADOW_WIND -> null
        else -> true
    }
    val appearance by Graph.appearancePreferences.mode.collectAsStateWithLifecycle()
    val palette by Graph.palettePreferences.state.collectAsStateWithLifecycle()
    val visual = rememberVisualPreferences()
    val toolKinds = if (config.kind == EverydayCardKind.SMART_STACK) EverydayToolkitWidgets.stackKinds(snapshot)
        else listOf(config.kind)
    val pager = rememberPagerState { toolKinds.size }
    Text(uiText("Preview"), style = MaterialTheme.typography.labelLarge)
    BoxWithConstraints(Modifier.fillMaxWidth().clip(HaloShapes.hero), contentAlignment = Alignment.Center) {
        HaloWallpaperCanvas(wallpaper.current.copy(wallpaper = previewScene, reactsToHalo = false),
            Modifier.matchParentSize(), previewTime = previewTime, animate = false)
        val width = minOf(size.widthDp.dp, maxWidth - 24.dp)
        HorizontalPager(pager, Modifier.padding(12.dp).width(width), userScrollEnabled = toolKinds.size > 1) { index ->
            val card = config.copy(kind = toolKinds[index], limit = minOf(config.limit, size.rowLimit))
            key(card, snapshot, locale, width, height, visual, palette, appearance, wallpaper, previewScene) {
                AndroidView(factory = {
                    EverydayWidgetViews.render(nativeContext, EverydayWidgetInstance(0, 0, card), snapshot,
                        width.value.toInt().coerceAtLeast(84), height, locale = locale,
                        label = { text -> resolveUiText(nativeContext.resources, text) }, preview = true,
                        previewWallpaperDark = previewDark)
                        .apply(nativeContext, null)
                }, modifier = Modifier.fillMaxWidth().height(height.dp)
                    .border(2.dp, MaterialTheme.colorScheme.primary, HaloShapes.hero))
            }
        }
    }
    if (toolKinds.size > 1) {
        val label = stringResource(R.string.widget_toolkit_stack_position, pager.currentPage + 1, toolKinds.size)
        Text((toolKinds.indices).joinToString(" ") { if (it == pager.currentPage) "●" else "○" },
            Modifier.semantics { contentDescription = label }, color = MaterialTheme.colorScheme.primary)
        Text(stringResource(R.string.widget_toolkit_stack_hint), style = MaterialTheme.typography.bodySmall)
    }
}
