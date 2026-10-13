package com.halo.app.nativeactions

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts

/** Permission grants never place a call: the user must press Call in a fresh review dialog. */
class NativeDirectCallActivity : ComponentActivity() {
    private val requestId get() = intent.getStringExtra("requestId").orEmpty()
    private val number get() = intent.getStringExtra("phoneNumber").orEmpty()
    private val permission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted && NativeActivityRequests.active(requestId)) review()
        else complete("Call permission denied or request cancelled. No call requested.")
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!NativeActivityRequests.active(requestId) || !number.matches(Regex("\\+?[0-9]{3,20}"))) { finish(); return }
        review()
    }
    private fun review() {
        android.app.AlertDialog.Builder(this).setTitle(nativeUi("Call %1\$s?", number))
            .setMessage(nativeUi("Review the exact number. Android may require phone permission. A permission grant alone does not place a call."))
            .setNegativeButton(android.R.string.cancel) { _, _ -> complete("Call cancelled. No call requested.") }
            .setPositiveButton(nativeUi("Call")) { _, _ ->
                if (!NativeActivityRequests.active(requestId)) { finish(); return@setPositiveButton }
                if (checkSelfPermission(Manifest.permission.CALL_PHONE) != PackageManager.PERMISSION_GRANTED) {
                    permission.launch(Manifest.permission.CALL_PHONE)
                    return@setPositiveButton
                }
                try {
                    startActivity(Intent(Intent.ACTION_CALL, Uri.parse("tel:$number")))
                    complete("Call intent dispatched after user review; actual call state not verified.")
                } catch (_: SecurityException) { complete("Android denied the call. No call completion claimed.") }
                catch (_: android.content.ActivityNotFoundException) { complete("No call handler available.") }
            }.setOnCancelListener { complete("Call cancelled.") }.show()
    }
    private fun complete(message: String) {
        NativeActivityRequests.complete(requestId, NativeActivityRequests.Receipt(false, message))
        android.widget.Toast.makeText(this, message, android.widget.Toast.LENGTH_LONG).show()
        finish()
    }
}
