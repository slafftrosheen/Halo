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
