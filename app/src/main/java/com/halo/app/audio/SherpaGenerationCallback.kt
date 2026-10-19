package com.halo.app.audio

import androidx.annotation.Keep

/** Sherpa JNI looks up invoke(float[]) returning boxed Integer; an indy lambda lacks that method. */
@Keep
internal class SherpaGenerationCallback(private val isActive: () -> Boolean) : (FloatArray) -> Int {
    override fun invoke(samples: FloatArray): Int = if (isActive()) 1 else 0
}
