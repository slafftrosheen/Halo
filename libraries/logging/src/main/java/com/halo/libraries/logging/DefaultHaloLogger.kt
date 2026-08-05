package com.halo.libraries.logging

import com.halo.sdk.runtime.HaloLogger
import android.util.Log

class DefaultHaloLogger : HaloLogger {
    override fun trace(category: String, message: String) {
        Log.v("HALO-$category", message)
    }

    override fun debug(category: String, message: String) {
        Log.d("HALO-$category", message)
    }

    override fun info(category: String, message: String) {
        Log.i("HALO-$category", message)
    }

    override fun warn(category: String, message: String) {
        Log.w("HALO-$category", message)
    }

    override fun error(category: String, message: String, throwable: Throwable?) {
        if (throwable != null) {
            Log.e("HALO-$category", message, throwable)
        } else {
            Log.e("HALO-$category", message)
        }
    }
}
