package com.halo.app.ui.zone6

import androidx.annotation.StringRes
import com.halo.app.R
import com.halo.app.ui.icons.HaloGlyph

internal enum class Zone6Tour(
    val route: String,
    @StringRes val title: Int,
    val glyph: HaloGlyph,
    val actionIds: Set<String> = emptySet(),
) {
    HOME("home", R.string.help_tour_home, HaloGlyph.HOME),
    TALK("home", R.string.help_tour_talk, HaloGlyph.SPEAK),
    STUDIO("studio", R.string.help_tour_studio, HaloGlyph.APPEARANCE,
        setOf("page.studio.new", "page.studio.draw", "page.studio.layers")),
    NOTES("file_tools", R.string.help_tour_notes, HaloGlyph.NOTE,
        setOf("page.office.new", "page.office.open", "page.office.edit")),
    CANVAS("canvases", R.string.help_tour_canvas, HaloGlyph.TOOL, setOf("page.studio.new-canvas")),
    LEARN("study", R.string.help_tour_learn, HaloGlyph.READ_MODE,
        setOf("page.study.new", "page.study.homework")),
    ;

    val seenKey: String get() = "tour.v7." + name.lowercase()

    fun atRoute(current: String?): Boolean = current?.substringBefore('?') == route
}
