package com.halo.app.di.tools.host

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.halo.host.android.resolver.*
import com.halo.runtime.cognition.Outcome
import com.halo.runtime.cognition.tools.*
import com.halo.sdk.provider.ToolCall
import com.halo.cognition.language.NativeCommandParser
import com.halo.cognition.language.NativeOpenApplicationIntent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class ApplicationResolutionRegressionTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private fun resolver(vararg apps: ResolvedApp) = AndroidApplicationResolver(context) { apps.toList() }
    @Test fun exactNamesAndPackagesResolveDeterministically() = runTest {
        val r = resolver(ResolvedApp("com.spotify.music", "Spotify"), ResolvedApp("com.other.player", "Spotify Lite"))
        assertEquals("com.spotify.music", (r.resolveApplication("Spotify") as ApplicationResolution.Resolved).app.packageName)
        assertEquals("com.other.player", (r.resolveApplication("com.other.player") as ApplicationResolution.Resolved).app.packageName)
    }
    @Test fun aliasesOnlyResolveInstalledVisiblePackages() = runTest {
        val r = resolver(ResolvedApp("com.google.android.youtube", "Localized video app"))
        assertTrue(r.resolveApplication("YouTube") is ApplicationResolution.Resolved)
        assertTrue(r.resolveApplication("WhatsApp") is ApplicationResolution.NotFound)
    }
    @Test fun duplicateLauncherActivitiesAreOneAppAndDuplicateNamesAreAmbiguous() = runTest {
        val a = ResolvedApp("com.first.player", "Player")
        assertTrue(resolver(a, a).resolveApplication("Player") is ApplicationResolution.Resolved)
        assertTrue(resolver(a, ResolvedApp("com.second.player", "Player")).resolveApplication("Player") is ApplicationResolution.Ambiguous)
    }
    @Test fun partialNamesAreSuggestionsNeverAutomaticExecutionTargets() = runTest {
        assertTrue(resolver(ResolvedApp("com.test.player", "Music Player")).resolveApplication("Music") is ApplicationResolution.Ambiguous)
    }
    @Test fun missingAndUnavailableCataloguesAreDistinct() = runTest {
        assertEquals(ApplicationResolution.NotFound, resolver(ResolvedApp("com.test.app", "Test")).resolveApplication("Missing"))
        assertEquals(ApplicationResolution.VisibilityUnavailable, resolver().resolveApplication("Missing"))
        assertEquals(ApplicationResolution.VisibilityUnavailable,
            AndroidApplicationResolver(context) { throw SecurityException("private") }.resolveApplication("Test"))
    }
    @Test fun spokenRequestUsesParserThenExactInstalledMapping() = runTest {
        val intent = NativeCommandParser.parse("Would you please open Spotify?") as NativeOpenApplicationIntent
        val result = resolver(ResolvedApp("com.spotify.music", "Spotify")).resolveApplication(intent.application)
        assertEquals("com.spotify.music", (result as ApplicationResolution.Resolved).app.packageName)
    }
    @Test fun rejectionTaxonomyNeverLaunchesAndNeverLeaksQuery() = runTest {
        var launches = 0
        for ((resolution, code) in listOf(
            ApplicationResolution.NotFound to "APP_NOT_FOUND",
            ApplicationResolution.Ambiguous(listOf(ResolvedApp("com.one.app", "One"), ResolvedApp("com.two.app", "Two"))) to "APP_AMBIGUOUS",
            ApplicationResolution.VisibilityUnavailable to "APP_VISIBILITY_UNAVAILABLE")) {
            val tools = ApplicationTools(context, isForeground = { true }, resolveApplication = { resolution },
                isLaunchable = { true }, launch = { launches++; Outcome.Dispatched("sent") })
            val result = tools.execute(ToolCall("call", "halo_open_app", mapOf("appName" to "private spoken query"))) as ToolExecutionResult.Rejected
            assertTrue(result.reason.startsWith(code)); assertFalse(result.reason.contains("private spoken query"))
        }
        assertEquals(0, launches)
    }
    @Test fun missingExplicitPackageDoesNotDispatch() = runTest {
        var launches = 0
        val tools = ApplicationTools(context, isForeground = { true }, isLaunchable = { false },
            launch = { launches++; Outcome.Dispatched("sent") })
        val result = tools.execute(ToolCall("call", "halo_open_app", mapOf("package" to "com.absent.app"))) as ToolExecutionResult.Rejected
        assertTrue(result.reason.startsWith("APP_NOT_FOUND")); assertEquals(0, launches)
    }
}
