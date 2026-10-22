package com.halo.app.lab.bridge

import android.app.Activity
import android.os.Bundle
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import com.halo.app.R

/** Reachable only from Halo's on-device model settings; never opened automatically by Lab. */
class HaloLabConsentActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val store = HaloLabConsentStore(this)
        val enabled = store.isEnabled()
        val padding = (24 * resources.displayMetrics.density).toInt()
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(padding, padding, padding, padding)
        }
        content.addView(TextView(this).apply {
            text = getString(R.string.halo_lab_consent_title)
            textSize = 22f
        })
        content.addView(TextView(this).apply {
            text = getString(R.string.halo_lab_consent_body)
            textSize = 16f
            setPadding(0, padding / 2, 0, padding)
        })
        if (enabled) {
            content.addView(Button(this).apply {
                text = getString(R.string.halo_lab_consent_disable)
                setOnClickListener {
                    store.setEnabled(false)
                    startService(android.content.Intent(this@HaloLabConsentActivity, HaloLabBridgeService::class.java)
                        .setAction(HaloLabBridgeService.ACTION_CONSENT_REVOKED))
                    finish()
                }
            })
        } else {
            content.addView(Button(this).apply {
                text = getString(R.string.halo_lab_consent_enable)
                setOnClickListener {
                    if (store.setEnabled(true)) finish()
                }
            })
        }
        content.addView(Button(this).apply {
            text = getString(R.string.halo_lab_consent_close)
            setOnClickListener { finish() }
        })
        setContentView(content)
    }
}
