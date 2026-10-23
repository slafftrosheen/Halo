package com.halo.app.data.studio

import com.halo.app.data.workspace.CanvasImageRecipe
import com.halo.sdk.runtime.ink.InkCodec
import java.util.UUID

/** Source pixels remain in Workspace. A layer pins the exact version it was edited against. */
data class StudioSource(val objectId: String, val version: String)
enum class StudioLayerKind { IMAGE, INK, TEXT, RECTANGLE, ELLIPSE }
data class StudioRect(val x: Int = 0, val y: Int = 0, val width: Int = 1000, val height: Int = 1000) {
    fun requireValid() {
        require(x in -1000..2000 && y in -1000..2000 && width in 1..3000 && height in 1..3000)
    }
}
/** Values are thousandths of the untransformed project rectangle, never screen pixels. */
data class StudioLayer(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val kind: StudioLayerKind,
    val bounds: StudioRect = StudioRect(),
    val visible: Boolean = true,
    val locked: Boolean = false,
    val opacity: Int = 1000,
    val rotation: Int = 0,
    val color: Int = 0xff18203a.toInt(),
    val text: String = "",
    val fontSize: Int = 80,
    val strokeWidth: Int = 5,
    val ink: String = "",
    val source: StudioSource? = null,
    val mask: StudioRect? = null,
    val brightness: Int = 0,
    val contrast: Int = 1000,
    val saturation: Int = 1000,
) {
    fun requireValid() {
        require(id.matches(Regex("[A-Za-z0-9_-]{1,100}")))
        require(name.isNotBlank() && name.length <= 120)
        bounds.requireValid(); mask?.requireValid()
        require(opacity in 0..1000 && rotation in -360..360)
        require(fontSize in 1..1000 && strokeWidth in 1..100)
        require(text.length <= 2000 && brightness in -1000..1000 && contrast in 0..3000 && saturation in 0..3000)
        if (kind == StudioLayerKind.IMAGE) {
            requireNotNull(source).let { require(com.halo.app.data.workspace.WorkspaceActionIds.isValid(it.objectId) && it.version.length in 1..200) }
        } else require(source == null)
        if (kind == StudioLayerKind.INK) require(InkCodec.decode(ink) != null) else require(ink.isEmpty())
        if (kind != StudioLayerKind.TEXT) require(text.isEmpty())
    }
}

data class StudioDocument(
    val width: Int = 1024,
    val height: Int = 1024,
    val background: Int = 0,
    val recipe: CanvasImageRecipe = CanvasImageRecipe(),
    val layers: List<StudioLayer> = emptyList(),
    val selectedLayerId: String? = null,
) {
    fun requireValid() {
        require(width in 1..4096 && height in 1..4096 && width.toLong() * height <= 12_000_000)
        require(layers.size <= MAX_LAYERS && layers.map { it.id }.distinct().size == layers.size)
        require(selectedLayerId == null || layers.any { it.id == selectedLayerId })
        layers.forEach(StudioLayer::requireValid)
        val points = layers.filter { it.kind == StudioLayerKind.INK }.sumOf { layer ->
            requireNotNull(InkCodec.decode(layer.ink)).strokes.sumOf { it.points.size }
        }
        require(points <= MAX_DOCUMENT_POINTS)
    }
    companion object {
        const val MAX_LAYERS = 32
        const val MAX_DOCUMENT_POINTS = 65_536
        const val MAX_HISTORY = 16
        const val MAX_ENCODED_CHARS = 2_000_000
    }
}

/** One validated command language for touch, text and voice. No command sends a network request. */
sealed interface StudioCommand {
    data class Batch(val commands: List<StudioCommand>) : StudioCommand
    data class Add(val layer: StudioLayer) : StudioCommand
    data class Replace(val layer: StudioLayer) : StudioCommand
    data class Remove(val layerId: String) : StudioCommand
    data class Move(val layerId: String, val index: Int) : StudioCommand
    data class Select(val layerId: String?) : StudioCommand
    data class Transform(val recipe: CanvasImageRecipe) : StudioCommand
    data class Resize(val width: Int, val height: Int) : StudioCommand
    data class Background(val color: Int) : StudioCommand
}

fun StudioDocument.apply(command: StudioCommand): StudioDocument {
    requireValid()
    fun editable(id: String): StudioLayer = requireNotNull(layers.find { it.id == id }).also { require(!it.locked) }
    val result = when (command) {
        is StudioCommand.Batch -> {
            require(command.commands.size in 1..64 && command.commands.none { it is StudioCommand.Batch })
            command.commands.fold(this) { document, item -> document.apply(item) }
        }
        is StudioCommand.Add -> {
            require(layers.none { it.id == command.layer.id })
            copy(layers = layers + command.layer, selectedLayerId = command.layer.id)
        }
        is StudioCommand.Replace -> {
            val existing = requireNotNull(layers.find { it.id == command.layer.id })
            // Unlocking is permitted; locked layers cannot otherwise be edited through another surface.
            require(!existing.locked || command.layer == existing.copy(locked = false))
            copy(layers = layers.map { if (it.id == command.layer.id) command.layer else it })
        }
        is StudioCommand.Remove -> {
            editable(command.layerId)
            val remaining = layers.filterNot { it.id == command.layerId }
            copy(layers = remaining, selectedLayerId = selectedLayerId.takeUnless { it == command.layerId })
        }
        is StudioCommand.Move -> {
            val layer = editable(command.layerId)
            require(command.index in layers.indices)
            copy(layers = layers.toMutableList().apply { remove(layer); add(command.index, layer) })
        }
        is StudioCommand.Select -> copy(selectedLayerId = command.layerId)
        is StudioCommand.Transform -> copy(recipe = command.recipe)
        is StudioCommand.Resize -> copy(width = command.width, height = command.height)
        is StudioCommand.Background -> copy(background = command.color)
    }
    return result.also(StudioDocument::requireValid)
}
