package com.halo.wear

import android.app.Activity
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.view.HapticFeedbackConstants
import android.widget.*
import com.google.android.gms.wearable.*
import org.json.JSONObject

/** Lifecycle-bound, transient interaction surface. The phone validates every response. */
class MainActivity : Activity(), MessageClient.OnMessageReceivedListener {
    private lateinit var layout: LinearLayout
    private var phone: String? = null
    private var lastHapticId: String? = null
    private var lastSurface: String? = null
    private var interactive = false
    private val foreground = Handler(Looper.getMainLooper())
    private val expireSurface = Runnable { showIdle(R.string.wear_expired) }
    private val presence = object : Runnable {
        override fun run() {
            if (!interactive) return
            if (screenReady()) phone?.let { node ->
                Wearable.getMessageClient(this@MainActivity).sendMessage(node, "/halo/ready", byteArrayOf())
                    .addOnFailureListener { if (interactive && phone == node) unavailable() }
            }
            foreground.postDelayed(this, 15_000)
        }
    }
    private fun screenReady() = interactive && getSystemService(PowerManager::class.java)?.isInteractive == true &&
        getSystemService(android.app.KeyguardManager::class.java)?.isDeviceLocked != true
    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        layout = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(24, 24, 24, 24) }
        setContentView(ScrollView(this).apply { addView(layout) })
        unavailable()
    }
    override fun onStart() {
        super.onStart()
        Wearable.getMessageClient(this).addListener(this)
        Wearable.getCapabilityClient(this).getCapability("halo_phone_endpoint", CapabilityClient.FILTER_REACHABLE)
            .addOnSuccessListener { capability ->
                phone = capability.nodes.firstOrNull { it.isNearby }?.id
                if (phone != null) {
                    foreground.removeCallbacks(presence)
                    foreground.post(presence)
                } else unavailable()
            }.addOnFailureListener { unavailable() }
    }
    override fun onResume() {
        super.onResume()
        interactive = true
        foreground.removeCallbacks(presence)
        foreground.post(presence)
    }
    override fun onPause() {
        interactive = false
        foreground.removeCallbacks(presence)
        unavailable()
        super.onPause()
    }
    override fun onStop() { Wearable.getMessageClient(this).removeListener(this); phone = null; unavailable(); super.onStop() }
    private fun unavailable() = showIdle(R.string.wear_phone_unavailable)
    private fun showIdle(label: Int) {
        foreground.removeCallbacks(expireSurface)
        lastSurface = null
        layout.removeAllViews()
        text(getString(label))
    }
    private fun text(value: String) { layout.addView(TextView(this).apply { text = value }) }
    override fun onMessageReceived(event: MessageEvent) {
        if (event.path != "/halo/surface" || event.data.size > 12_000) return
        Wearable.getNodeClient(this).connectedNodes.addOnSuccessListener { nodes ->
            if (!screenReady() || nodes.none { it.id == event.sourceNodeId && it.isNearby } || event.sourceNodeId != phone) return@addOnSuccessListener
            val payload = event.data.toString(Charsets.UTF_8)
            val json = runCatching { JSONObject(payload) }.getOrNull() ?: return@addOnSuccessListener
            if (json.optInt("version") != 1) return@addOnSuccessListener
            runOnUiThread {
                if (!screenReady() || event.sourceNodeId != phone || payload == lastSurface) return@runOnUiThread
                foreground.removeCallbacks(expireSurface)
                lastSurface = payload
                layout.removeAllViews()
                text(getString(R.string.wear_connected))
                if (!json.has("id")) {
                    text(getString(R.string.wear_no_interaction))
                    return@runOnUiThread
                }
                val remaining = json.optLong("expiresAt", Long.MAX_VALUE) - System.currentTimeMillis()
                if (remaining <= 0L) {
                    showIdle(R.string.wear_expired)
                    return@runOnUiThread
                }
                foreground.postDelayed(expireSurface, remaining.coerceAtMost(60_000L))
                text(json.optString("title").take(160)); text(json.optString("prompt").take(1500))
                val id = json.getString("id")
                if (json.optString("kind") == "CONFIRMATION") {
                    if (json.optBoolean("canConfirm", false)) {
                        button(getString(R.string.wear_yes), id, "CONFIRM")
                        button(getString(R.string.wear_no), id, "DENY")
                    } else text(getString(R.string.wear_review_on_phone))
                }
                layout.addView(Button(this).apply { setText(R.string.wear_open_on_phone); setOnClickListener {
                    val node = phone ?: return@setOnClickListener
                    Wearable.getNodeClient(this@MainActivity).connectedNodes.addOnSuccessListener { nearby ->
                        if (screenReady() && nearby.any { it.id == node && it.isNearby }) Wearable.getMessageClient(this@MainActivity).sendMessage(node, "/halo/open", byteArrayOf())
                    }
                } })
                if (lastHapticId != id) { layout.performHapticFeedback(HapticFeedbackConstants.CONFIRM); lastHapticId = id }
            }
        }
    }
    private fun button(label: String, id: String, response: String) {
        layout.addView(Button(this).apply {
            text = label
            setOnClickListener {
                isEnabled = false
                sendResponse(id, response)
            }
        })
    }
    private fun sendResponse(id: String, response: String) {
        val node = phone ?: return
        Wearable.getNodeClient(this).connectedNodes.addOnSuccessListener { nodes ->
            if (!screenReady() || nodes.none { it.id == node && it.isNearby }) { unavailable(); return@addOnSuccessListener }
            val bytes = JSONObject().put("version", 1).put("id", id).put("response", response).toString().toByteArray(Charsets.UTF_8)
            if (bytes.size <= 4096) Wearable.getMessageClient(this).sendMessage(node, "/halo/response", bytes)
                .addOnFailureListener { unavailable() }
        }
    }
}
