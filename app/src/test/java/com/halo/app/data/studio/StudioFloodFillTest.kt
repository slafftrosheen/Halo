package com.halo.app.data.studio

import android.graphics.*
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[33])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class StudioFloodFillTest {
    @Test fun fillFollowsConnectedPixelsAndLeavesOriginalAndOtherRegionsUntouched()=runTest {
        val source=Bitmap.createBitmap(5,3,Bitmap.Config.ARGB_8888).apply {eraseColor(Color.WHITE);for(y in 0..2) setPixel(2,y,Color.BLACK)}
        val mask=StudioFloodFill.mask(source,0,1,Color.RED,0)
        try {
            assertEquals(Color.RED,mask.getPixel(1,1));assertEquals(Color.TRANSPARENT,mask.getPixel(2,1));assertEquals(Color.TRANSPARENT,mask.getPixel(4,1))
            assertEquals(Color.WHITE,source.getPixel(1,1));assertEquals(Color.BLACK,source.getPixel(2,1))
        } finally {source.recycle();mask.recycle()}
    }
    @Test fun toleranceIncludesOnlyMatchingNeighbourColoursAndAlpha()=runTest {
        val source=Bitmap.createBitmap(3,1,Bitmap.Config.ARGB_8888).apply {setPixel(0,0,Color.rgb(100,100,100));setPixel(1,0,Color.rgb(105,105,105));setPixel(2,0,Color.TRANSPARENT)}
        val mask=StudioFloodFill.mask(source,0,0,Color.BLUE,8)
        try {assertEquals(Color.BLUE,mask.getPixel(1,0));assertEquals(Color.TRANSPARENT,mask.getPixel(2,0))} finally {source.recycle();mask.recycle()}
    }
}
