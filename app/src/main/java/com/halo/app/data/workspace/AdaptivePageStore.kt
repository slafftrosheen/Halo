package com.halo.app.data.workspace

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

internal data class AdaptiveLayout(val slots: List<AdaptiveSlot>, val large: Set<AdaptiveSlot> = emptySet()) {
    init {
        require(slots.size <= 9 && slots.distinct() == slots)
        require(large.all { it in slots })
    }

    fun move(slot: AdaptiveSlot, direction: Int): AdaptiveLayout {
        require(direction == -1 || direction == 1)
        val index = slots.indexOf(slot)
        if (index < 0 || index + direction !in slots.indices) return this
        return copy(slots = slots.toMutableList().apply { removeAt(index); add(index + direction, slot) })
    }

    fun swap(old: AdaptiveSlot, replacement: AdaptiveSlot): AdaptiveLayout {
        if (old !in slots || replacement in slots) return this
        return AdaptiveLayout(slots.map { if (it == old) replacement else it },
            if (old in large) large - old + replacement else large)
    }

    fun remove(slot: AdaptiveSlot) = AdaptiveLayout(slots - slot, large - slot)

    fun json() = JSONObject().put("slots", slots.joinToString(",") { it.name })
        .put("large", large.joinToString(",") { it.name })

    companion object {
        fun slotList(raw: String) = raw.split(',').mapNotNull { item -> AdaptiveSlot.entries.firstOrNull { it.name == item } }.distinct()
        fun read(value: JSONObject): AdaptiveLayout {
            val slots = slotList(value.getString("slots")).take(9)
            return AdaptiveLayout(slots, slotList(value.optString("large")).toSet().intersect(slots.toSet()))
        }
    }
}

internal data class AdaptivePageRef(val id: String, val revision: Long) {
    init {
        require(id.matches(Regex("[a-z0-9-]{1,36}")) && revision in 1..9_007_199_254_740_991L)
    }
}

internal data class SavedAdaptivePage(val ref: AdaptivePageRef, val title: String, val layout: AdaptiveLayout)

internal class AdaptivePageChanged : IllegalStateException("The saved page was changed. Reopen it first.")

/** A local, layout-only store. Collector data, executable routes, and the user's request are never stored here. */
internal class AdaptivePageStore(context: Context) {
    private val disk = context.applicationContext.getSharedPreferences("halo_adaptive_pages_v1", Context.MODE_PRIVATE)

    fun changes() = callbackFlow {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == null || key in setOf("pages", "pinned", "hidden")) trySend(Unit)
        }
        disk.registerOnSharedPreferenceChangeListener(listener)
        trySend(Unit)
        awaitClose { disk.unregisterOnSharedPreferenceChangeListener(listener) }
    }.buffer(Channel.CONFLATED)

    fun pages(): List<SavedAdaptivePage> {
        val source = disk.getString("pages", null)
        if (source == null) {
            val slots = AdaptiveLayout.slotList(disk.getString("pinned", null).orEmpty()).take(9)
            return if (slots.isEmpty()) emptyList() else listOf(SavedAdaptivePage(AdaptivePageRef("legacy", 1), "", AdaptiveLayout(slots)))
        }
        val rows = JSONArray(source)
        return List(minOf(rows.length(), MAX_PAGES)) { rows.getJSONObject(it) }.map { row ->
            SavedAdaptivePage(AdaptivePageRef(row.getString("id"), row.getLong("revision")),
                row.getString("title").take(80), AdaptiveLayout.read(row))
        }.also { pages -> check(pages.map { it.ref.id }.distinct().size == pages.size) }
    }

    fun requirePage(ref: AdaptivePageRef): SavedAdaptivePage =
        pages().find { it.ref == ref } ?: throw AdaptivePageChanged()

    fun hidden(): Set<AdaptiveSlot> = disk.getStringSet("hidden", null).orEmpty()
        .mapNotNullTo(mutableSetOf()) { key -> AdaptiveSlot.entries.firstOrNull { it.name == key } }

    suspend fun save(previous: AdaptivePageRef?, title: String, layout: AdaptiveLayout, verify: () -> Unit): SavedAdaptivePage =
        withContext(Dispatchers.IO) {
            require(title.length in 1..80 && title.none(Char::isISOControl) && layout.slots.isNotEmpty())
            synchronized(disk) {
                val current = pages()
                if (previous == null) require(current.size < MAX_PAGES) { "Saved page limit reached" }
                else {
                    val before = current.find { it.ref == previous } ?: throw AdaptivePageChanged()
                    if (before.title == title && before.layout == layout) {
                        verify()
                        return@synchronized before
                    }
                }
                val saved = SavedAdaptivePage(AdaptivePageRef(previous?.id ?: UUID.randomUUID().toString(),
                    (previous?.revision ?: 0L) + 1), title, layout)
                val updated = if (previous == null) listOf(saved) + current
                    else current.map { if (it.ref == previous) saved else it }
                persist(updated, verify)
                saved
            }
        }

    suspend fun delete(ref: AdaptivePageRef, verify: () -> Unit) = withContext(Dispatchers.IO) {
        synchronized(disk) {
            val current = pages()
            if (current.none { it.ref == ref }) throw AdaptivePageChanged()
            persist(current.filterNot { it.ref == ref }, verify)
        }
    }

    private fun persist(pages: List<SavedAdaptivePage>, verify: () -> Unit) {
        val rows = JSONArray(pages.map { item -> item.layout.json().put("id", item.ref.id)
            .put("revision", item.ref.revision).put("title", item.title) })
        verify()
        check(disk.edit().putString("pages", rows.toString()).remove("pinned").commit())
    }

    suspend fun hide(slot: AdaptiveSlot, verify: () -> Unit) = withContext(Dispatchers.IO) {
        synchronized(disk) {
            val next = hidden().mapTo(mutableSetOf()) { it.name } + slot.name
            verify()
            check(disk.edit().putStringSet("hidden", next).commit())
        }
    }

    suspend fun clearLearning(verify: () -> Unit) = withContext(Dispatchers.IO) {
        synchronized(disk) {
            verify()
            check(disk.edit().remove("hidden").commit())
        }
    }

    companion object { const val MAX_PAGES = 24 }
}
