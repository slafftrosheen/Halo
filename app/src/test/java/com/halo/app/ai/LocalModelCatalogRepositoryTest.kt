package com.halo.app.ai

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.halo.engines.ai.catalog.AiModelCatalogRepository
import com.halo.providers.litert.LiteRtLocalProvider
import com.halo.providers.litert.models.*
import com.halo.sdk.provider.*
import com.halo.sdk.runtime.Storage
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class LocalModelCatalogRepositoryTest {
    private val stored = mutableMapOf<String, Any>()
    private val storage = object : Storage {
        override fun getString(key: String, defaultValue: String?) = stored[key] as? String ?: defaultValue
        override fun getInt(key: String, defaultValue: Int) = stored[key] as? Int ?: defaultValue
        override fun getBoolean(key: String, defaultValue: Boolean) = defaultValue
        override fun putString(key: String, value: String?) { if (value == null) stored.remove(key) else stored[key] = value }
        override fun putInt(key: String, value: Int) { stored[key] = value }
        override fun putBoolean(key: String, value: Boolean) {}
        override fun remove(key: String) { stored.remove(key) }
        override fun clear() {}
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test fun freshInstallListsExistingCatalogueAndRefreshReflectsInstallAndDeletion() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val root = File(context.cacheDir, "runtime061-catalog-${System.nanoTime()}").apply { mkdirs() }
        val bytes = ByteArray(1_200_000) { (it % 251).toByte() }
        val sha = java.security.MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
        val model = LiteRtModelCatalog.QWEN3_0_6B_DYNAMIC_INT4.copy(id = "test", expectedBytes = bytes.size.toLong(), sha256 = sha)
        val manager = LiteRtModelManager(context, root, catalog = listOf(model))
        val provider = LiteRtLocalProvider(context, manager, storage, nativeResourceCoordinator = null, catalog = listOf(model))
        var refreshes = 0
        val delegate = object : AiModelCatalogRepository {
            override val state = MutableStateFlow(AiModelCatalogState())
            override suspend fun refresh() { refreshes++ }
        }
        val catalog = LocalModelCatalogRepository(delegate, manager, backgroundScope) { provider }
        manager.refresh(ModelInventoryRefreshReason.USER_REQUEST)
        runCurrent()
        val initial = catalog.state.first { it.models.size == 1 }.models.single()
        assertEquals(ModelCatalogueSource.LOCAL_DOWNLOADABLE, initial.source)
        assertEquals(false, initial.runtime.installed)
        File(root, "test.litertlm").writeBytes(bytes)
        catalog.refresh(provider.id)
        runCurrent()
        val installed = catalog.state.first { it.models.singleOrNull()?.runtime?.installed == true }.models.single()
        assertEquals(ModelCatalogueSource.LOCAL_INSTALLED, installed.source)
        assertEquals(true, installed.runtime.installed)
        assertEquals(1, refreshes)
        File(root, "test.litertlm").delete()
        catalog.refresh(provider.id)
        runCurrent()
        assertEquals(ModelCatalogueSource.LOCAL_DOWNLOADABLE, catalog.state.first { it.models.singleOrNull()?.source == ModelCatalogueSource.LOCAL_DOWNLOADABLE }.models.single().source)
    }
}
