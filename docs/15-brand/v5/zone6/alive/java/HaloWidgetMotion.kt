package com.halo.app.widget

/*
 * Widget motion (pack 7.2). Only what RemoteViews can really play:
 *   ViewFlipper / AdapterViewFlipper transitions on update, TextClock, animated-vector drawables,
 *   and the Android 12+ launch animation (android:id/background on the root — already in 6.2 layouts).
 * Updates only on real events. Motion level picks the layout variant (…_still has no in/out animations).
 * Reference code, not compiled against 98aa2fb.
 */

import android.appwidget.AppWidgetManager
import android.content.Context
import android.widget.RemoteViews
import com.halo.app.ui.theme.HaloMotion

object HaloWidgetMotion {
    /** Tasks: a task was ticked → show the "done" child (filled circle + strike), then the next list state. */
    fun taskDone(ctx: Context, mgr: AppWidgetManager, id: Int, motion: HaloMotion, build: (RemoteViews) -> Unit) {
        val layout = if (motion == HaloMotion.STILL) R.layout.halo_widget_tasks_still else R.layout.halo_widget_tasks_motion
        val rv = RemoteViews(ctx.packageName, layout)
        build(rv)
        // flipper children: 0 = current list, 1 = list with the ticked row struck, 2 = list after the row folded away
        rv.setDisplayedChild(R.id.halo_tasks_flipper, 1)
        mgr.partiallyUpdateAppWidget(id, rv)
        // the next state is posted by the widget's own update after 600 ms via a one-shot WorkManager / Handler in the provider
    }

    /** Your day / Weather: new data → flip to the next child; the flipper's inAnimation plays the slide or the fade. */
    fun newData(ctx: Context, mgr: AppWidgetManager, id: Int, flipperId: Int, child: Int) {
        val rv = RemoteViews(ctx.packageName, R.layout.halo_widget_day_motion)
        rv.setDisplayedChild(flipperId, child)
        mgr.partiallyUpdateAppWidget(id, rv)
    }

    /** Learn: 5 pre-built ring states (0/5 … 5/5) — one segment per finished step. */
    private val learnRings = intArrayOf(R.drawable.halo_learn_ring_0, R.drawable.halo_learn_ring_1, R.drawable.halo_learn_ring_2,
        R.drawable.halo_learn_ring_3, R.drawable.halo_learn_ring_4, R.drawable.halo_learn_ring_5)
    fun learnStep(ctx: Context, mgr: AppWidgetManager, id: Int, done: Int) {
        val rv = RemoteViews(ctx.packageName, R.layout.halo_widget_learn)
        rv.setImageViewResource(R.id.halo_learn_ring, learnRings[done.coerceIn(0, 5)])
        mgr.partiallyUpdateAppWidget(id, rv)
    }

    /** Ask halo: breathing ring only at FULL; GENTLE/STILL use the static ring drawable. */
    fun askRing(motion: HaloMotion) = if (motion == HaloMotion.FULL) R.drawable.halo_ring_breathe else R.drawable.halo_ring_static
}
