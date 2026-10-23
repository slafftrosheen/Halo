package com.halo.app.ui.shell

import android.content.Context
import android.content.Intent
import com.halo.app.MainActivity

/** Exact local screen presentation. Successful dispatch never claims a rendered or observed page. */
object HaloScreenLauncher {
    fun dispatch(context: Context, screen: String) {
        require(screen in HaloScreenCatalog.screens)
        context.startActivity(Intent(context, MainActivity::class.java).putExtra(HaloScreenCatalog.EXTRA_SCREEN, screen)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP))
    }
}
