package com.halo.app.di.tools

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.halo.app.data.notebook.WorkspaceObjectId
import com.halo.app.data.workspace.WorkspaceFile
import com.halo.app.data.workspace.WorkspaceFileRepository
import com.halo.app.data.notebook.WorkspaceObject
import com.halo.app.data.workspace.WorkspaceQuery
import com.halo.app.data.workspace.WorkspaceRepository
import com.halo.app.di.tools.host.ClipboardTools
import com.halo.app.di.tools.host.DeviceInsightTools
import com.halo.app.di.tools.host.IntegrityTools
import com.halo.app.di.tools.host.WebInsightTools
import com.halo.runtime.cognition.tools.ConfirmationPolicy
import com.halo.runtime.cognition.tools.ReplaySafety
import com.halo.runtime.cognition.tools.ToolCategory
import com.halo.runtime.cognition.tools.ToolDescriptor
import com.halo.runtime.cognition.tools.ToolRegistry
import com.halo.runtime.cognition.tools.ToolRiskClass
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * RUNTIME-056 regression tests for host descriptor dispatch.
 *
 * The bug these pin: every executor kind used to fall through to a default descriptor, so
 * `ToolRegistry`'s first-wins catalogue stored whichever kind appeared first in the
 * composition root for *every* host tool. Android settings, volume, media, alarms,
 * contacts and the whole accessibility family were catalogued as `NAVIGATION` +
 * `EXTERNAL_SIDE_EFFECT` + `NEVER_REPLAY`, and a descriptor that declares
 * `ConfirmationPolicy.ALWAYS` lost its mandatory confirmation to a user policy that may
 * be `ALWAYS_ALLOW`.
 *
 * The wiring below intentionally mirrors `ManualDependencyGraph`: one host executor
 * registered under every host kind, so a kind that stops claiming its own tools fails here.
 */
@RunWith(RobolectricTestRunner::class)
class HostToolDescriptorDispatchTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    private val hostExecutor = HostTools(
        context,
        { true },
        listOf(
            com.halo.app.di.tools.host.AssistantTools(context,
                com.halo.app.ui.theme.AppearancePreferences(context), com.halo.app.ui.theme.AssistantPreferences(context),
                { _, _ -> }, {}, { true }),
            DeviceInsightTools(context),
            ClipboardTools(context),
            IntegrityTools(context, NoFiles, NoWorkspace),
            WebInsightTools(hostResolver = { emptyList() }),
        ),
    )

    /**
     * Exactly the kinds `ManualDependencyGraph` binds to the host executor. The single-tool
     * kinds (current time, storage, notifications, use-skill) are deliberately absent: in
     * production each of those is bound to its own executor, and the host executor cannot
     * describe them.
     */
    private val hostKinds = listOf(
        HaloToolDescriptors.ExecutorKind.NAVIGATION,
        HaloToolDescriptors.ExecutorKind.SYSTEM,
        HaloToolDescriptors.ExecutorKind.ASSISTANT,
        HaloToolDescriptors.ExecutorKind.APPLICATION,
        HaloToolDescriptors.ExecutorKind.DEVICE,
        HaloToolDescriptors.ExecutorKind.DEVICE_INSIGHT,
        HaloToolDescriptors.ExecutorKind.CLIPBOARD,
        HaloToolDescriptors.ExecutorKind.WEB_INSIGHT,
        HaloToolDescriptors.ExecutorKind.INTEGRITY,
        HaloToolDescriptors.ExecutorKind.ALARM,
        HaloToolDescriptors.ExecutorKind.CALENDAR,
        HaloToolDescriptors.ExecutorKind.NOTIFICATION_REPLY,
        HaloToolDescriptors.ExecutorKind.APP_SEMANTIC,
        HaloToolDescriptors.ExecutorKind.COMMUNICATION,
        HaloToolDescriptors.ExecutorKind.MEDIA,
        HaloToolDescriptors.ExecutorKind.ACCESSIBILITY,
    )

    private val registry = ToolRegistry(
        listOf(hostExecutor),
        descriptors = DescriptorCatalog.buildDescriptors(
            *hostKinds.map { it to hostExecutor }.toTypedArray(),
        ),
    )

    /**
     * Names each kind is permitted to describe. A kind may describe fewer (device-dependent
     * definitions such as flashlight or web search are absent on a bare test device) but never
     * a name from another family.
     */
    private val allowedNames = mapOf(
        HaloToolDescriptors.ExecutorKind.CALENDAR to setOf("halo_calendar_upcoming", "halo_calendar_create", "halo_calendar_editor"),
        HaloToolDescriptors.ExecutorKind.NOTIFICATION_REPLY to setOf("halo_list_notifications", "halo_dismiss_notification", "halo_open_notification"),
        HaloToolDescriptors.ExecutorKind.NAVIGATION to setOf("halo_navigation", "halo_browser", "halo_search"),
        HaloToolDescriptors.ExecutorKind.ASSISTANT to setOf("halo_list_screens", "halo_open_screen", "halo_get_preferences", "halo_set_preferences", "halo_read_aloud", "halo_stop_reading", "halo_list_app_actions", "halo_perform_app_action"),
        HaloToolDescriptors.ExecutorKind.SYSTEM to setOf(
            "halo_open_settings", "halo_share_text", "halo_native_destination", "halo_native_result",
            "halo_native_target", "halo_native_observation", "halo_native_shortcut", "halo_native_global",
            "halo_native_map", "halo_google_mail",
        ),
        HaloToolDescriptors.ExecutorKind.APPLICATION to setOf("halo_list_apps", "halo_open_app"),
        HaloToolDescriptors.ExecutorKind.DEVICE to setOf("halo_volume", "halo_flashlight", "halo_ringer", "halo_brightness"),
        HaloToolDescriptors.ExecutorKind.DEVICE_INSIGHT to setOf("halo_device_info", "halo_battery_status", "halo_network_status"),
        HaloToolDescriptors.ExecutorKind.CLIPBOARD to setOf("halo_clipboard_read", "halo_clipboard_write"),
        HaloToolDescriptors.ExecutorKind.WEB_INSIGHT to setOf("halo_fetch_url"),
        HaloToolDescriptors.ExecutorKind.INTEGRITY to setOf("halo_checksum"),
        HaloToolDescriptors.ExecutorKind.ALARM to setOf("halo_set_alarm", "halo_timer", "halo_clock_view", "halo_clock_modify"),
        HaloToolDescriptors.ExecutorKind.APP_SEMANTIC to setOf("halo_current_app_capabilities", "halo_current_app_action"),
        HaloToolDescriptors.ExecutorKind.COMMUNICATION to setOf("halo_find_contact", "halo_dial_contact", "halo_contact_editor", "halo_compose_sms", "halo_compose_email", "halo_compose_message"),
        HaloToolDescriptors.ExecutorKind.MEDIA to setOf("halo_media_status", "halo_media_control"),
        HaloToolDescriptors.ExecutorKind.FOLLOWUP to setOf("halo_create_watch", "halo_list_watches", "halo_cancel_watch"),
        HaloToolDescriptors.ExecutorKind.ACCESSIBILITY to setOf(
            "halo_read_screen", "halo_find_on_screen", "halo_ui_click", "halo_ui_scroll",
            "halo_ui_set_text", "halo_ui_global_action", "halo_ui_search", "halo_ui_set_checked", "halo_ui_wait",
        ),
    )

    @Test
    fun `each definition is claimed exactly once, by a kind that owns it`() {
        val claimedByKind = HaloToolDescriptors.ExecutorKind.entries.associateWith { kind ->
            DescriptorCatalog.descriptorsFor(kind, hostExecutor).map { it.name }.toSet()
        }

        claimedByKind.forEach { (kind, names) ->
            val allowed = allowedNames[kind].orEmpty()
            assertEquals("$kind described names it does not own", emptySet<String>(), names - allowed)
        }

        val allClaims = claimedByKind.values.flatten()
        assertEquals(
            "a definition must not be claimable by two kinds",
            allClaims.size,
            allClaims.toSet().size,
        )
        assertEquals(
            "every host definition must be described by exactly one kind",
            hostExecutor.definitions().map { it.name }.toSet(),
            allClaims.toSet(),
        )
    }

    @Test
    fun `kinds bound to single-tool executors describe nothing here`() {
        listOf(
            HaloToolDescriptors.ExecutorKind.CURRENT_TIME,
            HaloToolDescriptors.ExecutorKind.STORAGE_STATUS,
            HaloToolDescriptors.ExecutorKind.NOTIFICATION_DIGEST,
            HaloToolDescriptors.ExecutorKind.USE_SKILL,
            HaloToolDescriptors.ExecutorKind.WORKSPACE,
            HaloToolDescriptors.ExecutorKind.FILE,
            HaloToolDescriptors.ExecutorKind.MEMORY,
        ).forEach { kind ->
            assertEquals(
                "$kind must refuse a definition it does not own",
                emptyList<String>(),
                DescriptorCatalog.descriptorsFor(kind, hostExecutor).map { it.name },
            )
        }
    }

    private val definedNames = hostExecutor.definitions().map { it.name }.toSet()

    private fun descriptor(name: String): ToolDescriptor =
        registry.descriptor(name) ?: throw AssertionError("$name has no descriptor")

    @Test
    fun `accessibility mutations keep their mandatory confirmation`() {
        listOf("halo_ui_click", "halo_ui_scroll", "halo_ui_global_action").forEach { name ->
            val descriptor = descriptor(name)
            assertEquals(name, ConfirmationPolicy.ALWAYS, descriptor.security.confirmation)
            assertEquals(name, ReplaySafety.NEVER_REPLAY, descriptor.security.replaySafety)
            assertEquals(name, ToolCategory.HOST_CONTROL, descriptor.category)
        }
        assertEquals(ConfirmationPolicy.ALWAYS, descriptor("halo_ui_set_text").security.confirmation)
        assertEquals(ToolRiskClass.SENSITIVE, descriptor("halo_ui_set_text").security.risk)
    }

    @Test
    fun `expanded native handoffs retain mandatory approval and never replay`() {
        listOf("halo_clock_modify", "halo_contact_editor", "halo_calendar_editor").forEach { name ->
            val owned = descriptor(name)
            assertEquals(name, ConfirmationPolicy.ALWAYS, owned.security.confirmation)
            assertEquals(name, ReplaySafety.NEVER_REPLAY, owned.security.replaySafety)
            assertEquals(name, ToolRiskClass.EXTERNAL_SIDE_EFFECT, owned.security.risk)
        }
        assertEquals(ReplaySafety.RECONCILE_BEFORE_RETRY,
            descriptor("halo_clock_view").security.replaySafety)
        assertEquals(ToolCategory.HOST_CONTROL, descriptor("halo_clock_view").category)
        assertEquals(ToolCategory.COMMUNICATION, descriptor("halo_contact_editor").category)
        assertEquals(ToolCategory.AUTOMATION, descriptor("halo_calendar_editor").category)
    }

    @Test
    fun `each host family owns its own category`() {
        assertEquals(ToolCategory.DEVICE_CONTROL, descriptor("halo_volume").category)
        assertEquals(ToolRiskClass.REVERSIBLE, descriptor("halo_volume").security.risk)
        assertEquals(ToolCategory.MEDIA, descriptor("halo_media_control").category)
        assertEquals(ToolCategory.AUTOMATION, descriptor("halo_set_alarm").category)
        assertEquals(ToolCategory.AUTOMATION, descriptor("halo_timer").category)
        assertEquals(ToolCategory.COMMUNICATION, descriptor("halo_dial_contact").category)
        assertEquals(ToolCategory.HOST_CONTROL, descriptor("halo_open_settings").category)
        assertEquals(ToolCategory.COMMUNICATION, descriptor("halo_share_text").category)
        assertEquals(ToolCategory.PERCEPTION, descriptor("halo_read_screen").category)
        // Web and navigation tools exist only when the device can resolve their intents; when
        // present they must still be navigation, never a fallback category.
        listOf("halo_browser", "halo_navigation", "halo_search").forEach { name ->
            if (definedNames.contains(name)) {
                assertEquals(name, ToolCategory.NAVIGATION, descriptor(name).category)
            }
        }
        assertTrue(
            "volume and media must never be categorised by a web tool",
            definedNames.containsAll(listOf("halo_volume", "halo_media_control")),
        )
        assertEquals(ToolRiskClass.READ_ONLY, descriptor("halo_read_screen").security.risk)
    }

    @Test
    fun `no host tool is silently classified by a fallback descriptor`() {
        val navigational = registry.descriptors()
            .filter { it.category == ToolCategory.NAVIGATION }
            .map { it.name }
            .toSet()
        assertTrue(
            "only the web and navigation tools may carry the navigation category, found $navigational",
            navigational.all { it in setOf("halo_navigation", "halo_browser", "halo_search", "halo_native_map") },
        )

        val fallbacks = registry.descriptors().filter(::isRegistryFallback).map { it.name }
        assertEquals(
            "these host tools were described by no kind and fell back to the registry default",
            emptyList<String>(),
            fallbacks,
        )
    }

    @Test
    fun `native host controls retain confirmation and cannot replay after ownership expansion`() {
        for (name in listOf("halo_native_destination", "halo_native_target", "halo_native_shortcut",
            "halo_native_global", "halo_native_map", "halo_google_mail", "halo_open_notification")) {
            val policy = descriptor(name).security
            assertEquals(name, ConfirmationPolicy.ALWAYS, policy.confirmation)
            assertEquals(name, ReplaySafety.NEVER_REPLAY, policy.replaySafety)
        }
        assertEquals(ToolCategory.NAVIGATION, descriptor("halo_native_map").category)
        assertEquals(ToolRiskClass.READ_ONLY, descriptor("halo_native_result").security.risk)
    }

    /**
     * The registry's own fallback is `SYSTEM` + `SENSITIVE` + `USER_POLICY` + `NEVER_REPLAY`
     * with no keywords and no presentation. No declared host descriptor has that shape, so
     * finding one means a definition was claimed by no kind at all.
     */
    private fun isRegistryFallback(descriptor: ToolDescriptor): Boolean =
        descriptor.category == ToolCategory.SYSTEM &&
            descriptor.keywords.isEmpty() &&
            descriptor.presentation == null &&
            descriptor.security.risk == ToolRiskClass.SENSITIVE &&
            descriptor.security.confirmation == ConfirmationPolicy.USER_POLICY &&
            descriptor.security.replaySafety == ReplaySafety.NEVER_REPLAY

    @Test
    fun `new native abilities carry their own capability requirements`() {
        assertEquals(
            setOf("host.device.inspect"),
            capabilityIds("halo_device_info"),
        )
        assertEquals(setOf("host.battery.read"), capabilityIds("halo_battery_status"))
        assertEquals(setOf("host.network.read"), capabilityIds("halo_network_status"))
        assertEquals(setOf("host.clipboard.read"), capabilityIds("halo_clipboard_read"))
        assertEquals(setOf("host.clipboard.write"), capabilityIds("halo_clipboard_write"))
        assertEquals(setOf("host.web.fetch"), capabilityIds("halo_fetch_url"))
        assertEquals(setOf("host.timer.set"), capabilityIds("halo_timer"))
        assertEquals(setOf("host.ringer.control"), capabilityIds("halo_ringer"))
        assertEquals(setOf("host.screen.brightness"), capabilityIds("halo_brightness"))
        assertEquals(setOf("host.settings.open"), capabilityIds("halo_open_settings"))
    }

    @Test
    fun `mutating native abilities are classified for consent, not convenience`() {
        assertEquals(ToolRiskClass.SENSITIVE, descriptor("halo_clipboard_read").security.risk)
        assertEquals(ReplaySafety.NEVER_REPLAY, descriptor("halo_clipboard_read").security.replaySafety)
        assertEquals(ToolRiskClass.REVERSIBLE, descriptor("halo_clipboard_write").security.risk)
        assertEquals(ToolRiskClass.READ_ONLY, descriptor("halo_checksum").security.risk)
        assertEquals(ToolRiskClass.READ_ONLY, descriptor("halo_fetch_url").security.risk)
        assertEquals(
            "reading a page leaves the device, so the user's policy decides",
            ConfirmationPolicy.USER_POLICY,
            descriptor("halo_fetch_url").security.confirmation,
        )
        assertEquals(
            "following a page's link must never be an unattended default",
            ReplaySafety.RECONCILE_BEFORE_RETRY,
            descriptor("halo_timer").security.replaySafety,
        )
    }

    @Test
    fun `every registered host definition is described by some kind`() {
        val described = registry.descriptors().map { it.name }.toSet()
        assertTrue(described.containsAll(hostExecutor.definitions().map { it.name }))
        assertNotNull(registry.descriptor("halo_fetch_url"))
    }

    private fun capabilityIds(name: String): Set<String> = descriptor(name).requirements
        .filterIsInstance<com.halo.runtime.cognition.tools.ToolRequirement.Capability>()
        .map { it.id.value }
        .toSet()

    private object NoFiles : WorkspaceFileRepository {
        override suspend fun importFile(
            uri: android.net.Uri,
            displayName: String?,
            mimeType: String?,
            sizeBytes: Long?,
            source: com.halo.app.data.notebook.WorkspaceObjectSource,
        ): WorkspaceFile = error("not used")

        override suspend fun getFile(objectId: WorkspaceObjectId): WorkspaceFile? = null
        override fun observeFiles(): Flow<List<WorkspaceFile>> = flowOf(emptyList())
        override suspend fun keepLocalCopy(objectId: WorkspaceObjectId): WorkspaceFile = error("not used")
        override suspend fun relink(objectId: WorkspaceObjectId, uri: android.net.Uri): WorkspaceFile = error("not used")
        override suspend fun reconcileAvailability() = Unit
    }

    private object NoWorkspace : WorkspaceRepository {
        override fun observe(query: WorkspaceQuery): Flow<List<WorkspaceObject>> = flowOf(emptyList())
        override suspend fun get(id: WorkspaceObjectId): WorkspaceObject? = null
        override suspend fun updateTitle(id: WorkspaceObjectId, title: String) = Unit
        override suspend fun archive(id: WorkspaceObjectId) = Unit
        override suspend fun trash(id: WorkspaceObjectId) = Unit
        override suspend fun restore(id: WorkspaceObjectId) = Unit
        override suspend fun removeFromInbox(id: WorkspaceObjectId) = Unit
    }
}
