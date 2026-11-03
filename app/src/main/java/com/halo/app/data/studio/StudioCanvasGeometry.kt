package com.halo.app.data.studio

import kotlin.math.*

/** Shared project↔render mapping; viewport zoom/rotation is separate and never stored as an edit. */
object StudioCanvasGeometry {
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
                val b=layer.bounds;val radians=Math.toRadians(-layer.rotation.toDouble())
                val dx=x-(b.x+b.width/2f);val dy=y-(b.y+b.height/2f)
                val px=dx*cos(radians)-dy*sin(radians)+b.x+b.width/2f
                val py=dx*sin(radians)+dy*cos(radians)+b.y+b.height/2f
                px>=b.x && px<=b.x+b.width && py>=b.y && py<=b.y+b.height
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
