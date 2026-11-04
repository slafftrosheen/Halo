package com.halo.app.data.studio

import com.halo.app.data.workspace.*
import org.junit.Assert.*
import org.junit.Test

class StudioCanvasGeometryTest {
    @Test fun resizingAnAlreadyRotatedGroupMatchesTheSameFinalTransform() {
        val group=StudioLayer(id="g",name="Group",kind=StudioLayerKind.GROUP)
        val child=StudioLayer(id="s",name="Shape",kind=StudioLayerKind.RECTANGLE,parentId="g",bounds=StudioRect(100,100,100,100))
        val original=StudioDocument(2000,1000,layers=listOf(group,child))
        val final=group.copy(rotation=90,bounds=StudioRect(100,100,500,700))
        val direct=original.apply(StudioCommand.Replace(final))
        val staged=original.apply(StudioCommand.Replace(group.copy(rotation=90))).apply(StudioCommand.Replace(final))
        assertEquals(direct,staged)
    }
    @Test fun paintCoordinatesRoundTripPhysicalLayerRotationAndAspect() {
        val ink=com.halo.sdk.runtime.ink.InkCodec.encode(com.halo.sdk.runtime.ink.InkDocument(aspectMilli=2000))
        val layer=StudioLayer(id="ink",name="Ink",kind=StudioLayerKind.INK,ink=ink,rotation=90,bounds=StudioRect(100,100,200,200))
        val document=StudioDocument(2000,1000,layers=listOf(layer))
        val point=StudioInkCoordinates.toProject(document,layer,200f,800f)
        val back=requireNotNull(StudioInkCoordinates.toInk(document,layer,point.first,point.second))
        assertEquals(200f,back.first,.01f);assertEquals(800f,back.second,.01f)
    }
    @Test fun nonSquareHitTestingAndGroupRotationUsePhysicalPixels() {
        val shape=StudioLayer(id="s",name="Shape",kind=StudioLayerKind.RECTANGLE,bounds=StudioRect(100,100,200,200),rotation=90)
        val document=StudioDocument(2000,1000,layers=listOf(shape))
        assertEquals("s",StudioCanvasGeometry.hit(document,200f,350f)?.id)
        val group=StudioLayer(id="g",name="Group",kind=StudioLayerKind.GROUP)
        val grouped=StudioDocument(2000,1000,layers=listOf(group,shape.copy(bounds=StudioRect(100,100,100,100),rotation=0,parentId="g")))
        val rotated=grouped.apply(StudioCommand.Replace(group.copy(rotation=90)))
        assertEquals(StudioRect(625,-250,100,100),rotated.layers.single {it.id=="s"}.bounds)
    }
    @Test fun mirrorIsAppliedBeforeGlobalQuarterTurnLikeTheBitmapOwner() {
        val document=StudioDocument(recipe=CanvasImageRecipe(quarterTurns=1,mirrorHorizontal=true))
        val point=StudioCanvasGeometry.forward(document,100f,200f)
        assertEquals(.8f,point.first,.001f);assertEquals(.9f,point.second,.001f)
    }
    @Test fun cropRotationAndMirrorMappingRoundTrip() {
        for(turns in 0..3) for(mirror in listOf(false,true)) {
            val document=StudioDocument(1200,800,recipe=CanvasImageRecipe(quarterTurns=turns,mirrorHorizontal=mirror,
                crop=CanvasImageCrop(100,200,800,600)))
            val screen=StudioCanvasGeometry.forward(document,350f,400f)
            val restored=StudioCanvasGeometry.inverse(document,screen.first,screen.second)
            assertEquals(350f,restored.first,.01f);assertEquals(400f,restored.second,.01f)
        }
    }
    @Test fun hitTestingHonoursVisibilityRotationAndHierarchy() {
        val shape=StudioLayer(id="s",name="Shape",kind=StudioLayerKind.RECTANGLE,bounds=StudioRect(100,100,300,200))
        val document=StudioDocument(layers=listOf(shape))
        assertEquals("s",StudioCanvasGeometry.hit(document,200f,200f)?.id)
        assertNull(StudioCanvasGeometry.hit(document,800f,800f))
        val grouped=document.apply(StudioCommand.Group(listOf("s"),"Group","g"))
        val group=grouped.layers.single { it.id=="g" }
        assertNull(StudioCanvasGeometry.hit(grouped.apply(StudioCommand.Replace(group.copy(visible=false))),200f,200f))
    }
}
