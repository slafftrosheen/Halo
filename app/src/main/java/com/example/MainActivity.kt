package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.staticCompositionLocalOf
import com.halo.app.di.ManualDependencyGraph
import com.halo.app.ui.navigation.AppNavigation
import com.halo.app.ui.theme.BrainToolTheme
import com.halo.host.android.HaloHost

val LocalHaloHost = staticCompositionLocalOf<HaloHost?> { null }

class MainActivity : ComponentActivity() {
    private lateinit var haloHost: HaloHost

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
        haloHost = HaloHost(this, ManualDependencyGraph.eventBus)
        haloHost.start()
        
        val runtime = ManualDependencyGraph.runtime
        setContent {
            CompositionLocalProvider(LocalHaloHost provides haloHost) {
                BrainToolTheme {
                    LaunchedEffect(Unit) {
                        runtime.boot()
                    }
                    
                    AppNavigation()
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        haloHost.stop()
    }
}
