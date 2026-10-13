package com.halo.app.nativeactions

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.print.PageRange
import android.print.PrintAttributes
import android.print.PrintDocumentAdapter
import android.print.PrintDocumentInfo
import android.print.PrintManager
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Closed export/print result owner, accessible only after canonical authorization. */
class NativeFileActionActivity : ComponentActivity() {
    private val requestId get() = intent.getStringExtra("requestId").orEmpty()
    private val source get() = Uri.parse(intent.getStringExtra("sourceUri"))
    private val title get() = intent.getStringExtra("title").orEmpty().take(160)
    private val export = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        lifecycleScope.launch {
            if (!NativeActivityRequests.active(requestId)) { finish(); return@launch }
            val destination = result.data?.data
            if (result.resultCode != Activity.RESULT_OK || destination?.scheme != "content") {
                complete(false, "Document creation cancelled; no export confirmed."); return@launch
            }
            try {
                require(destination != source) { "Source and destination must differ" }
                val bytes = withContext(Dispatchers.IO) {
                    contentResolver.openInputStream(source).use { input ->
                        requireNotNull(input)
                        contentResolver.openOutputStream(destination, "w").use { output ->
                            requireNotNull(output)
                            copyBounded(input, output) { NativeActivityRequests.active(requestId) }
                        }
                    }
                }
                complete(true, "Export write completed and stream closed; bytes=$bytes. Destination chosen by user.")
            } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
            catch (_: Exception) { complete(false, "Export could not complete. The chosen document may contain partial data; no completed export claimed.") }
        }
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!NativeActivityRequests.active(requestId) || source.scheme != "content") { finish(); return }
        if (savedInstanceState != null) return // Activity Result restores ownership; never re-launch.
        try {
            when (intent.getStringExtra("action")) {
                "export" -> export.launch(Intent(Intent.ACTION_CREATE_DOCUMENT).setType(intent.getStringExtra("mime"))
                    .addCategory(Intent.CATEGORY_OPENABLE).putExtra(Intent.EXTRA_TITLE, title))
                "print" -> openPrintUi()
                else -> complete(false, "Unknown file action")
            }
        } catch (_: android.content.ActivityNotFoundException) { complete(false, "No document creation handler") }
        catch (_: SecurityException) { complete(false, "Android denied file access") }
    }
    private fun openPrintUi() {
        require(intent.getStringExtra("mime") == "application/pdf") { "Choose an imported PDF for printing" }
        val manager = getSystemService(PrintManager::class.java)
        manager.print(title.ifBlank { "Halo PDF" }, object : PrintDocumentAdapter() {
            override fun onLayout(oldAttributes: PrintAttributes?, newAttributes: PrintAttributes?, cancellationSignal: CancellationSignal,
                callback: LayoutResultCallback, extras: Bundle?) {
                if (cancellationSignal.isCanceled || !NativeActivityRequests.active(requestId)) callback.onLayoutCancelled()
                else callback.onLayoutFinished(PrintDocumentInfo.Builder(title.ifBlank { "Halo PDF" })
                    .setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT).setPageCount(PrintDocumentInfo.PAGE_COUNT_UNKNOWN).build(), true)
            }
            override fun onWrite(pages: Array<out PageRange>, destination: ParcelFileDescriptor, cancellationSignal: CancellationSignal,
                callback: WriteResultCallback) {
                if (!pages.contains(PageRange.ALL_PAGES)) { callback.onWriteFailed("Selected page ranges are not supported for this PDF copy"); return }
                lifecycleScope.launch {
                    try {
                        withContext(Dispatchers.IO) {
                            contentResolver.openInputStream(source).use { input ->
                                requireNotNull(input)
                                java.io.FileOutputStream(destination.fileDescriptor).use { output ->
                                    copyBounded(input, output) { !cancellationSignal.isCanceled && NativeActivityRequests.active(requestId) }
                                }
                            }
                        }
                        callback.onWriteFinished(arrayOf(PageRange.ALL_PAGES))
                    } catch (cancelled: kotlinx.coroutines.CancellationException) { callback.onWriteCancelled(); throw cancelled }
                    catch (_: Exception) { callback.onWriteFailed("PDF could not be copied") }
                }
            }
            override fun onFinish() {
                complete(false, "Android print UI finished. A printed sheet or saved PDF was not verified.")
            }
        }, null)
    }
    private fun complete(ok: Boolean, details: String) {
        NativeActivityRequests.complete(requestId, NativeActivityRequests.Receipt(ok, details))
        android.widget.Toast.makeText(this, details, android.widget.Toast.LENGTH_LONG).show()
        finish()
    }
    override fun onDestroy() {
        if (isFinishing && NativeActivityRequests.active(requestId)) NativeActivityRequests.cancel(requestId)
        super.onDestroy()
    }
    companion object {
        internal fun copyBounded(input: java.io.InputStream, output: java.io.OutputStream, active: () -> Boolean): Long {
            val buffer = ByteArray(64 * 1024)
            var bytes = 0L
            while (true) {
                check(active()) { "Action stopped" }
                val count = input.read(buffer)
                if (count < 0) break
                bytes += count
                require(bytes <= 64L * 1024 * 1024) { "File exceeds export limit" }
                output.write(buffer, 0, count)
            }
            output.flush()
            return bytes
        }
    }
}
