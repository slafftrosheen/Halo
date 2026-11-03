package com.halo.app.data.studio

import java.util.UUID

internal fun StudioDocument.duplicateLayer(id: String): StudioDocument {
    val original = requireNotNull(layers.find { it.id == id })
    val source = listOf(original) + descendants(id)
    require(source.all { isEditable(it) })
    val ids = source.associate { it.id to UUID.randomUUID().toString() }
    val copies = source.map { layer -> layer.copy(id = ids.getValue(layer.id),
        parentId = ids[layer.parentId] ?: layer.parentId) }
    return copy(layers = layers + copies, selectedLayerId = ids.getValue(id))
}

internal fun StudioDocument.groupLayers(command: StudioCommand.Group): StudioDocument {
    require(command.layerIds.isNotEmpty() && command.layerIds.distinct().size == command.layerIds.size)
    val selected = command.layerIds.map { id -> requireNotNull(layers.find { it.id == id }).also { require(isEditable(it)) } }
    require(selected.map { it.parentId }.distinct().size == 1)
    require(layers.none { it.id == command.groupId })
    val left = selected.minOf { it.bounds.x }; val top = selected.minOf { it.bounds.y }
    val right = selected.maxOf { it.bounds.x + it.bounds.width }; val bottom = selected.maxOf { it.bounds.y + it.bounds.height }
    val group = StudioLayer(id = command.groupId, name = command.name, kind = StudioLayerKind.GROUP,
        parentId = selected.first().parentId, bounds = StudioRect(left, top, right-left, bottom-top))
    // Preserve original order inside the new group; the group's z position is the highest selected sibling.
    val insertion = layers.indexOfLast { it.id in command.layerIds }
    val result = layers.map { if (it.id in command.layerIds) it.copy(parentId = group.id) else it }.toMutableList()
    result.add(insertion + 1, group)
    return copy(layers = result, selectedLayerId = group.id)
}

internal fun StudioDocument.ungroupLayer(id: String): StudioDocument {
    val group = requireNotNull(layers.find { it.id == id && it.kind == StudioLayerKind.GROUP })
    require(isEditable(group) && descendants(id).all { isEditable(it) })
    // Group-level styling cannot be silently discarded by ungrouping.
    require(group.visible && group.opacity == 1000 && group.blend == StudioBlend.NORMAL && group.effects.isEmpty() && group.mask == null) {
        "Reset group styling before ungrouping"
    }
    return copy(layers = layers.filterNot { it.id == id }.map { if (it.parentId == id) it.copy(parentId = group.parentId) else it },
        selectedLayerId = selectedLayerId.takeUnless { it == id })
}

internal fun StudioDocument.layoutLayers(command: StudioCommand.Layout): StudioDocument {
    require(command.layerIds.size in 2..StudioDocument.MAX_LAYERS && command.layerIds.distinct().size == command.layerIds.size)
    val selected = command.layerIds.map { id -> requireNotNull(layers.find { it.id == id }).also { require(isEditable(it)) } }
    require(selected.map { it.parentId }.distinct().size == 1)
    val left = selected.minOf { it.bounds.x }; val top = selected.minOf { it.bounds.y }
    val right = selected.maxOf { it.bounds.x + it.bounds.width }; val bottom = selected.maxOf { it.bounds.y + it.bounds.height }
    val horizontal = command.operation == StudioLayout.DISTRIBUTE_HORIZONTAL
    val sorted = selected.sortedBy { if (horizontal) it.bounds.x else it.bounds.y }
    if (command.operation in setOf(StudioLayout.DISTRIBUTE_HORIZONTAL, StudioLayout.DISTRIBUTE_VERTICAL)) require(selected.size >= 3)
    val span = if (horizontal) right-left else bottom-top
    val used = sorted.sumOf { if (horizontal) it.bounds.width else it.bounds.height }
    val gap = (span-used).toDouble() / (selected.size-1)
    var cursor = (if (horizontal) left else top).toDouble()
    val targets = sorted.associate { layer ->
        val b = layer.bounds
        val next = when (command.operation) {
            StudioLayout.ALIGN_LEFT -> b.copy(x = left)
            StudioLayout.ALIGN_CENTER -> b.copy(x = (left+right-b.width)/2)
            StudioLayout.ALIGN_RIGHT -> b.copy(x = right-b.width)
            StudioLayout.ALIGN_TOP -> b.copy(y = top)
            StudioLayout.ALIGN_MIDDLE -> b.copy(y = (top+bottom-b.height)/2)
            StudioLayout.ALIGN_BOTTOM -> b.copy(y = bottom-b.height)
            StudioLayout.DISTRIBUTE_HORIZONTAL -> b.copy(x = cursor.toInt()).also { cursor += b.width+gap }
            StudioLayout.DISTRIBUTE_VERTICAL -> b.copy(y = cursor.toInt()).also { cursor += b.height+gap }
        }
        layer.id to layer.copy(bounds = next)
    }
    return selected.fold(this) { doc, layer -> doc.apply(StudioCommand.Replace(targets.getValue(layer.id))) }
}
