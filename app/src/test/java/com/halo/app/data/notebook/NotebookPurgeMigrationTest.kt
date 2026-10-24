package com.halo.app.data.notebook

import android.app.Application
import androidx.sqlite.db.*
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
class NotebookPurgeMigrationTest {
    @Test fun additiveReceiptMigrationKeepsExistingSourceAndReceiptDoesNotCascadeWithTheNote() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        // Robolectric isolates this test's directory; keep the full Windows SQLite path below 260.
        val name = "purge.db"
        val helper = FrameworkSQLiteOpenHelperFactory().create(SupportSQLiteOpenHelper.Configuration.builder(context).name(name)
            .callback(object : SupportSQLiteOpenHelper.Callback(28) {
                override fun onConfigure(db: SupportSQLiteDatabase) { db.setForeignKeyConstraintsEnabled(true) }
                override fun onCreate(db: SupportSQLiteDatabase) {
                    db.execSQL("CREATE TABLE workspace_objects(id TEXT NOT NULL PRIMARY KEY, title TEXT NOT NULL)")
                    db.execSQL("INSERT INTO workspace_objects VALUES('note', 'Existing source')")
                }
                override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
            }).build())
        try {
            val db = helper.writableDatabase
            AppDatabase.MIGRATION_28_29.migrate(db)
            db.query("SELECT title FROM workspace_objects WHERE id='note'").use { assertTrue(it.moveToFirst()); assertEquals("Existing source", it.getString(0)) }
            db.execSQL("INSERT INTO notebook_purges (noteId,sourceVersion,recoveryVersion,includeRecovery,revision,state,requestedAt,pending,exclusiveCleared,sharedRetained,externalRetained,reason) VALUES('note','source','note-draft:none',0,1,'PENDING',1,1,0,0,0,NULL)")
            db.execSQL("INSERT INTO notebook_purge_files VALUES('asset','note','identity')")
            db.execSQL("DELETE FROM workspace_objects WHERE id='note'")
            db.query("SELECT COUNT(*) FROM notebook_purges").use { it.moveToFirst(); assertEquals(1, it.getInt(0)) }
            db.query("SELECT COUNT(*) FROM notebook_purge_files").use { it.moveToFirst(); assertEquals(1, it.getInt(0)) }
            db.execSQL("DELETE FROM notebook_purges WHERE noteId='note'")
            db.query("SELECT COUNT(*) FROM notebook_purge_files").use { it.moveToFirst(); assertEquals(0, it.getInt(0)) }
        } finally { helper.close(); context.deleteDatabase(name) }
    }
}
