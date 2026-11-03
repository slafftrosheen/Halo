package com.halo.app.data.studio

import android.graphics.Bitmap
import android.graphics.Color
import kotlinx.coroutines.ensureActive
import kotlin.coroutines.coroutineContext
import kotlin.math.abs

/** A connected raster selection becomes a separate transparent paint layer; source pixels never change. */
object StudioFloodFill {
    suspend fun mask(source:Bitmap,x:Int,y:Int,color:Int,tolerance:Int):Bitmap {
        val w=source.width;val h=source.height
        require(w.toLong()*h<=StudioEffectRenderer.MAX_EFFECT_PIXELS&&x in 0 until w&&y in 0 until h&&tolerance in 0..255&&Color.alpha(color)>0)
        val pixels=IntArray(w*h);source.getPixels(pixels,0,w,0,0,w,h)
        val target=pixels[y*w+x];val output=IntArray(w*h);val seen=BooleanArray(w*h);val queue=IntArray(w*h)
        fun matches(value:Int)=abs(Color.alpha(value)-Color.alpha(target))<=tolerance&&abs(Color.red(value)-Color.red(target))<=tolerance&&
            abs(Color.green(value)-Color.green(target))<=tolerance&&abs(Color.blue(value)-Color.blue(target))<=tolerance
        var head=0;var tail=1;queue[0]=y*w+x;seen[queue[0]]=true
        fun admit(index:Int) {if(!seen[index]) {seen[index]=true;if(matches(pixels[index])) queue[tail++]=index}}
        while(head<tail) {
            if(head%4096==0) coroutineContext.ensureActive()
            val index=queue[head++];output[index]=color;val px=index%w;val py=index/w
            if(px>0) admit(index-1);if(px<w-1) admit(index+1);if(py>0) admit(index-w);if(py<h-1) admit(index+w)
        }
        coroutineContext.ensureActive()
        return Bitmap.createBitmap(output,w,h,Bitmap.Config.ARGB_8888)
    }
}
