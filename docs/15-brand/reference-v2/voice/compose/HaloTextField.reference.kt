package com.halo.app.ui.components

/*
 * halo DevKit v1.4 · Voice & Read everywhere — REFERENCE contract (Codex owns the implementation).
 *
 * Goal: one text-field component everywhere. Every input picks a VoiceFieldKind; voice + read come for free,
 * built on the existing HaloVoiceTextActions (local STT only, uncommitted preview, canonical TtsRequested playback,
 * cloud-voice consent). No screen should call OutlinedTextField directly after migration (see VOICE_READ_HANDOFF.md §2).
 *
 * Motion assets (assets/halo/fx/, existing naming → existing loader):
 *   halo_fx_voice_field      markers open | listen (loop) | recognize (loop) | close   — trailing mic
 *   halo_fx_dictation_insert marker  insert                                            — preview → caret
 *   halo_fx_read_aloud       markers start | reading (loop) | stop                     — speaker / Read aloud
 *   halo_fx_voice_wave       marker  listen (loop)                                     — long dictation sheet
 *   halo_fx_canvas_voice_add marker  add                                               — Canvas voice command
 * Phases MUST follow HaloVoiceTextActions.phase (IDLE / RECORDING / RECOGNIZING / CANCELLING) and real TTS events,
 * never a timer. Mic level drives HaloRipple / bar scale in Compose, not the Lottie.
 */

enum class VoiceFieldKind(val dictation: Dictation, val read: Read) {
    TEXT(Dictation.PREVIEW, Read.VALUE),
    SEARCH(Dictation.DIRECT_WITH_UNDO, Read.RESULTS),
    SHORT(Dictation.PREVIEW, Read.VALUE),
    NUMBER(Dictation.PREVIEW_NORMALIZED, Read.VALUE),     // "half past seven" → 07:30, show heard + value
    CODE(Dictation.DIGITS_CONFIRM, Read.MASKED),            // digits one by one, always confirmed
    URL_ID(Dictation.OFF, Read.SPELLED),                    // paste/scan/pick; read spells it out
    SECRET(Dictation.NEVER, Read.NEVER),                    // never routed to STT or TTS; TalkBack: "hidden"
    STRUCTURED(Dictation.DESCRIBE, Read.SUMMARY);           // "Describe it" → halo drafts, shown as a draft

    enum class Dictation { PREVIEW, DIRECT_WITH_UNDO, PREVIEW_NORMALIZED, DIGITS_CONFIRM, OFF, NEVER, DESCRIBE }
    enum class Read { VALUE, RESULTS, MASKED, SPELLED, SUMMARY, NEVER }
    val showsMic get() = dictation != Dictation.OFF && dictation != Dictation.NEVER
}

/*
 * @Composable fun HaloTextField(
 *     value: String, onValueChange: (String) -> Unit, kind: VoiceFieldKind,
 *     label: String?, modifier: Modifier = Modifier, placeholder: String? = null,
 *     readLabel: String? = label,               // what the read button announces
 *     language: String? = null, enabled: Boolean = true, singleLine: Boolean = kind != VoiceFieldKind.TEXT,
 * )
 *  - trailing: kind.showsMic → mic (halo_fx_voice_field); SECRET → lock glyph, no action; URL_ID → speaker "Spell it".
 *  - helper line (never toast-only): model missing → "Get speech model"; permission → "Allow microphone";
 *    cloud voice → existing consent dialog ("Send and read" / "Keep text private"); nothing heard → retry hint.
 *  - preview chip under the field: "Use spoken text" / "Edit before using" / "Discard" (existing strings).
 */

/** Voice approval rule (VR-CONFIRM). Voice may approve only reversible, non-sensitive actions. */
enum class ActionRisk { REVERSIBLE, IRREVERSIBLE_EXTERNAL, DESTRUCTIVE, PAYMENT, NEW_PERMISSION, CLOUD_CONSENT, SHARE }

fun voiceMayApprove(risk: ActionRisk): Boolean = risk == ActionRisk.REVERSIBLE
// Otherwise speak + show: "Please tap “<named action>” to confirm." and keep the named button focused.

/** One audio owner: starting to listen stops reading; starting to read stops listening. */
sealed interface AudioOwner { data object None : AudioOwner; data class Listening(val fieldId: String) : AudioOwner; data class Reading(val requestId: String) : AudioOwner }

fun nextOwner(current: AudioOwner, request: AudioOwner): AudioOwner = request // caller must stop `current` first (barge-in), same frame as FX cancel
