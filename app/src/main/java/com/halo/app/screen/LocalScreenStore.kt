package com.halo.app.screen

import android.content.Context
import kotlinx.coroutines.flow.*
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant

/** Local presentation cache. Source ownership stays with Notebook, Calendar and routines. */
enum class ScreenApplyResult { APPLIED, CONFLICT, PERSISTENCE_UNKNOWN }

class LocalScreenStore(context: Context,
    private val commitConfiguration: (android.content.SharedPreferences.Editor)->Boolean = {it.commit()}) : ScreenItemStore {
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
    private val _presets = MutableStateFlow(readPresets())
    val presets = _presets.asStateFlow()
    fun setTopics(value: String) = synchronized(lock) {
        val cleaned = value.trim().take(1000)
        preferences.edit().putString("topics", cleaned).apply()
        _topics.value = cleaned
    }
    private var undoLayout: ScreenLayout? = ScreenLayoutCodec.decode(preferences.getString("undo_layout", null))
    fun hasUndo(): Boolean = synchronized(lock) { undoLayout != null }

    override suspend fun publish(item: ScreenItem) = synchronized(lock) {
        validateItem(item)
        val old = _items.value.firstOrNull { it.id == item.id }
        if (isOlderPublication(old, item)) return@synchronized
        persistItems((_items.value.filterNot { it.id == item.id } + item).takeLast(100))
    }

    /** Atomically writes a locally sourced item and the layout card that displays it. */
    suspend fun publishAndSaveLayout(item: ScreenItem, value: ScreenLayout, recordUndo: Boolean = false,
        expectedLayoutVersion: Int? = null): Boolean = synchronized(lock) {
        validateItem(item)
        val checkedLayout = checkedLayout(value)
        val old = _items.value.firstOrNull { it.id == item.id }
        if (expectedLayoutVersion != null && expectedLayoutVersion != (_layout.value?.version ?: 0) ||
            isOlderPublication(old, item)) return@synchronized false
        val updated = (_items.value.filterNot { it.id == item.id } + item).takeLast(100)
        val nextLayout = checkedLayout.copy(version = (_layout.value?.version ?: 0) + 1)
        if (recordUndo) undoLayout = _layout.value
        preferences.edit().putString("items", itemsJson(updated)).putString("layout", ScreenLayoutCodec.encode(nextLayout))
            .putString("undo_layout", undoLayout?.let(ScreenLayoutCodec::encode)).apply()
        _items.value = updated
        _layout.value = nextLayout
        true
    }

    /** Missing timestamps cannot replace newer evidence with an undated result. */
    private fun isOlderPublication(old: ScreenItem?, incoming: ScreenItem): Boolean {
        val previous = old?.updatedAt ?: return false
        return incoming.updatedAt == null || incoming.updatedAt < previous
    }

    override suspend fun markFailed(id: String, at: Instant) {
        synchronized(lock) {
            val old = _items.value.firstOrNull { it.id == id } ?: return
            if (old.updatedAt?.isAfter(at) == true) return
            // Preserve the latest successful payload while holding the same upsert lock.
            persistItems(_items.value.map { if (it.id == id) old.copy(state = ScreenItemState.STALE, updatedAt = at) else it })
        }
    }

    override fun observe(ids: Set<String>): Flow<List<ScreenItem>> = items.map { values -> values.filter { it.id in ids } }

    fun saveLayout(value: ScreenLayout, recordUndo: Boolean = true) = synchronized(lock) {
        val checked = checkedLayout(value)
        if (recordUndo) undoLayout = _layout.value
        persistLayout(checked.copy(version = (_layout.value?.version ?: 0) + 1))
    }

    fun undo() = synchronized(lock) { undoLayout?.let { previous ->
        undoLayout = _layout.value
        // Undo restores content, never an old revision: a pre-undo preview must stay stale.
        persistLayout(previous.copy(version = (_layout.value?.version ?: 0) + 1))
    } }

    /** Caller runs on IO. Failure is unknown: SharedPreferences may have changed memory before disk failed. */
    fun applyDraftChecked(base: ScreenLayout,draft: ScreenLayout): ScreenApplyResult = synchronized(lock) {
        if(_layout.value!=base) return@synchronized ScreenApplyResult.CONFLICT
        if(base==draft) return@synchronized if(confirmToolPersistence()) ScreenApplyResult.APPLIED else ScreenApplyResult.PERSISTENCE_UNKNOWN
        val checked=checkedLayout(draft).copy(version=base.version+1)
        undoLayout=base
        val persisted=runCatching {commitConfiguration(preferences.edit().putString("layout",ScreenLayoutCodec.encode(checked))
            .putString("undo_layout",ScreenLayoutCodec.encode(base)))}.getOrDefault(false)
        _layout.value=checked
        if(persisted) ScreenApplyResult.APPLIED else ScreenApplyResult.PERSISTENCE_UNKNOWN
    }
    /** Compatibility callers must not treat a persistence failure as success. */
    fun applyDraft(base: ScreenLayout,draft: ScreenLayout): Boolean = applyDraftChecked(base,draft)==ScreenApplyResult.APPLIED

    fun savePreset(name: String, value: ScreenLayout): Boolean = synchronized(lock) {
        val label = name.trim().filterNot(Char::isISOControl).take(64)
        if (label.isBlank() || value.cards.size > 24 || value.cards.any { it.widgetType !in ScreenWidgets.types }) return@synchronized false
        val existing = _presets.value.firstOrNull { it.name.equals(label, ignoreCase = true) }
        if (existing == null && _presets.value.size >= 12) return@synchronized false
        val preset = checkedLayout(value).copy(id = existing?.id ?: java.util.UUID.randomUUID().toString(), name = label)
        persistPresets(_presets.value.filterNot { it.id == preset.id } + preset)
        true
    }

    fun removePreset(id: String) = synchronized(lock) { persistPresets(_presets.value.filterNot { it.id == id }) }

    private fun persistPresets(values: List<ScreenLayout>) {
        preferences.edit().putString("presets", JSONArray().apply {
            values.forEach { put(JSONObject(ScreenLayoutCodec.encode(it))) }
        }.toString()).apply()
        _presets.value = values
    }

    private fun readPresets(): List<ScreenLayout> = runCatching {
        val array = JSONArray(preferences.getString("presets", "[]"))
        (0 until minOf(array.length(), 12)).mapNotNull { ScreenLayoutCodec.decode(array.optJSONObject(it)?.toString()) }
            .distinctBy { it.id }
    }.getOrDefault(emptyList())

    fun setPrivacy(enabled: Boolean) = synchronized(lock) { preferences.edit().putBoolean("hide_private", enabled).apply(); _hidePrivate.value = enabled }
    fun setKeepCharging(enabled: Boolean) = synchronized(lock) { preferences.edit().putBoolean("keep_charging", enabled).apply(); _keepCharging.value = enabled }
    fun setDimAtNight(enabled: Boolean) = synchronized(lock) { preferences.edit().putBoolean("dim_night", enabled).apply(); _dimAtNight.value = enabled }

    /** Same owner/lock as touch edits. The digest covers configuration, never source bodies.
     * No second layout engine, preview store, or permission authority is created here.
     */
    internal fun <T> withToolState(expectedRevision: String? = null, action: (ScreenConfigurationSnapshot) -> T): T = synchronized(lock) {
        val current = ScreenConfigurationSnapshot(_layout.value, _hidePrivate.value, _keepCharging.value,
            _dimAtNight.value, _topics.value, _presets.value, undoLayout != null,
            screenConfigurationDigest(buildString {
                append(_layout.value?.let(ScreenLayoutCodec::encode)); append('\u0000')
                append(undoLayout?.let(ScreenLayoutCodec::encode)); append('\u0000')
                append(_hidePrivate.value); append(_keepCharging.value); append(_dimAtNight.value)
                append('\u0000'); append(_topics.value); append('\u0000')
                _presets.value.forEach { append(ScreenLayoutCodec.encode(it)); append('\u0000') }
            }))
        check(expectedRevision == null || expectedRevision == current.revision) { "Screen configuration changed" }
        action(current)
    }

    /** A checked synchronous edit waits for this owner's preceding apply writes.
     * Tools call this on IO before claiming a durable configuration receipt.
     * A false result leaves completion unknown; do not replay the mutation.
     */
    internal fun confirmToolPersistence(): Boolean = synchronized(lock) {
        runCatching {commitConfiguration(preferences.edit().putString("layout",_layout.value?.let(ScreenLayoutCodec::encode))
            .putString("undo_layout",undoLayout?.let(ScreenLayoutCodec::encode))
            .putString("presets",JSONArray(_presets.value.map {JSONObject(ScreenLayoutCodec.encode(it))}).toString())
            .putBoolean("hide_private",_hidePrivate.value).putBoolean("keep_charging",_keepCharging.value)
            .putBoolean("dim_night",_dimAtNight.value).putString("topics",_topics.value))}.getOrDefault(false)
    }

    private fun persistLayout(value: ScreenLayout) {
        preferences.edit().putString("layout", ScreenLayoutCodec.encode(value))
            .putString("undo_layout", undoLayout?.let(ScreenLayoutCodec::encode)).apply()
        _layout.value = value
    }

    private fun checkedLayout(value: ScreenLayout): ScreenLayout {
        require(value.cards.size <= 24 && value.cards.all { it.widgetType in ScreenWidgets.types })
        return value.copy(cards = screenCardIdentities(value.cards).map { it.copy(placement = it.placement?.bounded(), typography = it.typography.bounded()) })
    }

    fun portableSnapshot(): String = synchronized(lock) {
        JSONObject().put("schema",1).put("layout",_layout.value?.let {JSONObject(ScreenLayoutCodec.encode(it))})
            .put("presets",JSONArray(_presets.value.map {JSONObject(ScreenLayoutCodec.encode(it))}))
            .put("items",JSONArray(itemsJson(_items.value))).put("topics",_topics.value).put("dimAtNight",_dimAtNight.value).toString()
    }
    fun validatePortable(raw: String) {
        val json=com.halo.app.everyday.collectors.SafeCollectorJson.objectValue(raw,8*1024*1024)
        require(json.getInt("schema")==1 && json.keys().asSequence().all {it in setOf("schema","layout","presets","items","topics","dimAtNight")})
        json.optJSONObject("layout")?.let {checkedLayout(requireNotNull(ScreenLayoutCodec.decode(it.toString())))}
        val presets=json.getJSONArray("presets");require(presets.length()<=12)
        (0 until presets.length()).forEach {checkedLayout(requireNotNull(ScreenLayoutCodec.decode(presets.getJSONObject(it).toString())))}
        val items=json.getJSONArray("items");require(items.length()<=100 && items.toString().length<=2000000)
        require(readItems(items.toString()).size==items.length())
        require(json.getString("topics").length<=1000);json.getBoolean("dimAtNight")
    }
    fun remapPortable(raw: String,mapping: Map<String,String>): String {
        validatePortable(raw)
        val root=JSONObject(raw)
        fun layout(value: JSONObject) {
            val cards=value.getJSONArray("cards")
            (0 until cards.length()).forEach {index ->cards.getJSONObject(index).optJSONObject("config")?.let {config ->
                listOf("recordId" to "ORGANIZER","sourceId" to "SOURCE","headlineSourceId" to "SOURCE").forEach { (field,kind) ->
                    config.optString(field).takeIf {it.isNotBlank()}?.let {id ->mapping["everyday:$kind:$id"]?.let {config.put(field,it)}}
                }
            }}
        }
        root.optJSONObject("layout")?.let(::layout)
        val presets=root.getJSONArray("presets");(0 until presets.length()).forEach {layout(presets.getJSONObject(it))}
        return root.toString().also(::validatePortable)
    }
    fun restorePortable(raw: String) = synchronized(lock) {
        validatePortable(raw)
        val json=JSONObject(raw)
        val layout=json.optJSONObject("layout")?.let {requireNotNull(ScreenLayoutCodec.decode(it.toString())).copy(version=(_layout.value?.version ?: 0)+1)}
        val presets=json.getJSONArray("presets").let {values ->(0 until values.length()).map {requireNotNull(ScreenLayoutCodec.decode(values.getJSONObject(it).toString()))}}
        val items=readItems(json.getJSONArray("items").toString())
        check(preferences.edit().putString("layout",layout?.let(ScreenLayoutCodec::encode)).putString("presets",JSONArray(presets.map {JSONObject(ScreenLayoutCodec.encode(it))}).toString())
            .putString("items",itemsJson(items)).putString("topics",json.getString("topics")).putBoolean("dim_night",json.getBoolean("dimAtNight"))
            .putBoolean("hide_private",true).putBoolean("keep_charging",false).remove("undo_layout").commit())
        _layout.value=layout;_presets.value=presets;_items.value=items;_topics.value=json.getString("topics");_dimAtNight.value=json.getBoolean("dimAtNight")
        _hidePrivate.value=true;_keepCharging.value=false;undoLayout=null
    }
    fun clearPortable()=restorePortable(JSONObject().put("schema",1).put("presets",JSONArray()).put("items",JSONArray()).put("topics","").put("dimAtNight",true).toString())

    private fun validateItem(item: ScreenItem) {
        require(item.id.length in 1..240 && item.title.length <= 240)
        require(item.sourceId == null || item.sourceId.length in 1..240)
        require(item.sourceRevision == null || item.sourceRevision.length in 1..240)
        require((item.summary?.length ?: 0) <= 16_000 && (item.attribution?.length ?: 0) <= 240)
        require(item.sourceLinks.size <= 8 && item.sourceLinks.all {
            it.title.length in 1..200 && it.url.length in 1..2048 && it.url.startsWith("https://")
        })
    }

    private fun persistItems(items: List<ScreenItem>) {
        preferences.edit().putString("items", itemsJson(items)).apply()
        _items.value = items
    }

    private fun itemsJson(items: List<ScreenItem>) = JSONArray().apply { items.forEach { put(it.toJson()) } }.toString()

    private fun readLayout(): ScreenLayout? = ScreenLayoutCodec.decode(preferences.getString("layout", null))

    private fun readItems(raw: String = preferences.getString("items", "[]").orEmpty()): List<ScreenItem> = runCatching {
        if (raw.length > 2_000_000) return@runCatching emptyList()
        val array = JSONArray(raw)
        (maxOf(0, array.length() - 100) until array.length()).mapNotNull { index ->
            runCatching {
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
                    }.orEmpty(), j.optString("sourceRevision").takeUnless { it.isBlank() || it == "null" },
                    decodeAppSummary(j.optJSONObject("appSummary"))).also(::validateItem)
            }.getOrNull()
        }.asReversed().distinctBy { it.id }.asReversed()
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
        }).put("sourceRevision", sourceRevision).put("appSummary", encodeAppSummary(appSummary)).put("sourceLinks", JSONArray().apply {
            sourceLinks.take(8).forEach { link -> put(JSONObject().put("title", link.title.take(200)).put("url", link.url.take(2048))) }
        })

    /** Optional metadata must never make a valid source item unreadable after an upgrade. */
    private fun decodeAppSummary(value: JSONObject?): ScreenAppSummary? = runCatching {
        if (value == null || value.opt("version") != 1 || value.optString("kind") != "ROUTINE_ACTIONS_FINISHED") return null
        fun count(key: String): Int? = when (val number = value.opt(key)) {
            is Int -> number
            is Long -> number.takeIf { it in 0..Int.MAX_VALUE.toLong() }?.toInt()
            else -> null
        }
        ScreenAppSummary.RoutineActionsFinished(count("completed") ?: return null, count("total") ?: return null)
            .takeIf { it.valid }
    }.getOrNull()

    private fun encodeAppSummary(value: ScreenAppSummary?): JSONObject? = when (value) {
        is ScreenAppSummary.RoutineActionsFinished -> value.takeIf { it.valid }?.let {
            JSONObject().put("version", 1).put("kind", "ROUTINE_ACTIONS_FINISHED")
                .put("completed", it.completed).put("total", it.total)
        }
        null -> null
    }
}

object ScreenWidgets {
    val types = setOf("clock", "weather", "calendar", "tasks", "notebook", "study", "automation_result", "follow_up", "halo_activity", "interests", "research", "device") + com.halo.app.everyday.presentation.EverydayCards.screenKinds.keys
    fun starter(name: String): ScreenLayout {
        val types = when (name) {
            "Focus" -> listOf("clock", "tasks", "notebook")
            "Quiet" -> listOf("clock", "notebook")
            else -> listOf("clock", "upcoming", "organizer_tasks", "briefing", "weather", "notebook")
        }
        return ScreenLayout(name.lowercase(), name, types.map { ScreenCard(it, null, CardSize.STANDARD, it == "clock") }, 1)
    }
}
