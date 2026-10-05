package com.halo.app.data.watch

import android.content.Context
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import com.halo.app.data.AppDatabase
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

@RunWith(RobolectricTestRunner::class)
class ContinuationWatchMigrationTest {
    private val context get() = ApplicationProvider.getApplicationContext<Context>()

    private fun schema(version: Int): JSONObject {
        val relative = "schemas/com.halo.app.data.AppDatabase/$version.json"
        val file = listOf(File(relative), File("app/$relative")).first { it.exists() }
        return JSONObject(file.readText()).getJSONObject("database")
    }

    @Test
    fun migration22To23CreatesContinuationWatchesTable() {
        val dbName = "test-migration-22-23.db"
        context.deleteDatabase(dbName)

        // Create version 22 DB using exported 22.json schema
        val helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(dbName)
                .callback(object : SupportSQLiteOpenHelper.Callback(22) {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        val entities = schema(22).getJSONArray("entities")
                        for (index in 0 until entities.length()) {
                            val entity = entities.getJSONObject(index)
                            val table = entity.getString("tableName")
                            db.execSQL(entity.getString("createSql").replace("\${TABLE_NAME}", table))
                            val indices = entity.optJSONArray("indices")
                            if (indices != null) {
                                for (i in 0 until indices.length()) {
                                    val idx = indices.optJSONObject(i)
                                    if (idx != null) {
                                        db.execSQL(idx.getString("createSql").replace("\${TABLE_NAME}", table))
                                    }
                                }
                            }
                        }
                    }

                    override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {}
                })
                .build()
        )
        helper.writableDatabase.close()

        // Migrate 22 -> 23
        val migratedDb = Room.databaseBuilder(context, AppDatabase::class.java, dbName)
            .addMigrations(AppDatabase.MIGRATION_22_23, AppDatabase.MIGRATION_23_24)
            .allowMainThreadQueries()
            .build()

        try {
            val db = migratedDb.openHelper.writableDatabase
            assertEquals(AppDatabase.VERSION, db.version)
            db.query("SELECT name FROM sqlite_master WHERE type='table' AND name='continuation_watches'").use {
                assertTrue(it.moveToFirst())
            }
        } finally {
            migratedDb.close()
            context.deleteDatabase(dbName)
        }
    }
}
