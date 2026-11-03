package com.halo.app.data.studio

import android.graphics.*
import kotlin.math.roundToInt

/** Bounded procedural materials; no downloaded/licensed assets or unbounded full-resolution noise. */
object StudioTextureRenderer {
    fun draw(canvas: Canvas, bounds: RectF, texture: StudioTexture, color: Int, seed: Int) {
        val width = bounds.width().roundToInt().coerceIn(1,256)
        val height = bounds.height().roundToInt().coerceIn(1,256)
        val pixels = IntArray(width*height)
        for (y in 0 until height) for (x in 0 until width) {
            val noise = noise(seed,x,y)
            val value = when (texture) {
                StudioTexture.PAPER -> noise/12
                StudioTexture.CANVAS -> (if (x%4 == 0 || y%4 == 0) 30 else -10) + noise/20
                StudioTexture.CONCRETE -> noise/3 + noise(seed,x/5,y/5)/8
                StudioTexture.FABRIC -> (if ((x/3+y/3)%2 == 0) 22 else -22) + noise/16
                StudioTexture.FILM_GRAIN -> noise/2
                StudioTexture.DUST -> if (noise > 113) 100 else -10
                StudioTexture.SCRATCHES -> if (noise(seed,x/2,0) > 90 && y%17 < 13) 90 else noise/20
                StudioTexture.NOISE -> noise
            }
            fun channel(v: Int) = (v+value).coerceIn(0,255)
            pixels[y*width+x] = Color.argb(Color.alpha(color),channel(Color.red(color)),channel(Color.green(color)),channel(Color.blue(color)))
        }
        val bitmap = Bitmap.createBitmap(pixels,width,height,Bitmap.Config.ARGB_8888)
        try { canvas.drawBitmap(bitmap,null,bounds,Paint(Paint.FILTER_BITMAP_FLAG)) } finally { bitmap.recycle() }
    }
    internal fun noise(seed: Int, x: Int, y: Int): Int {
        var value = seed xor (x*374761393) xor (y*668265263)
        value = (value xor (value ushr 13))*1274126177
        return ((value xor (value ushr 16)) and 255)-128
    }
}
