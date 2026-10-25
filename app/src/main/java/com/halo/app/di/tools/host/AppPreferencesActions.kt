package com.halo.app.di.tools.host

import android.content.Context
import com.halo.app.language.*
import com.halo.app.ui.shell.HaloScreenCatalog
import com.halo.app.ui.theme.*
import com.halo.runtime.cognition.agent.ActionTruth
import com.halo.runtime.cognition.tools.*
import com.halo.sdk.provider.ToolCall
import kotlinx.coroutines.CancellationException

/** UI preference owners remain authoritative; this adapter never changes processing/sharing policy. */
internal class AppPreferencesActions(
    private val context: Context, private val appearance: AppearancePreferences, private val preferences: AssistantPreferences,
    private val palettes: PalettePreferences?, private val visual: VisualPreferences?, private val languages: AppLanguagePreferences?,
    private val languageReadback: (() -> String?)?,
) {
    companion object {
        val fields: Map<String, Any> get() = mapOf(
            "mode" to mapOf("type" to "string", "enum" to listOf("system", "light", "dark")),
            "fontScale" to mapOf("type" to "number", "minimum" to 0, "maximum" to 2.0),
            "startScreen" to mapOf("type" to "string", "enum" to HaloScreenCatalog.screens.keys.toList()),
            "paletteTheme" to mapOf("type" to "string", "enum" to listOf("light", "dark")),
            "accent" to mapOf("type" to "string", "enum" to HaloAccent.entries.map { it.name }),
            "tone" to mapOf("type" to "string", "enum" to HaloTone.entries.map { it.name }),
            "higherContrast" to mapOf("type" to "boolean"), "lessMotion" to mapOf("type" to "boolean"),
            "guideLight" to mapOf("type" to "boolean"),
            "language" to mapOf("type" to "string", "enum" to AppLanguage.entries.map { it.tag }),
        )
    }
    private suspend fun readback(): String {
        appearance.awaitLoaded()
        val value = preferences.get()
        palettes?.awaitReady()
        val palette = palettes?.state?.value
        val options = visual?.persistedSnapshot()
        return "mode=${appearance.mode.value.name.lowercase()}; fontScale=${value.fontScale}; startScreen=${value.startScreen}; " +
            "lightAccent=${palette?.light?.accent}; lightTone=${palette?.light?.tone}; darkAccent=${palette?.dark?.accent}; darkTone=${palette?.dark?.tone}; " +
            "higherContrast=${options?.higherContrast}; lessMotion=${options?.lessMotion}; guideLight=${options?.guideLight}; language=${languages?.language?.value?.tag}; scope=Halo_only"
    }
    suspend fun execute(call: ToolCall): ToolExecutionResult {
        fun reject(reason: String, kind: ToolRejectionKind = ToolRejectionKind.INVALID_ARGUMENTS) =
            ToolExecutionResult.Rejected(call.id, call.functionName, reason, kind)
        fun success(value: String, truth: ActionTruth) = ToolExecutionResult.Success(call.id, call.functionName,
            ToolResultEnvelope.success(actionTruth = truth, details = value), truth)
        val args = call.arguments
        if (call.functionName == "halo_get_preferences") {
            if (args.isNotEmpty()) return reject("Read preferences accepts no arguments")
            return success(readback(), ActionTruth.ACKNOWLEDGED)
        }
        if (args.isEmpty() || args.keys.any { it !in fields }) return reject("Supply only supported app preference fields")
        val mode = (args["mode"] as? String)?.let { raw -> AppearanceMode.entries.firstOrNull { it.name.equals(raw, true) } }
        if ("mode" in args && mode == null) return reject("Invalid appearance mode")
        val scale = (args["fontScale"] as? Number)?.toFloat()
        if ("fontScale" in args && (scale == null || !scale.isFinite() || scale != 0f && scale !in 0.45f..2f)) return reject("fontScale must be 0 (system) or 0.45..2.0")
        val screen = args["startScreen"] as? String
        if ("startScreen" in args && screen !in HaloScreenCatalog.screens) return reject("Invalid startScreen")
        val paletteChange = "accent" in args || "tone" in args
        val theme = args["paletteTheme"] as? String
        if ((paletteChange && theme !in setOf("light", "dark")) || ("paletteTheme" in args && (!paletteChange || theme !in setOf("light", "dark"))))
            return reject("Choose the exact light or dark palette and at least one accent/tone")
        val accent = (args["accent"] as? String)?.let { raw -> HaloAccent.entries.firstOrNull { it.name == raw } }
        val tone = (args["tone"] as? String)?.let { raw -> HaloTone.entries.firstOrNull { it.name == raw } }
        if ("accent" in args && accent == null || "tone" in args && tone == null) return reject("Invalid accent or tone")
        val visualChange = setOf("higherContrast", "lessMotion", "guideLight").any { it in args }
        if (setOf("higherContrast", "lessMotion", "guideLight").any { it in args && args[it] !is Boolean }) return reject("Visual options must be booleans")
        val language = (args["language"] as? String)?.let { raw -> AppLanguage.entries.firstOrNull { it.tag == raw } }
        if ("language" in args && language == null) return reject("Unsupported app language")
        if (paletteChange && palettes == null || visualChange && visual == null || language != null && (languages == null || languageReadback == null))
            return reject("The requested preference owner is unavailable", ToolRejectionKind.CAPABILITY_UNAVAILABLE)
        try {
            preferences.update(scale, screen)
            mode?.let { appearance.setModePersisted(it) }
            if (paletteChange) {
                val owner = requireNotNull(palettes); owner.awaitReady()
                val dark = theme == "dark"
                val old = if (dark) owner.state.value.dark else owner.state.value.light
                val next = old.copy(accent = accent ?: old.accent, tone = tone ?: old.tone)
                owner.select(dark, next); owner.awaitReady()
                // Palette UI publishes optimistically; confirm against its actual persisted namespace.
                val disk = context.getSharedPreferences("halo_palettes", Context.MODE_PRIVATE)
                val prefix = if (dark) "dark" else "light"
                check(disk.getString("$prefix.accent", null) == next.accent.name && disk.getString("$prefix.tone", null) == next.tone.name) { "Palette persistence is unconfirmed" }
            }
            if (visualChange) requireNotNull(visual).update { old -> old.copy(
                higherContrast = args["higherContrast"] as? Boolean ?: old.higherContrast,
                lessMotion = args["lessMotion"] as? Boolean ?: old.lessMotion,
                guideLight = args["guideLight"] as? Boolean ?: old.guideLight) }
            language?.let { requireNotNull(languages).select(it)
                check(requireNotNull(languageReadback).invoke() == it.tag) { "Language persistence is unconfirmed" }
                com.halo.app.nativeactions.HaloNativeWidget.refreshAsync(context)
            }
            return success(readback(), ActionTruth.CONFIRMED)
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { return ToolExecutionResult.Failed(call.id, call.functionName,
            "Some preferences may have saved. Read current preferences before requesting another change; no automatic retry.", completionUnknown = true, failureKind = ToolFailureKind.UNKNOWN) }
    }
}
