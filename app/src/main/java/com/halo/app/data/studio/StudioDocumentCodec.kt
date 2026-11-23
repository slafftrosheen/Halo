package com.halo.app.data.studio

import com.halo.app.data.workspace.CanvasImageCrop
import com.halo.app.data.workspace.CanvasImageFormat
import com.halo.app.data.workspace.CanvasImageRecipe
import org.json.JSONArray
import org.json.JSONObject

/** Strict portable project representation; unknown schemas/fields never become an empty project. */
object StudioDocumentCodec {
    fun encode(document: StudioDocument): String {
        document.requireValid()
        val recipe = document.recipe
        return JSONObject().put("schema", 1).put("width", document.width).put("height", document.height)
            .apply { if (document.dpi != 96) put("dpi", document.dpi) }
            .put("background", document.background).put("selected", document.selectedLayerId ?: JSONObject.NULL)
            .put("recipe", JSONObject().put("turns", recipe.quarterTurns).put("mirror", recipe.mirrorHorizontal)
                .put("square", recipe.squareCrop).put("edge", recipe.maxEdge).put("format", recipe.format.name)
                .put("crop", recipe.crop?.let { rect(StudioRect(it.x, it.y, it.width, it.height)) } ?: JSONObject.NULL))
            .put("layers", JSONArray().apply { document.layers.forEach { put(layer(it)) } })
            .toString().also { require(it.length <= StudioDocument.MAX_ENCODED_CHARS) }
    }

    fun decode(encoded: String): StudioDocument {
        require(encoded.length in 2..StudioDocument.MAX_ENCODED_CHARS)
        val root = JSONObject(encoded)
        root.fields(*(listOf("schema", "width", "height", "background", "selected", "recipe", "layers") +
            listOf("dpi").filter(root::has)).toTypedArray())
        require(root.integer("schema") == 1)
        val recipe = root.getJSONObject("recipe").fields("turns", "mirror", "square", "edge", "format", "crop")
        val layers = root.getJSONArray("layers")
        require(layers.length() <= StudioDocument.MAX_LAYERS)
        return StudioDocument(root.integer("width"), root.integer("height"), root.integer("background"),
            CanvasImageRecipe(recipe.integer("turns"), recipe.boolean("mirror"), recipe.boolean("square"),
                recipe.integer("edge"), CanvasImageFormat.valueOf(recipe.string("format")),
                if (recipe.isNull("crop")) null else readRect(recipe.getJSONObject("crop")).let { CanvasImageCrop(it.x, it.y, it.width, it.height) }),
            (0 until layers.length()).map { readLayer(layers.getJSONObject(it)) }, root.nullableString("selected"),
            if (root.has("dpi")) root.integer("dpi") else 96)
            .also(StudioDocument::requireValid)
    }

    private fun rect(value: StudioRect) = JSONObject().put("x", value.x).put("y", value.y)
        .put("width", value.width).put("height", value.height)
    private fun readRect(value: JSONObject): StudioRect {
        value.fields("x", "y", "width", "height")
        return StudioRect(value.integer("x"), value.integer("y"), value.integer("width"), value.integer("height"))
    }
    private fun layer(value: StudioLayer): JSONObject = JSONObject()
        .put("id", value.id).put("name", value.name).put("kind", value.kind.name).put("bounds", rect(value.bounds))
        .put("visible", value.visible).put("locked", value.locked).put("opacity", value.opacity)
        .put("rotation", value.rotation).put("color", value.color).put("text", value.text)
        .put("fontSize", value.fontSize).put("strokeWidth", value.strokeWidth).put("ink", value.ink)
        .put("source", value.source?.let { JSONObject().put("id", it.objectId).put("version", it.version) } ?: JSONObject.NULL)
        .put("mask", value.mask?.let(::rect) ?: JSONObject.NULL)
        .put("brightness", value.brightness).put("contrast", value.contrast).put("saturation", value.saturation)
        .apply { if (value.pencil) put("pencil", true) }
        .apply { value.vector?.let { put("vector", encodeVector(it)) } }
        .apply { value.symmetry?.let { put("symmetry", JSONObject().put("axis", it.axis.name).put("repeats", it.repeats)) } }
    private fun readLayer(value: JSONObject): StudioLayer {
        val expected = arrayOf("id", "name", "kind", "bounds", "visible", "locked", "opacity", "rotation", "color", "text",
            "fontSize", "strokeWidth", "ink", "source", "mask", "brightness", "contrast", "saturation")
        value.fields(*(expected + listOf("pencil", "symmetry", "vector").filter(value::has)))
        val source = if (value.isNull("source")) null else value.getJSONObject("source").let {
            it.fields("id", "version"); StudioSource(it.string("id"), it.string("version"))
        }
        return StudioLayer(value.string("id"), value.string("name"), StudioLayerKind.valueOf(value.string("kind")),
            readRect(value.getJSONObject("bounds")), value.boolean("visible"), value.boolean("locked"),
            value.integer("opacity"), value.integer("rotation"), value.integer("color"), value.string("text"),
            value.integer("fontSize"), value.integer("strokeWidth"), value.string("ink"), source,
            if (value.isNull("mask")) null else readRect(value.getJSONObject("mask")),
            value.integer("brightness"), value.integer("contrast"), value.integer("saturation"),
            value.has("pencil") && value.boolean("pencil"), value.optJSONObject("symmetry")?.let {
                it.fields("axis", "repeats")
                StudioSymmetry(StudioSymmetryAxis.valueOf(it.string("axis")), it.integer("repeats"))
            }.also { require(it != null || !value.has("symmetry")) },
            if (value.has("vector")) decodeVector(value.getJSONObject("vector")) else null)
    }
    private fun encodeVector(value: StudioVectorGeometry): JSONObject = JSONObject()
        .put("shape", value.shape.name).put("corners", JSONArray(value.corners))
        .put("sides", value.sides).put("inner", value.innerRadius.toDouble())
        .put("start", value.startAngle.toDouble()).put("sweep", value.sweep.toDouble())
        .put("stroke", value.stroke.toDouble()).put("dash", value.dash).put("closed", value.closed)
        .put("nodes", JSONArray(value.nodes.map { node ->
            JSONArray(listOf(node.x, node.y, node.incomingX, node.incomingY, node.outgoingX, node.outgoingY))
        }))

    private fun decodeVector(value: JSONObject): StudioVectorGeometry {
        value.fields("shape", "corners", "sides", "inner", "start", "sweep", "stroke", "dash", "closed", "nodes")
        val corners = value.getJSONArray("corners").also { require(it.length() == 4) }
        val nodes = value.getJSONArray("nodes").also { require(it.length() <= 128) }
        fun numeric(array: JSONArray, index: Int): Float = (array.get(index) as? Number)?.toFloat() ?: error("Invalid vector coordinate")
        fun numeric(key: String): Float = (value.get(key) as? Number)?.toFloat() ?: error("Invalid vector quantity")
        return StudioVectorGeometry(StudioShape.valueOf(value.string("shape")), List(4) { numeric(corners, it) },
            value.integer("sides"), numeric("inner"), numeric("start"), numeric("sweep"), numeric("stroke"),
            value.boolean("dash"), value.boolean("closed"), List(nodes.length()) { index ->
                val item = nodes.getJSONArray(index).also { require(it.length() == 6) }
                StudioPathNode(numeric(item, 0), numeric(item, 1), numeric(item, 2), numeric(item, 3), numeric(item, 4), numeric(item, 5))
            })
    }

    private fun JSONObject.fields(vararg expected: String): JSONObject = apply {
        require(keys().asSequence().toSet() == expected.toSet())
    }
    private fun JSONObject.integer(key: String): Int {
        val value = get(key)
        require(value is Number)
        val number = value.toDouble()
        require(number.isFinite() && number >= Int.MIN_VALUE && number <= Int.MAX_VALUE && number % 1.0 == 0.0)
        return number.toInt()
    }
    private fun JSONObject.boolean(key: String): Boolean = (get(key) as? Boolean) ?: error("Invalid project flag")
    private fun JSONObject.string(key: String): String = (get(key) as? String) ?: error("Invalid project text")
    private fun JSONObject.nullableString(key: String): String? = if (isNull(key)) null else string(key)
}
