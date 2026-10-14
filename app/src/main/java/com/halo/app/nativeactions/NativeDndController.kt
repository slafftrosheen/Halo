package com.halo.app.nativeactions

import android.app.AutomaticZenRule
import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.net.Uri
import android.os.Build
import android.service.notification.Condition
import android.service.notification.ZenPolicy

/** Explicit app-owned rule only. Never changes the global interruption filter or another owner's rules. */
internal class NativeDndController(private val context: Context) {
    data class Result(val acknowledged: Boolean, val details: String)
    private val manager = context.getSystemService(NotificationManager::class.java)
    private val preferences = context.getSharedPreferences("native_dnd", Context.MODE_PRIVATE)
    private val conditionId = Uri.Builder().scheme("condition").authority(context.packageName).appendPath("halo-dnd").build()
    private val configuration = ComponentName(context, NativeDndSettingsActivity::class.java)

    internal fun owns(rule: AutomaticZenRule): Boolean = rule.owner == null &&
        rule.configurationActivity == configuration && rule.conditionId == conditionId

    fun request(enabled: Boolean): Result = synchronized(ruleLock) {
        check(Build.VERSION.SDK_INT >= 29) { "App-owned Do Not Disturb requires Android 10 or newer" }
        if (!manager.isNotificationPolicyAccessGranted) throw SecurityException("Do Not Disturb access required")
        val remembered = preferences.getString("ruleId", null)
        var id = remembered?.takeIf { manager.getAutomaticZenRule(it)?.let(::owns) == true }
        if (id == null) {
            // Recover after process death between OS creation and local ID persistence.
            val candidates = manager.automaticZenRules.filterValues(::owns).keys
            check(candidates.size <= 1) { "Multiple Halo rules exist; review them in Android first" }
            id = candidates.singleOrNull()
        }
        if (id == null && !enabled) return@synchronized Result(true, "No matching Halo-managed Do Not Disturb rule exists; no rule was changed. Other rules may remain active.")
        if (id == null) {
            id = manager.addAutomaticZenRule(AutomaticZenRule("Halo", null, configuration,
                conditionId, ZenPolicy.Builder().build(), NotificationManager.INTERRUPTION_FILTER_PRIORITY, true))
            check(!id.isNullOrBlank()) { "Android did not create a Halo rule" }
        }
        preferences.edit().putString("ruleId", id).apply()
        val rule = manager.getAutomaticZenRule(id) ?: error("Halo rule became unavailable")
        check(owns(rule)) { "Halo rule identity changed; no unrelated rule will be modified" }
        if (enabled && !rule.isEnabled) {
            rule.isEnabled = true
            check(manager.updateAutomaticZenRule(id, rule)) { "Android did not enable the Halo rule" }
        }
        val wanted = if (enabled) Condition.STATE_TRUE else Condition.STATE_FALSE
        manager.setAutomaticZenRuleState(id, Condition(conditionId, "Halo", wanted))
        val acknowledged = Build.VERSION.SDK_INT >= 35 && manager.getAutomaticZenRuleState(id) == wanted
        Result(acknowledged, "Halo Do Not Disturb rule ${if (enabled) "activation" else "deactivation"} requested; " +
            "ownRuleStateAcknowledged=$acknowledged. Other rules were not modified; effective global sound behavior remains unverified.")
    }
    private companion object { val ruleLock = Any() }
}
