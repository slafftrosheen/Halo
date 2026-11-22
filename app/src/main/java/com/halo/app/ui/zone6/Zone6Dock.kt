package com.halo.app.ui.zone6

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.halo.app.language.uiText
import com.halo.app.ui.icons.HaloGlyph
import com.halo.app.ui.fx.rememberHaloMotionForeground
import com.halo.app.ui.fx.rememberHaloReducedMotion
import com.halo.app.ui.presence.*
import com.halo.app.ui.shell.primaryNavigationRoute
import com.halo.app.ui.theme.*
import com.halo.app.ui.zone5.Zone5Icon
import com.halo.app.ui.zone5.zone5ActionIconId
import kotlinx.coroutines.delay

internal object Zone6DockTokens {
    const val fraction = .25f
    const val largeTextScale = 1.4f
    val wide = 600.dp
    val radius = 30.dp
    val inset = 10.dp
    val minTouch = 48.dp
    val kidsTouch = 56.dp
    val actionHeight = 52.dp
    val gap = 8.dp
    val shortWindow = 480.dp
}

internal fun zone6SelectedPlace(route: String?, kids: Boolean): String? = when {
    route?.startsWith("screen/") == true -> "screen/sources"
    route?.startsWith("notifications") == true -> "activity"
    kids && (route?.startsWith("study") == true || route?.startsWith("notebook/study") == true) -> "study"
    else -> primaryNavigationRoute(route)
}

internal fun zone6PlaceGlyph(route: String?): Pair<String, HaloGlyph> = when (route) {
    "home" -> "brand_halo" to HaloGlyph.HOME
    "search" -> "action_search" to HaloGlyph.SEE
    "notebook" -> "nav_workspace" to HaloGlyph.NOTE
    "activity" -> "action_notifications" to HaloGlyph.ACTIVITY
    "devices" -> "nav_devices" to HaloGlyph.DEVICE
    "settings" -> "nav_settings" to HaloGlyph.SETTINGS
    "screen/sources" -> "screen_widgets" to HaloGlyph.INTEGRATIONS
    "study" -> "tool_study" to HaloGlyph.QUESTION
    "studio" -> "tool_studio" to HaloGlyph.TOOL
    "canvases" -> "type_canvas" to HaloGlyph.NOTE
    "file_tools" -> "tool_notes" to HaloGlyph.NOTE
    "notebook/memory" -> "tool_memory" to HaloGlyph.MEMORY
    "scan" -> "capture_camera" to HaloGlyph.CAMERA
    "record" -> "capture_record" to HaloGlyph.RECORD
    "planner", "screen/organizer" -> "screen_calendar" to HaloGlyph.CALENDAR
    "sheets" -> "office_table" to HaloGlyph.FILE
    "album" -> "type_image" to HaloGlyph.SEE
    "vault" -> "action_lock" to HaloGlyph.SECURITY
    else -> "action_tools" to (com.halo.app.ui.toolkit.HaloToolkit.tool(route)?.glyph ?: HaloGlyph.TOOL)
}

@Composable
internal fun Zone6PlaceRow(route: String?, owner: ShellPageOwner?, hint: ShellNextStep?, onDismissHint: () -> Unit) {
    val (icon, glyph, title) = when (owner?.routeFamily) {
        "settings" -> {
            val title = when (owner.activeToolId) {
                "settings.appearance" -> androidx.compose.ui.res.stringResource(com.halo.app.R.string.look_title)
                "settings.voice" -> uiText("Voice & reading")
                "settings.privacy" -> uiText("Privacy")
                else -> uiText("Settings")
            }
            Triple("nav_settings", HaloGlyph.SETTINGS, title)
        }
        "note", "file_tools" -> Triple("tool_notes", HaloGlyph.NOTE, uiText("Notes"))
        "studio" -> Triple("tool_studio", HaloGlyph.TOOL, uiText("Studio"))
        "canvas" -> Triple("type_canvas", HaloGlyph.NOTE, uiText("Canvas"))
        "notebook" -> {
            val library = owner.activeToolId?.contains("tools") != true
            if (library) Triple("nav_workspace", HaloGlyph.NOTE, uiText("Library"))
            else Triple("action_tools", HaloGlyph.TOOL, uiText("Tools"))
        }
        "study" -> Triple("tool_study", HaloGlyph.QUESTION, uiText("Study"))
        "memory" -> Triple("tool_memory", HaloGlyph.MEMORY, uiText("Memory"))
        "everyday_source" -> Triple("screen_widgets", HaloGlyph.INTEGRATIONS, uiText("Saved source"))
        "everyday_sources" -> Triple("screen_widgets", HaloGlyph.INTEGRATIONS,
            if (route == "home/reads") androidx.compose.ui.res.stringResource(com.halo.app.R.string.halo11_home_reads)
            else uiText("Widgets & sources"))
        "everyday_brief" -> Triple("nav_activity", HaloGlyph.ACTIVITY, uiText("Your day"))
        "everyday_organizer" -> Triple("nav_activity", HaloGlyph.CALENDAR, androidx.compose.ui.res.stringResource(com.halo.app.R.string.toolkit_planner))
        "scan", "translate", "magnifier", "record", "sheets", "album", "vault" -> {
            val family = owner?.routeFamily
            val tool = com.halo.app.ui.toolkit.HaloToolkit.tool(if (family in setOf("translate", "magnifier")) "scan" else family)
            val (symbol, toolGlyph) = zone6PlaceGlyph(tool?.route)
            Triple(symbol, toolGlyph,
                tool?.let { androidx.compose.ui.res.stringResource(it.title) } ?: uiText("Tools"))
        }
        "focus" -> Triple("action_tools", HaloGlyph.PAUSE, androidx.compose.ui.res.stringResource(com.halo.app.R.string.halo_focus_title))
        "family" -> Triple("nav_home", HaloGlyph.HOME, androidx.compose.ui.res.stringResource(com.halo.app.R.string.halo_family_title))
        "checkin" -> Triple("nav_activity", HaloGlyph.CALENDAR, androidx.compose.ui.res.stringResource(com.halo.app.R.string.halo_checkin_title))
        "on_move" -> Triple("action_forward", HaloGlyph.NEXT, androidx.compose.ui.res.stringResource(com.halo.app.R.string.halo_move_title))
        "winddown" -> Triple("action_pause", HaloGlyph.PAUSE, androidx.compose.ui.res.stringResource(com.halo.app.R.string.halo_winddown_title))
        "emergency" -> Triple("nav_settings", HaloGlyph.SECURITY, androidx.compose.ui.res.stringResource(com.halo.app.R.string.halo_emergency_title))
        "pause" -> Triple("action_pause", HaloGlyph.PAUSE, androidx.compose.ui.res.stringResource(com.halo.app.R.string.halo_pause_title))
        "private_chat" -> Triple("nav_settings", HaloGlyph.SECURITY, uiText("Private chat"))
        "at_a_glance" -> Triple("action_tools", HaloGlyph.TOOL, androidx.compose.ui.res.stringResource(com.halo.app.R.string.halo_map_title))
        "everyday_clocks" -> Triple("nav_activity", HaloGlyph.WATCH, uiText("World clocks"))
        else -> {
            val place = zone6SelectedPlace(route, false)
            val (icon, glyph) = zone6PlaceGlyph(place)
            Triple(icon, glyph, uiText(when (place) {
                "notebook" -> "Workspace"
                "activity" -> "Activity"
                "devices" -> "Devices"
                "settings" -> "Settings"
                "search" -> "Search"
                "screen/sources" -> "Widgets & sources"
                else -> "Home"
            }))
        }
    }
    val colors = MaterialTheme.colorScheme
    Row(Modifier.fillMaxWidth().heightIn(min = 44.dp).zone6GuideOutline(Zone6GuideTarget.PLACE), horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Surface(shape = RoundedCornerShape(12.dp), color = colors.primary.copy(alpha = .13f), contentColor = colors.onSurface) {
            Box(Modifier.size(36.dp), contentAlignment = Alignment.Center) {
                Zone5Icon(icon, glyph, Modifier.size(20.dp), tint = colors.primary)
            }
        }
        Column(Modifier.weight(1f)) {
            Text(title, modifier = Modifier.haloEditablePageTitle(), style = MaterialTheme.typography.titleSmall,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            owner?.statusLabel?.let { status ->
                Text(status, style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        if (hint != null) {
            AssistChip(onClick = onDismissHint, modifier = Modifier.widthIn(max = 188.dp),
                label = { Text(hint.message, maxLines = 1, overflow = TextOverflow.Ellipsis) })
        }
    }
}

private fun dockOrder(family: String?): List<String> = when (family) {
    "studio" -> listOf("add-layer", "draw", "layers", "new", "new-canvas")
    "note" -> listOf("format", "insert", "listen", "view-read")
    "canvas" -> listOf("done-connect", "cancel-connect", "add", "connect", "arrange")
    "study" -> listOf("next", "check", "hint", "listen", "practice", "new")
    "memory" -> listOf("add", "find", "sources", "view-suggestions")
    "notebook" -> listOf("view-library", "view-tools", "new-note", "continue", "import")
    "activity" -> listOf("review", "running", "history")
    "devices" -> listOf("add", "audio", "phone")
    "settings" -> listOf("search", "look", "help", "brain")
    "everyday_sources" -> listOf("add", "weather", "refresh")
    "everyday_source" -> listOf("read", "ask", "refresh", "settings")
    else -> emptyList()
}

@Composable
internal fun Zone6ActionRow(
    actions: List<HaloOrbContextAction>,
    family: String?,
    compact: Boolean,
    kids: Boolean,
    moreOpen: Boolean,
    onMore: () -> Unit,
    onAction: (HaloOrbContextAction) -> Unit,
    moreModifier: Modifier = Modifier,
    reducedMotion: Boolean,
) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val visual = rememberVisualPreferences()
        val sheetHost = LocalZone6SheetHost.current
        val scale = LocalDensity.current.fontScale
        val large = scale >= Zone6DockTokens.largeTextScale
        val easy = !kids && visual.viewMode == com.halo.app.ui.theme.HaloViewMode.EASY
        val slots = when {
            large -> 2
            kids -> 3
            easy -> 3
            maxWidth >= Zone6DockTokens.wide && !compact -> 6
            maxWidth < 350.dp -> 3
            else -> 4
        }
        val order = dockOrder(family)
        val preferred = actions.filterNot { it.glyph == HaloGlyph.TRASH || it.id in setOf("page-context", "type-message") }
            .let { candidates ->
                if (kids) candidates.filter { it.id.contains("kids.type") || it.id.contains("kids.help") || it.id == "study" }
                else candidates
            }
        val sorted = if (family == "studio") preferred else preferred.sortedBy { action ->
            order.indexOf(action.id.substringAfterLast('.')).takeIf { it >= 0 } ?: Int.MAX_VALUE
        }
        val direct = sorted.take(if (kids) slots else slots - 1)
        val overflow = !kids && (actions.size > direct.size || direct.isEmpty())
        if (direct.isEmpty() && !overflow) return@BoxWithConstraints
        val showIcons = !large && (maxWidth >= 340.dp || direct.size <= 2)
        val labels = direct.associateWith { uiText(it.title) }
        val primary = if (moreOpen) null else {
            direct.firstOrNull { it.enabled && labels[it] == sheetHost?.sheet?.title } ?: direct.firstOrNull { it.enabled }
        }
        val target = when {
            kids -> Zone6DockTokens.kidsTouch
            easy -> 64.dp
            else -> Zone6DockTokens.actionHeight
        }
        Row(Modifier.fillMaxWidth().zone6GuideOutline(Zone6GuideTarget.ACTIONS), horizontalArrangement = Arrangement.spacedBy(Zone6DockTokens.gap),
            verticalAlignment = Alignment.CenterVertically) {
            direct.forEachIndexed { index, action ->
                val filled = action === primary
                val label = labels.getValue(action)
                key(family, action.id) {
                    var centerX by remember { mutableStateOf<Float?>(null) }
                    Box(Modifier.weight(if (filled) 1.25f else 1f).heightIn(min = target)
                        .haloGuideControl(action.id)
                        .onGloballyPositioned { centerX = it.boundsInWindow().center.x }
                        .zone6Entrance(index, reducedMotion, visual.motionLevel, ((direct.lastIndex / 2f - index) * 38).dp, 16.dp)) {
                        Zone6DockButton(label, filled, action.enabled, target, {
                            sheetHost?.anchorX = centerX
                            onAction(action)
                        }) {
                            if (showIcons) Zone5Icon(zone5ActionIconId(action), action.glyph, Modifier.size(18.dp),
                                tint = LocalContentColor.current)
                        }
                    }
                }
            }
            if (overflow) {
                var centerX by remember { mutableStateOf<Float?>(null) }
                Box(Modifier.weight(1f).then(moreModifier).haloGuideControl("more")
                    .zone6Entrance(direct.size, reducedMotion, visual.motionLevel, (-18).dp, 16.dp)
                    .onGloballyPositioned { centerX = it.boundsInWindow().center.x }) {
                    Zone6DockButton(uiText(if (large) "All actions" else "More"), moreOpen, true, target, {
                        sheetHost?.anchorX = centerX
                        onMore()
                    }) { if (showIcons) Zone5Icon("action_more", HaloGlyph.TOOL, Modifier.size(18.dp), tint = LocalContentColor.current) }
                }
            }
        }
    }
}

@Composable
private fun Zone6DockButton(
    label: String, filled: Boolean, enabled: Boolean, height: Dp, click: () -> Unit,
    icon: (@Composable () -> Unit)? = null,
) {
    val colors = MaterialTheme.colorScheme
    Surface(onClick = click, enabled = enabled, shape = RoundedCornerShape(16.dp),
        border = if (filled) null else BorderStroke(1.dp, colors.outlineVariant),
        color = when { !enabled -> colors.surfaceContainer; filled -> colors.primaryContainer; else -> colors.onSurface.copy(alpha = .07f) },
        contentColor = when { !enabled -> colors.onSurfaceVariant; filled -> colors.onPrimaryContainer; else -> colors.onSurface },
        modifier = Modifier.fillMaxWidth().heightIn(min = height)) {
        Row(Modifier.padding(horizontal = 6.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterHorizontally)) {
            icon?.invoke()
            Text(label, Modifier.weight(1f, fill = false), style = MaterialTheme.typography.labelLarge, textAlign = TextAlign.Center,
                maxLines = if (LocalDensity.current.fontScale < Zone6DockTokens.largeTextScale) 2 else Int.MAX_VALUE,
                overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
internal fun Zone6UtilityRow(
    utility: @Composable () -> Unit,
    onNew: (() -> Unit)?,
    onMore: (() -> Unit)?,
    moreModifier: Modifier,
) {
    Row(Modifier.fillMaxWidth().zone6GuideOutline(Zone6GuideTarget.ACTIONS),
        horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.weight(1f)) { utility() }
        if (onNew != null) Box(Modifier.widthIn(min = 76.dp, max = 96.dp).haloGuideControl("page.notebook.new-note")) {
            Zone6DockButton(uiText("New note"), filled = true, enabled = true, height = 48.dp, click = onNew) {
                Zone5Icon("action_add", HaloGlyph.NOTE, Modifier.size(18.dp))
            }
        }
        if (onMore != null) {
            FilledTonalIconButton(onClick = onMore, modifier = Modifier.size(48.dp).then(moreModifier).haloGuideControl("more"),
                colors = IconButtonDefaults.filledTonalIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.onSurface.copy(alpha = .07f),
                    contentColor = MaterialTheme.colorScheme.onSurface)) {
                Zone5Icon("action_more", HaloGlyph.TOOL, Modifier.size(20.dp), contentDescription = uiText("More"))
            }
        }
    }
}

@Composable
internal fun Zone6Tab(title: String, glyph: HaloGlyph, iconId: String, selected: Boolean, count: Int,
                      modifier: Modifier = Modifier, onClick: () -> Unit, showLabel: Boolean = true) {
    val label = uiText(title)
    val minHeight = if (rememberVisualPreferences().viewMode == com.halo.app.ui.theme.HaloViewMode.EASY) 64.dp
        else Zone6DockTokens.minTouch
    val countLabel = if (count > 0) uiText("%1\$s needs your review", count.toString()) else null
    Surface(onClick = onClick, color = Color.Transparent, shape = RoundedCornerShape(14.dp),
        modifier = modifier.heightIn(min = minHeight).semantics(mergeDescendants = true) {
            role = Role.Tab
            this.selected = selected
            if (!showLabel) contentDescription = label
            countLabel?.let { stateDescription = it }
        }) {
        Column(Modifier.padding(horizontal = 1.dp, vertical = 4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            BadgedBox(badge = {
                if (count > 0) Badge(containerColor = MaterialTheme.colorScheme.tertiary,
                    contentColor = MaterialTheme.colorScheme.onTertiary) { Text(count.coerceAtMost(99).toString()) }
            }) {
                Box(Modifier.width(if (selected) 60.dp else 48.dp).height(32.dp).clip(RoundedCornerShape(16.dp))
                    .background(if (selected) MaterialTheme.colorScheme.primary.copy(alpha = .16f) else Color.Transparent),
                    contentAlignment = Alignment.Center) {
                    Zone5Icon(iconId, glyph, Modifier.size(20.dp),
                        tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (showLabel) Text(label, style = MaterialTheme.typography.labelMedium, textAlign = TextAlign.Center,
                maxLines = 2, overflow = TextOverflow.Ellipsis,
                color = if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
internal fun Zone6PlacesTiles(destinations: List<HaloOrbDestination>, selectedPlace: String?, attentionCount: Int,
                              kids: Boolean, singleColumn: Boolean = kids, onPlace: (String) -> Unit) {
    val reduce = rememberHaloReducedMotion()
    val motion = rememberVisualPreferences().motionLevel
    destinations.chunked(if (singleColumn) 1 else 2).forEachIndexed { index, pair ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                pair.forEachIndexed { column, place ->
                    val selected = place.route == selectedPlace
                    val (icon, glyph) = zone6PlaceGlyph(place.route)
                    Surface(onClick = { onPlace(place.route) }, shape = RoundedCornerShape(18.dp),
                        border = BorderStroke(1.dp, if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant),
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        modifier = Modifier.weight(1f).heightIn(min = if (kids) 56.dp else 76.dp)
                            .zone6Entrance(index * 2 + column, reduce, motion, (if (column == 0) 20 else -20).dp, 28.dp)
                            .semantics { this.selected = selected }) {
                        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Zone5Icon(icon, glyph, Modifier.size(22.dp), tint = MaterialTheme.colorScheme.primary)
                            Text(uiText(place.title), style = MaterialTheme.typography.titleSmall)
                            if (place.route == "activity" && attentionCount > 0) {
                                Text(uiText("%1\$s needs your review", attentionCount.toString()),
                                    style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.tertiary)
                            }
                        }
                    }
                }
                if (pair.size == 1 && !singleColumn) Spacer(Modifier.weight(1f))
            }
    }
}

/** Move the actual touch targets so the authored entrance and the layout share the same geometry. */
@Composable
private fun Modifier.zone6Entrance(index: Int, reduced: Boolean, motion: HaloLookMotion, x: Dp, y: Dp): Modifier {
    val foreground = rememberHaloMotionForeground()
    val enabled = foreground && !reduced && motion != HaloLookMotion.STILL
    val progress = remember { Animatable(if (enabled) 0f else 1f) }
    LaunchedEffect(enabled) {
        if (!enabled) progress.snapTo(1f)
        else if (progress.value < 1f) {
            delay(index * if (motion == HaloLookMotion.FULL) 40L else 24L)
            progress.animateTo(1f, if (motion == HaloLookMotion.FULL) spring(dampingRatio = motion.damping, stiffness = 380f)
                else tween(220))
        }
    }
    return graphicsLayer {
        val fraction = if (enabled) progress.value else 1f
        val travel = if (motion == HaloLookMotion.FULL) 1f else .38f
        alpha = fraction.coerceIn(0f, 1f)
        translationX = x.toPx() * (1f - fraction) * travel
        translationY = y.toPx() * (1f - fraction) * travel
        scaleX = 1f - .08f * (1f - fraction) * travel
        scaleY = scaleX
    }
}

@Composable
internal fun Zone6DockBackdrop(modifier: Modifier) {
    val colors = MaterialTheme.colorScheme
    val dark = colors.background.luminance() < .5f
    val highContrast = rememberVisualPreferences().higherContrast
    val top = if (highContrast) colors.surface else lerp(colors.surface, colors.surfaceContainerHigh, .54f)
    val bottom = if (dark) lerp(colors.surface, colors.background, .54f) else colors.surface
    Box(modifier.background(Brush.verticalGradient(listOf(top, bottom))))
}

@Composable
internal fun Zone6DockHorizon() {
    val accent = MaterialTheme.colorScheme.primary
    Box(Modifier.fillMaxWidth().height(4.dp), contentAlignment = Alignment.TopCenter) {
        Box(Modifier.fillMaxWidth(.70f).height(4.dp).background(Brush.horizontalGradient(
            listOf(Color.Transparent, accent.copy(alpha = .11f), Color.Transparent))))
        Box(Modifier.fillMaxWidth(.88f).height(1.dp).background(Brush.horizontalGradient(
            listOf(Color.Transparent, accent.copy(alpha = .70f), Color.Transparent))))
    }
}
