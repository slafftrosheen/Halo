package com.halo.app.ui.shell

import android.content.Intent
import android.os.SystemClock
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.StaleObjectException
import androidx.test.uiautomator.UiDevice
import com.halo.app.MainActivity
import com.halo.app.di.ManualDependencyGraph
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Assume.assumeFalse
import org.junit.Test
import org.junit.runner.RunWith

/** Actual shell, IME/accessibility input and encrypted draft owner; no Send, mic or provider.
 * Creates only owned QA conversations and restores the previously selected conversation.
 * Canonical clear is an explicit fixture mutation, not a claimed real submission receipt.
 */
@RunWith(AndroidJUnit4::class)
class MainActivityComposerPhysicalTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val device get() = UiDevice.getInstance(instrumentation)
    private val context get() = instrumentation.targetContext
    private val fieldSelector get() = By.clazz("android.widget.EditText").pkg(context.packageName)

    @Test fun canonicalClearUpdatesTheRealComposerAndCannotReplaceANewerEdit() = withQaConversation { id ->
        val owner = ManualDependencyGraph.composerDraftStore
        val literal = "HALO-QA-COMPOSER-" + "A complete editable local draft. ".repeat(10)
        val field = device.findObject(fieldSelector)
        field.click()
        field.text = literal
        eventually("Full IME text did not reach the canonical owner") { owner.current(id)?.text == literal }
        assertEquals(literal, device.findObject(fieldSelector).text)

        val sent = requireNotNull(owner.current(id))
        val cleared = owner.replaceIfCurrent(id, sent.revision, sent.text, "")
        assertNotNull(cleared)
        eventually("Canonical clear did not reach the visible shell composer") {
            device.findObject(fieldSelector)?.text == ""
        }
        val newer = "HALO-QA-COMPOSER-newer-unsent-edit"
        device.findObject(fieldSelector).text = newer
        eventually("Newer edit did not reach its owner") { owner.current(id)?.text == newer }
        assertNull(owner.replaceIfCurrent(id, cleared!!, "", literal))
        assertEquals(newer, device.findObject(fieldSelector).text)
        assertEquals(newer, owner.current(id)?.text)
    }

    @Test fun hardwareKeyBurstPreservesTheWholeLiteralInTheRealShell() = withQaConversation { id ->
        val literal = "HALO-QA-KEYS-" + (0 until 80).joinToString("-") { it.toString().padStart(3, '0') }
        val field = device.findObject(fieldSelector)
        field.click()
        device.executeShellCommand("input text $literal")
        eventually("Hardware key burst was shortened in the visible composer") {
            device.findObject(fieldSelector)?.text == literal
        }
        assertEquals(literal, ManualDependencyGraph.composerDraftStore.current(id)?.text)
    }

    private fun withQaConversation(check: (String) -> Unit) {
        val intent = Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            .putExtra(HaloScreenCatalog.EXTRA_SCREEN, "home")
        ActivityScenario.launch<MainActivity>(intent).use {
            runBlocking {
                withTimeout(30_000) { ManualDependencyGraph.kidsPreferences.loadedState.first { it } }
                ManualDependencyGraph.conversationStore.awaitReady()
            }
            assumeFalse("Preserve Kids policy", ManualDependencyGraph.kidsPreferences.enabled.value)
            assumeFalse("Preserve unfinished setup", ManualDependencyGraph.setupProgress.shouldAutoLaunch())
            eventually("Home composer was not rendered") { device.hasObject(fieldSelector) }
            val conversations = ManualDependencyGraph.conversationStore
            val previous = conversations.activeConversationId()
            val home = ManualDependencyGraph.homeViewModel
            instrumentation.runOnMainSync { home.newConversation() }
            runBlocking { withTimeout(30_000) { home.conversationChanging.first { changing -> !changing } } }
            val id = requireNotNull(conversations.activeConversationId())
            assertNotEquals(previous, id)
            try {
                eventually("New QA conversation did not reach the visible composer") {
                    device.findObject(fieldSelector)?.text == "" &&
                        home.uiState.value.conversationId == id
                }
                check(id)
            } finally {
                ownerClear(id)
                if (previous != null) {
                    instrumentation.runOnMainSync { home.openConversation(previous) }
                    runBlocking { withTimeout(30_000) { home.conversationChanging.first { changing -> !changing } } }
                }
            }
        }
    }

    private fun ownerClear(id: String) {
        val owner = ManualDependencyGraph.composerDraftStore
        owner.current(id)?.let { owner.replaceIfCurrent(id, it.revision, it.text, "") }
    }

    private fun eventually(message: String, predicate: () -> Boolean) {
        val until = SystemClock.uptimeMillis() + 20_000
        while (SystemClock.uptimeMillis() < until) {
            // Navigation replaces accessibility nodes between lookup and property read.
            // Retry the observation, preserving assertion failures and all owner guards.
            try { if (predicate()) return } catch (_: StaleObjectException) { }
            SystemClock.sleep(100)
        }
        val matched = try { predicate() } catch (_: StaleObjectException) { false }
        assertTrue(message, matched)
    }
}
