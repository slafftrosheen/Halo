package com.halo.app.wallpaper

import android.app.WallpaperManager
import android.content.ComponentName
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.*
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
    var editing by remember { mutableStateOf(false) }
    var draft by remember(saved) { mutableStateOf(saved.current) }
    var previewTime by remember { mutableStateOf<Float?>(null) }
    var saving by remember { mutableStateOf(false) }
    var failure by remember { mutableStateOf(false) }
    var photoFailure by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val photoStore = remember(context) { WallpaperPhotoStore(context) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null && !saving) {
            saving = true
            photoFailure = false
            scope.launch {
                try {
                    val previous = draft.photo
                    val imported = photoStore.importPhoto(uri)
                    draft = draft.copy(photo = imported, wallpaper = if (draft.usesPhoto()) draft.wallpaper else HaloWallpaper.YOUR_PHOTO)
                    val keep = setOfNotNull(saved.current.photo?.id, saved.previous?.previous?.photo?.id)
                    previous?.id?.takeIf { it !in keep }?.let { withContext(Dispatchers.IO) { photoStore.delete(it) } }
                } catch (cancelled: CancellationException) { throw cancelled }
                catch (_: Exception) { photoFailure = true }
                finally { saving = false }
            }
        }
    }
    val wallName = stringResource(saved.current.wallpaper.label)
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        TextButton(onClick = { editing = !editing }, modifier = Modifier.heightIn(min = 48.dp)) {
            Text(stringResource(R.string.alive_wallpaper_title, wallName))
        }
        if (editing) {
            val displayTime = previewTime
            val preview = if (displayTime != null) draft.copy(wallpaper = draft.wallpaperAt(wallpaperPeriod(displayTime))) else draft
            HaloWallpaperCanvas(preview, Modifier.fillMaxWidth().height(220.dp).clip(HaloShapes.hero), previewTime)
            WallpaperCollection.entries.forEach { collection ->
                WallpaperCollectionRow(collection, draft, !saving, previewTime) { wallpaper ->
                    previewTime = null
                    draft = draft.copy(wallpaper = wallpaper)
                }
            }
            Text(stringResource(R.string.alive_wallpaper_see_at), style = MaterialTheme.typography.labelLarge)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(null to R.string.alive_wallpaper_now, .25f to R.string.alive_wallpaper_morning,
                    .5f to R.string.alive_wallpaper_day, .75f to R.string.alive_wallpaper_evening,
                    0f to R.string.alive_wallpaper_night).forEach { (value, label) ->
                    FilterChip(previewTime == value, { previewTime = value }, label = { Text(stringResource(label)) },
                        modifier = Modifier.heightIn(min = 48.dp))
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                HaloWorkspaceToggleRow(title = stringResource(R.string.alive_wallpaper_in_app), checked = draft.inApp,
                    onCheckedChange = { if (!saving) draft = draft.copy(inApp = it) })
                HaloWorkspaceToggleRow(title = stringResource(R.string.alive_wallpaper_react), checked = draft.reactsToHalo,
                    onCheckedChange = { if (!saving) draft = draft.copy(reactsToHalo = it) })
                HaloWorkspaceToggleRow(title = stringResource(R.string.alive_wallpaper_calm), checked = draft.calmerAtNight,
                    onCheckedChange = { if (!saving) draft = draft.copy(calmerAtNight = it) })
                HaloWorkspaceToggleRow(title = stringResource(R.string.wallpaper_schedule), checked = draft.schedule.enabled,
                    onCheckedChange = { if (!saving) draft = draft.copy(schedule = draft.schedule.copy(enabled = it)) })
                if (draft.schedule.enabled) {
                    Text(stringResource(R.string.wallpaper_schedule_explanation), style = MaterialTheme.typography.bodySmall)
                    WallpaperPeriod.entries.forEach { period ->
                        WallpaperScheduleChoice(period, draft.schedule[period], !saving) { wallpaper ->
                            previewTime = period.previewTime
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
            fun persist(change: suspend () -> Unit) {
                saving = true
                scope.launch {
                    try {
                        change()
                        failure = false
                    } catch (cancelled: CancellationException) { throw cancelled }
                    catch (_: Exception) { failure = true }
                    finally { saving = false }
                }
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton({ draft = saved.current }, enabled = !saving && draft != saved.current,
                    modifier = Modifier.heightIn(min = 48.dp)) { Text(uiText("Undo")) }
                Button({ persist { preferences.apply(saved, draft) } },
                    enabled = !saving && draft != saved.current && (!draft.usesPhoto() || draft.photo != null),
                    modifier = Modifier.heightIn(min = 48.dp)) { Text(uiText("Apply")) }
            }
            if (saved.previous?.canRestore(System.currentTimeMillis()) == true) {
                TextButton(onClick = { persist { preferences.restore(saved) } },
                    enabled = !saving && draft == saved.current, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text(stringResource(R.string.alive_wallpaper_restore))
                }
            }
            TextButton(onClick = {
                failure = runCatching {
                    context.startActivity(Intent(WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER)
                        .putExtra(WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT,
                            ComponentName(context, HaloLiveWallpaperService::class.java)))
                }.isFailure
            }, enabled = !saving && draft == saved.current, modifier = Modifier.heightIn(min = 48.dp)) {
                Text(stringResource(R.string.alive_wallpaper_set_android))
            }
            if (failure) Text(uiText("Could not save your change. Try again."), color = MaterialTheme.colorScheme.error)
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
private fun WallpaperCollectionRow(collection: WallpaperCollection, draft: WallpaperOptions, enabled: Boolean,
    previewTime: Float?, onChoice: (HaloWallpaper) -> Unit) {
    Text(stringResource(when (collection) {
        WallpaperCollection.LIVELY -> R.string.wallpaper_collection_lively
        WallpaperCollection.CALM -> R.string.wallpaper_collection_calm
        WallpaperCollection.WORLD -> R.string.wallpaper_collection_world
    }), style = MaterialTheme.typography.titleMedium)
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        HaloWallpaper.entries.filter { it.collection == collection }.forEach { wallpaper ->
            val selected = wallpaper == draft.wallpaper
            Surface(onClick = { onChoice(wallpaper) }, enabled = enabled, shape = HaloShapes.control,
                border = BorderStroke(if (selected) 2.dp else 1.dp,
                    if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier.width(110.dp).semantics { this.selected = selected }) {
                Column(Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    HaloWallpaperCanvas(draft.copy(wallpaper = wallpaper, reactsToHalo = false),
                        Modifier.fillMaxWidth().height(116.dp).clip(HaloShapes.control), previewTime, animate = false)
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
    val periodLabel = stringResource(when (period) {
        WallpaperPeriod.MORNING -> R.string.alive_wallpaper_morning
        WallpaperPeriod.DAY -> R.string.alive_wallpaper_day
        WallpaperPeriod.NIGHT -> R.string.alive_wallpaper_night
    })
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
