package com.halo.app.di.tools

import com.halo.app.data.studio.*

/** Bounded readback using the existing v1 operability protocol for schema-2 Studio documents. */
internal object StudioToolResultContracts {
    private val text=mapOf("type" to "string","maxLength" to 200)
    private val id=mapOf("type" to "string","pattern" to "^[A-Za-z0-9_-]{1,100}$")
    private val bool=mapOf("type" to "boolean")
    private fun integer(min:Long,max:Long)=mapOf("type" to "integer","minimum" to min,"maximum" to max)
    private val revision=integer(1,StudioProjectRepository.MAX_REVISION)
    private fun nullable(schema:Map<String,Any>)=mapOf("anyOf" to listOf(schema,mapOf("type" to "null")))
    private fun array(item:Map<String,Any>,max:Int)=mapOf("type" to "array","maxItems" to max,"items" to item)
    private fun closed(properties:Map<String,Any>,optional:Set<String> = emptySet()):Map<String,Any> = mapOf(
        "type" to "object","properties" to properties,"required" to (properties.keys-optional).toList(),"additionalProperties" to false)
    private fun enumeration(values:List<String>)=mapOf("type" to "string","enum" to values)
    private val rect=closed(mapOf("x" to integer(-1000,2000),"y" to integer(-1000,2000),"width" to integer(1,3000),"height" to integer(1,3000)))
    private val summary=mapOf("projectId" to id,"revision" to revision,"selectedLayerId" to nullable(id),"title" to mapOf("type" to "string","maxLength" to 120),
        "width" to integer(1,4096),"height" to integer(1,4096),"layerCount" to integer(0,32),"localDraft" to bool,"canUndo" to bool,"canRedo" to bool)
    private val layer=closed(mapOf("id" to id,"name" to mapOf("type" to "string","maxLength" to 120),"kind" to enumeration(StudioLayerKind.entries.map {it.name}),
        "visible" to bool,"locked" to bool,"opacity" to integer(0,1000),"bounds" to rect,"textLength" to integer(0,2000),"effectCount" to integer(0,8),
        "rotation" to integer(-360,360),"color" to integer(Int.MIN_VALUE.toLong(),Int.MAX_VALUE.toLong()),"fontSize" to integer(1,1000),"strokeWidth" to integer(1,100),
        "brightness" to integer(-1000,1000),"contrast" to integer(0,3000),"saturation" to integer(0,3000),
        "sourceId" to nullable(id),"sourceVersion" to nullable(text),"parentId" to nullable(id),"blend" to StudioCreativeToolSchemas.fields.getValue("blend"),
        "editable" to bool,"typography" to StudioCreativeToolSchemas.typography,"imageCrop" to nullable(rect),"mask" to nullable(rect),
        "texture" to nullable(enumeration(StudioTexture.entries.map {it.name})),"textureSeed" to integer(Int.MIN_VALUE.toLong(),Int.MAX_VALUE.toLong()),"strokeOnly" to bool))
    private val inkSelection=closed(mapOf(
        "layerId" to id,"projectRevision" to revision,"sourceRevision" to text,"strokeCount" to integer(0,256),
        "bounds" to closed(mapOf("left" to integer(0,1000),"top" to integer(0,1000),
            "right" to integer(0,1000),"bottom" to integer(0,1000)))))
    private val context=mapOf("projectId" to id,"revision" to revision,"view" to enumeration(StudioToolReadback.views))
    private fun view(name:String,fields:Map<String,Any>,optional:Set<String> = emptySet()) =
        closed(context+fields+mapOf("view" to mapOf("type" to "string","const" to name)),optional)
    private val get:Map<String,Any> = mapOf("type" to "object","properties" to context,"required" to context.keys.toList(),"anyOf" to listOf(
        view("document",summary+mapOf("availableViews" to array(enumeration(StudioToolReadback.views),7),
            "background" to integer(Int.MIN_VALUE.toLong(),Int.MAX_VALUE.toLong()),
            "recipe" to closed(mapOf("quarterTurns" to integer(0,3),"mirror" to bool,"squareCrop" to bool,"maxEdge" to integer(512,4096),
                "format" to enumeration(listOf("PNG","JPEG")),"crop" to nullable(rect))),
            "sampleColor" to integer(Int.MIN_VALUE.toLong(),Int.MAX_VALUE.toLong()),"inkSelection" to inkSelection),setOf("sampleColor","inkSelection")),
        view("layers",mapOf("layers" to array(closed(mapOf("id" to id,"name" to mapOf("type" to "string","maxLength" to 40),
            "nameTruncated" to bool,"kind" to enumeration(StudioLayerKind.entries.map {it.name}),"visible" to bool,"locked" to bool,
            "editable" to bool,"parentId" to nullable(id))),4),"layersTruncated" to bool,"nextLayerOffset" to nullable(integer(0,32)))),
        view("layer",mapOf("layer" to layer)),
        view("text",mapOf("layerId" to id,"text" to mapOf("type" to "string","maxLength" to 384),
            "textOffset" to integer(0,2000),"textLength" to integer(0,2000),"nextTextOffset" to nullable(integer(0,2000)))),
        view("effects",mapOf("layerId" to id,"effects" to StudioCreativeToolSchemas.fields.getValue("effects"))),
        view("capabilities",mapOf(
            "brushes" to array(closed(mapOf("id" to enumeration(StudioBrush.entries.map {it.name}),"width" to integer(1,100),"opacity" to integer(0,1000))),4),
            "palette" to array(integer(Int.MIN_VALUE.toLong(),Int.MAX_VALUE.toLong()),8),
            "styles" to array(closed(mapOf("id" to text,"color" to integer(Int.MIN_VALUE.toLong(),Int.MAX_VALUE.toLong()),"opacity" to integer(0,1000),"typography" to StudioCreativeToolSchemas.typography)),3),
            "supportedEffects" to array(enumeration(StudioEffectKind.entries.map {it.name}),StudioEffectKind.entries.size),
            "supportedBlends" to array(enumeration(StudioBlend.entries.map {it.name}),StudioBlend.entries.size))),
        view("versions",mapOf("versions" to array(revision,100)))))
    fun forTool(name:String):Map<String,Any> = when(name) {
        "halo_studio_get"->get
        "halo_studio_list"->closed(mapOf("projects" to array(closed(summary+mapOf("title" to mapOf("type" to "string","maxLength" to 40),"titleTruncated" to bool)),4),
            "more" to bool,"nextProjectOffset" to nullable(integer(0,Int.MAX_VALUE.toLong()))))
        "halo_studio_current"->mapOf("type" to "object","anyOf" to listOf(closed(summary),closed(mapOf("attached" to mapOf("const" to false,"type" to "boolean")))))
        "halo_studio_status"->closed(mapOf("requestId" to id,"state" to enumeration(StudioUiRequestPhase.entries.map {it.name}+"UNKNOWN"),"projectId" to nullable(id),"resultProjectId" to nullable(id)))
        "halo_studio_open","halo_studio_ai_draft"->closed(mapOf("requestId" to id,"state" to mapOf("type" to "string","const" to "DISPATCHED"),"providerRequestSent" to mapOf("type" to "boolean","const" to false)))
        "halo_studio_export"->closed(mapOf("outputId" to id,"fileVersion" to text,"mimeType" to enumeration(listOf("image/png","image/jpeg")),"bytes" to integer(1,100_000_000),"savedToWorkspace" to bool))
        "halo_studio_insert"->closed(mapOf("canvasId" to id,"outputId" to id,"nodeId" to id,"inserted" to bool))
        else->closed(summary)
    }
}
