package com.halo.app.speech.diagnostics

import java.util.Locale

data class SpeechScore(val wer: Double, val cer: Double)
object SpeechScoring {
    fun normalize(text: String): String = java.text.Normalizer.normalize(text, java.text.Normalizer.Form.NFC)
        .lowercase(Locale.ROOT).replace(Regex("[\\p{P}\\p{S}]"), "").trim().replace(Regex("\\s+"), " ")
    fun score(reference: String, hypothesis: String): SpeechScore {
        val r = normalize(reference); val h = normalize(hypothesis)
        val words = if (r.isEmpty()) emptyList() else r.split(" ")
        val actual = if (h.isEmpty()) emptyList() else h.split(" ")
        return SpeechScore(distance(words, actual).toDouble() / words.size.coerceAtLeast(1),
            distance(r.toList(), h.toList()).toDouble() / r.length.coerceAtLeast(1))
    }
    private fun <T> distance(a: List<T>, b: List<T>): Int {
        var previous = IntArray(b.size + 1) { it }
        a.forEachIndexed { i, x ->
            val row = IntArray(b.size + 1); row[0] = i + 1
            b.forEachIndexed { j, y -> row[j + 1] = minOf(row[j] + 1, previous[j + 1] + 1, previous[j] + if (x == y) 0 else 1) }
            previous = row
        }
        return previous[b.size]
    }
}
val SpeechDiagnosticReferences = listOf(
    "What's the weather today?", "Open Spotify and play music.", "Set an alarm for seven tomorrow morning.",
    "Remind me tomorrow to call John.", "How much battery is left in my glasses?", "Turn the screen brightness down.",
    "Kāds šodien ir laiks?", "Atver Spotify un atskaņo mūziku.", "Uzliec modinātāju uz septiņiem no rīta.",
    "Atgādini man rīt piezvanīt Jānim.", "Cik daudz baterijas ir palicis brillēm?", "Samazini ekrāna spilgtumu."
)
