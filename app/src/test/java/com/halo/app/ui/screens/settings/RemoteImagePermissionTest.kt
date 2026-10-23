package com.halo.app.ui.screens.settings

import com.halo.app.ai.KEY_CLOUD_IMAGES
import com.halo.sdk.provider.PaidMediaPolicy
import com.halo.sdk.runtime.Storage
import org.junit.Assert.*
import org.junit.Test

class RemoteImagePermissionTest {
    @Test fun explicitApprovalPersistsBothGates() {
        val storage = MemoryStorage()
        val subject = ContextPrivacyViewModel(storage) { true }
        subject.grantRemoteImageAccess()
        assertTrue(subject.policy.value.allowCloudImages)
        assertTrue(subject.paidMediaAllowed.value)
        val reopened = ContextPrivacyViewModel(storage)
        assertTrue(reopened.policy.value.allowCloudImages)
        assertTrue(reopened.paidMediaAllowed.value)
    }

    @Test fun childRestrictionIsRecheckedWhenApprovalIsApplied() {
        val storage = MemoryStorage()
        var allowed = true
        val subject = ContextPrivacyViewModel(storage) { allowed }
        allowed = false
        assertThrows(com.halo.app.online.OnlineOperationRejected::class.java) {
            subject.grantRemoteImageAccess()
        }
        assertFalse(subject.policy.value.allowCloudImages)
        assertFalse(subject.paidMediaAllowed.value)
        assertFalse(storage.getBoolean(PaidMediaPolicy.CONSENT_KEY, false))
    }

    @Test fun failedPersistenceDoesNotReportPermissionGranted() {
        val storage = MemoryStorage().apply { failWrites = true }
        val subject = ContextPrivacyViewModel(storage) { true }
        assertThrows(IllegalStateException::class.java) { subject.grantRemoteImageAccess() }
        assertFalse(subject.policy.value.allowCloudImages)
        assertFalse(subject.paidMediaAllowed.value)
    }

    @Test fun contextToggleNeverGrantsSeparateSpendingConsent() {
        val storage = MemoryStorage()
        val subject = ContextPrivacyViewModel(storage)
        subject.update { it.copy(allowCloudImages = true) }
        assertTrue(subject.policy.value.allowCloudImages)
        assertFalse(storage.getBoolean(PaidMediaPolicy.CONSENT_KEY, false))
    }

    @Test fun revokingImagePermissionRetainsLocalImagesAndDoesNotChangeSpendingConsent() {
        val storage = MemoryStorage().apply {
            putBoolean(KEY_CLOUD_IMAGES, true)
            putBoolean(PaidMediaPolicy.CONSENT_KEY, true)
        }
        val subject = ContextPrivacyViewModel(storage)
        subject.update { it.copy(allowCloudImages = false) }
        assertFalse(subject.policy.value.allowCloudImages)
        assertTrue(subject.policy.value.includeImages)
        assertTrue(storage.getBoolean(PaidMediaPolicy.CONSENT_KEY, false))
    }

    private class MemoryStorage : Storage {
        private val values = mutableMapOf<String, Any?>()
        var failWrites = false
        override fun putBatch(strings: Map<String, String?>, booleans: Map<String, Boolean>) {
            check(!failWrites)
            values.putAll(strings)
            values.putAll(booleans)
        }
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
