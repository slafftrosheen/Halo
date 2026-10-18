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
}

/** AppLanguageProvider wraps the host Context; the native window owner is underneath it. */
internal tailrec fun miniHaloActivityOwner(context: android.content.Context): android.app.Activity? = when (context) {
    is android.app.Activity -> context
    is android.content.ContextWrapper -> if (context.baseContext === context) null else miniHaloActivityOwner(context.baseContext)
    else -> null
}
