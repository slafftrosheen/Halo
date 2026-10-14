package com.halo.app.nativeactions

import android.app.AutomaticZenRule
import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = android.app.Application::class, sdk = [35])
class NativeDndControllerTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val manager = context.getSystemService(NotificationManager::class.java)
    private val controller = NativeDndController(context)
    private val conditionId = Uri.parse("condition://${context.packageName}/halo-dnd")
    private val configuration = ComponentName(context, NativeDndSettingsActivity::class.java)
    @Before fun reset() {
        context.getSharedPreferences("native_dnd", Context.MODE_PRIVATE).edit().clear().commit()
    }

    @Test fun `permission grant is required before activation or deactivation`() {
        shadowOf(manager).setNotificationPolicyAccessGranted(false)
        assertThrows(SecurityException::class.java) { controller.request(true) }
        assertThrows(SecurityException::class.java) { controller.request(false) }
    }

    @Test fun `ownership requires exact configuration condition and no other provider`() {
        fun rule(component: ComponentName?, uri: Uri, owner: ComponentName? = null) =
            AutomaticZenRule("Halo", owner, component, uri, null, NotificationManager.INTERRUPTION_FILTER_PRIORITY, true)
        assertTrue(controller.owns(rule(configuration, conditionId)))
        assertFalse(controller.owns(rule(ComponentName(context, com.halo.app.MainActivity::class.java), conditionId)))
        assertFalse(controller.owns(rule(configuration, Uri.parse("condition://${context.packageName}/other"))))
        assertFalse(controller.owns(rule(configuration, conditionId, ComponentName("another.app", "Provider"))))
    }

    @Test fun `disable without a managed rule cannot create one`() {
        shadowOf(manager).setNotificationPolicyAccessGranted(true)
        val before = manager.automaticZenRules.size
        assertTrue(controller.request(false).acknowledged)
        assertEquals(before, manager.automaticZenRules.size)
    }

    @Test fun `stale remembered ID cannot disable an unrelated rule`() {
        shadowOf(manager).setNotificationPolicyAccessGranted(true)
        val unrelated = AutomaticZenRule("User rule", null,
            ComponentName(context, com.halo.app.MainActivity::class.java), Uri.parse("condition://${context.packageName}/user"),
            null, NotificationManager.INTERRUPTION_FILTER_ALARMS, true)
        val id = manager.addAutomaticZenRule(unrelated)
        context.getSharedPreferences("native_dnd", Context.MODE_PRIVATE).edit().putString("ruleId", id).commit()
        controller.request(false)
        assertTrue(manager.getAutomaticZenRule(id)!!.isEnabled)
        assertEquals(NotificationManager.INTERRUPTION_FILTER_ALARMS, manager.getAutomaticZenRule(id)!!.interruptionFilter)
        assertEquals(1, manager.automaticZenRules.size)
    }
    @Test fun `ambiguous managed rules require review without modifying either`() {
        shadowOf(manager).setNotificationPolicyAccessGranted(true)
        repeat(2) { manager.addAutomaticZenRule(AutomaticZenRule("Halo", null, configuration, conditionId,
            null, NotificationManager.INTERRUPTION_FILTER_PRIORITY, true)) }
        assertThrows(IllegalStateException::class.java) { controller.request(false) }
        assertEquals(2, manager.automaticZenRules.size)
        assertTrue(manager.automaticZenRules.values.all { it.isEnabled })
    }
}
