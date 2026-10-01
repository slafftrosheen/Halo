package com.halo.app.data

import android.content.Context
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

@RunWith(RobolectricTestRunner::class)
class CognitionMigrationTest {
    private val context get() = ApplicationProvider.getApplicationContext<Context>()
    private fun schema(version: Int): JSONObject {
        val relative = "schemas/com.halo.app.data.AppDatabase/$version.json"
        val file = listOf(File(relative), File("app/$relative")).first { it.exists() }
        return JSONObject(file.readText()).getJSONObject("database")
    }
    private fun assertCurrentSchema(db: SupportSQLiteDatabase) {
        assertEquals(AppDatabase.VERSION, db.version)
        val entities = schema(AppDatabase.VERSION).getJSONArray("entities")
        for (index in 0 until entities.length()) {
            val entity = entities.getJSONObject(index)
            val name = entity.getString("tableName")
            db.query("SELECT * FROM `$name` LIMIT 0").use { cursor ->
                val expected = entity.getJSONArray("fields")
                assertEquals(expected.length(), cursor.columnCount)
                for (field in 0 until expected.length()) assertTrue(cursor.columnNames.contains(expected.getJSONObject(field).getString("columnName")))
            }
            val indices = entity.optJSONArray("indices")
            if (indices != null) {
                for (i in 0 until indices.length()) {
                    val index = indices.optJSONObject(i)
                    if (index != null) {
                        db.query("SELECT name FROM sqlite_master WHERE type='index' AND name=?", arrayOf(index.getString("name"))).use {
                            assertTrue(it.moveToFirst())
                        }
                    }
                }
            }
        }
    }
    @Test fun freshDatabaseMatchesExportedTablesAndIndexes() {
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
        try { assertCurrentSchema(db.openHelper.writableDatabase) } finally { db.close() }
    }
    @Test fun eightThroughNineToTenPreservesLegacyAgentSteps() = migrate(8)
    @Test fun exportedNineToTenPreservesExpandedAgentIdentity() = migrate(9)

    private fun migrate(version: Int) {
        val name = "cognition-migration-$version.db"
        context.deleteDatabase(name)
        val helper = FrameworkSQLiteOpenHelperFactory().create(SupportSQLiteOpenHelper.Configuration.builder(context)
            .name(name).callback(object : SupportSQLiteOpenHelper.Callback(version) {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    val entities = schema(version).getJSONArray("entities")
                    for (index in 0 until entities.length()) {
                        val entity = entities.getJSONObject(index)
                        val table = entity.getString("tableName")
                        db.execSQL(entity.getString("createSql").replace("\${TABLE_NAME}", table))
                        val indices = entity.optJSONArray("indices")
                        if (indices != null) {
                            for (i in 0 until indices.length()) {
                                val index = indices.optJSONObject(i)
                                if (index != null) {
                                    db.execSQL(index.getString("createSql").replace("\${TABLE_NAME}", table))
                                }
                            }
                        }
                    }
                    db.execSQL("INSERT INTO workspace_objects VALUES ('note', 'NOTE', 'Title', 1, 1, 'USER_CREATED', 'ACTIVE', 1, 'READY', 'note:note')")
                    db.execSQL("INSERT INTO notes VALUES ('note', 'Body', 1)")
                    db.execSQL("INSERT INTO memories (content,timestamp) VALUES ('Memory', 1)")
                    db.execSQL("INSERT INTO conversations VALUES ('conversation',1,1,'Title',1,0)")
                    if (version == 8) {
                        db.execSQL("INSERT INTO agent_runs VALUES ('run','Request',1,NULL,'EXECUTING')")
                        db.execSQL("INSERT INTO agent_steps VALUES ('step','run','ToolRequested','{}',1)")
                    } else {
                        db.execSQL("INSERT INTO agent_runs VALUES ('run','interaction','Request',1,2,NULL,'EXECUTING','reason')")
                        db.execSQL("INSERT INTO agent_steps VALUES ('step','run','interaction',7,'ToolRequested',2,'{}','key','DISPATCHED',1,NULL)")
                        db.execSQL("INSERT INTO backup_records VALUES ('backup','archive','destination',1,'FULL',10,'checksum','COMPLETED')")
                    }
                }
                override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
            }).build())
        helper.writableDatabase
        helper.close()
        val db = Room.databaseBuilder(context, AppDatabase::class.java, name)
            .addMigrations(*AppDatabase.ALL_MIGRATIONS)
            .allowMainThreadQueries().build()
        try {
            val sql = db.openHelper.writableDatabase // Room validates the entire migrated entity schema here.
            assertCurrentSchema(sql)
            for (table in listOf("workspace_objects", "notes", "memories", "conversations", "agent_runs", "agent_steps")) {
                sql.query("SELECT COUNT(*) FROM `$table`").use { assertTrue(it.moveToFirst()); assertEquals(1, it.getInt(0)) }
            }
            if (version == 9) {
                sql.query("SELECT interactionId, failureReason FROM agent_runs").use {
                    assertTrue(it.moveToFirst()); assertEquals("interaction", it.getString(0)); assertEquals("reason", it.getString(1))
                }
                sql.query("SELECT ordinal, idempotencyKey FROM agent_steps").use {
                    assertTrue(it.moveToFirst()); assertEquals(7, it.getInt(0)); assertEquals("key", it.getString(1))
                }
                sql.query("SELECT checksum FROM backup_records").use { assertTrue(it.moveToFirst()); assertEquals("checksum", it.getString(0)) }
            }
        } finally { db.close(); context.deleteDatabase(name) }
    }
}
