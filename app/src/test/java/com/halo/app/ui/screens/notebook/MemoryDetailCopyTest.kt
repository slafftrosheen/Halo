package com.halo.app.ui.screens.notebook

import android.app.Application
import android.content.Context
import android.content.res.Configuration
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import com.halo.app.data.memory.MemoryRecord
import com.halo.app.data.memory.MemoryStatus
import com.halo.app.language.*
import com.halo.app.ui.theme.HostlessHaloTheme
import com.halo.runtime.cognition.memory.*
import com.halo.sdk.runtime.Storage
import java.time.Instant
import java.time.ZoneId
import java.util.Locale
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = Application::class)
class MemoryDetailCopyTest {
    @get:Rule val compose = createComposeRule()
    private val now = Instant.parse("2026-10-07T12:00:00Z").toEpochMilli()
    private val zone = ZoneId.of("Europe/Riga")
    private fun record() = MemoryRecord("private-id", "Fact", MemoryKind.FACT, MemoryScope.GLOBAL,
        null, now, now, null, null, null, MemoryStatus.ACTIVE,
        origin = MemoryProposalOrigin.USER_EXPLICIT, lastConfirmedAt = now, userConfirmed = true)

    @Test fun fixedMetadataChangesLanguageWithoutTranslatingFactSourceOrRecord() {
        val language = AppLanguagePreferences(MemoryStorage(), "en")
        val original = record()
        val item = MemoryPresentation.item(original, now = now)
        compose.setContent { AppLanguageProvider(language) { HostlessHaloTheme {
            val copy = memoryDetailCopy(item, now, zone)
            Column {
                listOf("kind" to copy.kind, "origin" to copy.origin, "approval" to copy.confirmation,
                    "confidence" to copy.confidence, "scope" to copy.scope, "usage" to copy.usage,
                    "confirmed" to requireNotNull(copy.lastConfirmed)).forEach { (tag, value) -> Text(value, Modifier.testTag(tag)) }
                Text(item.record.content, Modifier.testTag("fact"))
                // The evidence kind is owned copy; an identical source title is content.
                Text(uiText(MemoryPresentation.evidence(MemoryEvidenceKind.USER_ACTION)), Modifier.testTag("evidence"))
                Text("Approved by you", Modifier.testTag("source-title"))
            }
        } } }
        val context = ApplicationProvider.getApplicationContext<Context>()
        for (tag in listOf("en", "ru", "lv", "pl")) {
            compose.runOnIdle { language.select(AppLanguage.fromTag(tag)) }
            val resources = context.createConfigurationContext(Configuration(context.resources.configuration).apply {
                setLocale(Locale.forLanguageTag(tag))
            }).resources
            mapOf("kind" to "Fact", "origin" to "You asked Halo to remember this", "approval" to "Approved by you",
                "confidence" to "User stated", "scope" to "Everywhere", "usage" to "Not used yet",
                "evidence" to "Approved by you").forEach { (node, key) ->
                compose.onNodeWithTag(node).assertTextEquals(resolveUiText(resources, key))
            }
            compose.onNodeWithTag("confirmed").assertTextEquals(resolveUiText(resources, "Last confirmed %1\$s", resolveUiText(resources, "Today")))
            compose.onNodeWithTag("fact").assertTextEquals("Fact")
            compose.onNodeWithTag("source-title").assertTextEquals("Approved by you")
            assertSame(original, item.record)
            assertEquals("Fact", original.content)
            assertEquals(now, original.lastConfirmedAt)
            assertEquals(0L, original.usageCount)
            assertTrue(original.userConfirmed)
        }
    }

    @Test fun namedScopeAndUsedSummaryStayLiteralAndDatesUseAppLocaleInsteadOfDeviceDefault() {
        val previousLocale = Locale.getDefault()
        Locale.setDefault(Locale.JAPANESE)
        try {
            val language = AppLanguagePreferences(MemoryStorage(), "ru")
            val original = record().copy(scope = MemoryScope.PROJECT, scopeId = "scope-id", usageCount = 2,
                lastUsedAt = now, lastConfirmedAt = now - 86400000)
            val item = MemoryPresentation.item(original, "Everywhere", now)
            val old = now - 10 * 86400000L
            compose.setContent { AppLanguageProvider(language) { HostlessHaloTheme {
                val copy = memoryDetailCopy(item, now, zone)
                Column {
                    Text(copy.scope, Modifier.testTag("named-scope"))
                    Text(copy.usage, Modifier.testTag("used-summary"))
                    Text(requireNotNull(copy.lastConfirmed), Modifier.testTag("yesterday"))
                    Text(localizedMemoryDate(old, now, zone), Modifier.testTag("calendar"))
                }
            } } }
            val context = ApplicationProvider.getApplicationContext<Context>()
            val resources = context.createConfigurationContext(Configuration(context.resources.configuration).apply {
                setLocale(Locale.forLanguageTag("ru"))
            }).resources
            compose.onNodeWithTag("named-scope").assertTextEquals("Project · Everywhere")
            compose.onNodeWithTag("used-summary").assertTextEquals(item.usageSummary)
            compose.onNodeWithTag("yesterday").assertTextEquals(resolveUiText(resources, "Last confirmed %1\$s", resolveUiText(resources, "Yesterday")))
            compose.onNodeWithTag("calendar").assertTextEquals(MemoryPresentation.date(old, now, zone, Locale.forLanguageTag("ru")))
            assertEquals(2L, original.usageCount)
            assertEquals("scope-id", original.scopeId)
            assertEquals("Fact", original.content)
        } finally { Locale.setDefault(previousLocale) }
    }

    private class MemoryStorage : Storage {
        private val values = mutableMapOf<String, Any>()
        override fun getString(key: String, defaultValue: String?) = values[key] as? String ?: defaultValue
        override fun putString(key: String, value: String?) { if (value == null) values.remove(key) else values[key] = value }
        override fun getInt(key: String, defaultValue: Int) = values[key] as? Int ?: defaultValue
        override fun putInt(key: String, value: Int) { values[key] = value }
        override fun getBoolean(key: String, defaultValue: Boolean) = values[key] as? Boolean ?: defaultValue
        override fun putBoolean(key: String, value: Boolean) { values[key] = value }
        override fun remove(key: String) { values.remove(key) }
        override fun clear() { values.clear() }
    }
}
