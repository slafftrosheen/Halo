package com.halo.app.ui.screens.settings

import com.halo.app.ui.theme.AppearanceMode
import com.halo.app.ui.theme.HaloPalette
import com.halo.app.ui.theme.HaloPalettes

/** Local preview transaction. Live theme/owner emissions update only untouched fields. */
internal data class AppearanceDraft(
    val savedMode: AppearanceMode,
    val savedPalettes: HaloPalettes,
    val mode: AppearanceMode = savedMode,
    val palettes: HaloPalettes = savedPalettes,
) {
    val changed get() = mode != savedMode || palettes != savedPalettes
    fun palette(dark: Boolean) = if (dark) palettes.dark else palettes.light
    fun editPalette(dark: Boolean, palette: HaloPalette) = copy(
        palettes = if (dark) palettes.copy(dark = palette) else palettes.copy(light = palette))
    fun refresh(mode: AppearanceMode, palettes: HaloPalettes) = AppearanceDraft(
        savedMode = mode, savedPalettes = palettes,
        mode = if (this.mode == savedMode) mode else this.mode,
        palettes = HaloPalettes(
            light = if (this.palettes.light == savedPalettes.light) palettes.light else this.palettes.light,
            dark = if (this.palettes.dark == savedPalettes.dark) palettes.dark else this.palettes.dark),
    )
}
