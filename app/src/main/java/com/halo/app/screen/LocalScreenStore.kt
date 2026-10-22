package com.halo.app.screen

import android.content.Context
import kotlinx.coroutines.flow.*
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant

/** Local presentation cache. Source ownership stays with Notebook, Calendar and routines. */
class LocalScreenStore(context: Context) : ScreenItemStore {
    private val preferences = context.applicationContext.getSharedPreferences("halo_screen", Context.MODE_PRIVATE)
    private val lock = Any()
    private val _items = MutableStateFlow(readItems())
    val items: StateFlow<List<ScreenItem>> = _items.asStateFlow()
    private val _layout = MutableStateFlow(readLayout())
    val layout: StateFlow<ScreenLayout?> = _layout.asStateFlow()
    private val _hidePrivate = MutableStateFlow(preferences.getBoolean("hide_private", true))
    val hidePrivate = _hidePrivate.asStateFlow()
    private val _keepCharging = MutableStateFlow(preferences.getBoolean("keep_charging", false))
    val keepCharging = _keepCharging.asStateFlow()
    private val _dimAtNight = MutableStateFlow(preferences.getBoolean("dim_night", true))
    val dimAtNight = _dimAtNight.asStateFlow()
    private val _topics = MutableStateFlow(preferences.getString("topics", "").orEmpty())
    val topics = _topics.asStateFlow()
    fun setTopics(value: String) {
        val cleaned = value.trim().take(1000)
        preferences.edit().putString("topics", cleaned).apply()
        _topics.value = cleaned
    }
    private var undoLayout: ScreenLayout? = null

    override suspend fun publish(item: ScreenItem) = synchronized(lock) {
        validateItem(item)
        val old = _items.value.firstOrNull { it.id == item.id }
        if (old?.updatedAt != null && item.updatedAt != null && old.updatedAt > item.updatedAt) return@synchronized
        persistItems((_items.value.filterNot { it.id == item.id } + item).takeLast(100))
    }

    /** Atomically writes a locally sourced item and the layout card that displays it. */
    suspend fun publishAndSaveLayout(item: ScreenItem, value: ScreenLayout, recordUndo: Boolean = false) = synchronized(lock) {
        validateItem(item)
        require(value.cards.size <= 24 && value.cards.all { it.widgetType in ScreenWidgets.types })
        val old = _items.value.firstOrNull { it.id == item.id }
        val updated = if (old?.updatedAt != null && item.updatedAt != null && old.updatedAt > item.updatedAt) _items.value
            else (_items.value.filterNot { it.id == item.id } + item).takeLast(100)
        val nextLayout = value.copy(version = (_layout.value?.version ?: 0) + 1)
        if (recordUndo) undoLayout = _layout.value
        preferences.edit().putString("items", itemsJson(updated)).putString("layout", nextLayout.toJson().toString()).apply()
        _items.value = updated
        _layout.value = nextLayout
    }

    override suspend fun markFailed(id: String, at: Instant) {
        val old = _items.value.firstOrNull { it.id == id } ?: return
        publish(old.copy(state = ScreenItemState.STALE, updatedAt = at))
    }

    override fun observe(ids: Set<String>): Flow<List<ScreenItem>> = items.map { values -> values.filter { it.id in ids } }

    fun saveLayout(value: ScreenLayout, recordUndo: Boolean = true) = synchronized(lock) {
        require(value.cards.size <= 24 && value.cards.all { it.widgetType in ScreenWidgets.types })
        if (recordUndo) undoLayout = _layout.value
        persistLayout(value.copy(version = (_layout.value?.version ?: 0) + 1))
    }

    fun undo() = synchronized(lock) { undoLayout?.let { previous ->
        undoLayout = _layout.value
        persistLayout(previous)
    } }

    fun setPrivacy(enabled: Boolean) { preferences.edit().putBoolean("hide_private", enabled).apply(); _hidePrivate.value = enabled }
    fun setKeepCharging(enabled: Boolean) { preferences.edit().putBoolean("keep_charging", enabled).apply(); _keepCharging.value = enabled }
    fun setDimAtNight(enabled: Boolean) { preferences.edit().putBoolean("dim_night", enabled).apply(); _dimAtNight.value = enabled }

    private fun persistLayout(value: ScreenLayout) {
        preferences.edit().putString("layout", value.toJson().toString()).apply()
        _layout.value = value
    }

    private fun validateItem(item: ScreenItem) {
        require(item.id.length in 1..240 && item.title.length <= 240)
        require(item.sourceLinks.size <= 8 && item.sourceLinks.all {
            it.title.length in 1..200 && it.url.length in 1..2048 && it.url.startsWith("https://")
        })
    }

    private fun persistItems(items: List<ScreenItem>) {
        preferences.edit().putString("items", itemsJson(items)).apply()
        _items.value = items
    }

    private fun itemsJson(items: List<ScreenItem>) = JSONArray().apply { items.forEach { put(it.toJson()) } }.toString()

    private fun ScreenLayout.toJson() = JSONObject().put("id", id).put("name", name).put("version", version)
        .put("cards", JSONArray().apply { cards.forEach { card ->
            put(JSONObject().put("type", card.widgetType).put("itemId", card.itemId)
                .put("size", card.size.name).put("pinned", card.pinned).put("config", JSONObject(card.config)))
        } })

    private fun readLayout(): ScreenLayout? = runCatching {
        val json = JSONObject(preferences.getString("layout", null) ?: return@runCatching null)
        val cards = json.getJSONArray("cards")
        ScreenLayout(json.getString("id"), json.getString("name"), (0 until cards.length()).map { index ->
            val c = cards.getJSONObject(index)
            ScreenCard(c.getString("type"), c.optString("itemId").takeUnless { it.isBlank() || it == "null" },
                CardSize.valueOf(c.getString("size")), c.optBoolean("pinned"),
                    c.optJSONObject("config")?.let { config -> config.keys().asSequence().associateWith { config.getString(it) } }.orEmpty())
        }.filter { it.widgetType in ScreenWidgets.types }.take(24), json.optInt("version", 1))
    }.getOrNull()

    private fun readItems(): List<ScreenItem> = runCatching {
        val array = JSONArray(preferences.getString("items", "[]"))
        (0 until array.length()).map { index ->
            val j = array.getJSONObject(index)
            fun instant(key: String) = j.optString(key).takeIf { it.isNotBlank() && it != "null" }?.let(Instant::parse)
            ScreenItem(j.getString("id"), ScreenSourceType.valueOf(j.getString("sourceType")),
                j.optString("sourceId").takeUnless { it.isBlank() || it == "null" }, j.getString("title"),
                j.optString("summary").takeUnless { it == "null" }, ScreenContentType.valueOf(j.getString("contentType")),
                instant("updatedAt"), instant("lastSuccessAt"), instant("expiresAt"), ScreenItemState.valueOf(j.getString("state")),
                ScreenSensitivity.valueOf(j.getString("sensitivity")),
                j.optString("attribution").takeUnless { it.isBlank() || it == "null" },
                j.optJSONArray("availableActions")?.let { actions ->
                    (0 until actions.length()).mapNotNull { index -> screenAction(actions.optString(index)) }.distinct()
                } ?: listOf(ScreenAction.OpenSource, ScreenAction.HideFromScreen),
                j.optJSONArray("sourceLinks")?.let { links ->
                    (0 until links.length()).mapNotNull { index ->
                        links.optJSONObject(index)?.let { link ->
                            val title = link.optString("title").take(200)
                            val url = link.optString("url").take(2048)
                            if (title.isNotBlank() && url.startsWith("https://")) ScreenSourceLink(title, url) else null
                        }
                    }.take(8)
                }.orEmpty())
        }
    }.getOrDefault(emptyList())

    private fun screenAction(id: String): ScreenAction? = when (id) {
        ScreenAction.ReadAloud.id -> ScreenAction.ReadAloud
        ScreenAction.OpenSource.id -> ScreenAction.OpenSource
        ScreenAction.SaveToNotebook.id -> ScreenAction.SaveToNotebook
        ScreenAction.Pin.id -> ScreenAction.Pin
        ScreenAction.HideFromScreen.id -> ScreenAction.HideFromScreen
        ScreenAction.Setup.id -> ScreenAction.Setup
        ScreenAction.Retry.id -> ScreenAction.Retry
        ScreenAction.FewerLikeThis.id -> ScreenAction.FewerLikeThis
        else -> null
    }

    private fun ScreenItem.toJson() = JSONObject().put("id", id).put("sourceType", sourceType.name)
        .put("sourceId", sourceId).put("title", title).put("summary", summary).put("contentType", contentType.name)
        .put("updatedAt", updatedAt?.toString()).put("lastSuccessAt", lastSuccessAt?.toString())
        .put("expiresAt", expiresAt?.toString()).put("state", state.name).put("sensitivity", sensitivity.name)
        .put("attribution", attribution).put("availableActions", JSONArray().apply {
            availableActions.distinctBy { it.id }.forEach { put(it.id) }
        }).put("sourceLinks", JSONArray().apply {
            sourceLinks.take(8).forEach { link -> put(JSONObject().put("title", link.title.take(200)).put("url", link.url.take(2048))) }
        })
}

object ScreenWidgets {
    val types = setOf("clock", "weather", "calendar", "tasks", "notebook", "automation_result", "halo_activity", "interests", "research")
    fun starter(name: String): ScreenLayout {
        val types = when (name) {
            "Focus" -> listOf("clock", "tasks", "notebook")
            "Quiet" -> listOf("clock", "notebook")
            else -> listOf("clock", "calendar", "tasks", "automation_result", "notebook")
        }
        return ScreenLayout(name.lowercase(), name, types.map { ScreenCard(it, null, CardSize.STANDARD, it == "clock") }, 1)
    }
}
