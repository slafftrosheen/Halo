package com.halo.app.ui.presence

import android.app.Application
import androidx.compose.runtime.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.halo.app.ui.icons.HaloGlyph
import com.halo.app.ui.theme.HostlessHaloTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
class ShellPageActionsTest {
    @get:Rule val compose = createComposeRule()

    @Test fun callbackOnlyUpdatesKeepProjectionStableAndDispatchLatestCallback() {
        val holder = mutableStateOf<ShellPageProjection?>(null)
        val target = mutableStateOf("first draft")
        val calls = mutableListOf<String>()
        var compositions = 0
        compose.setContent { HostlessHaloTheme {
            CompositionLocalProvider(LocalShellPageActions provides holder) {
                val latestDraft = target.value
                BindShellPageActions(ShellPageOwner("note", "note-1", "7"), listOf(
                    ShellPageAction("find", "Find and replace", HaloGlyph.SEARCH) { calls += latestDraft }))
                SideEffect { compositions++ }
            }
        } }
        lateinit var original: ShellPageProjection
        compose.runOnIdle { original = holder.value!!; assertTrue(original.invoke("find")); target.value = "updated draft" }
        compose.runOnIdle { assertSame(original, holder.value); assertTrue(original.invoke("find")) }
        assertEquals(listOf("first draft", "updated draft"), calls)
        assertTrue("Callback recomposition must settle without a holder/projection loop", compositions < 10)
    }

    @Test fun disabledRemovedAndDifferentExactSourceActionsCannotUseCachedProjection() {
        val holder = mutableStateOf<ShellPageProjection?>(null)
        val owner = mutableStateOf(ShellPageOwner("canvas", "board-1", "revision-1"))
        val enabled = mutableStateOf(true)
        val present = mutableStateOf(true)
        var calls = 0
        compose.setContent { HostlessHaloTheme {
            CompositionLocalProvider(LocalShellPageActions provides holder) {
                BindShellPageActions(owner.value, if (present.value) listOf(
                    ShellPageAction("fit", "Fit all cards", HaloGlyph.NOTE, enabled.value) { calls++ }) else emptyList())
            }
        } }
        lateinit var cached: ShellPageProjection
        compose.runOnIdle { cached = holder.value!!; enabled.value = false }
        compose.runOnIdle { assertFalse(cached.invoke("fit")); enabled.value = true; owner.value = owner.value.copy(sourceIdentity = "revision-2") }
        compose.runOnIdle { assertFalse(cached.invoke("fit")); assertTrue(holder.value!!.invoke("fit")); cached = holder.value!!; owner.value = owner.value.copy(objectId = "board-2") }
        compose.runOnIdle { assertFalse(cached.invoke("fit")); present.value = false }
        compose.runOnIdle { assertTrue(holder.value!!.actions.isEmpty()); assertFalse(holder.value!!.invoke("fit")); assertFalse(holder.value!!.invoke("unknown")) }
        assertEquals(1, calls)
    }

    @Test fun disposalClearsOnlyOwnProjectionAndInvalidatesPreviouslyCapturedCallbacks() {
        val holder = mutableStateOf<ShellPageProjection?>(null)
        val visible = mutableStateOf(true)
        var calls = 0
        compose.setContent { HostlessHaloTheme {
            CompositionLocalProvider(LocalShellPageActions provides holder) {
                if (visible.value) BindShellPageActions(ShellPageOwner("studio", "project-1", "1"), listOf(
                    ShellPageAction("layers", "Layers", HaloGlyph.NOTE) { calls++ }))
            }
        } }
        lateinit var cached: ShellPageProjection
        val replacement = ShellPageProjection(ShellPageOwner("activity"), emptyList()) { false }
        compose.runOnIdle { cached = holder.value!!; holder.value = replacement; visible.value = false }
        compose.runOnIdle { assertSame(replacement, holder.value); assertFalse(cached.invoke("layers")) }
        assertEquals(0, calls)
    }

    @Test fun disposingCurrentPageRemovesItsProjectionWithoutDispatchOrReplay() {
        val holder = mutableStateOf<ShellPageProjection?>(null)
        val visible = mutableStateOf(true)
        var calls = 0
        compose.setContent { HostlessHaloTheme {
            CompositionLocalProvider(LocalShellPageActions provides holder) {
                if (visible.value) BindShellPageActions(ShellPageOwner("devices"), listOf(
                    ShellPageAction("phone", "This phone", HaloGlyph.PHONE) { calls++ }))
            }
        } }
        compose.runOnIdle { assertNotNull(holder.value); visible.value = false }
        compose.runOnIdle { assertNull(holder.value) }
        assertEquals(0, calls)
    }

    @Test fun animatedOutgoingAndIncomingPagesPublishOnlyCurrentExactOwner() {
        val holder = mutableStateOf<ShellPageProjection?>(null)
        val current = mutableStateOf("notebook")
        val filter: (ShellPageOwner) -> Boolean = { owner ->
            owner.routeFamily == current.value && (owner.routeFamily != "studio" || owner.objectId == "project-1")
        }
        val calls = mutableListOf<String>()
        compose.setContent { HostlessHaloTheme {
            CompositionLocalProvider(LocalShellPageActions provides holder, LocalShellPageOwnerFilter provides filter) {
                BindShellPageActions(ShellPageOwner("notebook"), listOf(
                    ShellPageAction("find", "Find related notes", HaloGlyph.SEARCH) { calls += "notebook" }))
                BindShellPageActions(ShellPageOwner("studio", "project-1"), listOf(
                    ShellPageAction("layers", "Layers", HaloGlyph.NOTE) { calls += "studio" }))
                BindShellPageActions(ShellPageOwner("studio", "wrong-project"), listOf(
                    ShellPageAction("layers", "Wrong source", HaloGlyph.NOTE) { calls += "wrong-project" }))
            }
        } }
        lateinit var outgoing: ShellPageProjection
        compose.runOnIdle { outgoing = holder.value!!; assertEquals("notebook", outgoing.owner.routeFamily); current.value = "studio" }
        compose.runOnIdle {
            assertFalse(outgoing.invoke("find"))
            assertEquals(ShellPageOwner("studio", "project-1"), holder.value!!.owner)
            assertTrue(holder.value!!.invoke("layers"))
            current.value = "unsupported"
        }
        compose.runOnIdle { assertNull(holder.value) }
        assertEquals(listOf("studio"), calls)
    }

    @Test fun sameFamilyAndSameObjectTransitionStillRequiresExactBackStackEntry() {
        val holder = mutableStateOf<ShellPageProjection?>(null)
        val entry = mutableStateOf("old-entry")
        val called = mutableListOf<String>()
        val filter: (ShellPageOwner) -> Boolean = { it.navigationEntryId == entry.value }
        compose.setContent { HostlessHaloTheme {
            CompositionLocalProvider(LocalShellPageActions provides holder, LocalShellPageOwnerFilter provides filter) {
                listOf("old-entry", "new-entry").forEach { id ->
                    BindShellPageActions(ShellPageOwner("studio", "same-project", "same-source", id), listOf(
                        ShellPageAction("layers", "Layers", HaloGlyph.NOTE) { called += id }))
                }
            }
        } }
        lateinit var old: ShellPageProjection
        compose.runOnIdle { old = holder.value!!; assertEquals("old-entry", old.owner.navigationEntryId); entry.value = "new-entry" }
        compose.runOnIdle { assertFalse(old.invoke("layers")); assertEquals("new-entry", holder.value!!.owner.navigationEntryId); assertTrue(holder.value!!.invoke("layers")) }
        assertEquals(listOf("new-entry"), called)
    }
}
