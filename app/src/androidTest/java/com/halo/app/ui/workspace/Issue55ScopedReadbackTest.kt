package com.halo.app.ui.workspace

import android.database.sqlite.SQLiteDatabase
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.security.MessageDigest
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Diagnostic readback only: an OK result never establishes cloud or voice acceptance. */
@RunWith(AndroidJUnit4::class)
class Issue55ScopedReadbackTest {
    @Test fun exportOnlyCampaignObjectsAndTheirSourceBindings() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val runId = InstrumentationRegistry.getArguments().getString("runId") ?: "issue55"
        require(runId.matches(Regex("[A-Za-z0-9._-]{1,80}")))
        val path = context.getDatabasePath("halo.db")
        assertTrue("An existing installed-app database is required", path.isFile)
        val report = JSONObject().put("kind", "SCOPED_DIAGNOSTIC_READBACK_NOT_CLOUD_ACCEPTANCE")
            .put("runId", runId).put("atMs", System.currentTimeMillis())
        SQLiteDatabase.openDatabase(path.path, null, SQLiteDatabase.OPEN_READONLY).use { db ->
            val objects = JSONArray()
            val studies = JSONArray()
            db.rawQuery("SELECT id,type,title,lifecycle FROM workspace_objects WHERE title LIKE ? OR title LIKE ? OR title LIKE ? ORDER BY createdAt",
                arrayOf("QA55%", "Голосовой тест%", "голосовой тест%")).use { cursor ->
                while (cursor.moveToNext()) {
                    val id = cursor.getString(0)
                    val type = cursor.getString(1)
                    val obj = JSONObject().put("id", id).put("type", type)
                        .put("title", cursor.getString(2)).put("lifecycle", cursor.getString(3))
                    if (type == "NOTE") db.rawQuery("SELECT body,revision FROM notes WHERE objectId=?", arrayOf(id)).use { note ->
                        if (note.moveToFirst()) obj.put("bodySha256", sha256(note.getString(0)))
                            .put("bodyChars", note.getString(0).length).put("revision", note.getLong(1))
                    }
                    if (type == "CANVAS") {
                        db.rawQuery("SELECT revision FROM canvas_documents WHERE objectId=?", arrayOf(id)).use { canvas ->
                            if (canvas.moveToFirst()) obj.put("revision", canvas.getLong(0))
                        }
                        val nodes = JSONArray()
                        db.rawQuery("SELECT id,type,refObjectId,text FROM canvas_nodes WHERE canvasId=? ORDER BY id", arrayOf(id)).use { node ->
                            while (node.moveToNext()) nodes.put(JSONObject().put("id", node.getString(0))
                                .put("type", node.getString(1)).put("refObjectId", node.getString(2) ?: JSONObject.NULL)
                                .put("textSha256", node.getString(3)?.let(::sha256) ?: JSONObject.NULL)
                                .put("textChars", node.getString(3)?.length ?: 0))
                        }
                        obj.put("nodes", nodes)
                    }
                    db.rawQuery("SELECT id,sourceObjectIdsJson,sourceRevision,tutorStage,lifecycle FROM study_sessions WHERE sourceObjectIdsJson LIKE ?",
                        arrayOf("%\"$id\"%")).use { study ->
                        while (study.moveToNext()) studies.put(JSONObject().put("id", study.getString(0))
                            .put("sourceObjectIdsJson", study.getString(1))
                            .put("sourceRevision", study.getString(2) ?: JSONObject.NULL)
                            .put("stage", study.getString(3)).put("lifecycle", study.getString(4)))
                    }
                    objects.put(obj)
                }
            }
            assertTrue("No campaign objects were found; this is not an acceptance PASS", objects.length() > 0)
            report.put("objects", objects).put("studyBindings", studies)
        }
        File(context.filesDir, "$runId-readback.json").writeText(report.toString(2))
    }

    private fun sha256(text: String): String = MessageDigest.getInstance("SHA-256")
        .digest(text.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it.toInt() and 255) }
}
