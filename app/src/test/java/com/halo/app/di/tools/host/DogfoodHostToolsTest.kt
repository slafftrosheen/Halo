package com.halo.app.di.tools.host

import android.app.Application
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.ApplicationInfo
import android.content.pm.ResolveInfo
import androidx.test.core.app.ApplicationProvider
import com.halo.runtime.cognition.agent.ActionTruth
import com.halo.runtime.cognition.tools.*
import com.halo.sdk.provider.ToolCall
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class DogfoodHostToolsTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private fun call(args: Map<String, Any> = mapOf("package" to "com.test.player")) = ToolCall("call", "halo_open_app", args)
    private fun registerLaunchableApp() {
        val info = ResolveInfo().apply { activityInfo = ActivityInfo().apply {
            packageName = "com.test.player"; name = "com.test.player.Main"
            applicationInfo = ApplicationInfo().apply { packageName = "com.test.player"; nonLocalizedLabel = "Player" }
        } }
        val pm = Shadows.shadowOf(context.packageManager)
        pm.addResolveInfoForIntent(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), info)
        pm.addResolveInfoForIntent(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER).setPackage("com.test.player"), info)
    }
    @Test fun schemaAndBackgroundPolicyHaveDifferentTaxonomy() = runTest {
        val tools = ApplicationTools(context) { false }
        assertEquals(ToolRejectionKind.INVALID_ARGUMENTS, (tools.execute(call(emptyMap())) as ToolExecutionResult.Rejected).kind)
        assertEquals(ToolRejectionKind.INVALID_ARGUMENTS, (tools.execute(call(mapOf("package" to "bad"))) as ToolExecutionResult.Rejected).kind)
        assertEquals(ToolRejectionKind.UNSUPPORTED_OPERATION, (tools.execute(call()) as ToolExecutionResult.Rejected).kind)
    }
    @Test fun hostRegistryCarriesValidatedDirectAuthorityToBackgroundLaunch() = runTest {
        registerLaunchableApp()
        val registry = HostToolRegistry(listOf(ApplicationTools(context) { false }))
        val result = registry.execute(call(), ToolExecutionContext("interaction", directUserAuthority = true))
        assertTrue(result.toString(), result is ToolExecutionResult.Success)
        assertEquals(ActionTruth.DISPATCHED, (result as ToolExecutionResult.Success).actionTruth)
        assertNotNull(Shadows.shadowOf(context as Application).nextStartedActivity)
    }
    @Test fun foregroundNeedsNoBackgroundAuthority() = runTest {
        registerLaunchableApp()
        assertTrue(ApplicationTools(context) { true }.execute(call()) is ToolExecutionResult.Success)
    }
    @Test fun invalidMediaActionIsSchemaErrorBeforeSessionDiscovery() = runTest {
        val result = MediaTools(context).execute(ToolCall("call", "halo_media_control", mapOf("action" to "bad")))
        assertEquals(ToolRejectionKind.INVALID_ARGUMENTS, (result as ToolExecutionResult.Rejected).kind)
    }
    @Test fun missingSessionIsCapabilityUnavailable() = runTest {
        val result = MediaTools(context).execute(ToolCall("call", "halo_media_control", mapOf("action" to "pause")))
        assertEquals(ToolRejectionKind.CAPABILITY_UNAVAILABLE, (result as ToolExecutionResult.Rejected).kind)
    }
    @Test fun missingMediaPermissionAndDisappearingSessionHaveExplicitKinds() = runTest {
        val call = ToolCall("call", "halo_media_control", mapOf("action" to "pause"))
        val denied = MediaTools(context, activeSessions = { throw SecurityException("private platform details") }).execute(call)
        assertEquals(ToolRejectionKind.PERMISSION_REQUIRED, (denied as ToolExecutionResult.Rejected).kind)
        assertFalse(denied.reason.contains("private"))
        val gone = MediaTools(context, activeSessions = { throw android.os.DeadObjectException() }).execute(call)
        assertEquals(ToolRejectionKind.RESOURCE_UNAVAILABLE, (gone as ToolExecutionResult.Rejected).kind)
    }

    @Test fun launchPortsCoverMissingAmbiguousAndSecurityRefusal() = runTest {
        val absent = ApplicationTools(context, isForeground = { true }, resolvePackage = { null }, isLaunchable = { false })
        for (args in listOf(mapOf("appName" to "missing"), mapOf("appName" to "ambiguous"), mapOf("package" to "com.missing.app")))
            assertEquals(ToolRejectionKind.CAPABILITY_UNAVAILABLE, (absent.execute(call(args)) as ToolExecutionResult.Rejected).kind)
        val denied = ApplicationTools(context, isForeground = { true }, isLaunchable = { true }, launch = { throw SecurityException("private") })
        assertEquals(ToolRejectionKind.PERMISSION_REQUIRED, (denied.execute(call()) as ToolExecutionResult.Rejected).kind)
        for (foreground in listOf(true, false)) {
            var launches = 0
            val tools = ApplicationTools(context, isForeground = { foreground }, isLaunchable = { true }, launch = { launches++; com.halo.runtime.cognition.Outcome.Dispatched("sent") })
            val result = tools.execute(call(), ToolExecutionContext("interaction", !foreground)) as ToolExecutionResult.Success
            assertEquals(1, launches)
            assertEquals(ActionTruth.DISPATCHED, result.actionTruth)
        }
    }

}
