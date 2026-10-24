package com.halo.app.di.tools

import com.halo.app.screen.*
import com.halo.runtime.cognition.tools.*
import com.halo.runtime.cognition.ui.UiTargetPreview
import com.halo.sdk.provider.ToolCall
import com.halo.sdk.provider.ToolDefinition
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/** Data-only adapter to Screen's existing native layout owner. Never reads source bodies. */
class ScreenToolExecutor(private val store: LocalScreenStore, private val allowed: () -> Boolean) : ToolExecutor, ToolPreflightProvider {
    private fun text(max: Int, min: Int = 0) = mapOf("type" to "string", "minLength" to min, "maxLength" to max)
    private fun integer(min: Int, max: Int) = mapOf("type" to "integer", "minimum" to min, "maximum" to max)
    private fun choice(values: List<String>) = mapOf("type" to "string", "enum" to values)
    private fun schema(properties: Map<String, Any>, required: List<String> = properties.keys.toList()) =
        mapOf("type" to "object", "properties" to properties, "required" to required, "additionalProperties" to false)
    private val boolean = mapOf("type" to "boolean")
    private val revision = mapOf("type" to "string", "pattern" to "^[a-f0-9]{64}$")
    private val operations = listOf("add", "remove", "reorder", "move", "resize", "resize_grid", "flags", "typography", "appearance", "starter", "preset")
    private val changeFields: Map<String, Any> = mapOf(
        "operation" to choice(operations), "widgetType" to choice(ScreenWidgets.types.sorted()),
        "index" to integer(0, 23), "toIndex" to integer(0, 23), "columns" to integer(1, 4),
        "dx" to integer(-1, 1), "dy" to integer(-1, 1), "dw" to integer(-1, 1), "dh" to integer(-1, 1),
        "size" to choice(CardSize.entries.map { it.name }), "pinned" to boolean, "archived" to boolean,
        "titlePercent" to integer(100, 150), "valuePercent" to integer(100, 150), "clockPercent" to integer(100, 200),
        "font" to choice(ScreenFont.entries.map { it.name }), "background" to choice(ScreenBackground.entries.map { it.name }),
        "translucent" to boolean, "name" to choice(listOf("Morning", "Focus", "Quiet")), "presetId" to text(100, 1))
    private val changes = mapOf("type" to "array", "minItems" to 1, "maxItems" to 24,
        "items" to schema(changeFields, listOf("operation")))
    private fun definition(name: String, description: String, properties: Map<String, Any>, required: List<String> = properties.keys.toList()) =
        ToolDefinition(name, name, description, schema(properties, required))

    override fun definitions() = listOf(
        definition("halo_screen_state", "Read bounded local Screen layout/settings and saved preset metadata. Returns current revision and native widget whitelist. Never returns item bodies, source IDs, citations, arbitrary card config, or grants source/network permissions. Optional widgetType filters cards only.",
            mapOf("widgetType" to choice(ScreenWidgets.types.sorted())), emptyList()),
        definition("halo_screen_preview", "Preview 1–24 native Screen edits without saving. Exact state revision required. Edits use zero-based card indices from that revision; no HTML/JS, generated news, source reads or source changes. Returns a previewToken bound to the exact resulting layout. Starter/preset replaces the layout only in preview. Grid moves/resizes use the same native packing as touch/keyboard.",
            mapOf("revision" to revision, "changes" to changes)),
        definition("halo_screen_apply", "Apply the exact Screen preview using its revision, identical changes and previewToken. Rejects stale/tampered previews. No source content, OS permission, microphone, cloud or display-mode change. Local configuration acceptance does not prove a screen rendered. Never replay uncertain mutations.",
            mapOf("revision" to revision, "changes" to changes, "previewToken" to revision)),
        definition("halo_screen_settings", "Change existing Screen settings at an exact revision: hidePrivate, keepCharging, dimAtNight, topics. Disabling hidePrivate can show personal/private cards to people nearby and requires the existing runtime's durable settings approval and scoped interaction. These presentation preferences never grant OS, source, provider or Full Power permissions. Topics do not fetch or fabricate articles.",
            mapOf("revision" to revision, "settings" to schema(mapOf("hidePrivate" to boolean,
                "keepCharging" to boolean, "dimAtNight" to boolean, "topics" to text(1000)), emptyList()))),
        definition("halo_screen_undo", "Use Screen's existing local layout Undo at the exact configuration revision. Does not undo settings or source content. Rejects absent history; never replay uncertain Undo.", mapOf("revision" to revision)),
        definition("halo_screen_save_preset", "Save the current saved Screen layout through its existing preset owner. Exact configuration revision and nonblank name required. Same-name replacement follows the manual UI's owner behavior; no source copies or content read.",
            mapOf("revision" to revision, "name" to text(64, 1))),
        definition("halo_screen_remove_preset", "Remove only one observed saved Screen preset at the exact configuration revision. Keeps current layout, notes, routines and other source content. Never replay uncertain removal.",
            mapOf("revision" to revision, "presetId" to text(100, 1))),
    )

    /** Read-only copy for the existing durable approval sheet; no content or authority added. */
    override suspend fun preflight(toolCall: ToolCall): ToolPreflightResult {
        val weakening = toolCall.functionName == "halo_screen_settings" &&
            (toolCall.arguments["settings"] as? Map<*, *>)?.get("hidePrivate") == false
        return ToolPreflightResult.Validated(toolCall, preview = if (weakening) UiTargetPreview(
            appLabel = "halo Screen", packageName = null, action = "Show private details in full screen",
            targetLabel = "People nearby may see personal and private cards", role = null, region = null,
        ) else null)
    }

    override suspend fun execute(toolCall: ToolCall): ToolExecutionResult = execute(toolCall, ToolExecutionContext())

    override suspend fun execute(toolCall: ToolCall, context: ToolExecutionContext): ToolExecutionResult =
        withContext(Dispatchers.IO) { executeGuarded(toolCall, context) }

    private fun executeGuarded(toolCall: ToolCall, context: ToolExecutionContext): ToolExecutionResult {
        fun reject(kind: ToolRejectionKind = ToolRejectionKind.INVALID_ARGUMENTS) = ToolExecutionResult.Rejected(
            toolCall.id, toolCall.functionName, "Screen request unavailable or changed. Read the current Screen configuration and review the exact change.", kind)
        fun success(value: JSONObject) = ToolExecutionResult.Success(toolCall.id, toolCall.functionName, value.toString())
        fun persisted(expected: String): ToolExecutionResult {
            if (!store.confirmToolPersistence()) return ToolExecutionResult.Failed(toolCall.id, toolCall.functionName,
                "Screen changed in memory but its disk save was not confirmed. Read current configuration; do not replay this change.",
                completionUnknown = true, failureKind = ToolFailureKind.RESOURCE_UNAVAILABLE)
            return store.withToolState { success(summary(it).put("applied", true).put("persisted", true).put("changed", it.revision != expected)) }
        }
        if (definitions().none { it.name == toolCall.functionName }) return reject(ToolRejectionKind.TOOL_UNKNOWN)
        if (!allowed()) return reject(ToolRejectionKind.PERMISSION_REQUIRED)
        val args = toolCall.arguments
        try {
            if (toolCall.functionName == "halo_screen_state") {
                exact(args, emptySet(), setOf("widgetType"))
                val filter = if ("widgetType" in args) string(args, "widgetType").also { require(it in ScreenWidgets.types) } else null
                return store.withToolState { success(summary(it, filter)) }
            }
            val required = when (toolCall.functionName) {
                "halo_screen_preview" -> setOf("revision", "changes")
                "halo_screen_apply" -> setOf("revision", "changes", "previewToken")
                "halo_screen_settings" -> setOf("revision", "settings")
                "halo_screen_save_preset" -> setOf("revision", "name")
                "halo_screen_remove_preset" -> setOf("revision", "presetId")
                else -> setOf("revision")
            }
            exact(args, required)
            val expected = string(args, "revision").also { require(it.matches(Regex("[a-f0-9]{64}"))) }
            return store.withToolState(expected) { state ->
                if (!allowed()) return@withToolState reject(ToolRejectionKind.PERMISSION_REQUIRED)
                when (toolCall.functionName) {
                    "halo_screen_preview", "halo_screen_apply" -> {
                        val changes = args["changes"] as? List<*> ?: error("Expected changes")
                        require(changes.size in 1..24)
                        val draft = changes.fold(state.layout) { layout, value -> edit(layout, fields(value), state.presets) }
                            ?: error("No layout")
                        val token = screenConfigurationDigest(expected + "\u0000" + ScreenLayoutCodec.encode(draft))
                        if (toolCall.functionName == "halo_screen_preview") {
                            success(JSONObject().put("revision", expected).put("previewToken", token)
                                .put("preview", layoutSummary(draft, null)).put("applied", false))
                        } else {
                            require(string(args, "previewToken") == token)
                            if (state.layout == null) store.saveLayout(draft)
                            else check(store.applyDraft(state.layout, draft))
                            persisted(expected)
                        }
                    }
                    "halo_screen_settings" -> {
                        val fields = fields(args["settings"])
                        exact(fields, emptySet(), setOf("hidePrivate", "keepCharging", "dimAtNight", "topics"))
                        require(fields.isNotEmpty())
                        // Validate every argument before any owner mutation.
                        fields.forEach { (key, _) -> if (key == "topics") boundedText(fields, key, 1000) else bool(fields, key) }
                        // ALWAYS/SENSITIVE approval deliberately does not set directUserAuthority.
                        // The registered durable authorizer owns approval; an interaction ID
                        // supplied by that runtime scopes execution and cannot be a model argument.
                        if (state.hidePrivate && fields["hidePrivate"] == false && context.interactionId.isNullOrBlank())
                            return@withToolState reject(ToolRejectionKind.PERMISSION_REQUIRED)
                        fields["hidePrivate"]?.let { store.setPrivacy(it as Boolean) }
                        fields["keepCharging"]?.let { store.setKeepCharging(it as Boolean) }
                        fields["dimAtNight"]?.let { store.setDimAtNight(it as Boolean) }
                        fields["topics"]?.let { store.setTopics(it as String) }
                        persisted(expected)
                    }
                    "halo_screen_undo" -> {
                        check(state.canUndo)
                        store.undo()
                        persisted(expected)
                    }
                    "halo_screen_save_preset" -> {
                        val name = boundedText(args, "name", 64).also { require(it.isNotBlank()) }
                        check(store.savePreset(name, checkNotNull(state.layout)))
                        persisted(expected)
                    }
                    "halo_screen_remove_preset" -> {
                        val id = boundedText(args, "presetId", 100).also { require(it.isNotBlank()) }
                        check(state.presets.any { it.id == id })
                        store.removePreset(id)
                        persisted(expected)
                    }
                    else -> reject(ToolRejectionKind.TOOL_UNKNOWN)
                }
            }
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: IllegalArgumentException) { return reject() }
        catch (_: IllegalStateException) { return reject(ToolRejectionKind.RESOURCE_UNAVAILABLE) }
        catch (_: Exception) { return ToolExecutionResult.Failed(toolCall.id, toolCall.functionName,
            "Screen could not complete the local change. Read current configuration before retrying.", completionUnknown = true) }
    }

    private fun edit(base: ScreenLayout?, values: Map<String, Any?>, presets: List<ScreenLayout>): ScreenLayout {
        val operation = string(values, "operation")
        if (operation == "starter") {
            exact(values, setOf("operation", "name"))
            val name = string(values, "name").also { require(it in setOf("Morning", "Focus", "Quiet")) }
            return ScreenWidgets.starter(name)
        }
        if (operation == "preset") {
            exact(values, setOf("operation", "presetId"))
            return checkNotNull(presets.singleOrNull { it.id == boundedText(values, "presetId", 100) })
        }
        val layout = checkNotNull(base)
        if (operation == "add") {
            exact(values, setOf("operation", "widgetType"))
            return checkNotNull(ScreenLayoutRequests.apply(layout, LayoutChange.Add(string(values, "widgetType"), emptyMap())))
        }
        if (operation == "appearance") {
            exact(values, setOf("operation"), setOf("font", "background", "translucent")); require(values.size > 1)
            return layout.copy(appearance = ScreenAppearance(
                if ("font" in values) ScreenFont.valueOf(string(values, "font")) else layout.appearance.font,
                if ("background" in values) ScreenBackground.valueOf(string(values, "background")) else layout.appearance.background,
                if ("translucent" in values) bool(values, "translucent") else layout.appearance.translucent))
        }
        val index = number(values, "index", 0, 23).also { require(it in layout.cards.indices) }
        val card = layout.cards[index]
        fun replace(value: ScreenCard) = layout.copy(cards = layout.cards.mapIndexed { i, c -> if (i == index) value else c })
        return when (operation) {
            "remove" -> { exact(values, setOf("operation", "index")); layout.copy(cards = layout.cards.filterIndexed { i, _ -> i != index }) }
            "reorder" -> {
                exact(values, setOf("operation", "index", "toIndex"))
                val target = number(values, "toIndex", 0, 23).also { require(it in layout.cards.indices) }
                layout.copy(cards = ScreenGrid.reorder(layout.cards, index, target))
            }
            "move", "resize_grid" -> {
                val a = if (operation == "move") "dx" else "dw"; val b = if (operation == "move") "dy" else "dh"
                exact(values, setOf("operation", "index", "columns", a, b))
                val columns = number(values, "columns", 1, 4); val first = number(values, a, -1, 1); val second = number(values, b, -1, 1)
                require(first != 0 || second != 0)
                layout.copy(cards = if (operation == "move") ScreenGrid.move(layout.cards, index, columns, first, second)
                    else ScreenGrid.resize(layout.cards, index, columns, first, second))
            }
            "resize" -> {
                exact(values, setOf("operation", "index", "size"))
                val size = CardSize.valueOf(string(values, "size"))
                replace(card.copy(size = size, placement = card.placement?.let { it.copy(
                    width = if (size == CardSize.COMPACT) 6 else 12, height = if (size == CardSize.EXPANDED) 2 else 1).bounded() }))
            }
            "flags" -> {
                exact(values, setOf("operation", "index"), setOf("pinned", "archived")); require(values.size > 2)
                replace(card.copy(pinned = if ("pinned" in values) bool(values, "pinned") else card.pinned,
                    archived = if ("archived" in values) bool(values, "archived") else card.archived))
            }
            "typography" -> {
                exact(values, setOf("operation", "index"), setOf("titlePercent", "valuePercent", "clockPercent")); require(values.size > 2)
                replace(card.copy(typography = ScreenCardTypography(
                    if ("titlePercent" in values) number(values, "titlePercent", 100, 150) else card.typography.titlePercent,
                    if ("valuePercent" in values) number(values, "valuePercent", 100, 150) else card.typography.valuePercent,
                    if ("clockPercent" in values) number(values, "clockPercent", 100, 200) else card.typography.clockPercent)))
            }
            else -> throw IllegalArgumentException("Unsupported native edit")
        }
    }

    private fun summary(state: ScreenConfigurationSnapshot, filter: String? = null) = JSONObject()
        .put("revision", state.revision).put("layout", state.layout?.let { layoutSummary(it, filter) } ?: JSONObject.NULL)
        .put("settings", JSONObject().put("hidePrivate", state.hidePrivate).put("keepCharging", state.keepCharging)
            .put("dimAtNight", state.dimAtNight).put("topics", state.topics.take(1000)))
        .put("canUndo", state.canUndo).put("nativeWidgets", JSONArray(ScreenWidgets.types.sorted()))
        .put("presets", JSONArray().apply { state.presets.take(12).forEach { preset -> put(JSONObject()
            .put("presetId", preset.id.take(100)).put("name", preset.name.take(64)).put("cardCount", preset.cards.size)) } })

    private fun layoutSummary(layout: ScreenLayout, filter: String?) = JSONObject().put("name", layout.name.take(64))
        .put("version", layout.version).put("appearance", JSONObject().put("font", layout.appearance.font.name)
            .put("background", layout.appearance.background.name).put("translucent", layout.appearance.translucent))
        .put("cards", JSONArray().apply { layout.cards.take(24).forEachIndexed { index, card ->
            if (filter == null || card.widgetType == filter) put(JSONObject().put("index", index).put("widgetType", card.widgetType)
                .put("size", card.size.name).put("pinned", card.pinned).put("archived", card.archived)
                .put("hasSource", card.itemId != null).put("hasConfiguration", card.config.isNotEmpty())
                .put("typography", JSONObject().put("titlePercent", card.typography.titlePercent)
                    .put("valuePercent", card.typography.valuePercent).put("clockPercent", card.typography.clockPercent))
                .put("placement", card.placement?.let { JSONObject().put("column", it.column).put("row", it.row)
                    .put("width", it.width).put("height", it.height) } ?: JSONObject.NULL))
        } })

    private fun exact(values: Map<String, Any?>, required: Set<String>, optional: Set<String> = emptySet()) {
        require(values.keys.containsAll(required) && values.keys.all { it in required || it in optional })
    }
    private fun fields(value: Any?): Map<String, Any?> {
        require(value is Map<*, *> && value.keys.all { it is String })
        return value.entries.associate { it.key as String to it.value }
    }
    private fun string(values: Map<String, Any?>, key: String) = values[key] as? String ?: throw IllegalArgumentException("Expected text")
    private fun boundedText(values: Map<String, Any?>, key: String, max: Int) = string(values, key).also {
        require(it.length <= max && it.none(Char::isISOControl))
    }
    private fun bool(values: Map<String, Any?>, key: String) = values[key] as? Boolean ?: throw IllegalArgumentException("Expected boolean")
    private fun number(values: Map<String, Any?>, key: String, min: Int, max: Int): Int {
        val number = values[key] as? Number ?: throw IllegalArgumentException("Expected integer")
        val value = number.toDouble(); require(value.isFinite() && value % 1.0 == 0.0 && value >= min && value <= max)
        return value.toInt()
    }
}
