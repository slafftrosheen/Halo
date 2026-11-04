package com.halo.app.data.studio

import kotlin.math.*

/** Shared project↔render mapping; viewport zoom/rotation is separate and never stored as an edit. */
object StudioCanvasGeometry {
    fun rotate(document:StudioDocument,x:Float,y:Float,cx:Float,cy:Float,degrees:Float):Pair<Float,Float> {
        val dx=(x-cx)*document.width/1000.0;val dy=(y-cy)*document.height/1000.0
        val a=Math.toRadians(degrees.toDouble())
        return (cx+(dx*cos(a)-dy*sin(a))*1000/document.width).toFloat() to
            (cy+(dx*sin(a)+dy*cos(a))*1000/document.height).toFloat()
    }
    fun resizeCorner(document:StudioDocument,layer:StudioLayer,x:Float,y:Float):StudioRect {
        val b=layer.bounds
        val anchor=rotate(document,b.x.toFloat(),b.y.toFloat(),b.x+b.width/2f,b.y+b.height/2f,layer.rotation.toFloat())
        val local=rotate(document,x,y,anchor.first,anchor.second,-layer.rotation.toFloat())
        val width=(local.first-anchor.first).roundToInt().coerceIn(1,3000)
        val height=(local.second-anchor.second).roundToInt().coerceIn(1,3000)
        val center=rotate(document,anchor.first+width/2f,anchor.second+height/2f,anchor.first,anchor.second,layer.rotation.toFloat())
        return StudioRect((center.first-width/2f).roundToInt().coerceIn(-1000,2000),(center.second-height/2f).roundToInt().coerceIn(-1000,2000),width,height)
    }
    private fun crop(document:StudioDocument):FloatArray {
        document.recipe.crop?.let { return floatArrayOf(it.x.toFloat(),it.y.toFloat(),it.width.toFloat(),it.height.toFloat()) }
        if(document.recipe.squareCrop) {
            val side=minOf(document.width,document.height).toFloat()
            val w=side/document.width*1000;val h=side/document.height*1000
            return floatArrayOf((1000-w)/2,(1000-h)/2,w,h)
        }
        return floatArrayOf(0f,0f,1000f,1000f)
    }
    fun forward(document:StudioDocument,x:Float,y:Float):Pair<Float,Float> {
        val c=crop(document);val raw=(x-c[0])/c[2];val u=if(document.recipe.mirrorHorizontal) 1-raw else raw;val v=(y-c[1])/c[3]
        val rotated=when(document.recipe.quarterTurns) { 1->1-v to u;2->1-u to 1-v;3->v to 1-u;else->u to v }
        return rotated
    }
    fun inverse(document:StudioDocument,x:Float,y:Float):Pair<Float,Float> {
        val u=x;val v=y
        val rotated=when(document.recipe.quarterTurns) { 1->v to 1-u;2->1-u to 1-v;3->1-v to u;else->u to v }
        val c=crop(document);return c[0]+(if(document.recipe.mirrorHorizontal) 1-rotated.first else rotated.first)*c[2] to c[1]+rotated.second*c[3]
    }
    fun hit(document:StudioDocument,x:Float,y:Float):StudioLayer? {
        fun ordered(parent:String?):List<StudioLayer> = document.layers.filter { it.parentId==parent }.flatMap { listOf(it)+ordered(it.id) }
        return ordered(null).asReversed().firstOrNull { layer ->
            if(!document.isVisible(layer) || layer.opacity==0 || layer.kind==StudioLayerKind.GROUP) false else {
                val b=layer.bounds;val point=rotate(document,x,y,b.x+b.width/2f,b.y+b.height/2f,-layer.rotation.toFloat())
                point.first>=b.x && point.first<=b.x+b.width && point.second>=b.y && point.second<=b.y+b.height
            }
        }
    }
    fun snapped(document:StudioDocument,layer:StudioLayer,x:Int,y:Int):StudioRect {
        val peers=document.layers.filter { it.id!=layer.id && it.parentId==layer.parentId && document.isVisible(it) }
        val xs=listOf(0,500,1000)+peers.flatMap { listOf(it.bounds.x,it.bounds.x+it.bounds.width/2,it.bounds.x+it.bounds.width) }
        val ys=listOf(0,500,1000)+peers.flatMap { listOf(it.bounds.y,it.bounds.y+it.bounds.height/2,it.bounds.y+it.bounds.height) }
        fun snap(value:Int,size:Int,guides:List<Int>):Int {
            val delta=listOf(0,size/2,size).flatMap { anchor->guides.map { it-(value+anchor) } }.minBy { abs(it) }
            return (value+if(abs(delta)<=10) delta else 0).coerceIn(-1000,2000)
        }
        return layer.bounds.copy(x=snap(x,layer.bounds.width,xs),y=snap(y,layer.bounds.height,ys))
    }
}
