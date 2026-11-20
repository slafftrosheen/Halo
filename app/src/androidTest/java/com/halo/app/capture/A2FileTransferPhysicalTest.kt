package com.halo.app.capture

import android.app.KeyguardManager
import android.content.Intent
import android.os.Bundle
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.halo.app.MainActivity
import com.halo.app.di.ManualDependencyGraph as Graph
import com.halo.app.ui.mini.HaloHostVisibility
import com.halo.app.ui.shell.HaloScreenCatalog
import com.halo.drivers.heycyan.protocol.HeyCyanConnectionState
import com.halo.drivers.heycyan.wifi.WifiConnectionState
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Explicit opt-in copy of named, previously authorized test files; no new recordings or deletion.
 * Selects only the exact previously observed/approved P2P address supplied by the operator.
 * Exercises the same file owner as the screen and assistant, including list-to-copy reuse. */
@RunWith(AndroidJUnit4::class)
class A2FileTransferPhysicalTest {
    @Test fun copyExplicitTestClipsAndRefresh() = runBlocking {
        val args = InstrumentationRegistry.getArguments()
        assumeTrue(args.getString("a2FileProbe") == "true")
        val expected = requireNotNull(args.getString("a2Address"))
        val peer = requireNotNull(args.getString("a2PeerAddress"))
        require(listOf(expected, peer).all { it.matches(Regex("[0-9A-Fa-f]{2}(:[0-9A-Fa-f]{2}){5}")) })
        val names = requireNotNull(args.getString("a2FileNames")).split(',').toSet()
        require(names.size in 1..16 && names.all { it.matches(Regex("[0-9]{17}\\.(mp4|opus|jpg)")) })
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        assertFalse(context.getSystemService(KeyguardManager::class.java).isDeviceLocked)
        context.startActivity(Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            .putExtra(HaloScreenCatalog.EXTRA_SCREEN, "devices"))
        withTimeout(60_000) {
            Graph.kidsPreferences.loadedState.first { it }
            while (!HaloHostVisibility.active.value) delay(100)
            Graph.hostGraph.heyCyanDriver.deviceState.first {
                it.connection == HeyCyanConnectionState.READY && it.transportIdentity?.controlBleAddress == expected
            }
        }
        assertFalse(Graph.kidsPreferences.enabled.value)
        val capture = requireNotNull(Graph.hostGraph.captureCoordinator)
        var selections = 0
        val selection = launch(start = CoroutineStart.UNDISPATCHED) {
            capture.wifiPeerSelection.collect { offered ->
                if (offered.deviceId == expected && offered.peers.any { it.address.equals(peer, true) }) {
                    capture.selectWifiPeer(expected, offered.peers.single { it.address.equals(peer, true) }.address)
                    selections++
                }
            }
        }
        fun receipt(value: String) = instrumentation.sendStatus(0, Bundle().apply { putString("stream", "$value\n") })
        try {
            val listed = withTimeout(180_000) { capture.files.refresh(expected) }
            assertEquals("READY", listed.phase)
            val selected = listed.files.filter { it.file.name in names }
            assertEquals("All explicitly requested test clips must be present", names, selected.map { it.file.name }.toSet())
            assertEquals(WifiConnectionState.CONNECTED, capture.fileWifiState.value)
            val prompts = selections
            val copied = withTimeout(300_000) { capture.files.importSelected(expected, listed.sourceVersion, selected.map { it.token }.toSet()) }
            assertEquals("COMPLETED", copied.phase)
            assertEquals(0, copied.failures)
            assertEquals(names, copied.saved.map { it.name }.toSet())
            assertEquals("Copy must reuse the verified browse connection", prompts, selections)
            assertEquals(WifiConnectionState.DISCONNECTED, capture.fileWifiState.value)
            receipt("A2_COPY files=${copied.saved.size} failures=${copied.failures} sameConnection=true")
            val refreshed = withTimeout(180_000) { capture.files.refresh(expected) }
            assertEquals("READY", refreshed.phase)
            receipt("A2_REFRESH available=${refreshed.files.size} physicalRetention=UNKNOWN")
        } finally {
            selection.cancelAndJoin()
            capture.files.state.value.takeIf { it.busy && it.deviceId == expected }?.operationId?.let { capture.files.cancel(expected, it) }
        }
    }
}
