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
    @Test fun additiveMigrationPreservesExistingSourcesAndMaintainsDerivedFtsOnDelete() {
        val context = ApplicationProvider.getApplicationContext<Application>(); val name = "reference-migration.db"; context.deleteDatabase(name)
        val helper = FrameworkSQLiteOpenHelperFactory().create(SupportSQLiteOpenHelper.Configuration.builder(context).name(name).callback(object : SupportSQLiteOpenHelper.Callback(34) {
            override fun onConfigure(db: SupportSQLiteDatabase) { db.setForeignKeyConstraintsEnabled(true) }
            override fun onCreate(db: SupportSQLiteDatabase) { db.execSQL("CREATE TABLE collector_sources(id TEXT NOT NULL PRIMARY KEY, version INTEGER NOT NULL, body TEXT NOT NULL)"); db.execSQL("INSERT INTO collector_sources VALUES('existing',7,'kept')") }
            override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
        }).build())
        try {
            val db = helper.writableDatabase; AppDatabase.MIGRATION_34_35.migrate(db)
            db.query("SELECT body,version FROM collector_sources WHERE id='existing'").use { assertTrue(it.moveToFirst()); assertEquals("kept", it.getString(0)); assertEquals(7, it.getInt(1)) }
            db.execSQL("INSERT INTO reference_libraries VALUES('existing','g','Owned','ru','fp',1,1,'USER_IMPORT')")
            db.execSQL("INSERT INTO reference_entries(sourceId,generation,ordinal,entryId,title,text,language,publisher,license,format,imagesJson) VALUES('existing','g',1,'a','Вода','Чистая вода','ru','Owned','CC0','TEXT','[]')")
            db.query("SELECT count(*) FROM reference_entries_fts WHERE reference_entries_fts MATCH 'вода'").use { it.moveToFirst(); assertEquals(1, it.getInt(0)) }
            db.execSQL("DELETE FROM collector_sources WHERE id='existing'")
            db.query("SELECT count(*) FROM reference_libraries").use { it.moveToFirst(); assertEquals(0, it.getInt(0)) }
            db.query("SELECT count(*) FROM reference_entries_fts WHERE reference_entries_fts MATCH 'вода'").use { it.moveToFirst(); assertEquals(0, it.getInt(0)) }
        } finally { helper.close(); context.deleteDatabase(name) }
    }
}
