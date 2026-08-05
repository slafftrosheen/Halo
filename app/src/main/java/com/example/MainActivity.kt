package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
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
        
        val microkernel = ManualDependencyGraph.microkernel
        setContent {
            CompositionLocalProvider(LocalHaloHost provides haloHost) {
                BrainToolTheme {
                    var permissionsGranted by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
                    var bootComplete by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
                    
                    if (!permissionsGranted) {
                        com.halo.app.ui.screens.PermissionsScreen(onPermissionsGranted = { permissionsGranted = true })
                    } else if (!bootComplete) {
                        LaunchedEffect(Unit) {
                            microkernel.boot()
                        }
                        com.halo.app.ui.screens.StartupScreen(onBootComplete = { bootComplete = true })
                    } else {
                        AppNavigation()
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        haloHost.stop()
    }
}
