package com.halo.app.ui.theme

import android.app.Activity
import android.app.KeyguardManager
import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.halo.app.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** The setting guards presentation writes. Android alone validates the device credential. */
internal class HaloLayoutLock private constructor(context: Context) {
    private val disk = context.getSharedPreferences("halo_layout_lock", Context.MODE_PRIVATE)
    private val current = MutableStateFlow(disk.getBoolean("kept", false))
    val state = current.asStateFlow()

    fun requireUnlocked() { check(!current.value) { "The Halo layout is kept" } }

    private fun change(expected: Boolean) = synchronized(this) {
        check(current.value == expected) { "Layout changed. Try again." }
        check(disk.edit().putBoolean("kept", !expected).commit()) { "Could not save layout lock" }
        current.value = !expected
    }

    companion object {
        @Volatile private var instance: HaloLayoutLock? = null
        fun get(context: Context): HaloLayoutLock = instance ?: synchronized(this) {
            instance ?: HaloLayoutLock(context.applicationContext).also { instance = it }
        }

        @Composable
        fun Controls(lock: HaloLayoutLock, locked: Boolean, enabled: Boolean) {
            val context = LocalContext.current
            val scope = rememberCoroutineScope()
            val title = stringResource(if (locked) R.string.layout_unlock else R.string.layout_keep)
            val reason = stringResource(R.string.layout_phone_lock)
            var pending by remember { mutableStateOf<Boolean?>(null) }
            var error by remember { mutableStateOf(false) }
            val confirmation = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
                val expected = pending
                pending = null
                if (result.resultCode == Activity.RESULT_OK && expected != null) scope.launch {
                    try { withContext(Dispatchers.IO) { lock.change(expected) } }
                    catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
                    catch (_: Exception) { error = true }
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                TextButton(onClick = {
                    error = false
                    val keyguard = context.getSystemService(KeyguardManager::class.java)
                    @Suppress("DEPRECATION")
                    val intent = if (keyguard?.isDeviceSecure == true)
                        keyguard.createConfirmDeviceCredentialIntent(title, reason) else null
                    if (intent == null) error = true else {
                        pending = locked
                        try { confirmation.launch(intent) }
                        catch (_: android.content.ActivityNotFoundException) { pending = null; error = true }
                    }
                }, enabled = pending == null && enabled, modifier = Modifier.heightIn(min = 48.dp)) { Text(title) }
                if (error) Text(stringResource(R.string.layout_lock_unavailable), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@Composable
internal fun rememberHaloLayoutLocked(): Boolean {
    val context = LocalContext.current
    if (!context.getSystemService(android.os.UserManager::class.java).isUserUnlocked) return true
    val holder = remember(context.applicationContext) { HaloLayoutLock.get(context) }
    return holder.state.collectAsStateWithLifecycle().value
}
