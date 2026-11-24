package com.halo.app.ui.adaptive.v13

/**
 * halo 13.0 · Dynamic UI across the whole app.
 *
 * Every screen is a SurfaceSpec: fixed anchors + regions of blocks.
 * Signals (time, input, device, place, level) pick which blocks are promoted, shown or tucked away.
 * The resolver enforces the calm rules – the person always keeps control.
 * Built on the 12.0 page plan (ui/adaptive/AdaptiveTodayPage.kt, assets/adaptive/blocks.json).
 */
enum class Anchor { BACK, TITLE, HALO_RING, DOCK, PRIMARY_ACTION }
enum class Slot { TOP, MAIN, TOOLS, BOTTOM_SHEET, SIDE_PANEL }
enum class DayPart { EARLY, MORNING, AFTERNOON, EVENING, NIGHT }
enum class InputKind { FINGER, PEN, KEYBOARD, VOICE, GLASSES }
enum class FormFactor { PHONE, FOLD_OPEN, TABLET, DESKTOP }
enum class Situation { HOME, ON_THE_MOVE, ABROAD, STUDYING, WORKING, WINDING_DOWN }

data class Signals(
    val dayPart: DayPart,
    val input: InputKind,
    val form: FormFactor,
    val situation: Situation?,
    val level: ExperienceLevel,
)

data class Block(
    val id: String,
    val slot: Slot,
    val minLevel: ExperienceLevel = ExperienceLevel.SIMPLE,
    val primary: Boolean = false,
    val availableAtLevel: Boolean = true,
)

sealed interface Condition {
    data class Part(val v: DayPart) : Condition
    data class Parts(val values: Set<DayPart>) : Condition
    data class Input(val v: InputKind) : Condition
    data class Form(val v: FormFactor) : Condition
    data class Where(val v: Situation) : Condition
    fun holds(s: Signals) = when (this) {
        is Part -> s.dayPart == v
        is Parts -> s.dayPart in values
        is Input -> s.input == v
        is Form -> s.form == v
        is Where -> s.situation == v
    }
}

sealed interface Change {
    val blockId: String
    data class Promote(override val blockId: String) : Change
    data class Reveal(override val blockId: String) : Change
    data class Tuck(override val blockId: String) : Change
    data class MakePrimary(override val blockId: String) : Change
}

data class SurfaceRule(val id: String, val whenAll: Set<Condition>, val change: Change, val reason: Int /* @StringRes */)

data class SurfaceSpec(val id: String, val anchors: Set<Anchor>, val blocks: List<Block>, val rules: List<SurfaceRule>)

data class Pins(val pinned: Set<String> = emptySet(), val hidden: Set<String> = emptySet(), val frozen: Boolean = false)

data class ResolvedSurface(
    val visible: List<Block>,
    val tucked: List<Block>,          // under "Show more" – still searchable
    val primary: Block?,
    val applied: List<SurfaceRule>,   // shown in "Why does it look like this?"
)

object SurfaceResolver {
    /** At most this many rule changes on one open – the screen must still feel familiar. */
    const val MAX_CHANGES_PER_OPEN = 2

    fun resolve(spec: SurfaceSpec, s: Signals, pins: Pins): ResolvedSurface {
        val blocksById = spec.blocks.associateBy(Block::id)
        val applied = if (pins.frozen) emptyList() else spec.rules.asSequence().filter { rule ->
            val id = rule.change.blockId
            val block = blocksById[id]
            block != null && id !in pins.hidden && id !in pins.pinned && rule.whenAll.all { it.holds(s) } &&
                (rule.change !is Change.Tuck || block.availableAtLevel && block.minLevel.ordinal <= s.level.ordinal)
        }.distinctBy { it.change.blockId }.take(MAX_CHANGES_PER_OPEN).toList()

        val revealed = applied.filterNot { it.change is Change.Tuck }.map { it.change.blockId }.toSet()
        val tuckedByRule = applied.filter { it.change is Change.Tuck }.map { it.change.blockId }.toSet()

        val (shown, tucked) = spec.blocks
            .filterNot { it.id in pins.hidden }
            .partition { b ->
                b.id in pins.pinned || b.id in revealed ||
                    (b.availableAtLevel && b.minLevel.ordinal <= s.level.ordinal && b.id !in tuckedByRule)
            }
        val primaryId = applied.firstNotNullOfOrNull { (it.change as? Change.MakePrimary)?.blockId }
        val ordered = shown.sortedWith(compareBy({ it.slot.ordinal }, {
            when (it.id) {
                in pins.pinned -> 0
                primaryId -> 1
                in revealed -> 2
                else -> 3
            }
        }))
        val primary = ordered.firstOrNull { it.id == primaryId } ?: ordered.firstOrNull { it.primary }
        // Invariant: exactly one primary action; anchors are never part of blocks, so they cannot move.
        return ResolvedSurface(ordered, tucked, primary, applied)
    }
}
