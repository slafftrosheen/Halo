package com.halo.app.ui.onboarding.v13

import java.security.SecureRandom

/**
 * halo 13.0 · "I've used halo before".
 * Three ways in: (1) old phone nearby → scan a code, (2) a backup file (backup/EverydayArchive.kt), (3) start fresh.
 * Phone-to-phone: one-time code in the QR, valid 10 minutes, both screens show the same 3 words to confirm,
 * transfer is end-to-end encrypted on the local network. NEEDS SECURITY REVIEW before shipping.
 * Never moved: AI service keys (re-enter), Kids grown-up PIN (device-owned), anything the person unticks.
 */
data class TransferOffer(val code: ByteArray, val words: List<String>, val expiresAtMs: Long)

enum class MovePart { NOTES_AND_DRAWINGS, PLANNER, SOURCES, LOOKS_AND_WALLPAPER, KIDS_PROFILES, STUDY, SETTINGS }

object MoveFromOldPhone {
    const val VALID_MS = 10 * 60 * 1000L
    private val WORDS = listOf("amber","birch","cloud","delta","ember","fjord","grove","harbor","iris","juniper","kettle","lumen","meadow","nectar","orbit","pebble")

    fun offer(nowMs: Long, rnd: SecureRandom = SecureRandom()): TransferOffer {
        val code = ByteArray(16).also(rnd::nextBytes)
        val words = (0 until 3).map { WORDS[(code[it].toInt() and 0xFF) % WORDS.size] }
        return TransferOffer(code, words, nowMs + VALID_MS)
    }
    fun stillValid(o: TransferOffer, nowMs: Long) = nowMs < o.expiresAtMs
    val NEVER_MOVED = listOf("ai_service_keys", "kids_grown_up_pin")
}
