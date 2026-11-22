package com.halo.app.ui.cards

import com.halo.app.ui.icons.HaloGlyph
import com.halo.runtime.cognition.interaction.PresentationNode
import com.halo.runtime.cognition.interaction.PresentationStepState
import com.halo.runtime.cognition.interaction.StructuredPresentation
import com.halo.sdk.runtime.multimodal.MediaAssetRef

internal enum class HaloCardType(val label: String, val glyph: HaloGlyph) {
    FACT("Fact", HaloGlyph.SEE), STEPS("Steps", HaloGlyph.ACTION),
    COMPARE("Compare", HaloGlyph.TOOL), TIMELINE("Timeline", HaloGlyph.ACTIVITY),
    PLACE("Place", HaloGlyph.SEE), PICTURES("Pictures", HaloGlyph.CAMERA),
    QUICK_CHECK("Quick check", HaloGlyph.QUESTION), FLIP("Flip", HaloGlyph.READ_MODE),
    CHART("Chart", HaloGlyph.ACTIVITY), CHECKLIST("Checklist", HaloGlyph.SUCCESS),
    WORD("Word", HaloGlyph.LANGUAGE), PLAN("Plan", HaloGlyph.CALENDAR),
    LABEL_IT("Label it", HaloGlyph.SEE), EXPLAIN_BACK("Explain back", HaloGlyph.CONVERSATION),
    WORKED_EXAMPLE("Worked example", HaloGlyph.ACTION), HELP("Help", HaloGlyph.QUESTION),
}

internal enum class HaloCardSize { PICTURES, SIMPLE, FULL }

internal data class HaloCardSource(val label: String, val locator: String? = null)
internal data class HaloCardRow(val text: String, val label: String? = null, val checked: Boolean = false)
internal data class HaloCardPicture(val assetId: String, val caption: String?, val version: String? = null,
    val studyAsset: MediaAssetRef? = null)
internal data class HaloCardChoice(val text: String, val correct: Boolean, val why: String? = null)

internal sealed interface HaloCardBody {
    data class Summary(val value: String, val note: String? = null) : HaloCardBody
    data class Items(val rows: List<HaloCardRow>) : HaloCardBody
    data class Compare(val columns: List<String>, val rows: List<List<String>>) : HaloCardBody
    data class Pictures(val items: List<HaloCardPicture>) : HaloCardBody
    data class Flip(val front: String, val back: String) : HaloCardBody
    data class Chart(val points: List<Pair<String, Double>>) : HaloCardBody
    data class Quiz(val options: List<HaloCardChoice>) : HaloCardBody
    data class Labels(val picture: HaloCardPicture, val labels: List<HaloCardRow>) : HaloCardBody
}

internal data class HaloCardData(
    val id: String,
    val type: HaloCardType,
    val title: String,
    val body: HaloCardBody,
    val source: HaloCardSource,
    val speak: String,
    val uncertainty: String? = null,
    val size: HaloCardSize = HaloCardSize.FULL,
)

private val orderedItem = Regex("^\\s*\\d{1,2}[.)]\\s+(.+)$")
private val bulletItem = Regex("^\\s*[-*•]\\s+(.+)$")
private val taskItem = Regex("^\\s*[-*]\\s+\\[([ xX])]\\s+(.+)$")
private val datedItem = Regex("^(\\d{4}(?:-\\d{2}-\\d{2})?)\\s*[—–:]\\s*(.+)$")
private val measure = Regex("[+-]?\\d+(?:[.,]\\d+)?(?:\\s?[%°\\p{L}²³]+)?")
private val numericValue = Regex("[+-]?\\d+(?:[.,]\\d+)?")
private val optionItem = Regex("^\\s*[-*]\\s+\\[([ xX])]\\s+(.+?)(?:\\s+[—–]\\s+(.+))?$")
private val markdownTableSeparator = Regex("^:?-{3,}:?$")
private val uncertaintyWords = Regex("(?iu)\\b(i(?:'m|’m| am) not sure|cannot verify|can’t verify|не уверен|не могу подтвердить|neesmu pārliecināts|nie mam pewności)\\b")
private val formatHeading = Regex("^#{1,5}\\s+(.+)$")
private val placeQuestion = Regex("(?iu)^(where|kur|где|gdzie)\\b")
private val wordQuestion = Regex("(?iu)^(what does .* mean|what is the meaning|ko nozīmē|что означает|что значит|co znaczy)")
private val planQuestion = Regex("(?iu)\\b(plan|schedule|plāns|план|запланируй|zaplanuj)\\b")
private val checkHeading = Regex("(?im)^#{1,5}\\s+(?:quick check|проверь себя|pārbaudi sevi|sprawdź się)\\s*:")
private val nextHeading = Regex("(?m)^#{1,5}\\s+")
private val actionHeadings = mapOf(
    "steps" to HaloCardType.STEPS, "шаги" to HaloCardType.STEPS, "soļi" to HaloCardType.STEPS,
    "plan" to HaloCardType.PLAN, "план" to HaloCardType.PLAN, "plāns" to HaloCardType.PLAN,
    "checklist" to HaloCardType.CHECKLIST, "чек-лист" to HaloCardType.CHECKLIST,
)

private fun actionType(heading: String?) = actionHeadings[heading.orEmpty().substringBefore(':').trim().lowercase()]

/** Keep the prose ahead of a recognized quick check while rendering its answer key in the card. */
internal fun cardPlainReply(text: String): String {
    val headings = nextHeading.findAll(text).toList()
    if (headings.isEmpty()) return text
    val remaining = StringBuilder(text.take(headings.first().range.first))
    var projected = 0
    headings.forEachIndexed { index, marker ->
        val start = marker.range.first
        val end = headings.getOrNull(index + 1)?.range?.first ?: text.length
        val section = text.substring(start, end)
        val heading = section.lineSequence().first().trimStart('#').trim()
        val type = chooseCard(section.substringAfter('\n', "").trim(), heading, "")?.first
        val bodyLines = section.substringAfter('\n', "").lineSequence().map(String::trim).filter(String::isNotEmpty).toList()
        val singleBlock = bodyLines.size in 2..12 && bodyLines.all {
            orderedItem.matches(it) || bulletItem.matches(it) || taskItem.matches(it)
        }
        val represented = projected < 3 && (type == HaloCardType.QUICK_CHECK && checkHeading.containsMatchIn(section) ||
            type != null && actionType(heading) == type && singleBlock)
        if (represented) projected++ else remaining.append(section)
    }
    return remaining.toString().trim().ifBlank { text }
}

internal fun haloCardSteps(text: String): List<HaloCardRow> = text.lineSequence().mapNotNull { line ->
    orderedItem.matchEntire(line)?.groupValues?.get(1)?.let { HaloCardRow(it.trim()) }
}.take(12).toList()

internal fun haloCardTimeline(text: String): List<HaloCardRow> = text.lineSequence().mapNotNull { line ->
    val value = bulletItem.matchEntire(line)?.groupValues?.get(1) ?: line.trim()
    datedItem.matchEntire(value)?.destructured?.let { (date, event) -> HaloCardRow(event, date) }
}.take(12).toList()

/** The committed reply stays authoritative. Card bodies only project explicitly available content. */
internal fun homeHaloCards(
    turnId: String,
    question: String?,
    text: String,
    structured: StructuredPresentation?,
    source: HaloCardSource,
): List<HaloCardData> {
    val reply = text.take(12_000).trim()
    if (reply.isBlank()) return emptyList()
    val title = question?.trim()?.takeIf(String::isNotBlank)?.take(80)
    val uncertainty = uncertaintyWords.find(reply)?.let { "" }
    val projected = structured?.root?.let { presentationCards(it, turnId, title, source, uncertainty) }.orEmpty()
    if (projected.isNotEmpty()) return projected.take(3)
    val sections = mutableListOf<Pair<String?, String>>()
    var heading: String? = null
    val lines = mutableListOf<String>()
    reply.lines().forEach { line ->
        val next = formatHeading.matchEntire(line)?.groupValues?.get(1)?.trim()
        if (next != null) {
            sections += heading to lines.joinToString("\n")
            heading = next
            lines.clear()
        } else lines += line
    }
    sections += heading to lines.joinToString("\n")
    return sections.asSequence().mapIndexedNotNull { index, (section, content) ->
        val cardTitle = section?.substringAfter(':')?.trim()?.takeIf(String::isNotEmpty)?.take(80)
            ?: title ?: return@mapIndexedNotNull null
        val (type, body) = chooseCard(content.trim(), section, question.orEmpty()) ?: return@mapIndexedNotNull null
        HaloCardData("$turnId:answer:$index", type, cardTitle, body, source, cardSpeech(body), uncertainty)
    }.take(3).toList()
}

private fun chooseCard(text: String, heading: String?, question: String): Pair<HaloCardType, HaloCardBody>? {
    if (text.isBlank()) return null
    val lines = text.lineSequence().map(String::trim).filter(String::isNotEmpty).take(24).toList()
    val table = lines.filter { it.startsWith('|') && it.endsWith('|') }.map { it.trim('|').split('|').map(String::trim) }
    val rows = table.drop(2).filter { it.size == table.first().size }
    if (table.size >= 3 && table[1].all { markdownTableSeparator.matches(it) } && rows.isNotEmpty()) {
        val points = rows.mapNotNull { row -> row.getOrNull(1)?.replace(',', '.')?.toDoubleOrNull()
            ?.takeIf(Double::isFinite)?.let { row.first() to it } }
        if (table.first().size == 2 && points.size == rows.size && points.size > 1) return HaloCardType.CHART to HaloCardBody.Chart(points)
        return HaloCardType.COMPARE to HaloCardBody.Compare(table.first(), rows)
    }
    val timeline = haloCardTimeline(text)
    if (timeline.size >= 2) return HaloCardType.TIMELINE to HaloCardBody.Items(timeline)
    val header = heading.orEmpty().substringBefore(':').trim().lowercase()
    val ticks = lines.mapNotNull { line -> taskItem.matchEntire(line)?.destructured?.let { (checked, item) ->
        HaloCardRow(item, checked = checked.isNotBlank())
    } }
    if (ticks.isNotEmpty()) {
        if (header in setOf("quick check", "проверь себя", "pārbaudi sevi", "sprawdź się")) {
            val choices = lines.mapNotNull { line -> optionItem.matchEntire(line)?.destructured?.let { (checked, item, why) ->
                HaloCardChoice(item, checked.isNotBlank(), why.takeIf(String::isNotBlank))
            } }
            if (choices.size >= 2 && choices.count { it.correct } == 1 && choices.any { it.correct && it.why != null }) {
                return HaloCardType.QUICK_CHECK to HaloCardBody.Quiz(choices)
            }
        }
        return HaloCardType.CHECKLIST to HaloCardBody.Items(ticks)
    }
    val steps = haloCardSteps(text)
    val explicitAction = actionType(heading)
    if (steps.size >= 2) return (explicitAction ?: if (planQuestion.containsMatchIn(question)) HaloCardType.PLAN else HaloCardType.STEPS) to HaloCardBody.Items(steps)
    if (explicitAction != null) {
        val bullets = lines.mapNotNull { bulletItem.matchEntire(it)?.groupValues?.get(1)?.let { item -> HaloCardRow(item) } }.take(12)
        if (bullets.size >= 2) return explicitAction to HaloCardBody.Items(bullets)
    }
    if (header in setOf("flip", "flashcard", "карточка", "kartīte", "fiszka") && lines.size == 2) {
        return HaloCardType.FLIP to HaloCardBody.Flip(lines[0], lines[1])
    }
    if (wordQuestion.containsMatchIn(question) && (lines.firstOrNull()?.length ?: 0) in 1..120) {
        return HaloCardType.WORD to HaloCardBody.Summary(lines.first(), lines.drop(1).joinToString(" ").takeIf(String::isNotBlank))
    }
    if ((placeQuestion.containsMatchIn(question) || header in setOf("place", "место", "vieta", "miejsce")) && lines.first().length <= 240) {
        return HaloCardType.PLACE to HaloCardBody.Summary(lines.first(), lines.drop(1).joinToString(" ").takeIf(String::isNotBlank))
    }
    val first = lines.firstOrNull()?.trim('*', '.', ' ') ?: return null
    if (measure.matches(first) && numericValue.containsMatchIn(first)) {
        return HaloCardType.FACT to HaloCardBody.Summary(first, lines.drop(1).joinToString(" ").takeIf(String::isNotBlank))
    }
    val singleNumber = numericValue.findAll(first).toList().singleOrNull()
    if (singleNumber != null && first.length <= 180 && lines.size <= 3) {
        val value = measure.find(first, singleNumber.range.first)?.value ?: singleNumber.value
        return HaloCardType.FACT to HaloCardBody.Summary(value, text)
    }
    return null
}

private fun presentationCards(
    root: PresentationNode,
    turnId: String,
    fallbackTitle: String?,
    source: HaloCardSource,
    uncertainty: String?,
): List<HaloCardData> {
    fun project(node: PresentationNode, title: String?): List<HaloCardData> {
        val children = when (node) {
            is PresentationNode.Card -> node.children
            is PresentationNode.Column -> node.children
            is PresentationNode.Row -> node.children
            else -> emptyList()
        }
        val name = (node as? PresentationNode.Card)?.title ?: children.filterIsInstance<PresentationNode.Heading>().firstOrNull()?.text ?: title
        val images = children.filterIsInstance<PresentationNode.ImageReference>()
        val body = when {
            images.isNotEmpty() -> HaloCardType.PICTURES to HaloCardBody.Pictures(images.take(6).map { HaloCardPicture(it.assetId, it.caption) })
            node is PresentationNode.Comparison -> HaloCardType.COMPARE to HaloCardBody.Compare(node.columns, node.rows)
            node is PresentationNode.Checklist -> {
                val type = if (planQuestion.containsMatchIn(node.label ?: name.orEmpty())) HaloCardType.PLAN else HaloCardType.CHECKLIST
                type to HaloCardBody.Items(node.items.map { HaloCardRow(it.text, checked = it.state == PresentationStepState.COMPLETED) })
            }
            node is PresentationNode.KeyValue -> HaloCardType.FACT to HaloCardBody.Summary(node.value, node.label)
            node is PresentationNode.BulletList -> HaloCardType.STEPS to HaloCardBody.Items(node.items.map { HaloCardRow(it) })
            node is PresentationNode.ImageReference -> HaloCardType.PICTURES to HaloCardBody.Pictures(listOf(HaloCardPicture(node.assetId, node.caption)))
            else -> null
        }
        if (body != null) {
            val label = (node as? PresentationNode.KeyValue)?.label ?: name ?: return emptyList()
            return listOf(HaloCardData("$turnId:${node.nodeId}", body.first, label.take(80), body.second, source, cardSpeech(body.second), uncertainty))
        }
        return children.flatMap { project(it, name) }
    }
    return project(root, fallbackTitle)
}

internal fun cardSpeech(body: HaloCardBody): String = when (body) {
    is HaloCardBody.Summary -> listOfNotNull(body.value, body.note).joinToString(". ")
    is HaloCardBody.Items -> body.rows.joinToString(". ") { listOfNotNull(it.label, it.text).joinToString(" — ") }
    is HaloCardBody.Compare -> body.rows.joinToString(". ") { row -> body.columns.zip(row).joinToString(", ") { (label, value) -> "$label — $value" } }
    is HaloCardBody.Pictures -> body.items.mapNotNull { it.caption }.joinToString(". ")
    is HaloCardBody.Flip -> body.front
    is HaloCardBody.Chart -> body.points.joinToString(". ") { (label, value) -> "$label — $value" }
    is HaloCardBody.Quiz -> body.options.joinToString(". ") { it.text }
    is HaloCardBody.Labels -> body.labels.joinToString(". ") { it.text }
}

internal fun cardPracticeAnswer(card: HaloCardData): String = when (val body = card.body) {
    is HaloCardBody.Flip -> body.back
    is HaloCardBody.Quiz -> body.options.firstOrNull { it.correct }?.let { choice ->
        listOfNotNull(choice.text, choice.why).joinToString(". ")
    }.orEmpty()
    else -> card.speak
}
