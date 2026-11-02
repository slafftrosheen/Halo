package com.halo.app.ui.everyday

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import com.halo.app.MainActivity

/** A saved brief is a typed entry, never an arbitrary navigation string from an Intent. */
object EverydayBriefEntry {
    const val EXTRA_BRIEF_ID = "com.halo.app.extra.EVERYDAY_BRIEF_ID"
    private val idPattern = Regex("[a-f0-9]{64}")
    fun route(id: String?): String? = id?.takeIf { idPattern.matches(it) }?.let(EverydayRoutes::brief)
    fun acceptsRoute(route: String): Boolean = route.startsWith("${EverydayRoutes.BRIEF}?briefId=") &&
        idPattern.matches(route.removePrefix("${EverydayRoutes.BRIEF}?briefId="))
    fun intent(context: Context, id: String): Intent {
        require(route(id) != null)
        return Intent(context, MainActivity::class.java).putExtra(EXTRA_BRIEF_ID, id)
            .setData(Uri.Builder().scheme("halo").authority("saved-brief").appendPath(id).build())
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
    }
    fun pending(context: Context, id: String): PendingIntent = PendingIntent.getActivity(context, 8110,
        intent(context, id), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
}
