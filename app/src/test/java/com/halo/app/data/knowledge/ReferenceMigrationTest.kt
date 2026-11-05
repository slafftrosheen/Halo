package com.halo.app.data.knowledge

import android.app.Application
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import com.halo.app.data.AppDatabase
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
class ReferenceMigrationTest {
    @Test fun roomReopensMigrated34DatabaseAndPreservesAnExistingSource() {
        val context = ApplicationProvider.getApplicationContext<Application>(); val name = "reference-room-migration.db"; context.deleteDatabase(name)
        try {
            // The unchanged tables are exactly the generated production schema. Remove only v35 additions.
            val before = androidx.room.Room.databaseBuilder(context, AppDatabase::class.java, name).allowMainThreadQueries().build()
            kotlinx.coroutines.runBlocking {
                com.halo.app.data.everyday.RoomCollectorRepository(before).save(com.halo.app.everyday.collectors.CollectorSource(
                    "existing", "Preserved", "Owned", com.halo.app.everyday.collectors.CollectorKind.LOCAL_DEVICE,
                    com.halo.app.everyday.collectors.SourceCategory.PERSONAL, "en"), null)
            }
            before.close()
            val v34 = FrameworkSQLiteOpenHelperFactory().create(SupportSQLiteOpenHelper.Configuration.builder(context).name(name).callback(object : SupportSQLiteOpenHelper.Callback(34) {
                override fun onCreate(db: SupportSQLiteDatabase) = error("Existing fixture required")
                override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = error("Downgrade fixture only")
                override fun onDowngrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {
                    check(oldVersion == 35 && newVersion == 34)
                    for (table in listOf("material_images", "reference_import_jobs", "reference_entries_fts", "reference_entries", "reference_libraries")) db.execSQL("DROP TABLE IF EXISTS `$table`")
                }
            }).build())
            v34.writableDatabase; v34.close()
            val after = androidx.room.Room.databaseBuilder(context, AppDatabase::class.java, name).allowMainThreadQueries().addMigrations(AppDatabase.MIGRATION_34_35).build()
            try {
                after.openHelper.writableDatabase // Runs actual Room migration validation, including FTS options.
                kotlinx.coroutines.runBlocking { assertEquals("Preserved", com.halo.app.data.everyday.RoomCollectorRepository(after).source("existing")!!.title) }
            } finally { after.close() }
        } finally { context.deleteDatabase(name) }
    }
    @Test fun additiveMigrationPreservesExistingSourcesAndMaintainsDerivedFtsOnDelete() {
        val context = ApplicationProvider.getApplicationContext<Application>(); val name = "reference-migration.db"; context.deleteDatabase(name)
        val helper = FrameworkSQLiteOpenHelperFactory().create(SupportSQLiteOpenHelper.Configuration.builder(context).name(name).callback(object : SupportSQLiteOpenHelper.Callback(34) {
            override fun onConfigure(db: SupportSQLiteDatabase) { db.setForeignKeyConstraintsEnabled(true) }
            override fun onCreate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE collector_sources(id TEXT NOT NULL PRIMARY KEY, version INTEGER NOT NULL, body TEXT NOT NULL)")
                db.execSQL("INSERT INTO collector_sources VALUES('existing',7,'kept')")
                db.execSQL("CREATE TABLE collector_snapshots(id TEXT PRIMARY KEY, payload TEXT, etag TEXT, lastModified TEXT, fetchedAt INTEGER)")
                db.execSQL("INSERT INTO collector_snapshots VALUES('article','{\"type\":\"articles\"}','old-tag','old-date',17)")
                db.execSQL("INSERT INTO collector_snapshots VALUES('weather','{\"type\":\"weather\"}','weather-tag','weather-date',18)")
            }
            override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
        }).build())
        try {
            val db = helper.writableDatabase; AppDatabase.MIGRATION_34_35.migrate(db)
            db.query("SELECT body,version FROM collector_sources WHERE id='existing'").use { assertTrue(it.moveToFirst()); assertEquals("kept", it.getString(0)); assertEquals(7, it.getInt(1)) }
            db.query("SELECT payload,etag,lastModified,fetchedAt FROM collector_snapshots WHERE id='article'").use {
                assertTrue(it.moveToFirst()); assertEquals("{\"type\":\"articles\"}", it.getString(0))
                assertTrue(it.isNull(1)); assertTrue(it.isNull(2)); assertEquals(17L, it.getLong(3))
            }
            db.query("SELECT etag,lastModified FROM collector_snapshots WHERE id='weather'").use {
                assertTrue(it.moveToFirst()); assertEquals("weather-tag", it.getString(0)); assertEquals("weather-date", it.getString(1))
            }
            db.execSQL("INSERT INTO reference_libraries VALUES('existing','g','Owned','ru','fp',1,1,'USER_IMPORT')")
            db.execSQL("INSERT INTO reference_entries(sourceId,generation,ordinal,entryId,title,text,language,publisher,license,format,imagesJson) VALUES('existing','g',1,'a','Вода','Чистая вода','ru','Owned','CC0','TEXT','[]')")
            db.query("SELECT count(*) FROM reference_entries_fts WHERE reference_entries_fts MATCH 'вода'").use { it.moveToFirst(); assertEquals(1, it.getInt(0)) }
            db.execSQL("DELETE FROM collector_sources WHERE id='existing'")
            db.query("SELECT count(*) FROM reference_libraries").use { it.moveToFirst(); assertEquals(0, it.getInt(0)) }
            db.query("SELECT count(*) FROM reference_entries_fts WHERE reference_entries_fts MATCH 'вода'").use { it.moveToFirst(); assertEquals(0, it.getInt(0)) }
        } finally { helper.close(); context.deleteDatabase(name) }
    }
}
