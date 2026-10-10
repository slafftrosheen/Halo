package com.halo.app.data.notebook

import java.security.MessageDigest
import java.util.UUID
import org.json.JSONObject

data class CanvasOrganizationGroup(val title: String, val memberIds: List<String>)
data class CanvasOrganizationProposal(
    val id: String = UUID.randomUUID().toString(),
    val canvasId: String,
    val sourceFingerprints: Map<String, String>,
    val sourceObjectVersions: Map<String, String>,
    val groups: List<CanvasOrganizationGroup>,
    val nextSteps: List<String> = emptyList(),
    val suggestedByAi: Boolean = false,
    val createLinkedNote: Boolean = true,
    val sourceEdgesFingerprint: String? = null,
) {
    fun validate() {
        require(id.matches(Regex("[a-zA-Z0-9-]{1,80}")))
        require(sourceFingerprints.size in 1..100 && groups.size in 1..12)
        val members = groups.flatMap { it.memberIds }
        require(members.isNotEmpty() && members.size <= 100 && members.distinct().size == members.size)
        require(members.all { it in sourceFingerprints })
        require(groups.all { it.title.isNotBlank() && it.title.length <= 80 && it.memberIds.isNotEmpty() })
        require(nextSteps.size <= 20 && nextSteps.all { it.isNotBlank() && it.length <= 300 })
    }
}

internal fun canvasNodeFingerprint(node: CanvasNode): String = MessageDigest.getInstance("SHA-256")
    .digest(listOf(node.id, node.type.name, node.refObjectId.orEmpty(), node.text,
        node.x, node.y, node.width, node.height, node.zIndex).joinToString("\u0000").toByteArray())
    .joinToString("") { "%02x".format(it) }

/** Parse only a bounded organization proposal. No executable tool/action fields are accepted. */
object CanvasOrganizationCodec {
    fun parse(text: String, base: CanvasOrganizationProposal): CanvasOrganizationProposal {
        require(text.length <= 20_000)
        val json = JSONObject(text.trim().removePrefix("```json").removePrefix("```").removeSuffix("```").trim())
        require(json.keys().asSequence().toSet().all { it in setOf("groups", "nextSteps") })
        val groups = json.getJSONArray("groups")
        require(groups.length() in 1..12)
        val parsed = (0 until groups.length()).map { index ->
            val group = groups.getJSONObject(index)
            require(group.keys().asSequence().toSet() == setOf("title", "memberIds"))
            val ids = group.getJSONArray("memberIds")
            require(ids.length() in 1..100)
            CanvasOrganizationGroup(group.getString("title"), (0 until ids.length()).map(ids::getString))
        }
        val steps = json.optJSONArray("nextSteps")
        require(steps == null || steps.length() <= 20)
        return base.copy(groups = parsed, nextSteps = steps?.let { array -> (0 until array.length()).map(array::getString) }.orEmpty(),
            suggestedByAi = true).also { it.validate() }
    }
}

data class CanvasUndoSnapshot(val nodes: List<CanvasNodeEntity>, val edges: List<CanvasEdgeEntity>,
    val afterFingerprint: String)

internal fun boardFingerprint(nodes: List<CanvasNodeEntity>, edges: List<CanvasEdgeEntity>): String =
    MessageDigest.getInstance("SHA-256").digest((nodes.sortedBy { it.id }.joinToString() + edges.sortedBy { it.id }.joinToString()).toByteArray())
        .joinToString("") { "%02x".format(it) }

internal fun canvasEdgesFingerprint(edges: List<CanvasEdgeEntity>): String = MessageDigest.getInstance("SHA-256")
    .digest(edges.sortedBy { it.id }.joinToString().toByteArray()).joinToString("") { "%02x".format(it) }
