package com.halo.app.data.studio

import com.halo.app.data.workspace.*
import org.junit.Assert.*
import org.junit.Test

class StudioCanvasGeometryTest {
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
