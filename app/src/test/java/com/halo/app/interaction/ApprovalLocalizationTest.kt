package com.halo.app.interaction

import android.app.Application
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.halo.app.language.AppLanguagePreferences
import com.halo.app.language.AppLanguageProvider
import com.halo.app.ui.theme.HostlessHaloTheme
import com.halo.runtime.cognition.interaction.*
import com.halo.sdk.runtime.Storage
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Instant

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
class ApprovalLocalizationTest {
    @get:Rule val compose = createComposeRule()
    private val action = "Show private details in full screen"
    private val warning = "People nearby may see personal and private cards"
    private fun surface(subject: String, detail: String) = InteractionSurface(
        id = InteractionSurfaceId("configuration-approval"), runId = null, interactionId = null, inputRequestId = null,
        kind = InteractionSurfaceKind.CONFIRMATION, title = null, message = subject,
        content = InteractionContent.Confirmation(subject = subject, detail = detail,
            risk = InteractionRiskPresentation(InteractionRiskLevel.SENSITIVE, detail)),
        actions = listOf(InteractionAction(InteractionActionId("approve"), "Approve", InteractionActionSemantic.CONFIRM),
            InteractionAction(InteractionActionId("deny"), "Decline", InteractionActionSemantic.DENY)),
        presentation = InteractionPresentationPolicy(), createdAt = Instant.EPOCH, expiresAt = null,
        provenance = InteractionSurfaceProvenance(InteractionSurfaceSource.AGENT_INPUT, "fixture-input"))

    private fun render(language: String, value: InteractionSurface, mode: SurfaceRenderMode,
        responses: MutableList<InteractionResponse> = mutableListOf()) {
        val preferences = AppLanguagePreferences(MemoryStorage(), language)
        compose.setContent { AppLanguageProvider(preferences) { HostlessHaloTheme {
            InteractionSurfaceRenderer(value, responses::add, mode = mode)
        } } }
    }

    @Test fun russianCompactPrivacyApprovalTranslatesFixedSegmentsAndOnlyExplicitClickResponds() {
        val value = surface("$action $warning", "halo Screen · $action · $warning")
        val original = value.content
        val responses = mutableListOf<InteractionResponse>()
        render("ru", value, SurfaceRenderMode.COMPACT, responses)
        val localizedAction = "Показать личные данные в полноэкранном режиме"
        val localizedWarning = "Окружающие могут видеть личные и приватные карточки"
        compose.onNodeWithText("$localizedAction $localizedWarning").assertExists()
        compose.onAllNodesWithText("halo Screen · $localizedAction · $localizedWarning").assertCountEquals(2)
        compose.onNodeWithText("$action $warning").assertDoesNotExist()
        assertTrue(responses.isEmpty())
        compose.onNodeWithText("Одобрить").performClick()
        assertEquals(listOf(InteractionResponse.Confirmed(value.id)), responses)
        assertEquals(original, value.content)
    }

    @Test fun polishFullPrivacyApprovalTranslatesTheSameConsequenceWithoutChangingRuntimeCopy() {
        val value = surface("$action $warning", "halo Screen · $action · $warning")
        render("pl", value, SurfaceRenderMode.FULL)
        val localizedAction = "Pokaż prywatne szczegóły na pełnym ekranie"
        val localizedWarning = "Osoby w pobliżu mogą zobaczyć osobiste i prywatne karty"
        compose.onNodeWithText("$localizedAction $localizedWarning").assertExists()
        compose.onAllNodesWithText("halo Screen · $localizedAction · $localizedWarning").assertCountEquals(2)
        assertEquals("$action $warning", (value.content as InteractionContent.Confirmation).subject)
    }

    @Test fun canonicalTitleLocalizesButAnUntrustedTargetMatchingKnownCopyStaysLiteral() {
        val value = surface("Change Screen settings", warning)
        render("pl", value, SurfaceRenderMode.COMPACT)
        compose.onNodeWithText("Zmień ustawienia ekranu").assertExists()
        compose.onAllNodesWithText(warning).assertCountEquals(2)
        compose.onNodeWithText("Osoby w pobliżu mogą zobaczyć osobiste i prywatne karty").assertDoesNotExist()
    }

    @Test fun unknownTargetSuffixIsNeverParsedOrTranslatedAsAnApprovalTemplate() {
        val target = "$action — Alice's device / Read Screen layout"
        render("ru", surface(target, "Read Screen layout"), SurfaceRenderMode.COMPACT)
        compose.onNodeWithText(target).assertExists()
        compose.onAllNodesWithText("Read Screen layout").assertCountEquals(2)
        compose.onNodeWithText("Просмотреть расположение карточек на экране").assertDoesNotExist()
    }

    @Test fun newRoutineApprovalLocalizesWhileTargetMatchingAnotherNewTitleRemainsLiteral() {
        val value = surface("Delete saved routine", "Read voice settings")
        val responses = mutableListOf<InteractionResponse>()
        render("pl", value, SurfaceRenderMode.COMPACT, responses)
        compose.onNodeWithText("Usuń zapisaną procedurę").assertExists()
        compose.onAllNodesWithText("Read voice settings").assertCountEquals(2)
        assertTrue(responses.isEmpty())
        compose.onNodeWithText("Odrzuć").performClick()
        assertEquals(listOf(InteractionResponse.Denied(value.id)), responses)
    }

    @Test fun russianVoiceSettingsApprovalTranslatesOnlyItsCanonicalSubject() {
        render("ru", surface("Change voice settings", "Local user choice"), SurfaceRenderMode.COMPACT)
        compose.onNodeWithText("Изменить настройки голоса").assertExists()
        compose.onAllNodesWithText("Local user choice").assertCountEquals(2)
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
