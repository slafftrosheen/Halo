package com.halo.app.nativeactions

import android.app.Activity
import android.app.Application
import android.content.Intent
import android.content.res.Configuration
import android.os.Looper
import android.provider.Settings
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextClock
import android.widget.TextView
import androidx.test.core.app.ApplicationProvider
import com.github.takahirom.roborazzi.captureRoboImage
import com.halo.app.R
import com.halo.app.everyday.briefing.*
import com.halo.app.everyday.collectors.*
import com.halo.app.everyday.organizer.*
import com.halo.app.everyday.presentation.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowSystemClock
import java.time.*
import java.util.Locale

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk=[34],application=Application::class,qualifiers="en-rUS-w400dp-h900dp")
class EverydayWidgetRenderingTest {
    private val now=Instant.parse("2026-11-03T12:00:00Z")
    private fun snapshot(): EverydaySnapshot {
        val task=OrganizerRecord("synthetic-task",OrganizerKind.TASK,"PRIVATE-TITLE-DO-NOT-EXPOSE",createdAt=now)
        val event=OrganizerRecord("synthetic-event",OrganizerKind.EVENT,task.title,time=OrganizerTime(LocalDateTime.parse("2026-11-04T09:00"),LocalDateTime.parse("2026-11-04T10:00"),"UTC"),createdAt=now)
        val alarm=OrganizerRecord("synthetic-alarm",OrganizerKind.ALARM,task.title,time=OrganizerTime(LocalDateTime.parse("2026-11-04T07:00"),zoneId="UTC"),createdAt=now)
        val place=WeatherPlace("Riga (synthetic)",56.9,24.1,"Europe/Riga")
        val weather=CollectorSource("synthetic-weather","Synthetic weather","Fixture publisher",CollectorKind.WEATHER,SourceCategory.WEATHER,"en","https://example.org/weather",selected=true,place=place)
        val feed=CollectorSource("synthetic-feed","Synthetic feed","Fixture publisher",CollectorKind.FEED,SourceCategory.WORLD,"en","https://example.org/feed",selected=true)
        fun state(source: CollectorSource,payload: CollectorPayload)=CollectorState(source,CollectorSnapshot(source.id,source.version,payload,"synthetic-revision",now.minusSeconds(600),now.minusSeconds(600),now.plusSeconds(3600)),CollectorAttempt(source.id,now.minusSeconds(600),CollectorAttemptState.CURRENT))
        val sources=listOf(state(weather,CollectorPayload.Weather(WeatherFact(place,now,8.0,3,emptyList(),"Fixture publisher","https://example.org/"))),
            state(feed,CollectorPayload.Articles(listOf(ArticleFact("synthetic-article","Synthetic headline: a saved astronomy update with a longer title for native wrapping","Synthetic text","https://example.org/story","Fixture publisher",now,"en")))))
        val brief=EverydayBrief("c".repeat(64),BriefPeriod.MORNING,BriefAudience.EVERYDAY,now.atZone(ZoneOffset.UTC).toLocalDate(),"UTC",now,
            listOf(BriefFact("private",BriefFactKind.NOTE,task.title,"PRIVATE-BODY", "organizer","1","",values=emptyMap())),emptyList(),emptyList(),null,true,emptySet(),0,0,"synthetic")
        return EverydaySnapshot(now,ZoneOffset.UTC,listOf(task,event,alarm),OrganizerRecurrence.between(event,emptyList(),now,now.plusSeconds(7*86400),ZoneOffset.UTC),sources,listOf(brief),power=true)
    }
    @Test fun allEightVariantsBindAndFitInLightTheme() = renderVariants(Locale.US,1f,false,false)
    @Test @Config(qualifiers="ru-rRU-w400dp-h900dp") fun allEightVariantsFitAt200PercentInDarkHighContrast() = renderVariants(Locale("ru"),2f,true,true)
    @Test @Config(qualifiers="lv-rLV-w400dp-h900dp-xxhdpi") fun allEightVariantsPreserveGeometryAtHighDensity() = renderVariants(Locale("lv"),1f,false,false)
    private fun renderVariants(locale: Locale,scale: Float,dark: Boolean,contrast: Boolean) {
        val app=ApplicationProvider.getApplicationContext<Application>()
        app.getSharedPreferences("halo_visual_options",0).edit().putBoolean("contrast",contrast).commit()
        val context=app.createConfigurationContext(Configuration(app.resources.configuration).apply {setLocale(locale);fontScale=scale})
        val data=snapshot()
        android.os.SystemClock.setCurrentTimeMillis(now.toEpochMilli())
        EverydayCards.launcherKinds.forEachIndexed { index,kind ->
            val instance=EverydayWidgetInstance(101+index,1,EverydayCardConfig(kind,zoneId="UTC",cityLabel="UTC",theme=if(dark) "DARK" else "LIGHT"))
            val width=320;val height=if(scale>=1.5f) 640 else when(kind) {EverydayCardKind.DASHBOARD -> 420;EverydayCardKind.CLOCK_WEATHER -> 300;EverydayCardKind.HEADLINES -> 280;else -> 220}
            val remote=EverydayWidgetViews.render(context,instance,data,width,height,locale,scale,{com.halo.app.language.resolveUiText(context.resources,it)})
            val view=remote.apply(context,FrameLayout(context))
            layout(view,width,height,context.resources.displayMetrics.density)
            visibleText(view).forEach { text ->
                assertFalse(text.text.toString().contains("PRIVATE-"))
                assertFits(view,text,kind.name)
            }
            assertNull(view.contentDescription)
            capture(view,"build/outputs/roborazzi/everyday-widget-${kind.name.lowercase()}-${locale.language}-${scale}-${context.resources.displayMetrics.density}-synthetic.png")
        }
    }
    @Test @Config(qualifiers="ru-rRU-w400dp-h900dp") fun allAdvertisedMinimumSizesFitAt200Percent() {
        val app=ApplicationProvider.getApplicationContext<Application>()
        val locale=Locale("ru")
        val context=app.createConfigurationContext(Configuration(app.resources.configuration).apply {setLocale(locale);fontScale=2f})
        android.os.SystemClock.setCurrentTimeMillis(now.toEpochMilli())
        val identities=mutableSetOf<String>()
        EverydayCards.launcherKinds.forEachIndexed { index,kind ->
            val height=when(kind) { EverydayCardKind.CLOCK_WEATHER -> 220;EverydayCardKind.HEADLINES -> 200;EverydayCardKind.DASHBOARD -> 260;else -> 160 }
            val view=EverydayWidgetViews.render(context,EverydayWidgetInstance(401+index,1,EverydayCardConfig(kind,theme="DARK")),snapshot(),160,height,locale,2f,{com.halo.app.language.resolveUiText(context.resources,it)}).apply(context,FrameLayout(context))
            layout(view,160,height,context.resources.displayMetrics.density)
            visibleText(view).forEach { assertFits(view,it,kind.name) }
            if(kind!=EverydayCardKind.CLOCK) {
                assertNotNull(view.contentDescription)
                assertTrue("Compact widgets must remain distinguishable",identities.add(visibleText(view).joinToString {it.text.toString()}))
            }
            capture(view,"build/outputs/roborazzi/everyday-widget-${kind.name.lowercase()}-ru-minimum-200-synthetic.png")
        }
    }
    private fun assertFits(root: View,text: TextView,kind: String) {
        val bounds=android.graphics.Rect(0,0,text.width,text.height)
        if(root is ViewGroup) root.offsetDescendantRectToMyCoords(text,bounds)
        assertTrue("$kind vertical: ${text.text} $bounds / ${root.height}",bounds.top>=0 && bounds.bottom<=root.height+1)
        assertTrue("$kind horizontal: ${text.text} $bounds / ${root.width}",bounds.left>=0 && bounds.right<=root.width+1)
        text.layout?.let { lines ->
            assertTrue("$kind text height: ${text.text}",lines.height<=text.height-text.compoundPaddingTop-text.compoundPaddingBottom+1)
            (0 until lines.lineCount).forEach { line ->
                assertEquals(0,lines.getEllipsisCount(line))
                assertTrue("$kind glyph width: ${text.text}",lines.getLineLeft(line)>=-1f && lines.getLineRight(line)<=lines.width+1f)
            }
        }
    }
    @Test fun compactRecoveryAndReapplyRestoreVisibleThemedActions() {
        val context=ApplicationProvider.getApplicationContext<Application>()
        val instance=EverydayWidgetInstance(201,1,EverydayCardConfig(EverydayCardKind.CLOCK,theme="LIGHT"))
        val data=snapshot();val label:(String)->String={it}
        val full=EverydayWidgetViews.render(context,instance,data,320,300,Locale.US,1f,label)
        val view=full.apply(context,FrameLayout(context));layout(view,320,300,context.resources.displayMetrics.density)
        val small=EverydayWidgetViews.render(context,instance,data,160,96,Locale.US,2f,label,unavailable=true)
        small.reapply(context,view);layout(view,160,96,context.resources.displayMetrics.density)
        assertEquals(View.VISIBLE,view.findViewById<TextView>(R.id.everyday_widget_value).visibility)
        assertTrue(view.findViewById<TextView>(R.id.everyday_widget_value).text.toString() in setOf("Configure widget","Halo","Clock","Clock\nConfigure widget"))
        visibleText(view).forEach { assertFits(view,it,"compact recovery") }
        full.reapply(context,view);layout(view,320,300,context.resources.displayMetrics.density)
        assertEquals(View.VISIBLE,view.findViewById<TextView>(R.id.everyday_widget_status).visibility)
        assertEquals(View.VISIBLE,view.findViewById<TextClock>(R.id.everyday_widget_clock).visibility)
    }
    @Test fun liveClockAndDateOwnTheirAccessibilityTextAfterTimeChangesWithoutRepaint() {
        val context=ApplicationProvider.getApplicationContext<Application>()
        Settings.System.putString(context.contentResolver,Settings.System.TIME_12_24,"24")
        android.os.SystemClock.setCurrentTimeMillis(Instant.parse("2026-11-03T23:59:00Z").toEpochMilli())
        val activity=Robolectric.buildActivity(Activity::class.java).setup()
        try {
            val instance=EverydayWidgetInstance(301,1,EverydayCardConfig(EverydayCardKind.CLOCK,"UTC","UTC"))
            val view=EverydayWidgetViews.render(context,instance,snapshot(),320,300,Locale.US,1f,{it}).apply(context,FrameLayout(context))
            activity.get().setContentView(view);layout(view,320,300,context.resources.displayMetrics.density)
            val clock=view.findViewById<TextClock>(R.id.everyday_widget_clock)
            val date=view.findViewById<TextClock>(R.id.everyday_widget_clock_date)
            assertNull(view.contentDescription);assertEquals(clock.text.toString(),clock.contentDescription.toString());assertEquals(date.text.toString(),date.contentDescription.toString())
            val old=clock.text.toString();val oldDate=date.text.toString()
            android.os.SystemClock.setCurrentTimeMillis(Instant.parse("2026-11-04T00:01:00Z").toEpochMilli())
            clockBroadcast(context,Intent(Intent.ACTION_TIME_CHANGED))
            assertNotEquals(old,clock.text.toString());assertNotEquals(oldDate,date.text.toString())
            assertEquals(clock.text.toString(),clock.contentDescription.toString());assertEquals(date.text.toString(),date.contentDescription.toString())
        } finally {activity.pause().stop().destroy()}
    }
    @Test fun minimumClockRetainsLiveTimeAndIndependentFixedCityWhenPhoneZoneChanges() {
        val app=ApplicationProvider.getApplicationContext<Application>()
        val oldZone=java.util.TimeZone.getDefault()
        val activity=Robolectric.buildActivity(Activity::class.java).setup()
        try {
            java.util.TimeZone.setDefault(java.util.TimeZone.getTimeZone("UTC"))
            Settings.System.putString(app.contentResolver,Settings.System.TIME_12_24,"24")
            android.os.SystemClock.setCurrentTimeMillis(Instant.parse("2026-11-03T00:00:00Z").toEpochMilli())
            val host=android.widget.LinearLayout(activity.get()).apply {orientation=android.widget.LinearLayout.VERTICAL}
            fun clock(config: EverydayCardConfig): TextClock {
                val view=EverydayWidgetViews.render(app,EverydayWidgetInstance(501+host.childCount,1,config),snapshot(),160,160,Locale.US,2f,{it}).apply(app,host)
                host.addView(view);layout(view,160,160,app.resources.displayMetrics.density)
                return view.findViewById(R.id.everyday_widget_clock)
            }
            val follow=clock(EverydayCardConfig(EverydayCardKind.CLOCK))
            val fixed=clock(EverydayCardConfig(EverydayCardKind.CLOCK,"Asia/Tokyo","Tokyo"))
            activity.get().setContentView(host)
            assertEquals(View.VISIBLE,follow.visibility);assertEquals(View.VISIBLE,fixed.visibility)
            assertEquals("00:00",follow.text.toString());assertEquals("09:00",fixed.text.toString())
            java.util.TimeZone.setDefault(java.util.TimeZone.getTimeZone("America/New_York"))
            clockBroadcast(app,Intent(Intent.ACTION_TIMEZONE_CHANGED).putExtra("time-zone","America/New_York"))
            assertEquals("19:00",follow.text.toString());assertEquals("09:00",fixed.text.toString())
        } finally {activity.pause().stop().destroy();java.util.TimeZone.setDefault(oldZone)}
    }
    private fun clockBroadcast(context: Application,intent: Intent) {
        // Exercise the real host clock receivers without starting unrelated manifest workers in this host fixture.
        val receivers=shadowOf(context).getReceiversForIntent(intent).filter {it.javaClass.name.startsWith("android.widget.TextClock")}
        assertTrue(receivers.isNotEmpty())
        receivers.forEach {it.onReceive(context,intent)}
        shadowOf(Looper.getMainLooper()).idle()
    }
    private fun capture(view: View,path: String) {
        // Native RemoteViews are a launcher host tree, not a Compose Activity. Rasterize the measured host directly.
        val bitmap=android.graphics.Bitmap.createBitmap(view.width,view.height,android.graphics.Bitmap.Config.ARGB_8888)
        view.draw(android.graphics.Canvas(bitmap))
        val file=java.io.File(path);file.parentFile?.mkdirs()
        file.outputStream().use {assertTrue(bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it))}
        bitmap.recycle()
    }
    private fun layout(view: View,width: Int,height: Int,density: Float) {
        val w=(width*density).toInt();val h=(height*density).toInt()
        view.measure(View.MeasureSpec.makeMeasureSpec(w,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(h,View.MeasureSpec.EXACTLY));view.layout(0,0,w,h)
    }
    private fun visibleText(view: View): List<TextView> {
        if(view.visibility!=View.VISIBLE) return emptyList()
        return if(view is ViewGroup) (0 until view.childCount).flatMap { visibleText(view.getChildAt(it)) } else listOfNotNull(view as? TextView)
    }
}
