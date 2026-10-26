package com.halo.app.ui.theme

import android.app.Application
import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Actual owner locks and SharedPreferences compensation; these regression sources are NOT_RUN. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
class AppearanceApplyRollbackTest {
    private lateinit var base: Context
    private val original = HaloPalettes(
        HaloPalette(HaloAccent.SAGE, HaloTone.PAPER, HaloBackgroundStyle.STILL),
        HaloPalette(HaloAccent.LILAC, HaloTone.COOL, HaloBackgroundStyle.MIST))
    private val requested = HaloPalettes(
        HaloPalette(HaloAccent.BLUE, HaloTone.WARM, HaloBackgroundStyle.MIST),
        HaloPalette(HaloAccent.ROSE, HaloTone.SLATE, HaloBackgroundStyle.HALO))

    @Before fun reset() = runBlocking {
        base = ApplicationProvider.getApplicationContext<Application>()
        base.getSharedPreferences("halo_appearance", Context.MODE_PRIVATE).edit().clear().commit()
        base.getSharedPreferences("halo_palettes", Context.MODE_PRIVATE).edit().clear().commit()
        AppearancePreferences(base).setModePersisted(AppearanceMode.LIGHT)
        PalettePreferences(base).selectPalettesPersisted(original)
        // Compensation must touch only its own fields, including on a failed disk write.
        base.getSharedPreferences("halo_appearance", Context.MODE_PRIVATE).edit()
            .putStringSet("orb_shortcuts", setOf("FIND")).commit()
        base.getSharedPreferences("halo_palettes", Context.MODE_PRIVATE).edit()
            .putString("unrelated", "keep").commit()
        Unit
    }

    @Test fun failedModeCommitRestoresOriginalIndependentPalettesAndMode() = runBlocking {
        val context = InterceptedContext(base)
        val palettes = PalettePreferences(context)
        val appearance = AppearancePreferences(context)
        palettes.awaitReady(); appearance.awaitLoaded()
        context.intercept = { name, count, commit ->
            val stored = commit()
            if (name == "halo_appearance" && count == 1) false else stored
        }
        val failure = failureOf { apply(appearance, palettes) }
        assertFalse(failure is AppearanceApplyException && failure.rollbackUncertain)
        assertOriginalOwnersAndDisk(appearance, palettes)
        assertEquals(2, context.commits["halo_palettes"])
        assertEquals(2, context.commits["halo_appearance"])
    }

    @Test fun cancellationDuringModeCommitCompensatesBothStoresBeforeReturning() = runBlocking {
        val context = InterceptedContext(base)
        val palettes = PalettePreferences(context)
        val appearance = AppearancePreferences(context)
        palettes.awaitReady(); appearance.awaitLoaded()
        lateinit var applying: Job
        context.intercept = { name, count, commit ->
            val stored = commit()
            if (name == "halo_appearance" && count == 1) applying.cancel(CancellationException("synthetic cancellation"))
            stored
        }
        applying = launch(start = CoroutineStart.LAZY) { apply(appearance, palettes) }
        applying.start(); applying.join()
        assertTrue(applying.isCancelled)
        assertOriginalOwnersAndDisk(appearance, palettes)
    }

    @Test fun rollbackFailureStaysUncertainUntilSuccessfulReapply() = runBlocking {
        val context = InterceptedContext(base)
        val palettes = PalettePreferences(context)
        val appearance = AppearancePreferences(context)
        palettes.awaitReady(); appearance.awaitLoaded()
        context.intercept = { name, count, commit ->
            when {
                name == "halo_appearance" && count == 1 -> { commit(); false }
                name == "halo_palettes" && count == 2 -> false // The old pair cannot be durably confirmed.
                else -> commit()
            }
        }
        val failure = failureOf { apply(appearance, palettes) }
        assertTrue(failure is AppearanceApplyException)
        assertTrue((failure as AppearanceApplyException).rollbackUncertain)
        assertTrue(appearance.applyUncertain.value)
        assertEquals(original, palettes.state.value)
        assertEquals(AppearanceMode.LIGHT, appearance.mode.value)
        // Last-known owner state is retained; disk truth is explicitly uncertain.
        assertEquals(HaloAccent.BLUE.name, base.getSharedPreferences("halo_palettes", Context.MODE_PRIVATE)
            .getString("light.accent", null))
        context.intercept = { _, _, commit -> commit() }
        apply(appearance, palettes)
        assertFalse(appearance.applyUncertain.value)
        assertEquals(requested, palettes.state.value)
        assertEquals(AppearanceMode.DARK, appearance.mode.value)
        val reconciled = PalettePreferences(base)
        reconciled.awaitReady()
        assertEquals(requested, reconciled.state.value)
    }

    @Test fun failedPaletteCommitDoesNotAttemptModeAndRestoresItsOriginalPair() = runBlocking {
        val context = InterceptedContext(base)
        val palettes = PalettePreferences(context)
        val appearance = AppearancePreferences(context)
        palettes.awaitReady(); appearance.awaitLoaded()
        context.intercept = { name, count, commit ->
            val stored = commit()
            if (name == "halo_palettes" && count == 1) false else stored
        }
        failureOf { apply(appearance, palettes) }
        assertOriginalOwnersAndDisk(appearance, palettes)
        assertNull(context.commits["halo_appearance"])
        assertEquals(2, context.commits["halo_palettes"])
    }

    @Test fun cancellationWithUnacknowledgedCompensationIsReportedAsUncertain() = runBlocking {
        val context = InterceptedContext(base)
        val palettes = PalettePreferences(context)
        val appearance = AppearancePreferences(context)
        palettes.awaitReady(); appearance.awaitLoaded()
        context.intercept = { name, count, commit ->
            when {
                name == "halo_appearance" && count == 1 -> { commit(); throw CancellationException("synthetic mode write") }
                name == "halo_palettes" && count == 2 -> false
                else -> commit()
            }
        }
        val failure = failureOf { apply(appearance, palettes) }
        assertTrue(failure is AppearanceApplyException && failure.rollbackUncertain)
        assertTrue(appearance.applyUncertain.value)
        assertEquals(original, palettes.state.value)
        assertEquals(AppearanceMode.LIGHT, appearance.mode.value)
    }

    @Test fun newerPaletteSelectionSurvivesFailedApplyAndItsQueuedWrite() = runBlocking {
        val context = InterceptedContext(base)
        val palettes = PalettePreferences(context)
        val appearance = AppearancePreferences(context)
        palettes.awaitReady(); appearance.awaitLoaded()
        val newer = HaloPalette(HaloAccent.CORAL, HaloTone.NATURAL, HaloBackgroundStyle.STILL)
        context.intercept = { name, count, commit ->
            val stored = commit()
            if (name == "halo_appearance" && count == 1) palettes.select(false, newer)
            stored
        }
        failureOf { apply(appearance, palettes) }
        palettes.awaitReady()
        val expected = original.copy(light = newer)
        assertEquals(expected, palettes.state.value)
        val restored = PalettePreferences(base)
        restored.awaitReady()
        assertEquals(expected, restored.state.value)
        assertEquals(AppearanceMode.LIGHT, appearance.mode.value)
    }

    @Test fun newerModeSelectionSurvivesCompensationAndItsQueuedWrite() = runBlocking {
        val context = InterceptedContext(base)
        val palettes = PalettePreferences(context)
        val appearance = AppearancePreferences(context)
        palettes.awaitReady(); appearance.awaitLoaded()
        context.intercept = { name, count, commit ->
            val stored = commit()
            if (name == "halo_appearance" && count == 1) appearance.setMode(AppearanceMode.SYSTEM)
            stored
        }
        failureOf { apply(appearance, palettes) }
        appearance.awaitPersistence()
        assertEquals(AppearanceMode.SYSTEM, appearance.mode.value)
        val restored = AppearancePreferences(base)
        restored.awaitLoaded()
        assertEquals(AppearanceMode.SYSTEM, restored.mode.value)
        assertEquals(original, palettes.state.value)
    }

    @Test fun staleSnapshotIsRejectedWithoutWritingEitherStore() = runBlocking {
        val context = InterceptedContext(base)
        val palettes = PalettePreferences(context)
        val appearance = AppearancePreferences(context)
        palettes.awaitReady(); appearance.awaitLoaded()
        failureOf { appearance.applyAppearancePersisted(AppearanceMode.LIGHT, AppearanceMode.DARK,
            palettes, HaloPalettes(), requested) }
        assertTrue(context.commits.isEmpty())
        assertOriginalOwnersAndDisk(appearance, palettes)
    }

    private suspend fun apply(appearance: AppearancePreferences, palettes: PalettePreferences) =
        appearance.applyAppearancePersisted(AppearanceMode.LIGHT, AppearanceMode.DARK, palettes, original, requested)

    private suspend fun failureOf(block: suspend () -> Unit): Exception {
        try { block() } catch (failure: Exception) { return failure }
        throw AssertionError("Expected Apply to fail")
    }

    private suspend fun assertOriginalOwnersAndDisk(appearance: AppearancePreferences, palettes: PalettePreferences) {
        assertEquals(AppearanceMode.LIGHT, appearance.mode.value)
        assertEquals(original, palettes.state.value)
        assertFalse(appearance.applyUncertain.value)
        val restoredMode = AppearancePreferences(base)
        val restoredPalettes = PalettePreferences(base)
        restoredMode.awaitLoaded(); restoredPalettes.awaitReady()
        assertEquals(AppearanceMode.LIGHT, restoredMode.mode.value)
        assertEquals(original, restoredPalettes.state.value)
        assertEquals(setOf("FIND"), base.getSharedPreferences("halo_appearance", Context.MODE_PRIVATE)
            .getStringSet("orb_shortcuts", null))
        assertEquals("keep", base.getSharedPreferences("halo_palettes", Context.MODE_PRIVATE).getString("unrelated", null))
    }

    /** Real disk-backed preferences, with a bounded commit failure/cancellation seam. */
    private class InterceptedContext(base: Context) : ContextWrapper(base) {
        val commits = java.util.concurrent.ConcurrentHashMap<String, Int>()
        var intercept: (String, Int, () -> Boolean) -> Boolean = { _, _, commit -> commit() }
        override fun getApplicationContext(): Context = this
        override fun getSharedPreferences(name: String, mode: Int): SharedPreferences {
            val actual = super.getSharedPreferences(name, mode)
            if (name !in setOf("halo_appearance", "halo_palettes")) return actual
            return object : SharedPreferences by actual {
                override fun edit(): SharedPreferences.Editor {
                    val editor = actual.edit()
                    return object : SharedPreferences.Editor by editor {
                        override fun commit(): Boolean {
                            val count = commits.merge(name, 1) { previous, increment -> previous + increment }!!
                            return intercept(name, count) { editor.commit() }
                        }
                        // Preserve this wrapper when the owners chain editor calls.
                        override fun putString(key: String, value: String?): SharedPreferences.Editor {
                            editor.putString(key, value)
                            return this
                        }
                    }
                }
            }
        }
    }
}
