package com.halo.app.ui.mini

import android.os.Bundle
import com.halo.app.MainActivity
import com.halo.app.ui.shell.HaloScreenCatalog

/** Same runtime bootstrap and permission/result owners, in a bounded native dialog window. */
class MiniHaloActivity : MainActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        if (savedInstanceState == null) intent.putExtra(HaloScreenCatalog.EXTRA_SCREEN, "mini_halo")
        super.onCreate(savedInstanceState)
    }

    override fun onStart() {
        super.onStart()
        MiniHaloHandoff.started(this)
    }

    override fun onStop() {
        MiniHaloHandoff.stopped(this)
        super.onStop()
    }
}

/** Receiving Main can adopt a visible Mini even when navigation came from a typed tool. */
internal object MiniHaloHandoff {
    private var source = java.lang.ref.WeakReference<MiniHaloActivity>(null)
    fun started(host: MiniHaloActivity) { source = java.lang.ref.WeakReference(host) }
    fun stopped(host: MiniHaloActivity) { if (source.get() === host) source.clear() }

    fun adopt(screen: String?) {
        val host = source.get() ?: return
        if (!canAdoptMiniSpeechHandoff(host.lifecycle.currentState, host.isFinishing, screen)) return
        source.clear()
        host.speechHandoff.launchAndFinish({}, { host.finish() }, {
            com.halo.app.di.ManualDependencyGraph.homeViewModel.cancelVoiceInput()
        })
    }
}

internal fun canAdoptMiniSpeechHandoff(state: androidx.lifecycle.Lifecycle.State, finishing: Boolean, screen: String?): Boolean =
    state.isAtLeast(androidx.lifecycle.Lifecycle.State.STARTED) && !finishing && screen != null &&
        screen != "mini_halo" && screen in HaloScreenCatalog.screens

/** AppLanguageProvider wraps the host Context; the native window owner is underneath it. */
internal tailrec fun miniHaloActivityOwner(context: android.content.Context): android.app.Activity? = when (context) {
    is android.app.Activity -> context
    is android.content.ContextWrapper -> if (context.baseContext === context) null else miniHaloActivityOwner(context.baseContext)
    else -> null
}
