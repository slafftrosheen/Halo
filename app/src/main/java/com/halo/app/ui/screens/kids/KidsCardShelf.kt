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

internal enum class KidsCardTopic { ANIMALS, COUNTING, NATURE }

internal data class GrownUpCard(val id: String, val title: String, val text: String, val source: String)
internal enum class KidsAdventureStop { LEARN, MAKE, FIND }
internal data class KidsAdventurePage(val day: LocalDate, val topic: KidsCardTopic, val done: Set<KidsAdventureStop>) {
    val complete get() = done.size == KidsAdventureStop.entries.size
}

internal data class KidsCardShelfState(
    val topics: Set<KidsCardTopic> = KidsCardTopic.entries.toSet(),
    val favorites: Set<String> = emptySet(),
    val approvedAnswers: List<GrownUpCard> = emptyList(),
    val adventures: List<KidsAdventurePage> = emptyList(),
)

internal fun KidsCardShelfState.adventureFor(day: LocalDate): KidsAdventurePage? {
    // Disabling a topic takes effect for the day's open adventure as well as the book.
    adventures.firstOrNull { it.day == day && it.topic in topics }?.let { return it }
    val allowed = KidsCardTopic.entries.filter { it in topics }
    if (allowed.isEmpty() || adventures.any { it.day == day && it.complete }) return null
    return KidsAdventurePage(day, allowed[Math.floorMod(day.toEpochDay(), allowed.size.toLong()).toInt()], emptySet())
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

    suspend fun finishAdventureStop(day: LocalDate, topic: KidsCardTopic, stop: KidsAdventureStop, checkAuthority: () -> Unit) =
        update(checkAuthority) { current ->
            check(day == LocalDate.now())
            val adventure = requireNotNull(current.adventureFor(day))
            check(adventure.topic == topic && !adventure.complete)
            val changed = adventure.copy(done = adventure.done + stop)
            current.copy(adventures = (current.adventures.filterNot { it.day == day } + changed).takeLast(60))
        }

    suspend fun setTopic(topic: KidsCardTopic, enabled: Boolean) = update {
        it.copy(topics = if (enabled) it.topics + topic else it.topics - topic)
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
            })
            verify()
            check(disk.edit().putStringSet("topics", changed.topics.map { it.name }.toSet())
                .putStringSet("favorites", changed.favorites).putString("approved", answers.toString())
                .putString("adventures", adventures.toString()).commit())
            mutable.value = changed
        }
    }

    private fun read(): KidsCardShelfState {
        val topicNames = disk.getStringSet("topics", null)
        val topics = KidsCardTopic.entries.filter { topicNames == null || it.name in topicNames }.toSet()
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
            val names = (0 until minOf(done.length(), 3)).map { done.optString(it) }.toSet()
            KidsAdventurePage(day, topic, KidsAdventureStop.entries.filter { it.name in names }.toSet())
        }.distinctBy(KidsAdventurePage::day)
        return KidsCardShelfState(topics, disk.getStringSet("favorites", emptySet()).orEmpty().toSet(), approved, pages)
    }
}
