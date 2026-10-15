package com.halo.app.security

import com.halo.sdk.runtime.Storage
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

/** Isolated fixtures: never open the phone's credential store or Keystore alias. */
class SecretMigrationSafetyTest {
    private class MemoryStorage : Storage {
        val values = mutableMapOf<String, Any>()
        override fun getString(key: String, defaultValue: String?) = values[key] as? String ?: defaultValue
        override fun putString(key: String, value: String?) { if (value == null) values.remove(key) else values[key] = value }
        override fun getInt(key: String, defaultValue: Int) = values[key] as? Int ?: defaultValue
        override fun putInt(key: String, value: Int) { values[key] = value }
        override fun getBoolean(key: String, defaultValue: Boolean) = values[key] as? Boolean ?: defaultValue
        override fun putBoolean(key: String, value: Boolean) { values[key] = value }
        override fun remove(key: String) { values.remove(key) }
        override fun clear() { values.clear() }
    }
    private class ProtectedFixture : SecretStore {
        val values = mutableMapOf<String, String>()
        var failRead = false
        override suspend fun put(key: String, value: String?) { if (value == null) values.remove(key) else values[key] = value }
        override suspend fun get(key: String): String? { check(!failRead); return values[key] }
        override suspend fun remove(key: String) { values.remove(key) }
    }

    @Test fun everyPresetMigratesAndSurvivesOwnerReconstruction() = runBlocking {
        val storage = MemoryStorage()
        val protected = ProtectedFixture()
        SecretMigrationV2.SECRET_KEYS.forEach { storage.putString(it, "fixture-$it") }
        SecretMigrationV2(storage, protected).migrate()
        SecretMigrationV2(storage, protected).migrate()
        SecretMigrationV2.SECRET_KEYS.forEach {
            assertNull(storage.getString(it))
            assertEquals("fixture-$it", protected.get(it))
        }
        assertEquals(3, storage.getInt("secret_migration_v2_version"))
    }

    @Test fun rotatedProtectedKeyWinsOverStaleLegacyPreferences() = runBlocking {
        val storage = MemoryStorage().apply { putString(SecretMigrationV2.GROQ_KEY, "fixture-old") }
        val protected = ProtectedFixture().apply { values[SecretMigrationV2.GROQ_KEY] = "fixture-rotated" }
        SecretMigrationV2(storage, protected).migrate()
        assertEquals("fixture-rotated", protected.get(SecretMigrationV2.GROQ_KEY))
        assertNull(storage.getString(SecretMigrationV2.GROQ_KEY))
    }

    @Test fun unreadableProtectedStoreDoesNotDeleteLegacyCredentialOrClaimMigration() = runBlocking {
        val storage = MemoryStorage().apply { putString(SecretMigrationV2.CEREBRAS_KEY, "fixture-preserved") }
        val protected = ProtectedFixture().apply { failRead = true }
        assertTrue(runCatching { SecretMigrationV2(storage, protected).migrate() }.isFailure)
        assertEquals("fixture-preserved", storage.getString(SecretMigrationV2.CEREBRAS_KEY))
        assertEquals(0, storage.getInt("secret_migration_v2_version"))
    }
}
