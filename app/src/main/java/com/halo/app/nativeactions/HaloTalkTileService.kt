package com.halo.app.nativeactions

import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.halo.app.MainActivity

/** User-added entry point. The normal assistant session owns microphone permission and capture. */
class HaloTalkTileService : TileService() {
    override fun onStartListening() {
        super.onStartListening()
        qsTile?.apply { state = Tile.STATE_INACTIVE; updateTile() }
    }
    override fun onClick() {
        super.onClick()
        unlockAndRun {
            val target = Intent(this, MainActivity::class.java).setAction(Intent.ACTION_ASSIST)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            if (Build.VERSION.SDK_INT >= 34) startActivityAndCollapse(PendingIntent.getActivity(this, 187, target,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
            else {
                @Suppress("DEPRECATION")
                startActivityAndCollapse(target)
            }
        }
    }
}
