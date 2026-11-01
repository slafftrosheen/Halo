package com.halo.app.ui.workspace

import android.app.Activity
import android.app.Application
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.result.ActivityResultRegistry
import androidx.activity.result.contract.ActivityResultContract
import androidx.core.app.ActivityOptionsCompat
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class WorkspaceExternalExportHostTest {
    private class Registry : ActivityResultRegistry() {
        val codes = mutableListOf<Int>()
        override fun <I, O> onLaunch(requestCode: Int, contract: ActivityResultContract<I, O>, input: I,
            options: ActivityOptionsCompat?) { codes += requestCode }
    }
    @Test fun delayedRestoredPickerCannotBeRelabelledAsTheNewLaunch() {
        val first = Registry()
        val old = registerWorkspaceExportPicker(first, "A") { error("Old host should be gone") }
        old.launch(Intent(Intent.ACTION_CREATE_DOCUMENT))
        val codeA = first.codes.single()
        val saved = Bundle(); first.onSaveInstanceState(saved); old.unregister()
        val restored = Registry(); restored.onRestoreInstanceState(saved)
        val received = mutableListOf<Pair<String, String?>>()
        val next = registerWorkspaceExportPicker(restored, "B") { received += "B" to it.data?.data?.toString() }
        next.launch(Intent(Intent.ACTION_CREATE_DOCUMENT))
        // A arrives before its restored registration, while B is active.
        restored.dispatchResult(codeA, Activity.RESULT_OK, Intent().setData(Uri.parse("content://docs/A")))
        assertTrue(received.isEmpty())
        val previous = registerWorkspaceExportPicker(restored, "A") { received += "A" to it.data?.data?.toString() }
        assertEquals(listOf("A" to "content://docs/A"), received)
        restored.dispatchResult(restored.codes.single(), Activity.RESULT_OK, Intent().setData(Uri.parse("content://docs/B")))
        assertEquals(listOf("A" to "content://docs/A", "B" to "content://docs/B"), received)
        previous.unregister(); next.unregister()
    }
    @Test fun outsideOpenExcludesHaloAndRechecksAuthorityBeforeDispatch() {
        val context = org.robolectric.Robolectric.buildActivity(Activity::class.java).setup().get()
        val uri = Uri.parse("content://docs/output")
        val view = Intent(Intent.ACTION_VIEW).setDataAndType(uri, "text/plain")
        val pm = Shadows.shadowOf(context.packageManager)
        fun register(pkg: String) {
            pm.addResolveInfoForIntent(view, android.content.pm.ResolveInfo().apply {
                activityInfo = android.content.pm.ActivityInfo().apply {
                    packageName = pkg; name = "$pkg.Viewer"; exported = true
                    applicationInfo = android.content.pm.ApplicationInfo().apply { packageName = pkg }
                }
            })
        }
        register(context.packageName); register("test.external")
        var checks = 0
        assertTrue(runCatching { openExportOutsideHalo(context, uri, "text/plain", "output.txt") { ++checks < 2 } }.isFailure)
        assertNull(Shadows.shadowOf(context).nextStartedActivity)
        openExportOutsideHalo(context, uri, "text/plain", "output.txt") { true }
        val chooser = Shadows.shadowOf(context).nextStartedActivity
        assertEquals(Intent.ACTION_CHOOSER, chooser.action)
        val target = chooser.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)!!
        assertEquals("test.external", target.component!!.packageName)
        assertEquals(uri, target.data)
        assertTrue(target.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0)
        assertEquals(uri, target.clipData!!.getItemAt(0).uri)
    }
}
