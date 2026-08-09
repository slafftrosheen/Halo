package com.halo.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.lifecycleScope
import com.halo.app.di.ManualDependencyGraph
import com.halo.app.ui.screens.PermissionsScreen
import com.halo.app.ui.navigation.AppNavigation
import com.halo.app.ui.theme.BrainToolTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private var hasRequiredPermissions by mutableStateOf(false)
    private lateinit var runtime: HaloRuntime

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        hasRequiredPermissions = areRequiredPermissionsGranted()
        if (hasRequiredPermissions) {
            startAlphaLoop()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        ManualDependencyGraph.init(this)
        runtime = HaloRuntime(ManualDependencyGraph)

        hasRequiredPermissions = areRequiredPermissionsGranted()
        if (hasRequiredPermissions) {
            startAlphaLoop()
        }

        setContent {
            BrainToolTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    if (hasRequiredPermissions) {
                        AppNavigation()
                    } else {
                        PermissionsScreen(
                            onPermissionsGranted = {
                                permissionLauncher.launch(requiredPermissions())
                            }
                        )
                    }
                }
            }
        }
    }

    private fun requiredPermissions(): Array<String> = buildList {
        add(Manifest.permission.RECORD_AUDIO)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            add(Manifest.permission.BLUETOOTH_SCAN)
            add(Manifest.permission.BLUETOOTH_CONNECT)
        } else {
            add(Manifest.permission.ACCESS_FINE_LOCATION)
        }
    }.toTypedArray()

    private fun areRequiredPermissionsGranted(): Boolean = requiredPermissions().all { permission ->
        checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED
    }

    private fun startAlphaLoop() {
        lifecycleScope.launch {
            ManualDependencyGraph.wearableHost.connect()
            runtime.start()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        lifecycleScope.launch {
            runtime.stop()
        }
    }
}
