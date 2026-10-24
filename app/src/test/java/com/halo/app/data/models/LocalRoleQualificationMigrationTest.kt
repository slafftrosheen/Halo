package com.halo.app.data.models

import android.app.Application
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.platform.app.InstrumentationRegistry
import com.halo.app.data.AppDatabase
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class LocalRoleQualificationMigrationTest {
    // SupportSQLiteDriver compares path components using '/' even on Windows hosts.
    // Report the real absolute filename while leaving the factory's physical database unchanged.
    private val absoluteNameFactory = object : SupportSQLiteOpenHelper.Factory {
        override fun create(configuration: SupportSQLiteOpenHelper.Configuration): SupportSQLiteOpenHelper {
            val delegate = FrameworkSQLiteOpenHelperFactory().create(configuration)
            return object : SupportSQLiteOpenHelper by delegate {
                override val databaseName: String? get() = configuration.name?.let {
                    configuration.context.getDatabasePath(it).absolutePath
                }
            }
        }
    }
    @get:Rule val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java, emptyList(), absoluteNameFactory)
    @Test fun migrationPreservesBenchmarkHistoryAndAddsEmptyRoleEvidence() {
        val name = "local-role-002-migration"
        helper.createDatabase(name, 24).apply {
            execSQL("INSERT INTO model_runtime_profiles VALUES ('legacy','artifact','sha','device','LITERT','CPU',4096,4,0,1200,3000,0,8.4,NULL,1,0,0,0,1,'app','runtime',1,1,NULL)")
            close()
        }
        helper.runMigrationsAndValidate(name, 25, true, AppDatabase.MIGRATION_24_25).apply {
            query("SELECT id FROM model_runtime_profiles").use { assertTrue(it.moveToFirst()); assertEquals("legacy", it.getString(0)) }
            query("SELECT COUNT(*) FROM local_role_qualifications").use { assertTrue(it.moveToFirst()); assertEquals(0, it.getInt(0)) }
            close()
        }
    }

    @Test fun buildIdentityMigrationPreservesLegacyPassAsUnscopedHistory() {
        val name = "r30.db"
        helper.createDatabase(name, 29).apply {
            execSQL("""INSERT INTO local_role_qualifications
                (id, artifactId, artifactFingerprint, deviceFingerprint, runtime, runtimeVersion,
                compatibilityEpoch, backend, contextTokens, threads, gpuLayers, probeSuiteVersion,
                testedAtMs, artifactBytes, loadMs, coldTtftMs, warmTtftSamples, fastVerdict,
                warmSamples, warmMedianMs, chatVerdict, chatPassed, chatTotal, reasoningVerdict,
                reasoningPassed, reasoningTotal, agenticVerdict, toolProtocol, initialToolPass,
                continuationPass, visionVerdict, verifiedVision, stable, memoryPressure, failureCode)
                VALUES ('legacy','model','${"a".repeat(64)}','device','LITERT','0.18.0',
                1,'CPU',4096,4,0,2,1,100,1,1,'1,1,1','PASS',3,1,'PASS',3,3,'PASS',3,3,
                'PASS','HALO_PROMPT',1,1,'NOT_APPLICABLE',0,1,'NORMAL',NULL)""".trimIndent())
            close()
        }
        helper.runMigrationsAndValidate(name, 30, true, AppDatabase.MIGRATION_29_30).apply {
            query("SELECT id, artifactFingerprint, fastVerdict, executionBuildIdentity FROM local_role_qualifications").use {
                assertTrue(it.moveToFirst())
                assertEquals("legacy", it.getString(0))
                assertEquals("a".repeat(64), it.getString(1))
                assertEquals("PASS", it.getString(2))
                assertTrue(it.isNull(3))
                assertFalse(it.moveToNext())
            }
            close()
        }
    }
}
