package com.halo.app.data.studio

import android.graphics.*
import com.halo.app.data.workspace.CanvasImageFormat
import com.halo.app.data.workspace.CanvasImageTools
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext
import kotlin.math.roundToInt

internal data class StudioAiLayerPixels(val png:ByteArray,val bounds:StudioRect)

/** A proposal is in rendered artboard coordinates. Undo the global recipe before adding it as a layer. */
internal suspend fun placeStudioAiLayer(bytes:ByteArray,document:StudioDocument):StudioAiLayerPixels=withContext(Dispatchers.IO) {
    val dimensions=BitmapFactory.Options().apply { inJustDecodeBounds=true }
    BitmapFactory.decodeByteArray(bytes,0,bytes.size,dimensions)
    require(dimensions.outWidth in 1..16384&&dimensions.outHeight in 1..16384)
    val options=BitmapFactory.Options().apply {
        inSampleSize=1
        while(maxOf(dimensions.outWidth,dimensions.outHeight)/inSampleSize>2048) inSampleSize*=2
    }
    var owned:Bitmap?=requireNotNull(BitmapFactory.decodeByteArray(bytes,0,bytes.size,options))
    try {
        coroutineContext.ensureActive()
        val current=requireNotNull(owned)
        val matrix=Matrix().apply {
            if(document.recipe.quarterTurns!=0) postRotate(-document.recipe.quarterTurns*90f)
            if(document.recipe.mirrorHorizontal) postScale(-1f,1f)
        }
        val adjusted=Bitmap.createBitmap(current,0,0,current.width,current.height,matrix,true)
        if(adjusted!==current) { current.recycle();owned=adjusted }
        val crop=document.recipe.crop
        val bounds=if(crop!=null) StudioRect(crop.x,crop.y,crop.width,crop.height) else if(document.recipe.squareCrop) {
            val side=minOf(document.width,document.height).toFloat()
            val width=(side/document.width*1000).roundToInt().coerceAtLeast(1)
            val height=(side/document.height*1000).roundToInt().coerceAtLeast(1)
            StudioRect((1000-width)/2,(1000-height)/2,width,height)
        } else StudioRect()
        val png=CanvasImageTools.encodeBitmap(requireNotNull(owned),CanvasImageFormat.PNG)
        coroutineContext.ensureActive()
        StudioAiLayerPixels(png,bounds)
    } finally { owned?.recycle() }
}
