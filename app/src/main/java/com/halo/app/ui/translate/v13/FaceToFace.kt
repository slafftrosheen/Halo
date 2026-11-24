package com.halo.app.ui.translate.v13

/**
 * halo 13.0 · Translate: hold to talk first, and a face-to-face screen for a phone lying flat between two people.
 * Languages are the shared scanLanguages list at 99aac2f (en, lv, pl, ru, de, es, fr, uk).
 */
object TranslateLanguages {
    val codes = listOf("en", "lv", "pl", "ru", "de", "es", "fr", "uk")
    fun valid(code: String) = code in codes
}

enum class Side { NEAR, FAR }

data class Turn(val side: Side, val heard: String, val translated: String)

class FaceToFace(val near: String, val far: String) {
    init { require(TranslateLanguages.valid(near) && TranslateLanguages.valid(far) && near != far) }
    var listening: Side? = null; private set
    val turns = mutableListOf<Turn>()

    /** Only one side listens at a time; holding the other side's button while one listens is ignored. */
    fun hold(side: Side): Boolean { if (listening != null) return false; listening = side; return true }
    fun release(heard: String, translate: (String, String, String) -> String?): Turn? {
        val side = listening ?: return null
        listening = null
        if (heard.isBlank()) return null
        val (from, to) = if (side == Side.NEAR) near to far else far to near
        val out = translate(heard, from, to) ?: return null
        return Turn(side, heard, out).also { turns += it }
    }
    /** The FAR half is drawn rotated 180°, and shows text in [far]; each half reads its own language. */
    fun languageShownOn(side: Side) = if (side == Side.NEAR) near else far
}
