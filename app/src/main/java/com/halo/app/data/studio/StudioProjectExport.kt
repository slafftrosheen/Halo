package com.halo.app.data.studio

import android.graphics.Matrix
import android.graphics.Path
import android.graphics.PathMeasure
import android.graphics.RectF
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import kotlin.math.ceil

enum class StudioProjectFormat(val extension: String, val mime: String) {
    SVG("svg", "image/svg+xml"), DXF("dxf", "application/dxf"), PDF("pdf", "application/pdf"), HALO("halo", "application/zip")
}

internal object StudioProjectExport {
    fun allows(document: StudioDocument, format: StudioProjectFormat): Boolean {
        if (format == StudioProjectFormat.HALO) return document.layers.none { it.source != null }
        val recipe = document.recipe
        if (format == StudioProjectFormat.PDF) return document.layers.none { it.source != null } && !recipe.squareCrop &&
            recipe.crop == null && !recipe.mirrorHorizontal && recipe.quarterTurns == 0
        val visible = document.layers.filter { it.visible && it.opacity > 0 }
        return !recipe.squareCrop && recipe.crop == null && !recipe.mirrorHorizontal && recipe.quarterTurns == 0 &&
            visible.isNotEmpty() && visible.all {
                it.kind in setOf(StudioLayerKind.RECTANGLE, StudioLayerKind.ELLIPSE) &&
                    it.mask == null && it.brightness == 0 && it.contrast == 1000 && it.saturation == 1000 &&
                    it.vector?.shape != StudioShape.DIMENSION
            }
    }

    fun encode(document: StudioDocument, format: StudioProjectFormat): ByteArray {
        document.requireValid()
        require(allows(document, format)) { "The project contains content unsupported by this format." }
        return when (format) {
            StudioProjectFormat.HALO -> ByteArrayOutputStream().use { bytes ->
                ZipOutputStream(bytes).use { archive ->
                    archive.putNextEntry(ZipEntry("project.json"))
                    archive.write(StudioDocumentCodec.encode(document).toByteArray(Charsets.UTF_8))
                    archive.closeEntry()
                }
                bytes.toByteArray()
            }
            StudioProjectFormat.SVG -> svg(document).toByteArray(Charsets.UTF_8)
            StudioProjectFormat.DXF -> dxf(document).toByteArray(Charsets.UTF_8)
            StudioProjectFormat.PDF -> error("PDF export requires the shared Studio renderer")
        }
    }

    fun readPackage(source: InputStream): StudioDocument = ZipInputStream(source).use { archive ->
        val entry = requireNotNull(archive.nextEntry)
        require(!entry.isDirectory && entry.name == "project.json")
        val limit = StudioDocument.MAX_ENCODED_CHARS * 4
        require(entry.size <= limit)
        val buffer = ByteArray(8192)
        val content = ByteArrayOutputStream()
        while (true) {
            val count = archive.read(buffer)
            if (count < 0) break
            require(content.size() + count <= limit) { "Invalid Studio package size" }
            content.write(buffer, 0, count)
        }
        archive.closeEntry()
        require(archive.nextEntry == null)
        val decoded = Charsets.UTF_8.newDecoder().decode(java.nio.ByteBuffer.wrap(content.toByteArray())).toString()
        StudioDocumentCodec.decode(decoded).also { require(it.layers.none { layer -> layer.source != null }) }
    }

    private data class Shape(val layer: StudioLayer, val path: Path, val stroke: Float)

    private fun shapes(document: StudioDocument) = document.layers.filter { it.visible && it.opacity > 0 }.map { layer ->
        val rect = layer.bounds.let { bounds ->
            RectF(bounds.x * document.width / 1000f, bounds.y * document.height / 1000f,
                (bounds.x + bounds.width) * document.width / 1000f, (bounds.y + bounds.height) * document.height / 1000f)
        }
        val vector = layer.vector
        val path = vector?.path(rect) ?: Path().apply {
            if (layer.kind == StudioLayerKind.RECTANGLE) addRect(rect, Path.Direction.CW)
            else addOval(rect, Path.Direction.CW)
        }
        path.transform(Matrix().apply { setRotate(layer.rotation.toFloat(), rect.centerX(), rect.centerY()) })
        Shape(layer, path, minOf(rect.width(), rect.height()) * (vector?.stroke ?: 0f))
    }

    private fun flatten(path: Path): List<Pair<List<Pair<Float, Float>>, Boolean>> {
        val measure = PathMeasure(path, false)
        val contours = mutableListOf<Pair<List<Pair<Float, Float>>, Boolean>>()
        val point = FloatArray(2)
        do {
            val length = measure.length
            if (length > 0f) {
                val count = ceil(length / 2f).toInt().coerceIn(if (measure.isClosed) 3 else 1, 2048)
                val vertices = (0..count).map { index ->
                    check(measure.getPosTan(length * index / count, point, null))
                    point[0] to point[1]
                }
                contours += (if (measure.isClosed) vertices.dropLast(1) else vertices) to measure.isClosed
            }
        } while (measure.nextContour())
        return contours
    }

    private fun svg(document: StudioDocument) = buildString {
        val width = StudioLengthUnit.MM.fromPixels(document.width.toDouble(), document.dpi)
        val height = StudioLengthUnit.MM.fromPixels(document.height.toDouble(), document.dpi)
        append("<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"").append(width).append("mm\" height=\"").append(height)
            .append("mm\" viewBox=\"0 0 ").append(document.width).append(' ').append(document.height).append("\">\n")
        if (document.background ushr 24 != 0) append("<rect width=\"100%\" height=\"100%\" fill=\"")
            .append(rgb(document.background)).append("\" fill-opacity=\"").append((document.background ushr 24) / 255f).append("\"/>\n")
        shapes(document).forEach { shape ->
            val color = rgb(shape.layer.color)
            val alpha = (shape.layer.color ushr 24) / 255f * shape.layer.opacity / 1000f
            append("<path d=\"")
            flatten(shape.path).forEach { (vertices, closed) ->
                vertices.forEachIndexed { index, (x, y) -> append(if (index == 0) "M" else "L").append(x).append(' ').append(y) }
                if (closed) append('Z')
            }
            append("\" opacity=\"").append(alpha).append('"')
            val vector = shape.layer.vector
            if (vector?.outlineOnly == true) {
                append(" fill=\"none\" stroke=\"").append(color).append("\" stroke-width=\"").append(shape.stroke).append('"')
                if (vector.dash) append(" stroke-dasharray=\"").append(shape.stroke * 4).append(' ').append(shape.stroke * 3).append('"')
            } else append(" fill=\"").append(color).append('"')
            append("/>\n")
        }
        append("</svg>\n")
    }

    private fun rgb(value: Int) = "#" + (value and 0xFFFFFF).toString(16).padStart(6, '0')

    private fun dxf(document: StudioDocument) = buildString {
        fun tag(code: Int, value: Any) { append(code).append('\n').append(value).append('\n') }
        tag(0, "SECTION"); tag(2, "HEADER"); tag(9, "$" + "ACADVER"); tag(1, "AC1032")
        tag(9, "$" + "INSUNITS"); tag(70, 4); tag(0, "ENDSEC")
        val shapes = shapes(document)
        tag(0, "SECTION"); tag(2, "TABLES"); tag(0, "TABLE"); tag(2, "LAYER"); tag(70, shapes.size)
        shapes.forEachIndexed { index, shape ->
            tag(0, "LAYER"); tag(100, "AcDbSymbolTableRecord"); tag(100, "AcDbLayerTableRecord")
            tag(2, "Halo_" + (index + 1)); tag(70, 0); tag(62, 7); tag(6, "CONTINUOUS")
            tag(420, shape.layer.color and 0xFFFFFF)
        }
        tag(0, "ENDTAB"); tag(0, "ENDSEC"); tag(0, "SECTION"); tag(2, "ENTITIES")
        shapes.forEachIndexed { index, shape ->
            flatten(shape.path).filter { it.first.size >= 2 }.forEach { (points, closed) ->
                tag(0, "LWPOLYLINE"); tag(100, "AcDbEntity"); tag(8, "Halo_" + (index + 1))
                tag(100, "AcDbPolyline"); tag(90, points.size); tag(70, if (closed) 1 else 0)
                points.forEach { (x, y) ->
                    tag(10, StudioLengthUnit.MM.fromPixels(x.toDouble(), document.dpi))
                    tag(20, StudioLengthUnit.MM.fromPixels(document.height - y.toDouble(), document.dpi))
                }
            }
        }
        tag(0, "ENDSEC"); tag(0, "EOF")
    }
}
