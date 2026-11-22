package com.halo.app.ui.screens.kids

import androidx.annotation.StringRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import com.halo.app.R
import com.halo.app.language.uiText
import com.halo.app.ui.design.HaloShapes
import com.halo.app.ui.icons.HaloGlyph
import com.halo.app.ui.icons.HaloIcon
import com.halo.app.wallpaper.HaloAmbient
import com.halo.app.wallpaper.HaloWallpaper
import com.halo.app.wallpaper.HaloWallpaperCanvas
import com.halo.app.wallpaper.WallpaperOptions
import com.halo.app.wallpaper.rememberWallpaperPreviewClock
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlin.math.sin

@StringRes
internal fun KidsSky.labelRes(): Int = when (this) {
    KidsSky.STARS -> R.string.kids_sky_stars
    KidsSky.SEA -> R.string.kids_sky_sea
    KidsSky.GARDEN -> R.string.kids_sky_garden
    KidsSky.MOON -> R.string.kids_sky_moon
    KidsSky.LUMEN_GARDEN -> R.string.wallpaper_garden
    KidsSky.SEASONS -> R.string.wallpaper_seasons
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun KidsSkyPicker(settings: KidsPresentationSettings, onChoose: suspend (KidsSky) -> Unit, onDismiss: () -> Unit) {
    val scope = rememberCoroutineScope()
    var saving by remember { mutableStateOf(false) }
    var problem by remember { mutableStateOf(false) }
    ModalBottomSheet(onDismissRequest = { if (!saving) onDismiss() }) {
        Column(
            Modifier.fillMaxWidth().heightIn(max = 600.dp).verticalScroll(rememberScrollState()).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(stringResource(R.string.kids_sky_picker), style = MaterialTheme.typography.titleLarge)
            Text(stringResource(R.string.kids_sky_tap_help), style = MaterialTheme.typography.bodyMedium)
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                maxItemsInEachRow = if (androidx.compose.ui.platform.LocalDensity.current.fontScale >= 1.5f) 1 else 2,
            ) {
                for (sky in KidsSky.entries.filter { it in settings.allowedSkies }) {
                    key(sky) {
                        val label = stringResource(sky.labelRes())
                        val spoken = rememberKidsSpokenChoice(label, alwaysRead = true) {
                            if (!saving) {
                                saving = true
                                problem = false
                                scope.launch {
                                    try {
                                        onChoose(sky)
                                        onDismiss()
                                    } catch (cancelled: CancellationException) {
                                        throw cancelled
                                    } catch (_: Exception) {
                                        problem = true
                                    } finally {
                                        saving = false
                                    }
                                }
                            }
                        }
                        val confirm = stringResource(R.string.kids_choice_tap_again)
                        val active = settings.sky == sky
                        Surface(
                            onClick = spoken.onClick,
                            enabled = !saving,
                            modifier = Modifier.weight(1f).semantics {
                                role = Role.RadioButton
                                selected = active
                                if (spoken.heard) stateDescription = confirm
                            },
                            shape = HaloShapes.card,
                            color = MaterialTheme.colorScheme.surfaceContainerLow,
                            border = BorderStroke(if (active || spoken.heard) 2.dp else 1.dp,
                                if (active || spoken.heard) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant),
                        ) {
                            Column(Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                KidsSkyArtwork(sky, Modifier.fillMaxWidth().height(96.dp).clip(HaloShapes.control), still = settings.still)
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    if (spoken.heard || active) HaloIcon(if (spoken.heard) HaloGlyph.SPEAK else HaloGlyph.SUCCESS,
                                        null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                                    Text(label, style = MaterialTheme.typography.titleMedium)
                                }
                            }
                        }
                    }
                }
            }
            if (problem) Text(uiText("Could not save your change. Try again."), color = MaterialTheme.colorScheme.error)
            TextButton(onClick = onDismiss, enabled = !saving, modifier = Modifier.heightIn(min = 56.dp)) { Text(uiText("Go back")) }
        }
    }
}

/** This section is mounted only inside the verified grown-up options sheet. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun KidsAppearanceControls(settings: KidsPresentationSettings, onApply: suspend (KidsPresentationSettings) -> Unit) {
    var draft by remember(settings) { mutableStateOf(settings) }
    var saving by remember { mutableStateOf(false) }
    var problem by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    Text(stringResource(R.string.kids_sky_allowed), style = MaterialTheme.typography.titleMedium)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        for (sky in KidsSky.entries) {
            val checked = sky in draft.allowedSkies
            FilterChip(
                selected = checked,
                onClick = {
                    val allowed = if (checked) draft.allowedSkies - sky else draft.allowedSkies + sky
                    draft = draft.copy(allowedSkies = allowed)
                },
                label = { Text(stringResource(sky.labelRes())) },
                enabled = !saving && (!checked || draft.allowedSkies.size > 1),
                modifier = Modifier.heightIn(min = 56.dp),
            )
        }
    }
    Text(stringResource(R.string.kids_learn_size), style = MaterialTheme.typography.titleMedium)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterChip(draft.preReader, { draft = draft.copy(preReader = true) },
            { Text(stringResource(R.string.kids_learn_pictures)) }, enabled = !saving, modifier = Modifier.heightIn(min = 56.dp))
        FilterChip(!draft.preReader, { draft = draft.copy(preReader = false) },
            { Text(stringResource(R.string.kids_learn_simple)) }, enabled = !saving, modifier = Modifier.heightIn(min = 56.dp))
    }
    Text(stringResource(R.string.kids_motion), style = MaterialTheme.typography.titleMedium)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterChip(!draft.still, { draft = draft.copy(still = false) },
            { Text(stringResource(R.string.kids_motion_gentle)) }, enabled = !saving, modifier = Modifier.heightIn(min = 56.dp))
        FilterChip(draft.still, { draft = draft.copy(still = true) },
            { Text(stringResource(R.string.kids_motion_still)) }, enabled = !saving, modifier = Modifier.heightIn(min = 56.dp))
    }
    Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).toggleable(
        value = draft.readAloud, enabled = !saving, role = Role.Switch,
        onValueChange = { draft = draft.copy(readAloud = it) }
    ), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(stringResource(R.string.kids_read_automatically), modifier = Modifier.weight(1f))
        Switch(checked = draft.readAloud, onCheckedChange = null, enabled = !saving)
    }
    Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).toggleable(
        value = draft.handDemos, enabled = !saving, role = Role.Switch,
        onValueChange = { draft = draft.copy(handDemos = it) }
    ), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(stringResource(R.string.coach_kids_demos), modifier = Modifier.weight(1f))
        Switch(checked = draft.handDemos, onCheckedChange = null, enabled = !saving)
    }
    if (problem) Text(uiText("Could not save your change. Try again."), color = MaterialTheme.colorScheme.error)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick = { draft = settings; problem = false },
            enabled = !saving && draft != settings, modifier = Modifier.heightIn(min = 56.dp)) { Text(uiText("Undo")) }
        KidsPrimaryButton(onClick = {
            saving = true
            problem = false
            scope.launch {
                try {
                    onApply(draft)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    problem = true
                } finally {
                    saving = false
                }
            }
        }, enabled = !saving && draft != settings, modifier = Modifier.heightIn(min = 56.dp)) { Text(uiText("Apply")) }
    }
}

private data class SkyColors(val top: Color, val horizon: Color, val disc: Color, val far: Color, val near: Color)

private fun skyColors(sky: KidsSky): SkyColors = when (sky) {
    KidsSky.STARS -> SkyColors(Color(0xFF151433), Color(0xFF66588A), Color(0xFFC8C6EC), Color(0xFF383255), Color(0xFF24223F))
    KidsSky.SEA -> SkyColors(Color(0xFF2E5676), Color(0xFFF0BE9A), Color(0xFFFFDFB4), Color(0xFF527897), Color(0xFF324D6C))
    KidsSky.GARDEN -> SkyColors(Color(0xFFE5DDC3), Color(0xFFF5E8CE), Color(0xFFFAEFBB), Color(0xFFA5BDA0), Color(0xFF7C9C86))
    KidsSky.MOON -> SkyColors(Color(0xFF171C3E), Color(0xFF54849F), Color(0xFFCADDE6), Color(0xFF464B78), Color(0xFF2D365C))
    KidsSky.LUMEN_GARDEN, KidsSky.SEASONS -> error("Toolkit sky uses the shared wallpaper painter")
}

@Composable
internal fun KidsSkyArtwork(
    sky: KidsSky, modifier: Modifier, backdrop: Boolean = false, still: Boolean = false,
    ambient: HaloAmbient = HaloAmbient.IDLE,
) {
    val scene = when (sky) {
        KidsSky.LUMEN_GARDEN -> HaloWallpaper.LUMEN_GARDEN
        KidsSky.SEASONS -> HaloWallpaper.SEASONS
        else -> null
    }
    if (scene != null) {
        val base = MaterialTheme.colorScheme.background
        Box(modifier) {
            HaloWallpaperCanvas(WallpaperOptions(wallpaper = scene, calmerAtNight = false),
                Modifier.matchParentSize(), animate = backdrop && !still, previewAmbient = ambient, kidsSky = true, motionScale = .5f)
            if (backdrop) Box(Modifier.matchParentSize().background(Brush.verticalGradient(
                0f to base.copy(alpha = .79f), .46f to base.copy(alpha = .64f), 1f to base.copy(alpha = .74f),
            )))
        }
        return
    }
    val clock = rememberWallpaperPreviewClock(enabled = backdrop && !still, motionScale = .5f)
    val colors = remember(sky) { skyColors(sky) }
    val opacity = if (backdrop) .31f else 1f
    Canvas(modifier.clipToBounds()) {
        val width = size.width
        val height = size.height
        drawRect(Brush.verticalGradient(listOf(colors.top.copy(alpha = opacity), colors.horizon.copy(alpha = opacity))))
        val drift = sin(clock.elapsed.value * .44f) * .012f
        val disc = Offset(width * if (sky == KidsSky.SEA) .67f else .34f, height * (.29f + drift))
        val radius = size.minDimension * if (sky == KidsSky.GARDEN) .17f else .12f
        drawCircle(Brush.radialGradient(listOf(colors.disc.copy(alpha = opacity * .55f), Color.Transparent),
            center = disc, radius = radius * 1.9f), radius = radius * 1.9f, center = disc)
        drawCircle(colors.disc.copy(alpha = opacity * .86f), radius = radius, center = disc)
        if (sky == KidsSky.STARS || sky == KidsSky.MOON) {
            repeat(8) { star ->
                val x = (.09f + (star * .237f) % .84f) * width
                val y = (.09f + (star * .149f) % .42f) * height
                drawCircle(colors.disc.copy(alpha = opacity * .65f), size.minDimension * .012f, Offset(x, y))
            }
        }
        repeat(2) { layer ->
            val horizon = height * if (layer == 0) .62f else .78f
            val path = Path().apply {
                moveTo(0f, horizon)
                cubicTo(width * .22f, horizon - height * .16f, width * .36f, horizon + height * .10f, width * .57f, horizon)
                cubicTo(width * .74f, horizon - height * .10f, width * .90f, horizon + height * .12f, width, horizon - height * .03f)
                lineTo(width, height)
                lineTo(0f, height)
                close()
            }
            drawPath(path, (if (layer == 0) colors.far else colors.near).copy(alpha = opacity))
        }
    }
}
