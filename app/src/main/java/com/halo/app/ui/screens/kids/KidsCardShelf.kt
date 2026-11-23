package com.halo.app.ui.screens.kids

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate

internal enum class KidsCardTopic { ANIMALS, COUNTING, NATURE, DINOSAURS }
private val builtInTopics = KidsCardTopic.entries.filterNot { it == KidsCardTopic.DINOSAURS }.toSet()

internal data class GrownUpCard(val id: String, val title: String, val text: String, val source: String)
internal enum class KidsAdventureStop { LEARN, MAKE, FIND, TELL }
internal enum class KidsAgeBand(val label: String, val target: Int, val choices: Int, private val stopCount: Int) {
    EARLY("4–6", 72, 2, 2), PRIMARY("7–9", 56, 3, 3), OLDER("10–12", 48, 3, 4);
    val stops get() = KidsAdventureStop.entries.take(stopCount)
}
internal data class KidsAdventurePage(
    val day: LocalDate, val topic: KidsCardTopic, val done: Set<KidsAdventureStop>,
    val age: KidsAgeBand = KidsAgeBand.PRIMARY, val drawing: String = "",
) {
    val complete get() = done.containsAll(age.stops)
}

internal data class KidsCardShelfState(
    val topics: Set<KidsCardTopic> = builtInTopics,
    val favorites: Set<String> = emptySet(),
    val approvedAnswers: List<GrownUpCard> = emptyList(),
    val adventures: List<KidsAdventurePage> = emptyList(),
    val age: KidsAgeBand = KidsAgeBand.PRIMARY,
    val dinosaurRequest: Boolean = false,
)

internal fun KidsCardShelfState.adventureFor(day: LocalDate, selected: KidsCardTopic? = null): KidsAdventurePage? {
    // Disabling a topic takes effect for the day's open adventure as well as the book.
    adventures.firstOrNull { it.day == day && it.topic in topics }?.let { return it }
    val allowed = KidsCardTopic.entries.filter { it in topics }
    if (allowed.isEmpty() || adventures.any { it.day == day && it.complete }) return null
    val topic = selected?.takeIf { it in allowed } ?: allowed[Math.floorMod(day.toEpochDay(), allowed.size.toLong()).toInt()]
    return KidsAdventurePage(day, topic, emptySet(), age)
}

/** Family-managed local shelf. Saved IDs reference bundled cards or a manually approved excerpt. */
internal class KidsCardShelf(context: Context) {
    private val disk = context.applicationContext.getSharedPreferences("halo_kids_cards_v1", Context.MODE_PRIVATE)
    private val writes = Mutex()
    private val mutable = MutableStateFlow<KidsCardShelfState?>(null)
    val state = mutable.asStateFlow()

    suspend fun load(): Unit = withContext(Dispatchers.IO) {
        writes.withLock { if (mutable.value == null) mutable.value = read() }
    }

    suspend fun keep(id: String, topic: KidsCardTopic) = update { current ->
        check(topic in current.topics)
        current.copy(favorites = current.favorites + id)
    }

    suspend fun forget(id: String) = update { it.copy(favorites = it.favorites - id) }

    suspend fun finishAdventureStop(day: LocalDate, topic: KidsCardTopic, stop: KidsAdventureStop,
        drawing: String = "", checkAuthority: () -> Unit) = update(checkAuthority) { current ->
            check(day == LocalDate.now() && topic in current.topics)
            val adventure = requireNotNull(current.adventureFor(day, topic))
            check(adventure.topic == topic && !adventure.complete && stop in adventure.age.stops)
            check(stop !in adventure.done)
            if (drawing.isNotEmpty()) {
                val (_, encoded) = kidsLessonInk(drawing)
                check(stop == KidsAdventureStop.MAKE && encoded.length <= 48_000 &&
                    com.halo.sdk.runtime.ink.InkCodec.decode(encoded)?.strokes?.isNotEmpty() == true)
            }
            val changed = adventure.copy(done = adventure.done + stop, drawing = drawing.ifBlank { adventure.drawing })
            current.copy(adventures = (current.adventures.filterNot { it.day == day } + changed).takeLast(60))
        }

    suspend fun setTopic(topic: KidsCardTopic, enabled: Boolean, verify: () -> Unit = {}) = update(verify) {
        check(topic != KidsCardTopic.DINOSAURS || !enabled || topic in it.topics)
        it.copy(topics = if (enabled) it.topics + topic else it.topics - topic)
    }

    suspend fun setAge(age: KidsAgeBand, verify: () -> Unit) = update(verify) { it.copy(age = age) }
    suspend fun requestDinosaurs(verify: () -> Unit) = update(verify) {
        check(KidsCardTopic.DINOSAURS !in it.topics)
        it.copy(dinosaurRequest = true)
    }

    suspend fun decideDinosaurs(allow: Boolean, verify: () -> Unit) = update(verify) {
        check(it.dinosaurRequest)
        it.copy(dinosaurRequest = false, topics = if (allow) it.topics + KidsCardTopic.DINOSAURS else it.topics)
    }

    suspend fun removeApproved(id: String) = update { current ->
        current.copy(approvedAnswers = current.approvedAnswers.filterNot { it.id == id })
    }

    suspend fun approveAnswer(id: String, title: String, text: String, source: String, verify: () -> Unit) = update(verify) { current ->
        val card = GrownUpCard(id.take(300), title.take(80), text.take(300), source.take(240))
        check(card.title.isNotBlank() && card.text.isNotBlank() && card.source.isNotBlank())
        current.copy(approvedAnswers = (current.approvedAnswers.filterNot { it.id == card.id } + card).takeLast(24))
    }

    private suspend fun update(verify: () -> Unit = {}, change: (KidsCardShelfState) -> KidsCardShelfState) = withContext(Dispatchers.IO) {
        writes.withLock {
            val changed = change(mutable.value ?: read())
            val answers = JSONArray()
            changed.approvedAnswers.forEach { card ->
                answers.put(JSONObject().put("id", card.id).put("title", card.title).put("text", card.text).put("source", card.source))
            }
            val adventures = JSONArray(changed.adventures.map { page ->
                JSONObject().put("day", page.day.toString()).put("topic", page.topic.name)
                    .put("done", JSONArray(page.done.map(KidsAdventureStop::name)))
                    .put("age", page.age.name).put("drawing", page.drawing)
            })
            verify()
            check(disk.edit().putStringSet("topics", changed.topics.map { it.name }.toSet())
                .putStringSet("favorites", changed.favorites).putString("approved", answers.toString())
                .putString("adventures", adventures.toString()).putString("age", changed.age.name)
                .putBoolean("dinosaurRequest", changed.dinosaurRequest).commit())
            mutable.value = changed
        }
    }

    private fun read(): KidsCardShelfState {
        val topicNames = disk.getStringSet("topics", null)
        val topics = if (topicNames == null) builtInTopics else KidsCardTopic.entries.filter { it.name in topicNames }.toSet()
        val answers = runCatching { JSONArray(disk.getString("approved", "[]")) }.getOrElse { JSONArray() }
        val approved = (0 until minOf(answers.length(), 24)).mapNotNull { index ->
            answers.optJSONObject(index)?.let { GrownUpCard(it.optString("id").take(300), it.optString("title").take(80),
                it.optString("text").take(300), it.optString("source").take(240)) }
        }.filter { it.id.isNotBlank() && it.title.isNotBlank() && it.text.isNotBlank() && it.source.isNotBlank() }
        val adventures = runCatching { JSONArray(disk.getString("adventures", "[]")) }.getOrElse { JSONArray() }
        val pages = (0 until minOf(adventures.length(), 60)).mapNotNull { index ->
            val row = adventures.optJSONObject(index) ?: return@mapNotNull null
            val day = runCatching { LocalDate.parse(row.optString("day")) }.getOrNull() ?: return@mapNotNull null
            val topic = KidsCardTopic.entries.firstOrNull { it.name == row.optString("topic") } ?: return@mapNotNull null
            val done = row.optJSONArray("done") ?: return@mapNotNull null
            val names = (0 until minOf(done.length(), KidsAdventureStop.entries.size)).map { done.optString(it) }.toSet()
            val age = KidsAgeBand.entries.firstOrNull { it.name == row.optString("age") } ?: KidsAgeBand.PRIMARY
            val drawing = row.optString("drawing").takeIf { it.length <= 48_020 }.orEmpty()
            KidsAdventurePage(day, topic, age.stops.filter { it.name in names }.toSet(), age, drawing)
        }.distinctBy(KidsAdventurePage::day)
        val age = KidsAgeBand.entries.firstOrNull { it.name == disk.getString("age", null) } ?: KidsAgeBand.PRIMARY
        return KidsCardShelfState(topics, disk.getStringSet("favorites", emptySet()).orEmpty().toSet(), approved, pages,
            age, disk.getBoolean("dinosaurRequest", false))
    }
}
