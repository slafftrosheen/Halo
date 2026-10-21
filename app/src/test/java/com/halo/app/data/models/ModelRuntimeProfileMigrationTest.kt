package com.halo.app.data.models

import android.app.Application
import android.content.Context
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import com.halo.app.data.AppDatabase
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class ModelRuntimeProfileMigrationTest {
    @Test fun migrationPreservesCalibrationAndAllowsSeparateFailureEvidence() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "runtime060-profile-migration.db"
        context.deleteDatabase(name)
        val relative = "schemas/com.halo.app.data.AppDatabase/23.json"
        val schema = JSONObject(listOf(File(relative), File("app/$relative")).first { it.exists() }.readText()).getJSONObject("database")
        val helper = FrameworkSQLiteOpenHelperFactory().create(SupportSQLiteOpenHelper.Configuration.builder(context).name(name)
            .callback(object : SupportSQLiteOpenHelper.Callback(23) {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    val entities = schema.getJSONArray("entities")
                    for (i in 0 until entities.length()) {
                        val entity = entities.getJSONObject(i)
                        val table = entity.getString("tableName")
                        db.execSQL(entity.getString("createSql").replace("\${TABLE_NAME}", table))
                        val indices = entity.optJSONArray("indices") ?: org.json.JSONArray()
                        for (j in 0 until indices.length()) db.execSQL(indices.getJSONObject(j).getString("createSql").replace("\${TABLE_NAME}", table))
                    }
                    db.execSQL("INSERT INTO model_runtime_profiles VALUES ('legacy','artifact','sha','device','LITERT','CPU',4096,4,0,1200,3000,0,8.4,NULL,1,0,0,0,1,'app','runtime',1,1,NULL)")
                }
                override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {}
            }).build())
        helper.writableDatabase
        helper.close()
        val db = Room.databaseBuilder(context, AppDatabase::class.java, name).addMigrations(AppDatabase.MIGRATION_23_24, AppDatabase.MIGRATION_24_25, AppDatabase.MIGRATION_25_26, AppDatabase.MIGRATION_26_27).allowMainThreadQueries().build()
        try {
            val repo = ModelRuntimeProfileRepository(db.modelRuntimeProfileDao())
            val original = repo.preferred("artifact", "sha", "device", "runtime", 1)!!
            assertEquals("legacy", original.id)
            repo.save(original.copy(id = "failure", isStable = false, textGenerationProven = false, failureReason = "LOAD_FAILED"))
            assertEquals(2, db.modelRuntimeProfileDao().getProfilesForModel("artifact", "device").size)
            assertEquals("legacy", repo.preferred("artifact", "sha", "device", "runtime", 1)!!.id)
            assertEquals(AppDatabase.VERSION, db.openHelper.writableDatabase.version)
        } finally { db.close(); context.deleteDatabase(name) }
    }
}
