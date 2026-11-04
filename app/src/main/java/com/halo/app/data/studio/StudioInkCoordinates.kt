package com.halo.app.data.studio

import com.halo.sdk.runtime.ink.InkCodec

data class StudioInkFrame(val x:Float,val y:Float,val width:Float,val height:Float)

/** Portable Ink coordinates use their fitted unrotated layer frame; composition uses project pixels. */
object StudioInkCoordinates {
    fun frame(document:StudioDocument,layer:StudioLayer):StudioInkFrame {
        val bounds=layer.bounds;val ink=requireNotNull(InkCodec.decode(layer.ink))
        if(ink.aspectMilli==0) return StudioInkFrame(bounds.x.toFloat(),bounds.y.toFloat(),bounds.width.toFloat(),bounds.height.toFloat())
        val width=bounds.width*document.width/1000f;val height=bounds.height*document.height/1000f
        val fitted=minOf(width,height*ink.aspectMilli/1000f)
        val w=fitted*1000/document.width;val h=(fitted*1000/ink.aspectMilli)*1000/document.height
        return StudioInkFrame(bounds.x+(bounds.width-w)/2,bounds.y+(bounds.height-h)/2,w,h)
    }
    fun toInk(document:StudioDocument,layer:StudioLayer,x:Float,y:Float):Pair<Float,Float>? {
        if(!document.isVisible(layer)||!document.isEditable(layer)) return null
        for(owner in listOf(layer)+document.ancestors(layer)) {
            val b=owner.bounds;val p=StudioCanvasGeometry.rotate(document,x,y,b.x+b.width/2f,b.y+b.height/2f,-owner.rotation.toFloat())
            owner.mask?.let {mask->
                if(p.first<b.x+mask.x*b.width/1000f||p.first>b.x+(mask.x+mask.width)*b.width/1000f||
                    p.second<b.y+mask.y*b.height/1000f||p.second>b.y+(mask.y+mask.height)*b.height/1000f) return null
            }
        }
        val b=layer.bounds;val p=StudioCanvasGeometry.rotate(document,x,y,b.x+b.width/2f,b.y+b.height/2f,-layer.rotation.toFloat())
        val frame=frame(document,layer)
        val u=(p.first-frame.x)*1000/frame.width;val v=(p.second-frame.y)*1000/frame.height
        return if(u in 0f..1000f&&v in 0f..1000f) u to v else null
    }
    fun toProject(document:StudioDocument,layer:StudioLayer,x:Float,y:Float):Pair<Float,Float> {
        val f=frame(document,layer);val b=layer.bounds
        return StudioCanvasGeometry.rotate(document,f.x+x*f.width/1000,f.y+y*f.height/1000,b.x+b.width/2f,b.y+b.height/2f,layer.rotation.toFloat())
    }
}
