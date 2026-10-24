package com.halo.app.ui.presence

import com.halo.app.R
import com.halo.app.ui.icons.HaloGlyph
import com.halo.app.ui.screens.home.HomeToolGroup

/** Presentation choices only; each opens the canonical Home catalog, never executes. */
enum class HaloOrbShortcut(val label: Int, val icon: HaloGlyph, val group: HomeToolGroup, val entry: String? = null) {
    CREATE(R.string.home_tools_create, HaloGlyph.NOTE, HomeToolGroup.CREATE),
    FIND(R.string.home_tools_find, HaloGlyph.SEARCH, HomeToolGroup.FIND),
    NOTEBOOK(R.string.home_tool_notebook, HaloGlyph.NOTE, HomeToolGroup.WORK, "work.notebook"),
    PHONE(R.string.home_tools_phone, HaloGlyph.DEVICE, HomeToolGroup.PHONE);

    companion object {
        val defaults: Set<HaloOrbShortcut> = setOf(CREATE, FIND, NOTEBOOK)
        fun decode(stored: Set<String>?): Set<HaloOrbShortcut> = stored?.mapNotNull { raw ->
            entries.firstOrNull { it.name == raw }
        }?.toSet() ?: defaults
    }
}

data class HaloToolCatalogRequest(val group: HomeToolGroup, val entry: String? = null)
enum class HaloOrbPrimary { TALK, FINISH, STOP, INTERRUPT, REVIEW }
data class HaloOrbInteraction(val primary: HaloOrbPrimary, val canStop: Boolean)

fun haloOrbInteraction(capturing: Boolean, starting: Boolean, processing: Boolean,
    speaking: Boolean, busy: Boolean, review: Boolean): HaloOrbInteraction {
    val active = capturing || starting || processing || speaking || busy
    return HaloOrbInteraction(when {
        review -> HaloOrbPrimary.REVIEW
        speaking -> HaloOrbPrimary.INTERRUPT
        capturing -> HaloOrbPrimary.FINISH
        active -> HaloOrbPrimary.STOP
        else -> HaloOrbPrimary.TALK
    }, active)
}

/** Requested listening is not proof that the Android capture device has opened. */
fun haloOrbInputPresentation(signal: HaloSignalPresentation, capturing: Boolean,
    starting: Boolean, processing: Boolean): HaloSignalPresentation = when {
    capturing -> signal.copy(state = HaloSignalState.CAPTURING, glyph = HaloGlyph.LISTEN,
        label = "Listening…", artwork = PresenceArtwork.CAPTURING_VOICE)
    processing -> signal.copy(state = HaloSignalState.THINKING, glyph = HaloGlyph.THINK,
        label = "Turning your voice into text…", artwork = null)
    starting -> signal.copy(state = HaloSignalState.WAITING, glyph = HaloGlyph.LISTEN,
        label = "Getting ready…", artwork = null)
    signal.state in setOf(HaloSignalState.LISTENING, HaloSignalState.CAPTURING) ->
        signal.copy(state = HaloSignalState.IDLE, glyph = HaloGlyph.HALO,
            label = "Ready when you are", artwork = null, activityLevel = 0f)
    else -> signal
}
