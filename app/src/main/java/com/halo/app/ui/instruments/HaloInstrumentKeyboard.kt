package com.halo.app.ui.instruments

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.view.KeyEvent
import java.util.WeakHashMap

/** Screen-lifecycle registration for actions that can be invoked while a document has keyboard focus. */
internal object HaloInstrumentKeyboard {
    private data class Handler(val active: () -> Boolean, val invoke: (KeyEvent) -> Boolean, val openHelp: () -> Unit)
    private val owners = WeakHashMap<Activity, LinkedHashMap<Any, Handler>>()

    fun register(activity: Activity, active: () -> Boolean, handler: (KeyEvent) -> Boolean, openHelp: () -> Unit): () -> Unit {
        val token = Any()
        owners.getOrPut(activity, ::LinkedHashMap)[token] = Handler(active, handler, openHelp)
        return {
            owners[activity]?.let { handlers ->
                handlers.remove(token)
                if (handlers.isEmpty()) owners.remove(activity)
            }
        }
    }

    fun dispatch(activity: Activity, event: KeyEvent): Boolean {
        if (event.action != KeyEvent.ACTION_DOWN || event.repeatCount != 0 ||
            !(event.isCtrlPressed || event.isAltPressed || event.keyCode in KeyEvent.KEYCODE_F1..KeyEvent.KEYCODE_F12)) return false
        val handler = owners[activity]?.values?.lastOrNull { it.active() } ?: return false
        if (event.isCtrlPressed && !event.isAltPressed && event.keyCode == KeyEvent.KEYCODE_SLASH) {
            handler.openHelp()
            return true
        }
        return handler.invoke(event)
    }

    fun showHelp(activity: Activity): Boolean {
        val handler = owners[activity]?.values?.lastOrNull { it.active() } ?: return false
        handler.openHelp()
        return true
    }
}

internal tailrec fun Context.instrumentActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.takeUnless { it === this }?.instrumentActivity()
    else -> null
}
