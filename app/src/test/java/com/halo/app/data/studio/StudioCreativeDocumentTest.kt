package com.halo.app.data.studio

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class StudioCreativeDocumentTest {
    @Test fun nearLimitLegacyHistoryDoesNotGrowWithCreativeDefaults() {
        val point=com.halo.sdk.runtime.ink.InkPoint(999,999,999999,999,1571,-3142,65535)
        fun document(points:Int):StudioDocument {
            val ink=com.halo.sdk.runtime.ink.InkCodec.encode(com.halo.sdk.runtime.ink.InkDocument(List(points) {point}.chunked(2048).mapIndexed {index,row->
                com.halo.sdk.runtime.ink.InkStroke(com.halo.sdk.runtime.ink.InkTool.STYLUS,row,"stroke-$index")
            }))
            return StudioDocument(layers=List(4) {StudioLayer(id="ink-$it",name="Ink",kind=StudioLayerKind.INK,ink=ink)}+
                List(28) {StudioLayer(id="text-$it",name="Text",kind=StudioLayerKind.TEXT,text="a".repeat(2000))})
        }
        fun legacy(doc:StudioDocument):String {
            val root=JSONObject(StudioDocumentCodec.encode(doc)).put("schema",1)
            val rows=root.getJSONArray("layers")
            for(index in 0 until rows.length()) listOf("parent","blend","typography","imageCrop","effects","texture","textureSeed","strokeOnly").forEach {rows.getJSONObject(index).remove(it)}
            return root.toString()
        }
        val base=legacy(document(6000)).length
        val encoded=legacy(document(6000+(998000-base)/(4*36)))
        assertTrue(encoded.length.toString(),encoded.length*4 in 3_980_000..4_000_000)
        val envelope=JSONObject().put("schema",1).put("current",encoded).put("undo",org.json.JSONArray(List(4) {encoded})).put("redo",org.json.JSONArray()).toString()
        val reopened=StudioSession.decode(envelope)
        assertEquals(4,reopened.undo.size)
        assertEquals(4,StudioSession.decode(reopened.encode()).undo.size)
    }
    @Test fun ungroupKeepsCurrentPaintOrderAndRefusesToneLoss() {
        val a=shape("a");val b=shape("b");val top=shape("top")
        val grouped=StudioDocument(layers=listOf(a,b,top)).apply(StudioCommand.Group(listOf("a","b"),"Pair","g"))
        val front=grouped.apply(StudioCommand.Move("g",grouped.layers.lastIndex))
        val ungrouped=front.apply(StudioCommand.Ungroup("g"))
        assertEquals(listOf("top","a","b"),ungrouped.layers.map {it.id})
        val group=grouped.layers.single {it.id=="g"}
        for(adjusted in listOf(group.copy(brightness=100),group.copy(contrast=1200),group.copy(saturation=500))) {
            val toned=grouped.apply(StudioCommand.Replace(adjusted))
            assertTrue(runCatching {toned.apply(StudioCommand.Ungroup("g"))}.isFailure)
        }
    }
    private fun shape(id: String, x: Int = 100) = StudioLayer(id = id, name = id,
        kind = StudioLayerKind.RECTANGLE, bounds = StudioRect(x, 100, 200, 200))

    @Test fun groupingAndUndoPreserveChildrenAndTheirGlobalGeometry() {
        val original = StudioDocument(layers = listOf(shape("a"), shape("b", 500)))
        val session = StudioSession(original).apply(StudioCommand.Group(listOf("a", "b"), "Pair", "group"))
        assertEquals(original.layers.map { it.bounds }, session.current.layers.filter { it.kind != StudioLayerKind.GROUP }.map { it.bounds })
        assertEquals(setOf("group"), session.current.layers.filter { it.id != "group" }.map { it.parentId }.toSet())
        assertEquals(original, session.undo().current)
        assertEquals(original.layers, session.current.apply(StudioCommand.Ungroup("group")).layers)
    }
    @Test fun lockedAncestorsRejectTypedAndDirectChildEdits() {
        val grouped = StudioDocument(layers = listOf(shape("a"))).apply(StudioCommand.Group(listOf("a"), "Group", "g"))
        val group = grouped.layers.single { it.id == "g" }
        val locked = grouped.apply(StudioCommand.Replace(group.copy(locked = true)))
        val child = locked.layers.single { it.id == "a" }
        for (command in listOf(StudioCommand.Replace(child.copy(color = 1)), StudioCommand.Remove("a"),
            StudioCommand.Move("a", 0), StudioCommand.Ungroup("g"), StudioCommand.Duplicate("a"))) {
            assertTrue(command.toString(), runCatching { locked.apply(command) }.isFailure)
        }
        assertEquals("a", locked.apply(StudioCommand.Select("a")).selectedLayerId)
        assertTrue(runCatching { StudioEditParser.parse(locked, "update_layer", mapOf("layerId" to "a", "changes" to mapOf("opacity" to 500))) }.isFailure)
    }
    @Test fun groupDuplicateCreatesIndependentHierarchyAndSourceReferences() {
        val grouped = StudioDocument(layers = listOf(shape("a"), shape("b", 500)))
            .apply(StudioCommand.Group(listOf("a", "b"), "Group", "g"))
        val result = grouped.apply(StudioCommand.Duplicate("g"))
        assertEquals(6, result.layers.size)
        val copy = result.layers.single { it.id == result.selectedLayerId }
        assertEquals(StudioLayerKind.GROUP, copy.kind)
        assertEquals(2, result.layers.count { it.parentId == copy.id })
        assertEquals(6, result.layers.map { it.id }.distinct().size)
        assertEquals(3, grouped.layers.size)
    }
    @Test fun invalidHierarchyAndOversizedEffectStacksFailClosed() {
        val a = StudioLayer(id = "a", name = "a", kind = StudioLayerKind.GROUP, parentId = "b")
        val b = StudioLayer(id = "b", name = "b", kind = StudioLayerKind.GROUP, parentId = "a")
        assertTrue(runCatching { StudioDocument(layers = listOf(a,b)).requireValid() }.isFailure)
        assertTrue(runCatching { StudioDocument(layers = listOf(shape("x").copy(parentId = "missing"))).requireValid() }.isFailure)
        assertTrue(runCatching { shape("x").copy(effects = List(9) { StudioEffect(kind = StudioEffectKind.GRAIN) }).requireValid() }.isFailure)
    }
    @Test fun legacySchemaOneLoadsWithoutInventingNewProperties() {
        val legacy=JSONObject(StudioDocumentCodec.encode(StudioDocument(layers=listOf(shape("old")))))
        legacy.put("schema",1)
        val layer=legacy.getJSONArray("layers").getJSONObject(0)
        listOf("parent","blend","typography","imageCrop","effects","texture","textureSeed","strokeOnly").forEach {layer.remove(it)}
        val reopened=StudioDocumentCodec.decode(legacy.toString())
        assertEquals(shape("old"),reopened.layers.single())
        assertEquals(reopened,StudioDocumentCodec.decode(StudioDocumentCodec.encode(reopened)))
    }
    @Test fun strictCreativeParserEditsRealCanonicalStateAndRejectsUnknownEffectFields() {
        val original=StudioDocument(layers=listOf(shape("a"),shape("b",500)))
        val grouped=StudioEditParser.parse(original,"group_layers",mapOf("layerIds" to listOf("a","b"),"name" to "Pair")) as StudioEditRequest.Command
        val document=original.apply(grouped.value)
        assertEquals(1,document.layers.count {it.kind==StudioLayerKind.GROUP})
        val effect=StudioCreativeCodec.effect(StudioEffect(id="tone",kind=StudioEffectKind.SATURATION)).let {value->
            value.keys().asSequence().associateWith {key->value.get(key)}
        }
        val edit=StudioEditParser.parse(original,"update_layer",mapOf("layerId" to "a","changes" to mapOf("effects" to listOf(effect)))) as StudioEditRequest.Command
        assertEquals(StudioEffectKind.SATURATION,original.apply(edit.value).layers.first().effects.single().kind)
        assertTrue(runCatching {StudioEditParser.parse(original,"update_layer",mapOf("layerId" to "a","changes" to mapOf("effects" to listOf(effect+mapOf("script" to "bad")))))}.isFailure)
    }
    @Test fun newTypographyCropTextureBlendAndEffectsSurviveHistoryRoundTrip() {
        val layer = StudioLayer(id = "paper", name = "Paper", kind = StudioLayerKind.TEXTURE,
            texture = StudioTexture.CANVAS, blend = StudioBlend.MULTIPLY,
            effects = listOf(StudioEffect(id = "tone", kind = StudioEffectKind.EXPOSURE, amount = 250, seed = 34)))
        val text = StudioLayer(id = "title", name = "Title", kind = StudioLayerKind.TEXT, text = "Привет · Sveiki",
            typography = StudioTypography(family = StudioFont.SERIF, weight = 700, italic = true,
                alignment = StudioTextAlignment.CENTER, letterSpacing = 100, lineSpacing = 1200))
        val document = StudioDocument(layers = listOf(layer, text), selectedLayerId = "title")
        assertEquals(document, StudioDocumentCodec.decode(StudioDocumentCodec.encode(document)))
        assertEquals(StudioSession(document), StudioSession.decode(StudioSession(document).encode()))
        val invalid = JSONObject(StudioDocumentCodec.encode(document)).apply { getJSONArray("layers").getJSONObject(0).put("futureEffect", true) }
        assertTrue(runCatching { StudioDocumentCodec.decode(invalid.toString()) }.isFailure)
    }
    @Test fun alignAndDistributeUseSameCanonicalCoordinatesAsTouch() {
        val document = StudioDocument(layers = listOf(shape("a", 100), shape("b", 400), shape("c", 650)))
        val aligned = document.apply(StudioCommand.Layout(listOf("a","b"), StudioLayout.ALIGN_LEFT))
        assertEquals(listOf(100,100), aligned.layers.take(2).map { it.bounds.x })
        val distributed = document.apply(StudioCommand.Layout(listOf("a","b","c"), StudioLayout.DISTRIBUTE_HORIZONTAL))
        assertEquals(375, distributed.layers[1].bounds.x)
        assertEquals(document, StudioSession(document).apply(StudioCommand.Layout(listOf("a","b"), StudioLayout.ALIGN_LEFT)).undo().current)
    }
}
