package com.halo.app.ui.guide

/*
 * The alive layer per page (pack 7.3): what each page lets you edit, its Show me tags, its gesture demos,
 * whether it has an Easy layout and which tour covers it. Reference code, not compiled against 98aa2fb.
 */

enum class HaloPage { HOME, WORKSPACE, ACTIVITY, PLACES, LIBRARY, MEMORY, WIDGETS_SOURCES, DEVICES, SETTINGS, HELP,
    STUDIO, NOTES, STUDY, CANVAS, ASSISTANT, MINI_HALO, KIDS }

enum class HaloTour { HOME_AND_DOCK, TALKING, STUDIO, NOTES, CANVAS, LEARN }

data class PageLayer(
    val edit: List<String>,               // keys of the page's make-it-yours options
    val showMe: List<String>,             // tag ids, in reading order → strings_pages.xml showme_<id>
    val demos: List<CoachGesture> = emptyList(),
    val easyLayout: Boolean = true,
    val tour: HaloTour? = null,
)

val HALO_PAGES: Map<HaloPage, PageLayer> = mapOf(
    HaloPage.HOME to PageLayer(listOf("starts", "greeting"), listOf("starts", "note_camera", "ring", "home", "places"), listOf(CoachGesture.HOLD_RING, CoachGesture.SWIPE_UP_DOCK), tour = HaloTour.HOME_AND_DOCK),
    HaloPage.WORKSPACE to PageLayer(listOf("dock_slot"), listOf("studio", "notes", "study", "memory", "canvas"), tour = HaloTour.HOME_AND_DOCK),
    HaloPage.ACTIVITY to PageLayer(listOf("group_by"), listOf("waiting", "done", "running", "receipt"), tour = HaloTour.HOME_AND_DOCK),
    HaloPage.PLACES to PageLayer(listOf("order", "hidden"), listOf("library", "memory", "widgets", "devices", "settings", "help", "make_it_yours", "history"), listOf(CoachGesture.SWIPE_UP_DOCK), tour = HaloTour.HOME_AND_DOCK),
    HaloPage.LIBRARY to PageLayer(listOf("view", "sort"), listOf("search", "view", "filters")),
    HaloPage.MEMORY to PageLayer(listOf("suggestions"), listOf("suggested", "forget", "remember")),
    HaloPage.WIDGETS_SOURCES to PageLayer(listOf("size", "motion", "match_look"), listOf("preview", "size", "add")),
    HaloPage.DEVICES to PageLayer(listOf("names"), listOf("add", "find", "steps")),
    HaloPage.SETTINGS to PageLayer(emptyList(), listOf("find", "make_it_yours")),
    HaloPage.HELP to PageLayer(emptyList(), listOf("ask", "tours", "checkup")),
    HaloPage.STUDIO to PageLayer(listOf("favourites", "artboard", "left_handed", "snap", "tool_names"), listOf("artboard", "add", "draw", "layers", "more"), listOf(CoachGesture.DRAG_CARD), tour = HaloTour.STUDIO),
    HaloPage.NOTES to PageLayer(listOf("paper", "letters", "spacing", "dictate", "read_back"), listOf("tick", "format", "insert", "dictate"), listOf(CoachGesture.HOLD_RING), tour = HaloTour.NOTES),
    HaloPage.STUDY to PageLayer(listOf("size", "highlight", "spacing", "reminder", "voice"), listOf("path", "try", "help", "check", "keep"), tour = HaloTour.LEARN),
    HaloPage.CANVAS to PageLayer(listOf("card_colours", "links", "snap", "as_list"), listOf("card", "link", "list", "more"), listOf(CoachGesture.DRAG_CARD), tour = HaloTour.CANVAS),
    HaloPage.ASSISTANT to PageLayer(emptyList(), emptyList(), listOf(CoachGesture.HOLD_RING), tour = HaloTour.TALKING),
    HaloPage.MINI_HALO to PageLayer(listOf("bubble_size", "side", "hide_in_video"), emptyList(), tour = HaloTour.TALKING),
    // Kids: child edits only the sky; no tips / stuck detector; Show me = spoken names (tap = hear).
    HaloPage.KIDS to PageLayer(listOf("sky"), emptyList(), listOf(CoachGesture.HOLD_RING), easyLayout = false),
)

/** Library view, Canvas list view and other per-page choices, persisted per page in "halo_look". */
enum class LibraryView { LIST, BIG_PICTURES }
enum class StudioArtboard { PLAIN, DOTS, GRID }
enum class NotesPaper { PLAIN, LINED, GRID }
enum class NotesLetters { EASY_READ, BOOK, ROUND }
enum class LineSpacing(val mult: Float) { NORMAL(1.0f), ROOMY(1.3f), EXTRA(1.6f) }
enum class CanvasCardColours { BY_KIND, BY_ME, ONE_COLOUR }
enum class CanvasLinks { CURVED, STRAIGHT, DOTTED }
enum class ReadHighlight { MINT, LILAC, SUN, UNDERLINE }
enum class BubbleSize(val dp: Int) { SMALL(44), MEDIUM(56), LARGE(68) }
enum class KidsSky { STARS, SEA, GARDEN, MOON }
