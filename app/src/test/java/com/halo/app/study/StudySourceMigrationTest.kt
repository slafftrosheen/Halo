package com.halo.app.study

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
class StudySourceMigrationTest {
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
    @Test fun migrationPreservesLessonAttemptsAndCardHistory() {
        val name = "study-sources-002-migration"
        helper.createDatabase(name, 25).apply {
            execSQL("INSERT INTO study_sessions (id,goal,sourceObjectIdsJson,sourceRevision,tutorStage,lifecycle,solutionRevealed,schemaVersion,createdAtEpochMs,updatedAtEpochMs) VALUES ('lesson','Goal','[\"a\",\"b\"]',7,'HINT','ACTIVE',0,2,1,1)")
            execSQL("INSERT INTO study_attempts VALUES ('attempt','lesson','exercise','Learner work','HINT','[]','submission',2)")
            execSQL("INSERT INTO study_feedback VALUES ('feedback','lesson','attempt','generation','UNVERIFIED','Hint','Try again',NULL,'[]',3)")
            execSQL("INSERT INTO study_practice_items (id,sessionId,question,answer,sourceObjectId,sourceRevision,accepted,intervalIndex,dueAtEpochMs,reviewCount,createdAtEpochMs) VALUES ('card','lesson','What?','Answer','a',7,1,0,5,1,1)")
            execSQL("INSERT INTO study_review_events VALUES ('review','card','lesson',1,0,1,5,4)")
            close()
        }
        helper.runMigrationsAndValidate(name, 26, true, AppDatabase.MIGRATION_25_26).apply {
            query("SELECT sourcesJson,sourceObjectIdsJson,sourceRevision,schemaVersion FROM study_sessions").use {
                assertTrue(it.moveToFirst())
                assertEquals(listOf(com.halo.sdk.runtime.study.StudySessionSource("a", "7"), com.halo.sdk.runtime.study.StudySessionSource("b", null)),
                    com.halo.app.data.study.StudyJson.decodeSources(it.getString(0), com.halo.app.data.study.StudyJson.decodeIds(it.getString(1)), it.getLong(2)))
                assertEquals(3, it.getInt(3))
            }
            listOf("study_attempts", "study_feedback", "study_practice_items", "study_review_events").forEach { table ->
                query("SELECT COUNT(*) FROM $table").use { assertTrue(it.moveToFirst()); assertEquals(1, it.getInt(0)) }
            }
            close()
        }
    }
}
