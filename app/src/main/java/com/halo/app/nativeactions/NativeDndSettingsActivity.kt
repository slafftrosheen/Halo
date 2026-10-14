package com.halo.app.nativeactions

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import com.halo.app.di.ManualDependencyGraph

/** Android rule configuration entry. Opening it never activates or deactivates a rule. */
class NativeDndSettingsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ManualDependencyGraph.init(applicationContext)
        android.app.AlertDialog.Builder(this)
            .setTitle(nativeUi("Halo Do Not Disturb"))
            .setMessage(nativeUi("Halo changes only its own Do Not Disturb rule. Other rules may keep Do Not Disturb active. Manage access in Android, then make a fresh request in Halo."))
            .setNegativeButton(android.R.string.cancel) { _, _ -> finish() }
            .setPositiveButton(nativeUi("Review in Android")) { _, _ ->
                try { startActivity(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)) }
                catch (_: android.content.ActivityNotFoundException) { unavailable() }
                catch (_: SecurityException) { unavailable() }
                finish()
            }.setOnCancelListener { finish() }.show()
    }
    private fun unavailable() {
        android.widget.Toast.makeText(this, nativeUi("Android settings could not be opened."), android.widget.Toast.LENGTH_LONG).show()
    }
}
