package com.halo.app.di.tools

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.halo.app.di.tools.host.AssistantTools
import com.halo.app.di.tools.host.HostToolRegistry
import com.halo.app.ui.theme.*
import com.halo.runtime.cognition.agent.ActionTruth
import com.halo.runtime.cognition.tools.*
import com.halo.sdk.provider.ToolCall
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows

@RunWith(RobolectricTestRunner::class)
class AssistantToolsTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    @Before fun reset() {
        context.getSharedPreferences("halo_assistant_preferences", Context.MODE_PRIVATE).edit().clear().commit()
        context.getSharedPreferences("halo_appearance", Context.MODE_PRIVATE).edit().clear().commit()
    }
    private fun tools(foreground: Boolean = true, read: suspend (String, String) -> Unit = { _, _ -> }, stop: () -> Unit = {},
        profiles: com.halo.app.di.tools.host.ModeProfileTools? = null,
        currentScreen: com.halo.app.di.tools.host.CurrentScreenTools? = null) =
        AssistantTools(context, AppearancePreferences(context), AssistantPreferences(context), read, stop, { foreground }, modeProfiles = profiles, currentScreen = currentScreen)
    private fun call(name: String, args: Map<String, Any> = emptyMap()) = ToolCall("test", name, args)

    @Test fun `configuration mutations require foreground grown-up admission before any owner write`() = runBlocking {
        val denied = AssistantTools(context, AppearancePreferences(context), AssistantPreferences(context), { _, _ -> }, {},
            isForeground = { true }, canConfigure = { false })
        listOf("halo_set_preferences", "halo_edit_mode_profile").forEach { name ->
            val result = denied.execute(call(name, mapOf("language" to "pl"))) as ToolExecutionResult.Rejected
            assertEquals(ToolRejectionKind.PERMISSION_REQUIRED, result.kind)
        }
        assertTrue(denied.execute(call("halo_get_preferences")) is ToolExecutionResult.Success)
        assertTrue(tools(foreground = false).execute(call("halo_set_preferences", mapOf("fontScale" to 2.0))) is ToolExecutionResult.Rejected)
    }

    @Test fun `navigation has versioned operability and dispatches every exact assistant destination`() = runBlocking {
        val executor = tools()
        val descriptors = DescriptorCatalog.descriptorsFor(HaloToolDescriptors.ExecutorKind.ASSISTANT, executor)
        val registry = ToolRegistry(listOf(executor), descriptors)
        assertEquals(emptyList<ToolOperabilityProblem>(), registry.operabilityReport(listOf(
            ToolFeatureOperability("interface-4-navigation", setOf("halo_list_screens", "halo_open_screen")))))
        assertEquals(ReplaySafety.NEVER_REPLAY, descriptors.single { it.name == "halo_open_screen" }.security.replaySafety)
        com.halo.cognition.language.NativeHaloScreenDestination.entries.forEach { destination ->
            val result = executor.execute(call("halo_open_screen", mapOf("screen" to destination.wireId))) as ToolExecutionResult.Success
            assertEquals(ActionTruth.DISPATCHED, result.actionTruth)
            assertTrue(result.output.contains("page render unconfirmed"))
            val intent = Shadows.shadowOf(context as android.app.Application).nextStartedActivity
            assertEquals(destination.wireId, intent.getStringExtra(com.halo.app.ui.shell.HaloScreenCatalog.EXTRA_SCREEN))
        }
    }

    @Test fun `settings persist and a new repository restores the same values`() = runBlocking {
        val executor = HostToolRegistry(listOf(tools()))
        val result = executor.execute(call("halo_set_preferences", mapOf("mode" to "dark", "fontScale" to 1.3, "startScreen" to "settings/voice/speech-lab")))
        assertEquals(ActionTruth.CONFIRMED, (result as ToolExecutionResult.Success).actionTruth)
        val restored = AssistantPreferences(context).get()
        assertEquals(1.3f, restored.fontScale)
        assertEquals("settings/voice/speech-lab", restored.startScreen)
        val read = tools().execute(call("halo_get_preferences")) as ToolExecutionResult.Success
        assertTrue(read.output.contains("mode=dark"))
    }
    @Test fun `invalid preferences do not partially change the theme`() = runBlocking {
        val executor = tools()
        val result = executor.execute(call("halo_set_preferences", mapOf("mode" to "dark", "fontScale" to 0.2)))
        assertTrue(result is ToolExecutionResult.Rejected)
        assertTrue((executor.execute(call("halo_get_preferences")) as ToolExecutionResult.Success).output.contains("mode=system"))
        assertTrue(executor.execute(call("halo_set_preferences", mapOf("fontScale" to Double.NaN))) is ToolExecutionResult.Rejected)
    }
    @Test fun `opening an internal page uses a bounded explicit intent and reports dispatch`() = runBlocking {
        val result = tools().execute(call("halo_open_screen", mapOf("screen" to "android_assistant"))) as ToolExecutionResult.Success
        assertEquals(ActionTruth.DISPATCHED, result.actionTruth)
        val intent = Shadows.shadowOf(context as android.app.Application).nextStartedActivity
        assertEquals("com.halo.app.MainActivity", intent.component?.className)
        assertEquals("android_assistant", intent.getStringExtra(com.halo.app.ui.shell.HaloScreenCatalog.EXTRA_SCREEN))
        assertTrue(tools().execute(call("halo_open_screen", mapOf("screen" to "notebook/note/private-id"))) is ToolExecutionResult.Rejected)
    }
    @Test fun `read and stop reach shared output and never claim playback complete`() = runBlocking {
        var spoken = ""
        var stopped = false
        val executor = tools(read = { text, _ -> spoken = text }, stop = { stopped = true })
        val read = executor.execute(call("halo_read_aloud", mapOf("text" to "Labdien"))) as ToolExecutionResult.Success
        assertEquals("Labdien", spoken)
        assertEquals(ActionTruth.DISPATCHED, read.actionTruth)
        executor.execute(call("halo_stop_reading")); assertTrue(stopped)
        val denied = tools(false, read = { _, _ -> fail("Background speech must not start") }).execute(call("halo_read_aloud", mapOf("text" to "hello")))
        assertEquals(ToolRejectionKind.PERMISSION_REQUIRED, (denied as ToolExecutionResult.Rejected).kind)
    }
    @Test fun `new tools have manifest discovery policy and exact descriptor ownership`() {
        val profiles = com.halo.app.di.tools.host.ModeProfileTools(com.halo.app.ai.PrivacyModeStore(MetadataStorage())) { _, _, _ ->
            error("Metadata review must not mutate a profile")
        }
        // Match the installed assistant owner set used by ManualDependencyGraph.
        // Discovery must not execute or read a current page.
        val screen = com.halo.app.di.tools.host.CurrentScreenTools(com.halo.app.context.CurrentScreenContext()) {
            error("Metadata review must not read a page")
        }
        val executor = tools(profiles = profiles, currentScreen = screen)
        val descriptors = DescriptorCatalog.descriptorsFor(HaloToolDescriptors.ExecutorKind.ASSISTANT, executor)
        assertEquals(executor.definitions().map { it.name }.toSet(), descriptors.map { it.name }.toSet())
        // Local allowlist must activate every new ability while keeping bootstrap bounded.
        com.halo.sdk.provider.ToolCallProtocol.entries.forEach { protocol ->
            val policy = LocalModelToolPolicy.forProtocol(protocol)
            assertTrue("Every assistant ability must be reachable through the actual discovery policy", executor.definitions().all { policy.mayExpose(it.name) })
            assertEquals(LocalModelToolPolicy.BOOTSTRAP, policy.allowedToolIds)
        }
        assertEquals(3, LocalModelToolPolicy.BOOTSTRAP.size)
        val skill = com.halo.app.di.BuiltInSkillManifestLoader(context).load().single { it.id.value == "halo.assistant" }
        assertEquals(executor.definitions().map { it.name }.toSet(), skill.tools)
        assertEquals(ReplaySafety.NEVER_REPLAY, descriptors.single { it.name == "halo_read_aloud" }.security.replaySafety)
    }

    private class MetadataStorage : com.halo.sdk.runtime.Storage {
        private val values = mutableMapOf<String, Any?>()
        override fun getString(key: String, defaultValue: String?) = values[key] as? String ?: defaultValue
        override fun putString(key: String, value: String?) { values[key] = value }
        override fun getInt(key: String, defaultValue: Int) = values[key] as? Int ?: defaultValue
        override fun putInt(key: String, value: Int) { values[key] = value }
        override fun getBoolean(key: String, defaultValue: Boolean) = values[key] as? Boolean ?: defaultValue
        override fun putBoolean(key: String, value: Boolean) { values[key] = value }
        override fun remove(key: String) { values.remove(key) }
        override fun clear() { values.clear() }
    }
}
