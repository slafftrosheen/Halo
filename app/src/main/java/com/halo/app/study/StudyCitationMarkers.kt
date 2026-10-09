package com.halo.app.study

import com.halo.app.data.study.StudyCitation

/** A supplied source is not a citation until the visible answer actually names its marker. */
object StudyCitationMarkers {
    private val marker = Regex("\\[S([0-9]+)\\]")
    private fun sourceIndex(key: String): Int? = key.takeIf { it in setOf("1", "2", "3", "4") }?.toInt()?.minus(1)
    fun citations(text: String, sources: List<StudySourceExcerpt>): List<StudyCitation> =
        marker.findAll(text).mapNotNull { sourceIndex(it.groupValues[1]) }.distinct().mapNotNull { index ->
            sources.getOrNull(index)?.takeIf { it.coverage != SourceCoverage.MISSING || it.suppliedVisual }?.let {
                StudyCitation(it.objectId, it.revision, it.locator, it.text.take(400), it.version, it.title)
            }
        }.toList()
    fun unknownCount(text: String, sources: List<StudySourceExcerpt>): Int = marker.findAll(text).count {
        val index = sourceIndex(it.groupValues[1])
        index == null || index >= sources.size
    }
    fun displayText(text: String, sources: List<StudySourceExcerpt>): String = marker.replace(text) {
        val index = sourceIndex(it.groupValues[1])
        if (index != null && index < sources.size) "[source]" else ""
    }
}
data class StudyCardDraft(val question: String, val answer: String, val source: StudySourceExcerpt)
object StudyCardDraftParser {
    fun parse(raw: String, sources: List<StudySourceExcerpt>): List<StudyCardDraft>? = runCatching {
        require(raw.length <= 12_000)
        if(!raw.trimStart().startsWith("{")) {
            val lines=raw.lineSequence().filter { it.isNotBlank() }.toList()
            require(lines.size in 1..5)
            return@runCatching lines.map { line ->
                val match=Regex("^(S[1-4]) \\| Q: (.+?) \\| A: (.+)$").matchEntire(line.trim()) ?: error("Invalid card line")
                val question=match.groupValues[2].trim(); val answer=match.groupValues[3].trim()
                require(question.isNotEmpty() && question.length <= 500 && answer.isNotEmpty() && answer.length <= 1000)
                val source=sources.getOrNull(match.groupValues[1].drop(1).toInt()-1) ?: error("Unknown source")
                require(source.coverage != SourceCoverage.MISSING || source.suppliedVisual)
                StudyCardDraft(question,answer,source)
            }
        }
        val reader = com.squareup.moshi.JsonReader.of(okio.Buffer().writeUtf8(raw))
        reader.use {
            val root = reader.readJsonValue() as? Map<*, *> ?: error("Object required")
            require(reader.peek() == com.squareup.moshi.JsonReader.Token.END_DOCUMENT)
            require(root.keys == setOf("cards"))
            val cards = root["cards"] as? List<*> ?: error("Cards required")
            require(cards.size in 1..5)
            cards.map { value ->
                val item = value as? Map<*, *> ?: error("Card object required")
                require(item.keys == setOf("question", "answer", "source"))
                val question = (item["question"] as? String ?: error("String required")).trim()
                val answer = (item["answer"] as? String ?: error("String required")).trim()
                require(question.isNotEmpty() && question.length <= 500 && answer.isNotEmpty() && answer.length <= 1000)
                val key = item["source"] as? String ?: error("Source required")
                require(Regex("S[1-4]").matches(key))
                val source = sources.getOrNull(key.drop(1).toInt() - 1) ?: error("Unknown source")
                require(source.coverage != SourceCoverage.MISSING || source.suppliedVisual)
                StudyCardDraft(question, answer, source)
            }
        }
    }.getOrNull()
}
