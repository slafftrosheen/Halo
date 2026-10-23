package com.halo.app.nativeactions

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.lifecycleScope
import com.halo.app.di.ManualDependencyGraph
import com.halo.cognition.language.NativeDestination
import com.halo.app.di.tools.host.NativeDestinationTools
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/** Non-exported Activity Result owner; only an active canonical tool request can launch it. */
class NativeResultActivity : ComponentActivity() {
    private var launched = false
    private var photoPath: String? = null
    private val requestId get() = intent.getStringExtra("requestId").orEmpty()
    private val destination get() = NativeDestination.fromWire(intent.getStringExtra("destination").orEmpty())
    private val launcher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        lifecycleScope.launch {
            if (!NativeActivityRequests.active(requestId)) { finish(); return@launch }
            if (result.resultCode != Activity.RESULT_OK) {
                discardPendingPhoto()
                complete(false, "User cancelled; no completed effect was confirmed.")
                return@launch
            }
            val uris = buildList {
                result.data?.data?.let(::add)
                result.data?.clipData?.let { clip -> repeat(clip.itemCount.coerceAtMost(16)) {
                    clip.getItemAt(it).uri?.let(::add)
                } }
            }.distinct().filter { it.scheme == "content" }
            val importedIds = mutableListOf<String>()
            try {
                if (destination == NativeDestination.PHOTO_CAPTURE) {
                    val file = photoPath?.let { java.io.File(it) } ?: error("No pending output")
                    val id = ManualDependencyGraph.captures.receive(file, sourceKind = "PHONE_CAMERA", publishForInference = false)
                    photoPath = null // Ownership transferred to Inbox; never discard the imported photo.
                    complete(true, "Full-size photo saved to Halo Inbox; objectId=$id")
                    return@launch
                }
                if (uris.isEmpty()) { complete(false, "Handler returned no accessible content URI."); return@launch }
                when (destination) {
                    NativeDestination.CONTACT_PICKER -> {
                        val token = java.util.UUID.randomUUID().toString()
                        getSharedPreferences("native_selected_targets", MODE_PRIVATE).edit()
                            .putString(token, uris.single().toString()).apply()
                        complete(true, "Contact selected; targetHandle=$token. No contact was edited.")
                    }
                    NativeDestination.FOLDER_PICKER -> {
                        val flags = (result.data?.flags ?: 0) and
                            (Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                        require(flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0)
                        contentResolver.takePersistableUriPermission(uris.single(), flags)
                        val token = java.util.UUID.randomUUID().toString()
                        getSharedPreferences("native_selected_targets", MODE_PRIVATE).edit()
                            .putString(token, uris.single().toString()).apply()
                        complete(true, "Folder grant acquired; targetHandle=$token. No files were changed.")
                    }
                    else -> {
                        ManualDependencyGraph.init(applicationContext)
                        for (uri in uris) {
                            if (!NativeActivityRequests.active(requestId)) { finish(); return@launch }
                            importedIds += ManualDependencyGraph.workspaceUseCases.importForCanvas(uri).objectId.value
                        }
                        complete(true, "Saved to Halo Inbox; objectIds=${importedIds.joinToString(",")}")
                    }
                }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { complete(false, if (importedIds.isEmpty())
                "Returned content could not be saved or granted."
                else "Import partially completed; saved objectIds=${importedIds.joinToString(",")}; remaining content was not saved. Do not repeat the whole import.") }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        launched = savedInstanceState?.getBoolean("launched") ?: false
        photoPath = savedInstanceState?.getString("photoPath")
        if (!NativeActivityRequests.active(requestId)) { finish(); return }
        setContentView(TextView(this).apply { text = nativeUi("Complete or cancel the Android picker to return to Halo."); setPadding(32,32,32,32) })
        if (!launched) {
            launched = true // Never replay a launch if recreation interrupts preparation.
            lifecycleScope.launch {
                try {
                    val target = destination?.let { NativeDestinationTools(this@NativeResultActivity).intentFor(it) }
                    if (target == null) { complete(false, "Unsupported result destination."); return@launch }
                    if (destination == NativeDestination.PHOTO_CAPTURE) {
                        ManualDependencyGraph.init(applicationContext)
                        val capture = ManualDependencyGraph.captures.preparePhoneCapture()
                        photoPath = capture.file.absolutePath
                        target.putExtra(android.provider.MediaStore.EXTRA_OUTPUT, capture.uri)
                        target.clipData = android.content.ClipData.newRawUri("Halo photo output", capture.uri)
                        target.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION or Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    launched = true
                    launcher.launch(target)
                } catch (_: android.content.ActivityNotFoundException) { complete(false, "No handler is available.") }
                catch (_: SecurityException) { complete(false, "Android denied this action.") }
            }
        }
    }
    override fun onSaveInstanceState(outState: Bundle) { outState.putBoolean("launched", launched); outState.putString("photoPath", photoPath); super.onSaveInstanceState(outState) }
    override fun onDestroy() {
        if (isFinishing && NativeActivityRequests.active(requestId)) complete(false, "Action cancelled.")
        super.onDestroy()
    }
    private fun discardPendingPhoto() {
        val pendingPath = photoPath ?: return
        photoPath = null
        cleanupScope.launch {
            try { ManualDependencyGraph.captures.discardPendingCapture(java.io.File(pendingPath)) }
            catch (_: java.io.IOException) { android.util.Log.w("HaloNativeCapture", "Pending capture cleanup failed") }
        }
    }
    private companion object {
        // File cleanup must outlive the finishing Activity's cancelled lifecycle scope.
        val cleanupScope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.IO)
    }
    private fun complete(ok: Boolean, details: String) {
        NativeActivityRequests.complete(requestId, NativeActivityRequests.Receipt(ok, details))
        android.widget.Toast.makeText(this, details, android.widget.Toast.LENGTH_LONG).show()
        finish()
    }
}
