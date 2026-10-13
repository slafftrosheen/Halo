package com.halo.app.nativeactions

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.lifecycleScope
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions
import com.google.mlkit.vision.documentscanner.GmsDocumentScanning
import com.google.mlkit.vision.documentscanner.GmsDocumentScanningResult
import com.halo.app.di.ManualDependencyGraph
import kotlinx.coroutines.launch

/** Google's published scanner contract; PDFs are copied into the existing local Inbox owner. */
class NativeDocumentScanActivity : ComponentActivity() {
    private val requestId get() = intent.getStringExtra("requestId").orEmpty()
    private val scanner = registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        lifecycleScope.launch {
            if (!NativeActivityRequests.active(requestId)) { finish(); return@launch }
            if (result.resultCode != RESULT_OK) { complete(false, "Document scan cancelled."); return@launch }
            val pdf = GmsDocumentScanningResult.fromActivityResultIntent(result.data)?.pdf
            if (pdf == null || pdf.uri.scheme != "content" || pdf.pageCount !in 1..12) {
                complete(false, "Scanner returned no supported PDF."); return@launch
            }
            try {
                ManualDependencyGraph.init(applicationContext)
                val file = ManualDependencyGraph.workspaceUseCases.importForCanvas(pdf.uri, "scan.pdf", "application/pdf")
                complete(true, "Scanned PDF saved to Halo Inbox; pages=${pdf.pageCount}; objectId=${file.objectId.value}")
            } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
            catch (_: Exception) { complete(false, "Scanned PDF could not be saved. No completed import claimed.") }
        }
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!NativeActivityRequests.active(requestId)) { finish(); return }
        if (savedInstanceState != null) return // Registered result callback owns an existing scan.
        val options = GmsDocumentScannerOptions.Builder().setGalleryImportAllowed(true).setPageLimit(12)
            .setResultFormats(GmsDocumentScannerOptions.RESULT_FORMAT_PDF)
            .setScannerMode(GmsDocumentScannerOptions.SCANNER_MODE_FULL).build()
        GmsDocumentScanning.getClient(options).getStartScanIntent(this)
            .addOnSuccessListener { sender ->
                if (!isDestroyed && NativeActivityRequests.active(requestId)) scanner.launch(IntentSenderRequest.Builder(sender).build())
            }.addOnFailureListener {
                complete(false, "Google document scanner unavailable. Google Play services may need its scanner module before first use.")
            }
    }
    private fun complete(ok: Boolean, details: String) {
        NativeActivityRequests.complete(requestId, NativeActivityRequests.Receipt(ok, details))
        android.widget.Toast.makeText(this, details, android.widget.Toast.LENGTH_LONG).show()
        finish()
    }
}
