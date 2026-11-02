package com.halo.app.screen

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.halo.app.everyday.presentation.*
import org.junit.Assert.*
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28],application=Application::class)
class EverydayScreenInstancesTest {
    private val context=ApplicationProvider.getApplicationContext<Application>()
    @After fun clean() {context.getSharedPreferences("halo_screen",0).edit().clear().commit()}
    @Test fun duplicateClocksHaveStableIndependentConfigAndExactInstanceChanges() {
        val base=ScreenLayout("fixture","Fixture",emptyList(),1)
        val addRiga=LayoutChange.Add("clock",EverydayCardConfig(EverydayCardKind.CLOCK,"Europe/Riga","Riga").fields())
        val first=ScreenLayoutRequests.apply(base,addRiga)!!
        // Runtime preview/apply rebuild the same proposal independently; identities must agree.
        assertEquals(first,ScreenLayoutRequests.apply(base,addRiga))
        val second=ScreenLayoutRequests.apply(first,LayoutChange.Add("clock",EverydayCardConfig(EverydayCardKind.CLOCK,"Asia/Tokyo","Tokyo").fields()))!!
        val a=second.cards.first();val b=second.cards.last()
        assertNotEquals(a.presentationId,b.presentationId)
        assertNull(ScreenLayoutRequests.apply(second,LayoutChange.Remove("clock")))
        val resized=ScreenLayoutRequests.apply(second,LayoutChange.Resize("clock",CardSize.EXPANDED,b.presentationId))!!
        assertEquals(CardSize.STANDARD,resized.cards.first().size);assertEquals(CardSize.EXPANDED,resized.cards.last().size)
        val removed=ScreenLayoutRequests.apply(second,LayoutChange.Remove("clock",a.presentationId))!!
        assertEquals(listOf(b),removed.cards)
        assertEquals(second,ScreenLayoutCodec.decode(ScreenLayoutCodec.encode(second)))
    }
    @Test fun restoredLayoutAndAConflictingDraftKeepBothSourceConfigurations() {
        val store=LocalScreenStore(context)
        val cards=listOf(ScreenCard("weather",null,CardSize.STANDARD,false,config=EverydayCardConfig(EverydayCardKind.WEATHER,sourceId="city:riga").fields(),presentationId="riga"),
            ScreenCard("weather",null,CardSize.COMPACT,false,config=EverydayCardConfig(EverydayCardKind.WEATHER,sourceId="city:tokyo").fields(),presentationId="tokyo"))
        store.saveLayout(ScreenLayout("fixture","Fixture",cards,1))
        val base=store.layout.value!!
        val draft=base.copy(cards=base.cards.map {if(it.presentationId=="riga") it.copy(config=it.config+("sourceId" to "city:other")) else it})
        store.saveLayout(base.copy(name="Changed elsewhere"))
        assertFalse(store.applyDraft(base,draft))
        val restored=LocalScreenStore(context).layout.value!!
        assertEquals(listOf("riga","tokyo"),restored.cards.map {it.presentationId})
        assertEquals(listOf("city:riga","city:tokyo"),restored.cards.map {it.config["sourceId"]})
    }
}
