package com.halo.app.data.studio

import com.halo.app.data.workspace.*
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class StudioDocumentTest {
    private val text = StudioLayer(id = "text", name = "Title", kind = StudioLayerKind.TEXT, text = "Sveiki · Привет",
        mask = StudioRect(10, 20, 800, 700), opacity = 600, brightness = -100, contrast = 1200, saturation = 700)
    @Test fun exactSourceAndNonDestructiveEditsSurviveRoundTrip() {
        val image = StudioLayer(id = "image", name = "Original", kind = StudioLayerKind.IMAGE, source = StudioSource("file", "sha256-v1"))
        val document = StudioDocument(width = 1600, height = 900, layers = listOf(image, text), selectedLayerId = text.id,
            recipe = CanvasImageRecipe(quarterTurns = 1, mirrorHorizontal = true, crop = CanvasImageCrop(100, 200, 800, 600)))
        assertEquals(document, StudioDocumentCodec.decode(StudioDocumentCodec.encode(document)))
        assertEquals(540 to 1280, document.recipe.outputDimensions(1600, 900))
        assertEquals(StudioSource("file", "sha256-v1"), document.layers.first().source)
    }
    @Test fun unsupportedOrMalformedDataNeverBecomesAnEmptyDocument() {
        val original = StudioDocumentCodec.encode(StudioDocument(layers = listOf(text)))
        for (mutation in listOf<(JSONObject) -> Unit>(
            { it.put("schema", 2) }, { it.put("width", 1.5) }, { it.put("width", "1024") },
            { it.put("script", "arbitrary") }, { it.getJSONArray("layers").getJSONObject(0).put("uri", "file:///private") },
            { it.put("selected", "missing") })) {
            val changed = JSONObject(original).also(mutation)
            assertTrue(changed.toString(), runCatching { StudioDocumentCodec.decode(changed.toString()) }.isFailure)
        }
    }
    @Test fun lockedLayersNeedAnExplicitUnlockBeforeAnyEdit() {
        val locked = text.copy(locked = true)
        val document = StudioDocument(layers = listOf(locked))
        assertTrue(runCatching { document.apply(StudioCommand.Replace(locked.copy(text = "Changed", locked = false))) }.isFailure)
        assertTrue(runCatching { document.apply(StudioCommand.Remove(locked.id)) }.isFailure)
        val result = document.apply(StudioCommand.Batch(listOf(StudioCommand.Replace(locked.copy(locked = false)),
            StudioCommand.Replace(locked.copy(locked = false, text = "Changed")))))
        assertEquals("Changed", result.layers.single().text)
        assertEquals("Sveiki · Привет", document.layers.single().text)
    }
    @Test fun historyIsPersistedBoundedAndSelectionDoesNotConsumeUndo() {
        var session = StudioSession(StudioDocument(layers = listOf(text)))
        repeat(30) { session = session.apply(StudioCommand.Background(it + 1)) }
        assertEquals(16, session.undo.size)
        val selected = session.apply(StudioCommand.Select(text.id))
        assertEquals(session.undo, selected.undo)
        val reopened = StudioSession.decode(selected.encode())
        assertEquals(selected, reopened)
        assertEquals(reopened.current, reopened.undo().redo().current)
        assertEquals(29, reopened.undo().current.background)
    }
    @Test fun parserRejectsPathsSourceRewritesAndFractionalGeometry() {
        val document = StudioDocument(layers = listOf(text))
        val invalid = listOf(
            "update_layer" to mapOf("layerId" to text.id, "changes" to mapOf("source" to "other")),
            "resize" to mapOf("width" to 20.5, "height" to 30),
            "add_rectangle" to mapOf("name" to "Shape", "path" to "/private"),
            "undo" to mapOf("replay" to true))
        invalid.forEach { (op, fields) -> assertTrue(runCatching { StudioEditParser.parse(document, op, fields) }.isFailure) }
        val command = StudioEditParser.parse(document, "update_layer", mapOf("layerId" to text.id, "changes" to mapOf("text" to "Updated"))) as StudioEditRequest.Command
        assertEquals("Updated", document.apply(command.value).layers.single().text)
    }
    @Test fun documentAndCropLimitsAreEnforcedBeforeRendering() {
        assertTrue(runCatching { StudioDocument(4096, 4096).requireValid() }.isFailure)
        assertTrue(runCatching { StudioDocument(layers = List(33) { text.copy(id = "id$it") }).requireValid() }.isFailure)
        assertTrue(runCatching { StudioDocument(layers = listOf(text, text)).requireValid() }.isFailure)
        assertTrue(runCatching { CanvasImageCrop(900, 0, 200, 1000) }.isFailure)
        assertTrue(runCatching { CanvasImageRecipe(squareCrop = true, crop = CanvasImageCrop(0, 0, 500, 500)) }.isFailure)
        assertEquals(CanvasCropPixels(0, 0, 1, 1), CanvasImageCrop(0, 0, 1, 1).pixels(1, 1))
    }
    @Test fun templatesRemainOrdinaryEditableLayers() {
        StudioPreset.entries.forEach { preset ->
            val document = StudioTemplates.document(preset, "My project")
            assertEquals(document, StudioDocumentCodec.decode(StudioDocumentCodec.encode(document)))
            document.layers.filter { it.kind == StudioLayerKind.TEXT }.forEach { layer ->
                assertEquals("Edited", document.apply(StudioCommand.Replace(layer.copy(text = "Edited"))).layers.first { it.id == layer.id }.text)
            }
        }
    }
}
