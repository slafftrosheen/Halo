package com.halo.app.data.studio

import android.graphics.Region
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = android.app.Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class StudioVectorEditingTest {
    private val outer = StudioLayer(id = "outer", name = "Outer", kind = StudioLayerKind.RECTANGLE,
        bounds = StudioRect(100, 100, 800, 800))
    private val inner = StudioLayer(id = "inner", name = "Inner", kind = StudioLayerKind.RECTANGLE,
        bounds = StudioRect(300, 300, 400, 400))

    @Test fun trimCutsAHoleAndPersistsExactSelectionInOneUndoStep() {
        val original = StudioDocument(1000, 1000, layers = listOf(outer, inner), selectedLayerId = outer.id)
        val change = StudioCommand.CombineShapes(outer.id, inner.id, StudioShapeOperation.TRIM, resultId = "cut")
        val session = StudioSession(original).apply(change)
        assertEquals(original, session.undo().current)
        val reopened = StudioDocumentCodec.decode(StudioDocumentCodec.encode(session.current))
        assertEquals("cut", reopened.selectedLayerId)
        assertEquals(listOf(outer.copy(visible = false), inner.copy(visible = false)), reopened.layers.take(2))
        val result = reopened.layers.last()
        val shape = requireNotNull(result.vector)
        assertEquals(2, shape.contours.size)
        val area = Region().apply { setPath(shape.path(result.bounds.vectorPixels(1000, 1000)), Region(0, 0, 1000, 1000)) }
        assertTrue(area.contains(150, 150))
        assertFalse(area.contains(500, 500))
        val svg = StudioProjectExport.encode(reopened, StudioProjectFormat.SVG).decodeToString()
        assertTrue(svg.contains("fill-rule=\"evenodd\""))
    }

    @Test fun splitPreservesTheCubicAndSvgExportsItsControlPoints() {
        val a = StudioPathNode(0f, 0f, outgoingX = 0f, outgoingY = 1f)
        val b = StudioPathNode(1f, 0f, incomingX = 1f, incomingY = 1f)
        val vector = StudioVectorGeometry(StudioShape.PATH, nodes = listOf(a, b), closed = false)
        val document = StudioDocument(800, 400, layers = listOf(outer.copy(vector = vector, rotation = 15)))
        val edit = StudioEditParser.parse(document, "split_path_segment", mapOf("layerId" to outer.id, "node" to 0))
            as StudioEditRequest.Command
        val changed = document.apply(edit.value)
        val nodes = requireNotNull(changed.layers.single().vector).nodes
        assertEquals(StudioPathNode(.5f, .75f, .25f, .75f, .75f, .75f), nodes[1])
        assertEquals(0.5f, nodes.first().outgoingY, 0f)
        assertEquals(0.5f, nodes.last().incomingY, 0f)
        val svg = StudioProjectExport.encode(changed, StudioProjectFormat.SVG).decodeToString()
        assertTrue(svg.contains("C80.0 200.0"))
        assertTrue(svg.contains("transform=\"rotate(15 400.0 200.0)\""))
    }

    @Test fun lockedShapesAndInvalidNodeIndicesLeaveTheSourceUntouched() {
        val path = outer.copy(vector = StudioVectorGeometry.default(StudioShape.PATH))
        val doc = StudioDocument(layers = listOf(path, inner.copy(locked = true)))
        assertTrue(runCatching {
            doc.apply(StudioCommand.CombineShapes(outer.id, inner.id, StudioShapeOperation.WELD))
        }.isFailure)
        assertTrue(runCatching { StudioEditParser.parse(doc, "move_path_node",
            mapOf("layerId" to outer.id, "contour" to 4, "node" to 0, "x" to .2, "y" to .2)) }.isFailure)
        assertEquals(path, doc.layers.first())
    }
}
