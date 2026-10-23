package com.halo.app.data.studio

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
class StudioMigrationTest {
    @Test fun additiveMigrationPreservesExistingObjectsAndCascadesOnlyTheirOwnedStudioRows() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val name = "studio-migration-fixture.db"
        context.deleteDatabase(name)
        val helper = FrameworkSQLiteOpenHelperFactory().create(SupportSQLiteOpenHelper.Configuration.builder(context).name(name)
            .callback(object : SupportSQLiteOpenHelper.Callback(27) {
                override fun onConfigure(db: SupportSQLiteDatabase) { db.setForeignKeyConstraintsEnabled(true) }
                override fun onCreate(db: SupportSQLiteDatabase) {
                    db.execSQL("CREATE TABLE workspace_objects(id TEXT PRIMARY KEY NOT NULL, title TEXT NOT NULL)")
                    db.execSQL("INSERT INTO workspace_objects VALUES('existing', 'Existing note')")
                    db.execSQL("INSERT INTO workspace_objects VALUES('project', 'Studio')")
                }
                override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
            }).build())
        try {
            val db = helper.writableDatabase
            AppDatabase.MIGRATION_27_28.migrate(db)
            db.query("SELECT title FROM workspace_objects WHERE id='existing'").use { assertTrue(it.moveToFirst()); assertEquals("Existing note", it.getString(0)) }
            db.execSQL("INSERT INTO studio_projects VALUES('project',1,'document','session')")
            db.execSQL("INSERT INTO studio_project_versions VALUES('project',1,'document',1)")
            db.execSQL("DELETE FROM workspace_objects WHERE id='project'")
            db.query("SELECT COUNT(*) FROM studio_project_versions").use { it.moveToFirst(); assertEquals(0, it.getInt(0)) }
            db.query("SELECT COUNT(*) FROM workspace_objects").use { it.moveToFirst(); assertEquals(1, it.getInt(0)) }
        } finally { helper.close(); context.deleteDatabase(name) }
    }
}
