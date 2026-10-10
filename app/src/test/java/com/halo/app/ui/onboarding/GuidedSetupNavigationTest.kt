package com.halo.app.ui.onboarding

import android.content.Context
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.navigation.NavHostController
import androidx.navigation.compose.ComposeNavigator
import androidx.navigation.compose.composable
import androidx.navigation.createGraph
import androidx.test.core.app.ApplicationProvider
import com.halo.app.ui.shell.navigateToGuidedSetup
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[33],application=android.app.Application::class)
class GuidedSetupNavigationTest {
    private fun controller(start:String):NavHostController {
        val context=ApplicationProvider.getApplicationContext<Context>()
        return NavHostController(context).apply {
            navigatorProvider.addNavigator(ComposeNavigator())
            setViewModelStore(ViewModelStore())
            setLifecycleOwner(object:LifecycleOwner {
                override val lifecycle=LifecycleRegistry(this).apply { currentState=Lifecycle.State.RESUMED }
            })
            graph=createGraph(startDestination=start) {
                composable("home") {}; composable("guided_setup") {}; composable("voice") {}; composable("activity") {}
            }
        }
    }
    @Test fun returningFromConfigurationAndReviewReusesExactGuideEntry() {
        val nav=controller("guided_setup")
        val guide=nav.currentBackStackEntry!!
        guide.savedStateHandle["checked"]=true
        nav.navigate("voice"); nav.navigate("activity")
        nav.navigateToGuidedSetup()
        assertSame(guide,nav.currentBackStackEntry)
        assertEquals(true,nav.currentBackStackEntry!!.savedStateHandle.get<Boolean>("checked"))
    }
    @Test fun rerunFromNormalAppDoesNotStackDuplicatesOrDiscardHome() {
        val nav=controller("home"); val home=nav.currentBackStackEntry!!
        nav.navigateToGuidedSetup(); val guide=nav.currentBackStackEntry!!
        nav.navigateToGuidedSetup(); assertSame(guide,nav.currentBackStackEntry)
        assertTrue(nav.popBackStack()); assertSame(home,nav.currentBackStackEntry)
    }
}
