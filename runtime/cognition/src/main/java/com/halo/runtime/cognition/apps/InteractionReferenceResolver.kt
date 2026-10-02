package com.halo.runtime.cognition.apps

import com.halo.cognition.language.*
import java.time.Instant

data class ApplicationReference(val name: String, val packageName: String? = null)
data class MediaReference(val packageName: String?)
enum class DeviceControlReference { VOLUME, BRIGHTNESS, RINGER }
data class ContactReference(val identifier: String, val displayName: String)
data class SemanticOperationReference(val intent: Intent, val safeToRepeat: Boolean)
data class InteractionReferenceContext(val lastApplication: ApplicationReference? = null, val lastMediaTarget: MediaReference? = null,
    val lastDeviceControl: DeviceControlReference? = null, val lastContactCandidates: List<ContactReference> = emptyList(),
    val lastSemanticOperation: SemanticOperationReference? = null, val foregroundApp: ApplicationReference? = null,
    val createdAt: Instant)
sealed interface ReferenceResolution {
    data class Resolved(val intent: Intent) : ReferenceResolution
    data class Ambiguous(val prompt: String) : ReferenceResolution
    data class Missing(val prompt: String) : ReferenceResolution
    data class Expired(val prompt: String) : ReferenceResolution
}
/** No storage dependency. Contact details/drafts/arguments die with the process or TTL. */
class InteractionReferenceResolver(private val now: () -> Instant = Instant::now, private val ttlSeconds: Long = 120) {
    private var observedForeground: String? = null
    private var context: InteractionReferenceContext? = null
    @Synchronized fun clear() { context = null }
    @Synchronized fun foregroundChanged(app: ApplicationReference?) {
        val expectedSwitch = app != null && context?.lastApplication?.name?.equals(app.name, true) == true
        if (observedForeground != null && observedForeground != app?.packageName && !expectedSwitch) clear()
        observedForeground = app?.packageName
        context = context?.copy(foregroundApp = app)
    }
    @Synchronized fun remember(intent: Intent) {
        val old = context?.takeIf { java.time.Duration.between(it.createdAt, now()).seconds < ttlSeconds }
        context = InteractionReferenceContext(
            lastApplication = (intent as? NativeOpenApplicationIntent)?.let { ApplicationReference(it.application) } ?: old?.lastApplication,
            lastMediaTarget = if (intent == PlayIntent || intent == PauseIntent) MediaReference(old?.lastApplication?.packageName) else old?.lastMediaTarget,
            lastDeviceControl = when (intent) { is VolumeIntent -> DeviceControlReference.VOLUME
                is BrightnessIntent -> DeviceControlReference.BRIGHTNESS; is RingerIntent -> DeviceControlReference.RINGER
                else -> old?.lastDeviceControl },
            lastContactCandidates = old?.lastContactCandidates.orEmpty(),
            lastSemanticOperation = SemanticOperationReference(intent, intent is VolumeIntent || intent is BrightnessIntent ||
                intent is RingerIntent || intent == PauseIntent || intent is NativeTargetMediaIntent), foregroundApp = old?.foregroundApp, createdAt = now())
    }
    @Synchronized fun resolve(text: String): ReferenceResolution {
        val current = context ?: return ReferenceResolution.Missing("What should I use for this action?")
        if (java.time.Duration.between(current.createdAt, now()).seconds !in 0 until ttlSeconds) {
            clear(); return ReferenceResolution.Expired("That reference expired. Please name the app or control.")
        }
        val result: Intent? = when (text.lowercase()) {
            "open it", "open that app" -> current.lastApplication?.let { NativeOpenApplicationIntent(it.name) }
            "pause it" -> current.lastApplication?.let { NativeTargetMediaIntent("pause", it.name) } ?: if (current.lastMediaTarget != null) PauseIntent else null
            "turn it down" -> if (current.lastMediaTarget != null || current.lastApplication != null || current.lastDeviceControl == DeviceControlReference.VOLUME) VolumeIntent(direction = "down") else null
            "turn it up" -> if (current.lastMediaTarget != null || current.lastApplication != null || current.lastDeviceControl == DeviceControlReference.VOLUME) VolumeIntent(direction = "up") else null
            "go back" -> NativeAppActionIntent("navigate_back")
            "again", "do that again" -> current.lastSemanticOperation?.takeIf { it.safeToRepeat }?.intent
            else -> Regex("^(?:make it|set it to) (\\d{1,3})(?: percent|%)?$").matchEntire(text.lowercase())?.let {
                val percent = it.groupValues[1].toInt()
                if (percent !in 0..100) null else when (current.lastDeviceControl) {
                    DeviceControlReference.VOLUME -> VolumeIntent(percent = percent)
                    DeviceControlReference.BRIGHTNESS -> BrightnessIntent(percent)
                    else -> null
                }
            }
        }
        return result?.let { ReferenceResolution.Resolved(it) } ?: ReferenceResolution.Ambiguous("Please name the target and action; I cannot safely repeat that.")
    }
}
