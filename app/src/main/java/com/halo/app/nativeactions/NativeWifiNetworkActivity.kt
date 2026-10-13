package com.halo.app.nativeactions

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.net.wifi.WifiNetworkSuggestion
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts

/** Credentials are entered locally, never tool arguments, saved state, logs, or model context. */
class NativeWifiNetworkActivity : ComponentActivity() {
    private val requestId get() = intent.getStringExtra("requestId").orEmpty()
    private var launched = false
    private val save = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (!NativeActivityRequests.active(requestId)) { finish(); return@registerForActivityResult }
        val statuses = result.data?.getIntegerArrayListExtra(Settings.EXTRA_WIFI_NETWORK_RESULT_LIST)
        val saved = result.resultCode == RESULT_OK && statuses?.singleOrNull() == Settings.ADD_WIFI_RESULT_SUCCESS
        val existing = result.resultCode == RESULT_OK && statuses?.singleOrNull() == Settings.ADD_WIFI_RESULT_ALREADY_EXISTS
        complete(saved || existing, when {
            saved -> "Android reports the Wi-Fi network saved. Connection is not verified."
            existing -> "Android reports the network already exists. Connection is not verified."
            else -> "Wi-Fi network save cancelled or failed. No saved network claimed."
        })
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE)
        if (android.os.Build.VERSION.SDK_INT < 30 || !NativeActivityRequests.active(requestId)) { finish(); return }
        launched = savedInstanceState?.getBoolean("launched") ?: false
        if (launched) return
        val name = android.widget.EditText(this).apply { hint = nativeUi("Network name (SSID)"); isSaveEnabled = false }
        val password = android.widget.EditText(this).apply {
            hint = nativeUi("Password"); isSaveEnabled = false
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
        }
        val security = android.widget.Spinner(this).apply {
            adapter = android.widget.ArrayAdapter(this@NativeWifiNetworkActivity, android.R.layout.simple_spinner_dropdown_item,
                listOf("WPA2", "WPA3", nativeUi("Open network")))
        }
        val form = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.VERTICAL; setPadding(24,16,24,16)
            addView(name); addView(security); addView(password)
        }
        val dialog = android.app.AlertDialog.Builder(this).setTitle(nativeUi("Add Wi-Fi network")).setView(form)
            .setNegativeButton(android.R.string.cancel) { _, _ -> complete(false, "Wi-Fi setup cancelled.") }
            .setPositiveButton(nativeUi("Review in Android"), null).setOnCancelListener { complete(false, "Wi-Fi setup cancelled.") }.create()
        dialog.setOnShowListener {
            dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                if (!NativeActivityRequests.active(requestId)) { finish(); return@setOnClickListener }
                try {
                    val ssid = name.text.toString()
                    require(ssid.toByteArray(Charsets.UTF_8).size in 1..32)
                    val builder = WifiNetworkSuggestion.Builder().setSsid(ssid)
                    when (security.selectedItemPosition) {
                        0 -> builder.setWpa2Passphrase(password.text.toString())
                        1 -> builder.setWpa3Passphrase(password.text.toString())
                    }
                    val suggestion = builder.build()
                    password.text.clear()
                    launched = true
                    save.launch(Intent(Settings.ACTION_WIFI_ADD_NETWORKS).putParcelableArrayListExtra(Settings.EXTRA_WIFI_NETWORK_LIST, arrayListOf(suggestion)))
                    dialog.dismiss()
                } catch (_: IllegalArgumentException) { name.error = nativeUi("Check network name, security, and password") }
                catch (_: android.content.ActivityNotFoundException) { complete(false, "Android has no supported Wi-Fi save handler.") }
                catch (_: SecurityException) { complete(false, "Android denied the Wi-Fi save request.") }
            }
        }
        dialog.show()
    }
    override fun onSaveInstanceState(outState: Bundle) { outState.putBoolean("launched", launched); super.onSaveInstanceState(outState) }
    private fun complete(ok: Boolean, details: String) {
        NativeActivityRequests.complete(requestId, NativeActivityRequests.Receipt(ok, details))
        android.widget.Toast.makeText(this, details, android.widget.Toast.LENGTH_LONG).show()
        finish()
    }
}
