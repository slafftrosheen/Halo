package com.halo.app.controls

import android.content.Intent
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.halo.app.BuildConfig
import com.halo.app.di.tools.host.NativeDestinationTools
import com.halo.cognition.language.NativeDestination
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Read-only contract/handler inspection. Never launches another app or reads call history. */
@RunWith(AndroidJUnit4::class)
class Native187CapabilityInspectionTest {
    @Test fun inspectFixedDestinations() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val tools = NativeDestinationTools(context)
        val rows = JSONArray()
        NativeDestination.entries.forEach { destination ->
            val intent = tools.intentFor(destination)
            rows.put(JSONObject().put("destination", destination.wireId)
                .put("intentProvided", intent != null)
                .put("resolvedPackage", intent?.resolveActivity(context.packageManager)?.packageName ?: JSONObject.NULL)
                .put("actionLaunched", false))
        }
        val callLog = tools.intentFor(NativeDestination.CALL_LOG)!!
        assertEquals(android.provider.CallLog.Calls.CONTENT_URI, callLog.data)
        assertEquals(0, callLog.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION)
        File(context.filesDir, "native-187-capability-inspection.json").writeText(
            JSONObject().put("sourceId", BuildConfig.HALO_SOURCE_ID)
                .put("device", android.os.Build.MODEL)
                .put("sdk", android.os.Build.VERSION.SDK_INT)
                .put("stage", "HANDLER_INSPECTION_ONLY")
                .put("destinations", rows).toString(2))
    }
}
