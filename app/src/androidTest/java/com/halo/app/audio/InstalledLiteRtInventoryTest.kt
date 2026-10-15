package com.halo.app.audio

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.halo.app.di.ManualDependencyGraph
import com.halo.providers.litert.LiteRtLocalProvider
import com.halo.providers.litert.models.*
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Explicit, read-only fingerprint inventory. No ModelInfo, Engine, downloads or selection. */
@RunWith(AndroidJUnit4::class)
class InstalledLiteRtInventoryTest {
    @Test fun exportVerifiedInventory() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val manager = ManualDependencyGraph.providerGraph.modelManager
        manager.refresh(ModelInventoryRefreshReason.USER_REQUEST)
        val initial = manager.inventoryState.value as? LocalModelInventoryState.Ready
            ?: error("Installed inventory is unavailable")
        val root = File(context.noBackupFilesDir, "models")
        val initialFiles = root.listFiles().orEmpty().map { it.name }.sorted()
        val rows = JSONArray()
        initial.models.sortedBy { it.id }.forEach { initialDescriptor ->
            val verified = manager.verifyPhysicalArtifact(initialDescriptor.id)
            val current = (manager.inventoryState.value as? LocalModelInventoryState.Ready)
                ?.models?.firstOrNull { it.id == initialDescriptor.id }
            val descriptor = verified ?: current ?: initialDescriptor
            val catalog = manager.getAllCatalogModels().firstOrNull { it.id == descriptor.id }
            val unsafe = LiteRtCommunityModelGuide.nonRunnableFingerprint(descriptor.sha256)
            rows.put(JSONObject()
                .put("id", descriptor.id).put("filename", File(descriptor.path).name)
                .put("sha256", descriptor.sha256).put("bytes", descriptor.bytes)
                .put("state", if (current == null && verified == null) "MISSING" else descriptor.validation.name)
                .put("verified", verified?.verified == true)
                .put("runnable", verified?.verified == true && !unsafe && catalog != null)
                .put("declaredTask", catalog?.task?.name ?: "UNKNOWN")
                .put("artifactVersion", catalog?.revision ?: JSONObject.NULL)
                .put("minimumRuntime", catalog?.minLiteRtLmVersion ?: JSONObject.NULL)
                .put("enabledModalities", JSONArray(buildList {
                    if (descriptor.supportsText) add("TEXT")
                    if (descriptor.supportsVision) add("VISION")
                    if (descriptor.supportsAudio) add("AUDIO")
                }))
                .put("detectedSupport", "NOT_INSPECTED")
                .put("knownNonRunnable", unsafe))
        }
        val representedFiles = initial.models.map { File(it.path).name }.toSet()
        root.listFiles().orEmpty().filter { it.name.endsWith(".litertlm") && it.name !in representedFiles }
            .sortedBy { it.name }.forEach { file ->
                rows.put(JSONObject().put("id", file.name.removeSuffix(".litertlm"))
                    .put("filename", file.name).put("sha256", JSONObject.NULL).put("bytes", file.length())
                    .put("state", "UNVERIFIED").put("verified", false).put("runnable", false)
                    .put("declaredTask", "UNKNOWN").put("artifactVersion", JSONObject.NULL)
                    .put("enabledModalities", JSONArray()).put("detectedSupport", "NOT_INSPECTED"))
            }
        val partials = root.listFiles().orEmpty().filter { it.name.endsWith(".litertlm.part") }
            .map { it.name }.sorted()
        val snapshot = JSONObject().put("schema", 1).put("status", "INVENTORY_VERIFIED")
            .put("package", context.packageName).put("runtime", LiteRtLocalProvider.RUNTIME_VERSION)
            .put("sourceStamp", InstrumentationRegistry.getArguments().getString("sourceStamp", "UNSTAMPED"))
            .put("capturedAtMs", System.currentTimeMillis()).put("models", rows)
            .put("partials", JSONArray(partials))
            .put("nativeInspection", "NOT_RUN")
        check(initialFiles == root.listFiles().orEmpty().map { it.name }.sorted()) {
            "Installed inventory changed during verification; no snapshot is admitted"
        }
        File(context.filesDir, "installed-litert-models.json").writeText(snapshot.toString())
        assertTrue(snapshot.has("models"))
    }
}
