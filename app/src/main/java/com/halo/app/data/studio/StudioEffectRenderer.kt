package com.halo.app.data.studio

import android.graphics.Bitmap
import android.graphics.Color
import kotlinx.coroutines.ensureActive
import kotlin.coroutines.coroutineContext
import kotlin.math.*

/** Deterministic effect stack on a single owned layer bitmap, never on retained source bytes. */
object StudioEffectRenderer {
    const val MAX_EFFECT_PIXELS = 2_560_000
    suspend fun apply(bitmap: Bitmap, effects: List<StudioEffect>) {
        require(bitmap.width.toLong()*bitmap.height <= MAX_EFFECT_PIXELS && effects.size <= 8)
        effects.forEach(StudioEffect::requireValid)
        val width = bitmap.width; val height = bitmap.height
        var pixels = IntArray(width*height)
        bitmap.getPixels(pixels,0,width,0,0,width,height)
        for (effect in effects.filter { it.enabled }) {
            coroutineContext.ensureActive()
            pixels = applyEffect(pixels,width,height,effect)
        }
        coroutineContext.ensureActive()
        bitmap.setPixels(pixels,0,width,0,0,width,height)
    }
    private suspend fun applyEffect(source: IntArray, w: Int, h: Int, effect: StudioEffect): IntArray {
        val a = effect.amount/1000.0
        val intensity = abs(a)
        val radius = (effect.radius * maxOf(w,h)/640.0).roundToInt().coerceIn(1,64)
        val blurred = if (effect.kind in setOf(StudioEffectKind.BLUR,StudioEffectKind.SHARPEN,StudioEffectKind.GLOW,
            StudioEffectKind.BLOOM,StudioEffectKind.DROP_SHADOW,StudioEffectKind.INNER_SHADOW)) blur(source,w,h,radius) else null
        val output = IntArray(source.size)
        fun at(x: Int,y: Int) = source[y.coerceIn(0,h-1)*w+x.coerceIn(0,w-1)]
        for (y in 0 until h) {
            if (y%16 == 0) coroutineContext.ensureActive()
            for (x in 0 until w) {
                val index = y*w+x; val c = source[index]
                var alpha = Color.alpha(c).toDouble(); var r = Color.red(c).toDouble()
                var g = Color.green(c).toDouble(); var b = Color.blue(c).toDouble()
                val luma = .2126*r+.7152*g+.0722*b
                val nx = (x+.5)/w; val ny = (y+.5)/h
                fun mixColor(color: Int, weight: Double) {
                    val k = weight.coerceIn(0.0,1.0)
                    r += (Color.red(color)-r)*k; g += (Color.green(color)-g)*k; b += (Color.blue(color)-b)*k
                }
                when (effect.kind) {
                    StudioEffectKind.BRIGHTNESS -> { r+=a*255; g+=a*255; b+=a*255 }
                    StudioEffectKind.CONTRAST -> { val k=1+a; r=(r-128)*k+128; g=(g-128)*k+128; b=(b-128)*k+128 }
                    StudioEffectKind.EXPOSURE -> { val k=2.0.pow(a*3); r*=k; g*=k; b*=k }
                    StudioEffectKind.SATURATION -> { val k=1+a; r=luma+(r-luma)*k; g=luma+(g-luma)*k; b=luma+(b-luma)*k }
                    StudioEffectKind.TEMPERATURE -> { r+=a*70; b-=a*70 }
                    StudioEffectKind.TINT -> { r+=a*35; g-=a*60; b+=a*35 }
                    StudioEffectKind.HIGHLIGHTS -> { val k=(luma/255).pow(2)*a*160; r+=k; g+=k; b+=k }
                    StudioEffectKind.SHADOWS -> { val k=(1-luma/255).pow(2)*a*160; r+=k; g+=k; b+=k }
                    StudioEffectKind.GRAYSCALE -> { r+=(luma-r)*intensity; g+=(luma-g)*intensity; b+=(luma-b)*intensity }
                    StudioEffectKind.DUOTONE -> {
                        val t=luma/255
                        fun tone(low: Int, high: Int) = low+(high-low)*t
                        r+=(tone(Color.red(effect.secondaryColor),Color.red(effect.color))-r)*intensity
                        g+=(tone(Color.green(effect.secondaryColor),Color.green(effect.color))-g)*intensity
                        b+=(tone(Color.blue(effect.secondaryColor),Color.blue(effect.color))-b)*intensity
                    }
                    StudioEffectKind.BLUR -> {
                        val p=blurred!![index]; r+=(Color.red(p)-r)*intensity; g+=(Color.green(p)-g)*intensity
                        b+=(Color.blue(p)-b)*intensity; alpha+=(Color.alpha(p)-alpha)*intensity
                    }
                    StudioEffectKind.SHARPEN -> {
                        val p=blurred!![index]; r+=(r-Color.red(p))*intensity*2; g+=(g-Color.green(p))*intensity*2; b+=(b-Color.blue(p))*intensity*2
                    }
                    StudioEffectKind.GRAIN -> { val n=StudioTextureRenderer.noise(effect.seed,x,y)*intensity; r+=n;g+=n;b+=n }
                    StudioEffectKind.VIGNETTE -> { val k=1-intensity*((nx-.5).pow(2)+(ny-.5).pow(2))*2; r*=k;g*=k;b*=k }
                    StudioEffectKind.POSTERIZE -> {
                        val levels=(16-intensity*14).roundToInt().coerceIn(2,16)-1
                        fun q(v:Double) = (v/255*levels).roundToInt()*255.0/levels
                        r=q(r);g=q(g);b=q(b)
                    }
                    StudioEffectKind.PIXELATE -> {
                        val block=(2+intensity*30).roundToInt(); val p=at(x/block*block,y/block*block)
                        r=Color.red(p).toDouble();g=Color.green(p).toDouble();b=Color.blue(p).toDouble();alpha=Color.alpha(p).toDouble()
                    }
                    StudioEffectKind.EMBOSS,StudioEffectKind.EDGES -> {
                        val p=at(x-1,y-1); val q=at(x+1,y+1)
                        fun edge(left:Int,right:Int,own:Double):Double {
                            val v=if (effect.kind == StudioEffectKind.EMBOSS) 128.0+(right-left) else abs(right-left).toDouble()*2
                            return own+(v-own)*intensity
                        }
                        r=edge(Color.red(p),Color.red(q),r);g=edge(Color.green(p),Color.green(q),g);b=edge(Color.blue(p),Color.blue(q),b)
                    }
                    StudioEffectKind.GLOW,StudioEffectKind.BLOOM -> {
                        val p=blurred!![index]; val k=intensity*(if (effect.kind==StudioEffectKind.BLOOM) maxOf(Color.red(p),Color.green(p),Color.blue(p))/255.0 else 1.0)
                        r=255-(255-r)*(1-Color.red(p)/255.0*k);g=255-(255-g)*(1-Color.green(p)/255.0*k);b=255-(255-b)*(1-Color.blue(p)/255.0*k)
                        alpha=maxOf(alpha,Color.alpha(p)*k)
                    }
                    StudioEffectKind.DROP_SHADOW -> {
                        val shadow=Color.alpha(blurred!![(y-radius).coerceAtLeast(0)*w+(x-radius).coerceAtLeast(0)])/255.0*intensity
                        val front=alpha/255; val total=front+shadow*(1-front)
                        if(total>0) { r=(r*front+Color.red(effect.color)*shadow*(1-front))/total;g=(g*front+Color.green(effect.color)*shadow*(1-front))/total;b=(b*front+Color.blue(effect.color)*shadow*(1-front))/total }
                        alpha=total*255
                    }
                    StudioEffectKind.INNER_SHADOW -> {
                        val p=blurred!![(y-radius).coerceAtLeast(0)*w+(x-radius).coerceAtLeast(0)]
                        mixColor(effect.color,(1-Color.alpha(p)/255.0)*intensity)
                    }
                    StudioEffectKind.FLARE -> mixColor(effect.color, intensity*exp(-((nx-.25).pow(2)+(ny-.25).pow(2))*24))
                    StudioEffectKind.LIGHT_LEAK -> mixColor(effect.color,intensity*(1-nx).pow(3))
                    StudioEffectKind.HAZE -> mixColor(effect.color,intensity*.6)
                    StudioEffectKind.GRADIENT_LIGHT -> mixColor(effect.color,intensity*(1-ny))
                    StudioEffectKind.REFLECTION -> mixColor(effect.color,intensity*exp(-((ny-.25-nx*.25)*12).pow(2)))
                }
                output[index]=Color.argb(alpha.roundToInt().coerceIn(0,255),r.roundToInt().coerceIn(0,255),g.roundToInt().coerceIn(0,255),b.roundToInt().coerceIn(0,255))
            }
        }
        return output
    }
    /** Linear-time separable box blur in premultiplied alpha; transparent pixels cannot add colour halos. */
    private suspend fun blur(source:IntArray,w:Int,h:Int,radius:Int):IntArray {
        suspend fun pass(input:IntArray,horizontal:Boolean):IntArray {
            val result=IntArray(input.size);val lines=if(horizontal) h else w;val length=if(horizontal) w else h
            fun index(line:Int,p:Int)=if(horizontal) line*w+p else p*w+line
            for(line in 0 until lines) {
                if(line%16==0) coroutineContext.ensureActive()
                var a=0L;var r=0L;var g=0L;var b=0L
                fun add(p:Int,sign:Int) {
                    val c=input[index(line,p.coerceIn(0,length-1))];val alpha=Color.alpha(c)
                    a+=alpha*sign;r+=Color.red(c).toLong()*alpha*sign;g+=Color.green(c).toLong()*alpha*sign;b+=Color.blue(c).toLong()*alpha*sign
                }
                for(p in -radius..radius) add(p,1)
                val n=radius*2+1
                for(p in 0 until length) {
                    result[index(line,p)]=if(a==0L) 0 else Color.argb((a/n).toInt(),(r/a).toInt(),(g/a).toInt(),(b/a).toInt())
                    add(p-radius,-1);add(p+radius+1,1)
                }
            }
            return result
        }
        return pass(pass(source,true),false)
    }
}
