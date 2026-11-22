package com.halo.app.nativeactions

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.core.content.ContextCompat
import com.halo.app.R
import com.halo.app.everyday.presentation.EverydayCardKind

/** Tool glyphs mark the instrument, independent of the Halo action or status ring. */
internal object EverydayWidgetGlyphs {
    fun resource(kind: EverydayCardKind): Int = when (kind) {
        EverydayCardKind.SCAN -> R.drawable.halo_ic_camera
        EverydayCardKind.RECORD -> R.drawable.halo_ic_mic
        EverydayCardKind.QUICK_NOTE -> R.drawable.halo_ic_notebook
        EverydayCardKind.TRANSLATE -> R.drawable.halo_ic_language
        EverydayCardKind.AIR_POLLEN, EverydayCardKind.LOCK_RAIN, EverydayCardKind.WEATHER,
        EverydayCardKind.CLOCK_WEATHER -> R.drawable.halo_ic_cloud
        EverydayCardKind.PLANNER_WEEK, EverydayCardKind.LOCK_NEXT_EVENT,
        EverydayCardKind.UPCOMING, EverydayCardKind.AGENDA, EverydayCardKind.DASHBOARD, EverydayCardKind.TODAY -> R.drawable.halo_ic_calendar
        EverydayCardKind.FOCUS, EverydayCardKind.CLOCK, EverydayCardKind.ALARMS_TIMERS -> R.drawable.halo_ic_timer
        EverydayCardKind.LITTLE_HABITS, EverydayCardKind.TASKS, EverydayCardKind.REMINDERS -> R.drawable.halo_ic_check
        EverydayCardKind.NEXT_RIDE -> R.drawable.halo_ic_connection
        EverydayCardKind.OUR_HOME, EverydayCardKind.SMART_STACK -> R.drawable.halo_ic_widget
        EverydayCardKind.LEARN, EverydayCardKind.KIDS_SKY -> R.drawable.halo_ic_study
        EverydayCardKind.YOUR_THINGS, EverydayCardKind.DEVICE -> R.drawable.halo_ic_devices
        else -> R.drawable.halo_ic_read_mode
    }

    fun bitmap(context: Context, resource: Int, sizeDp: Int, tint: Int): Bitmap {
        val size = (context.resources.displayMetrics.density * sizeDp).toInt().coerceAtLeast(1)
        return Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888).also { image ->
            ContextCompat.getDrawable(context, resource)?.mutate()?.apply {
                setTint(tint)
                setBounds(0, 0, size, size)
                draw(Canvas(image))
            }
        }
    }
}
