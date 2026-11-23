package com.halo.app.ui.workspace

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.halo.app.R
import com.halo.app.ui.components.HaloOverflowAction
import com.halo.app.ui.components.HaloScreenOverflow

@Composable
internal fun StudioOverflowMenu(busy: Boolean, hasUnsavedChanges: Boolean,
    onBackground: () -> Unit, onPresets: () -> Unit, onSaveAs: () -> Unit, onVersions: () -> Unit,
    onDiscard: () -> Unit, onExportPng: () -> Unit, onExportJpeg: () -> Unit, onCustomize: (() -> Unit)? = null, onVectorExport: (() -> Unit)? = null) {
    val customizationLabel = stringResource(R.string.alive_studio_look)
    HaloScreenOverflow(listOf(
        HaloOverflowAction(stringResource(R.string.studio_background), onClick = onBackground),
        HaloOverflowAction(stringResource(R.string.studio_presets), onClick = onPresets),
        HaloOverflowAction(stringResource(R.string.studio_save_as), onClick = onSaveAs),
        HaloOverflowAction(stringResource(R.string.studio_versions), onClick = onVersions),
        HaloOverflowAction(stringResource(R.string.studio_export_png), onClick = onExportPng),
        HaloOverflowAction(stringResource(R.string.studio_export_jpeg), onClick = onExportJpeg),
        HaloOverflowAction(stringResource(R.string.studio_discard), hasUnsavedChanges, onDiscard),
    ) + listOfNotNull(onVectorExport?.let { HaloOverflowAction(com.halo.app.language.uiText("Export project"), onClick = it) },
        onCustomize?.let { HaloOverflowAction(customizationLabel, onClick = it) }), enabled = !busy)
}
