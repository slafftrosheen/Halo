package com.halo.wear

import android.app.Activity
import android.os.Bundle
import android.view.HapticFeedbackConstants
import android.widget.*
import com.google.android.gms.wearable.*
import org.json.JSONObject

/** Lifecycle-bound, transient interaction surface. The phone validates every response. */
class MainActivity : Activity(), MessageClient.OnMessageReceivedListener {
    private lateinit var layout: LinearLayout
    private var phone: String? = null
    private var lastHapticId: String? = null
    private var interactive = false
    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        layout = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(24, 24, 24, 24) }
        setContentView(ScrollView(this).apply { addView(layout) })
        unavailable()
    }
    override fun onStart() {
        super.onStart()
        interactive = true
        Wearable.getMessageClient(this).addListener(this)
        Wearable.getCapabilityClient(this).getCapability("halo_phone_endpoint", CapabilityClient.FILTER_REACHABLE)
            .addOnSuccessListener { capability ->
                if (!interactive) return@addOnSuccessListener
                phone = capability.nodes.firstOrNull { it.isNearby }?.id
                if (phone != null) Wearable.getMessageClient(this).sendMessage(phone!!, "/halo/ready", byteArrayOf()) else unavailable()
            }.addOnFailureListener { unavailable() }
    }
    override fun onStop() { interactive = false; Wearable.getMessageClient(this).removeListener(this); phone = null; unavailable(); super.onStop() }
    private fun unavailable() { layout.removeAllViews(); text("Halo phone unavailable nearby") }
    private fun text(value: String) { layout.addView(TextView(this).apply { text = value }) }
    override fun onMessageReceived(event: MessageEvent) {
        if (event.path != "/halo/surface" || event.data.size > 12_000) return
        Wearable.getNodeClient(this).connectedNodes.addOnSuccessListener { nodes ->
            if (!interactive || nodes.none { it.id == event.sourceNodeId && it.isNearby } || event.sourceNodeId != phone) return@addOnSuccessListener
            val json = runCatching { JSONObject(event.data.toString(Charsets.UTF_8)) }.getOrNull() ?: return@addOnSuccessListener
            if (json.optInt("version") != 1) return@addOnSuccessListener
            runOnUiThread {
                layout.removeAllViews(); text("Halo · Connected to phone")
                if (!json.has("id")) { text("No pending interaction"); return@runOnUiThread }
                val expiry = json.optLong("expiresAt", Long.MAX_VALUE)
                if (expiry <= System.currentTimeMillis()) { text("Interaction expired"); return@runOnUiThread }
                text(json.optString("title").take(160)); text(json.optString("prompt").take(1500))
                val id = json.getString("id")
                if (json.optString("kind") == "CONFIRMATION") { if (json.optBoolean("canConfirm", false)) button("Yes", id, "CONFIRM")
                    else text("Review this confirmation on your phone.")
                    button("No", id, "DENY") }
                val options = json.optJSONArray("options")
                if (options != null) for (i in 0 until minOf(options.length(), 8)) {
                    val option = options.getJSONObject(i); button(option.getString("label").take(160), id, "SELECT", option.getString("id"))
                }
                if (json.optString("kind") in listOf("CONFIRMATION", "CHOICE")) button("Cancel", id, "CANCEL")
                if (json.optString("kind") in listOf("TEXT_INPUT", "CLARIFICATION")) {
                    val answer = EditText(this).apply { filters = arrayOf(android.text.InputFilter.LengthFilter(800)); hint = "Your answer" }
                    layout.addView(answer)
                    layout.addView(Button(this).apply { text = "Submit"; setOnClickListener { sendResponse(id, "TEXT", value = answer.text.toString()) } })
                }
                layout.addView(Button(this).apply { text = "Open on phone"; setOnClickListener {
                    val node = phone ?: return@setOnClickListener
                    Wearable.getNodeClient(this@MainActivity).connectedNodes.addOnSuccessListener { nearby ->
                        if (interactive && nearby.any { it.id == node && it.isNearby }) Wearable.getMessageClient(this@MainActivity).sendMessage(node, "/halo/open", byteArrayOf())
                    }
                } })
                if (lastHapticId != id) { layout.performHapticFeedback(HapticFeedbackConstants.CONFIRM); lastHapticId = id }
            }
        }
    }
    private fun button(label: String, id: String, response: String, option: String? = null) {
        layout.addView(Button(this).apply {
            text = label
            setOnClickListener {
                isEnabled = false
                sendResponse(id, response, option)
            }
        })
    }
    private fun sendResponse(id: String, response: String, option: String? = null, value: String? = null) {
        val node = phone ?: return
        Wearable.getNodeClient(this).connectedNodes.addOnSuccessListener { nodes ->
            if (!interactive || nodes.none { it.id == node && it.isNearby }) { unavailable(); return@addOnSuccessListener }
            val bytes = JSONObject().put("version", 1).put("id", id).put("response", response).put("option", option)
                .put("value", value?.take(800)).toString().toByteArray(Charsets.UTF_8)
            if (bytes.size <= 4096) Wearable.getMessageClient(this).sendMessage(node, "/halo/response", bytes)
                .addOnFailureListener { unavailable() }
        }
    }
}
