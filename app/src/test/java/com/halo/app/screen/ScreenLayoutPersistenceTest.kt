package com.halo.app.screen

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Instant

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [33])
class ScreenLayoutPersistenceTest {
    private val context get() = ApplicationProvider.getApplicationContext<Application>()
    @Before fun clear() { context.getSharedPreferences("halo_screen", 0).edit().clear().commit() }
    private fun styled() = ScreenLayout("desk", "My desk", listOf(ScreenCard("notebook", "note:1", CardSize.EXPANDED,
        true, mapOf("legacy" to "keep", "calendar" to "chosen"), archived = true,
        placement = ScreenPlacement(6, 4, 6, 3), typography = ScreenCardTypography(125, 150, 175))), 7,
        ScreenAppearance(ScreenFont.ATKINSON, ScreenBackground.MINT, true))

    private fun item() = ScreenItem("automation:one", ScreenSourceType.AUTOMATION, "one", "Routine fixture", "Saved result",
        ScreenContentType.SUMMARY, Instant.parse("2026-10-23T00:00:00Z"), null, null, ScreenItemState.READY,
        ScreenSensitivity.PRIVATE, null, listOf(ScreenAction.OpenSource))

    @Test fun completePresentationAndSourceReferencesSurviveRestart() {
        val store = LocalScreenStore(context)
        store.saveLayout(styled())
        val restored = LocalScreenStore(context).layout.value!!
        assertEquals(styled().cards, restored.cards)
        assertEquals(styled().appearance, restored.appearance)
        assertEquals("desk", restored.id); assertEquals("My desk", restored.name)
    }

    @Test fun migrationKeepsEveryLegacySizeConfigPinArchiveAndSourceReference() {
        context.getSharedPreferences("halo_screen", 0).edit().putString("layout", """{
          "id":"old","name":"Old layout","version":7,"cards":[
          {"type":"clock","itemId":null,"size":"COMPACT","pinned":true,"config":{"custom":"legacy"}},
          {"type":"research","itemId":"research:one","size":"EXPANDED","pinned":false,"archived":true,"config":{"source":"saved"}}]}
        """).commit()
        val before = LocalScreenStore(context).layout.value!!
        assertEquals(listOf(CardSize.COMPACT, CardSize.EXPANDED), before.cards.map { it.size })
        assertTrue(before.cards[0].pinned); assertTrue(before.cards[1].archived)
        assertEquals("research:one", before.cards[1].itemId)
        assertEquals("legacy", before.cards[0].config["custom"])
        assertEquals(ScreenAppearance(), before.appearance)
        assertTrue(before.cards.all { it.placement == null && it.typography == ScreenCardTypography() })
        LocalScreenStore(context).saveLayout(before)
        assertEquals(before.cards, LocalScreenStore(context).layout.value!!.cards)
    }

    @Test fun malformedPresentationBoundsRecoverConsistentlyBeforeAndAfterRestart() {
        val store = LocalScreenStore(context)
        store.saveLayout(styled().copy(cards = listOf(styled().cards.single().copy(
            placement = ScreenPlacement(-50, 999, 99, -2), typography = ScreenCardTypography(20, 999, 999)))))
        val card = store.layout.value!!.cards.single()
        assertEquals(ScreenPlacement(0, 143, 12, 1), card.placement)
        assertEquals(ScreenCardTypography(100, 150, 200), card.typography)
        assertEquals(card, LocalScreenStore(context).layout.value!!.cards.single())
    }

    @Test fun duplicateLegacySourcesReceiveStableIndependentPresentationIdentities() {
        val duplicate = ScreenCard("clock", null, CardSize.COMPACT, true)
        val old = ScreenLayout("legacy", "Duplicates", listOf(duplicate, duplicate), 1)
        val raw = ScreenLayoutCodec.encode(old).replace(Regex(",?\"cardId\":\"[^\"]*\""), "")
        val migrated = ScreenLayoutCodec.decode(raw)!!
        assertEquals(2, migrated.cards.map { it.presentationId }.toSet().size)
        assertEquals(migrated, ScreenLayoutCodec.decode(raw))
        val moved = migrated.copy(cards = ScreenGrid.move(migrated.cards, 0, 1, 0, 1))
        val store = LocalScreenStore(context)
        store.saveLayout(moved)
        assertEquals(migrated.cards.map { it.presentationId }.reversed(), LocalScreenStore(context).layout.value!!.cards.map { it.presentationId })
    }

    @Test fun unknownFontOrBackgroundFallsBackWithoutDroppingTheCard() {
        val raw = ScreenLayoutCodec.encode(styled()).replace("ATKINSON", "remote-font").replace("MINT", "remote-image")
        val restored = ScreenLayoutCodec.decode(raw)!!
        assertEquals(ScreenFont.APP, restored.appearance.font)
        assertEquals(ScreenBackground.PLAIN, restored.appearance.background)
        assertEquals(styled().cards, restored.cards)
    }

    @Test fun namedPresetsReplaceByNameAndDoNotChangeAppliedLayoutOrSourceItems() = runBlocking {
        val store = LocalScreenStore(context)
        store.publishAndSaveLayout(item(), styled())
        val original = store.layout.value
        assertTrue(store.savePreset("Evening", original!!))
        val id = store.presets.value.single().id
        assertTrue(store.savePreset("evening", original.copy(appearance = ScreenAppearance(ScreenFont.SORA))))
        val restored = LocalScreenStore(context)
        assertEquals(1, restored.presets.value.size); assertEquals(id, restored.presets.value.single().id)
        assertEquals(ScreenFont.SORA, restored.presets.value.single().appearance.font)
        assertEquals(original, restored.layout.value); assertEquals(listOf(item()), restored.items.value)
        restored.removePreset(id)
        assertTrue(LocalScreenStore(context).presets.value.isEmpty())
        assertEquals(original, LocalScreenStore(context).layout.value)
    }

    @Test fun presetNamesAndCountAreBoundedWithExplicitReplacementAtCapacity() {
        val store = LocalScreenStore(context)
        assertFalse(store.savePreset(" \n\t ", styled()))
        repeat(12) { assertTrue(store.savePreset("Layout $it", styled())) }
        assertFalse(store.savePreset("Thirteenth", styled()))
        assertTrue(store.savePreset("LAYOUT 0", styled()))
        assertEquals(12, LocalScreenStore(context).presets.value.size)
    }

    @Test fun staleDraftCannotOverwriteAnIncomingRoutineBinding() = runBlocking {
        val store = LocalScreenStore(context)
        store.saveLayout(ScreenWidgets.starter("Focus"))
        val base = store.layout.value!!
        val draft = base.copy(appearance = ScreenAppearance(background = ScreenBackground.WARM))
        store.publishAndSaveLayout(item(), base.copy(cards = base.cards + ScreenCard("automation_result", item().id, CardSize.STANDARD, false)))
        assertFalse(store.applyDraft(base, draft))
        assertTrue(store.layout.value!!.cards.any { it.itemId == item().id })
        assertEquals(listOf(item()), store.items.value)
        assertEquals(base.appearance, store.layout.value!!.appearance)
    }

    @Test fun undoRestoresTheFullPresentationAcrossRestartWithoutChangingSources() = runBlocking {
        val store = LocalScreenStore(context)
        store.publishAndSaveLayout(item(), styled())
        val base = store.layout.value!!
        val draft = base.copy(cards = ScreenGrid.move(base.cards, 0, 2, -1, 1), appearance = ScreenAppearance(ScreenFont.SYSTEM))
        assertTrue(store.applyDraft(base, draft))
        val restored = LocalScreenStore(context)
        assertTrue(restored.hasUndo())
        restored.undo()
        assertEquals(base, restored.layout.value)
        assertEquals(listOf(item()), restored.items.value)
        assertEquals(base, LocalScreenStore(context).layout.value)
    }

    @Test fun applyingAnUnchangedPreviewDoesNotCreateAnUndoOrVersionChange() {
        val store = LocalScreenStore(context)
        store.saveLayout(ScreenWidgets.starter("Quiet"))
        val base = store.layout.value!!
        assertTrue(store.applyDraft(base, base))
        assertFalse(store.hasUndo()); assertEquals(base, store.layout.value)
    }

    @Test fun invalidLayoutCannotPartiallyPublishAnItem() = runBlocking {
        val store = LocalScreenStore(context)
        store.saveLayout(ScreenWidgets.starter("Quiet"))
        val base = store.layout.value!!
        try {
            store.publishAndSaveLayout(item(), base.copy(cards = listOf(ScreenCard("executable-html", null, CardSize.STANDARD, false))))
            fail("Unregistered widget accepted")
        } catch (_: IllegalArgumentException) { }
        assertEquals(base, store.layout.value); assertTrue(store.items.value.isEmpty())
        assertTrue(LocalScreenStore(context).items.value.isEmpty())
    }
}
