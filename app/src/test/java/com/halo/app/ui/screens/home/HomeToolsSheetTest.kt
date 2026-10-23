package com.halo.app.ui.screens.home

import android.app.Application
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.CompositionLocalProvider
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.halo.app.ui.theme.HostlessHaloTheme
import kotlinx.coroutines.CompletableDeferred
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class, qualifiers = "w360dp-h800dp")
class HomeToolsSheetTest {
    @get:Rule val compose = createComposeRule()
    private fun snapshot(id: String, status: HomeToolStatus = HomeToolStatus.READY,
        reason: HomeToolReason = HomeToolReason.READY, recovery: HomeToolRecovery = HomeToolRecovery.NONE) =
        HomeToolCatalogSnapshot(listOf(HomeToolEntry(HomeToolCatalog.descriptors.single { it.id == id }, status, reason, recovery)), false)

    @Test fun orbNotebookHandoffOpensExistingDetailWithoutResolvingUntilExplicitUse() {
        var resolved = 0; var action: HomeToolAction? = null
        compose.setContent { HostlessHaloTheme {
            HomeToolsSheet(snapshot("work.notebook"), false, {}, { resolved++; HomeToolAction.NOTEBOOK },
                { action = it }, {}, {}, initialGroup = HomeToolGroup.WORK, initialEntry = "work.notebook")
        } }
        compose.onNodeWithText("Open Workspace").assertExists()
        assertEquals(0, resolved); assertNull(action)
        compose.onNodeWithText("Open Workspace").performScrollTo().performClick()
        compose.waitForIdle()
        assertEquals(1, resolved); assertEquals(HomeToolAction.NOTEBOOK, action)
    }

    @Test fun browsingAndPlannedDetailsNeverResolveOrExecute() {
        var resolved = 0; var actions = 0
        compose.setContent { HostlessHaloTheme {
            HomeToolsSheet(snapshot("work.python", HomeToolStatus.IN_DEVELOPMENT, HomeToolReason.PYTHON), false,
                {}, { resolved++; HomeToolAction.PLANNED }, { actions++ }, {}, {})
        } }
        compose.onNodeWithText("Work").performClick()
        compose.onNodeWithText("Python / data sandbox").performClick()
        compose.onAllNodesWithText("In development. No Python code runs here. A future data sandbox must be isolated and require an explicit run action.")
            .assertCountEquals(1)
        compose.onNodeWithText("Prepare a message").assertDoesNotExist()
        compose.onNodeWithText("Close").performScrollTo().assertIsDisplayed()
        assertEquals(0, resolved); assertEquals(0, actions)
    }
    @Test fun explicitUseRechecksAndDoesNotExecuteRevokedAction() {
        var resolved = 0; var actions = 0
        compose.setContent { HostlessHaloTheme {
            HomeToolsSheet(snapshot("create.note"), false, {}, { resolved++; null }, { actions++ }, {}, {})
        } }
        compose.onNodeWithText("New note").performClick()
        assertEquals(0, resolved)
        compose.onNodeWithText("Create note").performScrollTo().performClick()
        compose.waitForIdle()
        assertEquals(1, resolved); assertEquals(0, actions)
        compose.onNodeWithText("Availability changed. Review the current setup before continuing.").assertExists()
    }
    @Test fun stopIntentCannotTurnIntoStartWhileAvailabilityIsRead() {
        val active = mutableStateOf(true)
        val pending = CompletableDeferred<HomeToolAction?>()
        val actions = mutableListOf<HomeToolAction>()
        compose.setContent { HostlessHaloTheme {
            HomeToolsSheet(snapshot("phone.voice"), active.value, {}, { pending.await() }, { actions += it }, {}, {})
        } }
        compose.onNodeWithText("On this phone").performClick()
        compose.onNodeWithText("Voice Mode").performClick()
        compose.onNodeWithText("End Voice Mode").performScrollTo().performClick()
        compose.runOnIdle { active.value = false; pending.complete(HomeToolAction.VOICE_MODE) }
        compose.waitForIdle()
        assertEquals(listOf(HomeToolAction.END_VOICE), actions)
    }
    @Test fun dismissalCancelsPendingUse() {
        val visible = mutableStateOf(true)
        val pending = CompletableDeferred<HomeToolAction?>()
        var actions = 0
        compose.setContent { HostlessHaloTheme { if (visible.value)
            HomeToolsSheet(snapshot("create.note"), false, {}, { pending.await() }, { actions++ }, {}, { visible.value = false })
        } }
        compose.onNodeWithText("New note").performClick()
        compose.onNodeWithText("Create note").performScrollTo().performClick()
        compose.onNodeWithText("Close").performScrollTo().performClick()
        compose.waitForIdle()
        compose.runOnIdle { pending.complete(HomeToolAction.NEW_NOTE) }
        compose.waitForIdle()
        assertEquals(0, actions)
    }
    @Test fun setupOpensOnlyTheAdvertisedRecovery() {
        var recovery: HomeToolRecovery? = null
        compose.setContent { HostlessHaloTheme(fontScale = 2f) {
            HomeToolsSheet(snapshot("create.note", HomeToolStatus.NEEDS_SETUP, HomeToolReason.DISABLED, HomeToolRecovery.SKILLS),
                false, {}, { error("No execution") }, { error("No action") }, { recovery = it }, {})
        } }
        compose.onNodeWithText("New note").performClick()
        val layouts = mutableListOf<androidx.compose.ui.text.TextLayoutResult>()
        compose.onNodeWithText("New note").performSemanticsAction(
            androidx.compose.ui.semantics.SemanticsActions.GetTextLayoutResult) { it(layouts) }
        assertEquals(2f, layouts.single().layoutInput.density.fontScale, 0.01f)
        compose.onNodeWithText("Open setup").performScrollTo().performClick()
        assertEquals(HomeToolRecovery.SKILLS, recovery)
    }
    @Test fun stoppingHomeCancelsPendingStartWithoutDisposingSheet() {
        val owner = object : LifecycleOwner { override val lifecycle = LifecycleRegistry(this) }
        compose.runOnIdle { owner.lifecycle.currentState = Lifecycle.State.RESUMED }
        val pending = CompletableDeferred<HomeToolAction?>()
        var actions = 0
        compose.setContent { CompositionLocalProvider(LocalLifecycleOwner provides owner) { HostlessHaloTheme {
            HomeToolsSheet(snapshot("phone.voice"), false, {}, { pending.await() }, { actions++ }, {}, {})
        } } }
        compose.onNodeWithText("On this phone").performClick()
        compose.onNodeWithText("Voice Mode").performClick()
        compose.onNodeWithText("Start Voice Mode").performScrollTo().performClick()
        compose.runOnIdle { owner.lifecycle.currentState = Lifecycle.State.CREATED }
        compose.waitForIdle()
        compose.runOnIdle { pending.complete(HomeToolAction.VOICE_MODE) }
        compose.waitForIdle()
        assertEquals(0, actions)
    }
    @Test fun importedPdfOnlyOffersStudyAndNeverAttachesOnOpen() {
        var attached = 0; var study = 0
        compose.setContent { HostlessHaloTheme(fontScale = 2f) {
            HomeImportedSourceDialog(HomeImportedSource("id", "Reading.pdf", HomeImportedSourceKind.PDF),
                { attached++ }, { study++ }, {}, {})
        } }
        assertEquals(0, attached); assertEquals(0, study)
        compose.onNodeWithText("Attach to this chat").assertDoesNotExist()
        compose.onNodeWithText("Open Study").performScrollTo().performClick()
        assertEquals(1, study); assertEquals(0, attached)
    }
}
