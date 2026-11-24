package com.halo.app.wallpaper

import android.app.WallpaperManager
import android.content.ComponentName
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.halo.app.R
import com.halo.app.language.uiText
import com.halo.app.ui.design.HaloShapes
import com.halo.app.ui.icons.HaloGlyph
import com.halo.app.ui.icons.HaloIcon
import com.halo.app.ui.theme.rememberVisualPreferences
import com.halo.app.ui.theme.rememberHaloLayoutLocked
import com.halo.app.ui.workspace.HaloWorkspaceToggleRow
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun WallpaperControls() {
    val context = LocalContext.current
    val preferences = remember(context) { WallpaperPreferences.get(context) }
    val saved by preferences.state.collectAsStateWithLifecycle()
    var editing by rememberSaveable { mutableStateOf(false) }
    var draft by remember(saved) { mutableStateOf(saved.current) }
    var previewTime by remember { mutableStateOf<Float?>(null) }
    var previewScheduled by remember { mutableStateOf(false) }
    var previewAmbient by remember { mutableStateOf(HaloAmbient.IDLE) }
    var saving by remember { mutableStateOf(false) }
    var failure by remember { mutableStateOf(false) }
    var androidFailure by remember { mutableStateOf(false) }
    var photoFailure by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val photoStore = remember(context) { WallpaperPhotoStore(context) }
    val layoutLocked = rememberHaloLayoutLocked()
    fun chooseGalleryStyle(style: HaloWallpaper) {
        previewTime = null
        previewScheduled = false
        draft = draft.chooseGalleryScene(style, wallpaperPeriod(haloWallpaperTime()))
    }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null && !saving) {
            saving = true
            photoFailure = false
            scope.launch {
                try {
                    val previous = draft.photo
                    val imported = photoStore.importPhoto(uri)
                    val hadPhotoStyle = draft.usesPhoto()
                    draft = draft.copy(photo = imported, inApp = true)
                    if (!hadPhotoStyle) chooseGalleryStyle(HaloWallpaper.YOUR_PHOTO)
                    val keep = setOfNotNull(saved.current.photo?.id, saved.previous?.previous?.photo?.id)
                    previous?.id?.takeIf { it !in keep }?.let { withContext(Dispatchers.IO) { photoStore.delete(it) } }
                } catch (cancelled: CancellationException) { throw cancelled }
                catch (_: Exception) { photoFailure = true }
                finally { saving = false }
            }
        }
    }
    val wallName = stringResource(saved.current.wallpaperAt(wallpaperPeriod(haloWallpaperTime())).label)
    val photoReady = !draft.usesPhoto() || draft.photo != null
    val dirty = draft != saved.current
    val launcherComponent = remember(context) { ComponentName(context, HaloLiveWallpaperService::class.java) }
    val visual = rememberVisualPreferences()
    val canApply = !saving && !layoutLocked && photoReady && dirty
    fun persist(change: suspend () -> Unit) {
        if (saving) return
        saving = true
        failure = false
        scope.launch {
            try { change() }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { failure = true }
            finally { saving = false }
        }
    }
    fun openAndroidPicker() {
        val manager = WallpaperManager.getInstance(context)
        val allowed = runCatching { manager.isWallpaperSupported && manager.isSetWallpaperAllowed }.getOrDefault(false)
        androidFailure = !allowed || runCatching {
            val preview = Intent(WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER)
                .putExtra(WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT, launcherComponent)
            try { context.startActivity(preview) }
            catch (_: android.content.ActivityNotFoundException) {
                context.startActivity(Intent(WallpaperManager.ACTION_LIVE_WALLPAPER_CHOOSER))
            }
        }.isFailure
    }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedButton(onClick = { editing = !editing }, enabled = saved.loaded && !saving,
            modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
            HaloIcon(HaloGlyph.APPEARANCE, null)
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.alive_wallpaper_title, wallName), Modifier.weight(1f))
        }
        if (editing) {
            val displayTime = previewTime
            val period = wallpaperPeriod(displayTime ?: haloWallpaperTime())
            val preview = draft.wallpaperAt(period)
            val previewClock = rememberWallpaperPreviewClock()
            Box(Modifier.fillMaxWidth().height(270.dp), contentAlignment = Alignment.Center) {
                Crossfade(preview, modifier = Modifier.widthIn(max = 176.dp).fillMaxSize().clip(HaloShapes.hero),
                    animationSpec = tween(if (!previewClock.moving) 0 else if (previewScheduled) 2_000 else 190),
                    label = "Wallpaper preview") { style ->
                    HaloWallpaperCanvas(draft.copy(wallpaper = style), Modifier.fillMaxSize(), previewTime,
                        previewAmbient = previewAmbient)
                }
                if (preview == HaloWallpaper.YOUR_PHOTO && draft.photo == null) {
                    Surface(shape = HaloShapes.pill, color = MaterialTheme.colorScheme.surfaceContainerHighest) {
                        Text(stringResource(R.string.wallpaper_photo_example), Modifier.padding(horizontal = 12.dp, vertical = 6.dp))
                    }
                }
            }
            Text(stringResource(R.string.wallpaper_preview_name, stringResource(preview.label)), style = MaterialTheme.typography.titleMedium)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button({ persist { preferences.apply(saved, draft) } }, enabled = canApply,
                    modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.wallpaper_apply)) }
                TextButton({ draft = saved.current; previewTime = null; previewScheduled = false; previewAmbient = HaloAmbient.IDLE },
                    enabled = !saving && dirty, modifier = Modifier.heightIn(min = 48.dp)) { Text(uiText("Undo")) }
            }
            Text(stringResource(when {
                layoutLocked -> R.string.layout_kept
                visual.higherContrast && draft.inApp -> R.string.wallpaper_contrast_note
                saved.current.inApp && !dirty -> R.string.wallpaper_applied_home
                draft.inApp -> R.string.wallpaper_apply_home_hint
                else -> R.string.wallpaper_apply_style_hint
            }), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            OutlinedButton({ persist {
                if (dirty) preferences.apply(saved, draft)
                openAndroidPicker()
            } }, enabled = !saving && photoReady && (!dirty || !layoutLocked), modifier = Modifier.heightIn(min = 48.dp)) {
                Text(stringResource(R.string.wallpaper_activate_android))
            }
            Text(stringResource(R.string.wallpaper_android_hint), style = MaterialTheme.typography.bodySmall)
            if (failure) Text(uiText("Could not save your change. Try again."), color = MaterialTheme.colorScheme.error)
            if (androidFailure) Text(stringResource(R.string.wallpaper_android_error), color = MaterialTheme.colorScheme.error)
            if (preview == HaloWallpaper.YOUR_PHOTO && draft.photo == null) {
                Button({ picker.launch(arrayOf("image/*")) }, enabled = !saving,
                    modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.wallpaper_photo_choose)) }
            }
            if (draft.schedule.enabled) Text(stringResource(R.string.wallpaper_gallery_schedule,
                stringResource(wallpaperPeriod(haloWallpaperTime()).label)), style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            WallpaperCollection.entries.forEach { collection ->
                WallpaperCollectionRow(collection, draft, preview, !saving, previewTime, previewClock, ::chooseGalleryStyle)
            }
            if (dirty) Button(onClick = { persist { preferences.apply(saved, draft) } }, enabled = canApply,
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                Text(stringResource(R.string.wallpaper_apply))
            }
            Text(stringResource(R.string.alive_wallpaper_see_at), style = MaterialTheme.typography.labelLarge)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(null to R.string.alive_wallpaper_now, .25f to R.string.alive_wallpaper_morning,
                    .5f to R.string.alive_wallpaper_day, .75f to R.string.alive_wallpaper_evening,
                    0f to R.string.alive_wallpaper_night).forEach { (value, label) ->
                    FilterChip(previewTime == value, { previewTime = value; previewScheduled = draft.schedule.enabled },
                        label = { Text(stringResource(label)) },
                        modifier = Modifier.heightIn(min = 48.dp))
                }
            }
            if (draft.reactsToHalo) {
                Text(stringResource(R.string.wallpaper_see_reactions), style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(HaloAmbient.IDLE to R.string.wallpaper_reaction_idle, HaloAmbient.LISTENING to R.string.wallpaper_reaction_listening,
                        HaloAmbient.WORKING to R.string.wallpaper_reaction_working, HaloAmbient.NEEDS_YOU to R.string.wallpaper_reaction_needs_you)
                        .forEach { (mode, label) ->
                            FilterChip(previewAmbient == mode, { previewAmbient = mode }, label = { Text(stringResource(label)) },
                                modifier = Modifier.heightIn(min = 48.dp))
                        }
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                HaloWorkspaceToggleRow(title = stringResource(R.string.alive_wallpaper_in_app), checked = draft.inApp,
                    onCheckedChange = { if (!saving) draft = draft.copy(inApp = it) })
                HaloWorkspaceToggleRow(title = stringResource(R.string.alive_wallpaper_react), checked = draft.reactsToHalo,
                    onCheckedChange = { if (!saving) draft = draft.copy(reactsToHalo = it) })
                HaloWorkspaceToggleRow(title = stringResource(R.string.alive_wallpaper_calm), checked = draft.calmerAtNight,
                    onCheckedChange = { if (!saving) draft = draft.copy(calmerAtNight = it) })
                HaloWorkspaceToggleRow(title = stringResource(R.string.f13_sky_follow), checked = draft.followSky,
                    onCheckedChange = { enabled -> if (!saving) {
                        previewScheduled = false
                        draft = draft.copy(followSky = enabled, inApp = draft.inApp || enabled)
                    } })
                if (draft.followSky) {
                    Text(stringResource(R.string.f13_sky_follow_sub), style = MaterialTheme.typography.bodySmall)
                    Text(stringResource(R.string.f13_sky_no_location), style = MaterialTheme.typography.bodySmall)
                    com.halo.app.ui.theme.v13.Halo13Animation(com.halo.app.ui.theme.v13.Halo13Motion.SKY_CYCLE,
                        Modifier.fillMaxWidth().height(96.dp))
                }
                HaloWorkspaceToggleRow(title = stringResource(R.string.wallpaper_schedule), checked = draft.schedule.enabled,
                    onCheckedChange = { if (!saving) {
                        previewScheduled = it
                        draft = draft.copy(schedule = draft.schedule.copy(enabled = it), inApp = draft.inApp || it,
                            followSky = draft.followSky && !it)
                    } })
                if (draft.schedule.enabled && !draft.followSky) {
                    Text(stringResource(R.string.wallpaper_schedule_explanation), style = MaterialTheme.typography.bodySmall)
                    WallpaperPeriod.entries.forEach { period ->
                        WallpaperScheduleChoice(period, draft.schedule[period], !saving) { wallpaper ->
                            previewTime = period.previewTime
                            previewScheduled = true
                            draft = draft.copy(schedule = draft.schedule.with(period, wallpaper))
                        }
                    }
                }
            }
            if (draft.usesPhoto() || draft.photo != null) {
                WallpaperPhotoControls(draft.photo, !saving, choose = { picker.launch(arrayOf("image/*")) },
                    onChange = { draft = draft.copy(photo = it) },
                    onRemove = {
                        val prior = draft.photo
                        val schedule = draft.schedule
                        draft = draft.copy(photo = null,
                            wallpaper = if (draft.wallpaper == HaloWallpaper.YOUR_PHOTO) HaloWallpaper.PAPER_CUT else draft.wallpaper,
                            schedule = WallpaperPeriod.entries.fold(schedule) { value, period ->
                                if (value[period] == HaloWallpaper.YOUR_PHOTO) value.with(period, null) else value
                            })
                        if (prior?.id != saved.current.photo?.id && prior?.id != saved.previous?.previous?.photo?.id) {
                            prior?.let { removed -> scope.launch(Dispatchers.IO) { photoStore.delete(removed.id) } }
                        }
                    })
            }
            if (photoFailure) Text(stringResource(R.string.wallpaper_photo_error), color = MaterialTheme.colorScheme.error)
            if (saved.previous?.canRestore(System.currentTimeMillis()) == true) {
                TextButton(onClick = { persist { preferences.restore(saved) } },
                    enabled = !saving && !layoutLocked && draft == saved.current, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text(stringResource(R.string.alive_wallpaper_restore))
                }
            }
        }
    }
}

private val HaloWallpaper.label: Int
    get() = when (this) {
        HaloWallpaper.DRIFT -> R.string.alive_wallpaper_drift
        HaloWallpaper.AURORA -> R.string.alive_wallpaper_aurora
        HaloWallpaper.TIDE -> R.string.alive_wallpaper_tide
        HaloWallpaper.DAY_ARC -> R.string.alive_wallpaper_arc
        HaloWallpaper.CONSTELLATION -> R.string.alive_wallpaper_constellation
        HaloWallpaper.PAPER -> R.string.alive_wallpaper_paper
        HaloWallpaper.NORTHERN_FOREST -> R.string.wallpaper_forest
        HaloWallpaper.BALTIC_DUNES -> R.string.wallpaper_dunes
        HaloWallpaper.RAIN_GLASS -> R.string.wallpaper_rain
        HaloWallpaper.LUMEN_GARDEN -> R.string.wallpaper_garden
        HaloWallpaper.NIGHT_TRAIN -> R.string.wallpaper_train
        HaloWallpaper.SEASONS -> R.string.wallpaper_seasons
        HaloWallpaper.RIGA_ROOFTOPS -> R.string.wallpaper_rooftops
        HaloWallpaper.DEEP_SEA -> R.string.wallpaper_deep_sea
        HaloWallpaper.PAPER_CUT -> R.string.wallpaper_paper_cut
        HaloWallpaper.YOUR_PHOTO -> R.string.wallpaper_photo
        HaloWallpaper.MEADOW_WIND -> R.string.wallpaper_meadow
        HaloWallpaper.INK_WASH -> R.string.wallpaper_ink
    }

@Composable
private fun WallpaperCollectionRow(collection: WallpaperCollection, draft: WallpaperOptions, selectedStyle: HaloWallpaper, enabled: Boolean,
    previewTime: Float?, sharedPreviewClock: WallpaperPreviewClock, onChoice: (HaloWallpaper) -> Unit) {
    Text(stringResource(when (collection) {
        WallpaperCollection.LIVELY -> R.string.wallpaper_collection_lively
        WallpaperCollection.CALM -> R.string.wallpaper_collection_calm
        WallpaperCollection.WORLD -> R.string.wallpaper_collection_world
    }), style = MaterialTheme.typography.titleMedium)
    LazyRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(HaloWallpaper.entries.filter { it.collection == collection }, key = { it.name }) { wallpaper ->
            val selected = wallpaper == selectedStyle
            Surface(onClick = { onChoice(wallpaper) }, enabled = enabled, shape = HaloShapes.control,
                border = BorderStroke(if (selected) 2.dp else 1.dp,
                    if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier.width(110.dp).semantics { this.selected = selected }) {
                Column(Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    HaloWallpaperCanvas(draft.copy(wallpaper = wallpaper, reactsToHalo = false),
                        Modifier.fillMaxWidth().height(120.dp).clip(HaloShapes.control), previewTime, animate = false,
                        sharedPreview = sharedPreviewClock)
                    Text(stringResource(wallpaper.label), style = MaterialTheme.typography.labelLarge)
                    Text(stringResource(wallpaper.motionLabel), style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun WallpaperScheduleChoice(period: WallpaperPeriod, style: HaloWallpaper?, enabled: Boolean, onChoice: (HaloWallpaper?) -> Unit) {
    var open by remember(period) { mutableStateOf(false) }
    val periodLabel = stringResource(period.label)
    val styleLabel = stringResource(style?.label ?: R.string.wallpaper_schedule_default)
    Box {
        OutlinedButton({ open = true }, enabled = enabled, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
            Text(stringResource(R.string.wallpaper_period_choice, periodLabel, styleLabel))
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }, modifier = Modifier.heightIn(max = 340.dp)) {
            DropdownMenuItem({ Text(stringResource(R.string.wallpaper_schedule_default)) }, { open = false; onChoice(null) })
            HaloWallpaper.entries.forEach { wallpaper ->
                DropdownMenuItem({ Text(stringResource(wallpaper.label)) }, { open = false; onChoice(wallpaper) })
            }
        }
    }
}

private val WallpaperPeriod.label: Int
    get() = when (this) {
        WallpaperPeriod.MORNING -> R.string.alive_wallpaper_morning
        WallpaperPeriod.DAY -> R.string.alive_wallpaper_day
        WallpaperPeriod.NIGHT -> R.string.alive_wallpaper_night
    }

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun WallpaperPhotoControls(photo: WallpaperPhoto?, enabled: Boolean, choose: () -> Unit, onChange: (WallpaperPhoto) -> Unit, onRemove: () -> Unit) {
    Text(stringResource(R.string.wallpaper_photo_privacy), style = MaterialTheme.typography.bodySmall)
    OutlinedButton(choose, enabled = enabled, modifier = Modifier.heightIn(min = 48.dp)) {
        Text(stringResource(if (photo == null) R.string.wallpaper_photo_choose else R.string.wallpaper_photo_replace))
    }
    if (photo != null) {
        Text(stringResource(R.string.wallpaper_photo_blur), style = MaterialTheme.typography.labelLarge)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            WallpaperBlur.entries.forEach { blur ->
                FilterChip(photo.blur == blur, { onChange(photo.copy(blur = blur)) }, enabled = enabled,
                    label = { Text(stringResource(when (blur) {
                        WallpaperBlur.LIGHT -> R.string.wallpaper_blur_light
                        WallpaperBlur.SOFT -> R.string.wallpaper_blur_soft
                        WallpaperBlur.STRONG -> R.string.wallpaper_blur_strong
                    })) }, modifier = Modifier.heightIn(min = 48.dp))
            }
        }
        Text(stringResource(R.string.wallpaper_photo_ring), style = MaterialTheme.typography.labelLarge)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            WallpaperRing.entries.forEach { ring ->
                FilterChip(photo.ring == ring, { onChange(photo.copy(ring = ring)) }, enabled = enabled,
                    label = { Text(stringResource(when (ring) {
                        WallpaperRing.TOP -> R.string.wallpaper_ring_top
                        WallpaperRing.CENTER -> R.string.wallpaper_ring_center
                        WallpaperRing.LOWER -> R.string.wallpaper_ring_lower
                    })) }, modifier = Modifier.heightIn(min = 48.dp))
            }
        }
        TextButton(onRemove, enabled = enabled, modifier = Modifier.heightIn(min = 48.dp)) {
            Text(stringResource(R.string.wallpaper_photo_remove))
        }
    }
}

private val WallpaperPeriod.previewTime: Float
    get() = when (this) {
        WallpaperPeriod.MORNING -> .30f
        WallpaperPeriod.DAY -> .58f
        WallpaperPeriod.NIGHT -> .90f
    }

private val HaloWallpaper.motionLabel: Int
    get() = when (this) {
        HaloWallpaper.DRIFT, HaloWallpaper.AURORA, HaloWallpaper.CONSTELLATION -> R.string.wallpaper_motion_halo
        HaloWallpaper.TIDE, HaloWallpaper.DAY_ARC, HaloWallpaper.RIGA_ROOFTOPS -> R.string.wallpaper_motion_day
        HaloWallpaper.BALTIC_DUNES, HaloWallpaper.MEADOW_WIND -> R.string.wallpaper_motion_wind
        HaloWallpaper.RAIN_GLASS, HaloWallpaper.SEASONS -> R.string.wallpaper_motion_weather
        HaloWallpaper.LUMEN_GARDEN -> R.string.wallpaper_motion_planner
        HaloWallpaper.YOUR_PHOTO -> R.string.wallpaper_motion_photo
        HaloWallpaper.PAPER, HaloWallpaper.PAPER_CUT, HaloWallpaper.INK_WASH -> R.string.wallpaper_motion_still
        HaloWallpaper.NORTHERN_FOREST, HaloWallpaper.NIGHT_TRAIN, HaloWallpaper.DEEP_SEA -> R.string.wallpaper_motion_slow
    }
