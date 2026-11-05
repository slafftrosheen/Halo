package com.halo.app.nativeactions

import android.app.Application
import android.app.PendingIntent
import androidx.test.core.app.ApplicationProvider
import com.halo.app.everyday.presentation.*
import org.junit.Assert.*
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28],application=Application::class)
class EverydayWidgetStateTest {
    private val context=ApplicationProvider.getApplicationContext<Application>()
    @After fun clean() { context.getSharedPreferences("halo_everyday_widgets_v1",0).edit().clear().commit(); java.io.File(context.filesDir,"everyday-widgets").deleteRecursively() }
    @Test fun independentInstancesSurviveOwnerRecreationAndStaleEditorsCannotOverwriteEither() {
        val store=EverydayWidgetStore(context)
        val first=store.save(11,null,EverydayCardConfig(EverydayCardKind.CLOCK,"Asia/Tokyo","Tokyo"))
        val second=store.save(12,null,EverydayCardConfig(EverydayCardKind.CLOCK,"Europe/Riga","Riga"))
        val restored=EverydayWidgetStore(context)
        assertEquals(first,restored.get(11));assertEquals(second,restored.get(12))
        val review=EverydayWidgetReview.restore(EverydayWidgetReview.encode(first))
        val changed=store.save(first.id,first.version,first.config.copy(cityLabel="Changed elsewhere"))
        assertTrue(runCatching { restored.save(review.id,review.version,review.config.copy(cityLabel="Old restored draft")) }.isFailure)
        assertEquals(changed,store.get(first.id));assertEquals(second,store.get(second.id))
    }
    @Test fun failedAtomicSaveCannotPublishAnInMemoryCompletionOrLoseThePriorSettings() {
        val store=EverydayWidgetStore(context)
        val first=store.save(71,null,EverydayCardConfig(EverydayCardKind.CLOCK,cityLabel="Saved"))
        val operation=java.util.UUID.randomUUID().toString()
        val failing=EverydayWidgetStore(context) {throw java.io.IOException("Synthetic disk failure")}
        assertTrue(runCatching {failing.save(71,first.version,first.config.copy(cityLabel="Unsaved"),operation)}.isFailure)
        assertEquals(first,store.get(71));assertFalse(store.completed(71,operation))
    }
    @Test fun committedOperationSurvivesRecreationBeforePaintAndDoesNotRebaseAnUnrelatedDraft() {
        val operation=java.util.UUID.randomUUID().toString()
        val store=EverydayWidgetStore(context)
        val draft=EverydayWidgetInstance(61,0,EverydayCardConfig(EverydayCardKind.CLOCK))
        val savedState=EverydayWidgetReview.encode(draft)
        val saved=store.save(61,null,draft.config,operation)
        assertTrue(EverydayWidgetStore(context).completed(61,operation))
        assertFalse(store.completed(61,java.util.UUID.randomUUID().toString()))
        store.save(61,saved.version,saved.config.copy(cityLabel="Newer editor"))
        assertTrue(store.completed(61,operation))
        val oldReview=EverydayWidgetReview.restore(savedState)
        assertTrue(runCatching { store.save(61,oldReview.version.takeIf { it>0 },oldReview.config) }.isFailure)
        store.remove(61);assertFalse(store.completed(61,operation))
    }
    @Test fun revokedAuthorityAndAnOlderGenerationCannotPublishOrReplaceANewerConfiguration() {
        val store=EverydayWidgetStore(context)
        val first=store.save(21,null,EverydayCardConfig(EverydayCardKind.TASKS))
        var checks=0
        assertTrue(runCatching { store.save(21,first.version,first.config.copy(limit=1)) { check(++checks<2) } }.isFailure)
        assertEquals(first,store.get(21))
        val oldGeneration=EverydayWidgetGeneration.invalidate()
        val next=store.save(21,first.version,first.config.copy(limit=2))
        var publications=0
        assertTrue(runCatching { store.publishIfCurrent(first,oldGeneration) { publications++ } }.exceptionOrNull() is WidgetPublicationChanged)
        store.publishIfCurrent(next,EverydayWidgetGeneration.current()) { publications++ }
        assertEquals(1,publications)
    }
    @Test fun restoreAndDeletionKeepSiblingInstancesAndDiscardOldClickAuthority() {
        val store=EverydayWidgetStore(context)
        val config=EverydayCardConfig(EverydayCardKind.HEADLINES,sourceId="source:one")
        store.save(31,null,config);store.save(32,null,config.copy(sourceId="source:two"))
        store.restored(31,41)
        assertNull(store.get(31));assertEquals(config,store.get(41)!!.config);assertEquals("source:two",store.get(32)!!.config.sourceId)
        store.remove(41);assertNull(store.get(41));assertNotNull(store.get(32))
    }
    @Test fun pendingIntentIdentityIncludesInstanceVersionAndExactTargetRatherThanOnlyTheRowIndex() {
        val a=EverydayWidgetInstance(51,1,EverydayCardConfig(EverydayCardKind.TASKS))
        val b=a.copy(id=52)
        val first=EverydayTarget.Record("first",1)
        val second=EverydayTarget.Record("second",2)
        fun pending(instance: EverydayWidgetInstance,target: EverydayTarget)=PendingIntent.getActivity(context,0,
            EverydayWidgetViews.intent(context,instance,"row:0",target),PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val p1=pending(a,first);val p2=pending(b,first);val p3=pending(a,second)
        assertNotEquals(p1,p2);assertNotEquals(p1,p3)
        assertEquals(first,EverydayWidgetTargets.decode(shadowOf(p1).savedIntent.getStringExtra(EverydayWidgetViews.EXTRA_TARGET)!!))
        assertEquals(second,EverydayWidgetTargets.decode(shadowOf(p3).savedIntent.getStringExtra(EverydayWidgetViews.EXTRA_TARGET)!!))
    }
    @Test fun referenceTargetsRoundTripExactSourceVersionGenerationAndEntryIdentity() {
        val entry = EverydayTarget.LibraryEntry("library:one", 7, "generation-two", "water/first-aid")
        assertEquals(EverydayTarget.Libraries, EverydayWidgetTargets.decode(EverydayWidgetTargets.encode(EverydayTarget.Libraries)))
        assertEquals(entry, EverydayWidgetTargets.decode(EverydayWidgetTargets.encode(entry)))
        assertTrue(runCatching { EverydayWidgetTargets.decode(EverydayWidgetTargets.encode(entry.copy(version = 0))) }.isFailure)
    }

}
