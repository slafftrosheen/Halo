package com.halo.app.nativeactions

import com.halo.app.data.notebook.CanvasDocumentEntity
import com.halo.app.data.notebook.CanvasEdgeEntity
import com.halo.app.data.notebook.CanvasNodeEntity
import org.json.JSONArray
import org.json.JSONObject

internal data class PrivateLibraryCanvas(val document: CanvasDocumentEntity, val nodes: List<CanvasNodeEntity>, val edges: List<CanvasEdgeEntity>) {
    fun encode(): JSONObject = JSONObject().put("viewportX", document.viewportX.toRawBits())
        .put("viewportY", document.viewportY.toRawBits()).put("scale", document.viewportScale.toRawBits())
        .put("rev", document.revision).put("nodes", JSONArray(nodes.map { node ->
            JSONObject().put("id", node.id).put("kind", node.type).put("ref", node.refObjectId).put("text", node.text)
                .put("x", node.x.toRawBits()).put("y", node.y.toRawBits()).put("width", node.width.toRawBits())
                .put("height", node.height.toRawBits()).put("z", node.zIndex).put("created", node.createdAtEpochMs)
                .put("updated", node.updatedAtEpochMs).put("locked", node.locked)
        })).put("edges", JSONArray(edges.map { edge ->
            JSONObject().put("id", edge.id).put("from", edge.fromNodeId).put("to", edge.toNodeId)
                .put("label", edge.label).put("created", edge.createdAtEpochMs)
        }))

    companion object {
        fun decode(id: String, root: JSONObject): PrivateLibraryCanvas {
            val nodes = root.getJSONArray("nodes").let { array -> List(array.length()) { index ->
                with(array.getJSONObject(index)) {
                    CanvasNodeEntity(getString("id"), id, getString("kind"), nullable("ref"), getString("text"),
                        float("x"), float("y"), float("width"), float("height"), getInt("z"), getLong("created"),
                        getLong("updated"), getBoolean("locked"))
                }
            } }
            val edges = root.getJSONArray("edges").let { array -> List(array.length()) { index ->
                with(array.getJSONObject(index)) {
                    CanvasEdgeEntity(getString("id"), id, getString("from"), getString("to"), nullable("label"), getLong("created"))
                }
            } }
            return PrivateLibraryCanvas(CanvasDocumentEntity(id, root.float("viewportX"), root.float("viewportY"),
                root.float("scale"), root.getInt("rev")), nodes, edges)
        }

        private fun JSONObject.float(key: String) = Float.fromBits(getInt(key))
        private fun JSONObject.nullable(key: String) = if (isNull(key)) null else getString(key)
    }
}
