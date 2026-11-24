package com.halo.app.ui.onboarding.v13

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.halo.app.R
import com.halo.app.ui.theme.v13.Halo13Animation
import com.halo.app.ui.theme.v13.Halo13Motion

@Composable
internal fun Halo13AccessDialog(@StringRes title: Int, @StringRes description: Int, onAllow: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(title)) },
        text = {
            Column {
                Halo13Animation(Halo13Motion.ASK_ACCESS, Modifier.fillMaxWidth().height(68.dp))
                Text(stringResource(description))
            }
        },
        confirmButton = { TextButton(onAllow) { Text(stringResource(R.string.ob13_ask_allow)) } },
        dismissButton = { TextButton(onDismiss) { Text(stringResource(R.string.ob13_ask_not_now)) } },
    )
}
