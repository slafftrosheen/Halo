package com.halo.app.data.studio

import android.graphics.Bitmap
import android.graphics.Color
import kotlinx.coroutines.ensureActive
import kotlin.coroutines.coroutineContext
import kotlin.math.roundToInt

/** PorterDuff MULTIPLY modulates alpha on API 26–28. Use source-over artistic multiply instead. */
internal object StudioBlendRenderer {
    suspend fun multiply(destination:Bitmap,source:Bitmap,layer:StudioLayer) {
        require(source.width.toLong()*source.height<=StudioEffectRenderer.MAX_EFFECT_PIXELS)
        val foreground=IntArray(source.width*source.height)
        source.getPixels(foreground,0,source.width,0,0,source.width,source.height)
        val row=IntArray(destination.width)
        for(y in 0 until destination.height) {
            coroutineContext.ensureActive()
            destination.getPixels(row,0,row.size,0,y,row.size,1)
            val sy=y.toLong()*source.height/destination.height
            for(x in row.indices) {
                val s=foreground[(sy*source.width+x.toLong()*source.width/destination.width).toInt()]
                val sa=Color.alpha(s)/255.0*layer.opacity/1000
                if(sa==0.0) continue
                val d=row[x];val da=Color.alpha(d)/255.0;val alpha=sa+da-sa*da
                val luminance=.213*Color.red(s)+.715*Color.green(s)+.072*Color.blue(s)
                fun mix(sc:Int,dc:Int):Int {
                    val tone=((luminance+(sc-luminance)*layer.saturation/1000)*layer.contrast/1000+
                        128*(1-layer.contrast/1000.0)+layer.brightness*255/1000.0).coerceIn(0.0,255.0)/255
                    val back=dc/255.0
                    return ((tone*sa*(1-da)+back*da*(1-sa)+tone*back*sa*da)/alpha*255).roundToInt().coerceIn(0,255)
                }
                row[x]=Color.argb((alpha*255).roundToInt(),mix(Color.red(s),Color.red(d)),mix(Color.green(s),Color.green(d)),mix(Color.blue(s),Color.blue(d)))
            }
            destination.setPixels(row,0,row.size,0,y,row.size,1)
        }
    }
}
