package com.halo.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
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
import com.halo.app.ui.shell.PermissionsScreen
import com.halo.app.ui.shell.AppShell
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
        enableEdgeToEdge()
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
                        AppShell()
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
            val host = ManualDependencyGraph.wearableHost
            host.connectivity.collect { state ->
                if (state is com.halo.sdk.host.ConnectionState.Connected && !runtime.isStarted()) {
                    runtime.start()
                }
            }
        }

        lifecycleScope.launch {
            val host = ManualDependencyGraph.wearableHost
            if (host !is com.halo.drivers.heycyan.HeyCyanHost) {
                host.connect()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        lifecycleScope.launch {
            runtime.stop()
        }
    }
}
