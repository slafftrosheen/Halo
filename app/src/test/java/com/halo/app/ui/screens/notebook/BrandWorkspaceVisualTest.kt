package com.halo.app.ui.screens.notebook

import android.app.Application
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.captureRoboImage
import com.halo.app.ui.theme.HaloTheme
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.junit.Assert.assertEquals

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [33], application = Application::class, qualifiers = "w360dp-h500dp")
@OptIn(ExperimentalLayoutApi::class)
class BrandWorkspaceVisualTest {
    @org.junit.Before fun useStillMotionForCapture() {
        android.provider.Settings.Global.putFloat(org.robolectric.RuntimeEnvironment.getApplication().contentResolver,
            android.provider.Settings.Global.ANIMATOR_DURATION_SCALE, 0f)
    }

    private fun capture(dark: Boolean, scale: Float = 0f) = captureRoboImage {
        HaloTheme(darkTheme = dark, fontScale = scale) {
            Surface {
                Column(Modifier.width(360.dp).height(500.dp)) {
                    EditorWorkspaceHeader("My ideas", false, {}, {}, {})
                    FlowRow {
                        NotebookCreateAction("Note", com.halo.app.R.drawable.halo_ic_new_note, true, onClick = {})
                        NotebookCreateAction("Canvas", com.halo.app.R.drawable.halo_ic_canvas, true, onClick = {})
                        NotebookCreateAction("Import", com.halo.app.R.drawable.halo_ic_import, true, onClick = {})
                        NotebookCreateAction("Inbox", com.halo.app.R.drawable.halo_ic_inbox, true, onClick = {})
                    }
                    Spacer(Modifier.weight(1f))
                    CanvasBrandDock(true, false, {}, {}, {}, {}, {}, {}, {})
                }
            }
        }
    }
    @Test fun darkWorkspace() = capture(true)
    @Test fun lightWorkspace() = capture(false)
    @Test @Config(qualifiers = "ru-rRU-w360dp-h500dp") fun russianLarge() = capture(false, 2f)
    @Test fun generatedTitlesUseReadableContentWithoutChangingNamedItems() {
        assertEquals("My title", workspaceDisplayTitle("My title", "Other text", "Note"))
        assertEquals("First idea", workspaceDisplayTitle("memo_abcd123", "\n# First idea\nSecond idea", "Note"))
        assertEquals("Canvas", workspaceDisplayTitle("Untitled canvas", null, "Canvas"))
    }
    @Test @Config(qualifiers = "ru-rRU-w320dp-h450dp") fun longLibraryMetadata() = captureRoboImage {
        HaloTheme(darkTheme = false, fontScale = 2f) {
            Surface(Modifier.width(320.dp)) {
                WorkspaceObjectRow(
                    NotebookWorkspaceItem(com.halo.app.data.notebook.WorkspaceObject(
                        com.halo.app.data.notebook.WorkspaceObjectId("preview-note"),
                        com.halo.app.data.notebook.WorkspaceObjectType.NOTE,
                        "A longer title for a saved note", 0, 0,
                        com.halo.app.data.notebook.WorkspaceObjectSource.USER_CREATED,
                        com.halo.app.data.notebook.WorkspaceObjectLifecycle.ACTIVE, true, "preview"), "A readable note preview"),
                    true, true, emptyList(), false, {}, {}, {}, {}, {}, {})
            }
        }
    }
}
